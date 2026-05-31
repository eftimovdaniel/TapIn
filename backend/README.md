# TapIn Backend (FastAPI / Python)

REST API za teacher app, student app i web dashboard.

## Brz start

```bash
cd backend

# 1. Virtuelno okruzenje
python3 -m venv .venv
source .venv/bin/activate

# 2. Instaliraj dependencies
pip install -r requirements.txt

# 3. Kopiraj .env.example -> .env i popolni Supabase URL
cp .env.example .env

# 4. Startuvaj
uvicorn main:app --host 0.0.0.0 --port 8080 --reload
```

- API:      http://localhost:8080
- Swagger:  http://localhost:8080/docs
- ReDoc:    http://localhost:8080/redoc
- Health:   http://localhost:8080/actuator/health

## Spec endpoints (Section 3.3)

| Method | Path | Opis |
|--------|------|------|
| POST | `/api/login` | Login (teacher + student) |
| POST | `/api/attendance` | Sync atendansi od teacher app |
| GET | `/api/attendance` | Lista atendansi (filteri: `studentId`, `teacherId`, `courseId`, `sessionId`, `from`, `to`) |
| GET | `/api/statistics` | Agregat (overview + per-course + trend) |

## Plus

| Method | Path |
|--------|------|
| POST | `/api/register` |
| GET | `/api/auth/me` |
| GET | `/api/courses` |
| POST | `/api/courses` |
| POST | `/api/sessions` |
| POST | `/api/sessions/{id}/close` |
| GET | `/api/sessions` |

## Tehnologii

- **FastAPI** — moderniot Python web framework
- **SQLAlchemy 2.0** — ORM
- **psycopg 3** — PostgreSQL driver
- **Pydantic 2** — validacija na request body
- **bcrypt** — password hashing
- **python-jose** — JWT
- **pytest + httpx** — testovi (in-memory SQLite)

## Folder struktura

```
backend/
├── requirements.txt
├── requirements-dev.txt ← + pytest, httpx
├── pytest.ini
├── Dockerfile           ← multi-stage Python 3.12 slim
├── .env                 ← Supabase password (skrieno od git)
├── main.py              ← FastAPI app + CORS + health
├── config.py            ← Settings (chita .env)
├── db.py                ← SQLAlchemy engine + sesija
├── models.py            ← Tabeli (User, Course, Session, ...)
├── schemas.py           ← Pydantic DTOs
├── security.py          ← BCrypt + JWT
├── deps.py              ← Auth dependency (Bearer token)
├── routers/
│   ├── auth.py
│   ├── courses.py
│   ├── sessions.py
│   ├── attendance.py
│   └── statistics.py
└── tests/
    ├── conftest.py            ← in-memory SQLite + fixtures
    ├── test_health.py
    ├── test_auth.py           ← 14 testovi
    ├── test_courses.py        ← 5 testovi
    ├── test_attendance_flow.py ← 10 testovi
    └── test_statistics.py     ← 5 testovi
```

## Testovi

```bash
pip install -r requirements-dev.txt
pytest
```

```
36 passed in 1.97s
```

Testovite koristat **in-memory SQLite** preku `dependency_overrides`, taka shto:
- ne ti treba Supabase
- sekoj test pochnuva so prazna baza (avtoclean fixture)
- ramen rok < 2 s za site 36 testovi

## Docker

```bash
# Build samo backend (od backend/ folder)
docker build -t tapin-backend .
docker run --rm -p 8080:8080 --env-file .env tapin-backend

# Ili zaedno so dashboard preku compose (od korenot)
cd .. && docker compose up --build
```

## Test (curl)

```bash
# Registracija
curl -X POST http://localhost:8080/api/register \
  -H "Content-Type: application/json" \
  -d '{"email":"prof@finki.mk","password":"test12345","fullName":"Prof. Marko","role":"TEACHER"}'

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"email":"prof@finki.mk","password":"test12345"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# Statistiki
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/statistics
```
