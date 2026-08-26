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
