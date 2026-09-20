from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_reports_model_state():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["model_loaded"] is False


def test_score_returns_contract_fields():
    response = client.post("/score", json={"application_id": "app-123", "features": {"velocity_6h": 4}})
    assert response.status_code == 200
    body = response.json()
    assert 0.0 <= body["fraud_probability"] <= 1.0
    assert 0.0 <= body["novelty_score"] <= 1.0
    assert body["reason_codes"]
    assert body["placeholder"] is True


def test_score_is_deterministic_for_the_same_application():
    first = client.post("/score", json={"application_id": "app-abc"}).json()
    second = client.post("/score", json={"application_id": "app-abc"}).json()
    assert first["fraud_probability"] == second["fraud_probability"]


def test_score_rejects_empty_application_id():
    response = client.post("/score", json={"application_id": "", "features": {}})
    assert response.status_code == 422


def test_reason_codes_are_ordered_by_absolute_contribution():
    body = client.post("/score", json={"application_id": "app-xyz"}).json()
    contributions = [abs(r["contribution"]) for r in body["reason_codes"]]
    assert contributions == sorted(contributions, reverse=True)


def test_health_and_score_agree_on_model_state():
    """Health and every score response must tell the same story about whether a model is loaded."""
    health = client.get("/health").json()
    scored = client.post("/score", json={"application_id": "app-state"}).json()
    assert health["model_loaded"] == (not scored["placeholder"])
