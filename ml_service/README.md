# OpenMicHub ML

Four things live behind this service. They are not equally "trained", and the
difference matters more than any single number in this document, so it comes
first.

| Component | Fitted on | Trained? |
|---|---|---|
| Profile embeddings | Real catalogue | No — a pretrained encoder reads real profiles |
| Similar artists | Real catalogue | No — nearest neighbours over those vectors |
| **Artist segmentation** | **Real catalogue** | **Yes** — k-means fitted on the live catalogue |
| **Search ranker** | **Synthetic** | Yes, but on simulated preferences |

The ranker is a real learned model — it fits weights, it is validated on a
held-out split, it beats every baseline it is measured against. What it has
never seen is a real person choosing a real artist, because the platform does
not record that yet. Everything below is written so that claim can be checked
rather than taken on faith.

---

## 1. Profile embeddings

**What it does.** Turns each artist's profile into a 384-dimensional vector so
that "something mellow for a restaurant opening" can match a bio that never uses
any of those words.

| | |
|---|---|
| Model | `BAAI/bge-small-en-v1.5` |
| Runtime | ONNX via `fastembed`, no PyTorch in the image |
| Dimensions | 384 |
| Storage | `ml.artist_embedding`, pgvector |
| Rows | one per artist (300 in the demo catalogue) |
| Trained here? | **No.** Pretrained encoder, used as-is |
| Network at runtime | None — the model is baked into the image at build time |

**Two vectors per artist, not one.** This is the least obvious decision in the
service and the one worth understanding.

- `embedding` — includes the stage name. Used for **search**, because someone
  typing "Velvet" should find *The Velvet Club*.
- `profile_embedding` — deliberately excludes the name. Used for
  **segmentation and similarity**, because clustering on the search vector
  grouped artists whose *names* shared a word rather than whose music did.

Removing the name from the clustering vector moved silhouette from **0.138 to
0.318** on the previous catalogue. It is the single largest quality change in
the history of this service, and it came from deleting an input rather than
adding one.

The document fed to the encoder is assembled in `app/repository.py::_document`:

```
[stage name, if searching]. plays <parent genres>. styles: <sub-genres>.
based in <city>. <bio>
```

**Rebuild** after the catalogue changes — embeddings are keyed by artist id and
do not follow renames or new rows on their own:

```bash
curl -X POST localhost:8000/embeddings/rebuild
```

---

## 2. Search ranker

**Architecture.** LightGBM **LambdaRank** — gradient-boosted decision trees with
a pairwise ranking objective. Not a classifier and not a regressor: it optimises
the *order* of a result list directly, which is what a search page needs.

| | |
|---|---|
| Library | LightGBM 4.5.0 (Python 3.12.13) |
| Objective | `lambdarank`, NDCG at 3/5/10 |
| Label gain | `[0, 1, 3, 7]` — four relevance grades, super-linear |
| Learning rate | 0.05 |
| Num leaves | 31 |
| Min data in leaf | 20 |
| Feature / bagging fraction | 0.9 / 0.9 (freq 1) |
| L2 | 1.0 |
| **Best iteration** | **40** (early-stopped from 600) |
| Model file | `models/ranker.txt` + `ranker_meta.json` |

### Training data — synthetic, and stated in full

`training/generate.py` draws an organizer's true satisfaction from an explicit
utility function:

```
u = 0.24 * genre_match        exact sub-genre, same parent, or neither
  + 0.18 * price_fit          peaks near 85% of budget, falls off above it
  + 0.16 * style_affinity     LATENT — never shown to the model
  + 0.13 * rating_norm
  + 0.13 * location_match     same city, same province, or far
  + 0.08 * experience         completed bookings, log-scaled
  + 0.04 * event_fit
  + 0.04 * responsiveness
  + noise                     sd 0.12
```

