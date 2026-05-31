"""Password hashing (bcrypt) + JWT — kako shto bara specifikacijata."""
from datetime import datetime, timedelta, timezone

import bcrypt
from jose import JWTError, jwt

from config import get_settings

_settings = get_settings()

ALGORITHM = "HS256"
BCRYPT_ROUNDS = 10
_MAX_BCRYPT_BYTES = 72  # bcrypt limit


def _truncate_72_bytes(password: str) -> bytes:
    """Bcrypt allows max 72 bytes — truncate if longer."""
    raw = password.encode("utf-8")
    return raw[:_MAX_BCRYPT_BYTES]


def hash_password(password: str) -> str:
    salt = bcrypt.gensalt(rounds=BCRYPT_ROUNDS)
    return bcrypt.hashpw(_truncate_72_bytes(password), salt).decode("utf-8")


def verify_password(plain: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(_truncate_72_bytes(plain), hashed.encode("utf-8"))
    except (ValueError, TypeError):
        return False


def issue_token(*, user_id: int, email: str, role: str, full_name: str) -> tuple[str, datetime]:
    expires_at = datetime.now(timezone.utc) + timedelta(hours=_settings.jwt_ttl_hours)
    payload = {
        "sub": str(user_id),
        "email": email,
        "role": role,
        "name": full_name,
        "exp": int(expires_at.timestamp()),
        "iat": int(datetime.now(timezone.utc).timestamp()),
    }
    token = jwt.encode(payload, _settings.jwt_secret, algorithm=ALGORITHM)
    return token, expires_at


def parse_token(token: str) -> dict | None:
    try:
        return jwt.decode(token, _settings.jwt_secret, algorithms=[ALGORITHM])
    except JWTError:
        return None
