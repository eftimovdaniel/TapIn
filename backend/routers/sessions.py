"""Attendance sessions — pochne / zatvori / lista."""
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from db import get_db
from deps import CurrentUser, require_teacher
from models import Attendance, AttendanceSession, Course
from schemas import SessionView, StartSessionRequest

router = APIRouter(prefix="/api/sessions", tags=["Sessions"])


def _to_view(s: AttendanceSession, count: int) -> SessionView:
    return SessionView(
        id=s.id,
        courseId=s.course.id,
        courseName=s.course.name,
        teacherId=s.teacher.id,
        teacherName=s.teacher.full_name,
        startedAt=s.started_at,
        endedAt=s.ended_at,
        active=s.active,
        attendanceCount=count,
    )


@router.post("", response_model=SessionView, status_code=201)
def start_session(
    req: StartSessionRequest,
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> SessionView:
    course = db.get(Course, req.courseId)
    if not course:
        raise HTTPException(status_code=404, detail="Предметот не е најден")
    if not user.is_admin and course.teacher_id != user.id:
        raise HTTPException(status_code=403, detail="Овој предмет не е твој")

    s = AttendanceSession(course_id=course.id, teacher_id=user.id)
    db.add(s)
    db.commit()
    db.refresh(s)
    return _to_view(s, count=0)


@router.post("/{session_id}/close", status_code=204)
def close_session(
    session_id: int,
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> None:
    s = db.get(AttendanceSession, session_id)
    if not s:
        raise HTTPException(status_code=404, detail="Сесијата не е најдена")
    if not user.is_admin and s.teacher_id != user.id:
        raise HTTPException(status_code=403, detail="Ова не е твоја сесија")
    if s.ended_at is None:
        s.ended_at = datetime.now(timezone.utc)
        db.commit()


@router.get("", response_model=list[SessionView])
def list_sessions(
    courseId: int | None = None,
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> list[SessionView]:
    stmt = select(AttendanceSession).order_by(AttendanceSession.started_at.desc())
    if not user.is_admin:
        stmt = stmt.where(AttendanceSession.teacher_id == user.id)
    if courseId is not None:
        stmt = stmt.where(AttendanceSession.course_id == courseId)
    sessions = db.scalars(stmt).all()

    out: list[SessionView] = []
    for s in sessions:
        count = db.scalar(select(func.count()).select_from(Attendance).where(Attendance.session_id == s.id)) or 0
        out.append(_to_view(s, count=count))
    return out