| | |
|---|---|
| Artists | 600 |
| Queries | 5,000 |
| Candidate pairs | 123,723 |
| Candidates per query | 25 |
| Noise sd | 0.12 |
| Split | 3,500 train / 750 val / 750 test, **split by query** |

Two design points make this an honest exercise rather than a circular one.

**`style_affinity` is latent.** It is 16% of true utility and the model never
sees it. What it sees instead is `text_similarity`, a noisy observation of the
same thing. The model therefore cannot reach perfect ranking, which is correct —
neither can a real one.

**The split is by query, not by row.** All 25 candidates for a query live on the
same side of the split. Splitting by row would leak: the model would train on
some candidates for a query it is then tested on.

### The 13 features

| Feature | What it measures |
|---|---|
| `genre_match` | Exact sub-genre / same parent / neither |
| `text_similarity` | Cosine between query text and artist profile |
| `price_fit` | Peaks near 85% of stated budget |
| `rating_norm` | Average review score, normalised |
| `location_match` | Same city / same province / far |
| `experience` | Completed bookings, log-scaled |
| `event_fit` | Whether the genre suits the event type |
| `responsiveness` | Share of requests answered |
| `rate_to_budget_ratio` | Hourly rate over budget |
| `log_hourly_rate` | Absolute price level |
| `log_completed_bookings` | Raw volume |
| `same_city` | Binary |
| `same_province` | Binary |

### Efficiency — measured on the 750 held-out queries

| Ranker | NDCG@10 | NDCG@5 | NDCG@3 | MAP@10 | P@3 |
|---|---|---|---|---|---|
| **LambdaRank** | **0.774** | **0.702** | **0.642** | **0.644** | **0.760** |
| Genre only | 0.595 | 0.490 | 0.411 | 0.438 | 0.519 |
| Price only | 0.511 | 0.406 | 0.350 | 0.339 | 0.436 |
| Rating only | 0.411 | 0.318 | 0.276 | 0.237 | 0.337 |
| Random | 0.346 | 0.259 | 0.215 | 0.187 | 0.272 |

The baselines are the point. A ranking number alone says nothing — NDCG@10 of
0.774 could be excellent or embarrassing depending on how hard the task is.
Random scores 0.346, so the floor is high, and the best single-signal heuristic
(genre) reaches 0.595. The model's contribution is the gap from **0.595 to
0.774**: what it adds over the most obvious rule anyone would write by hand.

### Overfitting

Early stopping picked iteration **40** of a possible 600.

| Iteration | Train NDCG@10 | Val NDCG@10 | Gap |
|---|---|---|---|
| 1 | 0.7279 | 0.7257 | 0.002 |
| 10 | 0.7674 | 0.7585 | 0.009 |
| 40 (**chosen**) | 0.7807 | 0.7652 | **0.015** |
| 90 | 0.7925 | 0.7677 | 0.025 |

Train and validation separate slowly and never dramatically. Running to 90
iterations would buy 0.0025 of validation NDCG while doubling the gap — a
textbook picture of a model that is capacity-limited rather than data-limited,
which is what you would expect with 123k pairs and 31 leaves.

### What the model actually learned

Share of total split gain:

| Feature | Share |
|---|---|
| `text_similarity` | 53.7% |
| `genre_match` | 16.1% |
| `rate_to_budget_ratio` | 9.9% |
| `price_fit` | 9.9% |
| `location_match` | 6.2% |
| `rating_norm` | 2.0% |
| everything else | < 1% each |

Read this against the true weights above and it is informative rather than
flattering. The generator gave `genre_match` the largest weight at 0.24, but the
model leans hardest on `text_similarity` — because that is its only window onto
the latent `style_affinity`, worth another 0.16. Together those two account for
40% of true utility and 70% of the model's gain. That is the model recovering
the structure it was given, through the noisy channel it was given it through.

`rating_norm` gets 13% of true weight but only 2% of gain. Ratings in the
generator are near-uniform and weakly separating; the trees find cheaper splits
elsewhere. The three binary features contribute nothing measurable, being
largely redundant with `location_match`.

