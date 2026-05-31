"""Pytest setup — koristi izolirana in-memory baza samo za testovi.

Glavnata aplikacija (db.py, models.py, routerite) ne znae za ova; tuka samo
vo testna sesija privremeno se override-uva DATABASE_URL i `get_db` dependency.
"""
from __future__ import annotations

import os
import sys
from collections.abc import Generator
from pathlib import Path

# ─── 1. Postavi izolirani test env PRED app import ───
# Fake Postgres URL — ne se konektira (SQLAlchemy e lazy, konekcija samo na query).
# Vistinskata test baza e in-memory SQLite, postavena podolu.
os.environ["DATABASE_URL"] = "postgresql+psycopg://test:test@127.0.0.1:1/testdb"
os.environ["JWT_SECRET"] = "test-secret-key-must-be-long-enough-for-testing-purposes-only-12345"
os.environ["JWT_TTL_HOURS"] = "1"
os.environ["CORS_ORIGINS"] = "http://localhost"

BACKEND_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BACKEND_DIR))

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import BigInteger, create_engine, event
from sqlalchemy.ext.compiler import compiles
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

# Kompajl-hook: produkciskite modeli koristat BIGINT (Supabase / PostgreSQL native).
# Test infrastrukturata go preveduva na INTEGER samo koga dialektot e sqlite
# (SQLite avtoinkrementira samo INTEGER PRIMARY KEY).
# Ne vlijae vrz Supabase — taa go vidi BIGINT kako shto e.
@compiles(BigInteger, "sqlite")
def _bigint_to_int(_element, _compiler, **_kw):
    return "INTEGER"


import db as db_module
import models  # noqa: F401  ── side-effect: registrira tabeli vo Base.metadata
from db import Base, get_db
from main import app


# ─── 2. Izoliran in-memory engine za testovi ───
# Vakov engine ne se dopira Supabase — celiot test flow e lokalen vo RAM.
TEST_ENGINE = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestSessionLocal = sessionmaker(bind=TEST_ENGINE, autoflush=False, autocommit=False, expire_on_commit=False)

# Override-ni gi globalnite vo db modul tako shto kodot koj direktno gi
# importira (ako ima takov) isto da koristi test engine namesto fake postgres-ot.
db_module.engine = TEST_ENGINE
db_module.SessionLocal = TestSessionLocal


@event.listens_for(TEST_ENGINE, "connect")
def _enable_fk(conn, _):
    cur = conn.cursor()
    cur.execute("PRAGMA foreign_keys=ON")
    cur.close()


@pytest.fixture(scope="session", autouse=True)
def _setup_schema():
    """Kreiraj tabeli edin pat za site testovi."""
    Base.metadata.create_all(TEST_ENGINE)
    yield
    Base.metadata.drop_all(TEST_ENGINE)


@pytest.fixture(autouse=True)
def _clean_tables():
    """Pred sekoj test izbrisi gi site redovi (no ne i tabelite)."""
    with TEST_ENGINE.begin() as conn:
        for table in reversed(Base.metadata.sorted_tables):
            conn.execute(table.delete())
    yield


@pytest.fixture
def db_session() -> Generator:
    sess = TestSessionLocal()
    try:
        yield sess
    finally:
        sess.close()


@pytest.fixture
def client() -> Generator[TestClient, None, None]:
    """FastAPI TestClient so override-iran get_db."""
    def _override_get_db():
        sess = TestSessionLocal()
        try:
            yield sess
        finally:
            sess.close()

    app.dependency_overrides[get_db] = _override_get_db
    # Sredi i globalniot SessionLocal za slucaj nekoj kod direktno go povikuva
    db_module.SessionLocal = TestSessionLocal
    db_module.engine = TEST_ENGINE

    with TestClient(app) as c:
        yield c

    app.dependency_overrides.clear()


# ─────────────────────── Helpers ───────────────────────


def register(client: TestClient, *, email: str, password: str = "test12345",
             full_name: str = "Test User", role: str = "TEACHER",
             student_number: str | None = None) -> dict:
    body = {
        "email": email, "password": password,
        "fullName": full_name, "role": role,
    }
    if student_number is not None:
        body["studentNumber"] = student_number
    resp = client.post("/api/register", json=body)
    assert resp.status_code in (200, 201), resp.text
    return resp.json()


def auth_header(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def teacher(client: TestClient) -> dict:
    """Registriran teacher + token + headers."""
    data = register(client, email="teacher@test.mk", role="TEACHER", full_name="Test Teacher")
    return {
        "token": data["token"],
        "user": data["user"],
        "headers": auth_header(data["token"]),
    }


@pytest.fixture
def student(client: TestClient) -> dict:
    """Registriran student + token + headers."""
    data = register(client, email="student@test.mk", role="STUDENT",
                    full_name="Test Student", student_number="123456")
    return {
        "token": data["token"],
        "user": data["user"],
        "headers": auth_header(data["token"]),
    }
