"""Validacija na potpishani NFC payloads od studentskata aplikacija.

Dva formata se podrzhani (v2 e nov, v1 e legacy):

  v1:  "<studentNumber>|<unixSeconds>|<hmac16>"
       hmac16 = HMAC-SHA256(SECRET, "<studentNumber>:<unixSeconds>")[:16]

  v2:  "v2|<studentNumber>|<studentName>|<unixSeconds>|<hmac16>"
       hmac16 = HMAC-SHA256(SECRET, "v2:<studentNumber>:<studentName>:<unixSeconds>")[:16]

Spec 3.2.3 bara `student_id` + `student_name` vo NFC payload-ot — toa go pokriva v2.
v1 se ostavи za backward kompatibilnost so postari APK build-i.

Validacija:
  1. Ispravna struktura
  2. Časovniot timestamp e во window од ±N sekundi sporedена со serveroт
  3. HMAC se računa повторно и se sporeduva (constant-time)
"""
from __future__ import annotations

import hashlib
import hmac
import time
from dataclasses import dataclass

from config import get_settings


@dataclass(frozen=True)
class VerifiedPayload:
    student_number: str
    timestamp: int
    student_name: str | None = None
    version: int = 1


class NfcPayloadError(ValueError):
    """Generic neuspeshna validacija — koristи vo HTTPException(400)."""


def parse_and_verify(payload: str, *, now: int | None = None) -> VerifiedPayload:
    if not payload or "|" not in payload:
        raise NfcPayloadError("Невалиден NFC payload")
    parts = payload.split("|")

    if len(parts) == 5 and parts[0] == "v2":
        return _verify_v2(parts, now)
    if len(parts) == 3:
        return _verify_v1(parts, now)

    raise NfcPayloadError("Невалиден формат на NFC payload")


def _verify_v1(parts: list[str], now: int | None) -> VerifiedPayload:
    student_number, ts_str, sig = parts
    student_number = student_number.strip()
    if not student_number:
        raise NfcPayloadError("Праз број на студент")

    ts = _parse_ts(ts_str)
    _check_skew(ts, now)

    settings = get_settings()
    expected = _hmac_hex(settings.nfc_shared_secret, f"{student_number}:{ts}")[:16]
    if not hmac.compare_digest(expected, sig.lower()):
        raise NfcPayloadError("Невалиден потпис на NFC payload")

    return VerifiedPayload(student_number=student_number, timestamp=ts, version=1)


def _verify_v2(parts: list[str], now: int | None) -> VerifiedPayload:
    _, student_number, student_name, ts_str, sig = parts
    student_number = student_number.strip()
    student_name = student_name.strip()
    if not student_number:
        raise NfcPayloadError("Праз број на студент")

    ts = _parse_ts(ts_str)
    _check_skew(ts, now)

    settings = get_settings()
    msg = f"v2:{student_number}:{student_name}:{ts}"
    expected = _hmac_hex(settings.nfc_shared_secret, msg)[:16]
    if not hmac.compare_digest(expected, sig.lower()):
        raise NfcPayloadError("Невалиден потпис на NFC payload")

    return VerifiedPayload(
        student_number=student_number,
        student_name=student_name or None,
        timestamp=ts,
        version=2,
    )


def _parse_ts(raw: str) -> int:
    try:
        return int(raw)
    except ValueError as e:
        raise NfcPayloadError("Невалиден timestamp") from e


def _check_skew(ts: int, now: int | None) -> None:
    settings = get_settings()
    now_s = int(now if now is not None else time.time())
    if abs(now_s - ts) > settings.nfc_max_skew_seconds:
        raise NfcPayloadError("NFC потписот е истечен (timestamp)")


def _hmac_hex(secret: str, msg: str) -> str:
    raw = hmac.new(
        secret.encode("utf-8"),
        msg.encode("utf-8"),
        hashlib.sha256,
    ).digest()
    return raw.hex()
