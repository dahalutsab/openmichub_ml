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
| Personalisation | Real user history | No — weighted aggregation, no fitted parameters |

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
| **Best iteration** | **87** (early-stopped from 600) |
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
| Candidate pairs | 123,799 |
| Candidates per query | 25 |
| Noise sd | 0.12 |
| Similarity noise sd | 0.314 — **measured**, see below |
| Genre / budget / text withheld | 45% / 35% / 20% of queries |
| Split | 3,500 train / 750 val / 750 test, **split by query** |

Three design points make this an honest exercise rather than a circular one.

**`style_affinity` is latent.** It is 16% of true utility and the model never
sees it. What it sees instead is `text_similarity`, a noisy observation of the
same thing. The model therefore cannot reach perfect ranking, which is correct —
neither can a real one.

**How noisy that observation is, is measured rather than assumed.** This is the
part that used to be wrong. The generator simulated `text_similarity` with a
noise sd of 0.10, which made a genre match separate from a non-match more than
twice as cleanly in training as the real encoder manages on the real catalogue:

| | genre-matching | non-matching | separation / pooled sd |
|---|---|---|---|
| `bge-small-en-v1.5`, live catalogue | 0.5953 ± 0.0438 | 0.5246 ± 0.0410 | **1.67** |
| generator, noise sd 0.10 | 0.775 ± 0.142 | 0.225 ± 0.142 | **3.88** |
| generator, noise sd 0.314 | 0.775 ± 0.330 | 0.225 ± 0.330 | **1.67** |

The model had learned to trust a signal that clean and gave `text_similarity`
54% of its total gain — weight the feature cannot possibly earn in production,
where it is half as informative. `training/calibrate.py` measures the real
figure against the live catalogue and prints the constants; re-run it after
changing `embedding_model`.

**The text signal is withheld on 20% of queries.** Browse and "similar artists"
surfaces rank with no query text at all, so `text_similarity` is genuinely
absent there, in the same way `genre_match` is absent when nobody picked a
genre. Every training row used to carry it, leaving the model no branch for its
absence — and serving passed a flat 0.5 for every candidate, which pinned the
most important feature to a constant and collapsed the trees onto a handful of
leaves. A hundred artists came back on thirty-six distinct scores, the top ten
sharing two between them.

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
| **LambdaRank** | **0.720** | **0.648** | **0.594** | **0.571** | **0.702** |
| Genre only | 0.603 | 0.503 | 0.430 | 0.443 | 0.548 |
| Similarity only | 0.535 | 0.433 | 0.379 | 0.358 | 0.475 |
| Price only | 0.529 | 0.424 | 0.369 | 0.349 | 0.444 |
| Rating only | 0.414 | 0.330 | 0.286 | 0.242 | 0.349 |
| Random | 0.341 | 0.255 | 0.214 | 0.185 | 0.269 |

The baselines are the point. A ranking number alone says nothing — NDCG@10 of
0.720 could be excellent or embarrassing depending on how hard the task is.
Random scores 0.341, so the floor is high, and the best single-signal heuristic
(genre) reaches 0.603. The model's contribution is the gap from **0.603 to
0.720**: what it adds over the most obvious rule anyone would write by hand.

**Similarity only** is the baseline that matters most for this architecture. It
is what shipping retrieval on its own would give: order by the text signal and
ignore price, distance and rating entirely. It reaches 0.535, so the ranking
stage earns its place — but note how close that is to the other single signals.
Vector similarity is one opinion among several, not the answer.

> **These numbers are lower than the 0.774 this file used to report, and that is
> the improvement.** The old figure was measured against a generator whose
> simulated embedding was twice as discriminative as the real one, with the text
> signal present on every row. The model was being graded on an easier task than
> the one it actually faces. Fixing the simulation made the benchmark harder and
> the model honest; the live results got better at the same time. The two
> numbers are not comparable, and only the second describes production.

### Overfitting

Early stopping picked iteration **87** of a possible 600.

| Iteration | Train NDCG@10 | Val NDCG@10 | Gap |
|---|---|---|---|
| 1 | 0.6879 | 0.6876 | 0.000 |
| 10 | 0.7147 | 0.7113 | 0.003 |
| 40 | 0.7258 | 0.7188 | 0.007 |
| 87 (**chosen**) | 0.7374 | 0.7226 | **0.015** |
| 137 | 0.7471 | 0.7216 | 0.026 |