### Retrain

```bash
curl -X POST localhost:8000/train -H 'content-type: application/json' -d '{"queries":5000}'
```

---

## 3. Artist segmentation

The one component fitted on the real catalogue.

**Architecture.** k-means over the name-free `profile_embedding` vectors, with k
chosen by sweeping and comparing internal validity metrics rather than picked in
advance.

| | |
|---|---|
| Algorithm | k-means (scikit-learn), `n_init=10`, `random_state=42` |
| Input | 300 × 384 name-free profile embeddings |
| k searched | 2 … 12 |
| **k chosen** | **9** |
| Selection | Highest silhouette among partitions whose smallest cluster has >= 3 members |
| Persisted to | `models/segments.joblib`, `models/segments.json`, `ml.artist_segment` |

### The sweep

| k | Silhouette | Davies-Bouldin | Smallest cluster |
|---|---|---|---|
| 2 | 0.085 | 3.670 | 90 |
| 4 | 0.136 | 3.110 | 39 |
| 6 | 0.190 | 2.574 | 38 |
| 7 | 0.218 | 2.501 | 36 |
| 8 | 0.232 | 2.268 | 33 |
| **9** | **0.237** | **2.249** | **23** |
| 10 | 0.214 | 2.297 | 19 |
| 12 | 0.200 | 2.392 | 14 |

Silhouette peaks at k=9 and Davies-Bouldin bottoms out there too — the two
metrics agreeing is the reason to trust the choice. Beyond 9 both worsen and
clusters start fragmenting into groups too small to act on.

**Final:** silhouette **0.237**, Davies-Bouldin **2.249**,
Calinski-Harabasz **16.6**, smallest cluster **23**.

### Is 0.237 good?

Honestly: it is modest, and that is the truthful number for this problem.
Silhouette runs −1 to 1; textbook examples on well-separated blobs score 0.6+.
Real short text embedded into 384 dimensions does not produce blobs. Musical
genres genuinely overlap — an act described as "indie rock with a brass section"
sits between clusters because it *is* between them.

Two caveats a reader should have:

