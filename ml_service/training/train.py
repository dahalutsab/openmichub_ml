"""Trains the LambdaRank model and writes a report.

Run inside the container:

    python -m training.train --queries 4000

Everything is split by query group, never by row: putting some candidates for a
query in train and the rest in test would leak the answer, because relevance is
assigned relative to the other candidates for that same query.
"""

from __future__ import annotations

import argparse
import json
import platform
from datetime import datetime, timezone
from pathlib import Path

import lightgbm as lgb
import numpy as np
import pandas as pd

from app.features import FEATURE_COLUMNS, build_frame
from training.evaluate import evaluate
from training.generate import TRUE_WEIGHTS, GeneratorConfig, generate

KS = (3, 5, 10)


def split_by_query(pairs: pd.DataFrame, seed: int,
                   val_fraction: float = 0.15, test_fraction: float = 0.15):
    """Groupwise split. A query appears in exactly one of train, val or test."""
    query_ids = pairs["query_id"].unique()
    rng = np.random.default_rng(seed)
    rng.shuffle(query_ids)

    n_test = int(len(query_ids) * test_fraction)
    n_val = int(len(query_ids) * val_fraction)
    test_ids = set(query_ids[:n_test])
    val_ids = set(query_ids[n_test:n_test + n_val])

    is_test = pairs["query_id"].isin(test_ids)
    is_val = pairs["query_id"].isin(val_ids)
    return pairs[~(is_test | is_val)].copy(), pairs[is_val].copy(), pairs[is_test].copy()


def _group_sizes(frame: pd.DataFrame) -> np.ndarray:
    """Candidates per query, in the order LightGBM will read the rows."""
    return frame.groupby("query_id", sort=False).size().to_numpy()


def _as_dataset(frame: pd.DataFrame, reference: lgb.Dataset | None = None) -> lgb.Dataset:
    ordered = frame.sort_values("query_id", kind="stable")
    return lgb.Dataset(
        ordered[FEATURE_COLUMNS].to_numpy(dtype=np.float32),
        label=ordered["relevance"].to_numpy(),
        group=_group_sizes(ordered),
        feature_name=FEATURE_COLUMNS,
        reference=reference,
        free_raw_data=False,
    )


def add_baselines(frame: pd.DataFrame, seed: int) -> pd.DataFrame:
    """Reference points the model has to beat to have earned its place.

    Random is the floor. Rating-only is the honest one: it is what the platform
    would ship without any model, and it is a genuinely reasonable heuristic.
    Price-only checks that the model is not merely learning "cheaper is better".
    """
    rng = np.random.default_rng(seed)
    frame = frame.copy()
    frame["score_random"] = rng.random(len(frame))
    frame["score_rating_only"] = frame["rating_norm"]
    frame["score_price_only"] = frame["price_fit"]
    frame["score_genre_only"] = frame["genre_match"]
    # What shipping retrieval alone would give: order by the text signal and
    # ignore price, distance and rating entirely. The ranker has to beat this or
    # the second stage is not paying for itself.
    frame["score_similarity_only"] = frame["text_similarity"]
    return frame


