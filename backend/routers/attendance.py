"""Attendance — POST sync (од teacher app) и GET listing (со филтри)."""
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from db import get_db
from deps import CurrentUser, require_admin, require_teacher
from models import Attendance, AttendanceSession, User
from schemas import (
    AttendancePage,
    AttendanceView,
    BulkAttendanceRequest,
    BulkAttendanceResponse,
)
from secure_nfc import NfcPayloadError, parse_and_verify

router = APIRouter(prefix="/api/attendance", tags=["Attendance"])
# Рутер за управување со присуство.
# - POST /api/attendance: синхронизирање на голем број записи од апликацијата за професори
# - GET /api/attendance: листа на присуство со опционални филтри
# - DELETE /api/attendance/{attendance_id}: бришење поединечен запис за присуство


# Претвора Attendance модел во view одговор.
def _to_view(a: Attendance) -> AttendanceView:
    return AttendanceView(
        id=a.id,
        sessionId=a.session.id,
        studentId=a.student.id,
        studentName=a.student.full_name,
        studentNumber=a.student.student_number,
        courseName=a.session.course.name,
        teacherName=a.session.teacher.full_name,
        tappedAt=a.tapped_at,
    )


# ─── POST /api/attendance — sync од teacher app ───
# Прифаќа записи за присуство, ги верификува податоците и ја избегнува двојната евиденција.
@router.post("", response_model=BulkAttendanceResponse)
def upload_attendance(
    req: BulkAttendanceRequest,
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> BulkAttendanceResponse:
    s = db.get(AttendanceSession, req.sessionId)
    if not s:
        raise HTTPException(status_code=404, detail="Сесијата не е најдена")
    if not user.is_admin and s.teacher_id != user.id:
        raise HTTPException(status_code=403, detail="Ова не е твоја сесија")

    accepted = 0
    duplicates = 0
    rejected = 0
    invalid_sigs = 0
    ids: list[int] = []

    for r in req.records:
        try:
            student_id = r.studentId

            # Безбеден потпис: ако е приложен, го верификува и го разрешува студентот
            if r.signedPayload:
                try:
                    verified = parse_and_verify(r.signedPayload)
                except NfcPayloadError:
                    invalid_sigs += 1
                    continue
                resolved = db.scalar(
                    select(User).where(User.student_number == verified.student_number)
                )
                if not resolved:
                    rejected += 1
                    continue
                student_id = resolved.id

            if student_id is None:
                rejected += 1
                continue

            existing = db.scalar(
                select(Attendance).where(
                    Attendance.session_id == s.id, Attendance.student_id == student_id
                )
            )
            if existing:
                duplicates += 1
                continue

            student = db.get(User, student_id)
            if not student:
                rejected += 1
                continue

            a = Attendance(session_id=s.id, student_id=student.id)
            if r.tappedAt is not None:
                a.tapped_at = r.tappedAt
            db.add(a)
            db.flush()
            ids.append(a.id)
            accepted += 1
        except Exception:
            db.rollback()
            rejected += 1

    db.commit()
    return BulkAttendanceResponse(
        accepted=accepted,
        duplicates=duplicates,
        rejected=rejected,
        invalidSignatures=invalid_sigs,
        ids=ids,
    )


# ─── GET /api/attendance — lista (so filteri) ───
@router.get("", response_model=AttendancePage)
def list_attendance(
    studentId: int | None = None,
    teacherId: int | None = None,
    courseId: int | None = None,
    sessionId: int | None = None,
    fromDate: datetime | None = Query(default=None, alias="from"),
    toDate: datetime | None = Query(default=None, alias="to"),
    page: int = Query(default=0, ge=0),
    size: int = Query(default=20, ge=1, le=200),
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> AttendancePage:
    if user.is_teacher:
        teacherId = user.id

    stmt = (
        select(Attendance)
        .join(AttendanceSession, Attendance.session_id == AttendanceSession.id)
    )
    if studentId is not None:
        stmt = stmt.where(Attendance.student_id == studentId)
    if teacherId is not None:
        stmt = stmt.where(AttendanceSession.teacher_id == teacherId)
    if courseId is not None:
        stmt = stmt.where(AttendanceSession.course_id == courseId)
    if sessionId is not None:
        stmt = stmt.where(Attendance.session_id == sessionId)
    if fromDate is not None:
        stmt = stmt.where(Attendance.tapped_at >= fromDate)
    if toDate is not None:
        stmt = stmt.where(Attendance.tapped_at <= toDate)

    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0

    stmt = stmt.order_by(Attendance.tapped_at.desc()).offset(page * size).limit(size)
    rows = db.scalars(stmt).all()
    items = [_to_view(a) for a in rows]
    return AttendancePage(items=items, totalElements=total, page=page, size=size)


@router.delete("/{attendance_id}", status_code=204)
def delete_attendance(
    attendance_id: int,
    user: CurrentUser = Depends(require_admin),
    db: Session = Depends(get_db),
) -> None:
    a = db.get(Attendance, attendance_id)
    if a:
        db.delete(a)
        db.commit()
