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
