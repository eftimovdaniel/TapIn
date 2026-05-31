"""Tests za /api/statistics (overview + perCourse + trend)."""


def _setup_attendance(client, teacher, student):
    """Kreiraj 1 kurs, 1 sesija, 1 tap."""
    course = client.post("/api/courses", json={"code": "STAT1", "name": "Statistika"},
                         headers=teacher["headers"]).json()
    session = client.post("/api/sessions", json={"courseId": course["id"]},
                          headers=teacher["headers"]).json()
    client.post("/api/attendance", json={
        "sessionId": session["id"],
        "records": [{"studentId": student["user"]["id"]}],
    }, headers=teacher["headers"])
    return course, session


def test_statistics_aggregate_shape(client, teacher, student):
    _setup_attendance(client, teacher, student)
    resp = client.get("/api/statistics", headers=teacher["headers"])
    assert resp.status_code == 200
    body = resp.json()
    assert set(body.keys()) == {"overview", "perCourse", "trend"}
    assert set(body["overview"].keys()) == {
        "todayCount", "weekCount", "monthCount",
        "activeSessions", "totalStudents", "totalCourses",
    }


def test_statistics_overview_counts(client, teacher, student):
    _setup_attendance(client, teacher, student)
    o = client.get("/api/statistics/overview", headers=teacher["headers"]).json()
    assert o["todayCount"] == 1
    assert o["weekCount"] >= 1
    assert o["monthCount"] >= 1
    assert o["activeSessions"] == 1
    assert o["totalStudents"] == 1
    assert o["totalCourses"] == 1


def test_statistics_per_course_returns_rate(client, teacher, student):
    course, _ = _setup_attendance(client, teacher, student)
    rows = client.get("/api/statistics/per-course",
                      headers=teacher["headers"]).json()
    assert len(rows) >= 1
    row = next(r for r in rows if r["courseId"] == course["id"])
    assert row["attended"] == 1
    assert row["enrolled"] == 0  # Bez upisi → 0
    assert row["rate"] == 0.0


def test_statistics_trend_includes_today(client, teacher, student):
    _setup_attendance(client, teacher, student)
    rows = client.get("/api/statistics/trend?days=7",
                      headers=teacher["headers"]).json()
    assert len(rows) >= 1
    assert sum(r["count"] for r in rows) == 1


def test_statistics_per_student_returns_rate(client, teacher, student):
    _setup_attendance(client, teacher, student)
    rows = client.get("/api/statistics/per-student",
                      headers=teacher["headers"]).json()
    assert isinstance(rows, list)
    me = next((r for r in rows if r["studentId"] == student["user"]["id"]), None)
    assert me is not None
    assert me["studentName"] == student["user"]["fullName"]
    assert me["attended"] == 1
    assert me["totalSessions"] >= 1
    assert 0.0 <= me["rate"] <= 1.0


def test_statistics_per_student_filter_by_course(client, teacher, student):
    course, _ = _setup_attendance(client, teacher, student)
    rows = client.get(f"/api/statistics/per-student?courseId={course['id']}",
                      headers=teacher["headers"]).json()
    assert any(r["studentId"] == student["user"]["id"] for r in rows)


def test_statistics_requires_auth(client):
    assert client.get("/api/statistics").status_code == 401
    assert client.get("/api/statistics/overview").status_code == 401
    assert client.get("/api/statistics/trend").status_code == 401
    assert client.get("/api/statistics/per-student").status_code == 401
