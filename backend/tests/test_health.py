"""Smoke testovi — server se podiga, health endpoint odgovara."""


def test_health_returns_up(client):
    resp = client.get("/actuator/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "UP"}


def test_swagger_docs_available(client):
    resp = client.get("/docs")
    assert resp.status_code == 200
    assert "TapIn API" in resp.text
