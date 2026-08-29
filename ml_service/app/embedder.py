"""Text embeddings.

fastembed runs the model through ONNX rather than PyTorch, which keeps the image
near 1GB instead of 4GB and makes CPU inference fast enough to embed on request.
The model is baked into the image at build time, so no network call happens on
the first query.
"""

from __future__ import annotations

import logging
import threading
from functools import lru_cache

import numpy as np

from app.config import get_settings

log = logging.getLogger(__name__)

_model = None
_lock = threading.Lock()


def get_model():
    """Loads the encoder once, on first use.

    Deferred rather than loaded at import so the container can pass its health
    check while the model is still warming up.
    """
    global _model
    if _model is None:
        with _lock:
            if _model is None:
                from fastembed import TextEmbedding

                settings = get_settings()
                log.info("Loading embedding model %s", settings.embedding_model)
                _model = TextEmbedding(model_name=settings.embedding_model)
                log.info("Embedding model ready")
    return _model


def embed(texts: list[str]) -> np.ndarray:
    if not texts:
        return np.empty((0, get_settings().embedding_dim), dtype=np.float32)
    vectors = np.array(list(get_model().embed(texts)), dtype=np.float32)
    # Normalised, so cosine distance and inner product agree.
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    return vectors / np.clip(norms, 1e-9, None)


@lru_cache(maxsize=1024)
def _embed_one_cached(text: str) -> np.ndarray:
    return embed([text])[0]


def embed_one(text: str) -> np.ndarray:
    """Embeds a single query, remembering recent ones.

    Encoding one short string costs about 45ms, which is most of a search
    request. Browse surfaces repeat themselves hard — `/recommend` turns its
    filters back into a query, and there are only so many genre-event-city
    combinations — so the same text arrives over and over. Text searches repeat
    too, if less reliably.

    The vector is handed out shared rather than copied, so callers must treat it
    as read-only. Every caller here either passes it to psycopg or reads it in a
    dot product.
    """
    return _embed_one_cached(text)
