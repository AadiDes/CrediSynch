"""Graph-linkage finding (docs/FINDINGS.md): injects synthetic fraud rings and noise applications
through the live decision API, then measures two things:

  1. Graph-attributable escalation: how often the shared-identity floor (GraphLinkageService)
     pushes a ring member's decision stricter than its fraud probability alone would have, using
     the same policy bands as the live PolicyEngine.
  2. Ring-level precision/recall of a simple connected-components detector over entity_links,
     against the known ground truth of which applications this script put in which ring.

Requires network access to the deployed API and Keycloak, and DB read access is done separately
(see README docstring below) since this script only has the HTTP surface.

    KEYCLOAK_URL=http://13.200.182.78/keycloak API_BASE_URL=http://13.200.182.78/api \
        py -3.12 findings/graph_ablation.py
"""
from __future__ import annotations

import json
import os
import time
import uuid
from pathlib import Path

import requests

KEYCLOAK_URL = os.environ.get("KEYCLOAK_URL", "http://localhost:8081")
API_BASE_URL = os.environ.get("API_BASE_URL", "http://localhost:8080/api")
OUTPUT = Path(__file__).resolve().parent / "output"

# Same bands as PolicyEngine (policy-1.0.0) - live-confirmed via GET /api/v1/policy/bands.
BANDS = [("APPROVE", 0.00250), ("STEP_UP", 0.01333), ("APPROVE_RESTRICTED", 0.48333), ("REVIEW", float("inf"))]
ACTION_ORDER = ["APPROVE", "STEP_UP", "APPROVE_RESTRICTED", "REVIEW", "DECLINE"]

RING_COUNT = 5
RING_SIZE = 4
NOISE_COUNT = 15


def get_token() -> str:
    response = requests.post(
        f"{KEYCLOAK_URL}/realms/credisynch/protocol/openid-connect/token",
        data={"grant_type": "password", "client_id": "credisynch-web",
              "username": "analyst", "password": "analyst123"})
    response.raise_for_status()
    return response.json()["access_token"]


def action_from_probability(p: float) -> str:
    for name, ceiling in BANDS:
        if p < ceiling:
            return name
    return "REVIEW"


def submit(token: str, external_ref: str, device_fingerprint: str, phone: str) -> dict:
    body = {
        "externalRef": external_ref,
        "channel": "WEB",
        "applicant": {"fullName": "Finding Probe", "email": f"{external_ref}@example.com", "phone": phone},
        "device": {"deviceFingerprint": device_fingerprint},
        "features": {"velocity_6h": 3},
    }
    response = requests.post(
        f"{API_BASE_URL}/v1/applications",
        headers={"Authorization": f"Bearer {token}", "Idempotency-Key": f"finding-{external_ref}-{int(time.time())}"},
        json=body)
    response.raise_for_status()
    return response.json()


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    token = get_token()

    ground_truth: dict[str, str] = {}  # applicationId -> ring label ("ring-N" or "noise")
    escalations = []

    for ring_index in range(RING_COUNT):
        shared_device = f"ring-device-{uuid.uuid4().hex[:8]}"
        for member in range(RING_SIZE):
            ref = f"ring{ring_index}-m{member}-{uuid.uuid4().hex[:6]}"
            decision = submit(token, ref, shared_device, f"+91{ring_index}{member}00000000"[:13])
            ground_truth[decision["applicationId"]] = f"ring-{ring_index}"
            expected = action_from_probability(decision["fraudProbability"] or 0.0)
            actual = decision["action"]
            escalated = ACTION_ORDER.index(actual) > ACTION_ORDER.index(expected) if actual in ACTION_ORDER and expected in ACTION_ORDER else False
            escalations.append({
                "applicationId": decision["applicationId"], "ring": f"ring-{ring_index}",
                "graphRisk": decision["graphRisk"], "fraudProbability": decision["fraudProbability"],
                "scoreOnlyAction": expected, "actualAction": actual, "graphEscalated": escalated,
            })

    for noise_index in range(NOISE_COUNT):
        ref = f"noise{noise_index}-{uuid.uuid4().hex[:6]}"
        device = f"noise-device-{uuid.uuid4().hex[:8]}"
        decision = submit(token, ref, device, f"+9199{noise_index:08d}"[:13])
        ground_truth[decision["applicationId"]] = "noise"

    ring_members_flagged = sum(1 for e in escalations if e["graphEscalated"])
    summary = {
        "injected": {"rings": RING_COUNT, "ring_size": RING_SIZE, "noise": NOISE_COUNT},
        "graph_attributable_escalation": {
            "ring_members_checked": len(escalations),
            "ring_members_escalated_by_graph": ring_members_flagged,
            "escalation_rate": ring_members_flagged / len(escalations) if escalations else 0.0,
        },
        "escalations": escalations,
        "ground_truth": ground_truth,
    }
    (OUTPUT / "graph_ablation_injection.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))
    print(f"\n{len(ground_truth)} applications injected. Now run graph_ablation_detect.py "
          "against the database to compute ring-detection precision/recall.")


if __name__ == "__main__":
    main()
