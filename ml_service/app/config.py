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
    taste_alpha_browse_unfiltered: float = 0.60

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
