"""Statistics — GET /api/statistics (agregat) + sub-endpoints."""
from datetime import date, datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select, text
from sqlalchemy.orm import Session

from db import get_db
from deps import CurrentUser, require_teacher
from models import Attendance, AttendanceSession, Course, User
from schemas import CourseStat, Overview, StatisticsAggregate, TrendPoint

router = APIRouter(prefix="/api/statistics", tags=["Statistics"])


def _overview(db: Session) -> Overview:
    now = datetime.now(timezone.utc)
    start_today = datetime(now.year, now.month, now.day, tzinfo=timezone.utc)
    start_week = start_today - timedelta(days=7)
    start_month = start_today - timedelta(days=30)

    def count_between(a: datetime, b: datetime) -> int:
        return db.scalar(
            select(func.count())
            .select_from(Attendance)
            .where(Attendance.tapped_at >= a, Attendance.tapped_at < b)
        ) or 0

    today = count_between(start_today, now + timedelta(days=1))
    week = count_between(start_week, now)
    month = count_between(start_month, now)
    active = db.scalar(
        select(func.count()).select_from(AttendanceSession).where(AttendanceSession.ended_at.is_(None))
    ) or 0
    students = db.scalar(select(func.count()).select_from(User).where(User.role == "STUDENT")) or 0
    courses = db.scalar(select(func.count()).select_from(Course)) or 0

    return Overview(
        todayCount=today, weekCount=week, monthCount=month,
        activeSessions=active, totalStudents=students, totalCourses=courses,
    )


def _per_course(db: Session) -> list[CourseStat]:
    sql = text("""
        select c.id, c.name,
               count(distinct a.student_id) as attended,
               (select count(*) from enrollments e where e.course_id = c.id) as enrolled
        from courses c
        left join attendance_sessions s on s.course_id = c.id
        left join attendance a          on a.session_id = s.id
        group by c.id, c.name
        order by c.name
    """)
    out: list[CourseStat] = []
    for row in db.execute(sql).all():
        cid, name, attended, enrolled = row
        rate = (attended / enrolled) if enrolled else 0.0
        out.append(CourseStat(courseId=cid, courseName=name, attended=int(attended), enrolled=int(enrolled), rate=rate))
    return out


def _trend(db: Session, days: int) -> list[TrendPoint]:
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    sql = text("""
        select date_trunc('day', tapped_at)::date as day, count(*) as cnt
        from attendance
        where tapped_at >= :cutoff
        group by 1 order by 1
    """)
    out: list[TrendPoint] = []
    for row in db.execute(sql, {"cutoff": cutoff}).all():
        day, cnt = row
        d = day if isinstance(day, date) else date.fromisoformat(str(day))
        out.append(TrendPoint(date=d, count=int(cnt)))
    return out


# ─── GET /api/statistics — spec endpoint ───
@router.get("", response_model=StatisticsAggregate)
def aggregate(
    days: int = Query(default=30, ge=1, le=365),
    _: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> StatisticsAggregate:
    return StatisticsAggregate(
        overview=_overview(db),
        perCourse=_per_course(db),
        trend=_trend(db, days),
    )


@router.get("/overview", response_model=Overview)
def overview(_: CurrentUser = Depends(require_teacher), db: Session = Depends(get_db)) -> Overview:
    return _overview(db)


@router.get("/per-course", response_model=list[CourseStat])
def per_course(_: CurrentUser = Depends(require_teacher), db: Session = Depends(get_db)) -> list[CourseStat]:
    return _per_course(db)


@router.get("/trend", response_model=list[TrendPoint])
def trend(
    days: int = Query(default=30, ge=1, le=365),
    _: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> list[TrendPoint]:
    return _trend(db, days)
