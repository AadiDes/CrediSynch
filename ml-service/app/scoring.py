"""Phase 1 scoring stub.

A trained LightGBM model replaces `score()` in phase 2. Until then this returns a
*deterministic* pseudo-score derived from the application id, so that the API,
the policy engine and the UI can be built and tested against a stable contract.
It is flagged `placeholder=True` in every response so a stub can never be mistaken
for a model in a demo or a screenshot.
"""
from __future__ import annotations

import hashlib
import time

from app.config import get_settings
from app.model import FraudModel
from app.schemas import ReasonCode, ScoreRequest, ScoreResponse

_RISK_FEATURES = (
    ("VELOCITY_6H", "velocity_6h"),
    ("DEVICE_SHARED_EMAILS", "device_distinct_emails_8w"),
    ("NAME_EMAIL_MISMATCH", "name_email_similarity"),
    ("FREE_EMAIL_DOMAIN", "email_is_free"),
    ("SESSION_LENGTH", "session_length_in_minutes"),
)


def _deterministic_unit_interval(seed: str, salt: str) -> float:
    digest = hashlib.sha256(f"{salt}:{seed}".encode()).digest()
    return int.from_bytes(digest[:4], "big") / 0xFFFFFFFF


_model: FraudModel | None = None


def get_model() -> FraudModel:
    """Loaded once per process; absent artefacts keep the stub in place rather than failing."""
    global _model
    if _model is None:
        _model = FraudModel(get_settings().model_dir)
    return _model


def score(request: ScoreRequest) -> ScoreResponse:
    started = time.perf_counter()
    settings = get_settings()
    model = get_model()

    if model.loaded:
        probability, reasons = model.predict(dict(request.features))
        novelty = round(_deterministic_unit_interval(request.application_id, "novelty"), 6)
        return ScoreResponse(
            application_id=request.application_id,
            model_version=model.model_version,
            fraud_probability=round(probability, 6),
            novelty_score=novelty,
            reason_codes=reasons,
            scored_in_ms=round((time.perf_counter() - started) * 1000, 3),
            placeholder=False,
        )

    probability = round(_deterministic_unit_interval(request.application_id, "fraud") * 0.6, 6)
    novelty = round(_deterministic_unit_interval(request.application_id, "novelty"), 6)

    reasons: list[ReasonCode] = []
    for index, (code, feature) in enumerate(_RISK_FEATURES[:3]):
        contribution = round(
            _deterministic_unit_interval(request.application_id, code) * (0.4 - 0.1 * index), 6
        )
        reasons.append(
            ReasonCode(
                code=code,
                feature=feature,
                contribution=contribution,
                direction="INCREASES_RISK" if contribution >= 0.1 else "DECREASES_RISK",
            )
        )

    return ScoreResponse(
        application_id=request.application_id,
        model_version=settings.model_version,
        fraud_probability=probability,
        novelty_score=novelty,
        reason_codes=sorted(reasons, key=lambda r: abs(r.contribution), reverse=True),
        scored_in_ms=round((time.perf_counter() - started) * 1000, 3),
        placeholder=True,
    )
