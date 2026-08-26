# OpenMicHub ML

Two things: finding artists by meaning, and ordering them by fit.

```
query ──> embed ──> pgvector nearest neighbours ──> LightGBM ranker ──> results
          (retrieval: what kind of artist)          (ranking: which one)
```

Retrieval and ranking are separate on purpose. Vector similarity is good at *what
kind of artist is this* and blind to whether they are affordable, nearby or any
good. The ranker weighs those against each other. Running them in sequence keeps
each part simple enough to reason about, and is what production search systems do.

## Endpoints

| | |
|---|---|
| `POST /search` | Text query → retrieval → ranking |
| `POST /recommend` | Ranking only, for browse and filter surfaces |
| `POST /train` | Retrain the ranker and hot-reload it |
| `POST /embeddings/rebuild` | Re-embed the catalogue after import or edits |
| `GET /model` | Full training report |
| `GET /health` | Database, embedding count, model state |

Interactive docs at `http://localhost:8000/docs`.

## First run

```bash
docker compose exec ml python -m training.seed_db --artists 200
curl -X POST localhost:8000/embeddings/rebuild
curl -X POST localhost:8000/train -H 'content-type: application/json' -d '{"queries":5000}'
```

`seed_db` writes a demo catalogue into the real tables so search can be
demonstrated before the platform has artists. Every seeded account uses the
`@seed.openmichub.local` domain, so `--clear` removes exactly what it wrote.

The service works before any of this: with no trained model it falls back to a
weighted heuristic, and search simply returns nothing until embeddings exist.

## The model

**LightGBM LambdaRank.** Learning-to-rank rather than regression or
classification, because the goal is the *order* of a result list, not an absolute
score per artist. NDCG is optimised directly.

**Training data is synthetic**, and the generative process is stated in full in
`training/generate.py`. The platform has no booking history yet, so the model is
bootstrapped on data drawn from a known utility function and then measured on how
well it recovers it. A noise term gives an irreducible error floor, so the gap
between the model and the baselines is a real result rather than an artefact of a
solvable puzzle.

### Features

| Feature | Source at serving time |
|---|---|
| `text_similarity` | Cosine distance, query to artist profile, normalised across the candidate pool |
| `genre_match` | Exact sub-genre, same parent genre, or neither |
| `price_fit` | Peaks near 85% of budget, falls off sharply above it |
| `rating_norm` | Average review score |
| `location_match` | Same city, same province, or distance-decayed |
| `experience` | Confirmed bookings, log-scaled |
| `event_fit` | How well the genre suits the event type |
| `responsiveness` | Share of booking requests answered |

Plus derived terms: `rate_to_budget_ratio`, `log_hourly_rate`,
`log_completed_bookings`, `same_city`, `same_province`.

### Signals that are not always there

An organizer who types *"something mellow for a restaurant opening"* wants a
particular kind of act but never names a genre, and often gives no budget. If
every training row carried both, the model would concentrate its weight on
features that are missing exactly when a free-text search runs — and rank on
almost nothing.

So genre and budget are withheld on a share of training rows, as `NaN`, which
LightGBM routes down its own branch. `NaN` rather than `0`, because zero means
*no match* and conflating the two teaches the model that an unstated genre is a
bad one. The label still reflects the organizer's true preference: they knew what
they wanted, they just did not type it.

The effect is visible in the feature importances — with the signals always
present the model puts ~65% of its weight on `genre_match`; withheld, it shifts
to `text_similarity`, which is what search can actually supply.

At serving time a genre is recovered from the query text where one was named
("jazz trio for a corporate dinner" states a genre), and passed as `NaN` only
when genuinely absent.

### Evaluation

Split by **query group**, never by row: candidates for one query all land in the
same fold, because relevance is assigned relative to the others in that list.
Splitting by row would leak the answer.

Measured on held-out queries against four baselines — random, rating-only,
genre-only and price-only. Rating-only is the honest comparison: it is what the
platform would ship with no model at all.

Metrics are NDCG@{3,5,10}, MAP@10 and Precision@5, implemented in
`training/evaluate.py` so the report can state exactly what was computed.

## Retraining on real data

The synthetic generator exists to bootstrap. Once the platform has real bookings,
replace `generate()` with a query that reads them: an organizer's confirmed
booking is a relevance-3 label, a declined request a 1, an impression that led
nowhere a 0. Everything downstream — features, split, metrics, serving — stays
as it is.

## Layout

```
app/
  main.py        FastAPI routes
  search.py      pgvector retrieval
  ranker.py      model loading, feature assembly, scoring
  features.py    feature definitions shared by training and serving
  embedder.py    ONNX sentence embeddings via fastembed
  repository.py  reads artists from the API's database
training/
  generate.py    the synthetic generative process
  train.py       training and reporting
  evaluate.py    NDCG, MAP, Precision
  seed_db.py     demo catalogue
```

`features.py` is deliberately shared. Training and serving building features
separately is the most common way a model quietly degrades in production.