- **An earlier catalogue scored 0.318**, higher than this. That was not a better
  model. Those bios named their genre almost verbatim ("Chitwan. Punk. 3
  years."), which made clusters trivially separable. The current bios are varied
  prose, so the task is harder and the number is lower and more honest.
- **The per-segment average rating counts unrated artists as 3.0**, because the
  query coalesces nulls. With 47 artists unrated, segment averages read slightly
  low. The clustering is unaffected — rating is not an input — but do not quote
  those averages as ratings.

### The nine segments

| Segment | n | Avg rating | Avg rate (NPR/hr) | Distinctive terms |
|---|---|---|---|---|
| Nepali Pop | 40 | 4.12 | 4,040 | pop, synth, dance |
| Hindustani | 39 | 3.95 | 4,100 | flute, hindustani, sitar |
| Bluegrass | 37 | 3.99 | 3,942 | bluegrass, folk, storytelling |
| Electric Blues | 37 | 3.93 | 4,319 | delta, soul, blues |
| Smooth Jazz | 37 | 4.25 | 4,184 | smooth, fusion, bebop |
| Classic Rock | 32 | 4.05 | 4,331 | indie, punk, classic |
| Techno | 32 | 3.91 | 4,145 | ambient, techno, electronic |
| Boom Bap | 23 | 4.01 | 4,413 | boom, bap, hip-hop |
| Trap | 23 | 3.97 | 4,013 | lyrical, trap, rap |

Labels are assigned from the dominant genre, largest segment first, and are
forced to be unique — two segments both called "Techno" would be useless in a
picker. Distinctive terms are scored by how far a word's rate *inside* a cluster
exceeds its rate across the catalogue; raw frequency would return the same
filler for every group.

**Term scoring is name-blind.** A third of the catalogue trades under a personal
name, and those words are unique to one artist by construction — exactly what a
distinctiveness score rewards. Before this was fixed, labels read
"Punk — puja, maharjan, khadka". The description now excludes names, as the
vector already did.

### Retrain

```bash
docker compose exec ml python -m training.segment
```

Re-fit whenever the catalogue changes materially. Segment assignments are keyed
by artist id and go stale silently otherwise.

---

## 4. Similar artists

No model and no training: exact k-nearest-neighbours by cosine distance over the
name-free vectors, answered by pgvector's `<=>` operator.

```sql
SELECT ... COALESCE(e.profile_embedding, e.embedding) <=> (SELECT vec FROM target)
FROM ml.artist_embedding e ... ORDER BY distance LIMIT :limit
```

Deliberately *not* answered from the clustering. Two artists can be genuinely
close while sitting either side of a cluster boundary, and for a "more like this"
strip proximity is the honest answer. Similarity shown to users is `1 - distance`
as a percentage, because a bare 0.87 leaves the reader to interpret it.

**Scale note.** There is no ANN index — the table has a primary key and nothing
else, so this is a sequential scan. Exact and instant at 300 rows. Past roughly
50k artists, add an HNSW or IVFFlat index; the query does not change.

---

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Liveness, embedding count, ranker status |
| POST | `/search` | Semantic search plus learned ranking |
| POST | `/recommend` | Ranked suggestions without a text query |
| POST | `/embeddings/rebuild` | Re-embed the catalogue |
| POST | `/train` | Retrain the ranker |
| GET | `/model` | Ranker metadata and metrics |
| POST | `/segments/train` | Re-fit segmentation |
| GET | `/segments`, `/segments/report` | Segments and their metrics |
| GET | `/artists/{id}/segment` | One artist's segment |
| GET | `/artists/{id}/similar` | Nearest neighbours |

Search responses carry a `strategy` field naming the ranker that produced the
order. The API degrades to the plain artist listing when this service is
unreachable, so discovery never hard-fails.

---

## First run

```bash
docker compose exec ml python -m training.seed_world --artists 300 --wipe
curl -X POST localhost:8000/embeddings/rebuild
docker compose exec ml python -m training.segment
curl -X POST localhost:8000/train -H 'content-type: application/json' -d '{"queries":5000}'
```

Order matters: segmentation reads embeddings, so re-embed first.

---

## Retraining the ranker on real data

This is the open work, and it is blocked on data rather than modelling.

**What is needed:** a search interaction log — the query, the artists shown, the
positions they occupied, which were clicked, which led to a booking request.
That is the label LambdaRank wants, and the platform records none of it.

**What will not substitute:**

- *Booking outcomes.* Over 99% of bookings resolve identically. A label with one
  class teaches nothing.
- *Ratings.* They describe how a gig went, not whether that artist should have
  ranked above another for a particular request.
- *More synthetic data.* Increasing `--queries` sharpens recovery of a utility
  function someone wrote down. It cannot discover a preference nobody encoded.

**When the log exists:** swap `generate.py` for real impressions, keep the
split-by-query discipline, and report NDCG against the synthetic-trained model
as the baseline. That comparison — and only that — is the moment "trained on our
data" becomes a claim worth making.

---

## Layout

```
app/
  main.py        FastAPI routes
  search.py      retrieval
  ranker.py      LightGBM load and score
  features.py    the 13 features
  segments.py    segment lookups and similarity
  embedder.py    fastembed / ONNX
  repository.py  catalogue reads, document assembly
  db.py          pool, pgvector registration, schema
training/
  generate.py    the synthetic process, stated in full
  train.py       ranker training and evaluation
  segment.py     k sweep, fitting, labelling
  seed_world.py  demo catalogue with a history
  world.py       names, bios, venues, review text
  artwork.py     generated cover art
models/
  ranker.txt, ranker_meta.json
  segments.joblib, segments.json
```
