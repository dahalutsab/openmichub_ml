"""Request and response shapes for the ML API."""

from __future__ import annotations

from pydantic import BaseModel, Field


class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=500,
                       description="What the organizer is looking for, in their own words")
    city: str | None = Field(None, description="Hard filter: only artists based here")
    event_type: str | None = Field(None, description="Wedding, Corporate, Festival, ...")
    budget_per_hour: float | None = Field(None, gt=0)
    genre: str | None = Field(None, description="Preferred genre or sub-genre")
    limit: int = Field(20, ge=1, le=100)


class RecommendRequest(BaseModel):
    """Ranking without a text query — for browse and 'similar artists' surfaces."""

    city: str | None = None
    event_type: str | None = None
    budget_per_hour: float | None = Field(None, gt=0)
    genre: str | None = None
    limit: int = Field(20, ge=1, le=100)


class ArtistHit(BaseModel):
    artist_id: int
    stage_name: str
    full_name: str | None = None
    bio: str | None = None
    city: str | None = None
    hourly_rate: float
    rating: float
    completed_bookings: int
    sub_genres: list[str] = []
    parent_genres: list[str] = []
    profile_image: str | None = None

    score: float = Field(..., description="Ranking score; higher is better")
    similarity: float | None = Field(None, description="Cosine similarity to the query text")


class SearchResponse(BaseModel):
    query: str | None = None
    total: int
    strategy: str = Field(..., description="Which ranker produced the ordering")
    results: list[ArtistHit]


class RebuildResponse(BaseModel):
    embedded: int


class TrainRequest(BaseModel):
    queries: int = Field(4000, ge=200, le=50000)
    artists: int = Field(600, ge=50, le=10000)
    candidates_per_query: int = Field(25, ge=5, le=100)
    noise_sd: float = Field(0.12, ge=0.0, le=1.0)
    rounds: int = Field(600, ge=50, le=5000)
    seed: int = 42


class HealthResponse(BaseModel):
    status: str
    database: str
    embeddings: int | None = None
    ranker: dict
