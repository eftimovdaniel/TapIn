"""Tests za potpishani NFC payloads (HMAC validacija)."""
import hashlib
import hmac as _hmac
import time

import pytest

from secure_nfc import NfcPayloadError, parse_and_verify
from config import get_settings


def _make_payload(student_number: str, ts: int, secret: str | None = None) -> str:
    s = secret or get_settings().nfc_shared_secret
    msg = f"{student_number}:{ts}"
    sig = _hmac.new(
        s.encode("utf-8"),
        msg.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()[:16]
    return f"{student_number}|{ts}|{sig}"


def test_valid_payload_parses():
    now = int(time.time())
    p = _make_payload("193001", now)
    out = parse_and_verify(p, now=now)
    assert out.student_number == "193001"
    assert out.timestamp == now


def test_payload_within_skew_ok():
    now = int(time.time())
    p = _make_payload("193001", now - 30)
    out = parse_and_verify(p, now=now)
    assert out.timestamp == now - 30


def test_payload_outside_skew_rejected():
    now = int(time.time())
    p = _make_payload("193001", now - 3600)  # 1 час старо
    with pytest.raises(NfcPayloadError):
        parse_and_verify(p, now=now)


def test_payload_with_wrong_secret_rejected():
    now = int(time.time())
    p = _make_payload("193001", now, secret="WRONG_SECRET")
    with pytest.raises(NfcPayloadError):
        parse_and_verify(p, now=now)


def test_tampered_student_number_rejected():
    now = int(time.time())
    p = _make_payload("193001", now)
    parts = p.split("|")
    tampered = f"999999|{parts[1]}|{parts[2]}"
    with pytest.raises(NfcPayloadError):
        parse_and_verify(tampered, now=now)


def test_malformed_payload_rejected():
    with pytest.raises(NfcPayloadError):
        parse_and_verify("not-a-payload")
    with pytest.raises(NfcPayloadError):
        parse_and_verify("only|two")
    with pytest.raises(NfcPayloadError):
        parse_and_verify("")


def test_invalid_timestamp_rejected():
    with pytest.raises(NfcPayloadError):
        parse_and_verify("193001|notanumber|abcdef0123456789")


# ─────────── v2 (so studentName) — spec 3.2.3 ───────────

def _make_payload_v2(
    student_number: str,
    student_name: str,
    ts: int,
    secret: str | None = None,
) -> str:
    s = secret or get_settings().nfc_shared_secret
    msg = f"v2:{student_number}:{student_name}:{ts}"
    sig = _hmac.new(
        s.encode("utf-8"),
        msg.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()[:16]
    return f"v2|{student_number}|{student_name}|{ts}|{sig}"


def test_v2_valid_payload_parses():
    now = int(time.time())
    p = _make_payload_v2("193001", "Ana Stojanoska", now)
    out = parse_and_verify(p, now=now)
    assert out.student_number == "193001"
    assert out.student_name == "Ana Stojanoska"
    assert out.timestamp == now
    assert out.version == 2


def test_v2_tampered_name_rejected():
    now = int(time.time())
    p = _make_payload_v2("193001", "Ana", now)
    parts = p.split("|")
    tampered = "|".join([parts[0], parts[1], "Hacker", parts[3], parts[4]])
    with pytest.raises(NfcPayloadError):
        parse_and_verify(tampered, now=now)


def test_v2_outside_skew_rejected():
    now = int(time.time())
    p = _make_payload_v2("193001", "Ana", now - 3600)
    with pytest.raises(NfcPayloadError):
        parse_and_verify(p, now=now)
