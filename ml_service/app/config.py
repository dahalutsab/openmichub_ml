"""Runtime configuration, read from the environment."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Postgres. Defaults match docker-compose; override with DB_* for local runs.
    db_host: str = "postgres"
    db_port: int = 5432
    db_name: str = "open_mic_hub"
    db_user: str = "postgres"
    db_password: str = "root"

    # BAAI/bge-small-en-v1.5 produces 384-dimensional vectors. Changing the model
    # means changing embedding_dim and rebuilding every stored embedding.
    embedding_model: str = "BAAI/bge-small-en-v1.5"
    embedding_dim: int = 384

    # This service keeps its objects in their own schema. `public` belongs to the
    # API and is managed by Flyway there; creating a table in it from here would
    # make the schema look non-empty and cause Flyway to baseline a fresh
    # database instead of building it.
    db_schema: str = "ml"

    model_dir: str = "/app/models"
    ranker_filename: str = "ranker.txt"
    ranker_meta_filename: str = "ranker_meta.json"

    # Candidates pulled by vector search before the ranker reorders them.
    candidate_pool_size: int = 100

    # Cosine band that maps onto the model's 0-1 `text_similarity` scale.
    #
    # Raw cosines from this encoder occupy a narrow, model-specific band — on
    # this catalogue genre-matching pairs average 0.595 and non-matching ones
    # 0.525. The affine map through these two numbers puts those two means back
    # where training put them (0.775 and 0.225), so the feature means the same
    # thing on both sides. Re-measure with `python -m training.calibrate` after
    # changing `embedding_model` or materially changing the catalogue.
    similarity_cos_low: float = 0.4956
    similarity_cos_high: float = 0.6243

    # How noisy that observation actually is, as a fraction of the 0-1 scale.
    # Measured, and fed to the generator so training simulates a text signal no
    # more trustworthy than the real one. The old hard-coded 0.10 overstated the
    # encoder's discriminability by more than double, which is what taught the
    # model to hand `text_similarity` 54% of its gain.
    similarity_noise_sd: float = 0.314

    # ----------------------------------------------------------------- taste --
    # Personalisation reads one person's own history: their searches, the
    # profiles they opened, the filters they browsed with, and their bookings.

    # How far back that history is read, and how much of it. Both are bounds on
    # the query rather than opinions about relevance - the decay below is what
    # decides how much an old event actually counts for.
    taste_window_days: int = 180
    taste_max_events: int = 300

    # After this many days an event counts half as much. Six weeks: long enough
    # that a wedding booked last month still shapes the list, short enough that
    # last spring's festival hunt does not outweigh what someone is doing today.
    taste_half_life_days: float = 45.0

    # Searches get their own, much shorter one. A search is about the event
    # being planned this week; a fortnight later it says almost nothing, while a
    # booking from the same day still does.
    taste_intent_half_life_days: float = 7.0

    # Recent searches' share when they are blended with stated browse filters.
    # Both are query text, so this is a like-for-like mix. Over half, because a
    # search is what someone typed and a filter is what they clicked past.
    taste_intent_share: float = 0.6

    # Total decayed weight needed before any personalisation is applied. Below
    # it a person is treated as new and gets the unpersonalised ranking, because
    # a taste profile built from one profile view is a guess dressed up as data.
    taste_min_signal: float = 0.75

    # How far personalisation is allowed to move the ranking, as a share of the
    # final score. Lower when someone typed a query: they have just said what
    # they want, and their history is context rather than a correction.
    taste_alpha_search: float = 0.25
    taste_alpha_browse: float = 0.40

    # And higher still when the browse states nothing at all - no genre, no
    # occasion, no budget. The ranker's request-specific features are all
    # withheld on such a request, so what it is left ordering by is rating and
    # track record, the same for everyone. That is exactly the surface where a
    # person's own history should have the larger say.
    #
    # 0.90, chosen by `training/tune_recs.py` on a validation window: from 0.60
    # it lifted NDCG@10 across organizers and visitors from 0.076 to 0.123, the
    # largest effect of anything tuned. 1.0 scored within noise of it and would
    # switch the model off entirely; 0.9 keeps it as the tie-breaker. The filtered
    # and search shares above were not tuned - the evaluation replays the front
    # page, not searches.
    taste_alpha_browse_unfiltered: float = 0.90

    # Share of the browse candidate pool retrieved from the taste vector, the
    # rest coming from what the person is asking for now. A share of the slots
    # rather than of a blended vector: the two sources are cosine distributions
    # with different centres, and averaging them hands the pool to the taste
    # side however the weights are written.
    taste_retrieval_share: float = 0.35

    # Cosine band between a taste vector and an artist's profile vector, mapped
    # onto 0-1 the same way `similarity_cos_*` is, and for the same reason: raw
    # cosines from this encoder sit in a narrow band, and a fixed map keeps the
    # number meaning the same thing for every user. Profile-to-profile cosines
    # run higher than query-to-profile ones, so the band is its own measurement:
    # `python -m training.calibrate` prints both.
    taste_cos_low: float = 0.55
    taste_cos_high: float = 0.80

    # A taste profile is rebuilt at most this often per user. Long enough to
    # spare the database a history scan per keystroke, short enough that the
    # artist someone just opened counts towards their next search. A search does
    # not wait for it at all - `personalization.note_search` folds that into the
    # cached profile as it happens.
    taste_cache_ttl_seconds: float = 60.0

    # ------------------------------------------------------- platform signals --
    # What everyone's behaviour says: see `app/signals.py`.

    # Co-choice reads a year of bookings and views. Ties between acts are durable -
    # the circuit a planner hires from changes over seasons, not weeks - so the
    # half-life is long, and views fade faster than bookings because a glance is
    # a weaker tie than a hire.
    co_window_days: int = 365
    co_half_life_days: float = 180.0
    co_view_half_life_days: float = 60.0

    # Demand is "this month", not "ever": a three-week half-life inside a
    # quarter-long window, so the front page moves as bookings do.
    demand_window_days: int = 90
    demand_half_life_days: float = 21.0

    # Exposure counts appearances in the top of a served list over the last month.
    exposure_window_days: int = 30
    exposure_top_positions: int = 8

    # Rebuilt at most this often. The whole platform's history does not change
    # meaningfully in five minutes, and one person's own actions reach their
    # ranking through their taste profile, which refreshes every minute.
    signals_ttl_seconds: float = 300.0

    # Share of a personalised browse pool drawn from co-choice neighbours, beside
    # the taste and query shares.
    co_retrieval_share: float = 0.25

    # ------------------------------------------------------------- blending --
    # How demand, exposure and variety enter the final order, by surface. The
    # blended score is always the largest part; these move it, they do not replace it.

    # Demand's share of the final score. Largest on the unfiltered front page,
    # where nothing about the request distinguishes one act from another and
    # "who are organizers booking right now" is the most useful thing to show.
    # 0.10 rather than the 0.25 first written: on the validation window 0.25 cost
    # organizers with a clear taste more than it gave anyone else - a list of
    # what is popular is a poor list for someone who knows what they want.
    demand_weight_home: float = 0.10
    demand_weight_browse: float = 0.10
    demand_weight_search: float = 0.04

    # The most an artist that has barely been shown can gain over one shown on
    # every visit, as a share of the final score. Small on purpose: it is a
    # chance to be seen, not a promotion.
    exploration_weight: float = 0.06
    # Exposure at which half of that bonus is gone.
    exploration_half_exposure: float = 40.0

    # An artist shown near the top on this many separate visits (half-hour
    # buckets) to the same person, and never opened, starts to give way to others.
    skip_threshold: int = 3
    skip_window_days: int = 21
    # And the most it can be pushed down by, as a share of the final score,
    # reached three visits past the threshold. 0.25, not the 0.10 first written:
    # in a browser test an act ignored at the top of six front pages kept its
    # slot, because personalised scores on one first screen span about 0.13 and
    # a 0.10 penalty could never move anything off it.
    skip_penalty: float = 0.25

    # Maximal-marginal-relevance on browse surfaces: the share of each pick that
    # is relevance, the rest being difference from what is already on the page.
    # 1.0 turns variety off. Search is left as ranked - someone who typed "jazz
    # trio" wants jazz trios, not a sampler.
    diversity_lambda: float = 0.8

    # The most of a browse page's first screen that may be acts this person has
    # already booked or opened. Chosen, not tuned: the evaluation rewards
    # rebooking, and a front page of eight acts someone already knows scored well
    # on it while showing them nothing new.
    familiar_share_first_screen: float = 0.375

    # Ratings are shrunk towards the catalogue mean as if every artist had this
    # many extra reviews at the mean. One 5-star review should not outrank forty
    # at 4.6.
    rating_prior_reviews: float = 5.0

    @property
    def dsn(self) -> str:
        # search_path puts this service's schema first, so unqualified writes land
        # there, while `public` stays visible for reading the API's tables.
        search_path = f"{self.db_schema},public"
        return (
            f"host={self.db_host} port={self.db_port} dbname={self.db_name} "
            f"user={self.db_user} password={self.db_password} "
            f"options=-csearch_path={search_path}"
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()
