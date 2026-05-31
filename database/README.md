# TapIn — Database (Supabase)

Bazata e **prazna** — nema demo podatoci. Korisnicite se registriraat preku aplikacijata.

## Setup (veke napraveno ako vidish Success)

1. [supabase.com](https://supabase.com) → projekt **TapIN**
2. **SQL Editor** → kopiraj `schema.sql` → **Run**
3. **Table Editor** → proveri: `users`, `courses`, `enrollments`, `attendance_sessions`, `attendance`

## Connection za backend

**Settings → Database → Connection string → JDBC**

Vo `backend/.env`:
```env
SPRING_DATASOURCE_URL=jdbc:postgresql://db.xxxxx.supabase.co:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=tvojot_password
```

## Registracija

- **Profesor:** `role: TEACHER` preku `POST /api/auth/register`
- **Student:** `role: STUDENT` + `studentNumber` (indeks)

Posle registracija profesorot kreira predmet preku `POST /api/courses`.
