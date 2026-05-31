"""Tests za celosen flow: course → session → tap → list/filter."""


def _create_course(client, teacher, code="C1", name="Course 1") -> dict:
    resp = client.post("/api/courses", json={"code": code, "name": name},
                       headers=teacher["headers"])
    assert resp.status_code == 201, resp.text
    return resp.json()


def _start_session(client, teacher, course_id: int) -> dict:
    resp = client.post("/api/sessions", json={"courseId": course_id},
                       headers=teacher["headers"])
    assert resp.status_code == 201, resp.text
    return resp.json()


# ── Sessions ─────────────────────────────────────────────


def test_start_session_for_own_course(client, teacher):
    course = _create_course(client, teacher)
    s = _start_session(client, teacher, course["id"])
    assert s["active"] is True
    assert s["courseId"] == course["id"]
    assert s["endedAt"] is None


def test_start_session_for_unknown_course_404(client, teacher):
    resp = client.post("/api/sessions", json={"courseId": 9999},
                       headers=teacher["headers"])
    assert resp.status_code == 404


def test_close_session(client, teacher):
    course = _create_course(client, teacher)
    s = _start_session(client, teacher, course["id"])
    resp = client.post(f"/api/sessions/{s['id']}/close",
                       headers=teacher["headers"])
    assert resp.status_code == 204

    listed = client.get("/api/sessions",
                        headers=teacher["headers"]).json()
    assert listed[0]["active"] is False
    assert listed[0]["endedAt"] is not None


# ── Attendance upload (POST /api/attendance) ──────────────


def test_upload_attendance_records_tap(client, teacher, student):
    course = _create_course(client, teacher)
    s = _start_session(client, teacher, course["id"])

    resp = client.post("/api/attendance", json={
        "sessionId": s["id"],
        "records": [{"studentId": student["user"]["id"]}],
    }, headers=teacher["headers"])

    assert resp.status_code == 200
    body = resp.json()
    assert body["accepted"] == 1
    assert body["duplicates"] == 0
    assert body["rejected"] == 0


def test_upload_attendance_dedupes_same_student(client, teacher, student):
    course = _create_course(client, teacher)
    s = _start_session(client, teacher, course["id"])
    payload = {"sessionId": s["id"],
               "records": [{"studentId": student["user"]["id"]}]}

    first = client.post("/api/attendance", json=payload,
                        headers=teacher["headers"]).json()
    second = client.post("/api/attendance", json=payload,
                         headers=teacher["headers"]).json()

    assert first["accepted"] == 1
    assert second["duplicates"] == 1
    assert second["accepted"] == 0


def test_upload_attendance_unknown_student_rejected(client, teacher):
    course = _create_course(client, teacher)
    s = _start_session(client, teacher, course["id"])

    resp = client.post("/api/attendance", json={
        "sessionId": s["id"],
        "records": [{"studentId": 99999}],
    }, headers=teacher["headers"])

    body = resp.json()
    assert body["rejected"] == 1
    assert body["accepted"] == 0


# ── Sigurnoсni potpishani NFC payloads ─────────────────────


