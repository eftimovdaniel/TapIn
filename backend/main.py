"""TapIn FastAPI backend — startna tochka.

Run:
    uvicorn main:app --host 0.0.0.0 --port 8080 --reload
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

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
