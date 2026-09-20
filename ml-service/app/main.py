import logging

from fastapi import FastAPI

from app.config import get_settings
from app.schemas import HealthResponse, ScoreRequest, ScoreResponse
from app.scoring import get_model, score

logging.basicConfig(level=get_settings().log_level)

app = FastAPI(
    title="CrediSynch Model Service",
    version="0.1.0",
    description="Fraud probability, reason codes and novelty score. Called only by the decision API.",
)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    model = get_model()
    return HealthResponse(
        status="UP",
        model_version=model.model_version if model.loaded else get_settings().model_version,
        model_loaded=model.loaded,
    )


@app.post("/score", response_model=ScoreResponse)
def score_application(request: ScoreRequest) -> ScoreResponse:
    return score(request)
