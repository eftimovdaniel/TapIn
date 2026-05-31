"""TapIn FastAPI backend — startna tochka.

Run:
    uvicorn main:app --host 0.0.0.0 --port 8080 --reload
"""
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from config import get_settings
from routers import attendance, auth, courses, sessions, statistics

_settings = get_settings()

app = FastAPI(
    title="TapIn API",
    version="1.0.0",
    description="Classroom Presence System — REST API.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=_settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/actuator/health", tags=["Health"])
def health() -> dict[str, str]:
    return {"status": "UP"}


app.include_router(auth.router)
app.include_router(courses.router)
app.include_router(sessions.router)
app.include_router(attendance.router)
app.include_router(statistics.router)


# ─────────────────────────────────────────────────────────────
# Dashboard hosting — istiot servis go servira i frontend-ot,
# pa nema CORS / cross-port problemi (Safari ITP).
#   /        → index.html (login)
#   /dashboard → dashboard.html
#   /register  → register.html
#   /assets/*  → static fajlovi (js, css, png)
# ─────────────────────────────────────────────────────────────
_DASHBOARD_DIR = (Path(__file__).resolve().parent.parent / "web-dashboard").resolve()

if _DASHBOARD_DIR.is_dir():
    app.mount(
        "/assets",
        StaticFiles(directory=str(_DASHBOARD_DIR / "assets")),
        name="assets",
    )

    @app.get("/", include_in_schema=False)
    def _index() -> FileResponse:
        return FileResponse(_DASHBOARD_DIR / "index.html")

    @app.get("/dashboard", include_in_schema=False)
    @app.get("/dashboard.html", include_in_schema=False)
    def _dashboard() -> FileResponse:
        return FileResponse(_DASHBOARD_DIR / "dashboard.html")

    @app.get("/register", include_in_schema=False)
    @app.get("/register.html", include_in_schema=False)
    def _register() -> FileResponse:
        return FileResponse(_DASHBOARD_DIR / "register.html")

    @app.get("/index.html", include_in_schema=False)
    def _index_html() -> FileResponse:
        return FileResponse(_DASHBOARD_DIR / "index.html")