Train and validation separate slowly and never dramatically. Past 87 the
validation curve turns over — 137 iterations is worse on validation while the
gap has nearly doubled — so the stopping point is a real optimum rather than a
budget running out.

The model now needs 87 iterations where it used to stop at 40. That is the
expected consequence of a harder, more realistic task: with the text signal
noisier and sometimes absent, the remaining features have to be combined in more
ways, and there is more structure left to fit.

### What the model actually learned

Share of total split gain, before and after the calibration fix:

| Feature | Before | **After** |
|---|---|---|
| `genre_match` | 16.1% | **44.6%** |
| `text_similarity` | 53.7% | **14.8%** |
| `rate_to_budget_ratio` | 9.9% | **14.4%** |
| `price_fit` | 9.9% | **10.9%** |
| `location_match` | 6.2% | **8.1%** |
| `rating_norm` | 2.0% | **3.5%** |
| `log_hourly_rate` | < 1% | 1.5% |
| `experience` | < 1% | 1.1% |
| everything else | < 1% each | < 1% each |

The two columns are the whole story of what was wrong. The generator gives
`genre_match` the largest true weight at 0.24, and the model now agrees with it.
Before, the model leaned hardest on `text_similarity` — not because it was the
strongest signal, but because the simulation made it twice as reliable as the
real encoder is. Trained against a realistic encoder, the model puts the text
signal back where it belongs: a useful third opinion at 14.8%, not the whole
answer at 54%.

Everything else moved in the same direction. Price, distance and rating all rose
as the model stopped spending its splits on a feature that could not carry them.
`rating_norm` still gets 13% of true weight for only 3.5% of gain — ratings in
the generator are near-uniform and weakly separating, so the trees find cheaper
splits elsewhere. The three binary features contribute nothing measurable, being
largely redundant with `location_match`; they are the first candidates to drop
if the feature set is ever pruned.

`event_fit` earns 0.1% of gain against 4% of true weight, which is honest but
unflattering: the event-genre table is coarse enough that the model can mostly
infer it from `genre_match` and does.

### Serving must compute these the same way

A ranking model is only as good as the features it is handed at request time,
and four of these were being computed differently at serving than in training.
All four are fixed; they are worth naming because each is easy to reintroduce.

| | Was | Now |
|---|---|---|
| `text_similarity` on search | Min-max normalised across the retrieved pool, so the top hit always scored exactly 1.0 and the last exactly 0.0 — a restatement of the retrieval order, stretching a 0.08-wide cosine band over the full range | Fixed affine map from the measured cosine band (`similarity_cos_low`/`high`), so the same cosine means the same thing on every query |
| `text_similarity` on browse | Flat 0.5 for every candidate, pinning 54% of the model's gain to a constant | NaN — a withheld feature, which the model is now trained to handle — and `/recommend` builds a query from the filters so it usually is not withheld at all |
| `genre_match` | One name passed as both sub-genre and parent, so a request for "Bebop" scored a Jazz act without it at 0.0, the same as a Techno act | Resolved through the catalogue's own taxonomy into a real (sub, parent) pair: 1.0 / 0.6 / 0.0 |
| `event_fit` | Serving's copy of the event table listed 3–4 genres per event instead of 8; the rest silently took the 0.5 default | One table in `app/taxonomy.py`, imported by both sides |

The genre and event tables now have a single definition that training and
serving both import, and `genre_match` and `price_fit` are single functions
called from both. Two copies of a scoring rule is how the first three of those
happened.

### Retrain

```bash
curl -X POST localhost:8000/train -H 'content-type: application/json' -d '{"queries":5000}'
```

Re-measure the encoder first if the embedding model or the catalogue has changed
materially — the generator reads `similarity_noise_sd` from settings, so a stale
value silently trains against the wrong encoder:

```bash
docker compose exec ml python -m training.calibrate   # prints the constants
```

Paste the three numbers it prints into `app/config.py`, rebuild, then retrain.

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

## 5. Personalisation

Everything above ranks a *request*. Two people planning the same event get the
same list, however differently they have behaved on the platform. This is the
part that reads the person.

**What it reads.** Four sources, all already on the platform, none of them new
tracking beyond the interaction log the API now writes:

