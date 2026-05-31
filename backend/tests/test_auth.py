"""Tests za /api/register, /api/login, /api/auth/me."""
from tests.conftest import auth_header, register


# ── REGISTER ──────────────────────────────────────────────


def test_register_teacher_creates_user(client):
    resp = client.post("/api/register", json={
        "email": "prof@finki.mk", "password": "secret123",
        "fullName": "Prof Marko", "role": "TEACHER",
    })
    assert resp.status_code == 201
    body = resp.json()
    assert body["user"]["email"] == "prof@finki.mk"
    assert body["user"]["role"] == "TEACHER"
    assert body["user"]["fullName"] == "Prof Marko"
    assert body["token"]
    assert body["expiresAt"]


def test_register_student_requires_student_number(client):
    resp = client.post("/api/register", json={
        "email": "s@finki.mk", "password": "secret123",
        "fullName": "Student", "role": "STUDENT",
    })
    assert resp.status_code == 400
    # Detail e na kirilica sega — proverka samo na statusot
    assert resp.json()["detail"]


def test_register_student_with_number_ok(client):
    resp = client.post("/api/register", json={
        "email": "s@finki.mk", "password": "secret123",
        "fullName": "Student Stoyan", "role": "STUDENT",
        "studentNumber": "193001",
    })
    assert resp.status_code == 201
    assert resp.json()["user"]["studentNumber"] == "193001"


def test_register_admin_role_rejected(client):
    resp = client.post("/api/register", json={
        "email": "a@finki.mk", "password": "secret123",
        "fullName": "Admin", "role": "ADMIN",
    })
    assert resp.status_code == 400


def test_register_duplicate_email_409(client):
    register(client, email="dup@test.mk")
    resp = client.post("/api/register", json={
        "email": "dup@test.mk", "password": "secret123",
        "fullName": "Other", "role": "TEACHER",
    })
    assert resp.status_code == 409


def test_register_short_password_422(client):
    resp = client.post("/api/register", json={
        "email": "a@b.mk", "password": "12", "fullName": "Short", "role": "TEACHER",
    })
    assert resp.status_code == 422


# ── LOGIN ─────────────────────────────────────────────────


def test_login_success_returns_jwt(client):
    register(client, email="user@test.mk", password="hello123")
    resp = client.post("/api/login", json={
        "email": "user@test.mk", "password": "hello123",
    })
    assert resp.status_code == 200
    body = resp.json()
    assert body["token"].count(".") == 2  # JWT = header.payload.sig
    assert body["user"]["email"] == "user@test.mk"


def test_login_wrong_password_401(client):
    register(client, email="user@test.mk", password="correctpass")
    resp = client.post("/api/login", json={
        "email": "user@test.mk", "password": "wrongpass",
    })
    assert resp.status_code == 401


def test_login_unknown_email_401(client):
    resp = client.post("/api/login", json={
        "email": "ghost@finki.mk", "password": "anything",
    })
    assert resp.status_code == 401


# ── /api/auth/me ──────────────────────────────────────────


def test_me_with_valid_token(client, teacher):
    resp = client.get("/api/auth/me", headers=teacher["headers"])
    assert resp.status_code == 200
    assert resp.json()["email"] == teacher["user"]["email"]


def test_me_without_token_401(client):
    resp = client.get("/api/auth/me")
    assert resp.status_code == 401


def test_me_with_invalid_token_401(client):
    resp = client.get("/api/auth/me", headers=auth_header("not-a-valid-token"))
    assert resp.status_code == 401


# ── student lookup po broj (NFC tap) ──────────────────────


def test_lookup_student_by_number(client, teacher, student):
    resp = client.get(
        f"/api/users/by-number/{student['user']['studentNumber']}",
        headers=teacher["headers"],
    )
    assert resp.status_code == 200
    assert resp.json()["id"] == student["user"]["id"]


def test_lookup_unknown_student_404(client, teacher):
    resp = client.get("/api/users/by-number/000000", headers=teacher["headers"])
    assert resp.status_code == 404


def test_lookup_requires_auth(client):
    resp = client.get("/api/users/by-number/193001")
    assert resp.status_code == 401
