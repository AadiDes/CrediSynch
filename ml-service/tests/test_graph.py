from fastapi.testclient import TestClient

from app.graph import detect_rings
from app.main import app
from app.schemas import EntityLink, RingDetectionRequest

client = TestClient(app)


def link(app_id: str, entity_type: str, entity_hash: str) -> EntityLink:
    return EntityLink(application_id=app_id, entity_type=entity_type, entity_hash=entity_hash)


def test_four_applications_sharing_one_device_form_a_single_ring():
    links = [link(f"app-{i}", "DEVICE", "shared-device") for i in range(4)]
    result = detect_rings(RingDetectionRequest(entity_links=links, min_cluster_size=2))

    assert result.algorithm == "CONNECTED_COMPONENTS"
    assert len(result.clusters) == 1
    cluster = result.clusters[0]
    assert cluster.size == 4
    assert set(cluster.members) == {f"app-{i}" for i in range(4)}
    assert cluster.density == 1.0


def test_unrelated_applications_form_no_cluster():
    links = [link("app-a", "DEVICE", "hash-a"), link("app-b", "DEVICE", "hash-b")]
    result = detect_rings(RingDetectionRequest(entity_links=links, min_cluster_size=2))

    assert result.clusters == []


def test_min_cluster_size_filters_small_components():
    links = [link("app-a", "PHONE", "shared-phone"), link("app-b", "PHONE", "shared-phone")]
    result = detect_rings(RingDetectionRequest(entity_links=links, min_cluster_size=3))

    assert result.clusters == []


def test_chained_sharing_transitively_joins_one_cluster():
    # app-a/app-b share a device; app-b/app-c share a phone. No direct link between a and c,
    # but they belong to the same connected component through app-b.
    links = [
        link("app-a", "DEVICE", "shared-device"),
        link("app-b", "DEVICE", "shared-device"),
        link("app-b", "PHONE", "shared-phone"),
        link("app-c", "PHONE", "shared-phone"),
    ]
    result = detect_rings(RingDetectionRequest(entity_links=links, min_cluster_size=2))

    assert len(result.clusters) == 1
    assert set(result.clusters[0].members) == {"app-a", "app-b", "app-c"}


def test_two_independent_rings_are_reported_separately():
    links = [link(f"ring0-{i}", "DEVICE", "device-0") for i in range(3)]
    links += [link(f"ring1-{i}", "DEVICE", "device-1") for i in range(3)]
    result = detect_rings(RingDetectionRequest(entity_links=links, min_cluster_size=2))

    assert len(result.clusters) == 2
    assert {c.size for c in result.clusters} == {3, 3}


def test_endpoint_returns_clusters():
    body = {
        "entity_links": [
            {"application_id": "app-1", "entity_type": "DEVICE", "entity_hash": "d1"},
            {"application_id": "app-2", "entity_type": "DEVICE", "entity_hash": "d1"},
        ],
        "min_cluster_size": 2,
    }
    response = client.post("/graph/rings", json=body)
    assert response.status_code == 200
    payload = response.json()
    assert payload["algorithm"] == "CONNECTED_COMPONENTS"
    assert payload["clusters"][0]["size"] == 2


def test_endpoint_rejects_min_cluster_size_below_two():
    body = {"entity_links": [], "min_cluster_size": 1}
    response = client.post("/graph/rings", json=body)
    assert response.status_code == 422
