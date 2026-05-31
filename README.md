# TapIn — Classroom Presence System

Studentot ja dopira zadnata strana na profesoroviot telefon (NFC) → atendansa avtomatski se zapishuva vo bazata.

## Proekt struktura

```
Mobilniaplikacii-grupna/
├── database/         ← Supabase PostgreSQL schema
├── backend/          ← Python / FastAPI REST API
├── teacher-app/      ← Android (Kotlin + Compose) — chita NFC tap
├── student-app/      ← Android (Kotlin + Compose) — emulira NFC kartichka (HCE)
└── web-dashboard/    ← Web Dashboard (HTML + Tailwind + Chart.js)
```

## Status

- [x] Baza (Supabase schema)
- [x] Backend (FastAPI: login, register, courses, sessions, attendance, statistics)
- [x] Teacher Android app — Auth + Predmeti + Sesija + NFC reader
- [x] Student Android app — Auth + HCE (Host Card Emulation)
- [x] Web dashboard — login, stats kartichki, grafikoni, tabela so filteri, CSV izvoz
- [x] **36 backend testovi** (pytest + httpx, in-memory SQLite)
- [x] **Docker compose** — backend + dashboard (+ optional lokalna Postgres)

## Tech stack

| Del | Tehnologija |
|-----|-------------|
| Baza | PostgreSQL (Supabase) |
| Backend | Python 3, FastAPI, SQLAlchemy 2, Pydantic 2 |
| Auth | JWT (HS256), BCrypt |
| Mobile | Kotlin, Jetpack Compose, Ktor (HTTP), DataStore |
| NFC | HCE (student emit) + ReaderMode (teacher read) |
| Dashboard | HTML + TailwindCSS + Chart.js + vanilla JS (no build step) |

## Kako raboti NFC

```
┌──────────────┐     SELECT AID F0544150494E3031      ┌──────────────┐
│ TEACHER APP  │ ───────────────────────────────────► │ STUDENT APP  │
│  (reader)    │                                       │   (HCE)      │
│              │ ◄─────────────────────────────────── │              │
│              │     "193001" + 9000 (success)         │              │
└──────┬───────┘                                       └──────────────┘
       │
       │ POST /api/users/by-number/193001
       │ POST /api/attendance { sessionId, [193001] }
       ▼
┌──────────────┐
│   BACKEND    │
│  (FastAPI)   │
└──────────────┘
```

- AID: `F0 54 41 50 49 4E 30 31` (custom — ne kolidira so payment kartichki)
- Student app raboti dur i koga e zaklucen (`requireDeviceUnlock=false`)

## Brz start

### 1. Backend

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env  # popolni Supabase URL + JWT_SECRET
uvicorn main:app --host 0.0.0.0 --port 8080
```

- API: http://localhost:8080
- Swagger: http://localhost:8080/docs

### 2. Baza (Supabase)

Kopiraj `database/schema.sql` vo Supabase SQL editor. Vidi `database/README.md`.

### 3. Mobilni aplikacii

```bash
# Teacher
cd teacher-app && ./gradlew assembleDebug
# APK: teacher-app/app/build/outputs/apk/debug/app-debug.apk

# Student
cd student-app && ./gradlew assembleDebug
# APK: student-app/app/build/outputs/apk/debug/app-debug.apk
```

### 4. Web Dashboard

```bash
cd web-dashboard
python3 -m http.server 8000
# Otvori: http://localhost:8000
```

Login so postoechki teacher akaunt (npr. `prof.fastapi@finki.mk` / `test12345`).

> Emulator: `10.0.2.2` se mapira na host `localhost`.
> Realen telefon: vo `ApiClient.kt` zameni `BASE_URL` so LAN IP (npr. `http://192.168.1.42:8080`).

## Spec endpoints

| Metod | Path | Opis |
|-------|------|------|
| POST | `/api/login` | Login (teacher + student) |
| POST | `/api/register` | Registracija |
| POST | `/api/attendance` | Sync atendansi od teacher app |
| GET | `/api/attendance` | Lista (filteri: studentId, courseId, sessionId, from, to) |
| GET | `/api/statistics` | Agregat (overview + per-course + trend) |
| GET | `/api/users/by-number/{n}` | Lookup za NFC tap |

## Testiranje

```bash
cd backend
source .venv/bin/activate
pip install -r requirements-dev.txt
pytest
```

Pokriva: register/login/me, courses CRUD, sessions, attendance sync + filter +
pagination, statistics (overview/per-course/trend), 401/403/404/409 ednoznachnost.

```
36 passed in 1.97s
```

Testovite koristat in-memory SQLite — ne ti treba Supabase za da gi pokrenesh.

## Docker (eden komand za celiot stack)

**Default — koristi Supabase** (popolni `backend/.env` prvo):

```bash
docker compose up --build
```

- Backend: <http://localhost:8080> (Swagger: `/docs`)
- Dashboard: <http://localhost:8000> (nginx proksira `/api/*` do backend)

**So lokalna Postgres** (bez Supabase, kontejnerizirana baza):

```bash
docker compose --profile local-db up --build
```

Postgres se inicijalizira avtomatski so `database/schema.sql`.
Vo `backend/.env` postavi `DATABASE_URL=postgresql+psycopg://tapin:tapin_dev@postgres:5432/tapin`.

Stop: `docker compose down`. Vo cista sostojba: `docker compose down -v`.
