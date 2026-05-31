"""Validacija na potpishani NFC payloads od studentskata aplikacija.

Format:
    "<studentNumber>|<unixSeconds>|<hmac16>"

Caде hmac16 se prvите 16 hex znaci od HMAC-SHA256(SECRET, "<studentNumber>:<unixSeconds>").

Validacija:
  1. Ispravna struktura (3 dela, valid HMAC hex)
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


class NfcPayloadError(ValueError):
    """Generic neuspeshna validacija — koristи vo HTTPException(400)."""


def parse_and_verify(payload: str, *, now: int | None = None) -> VerifiedPayload:
    if not payload or "|" not in payload:
        raise NfcPayloadError("Невалиден NFC payload")
    parts = payload.split("|")
    if len(parts) != 3:
        raise NfcPayloadError("Невалиден формат на NFC payload")

    student_number, ts_str, sig = parts
    student_number = student_number.strip()
    if not student_number:
        raise NfcPayloadError("Праз број на студент")

    try:
        ts = int(ts_str)
    except ValueError as e:
        raise NfcPayloadError("Невалиден timestamp") from e

    settings = get_settings()
    now_s = int(now if now is not None else time.time())
    if abs(now_s - ts) > settings.nfc_max_skew_seconds:
        raise NfcPayloadError("NFC потписот е истечен (timestamp)")

    expected = _hmac_hex(settings.nfc_shared_secret, f"{student_number}:{ts}")[:16]
    if not hmac.compare_digest(expected, sig.lower()):
        raise NfcPayloadError("Невалиден потпис на NFC payload")

    return VerifiedPayload(student_number=student_number, timestamp=ts)


def _hmac_hex(secret: str, msg: str) -> str:
    raw = hmac.new(
        secret.encode("utf-8"),
        msg.encode("utf-8"),
        hashlib.sha256,
    ).digest()
    return raw.hex()