def _signed(student_number: str, ts: int | None = None) -> str:
    import hashlib
    import hmac
    import time
    from config import get_settings
    if ts is None:
        ts = int(time.time())
    msg = f"{student_number}:{ts}"
    sig = hmac.new(
        get_settings().nfc_shared_secret.encode("utf-8"),
        msg.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()[:16]
    return f"{student_number}|{ts}|{sig}"


def test_upload_attendance_with_signed_payload(client, teacher, student):
    """End-to-end: HMAC-potpishan payload od HCE → backend validira i zapishuva."""
    course = _create_course(client, teacher)
    s = _start_session(client, teacher, course["id"])

    payload = _signed(student["user"]["studentNumber"])
    resp = client.post("/api/attendance", json={
        "sessionId": s["id"],
        "records": [{"signedPayload": payload}],
    }, headers=teacher["headers"])
    body = resp.json()

    assert body["accepted"] == 1
    assert body["invalidSignatures"] == 0
    # Treba zapis za toj student so vistinskoto име
    listing = client.get("/api/attendance", headers=teacher["headers"]).json()
    assert listing["totalElements"] == 1
    assert listing["items"][0]["studentName"] == student["user"]["fullName"]


def test_upload_attendance_with_invalid_signature(client, teacher, student):
    course = _create_course(client, teacher)
    s = _start_session(client, teacher, course["id"])

    bad = f"{student['user']['studentNumber']}|9999999999|0000000000000000"
    resp = client.post("/api/attendance", json={
        "sessionId": s["id"],
        "records": [{"signedPayload": bad}],
    }, headers=teacher["headers"])
    body = resp.json()
    assert body["accepted"] == 0
    assert body["invalidSignatures"] == 1


def test_upload_attendance_with_replay_old_payload(client, teacher, student):
    """Stari payload-i (extra 1h) ne smejat da pominat — zashtiта od replay."""
    import time
    old_ts = int(time.time()) - 3600
    payload = _signed(student["user"]["studentNumber"], ts=old_ts)
    course = _create_course(client, teacher)
    s = _start_session(client, teacher, course["id"])

    resp = client.post("/api/attendance", json={
        "sessionId": s["id"],
        "records": [{"signedPayload": payload}],
    }, headers=teacher["headers"])
    body = resp.json()
    assert body["accepted"] == 0
    assert body["invalidSignatures"] == 1


# ── Attendance list (GET /api/attendance) ─────────────────


def test_list_attendance_returns_recorded(client, teacher, student):
    course = _create_course(client, teacher)
    s = _start_session(client, teacher, course["id"])
    client.post("/api/attendance", json={
        "sessionId": s["id"],
        "records": [{"studentId": student["user"]["id"]}],
    }, headers=teacher["headers"])

    resp = client.get("/api/attendance", headers=teacher["headers"])
    assert resp.status_code == 200
    page = resp.json()
    assert page["totalElements"] == 1
    item = page["items"][0]
    assert item["studentName"] == student["user"]["fullName"]
    assert item["studentNumber"] == student["user"]["studentNumber"]
    assert item["courseName"] == course["name"]


def test_list_attendance_filter_by_session(client, teacher, student):
    c1 = _create_course(client, teacher, code="C-A", name="Alfa")
    c2 = _create_course(client, teacher, code="C-B", name="Beta")
    s1 = _start_session(client, teacher, c1["id"])
    s2 = _start_session(client, teacher, c2["id"])
    sid = student["user"]["id"]
    client.post("/api/attendance", json={"sessionId": s1["id"], "records": [{"studentId": sid}]},
                headers=teacher["headers"])
    client.post("/api/attendance", json={"sessionId": s2["id"], "records": [{"studentId": sid}]},
                headers=teacher["headers"])

    only_s1 = client.get(f"/api/attendance?sessionId={s1['id']}",
                         headers=teacher["headers"]).json()
    assert only_s1["totalElements"] == 1
    assert only_s1["items"][0]["sessionId"] == s1["id"]


def test_list_attendance_pagination(client, teacher):
    """Kreiraj 5 razlichni studenti i 1 session, testiraj paginate(size=2)."""
    course = _create_course(client, teacher)
    s = _start_session(client, teacher, course["id"])

    student_ids = []
    for i in range(5):
        r = client.post("/api/register", json={
            "email": f"s{i}@test.mk", "password": "test12345",
            "fullName": f"Student {i}", "role": "STUDENT",
            "studentNumber": f"S{1000 + i}",
        })
        student_ids.append(r.json()["user"]["id"])

    client.post("/api/attendance", json={
        "sessionId": s["id"],
        "records": [{"studentId": sid} for sid in student_ids],
    }, headers=teacher["headers"])

    page0 = client.get("/api/attendance?page=0&size=2",
                       headers=teacher["headers"]).json()
    assert page0["totalElements"] == 5
    assert len(page0["items"]) == 2

    page1 = client.get("/api/attendance?page=2&size=2",
                       headers=teacher["headers"]).json()
    assert len(page1["items"]) == 1
