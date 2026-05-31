"""Statistics — GET /api/statistics (agregat) + sub-endpoints.

Role-based: profesori gledat samo svoi predmeti i sesii.
Admin gleda site.
"""
from datetime import date, datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from db import get_db
from deps import CurrentUser, require_teacher
from models import Attendance, AttendanceSession, Course, Enrollment, User
from schemas import CourseStat, Overview, StatisticsAggregate, StudentStat, TrendPoint

router = APIRouter(prefix="/api/statistics", tags=["Statistics"])


def _overview(db: Session, teacher_id: int | None) -> Overview:
    now = datetime.now(timezone.utc)
    start_today = datetime(now.year, now.month, now.day, tzinfo=timezone.utc)
    start_week = start_today - timedelta(days=7)
    start_month = start_today - timedelta(days=30)

    def count_between(a: datetime, b: datetime) -> int:
        stmt = select(func.count()).select_from(Attendance).where(
            Attendance.tapped_at >= a, Attendance.tapped_at < b
        )
        if teacher_id is not None:
            stmt = stmt.join(AttendanceSession, Attendance.session_id == AttendanceSession.id) \
                       .where(AttendanceSession.teacher_id == teacher_id)
        return db.scalar(stmt) or 0

    today = count_between(start_today, now + timedelta(days=1))
    week = count_between(start_week, now)
    month = count_between(start_month, now)

    active_stmt = select(func.count()).select_from(AttendanceSession).where(
        AttendanceSession.ended_at.is_(None)
    )
    if teacher_id is not None:
        active_stmt = active_stmt.where(AttendanceSession.teacher_id == teacher_id)
    active = db.scalar(active_stmt) or 0

    if teacher_id is not None:
        # Distinct studenti koi vekje tapnale prisustvo vo bilo koja sesija
        # na ovoj profesor (po-relevantno otkolku enrollments koi mozhe da
        # ne postojat).
        students = db.scalar(
            select(func.count(func.distinct(Attendance.student_id)))
            .join(AttendanceSession, Attendance.session_id == AttendanceSession.id)
            .where(AttendanceSession.teacher_id == teacher_id)
        ) or 0
        courses = db.scalar(
            select(func.count()).select_from(Course).where(Course.teacher_id == teacher_id)
        ) or 0
    else:
        students = db.scalar(
            select(func.count()).select_from(User).where(User.role == "STUDENT")
        ) or 0
        courses = db.scalar(select(func.count()).select_from(Course)) or 0

    return Overview(
        todayCount=today, weekCount=week, monthCount=month,
        activeSessions=active, totalStudents=students, totalCourses=courses,
    )


def _per_course(db: Session, teacher_id: int | None) -> list[CourseStat]:
    """Stapka na prisustvo po predmet."""
    attended_subq = (
        select(
            AttendanceSession.course_id.label("course_id"),
            func.count(func.distinct(Attendance.student_id)).label("attended"),
        )
        .join(Attendance, Attendance.session_id == AttendanceSession.id)
        .group_by(AttendanceSession.course_id)
        .subquery()
    )
    enrolled_subq = (
        select(
            Enrollment.course_id.label("course_id"),
            func.count().label("enrolled"),
        )
        .group_by(Enrollment.course_id)
        .subquery()
    )

    stmt = (
        select(
            Course.id, Course.name,
            func.coalesce(attended_subq.c.attended, 0),
            func.coalesce(enrolled_subq.c.enrolled, 0),
        )
        .outerjoin(attended_subq, attended_subq.c.course_id == Course.id)
        .outerjoin(enrolled_subq, enrolled_subq.c.course_id == Course.id)
        .order_by(Course.name)
    )
    if teacher_id is not None:
        stmt = stmt.where(Course.teacher_id == teacher_id)

    out: list[CourseStat] = []
    for cid, name, attended, enrolled in db.execute(stmt).all():
        rate = (attended / enrolled) if enrolled else 0.0
        out.append(CourseStat(
            courseId=cid, courseName=name,
            attended=int(attended), enrolled=int(enrolled), rate=rate,
        ))
    return out


