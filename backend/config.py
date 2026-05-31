"""Konfiguracija — chita .env vo ENV varijabli."""
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str
    jwt_secret: str
    jwt_ttl_hours: int = 24
    cors_origins: str = "http://localhost:5173,http://localhost:3000"
    port: int = 8080

    # NFC payload signing — споделен sekret za HMAC. ISTIOT moora da postoi
    # vo studentskata aplikacija (com.tapin.student.nfc.SecureNfc.SHARED_SECRET).
    # Vo proizvodstvo: rotira pri sekoja nova verzija na aplikacijata.
    nfc_shared_secret: str = "TAPIN_NFC_SECRET_v1_2026"
    nfc_max_skew_seconds: int = 60

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
