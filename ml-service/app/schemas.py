"""Scoring contract between the Spring Boot decision service and this model service.

The shape is fixed in phase 1 so that phase 2 only swaps the implementation,
never the interface.
"""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class ScoreRequest(BaseModel):
    application_id: str = Field(min_length=1, max_length=64)
    features: dict[str, float | int | str | None] = Field(default_factory=dict)


class ReasonCode(BaseModel):
    code: str
    feature: str
    contribution: float
    direction: Literal["INCREASES_RISK", "DECREASES_RISK"]


class ScoreResponse(BaseModel):
    application_id: str
    model_version: str
    fraud_probability: float = Field(ge=0.0, le=1.0)
    novelty_score: float = Field(ge=0.0, le=1.0)
    reason_codes: list[ReasonCode]
    scored_in_ms: float
    placeholder: bool = Field(
        default=False,
        description="True while the deterministic stub is in use (phase 1); false once a trained model is loaded.",
    )


class HealthResponse(BaseModel):
    status: Literal["UP", "DEGRADED"]
    model_version: str
    model_loaded: bool