def _trend(db: Session, days: int, teacher_id: int | None) -> list[TrendPoint]:
    """Trend na taps po denovi (poslednite N dena)."""
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    day_col = func.date(Attendance.tapped_at).label("day")
    stmt = (
        select(day_col, func.count().label("cnt"))
        .where(Attendance.tapped_at >= cutoff)
        .group_by(day_col)
        .order_by(day_col)
    )
    if teacher_id is not None:
        stmt = stmt.join(AttendanceSession, Attendance.session_id == AttendanceSession.id) \
                   .where(AttendanceSession.teacher_id == teacher_id)

    out: list[TrendPoint] = []
    for day, cnt in db.execute(stmt).all():
        d = day if isinstance(day, date) else date.fromisoformat(str(day))
        out.append(TrendPoint(date=d, count=int(cnt)))
    return out


def _scope_teacher_id(user: CurrentUser) -> int | None:
    """ADMIN gleda site; profesor gleda samo svoi."""
    return None if user.is_admin else user.id


# ─── GET /api/statistics — spec endpoint ───
@router.get("", response_model=StatisticsAggregate)
def aggregate(
    days: int = Query(default=30, ge=1, le=365),
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> StatisticsAggregate:
    tid = _scope_teacher_id(user)
    return StatisticsAggregate(
        overview=_overview(db, tid),
        perCourse=_per_course(db, tid),
        trend=_trend(db, days, tid),
    )


@router.get("/overview", response_model=Overview)
def overview(
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> Overview:
    return _overview(db, _scope_teacher_id(user))


@router.get("/per-course", response_model=list[CourseStat])
def per_course(
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> list[CourseStat]:
    return _per_course(db, _scope_teacher_id(user))


@router.get("/trend", response_model=list[TrendPoint])
def trend(
    days: int = Query(default=30, ge=1, le=365),
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> list[TrendPoint]:
    return _trend(db, days, _scope_teacher_id(user))


# ─── GET /api/statistics/per-student ───
@router.get("/per-student", response_model=list[StudentStat])
def per_student(
    courseId: int | None = Query(default=None),
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> list[StudentStat]:
    """Stapka na prisustvo za sekoj student.

    Filteri:
      - courseId: ako e zadaden, gleda samo sесии od toj predmet
      - inaku: site sесии koi se vidlivi za vlechniот korisnik
        (profesor → svoi sесии; admin → site)
    """
    teacher_id = _scope_teacher_id(user)

    # 1) Vkupno sесии za score
    total_sessions_stmt = select(func.count()).select_from(AttendanceSession)
    if teacher_id is not None:
        total_sessions_stmt = total_sessions_stmt.where(
            AttendanceSession.teacher_id == teacher_id
        )
    if courseId is not None:
        total_sessions_stmt = total_sessions_stmt.where(
            AttendanceSession.course_id == courseId
        )
    total_sessions = db.scalar(total_sessions_stmt) or 0

    # 2) Studenti i broj na pominati sесии (distinct sessions)
    attended_stmt = (
        select(
            User.id,
            User.full_name,
            User.student_number,
            func.count(func.distinct(Attendance.session_id)).label("attended"),
        )
        .join(Attendance, Attendance.student_id == User.id)
        .join(AttendanceSession, AttendanceSession.id == Attendance.session_id)
        .where(User.role == "STUDENT")
    )
    if teacher_id is not None:
        attended_stmt = attended_stmt.where(AttendanceSession.teacher_id == teacher_id)
    if courseId is not None:
        attended_stmt = attended_stmt.where(AttendanceSession.course_id == courseId)
    attended_stmt = (
        attended_stmt.group_by(User.id, User.full_name, User.student_number)
        .order_by(func.count(func.distinct(Attendance.session_id)).desc(), User.full_name)
    )

    out: list[StudentStat] = []
    for sid, name, num, attended in db.execute(attended_stmt).all():
        rate = (attended / total_sessions) if total_sessions else 0.0
        out.append(StudentStat(
            studentId=sid, studentName=name, studentNumber=num,
            attended=int(attended),
            totalSessions=int(total_sessions),
            rate=rate,
        ))
    return out
