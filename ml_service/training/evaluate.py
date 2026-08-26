"""Ranking metrics.

Implemented directly rather than pulled from a library so the report can state
exactly what was computed. All three are standard: NDCG rewards putting highly
relevant results near the top, MAP measures precision across every relevant
result, and Precision@k is the blunt "how many of the top k were any good".
"""

from __future__ import annotations

import numpy as np
import pandas as pd


def _dcg(relevances: np.ndarray, k: int) -> float:
    relevances = relevances[:k]
    discounts = np.log2(np.arange(2, relevances.size + 2))
    return float(np.sum((np.power(2, relevances) - 1) / discounts))


def ndcg_at_k(true_relevance: np.ndarray, scores: np.ndarray, k: int) -> float:
    order = np.argsort(-scores, kind="stable")
    ideal = np.sort(true_relevance)[::-1]
    best = _dcg(ideal, k)
    if best == 0:
        return 0.0
    return _dcg(true_relevance[order], k) / best


def map_at_k(true_relevance: np.ndarray, scores: np.ndarray, k: int,
             relevant_threshold: int = 2) -> float:
    order = np.argsort(-scores, kind="stable")[:k]
    hits, precision_sum = 0, 0.0
    for position, index in enumerate(order, start=1):
        if true_relevance[index] >= relevant_threshold:
            hits += 1
            precision_sum += hits / position
    total_relevant = int(np.sum(true_relevance >= relevant_threshold))
    if total_relevant == 0:
        return 0.0
    return precision_sum / min(total_relevant, k)


def precision_at_k(true_relevance: np.ndarray, scores: np.ndarray, k: int,
                   relevant_threshold: int = 2) -> float:
    order = np.argsort(-scores, kind="stable")[:k]
    if order.size == 0:
        return 0.0
    return float(np.mean(true_relevance[order] >= relevant_threshold))


def evaluate(frame: pd.DataFrame, score_column: str,
             ks: tuple[int, ...] = (3, 5, 10)) -> dict[str, float]:
    """Averages each metric over query groups."""
    results: dict[str, list[float]] = {}
    for _, group in frame.groupby("query_id", sort=False):
        relevance = group["relevance"].to_numpy()
        scores = group[score_column].to_numpy()
        for k in ks:
            results.setdefault(f"ndcg@{k}", []).append(ndcg_at_k(relevance, scores, k))
            results.setdefault(f"map@{k}", []).append(map_at_k(relevance, scores, k))
            results.setdefault(f"precision@{k}", []).append(precision_at_k(relevance, scores, k))
    return {metric: float(np.mean(values)) for metric, values in results.items()}
