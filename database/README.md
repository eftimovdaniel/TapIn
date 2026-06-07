# TapIn — Database (Supabase)

Bazata e **prazna** — nema demo podatoci. Korisnicite se registriraat preku aplikacijata.

## Setup (veke napraveno ako vidish Success)

1. [supabase.com](https://supabase.com) → projekt **TapIN**
2. **SQL Editor** → kopiraj `schema.sql` → **Run**
3. **Table Editor** → proveri: `users`, `courses`, `enrollments`, `attendance_sessions`, `attendance`

## Connection za backend

Backend-ot (FastAPI + SQLAlchemy/psycopg) chita edna `DATABASE_URL` env varijabla.

**Settings → Database → Connection string → URI** (psycopg/SQLAlchemy format)

Vo `backend/.env`:
```env
DATABASE_URL=postgresql+psycopg://postgres:tvojot_password@db.xxxxx.supabase.co:5432/postgres?sslmode=require
JWT_SECRET=nekoj_dolg_random_string
# Opcionalno:
# JWT_TTL_HOURS=24
# NFC_SHARED_SECRET=TAPIN_NFC_SECRET_v1_2026   # mora da e ist so studentskata app
# PORT=8080
```

## Registracija

- **Profesor:** `role: TEACHER` preku `POST /api/auth/register`
- **Student:** `role: STUDENT` + `studentNumber` (indeks)

Posle registracija profesorot kreira predmet preku `POST /api/courses`.
