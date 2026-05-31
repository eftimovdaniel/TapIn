"""Demo seed — sozdava realisticno demo demoто za prezentacija.

Bara da se startuva ВО pokrenat backend (на http://localhost:8080).

Krenuva:
  - 1 profesor (prof.demo@ugd.edu.mk / demo1234)
  - 2 predmeti (Програмирање 1, Бази на податоци)
  - 8 studenti (numbers 200001..200008)
  - 4 sесии (po 2 za sekoj predmet, vчepacher i denes)
  - ~25 random tap-a (so некои studenti redovni а некои neredovni)

Koristejе:
  cd backend
  python seed_demo.py                       # local backend, default URL
  TAPIN_API=http://192.168.0.106:8080 python seed_demo.py   # device on LAN
"""
from __future__ import annotations

import os
import random
import sys
import time
from datetime import datetime, timedelta, timezone

import httpx

API = os.getenv("TAPIN_API", "http://localhost:8080")
TIMEOUT = 8.0


# ── Demo dataset ─────────────────────────────────────────

PROF = {
    "email": "prof.demo@ugd.edu.mk",
    "password": "demo1234",
    "fullName": "Проф. Демо",
    "role": "TEACHER",
}

STUDENTS = [
    ("Ана Стојаноска",       "200001"),
    ("Бојан Илиев",          "200002"),
    ("Велика Митева",        "200003"),
    ("Дарко Јовановски",     "200004"),
    ("Елена Петровска",      "200005"),
    ("Филип Костадинов",     "200006"),
    ("Гоце Стефановски",     "200007"),
    ("Хана Димитрова",       "200008"),
]

COURSES = [
    ("PI1", "Програмирање 1"),
    ("BP1", "Бази на податоци"),
]


# ── HTTP helpers ─────────────────────────────────────────


def _post(client: httpx.Client, path: str, json: dict, headers: dict | None = None) -> dict:
    r = client.post(API + path, json=json, headers=headers, timeout=TIMEOUT)
    if r.status_code >= 400:
        raise RuntimeError(f"POST {path} → {r.status_code}: {r.text[:200]}")
    if r.status_code == 204 or not r.content:
        return {}
    if not r.headers.get("content-type", "").startswith("application/json"):
        return {}
    return r.json()


def _get(client: httpx.Client, path: str, headers: dict | None = None) -> dict | list:
    r = client.get(API + path, headers=headers, timeout=TIMEOUT)
    if r.status_code >= 400:
        raise RuntimeError(f"GET {path} → {r.status_code}: {r.text[:200]}")
    return r.json()


def _login_or_register(client: httpx.Client, body: dict) -> tuple[dict, dict]:
    """Probaj login, ako ne pomine — registry."""
    try:
        r = client.post(API + "/api/login", json={
            "email": body["email"], "password": body["password"]
        }, timeout=TIMEOUT)
        if r.status_code == 200:
            return r.json(), {"Authorization": "Bearer " + r.json()["token"]}
    except Exception:
        pass
    auth = _post(client, "/api/register", body)
    return auth, {"Authorization": "Bearer " + auth["token"]}


# ── Seed ─────────────────────────────────────────────────


def main() -> int:
    print(f"→ Konектира na backend: {API}")

    with httpx.Client() as client:
        # 0) Health check
        try:
            client.get(API + "/api/auth/me", timeout=3.0)
        except Exception as e:
            print(f"❌ Backend ne odgovara na {API}. ({e})")
            return 1

        # 1) Profesor
        prof_auth, prof_h = _login_or_register(client, PROF)
        prof_id = prof_auth["user"]["id"]
        print(f"✓ Profesor: {prof_auth['user']['fullName']} (id={prof_id})")

        # 2) Studенти
        student_ids: list[int] = []
        for name, num in STUDENTS:
            body = {
                "email": f"s{num}@ugd.edu.mk",
                "password": "student123",
                "fullName": name,
                "role": "STUDENT",
                "studentNumber": num,
            }
            auth, _ = _login_or_register(client, body)
            student_ids.append(auth["user"]["id"])
        print(f"✓ Studенти: {len(student_ids)}")

        # 3) Predmeti (toleriraj duplikati)
        course_ids: list[int] = []
        for code, name in COURSES:
            try:
                c = _post(client, "/api/courses", {"code": code, "name": name}, prof_h)
                course_ids.append(c["id"])
            except RuntimeError as e:
                if "409" in str(e):
                    courses = _get(client, "/api/courses", prof_h)
                    found = next(c for c in courses if c["code"] == code)
                    course_ids.append(found["id"])
                else:
                    raise
        print(f"✓ Predmeti: {len(course_ids)}")

        # 4) Po 2 sesии za sekoj predmet, prvata ZAVRSHENA, vtorata AKTIVNA
        all_sessions: list[tuple[int, int, bool]] = []  # (id, courseId, active)
        for cid in course_ids:
            # vчera/denes — startuvame i odmah zavrshuvame prvata
            s1 = _post(client, "/api/sessions", {"courseId": cid}, prof_h)
            _post(client, f"/api/sessions/{s1['id']}/close", {}, prof_h)
            all_sessions.append((s1["id"], cid, False))

            s2 = _post(client, "/api/sessions", {"courseId": cid}, prof_h)
            all_sessions.append((s2["id"], cid, True))
        print(f"✓ Sesии: {len(all_sessions)}")

        # 5) Random taps — некои studenti redovni, некои retko
        rng = random.Random(7)
        attendance_ratios = [0.95, 0.90, 0.80, 0.70, 0.55, 0.45, 0.30, 0.15]
        rng.shuffle(attendance_ratios)
        student_with_ratio = list(zip(student_ids, attendance_ratios))

        total_taps = 0
        now = datetime.now(timezone.utc)
        for i, (sess_id, _cid, _active) in enumerate(all_sessions):
            taps = []
            # razno mало vremiya za taps — vo pos rod  na sesijata
            for sid, ratio in student_with_ratio:
                if rng.random() <= ratio:
                    offset_minutes = rng.randint(0, 9)
                    ts = now - timedelta(hours=24 * (len(all_sessions) - i),
                                          minutes=-offset_minutes)
                    taps.append({"studentId": sid, "tappedAt": ts.isoformat()})
            if not taps:
                continue
            resp = _post(client, "/api/attendance",
                         {"sessionId": sess_id, "records": taps}, prof_h)
            total_taps += resp.get("accepted", 0)
        print(f"✓ Tapovi: {total_taps}")

        print()
        print("════════════════════════════════════════════")
        print(" Demo dataset E POSTAVEN ✓")
        print("════════════════════════════════════════════")
        print(f"  Profesor:  prof.demo@ugd.edu.mk / demo1234")
        print(f"  Studenti:  s200001..s200008@ugd.edu.mk / student123")
        print(f"  Sesии:    {len(all_sessions)}  (1 aktivna po predmet)")
        print(f"  Tapоvi:   {total_taps}")
        print()
        print("→ Otvori dashboard: http://localhost:8000")
        print("→ Najavi se kako profesor i provedi prezentacija!")
        return 0


if __name__ == "__main__":
    sys.exit(main())
