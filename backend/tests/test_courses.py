"""Tests za /api/courses (list, create)."""


def test_create_course_as_teacher(client, teacher):
    resp = client.post("/api/courses", json={
        "code": "FCSE-MA101", "name": "Mobilni Aplikacii",
    }, headers=teacher["headers"])
    assert resp.status_code == 201
    body = resp.json()
    assert body["code"] == "FCSE-MA101"
    assert body["teacherId"] == teacher["user"]["id"]


def test_create_course_requires_auth(client):
    resp = client.post("/api/courses", json={"code": "X", "name": "Y"})
    assert resp.status_code == 401


def test_create_course_rejects_student(client, student):
    resp = client.post("/api/courses", json={
        "code": "X1", "name": "Test",
    }, headers=student["headers"])
    assert resp.status_code == 403


def test_duplicate_course_code_409(client, teacher):
    client.post("/api/courses", json={"code": "DUP1", "name": "First"},
                headers=teacher["headers"])
    resp = client.post("/api/courses", json={
        "code": "DUP1", "name": "Second",
    }, headers=teacher["headers"])
    assert resp.status_code == 409


def test_list_courses_only_own(client, teacher):
    client.post("/api/courses", json={"code": "MINE1", "name": "Moj"},
                headers=teacher["headers"])
    client.post("/api/courses", json={"code": "MINE2", "name": "Vtor"},
                headers=teacher["headers"])
    resp = client.get("/api/courses", headers=teacher["headers"])
    assert resp.status_code == 200
    codes = {c["code"] for c in resp.json()}
    assert codes == {"MINE1", "MINE2"}
