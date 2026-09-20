import logging

from fastapi import FastAPI

from app.config import get_settings
from app.schemas import HealthResponse, ScoreRequest, ScoreResponse
from app.scoring import score

logging.basicConfig(level=get_settings().log_level)

app = FastAPI(
    title="CrediSynch Model Service",
    version="0.1.0",
    description="Fraud probability, reason codes and novelty score. Called only by the decision API.",
)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    settings = get_settings()
    return HealthResponse(status="UP", model_version=settings.model_version, model_loaded=False)


@app.post("/score", response_model=ScoreResponse)
def score_application(request: ScoreRequest) -> ScoreResponse:
    return score(request)
