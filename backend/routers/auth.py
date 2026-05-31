"""Auth endpoints — login, register, me."""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from db import get_db
from deps import CurrentUser, current_user, require_teacher
from models import User
from schemas import AuthResponse, LoginRequest, RegisterRequest, UserView
from security import hash_password, issue_token, verify_password

router = APIRouter(tags=["Auth"])


def _to_auth_response(user: User) -> AuthResponse:
    token, exp = issue_token(
        user_id=user.id, email=user.email, role=user.role, full_name=user.full_name
    )
    return AuthResponse(
        token=token,
        user=UserView.from_orm_user(user),
        expiresAt=exp,
    )


# ── REGISTER ──────────────────────────────────────────────
# Spec ne baruvashe registracija, ama nie sami se registrirame,
# pa expose-uvame i pod /api/register i pod /api/auth/register.
@router.post("/api/register", response_model=AuthResponse, status_code=201)
@router.post("/api/auth/register", response_model=AuthResponse, status_code=201, include_in_schema=False)
def register(req: RegisterRequest, db: Session = Depends(get_db)) -> AuthResponse:
    if req.role == "ADMIN":
        raise HTTPException(status_code=400, detail="Cannot register as admin")

    if db.scalar(select(User).where(User.email == req.email.lower())):
        raise HTTPException(status_code=409, detail="Email already registered")

    if req.role == "STUDENT":
        if not req.studentNumber or not req.studentNumber.strip():
            raise HTTPException(status_code=400, detail="Student number is required for students")
        if db.scalar(select(User).where(User.student_number == req.studentNumber.strip())):
            raise HTTPException(status_code=409, detail="Student number already registered")
    elif req.studentNumber:
        raise HTTPException(status_code=400, detail="Student number is only for students")

    user = User(
        email=req.email.lower().strip(),
        password_hash=hash_password(req.password),
        full_name=req.fullName.strip(),
        role=req.role,
        student_number=req.studentNumber.strip() if req.role == "STUDENT" and req.studentNumber else None,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return _to_auth_response(user)


# ── LOGIN (spec endpoint) ──────────────────────────────────
@router.post("/api/login", response_model=AuthResponse)
@router.post("/api/auth/login", response_model=AuthResponse, include_in_schema=False)
def login(req: LoginRequest, db: Session = Depends(get_db)) -> AuthResponse:
    user = db.scalar(select(User).where(User.email == req.email.lower()))
    if not user or not verify_password(req.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials"
        )
    return _to_auth_response(user)


@router.get("/api/auth/me", response_model=UserView)
def me(user: CurrentUser = Depends(current_user), db: Session = Depends(get_db)) -> UserView:
    db_user = db.get(User, user.id)
    if not db_user:
        raise HTTPException(status_code=404, detail="User not found")
    return UserView.from_orm_user(db_user)


# ── Lookup za NFC tap ──────────────────────────────────────
# Teacher app po NFC dobiva student_number kako string;
# ovde go razresuva vo User (id) za potoa POST /api/attendance.
@router.get("/api/users/by-number/{student_number}", response_model=UserView)
def find_by_student_number(
    student_number: str,
    _: CurrentUser = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> UserView:
    student = db.scalar(
        select(User).where(User.student_number == student_number.strip(), User.role == "STUDENT")
    )
    if not student:
        raise HTTPException(status_code=404, detail="Student not found")
    return UserView.from_orm_user(student)