def train(cfg: GeneratorConfig, out_dir: Path, num_rounds: int = 600) -> dict:
    print("Generating synthetic data ...")
    artists, queries, pairs = generate(cfg)
    print(f"  {len(artists):,} artists, {len(queries):,} queries, {len(pairs):,} pairs")

    pairs = pairs.merge(
        artists[["artist_id", "hourly_rate", "completed_bookings"]],
        on="artist_id", how="left",
    )
    pairs = build_frame(pairs)

    train_df, val_df, test_df = split_by_query(pairs, cfg.seed)
    print(f"  split by query: train {train_df['query_id'].nunique():,} / "
          f"val {val_df['query_id'].nunique():,} / test {test_df['query_id'].nunique():,}")

    train_set = _as_dataset(train_df)
    val_set = _as_dataset(val_df, reference=train_set)

    params = {
        "objective": "lambdarank",
        "metric": "ndcg",
        "ndcg_eval_at": list(KS),
        # Relevance is graded 0-3, so the gain table needs four entries.
        "label_gain": [0, 1, 3, 7],
        "learning_rate": 0.05,
        "num_leaves": 31,
        "min_data_in_leaf": 20,
        "feature_fraction": 0.9,
        "bagging_fraction": 0.9,
        "bagging_freq": 1,
        "lambda_l2": 1.0,
        "verbosity": -1,
        "seed": cfg.seed,
    }

    print("Training LambdaRank ...")
    evals: dict = {}
    booster = lgb.train(
        params,
        train_set,
        num_boost_round=num_rounds,
        valid_sets=[train_set, val_set],
        valid_names=["train", "val"],
        callbacks=[
            lgb.early_stopping(stopping_rounds=50, verbose=False),
            lgb.record_evaluation(evals),
            lgb.log_evaluation(period=100),
        ],
    )
    print(f"  stopped at iteration {booster.best_iteration}")

    test_df = add_baselines(test_df, cfg.seed)
    test_df["score_model"] = booster.predict(
        test_df[FEATURE_COLUMNS].to_numpy(dtype=np.float32),
        num_iteration=booster.best_iteration,
    )

    strategies = {
        "model (LambdaRank)": "score_model",
        "rating only": "score_rating_only",
        "genre only": "score_genre_only",
        "similarity only": "score_similarity_only",
        "price only": "score_price_only",
        "random": "score_random",
    }
    results = {name: evaluate(test_df, column, KS) for name, column in strategies.items()}

    gains = booster.feature_importance(importance_type="gain")
    importance = {
        name: float(value)
        for name, value in sorted(zip(FEATURE_COLUMNS, gains), key=lambda kv: -kv[1])
    }
    total_gain = sum(importance.values()) or 1.0
    importance_share = {name: value / total_gain for name, value in importance.items()}

    out_dir.mkdir(parents=True, exist_ok=True)
    booster.save_model(str(out_dir / "ranker.txt"), num_iteration=booster.best_iteration)

    report = {
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "python": platform.python_version(),
        "lightgbm": lgb.__version__,
        "dataset": {
            "artists": len(artists),
            "queries": len(queries),
            "pairs": len(pairs),
            "candidates_per_query": cfg.candidates_per_query,
            "noise_sd": cfg.noise_sd,
            "seed": cfg.seed,
        },
        "split": {
            "train_queries": int(train_df["query_id"].nunique()),
            "val_queries": int(val_df["query_id"].nunique()),
            "test_queries": int(test_df["query_id"].nunique()),
        },
        "params": params,
        "best_iteration": int(booster.best_iteration),
        "features": FEATURE_COLUMNS,
        "metrics": results,
        "feature_importance_gain": importance,
        "feature_importance_share": importance_share,
        "true_utility_weights": TRUE_WEIGHTS,
        "learning_curve": {
            "train_ndcg@10": evals.get("train", {}).get("ndcg@10", []),
            "val_ndcg@10": evals.get("val", {}).get("ndcg@10", []),
        },
    }
    (out_dir / "ranker_meta.json").write_text(json.dumps(report, indent=2))
    return report


def print_report(report: dict) -> None:
    print("\n" + "=" * 74)
    print("TEST-SET RANKING QUALITY".center(74))
    print("=" * 74)
    header = f"{'strategy':<22}" + "".join(f"{f'NDCG@{k}':>10}" for k in KS) + f"{'MAP@10':>10}{'P@5':>8}"
    print(header)
    print("-" * 74)
    for name, metrics in report["metrics"].items():
        row = f"{name:<22}"
        row += "".join(f"{metrics[f'ndcg@{k}']:>10.4f}" for k in KS)
        row += f"{metrics['map@10']:>10.4f}{metrics['precision@5']:>8.4f}"
        print(row)
    print("-" * 74)

    model_ndcg = report["metrics"]["model (LambdaRank)"]["ndcg@10"]
    best_baseline_name, best_baseline = max(
        ((n, m) for n, m in report["metrics"].items() if n != "model (LambdaRank)"),
        key=lambda kv: kv[1]["ndcg@10"],
    )
    lift = (model_ndcg / best_baseline["ndcg@10"] - 1) * 100
    print(f"\nBest baseline: {best_baseline_name} (NDCG@10 {best_baseline['ndcg@10']:.4f})")
    print(f"Model improves NDCG@10 by {lift:+.1f}%")

    print("\n" + "=" * 74)
    print("WHAT THE MODEL LEARNED TO WEIGHT".center(74))
    print("=" * 74)
    print(f"{'feature':<26}{'learned share':>16}{'true weight':>16}")
    print("-" * 74)
    for name, share in list(report["feature_importance_share"].items())[:8]:
        true_weight = report["true_utility_weights"].get(name)
        true_text = f"{true_weight:.2f}" if true_weight is not None else "derived"
        print(f"{name:<26}{share:>15.1%}{true_text:>16}")
    print("-" * 74)
    print("Derived features carry no true weight of their own; they re-express\n"
          "the same signals, so importance spreads across them.")


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the artist ranking model.")
    parser.add_argument("--artists", type=int, default=GeneratorConfig.n_artists)
    parser.add_argument("--queries", type=int, default=GeneratorConfig.n_queries)
    parser.add_argument("--candidates", type=int, default=GeneratorConfig.candidates_per_query)
    parser.add_argument("--noise", type=float, default=GeneratorConfig.noise_sd)
    parser.add_argument("--seed", type=int, default=GeneratorConfig.seed)
    parser.add_argument("--rounds", type=int, default=600)
    parser.add_argument("--out", type=Path, default=Path("/app/models"))
    args = parser.parse_args()

    cfg = GeneratorConfig(
        n_artists=args.artists, n_queries=args.queries,
        candidates_per_query=args.candidates, noise_sd=args.noise, seed=args.seed,
    )
    report = train(cfg, args.out, num_rounds=args.rounds)
    print_report(report)
    print(f"\nModel and report written to {args.out}")


if __name__ == "__main__":
    main()
