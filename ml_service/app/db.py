"""Postgres access.

The ML service reads the same database the API writes to, rather than keeping its
own copy. Artists, bookings and reviews are the training signal, so a second
store would only introduce drift.
"""

from __future__ import annotations

import logging
from contextlib import contextmanager

import psycopg
from pgvector.psycopg import register_vector
from psycopg_pool import ConnectionPool

from app.config import get_settings

log = logging.getLogger(__name__)

_pool: ConnectionPool | None = None
_schema_ready = False


def _configure(conn: psycopg.Connection) -> None:
    """Teaches the connection the pgvector types.

    Tolerant of the extension not existing yet: the service can start before the
    schema has been prepared, and registering would otherwise fail every
    connection in the pool and make the database look permanently unreachable.
    """
    try:
        register_vector(conn)
    except psycopg.ProgrammingError:
        log.debug("pgvector types not registered yet; will register after schema setup")


def get_pool() -> ConnectionPool:
    global _pool
    if _pool is None:
        settings = get_settings()
        _pool = ConnectionPool(
            settings.dsn,
            min_size=1,
            max_size=8,
            # Kept short so a health check fails fast rather than hanging past
            # the container's healthcheck timeout.
            timeout=5.0,
            configure=_configure,
            open=True,
            check=ConnectionPool.check_connection,
        )
    return _pool


@contextmanager
def connection():
    ensure_schema()
    with get_pool().connection() as conn:
        yield conn


def init_schema() -> None:
    """Creates the vector extension and the embedding table.

    Uses a direct connection rather than the pool: the pool registers pgvector
    types on every connection, which cannot succeed until the extension exists.

    Everything is created in this service's own schema, never in `public`.
    `public` belongs to the API and is under Flyway's control; putting a table
    there from here makes a fresh database look non-empty, at which point Flyway
    baselines it instead of running the migrations and the API starts against an
    empty schema.
    """
    global _schema_ready
    settings = get_settings()
    schema = settings.db_schema

    with psycopg.connect(settings.dsn, connect_timeout=5) as conn:
        conn.execute(f"CREATE SCHEMA IF NOT EXISTS {schema}")
        conn.commit()
        # Installed into this schema on a fresh database. If an earlier install
        # put it in `public` it stays there, and the search_path finds it either
        # way.
        conn.execute(f"CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA {schema}")
        conn.commit()
        conn.execute(
            f"""
            CREATE TABLE IF NOT EXISTS {schema}.artist_embedding (
                artist_id   BIGINT PRIMARY KEY,
                embedding   vector({settings.embedding_dim}) NOT NULL,
                source_text TEXT NOT NULL,
                updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """
        )
        conn.commit()

    # Existing pooled connections predate the extension, so drop them and let
    # the pool rebuild with the vector types registered.
    global _pool
    if _pool is not None:
        _pool.close()
        _pool = None

    _schema_ready = True
    log.info("Vector schema ready")


def ensure_schema() -> None:
    """Prepares the schema once, on first use."""
    if not _schema_ready:
        init_schema()
