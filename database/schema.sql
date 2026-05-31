-- TapIn — PostgreSQL schema (Supabase compatible)
-- Run this FIRST in Supabase SQL Editor.

BEGIN;

-- ─────────────────────────────────────────────────────────
-- USERS: teachers, students, admin (one table, role-based)
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(160) NOT NULL UNIQUE,
    password_hash   VARCHAR(72)  NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    role            VARCHAR(16)  NOT NULL CHECK (role IN ('ADMIN','TEACHER','STUDENT')),
    student_number  VARCHAR(20)  UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS users_role_idx ON users(role);

-- ─────────────────────────────────────────────────────────
-- COURSES: each course owned by a teacher
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS courses (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(160) NOT NULL,
    teacher_id  BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────────────────
-- ENROLLMENTS: which student is in which course
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS enrollments (
    student_id  BIGINT NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    course_id   BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    PRIMARY KEY (student_id, course_id)
);

-- ─────────────────────────────────────────────────────────
-- ATTENDANCE SESSIONS: a class meeting in time
-- Started by the teacher's app, ended manually or auto.
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS attendance_sessions (
    id           BIGSERIAL PRIMARY KEY,
    course_id    BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    teacher_id   BIGINT      NOT NULL REFERENCES users(id)   ON DELETE RESTRICT,
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS sessions_course_idx ON attendance_sessions(course_id);

-- ─────────────────────────────────────────────────────────
-- ATTENDANCE: one row per NFC tap
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS attendance (
    id           BIGSERIAL PRIMARY KEY,
    session_id   BIGINT      NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    student_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    tapped_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, student_id)   -- prevents double-tap in same session
);

CREATE INDEX IF NOT EXISTS attendance_student_idx ON attendance(student_id);
CREATE INDEX IF NOT EXISTS attendance_session_idx ON attendance(session_id);

COMMIT;
