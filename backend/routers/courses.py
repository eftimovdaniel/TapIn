"""Predmeti (Courses) — kreiranje i listanje."""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from db import get_db
from deps import CurrentUser, require_teacher
from models import Course, Enrollment, User
from schemas import CourseRequest, CourseView

router = APIRouter(prefix="/api/courses", tags=["Courses"])


def _to_view(c: Course) -> CourseView:
    return CourseView(
        id=c.id, code=c.code, name=c.name,
        teacherId=c.teacher.id, teacherName=c.teacher.full_name,
    )


@router.get("", response_model=list[CourseView])
def list_courses(
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> list[CourseView]:
    if user.is_admin:
        courses = db.scalars(select(Course)).all()
    else:
        courses = db.scalars(select(Course).where(Course.teacher_id == user.id)).all()
    return [_to_view(c) for c in courses]


@router.post("", response_model=CourseView, status_code=201)
def create_course(
    req: CourseRequest,
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> CourseView:
    teacher_id = user.id if user.is_teacher else (req.teacherId or user.id)
    teacher = db.get(User, teacher_id)
    if not teacher:
        raise HTTPException(status_code=404, detail="Teacher not found")
    if teacher.role not in ("TEACHER", "ADMIN"):
        raise HTTPException(status_code=400, detail="User is not a teacher")
    if db.scalar(select(Course).where(Course.code == req.code.strip())):
        raise HTTPException(status_code=409, detail="Course code already exists")

    course = Course(code=req.code.strip(), name=req.name.strip(), teacher_id=teacher.id)
    db.add(course)
    db.commit()
    db.refresh(course)
    return _to_view(course)


@router.post("/{course_id}/enroll", status_code=204)
def enroll_student(
    course_id: int,
    studentId: int,
    user: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> None:
    course = db.get(Course, course_id)
    if not course:
        raise HTTPException(status_code=404, detail="Course not found")
    student = db.get(User, studentId)
    if not student or student.role != "STUDENT":
        raise HTTPException(status_code=400, detail="User is not a student")

    exists = db.get(Enrollment, (studentId, course_id))
    if exists:
        return None

    db.add(Enrollment(student_id=studentId, course_id=course_id))
    db.commit()
