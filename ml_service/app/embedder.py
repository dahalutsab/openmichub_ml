"""Text embeddings.

fastembed runs the model through ONNX rather than PyTorch, which keeps the image
near 1GB instead of 4GB and makes CPU inference fast enough to embed on request.
The model is baked into the image at build time, so no network call happens on
the first query.
"""

from __future__ import annotations

import logging
import threading

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


def embed_one(text: str) -> np.ndarray:
    return embed([text])[0]