| Source | Table | Weight before decay |
|---|---|---|
| Confirmed or completed booking | `booking` | 1.0 |
| Booking request, not yet answered | `booking` | 0.6 |
| Booking the artist declined | `booking` | 0.45 |
| Profile view | `user_interaction` | 0.45 |
| Search, with its text | `user_interaction` | 0.35 |
| Browse with filters | `user_interaction` | 0.25 |

Only signed-in visitors are recorded. An anonymous one is not identified across
requests and is served exactly as before.

**Recency decays rather than cutting off.** Every event halves in weight each
`taste_half_life_days` (45). A window with a hard edge would make someone's
ranking jump on the day an old booking fell out of it.

**What it builds.** One `TasteProfile` per person, cached for two minutes:

- a **preference vector** — the weighted centre of the profile vectors of
  artists they engaged with, plus the embedded text of their recent searches,
  re-normalised to unit length
- **genre and city affinities**, relative, scaled so the strongest is 1.0
- a **typical rate**, from rates actually paid and budgets actually typed
- the artists they have **booked** and **read**

**Where it applies.** Two places, deliberately different:

| Surface | Retrieval | Re-ranking |
|---|---|---|
| `/search` — words were typed | untouched | 25% of the final score |
| `/recommend` — browse | taste vector, blended 35% with any stated filters | 40% |

Re-ranking alone cannot fix a browse surface: it can only reorder whatever pool
retrieval returned, so on a surface with no words the taste vector is allowed
into retrieval itself. A typed query is left alone — somebody who searches "dj
for a club night" gets DJs, however much jazz they have booked.

The model's score and the affinity are blended after mapping the score onto 0-1
with a logistic on its standardised value. Min-max would pin the top candidate
to exactly 1.0 and the last to 0.0 on every request, which throws away how far
apart they actually were.

**Below `taste_min_signal` (0.75 of decayed weight) nothing happens at all.** One
profile view is worth 0.45; a person with a single click gets the ordinary
ranking rather than a taste profile inferred from nothing.

**Reasons are emitted by the component that moved the score**, not written
afterwards — "you have booked them before", "you keep coming back to Jazz",
"around what you usually pay". At most two per artist reach the card.

**Calibration.** The cosine between a taste vector and an artist's profile
vector runs higher and tighter than a query-to-profile cosine, so it has its own
band (`taste_cos_low`/`taste_cos_high`), measured the same way and printed by the
same command:

```bash
docker compose exec ml python -m training.calibrate
```

**Not a trained model.** Nothing here is fitted. The weights above are a stated
policy about what a booking is worth relative to a click, and they are in one
place — `app/personalization.py` — precisely so they can be argued with. What
would make this learned is the same missing ingredient the ranker needs: an
impression log with positions and clicks.

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
| GET | `/users/{id}/taste` | One person's taste profile, and whether it is usable |

`/search` and `/recommend` accept an optional `user_id`. With one, the response
carries `personalized: true` and each hit carries the `reasons` behind its
position; without one, both endpoints behave exactly as they did before.

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
That is the label LambdaRank wants.

`user_interaction` is now half of it. Searches, browse filters and profile views
are recorded per user, which is what personalisation runs on — but it records
what someone *did*, not what they were *shown*. Without the impressions and the
positions they occupied, a click cannot be told apart from an artist who simply
happened to be first, and that distinction is the whole of what LambdaRank
learns. Logging the returned ids and their ranks alongside the search row is the
remaining step, and it is a small one.

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
  features.py    the 13 features, and the similarity calibration
  taxonomy.py    genres, cities and event fit — one copy, shared with training
  segments.py    segment lookups and similarity
  personalization.py  taste profiles from one person's own history
  embedder.py    fastembed / ONNX
  repository.py  catalogue reads, document assembly
  db.py          pool, pgvector registration, schema
training/
  generate.py    the synthetic process, stated in full
  calibrate.py   measures the encoder's real accuracy, for the generator
  train.py       ranker training and evaluation
  segment.py     k sweep, fitting, labelling
  seed_world.py  demo catalogue with a history
  world.py       names, bios, venues, review text
  artwork.py     generated cover art
models/
  ranker.txt, ranker_meta.json
  segments.joblib, segments.json
```
