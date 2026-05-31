"""Pydantic schemas — request/response oblici za API."""
from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field

Role = Literal["ADMIN", "TEACHER", "STUDENT"]

# ─────────────────────── Auth ───────────────────────


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=6, max_length=72)
    fullName: str = Field(min_length=2, max_length=200)
    role: Role = "TEACHER"
    studentNumber: str | None = None


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class UserView(BaseModel):
    """Vraka se vo response na login/register/me."""
    id: int
    email: str
    fullName: str
    role: Role
    studentNumber: str | None = None

    @classmethod
    def from_orm_user(cls, u) -> "UserView":
        return cls(
            id=u.id,
            email=u.email,
            fullName=u.full_name,
            role=u.role,
            studentNumber=u.student_number,
        )


class AuthResponse(BaseModel):
    token: str
    user: UserView
    expiresAt: datetime


# ─────────────────────── Courses ───────────────────────


class CourseRequest(BaseModel):
    code: str = Field(min_length=2, max_length=20)
    name: str = Field(min_length=2, max_length=160)
    teacherId: int | None = None


class CourseView(BaseModel):
    id: int
    code: str
    name: str
    teacherId: int
    teacherName: str


# ─────────────────────── Sessions ───────────────────────


class StartSessionRequest(BaseModel):
    courseId: int


class SessionView(BaseModel):
    id: int
    courseId: int
    courseName: str
    teacherId: int
    teacherName: str
    startedAt: datetime
    endedAt: datetime | None
    active: bool
    attendanceCount: int = 0


# ─────────────────────── Attendance ───────────────────────


class TapRecord(BaseModel):
    studentId: int
    tappedAt: datetime | None = None


class BulkAttendanceRequest(BaseModel):
    sessionId: int
    records: list[TapRecord] = Field(min_length=1)


class BulkAttendanceResponse(BaseModel):
    accepted: int
    duplicates: int
    rejected: int
    ids: list[int]


class AttendanceView(BaseModel):
    id: int
    sessionId: int
    studentId: int
    studentName: str
    studentNumber: str | None
    courseName: str
    teacherName: str
    tappedAt: datetime


class AttendancePage(BaseModel):
    items: list[AttendanceView]
    totalElements: int
    page: int
    size: int


# ─────────────────────── Statistics ───────────────────────


class Overview(BaseModel):
    todayCount: int
    weekCount: int
    monthCount: int
    activeSessions: int
    totalStudents: int
    totalCourses: int


class CourseStat(BaseModel):
    courseId: int
    courseName: str
    attended: int
    enrolled: int
    rate: float


class TrendPoint(BaseModel):
    date: date
    count: int


class StatisticsAggregate(BaseModel):
    overview: Overview
    perCourse: list[CourseStat]
    trend: list[TrendPoint]
