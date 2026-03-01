import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.db.database import init_db
from app.routers import enrollment, mood, analytics, burnout, coaching
from app.websocket.handler import websocket_endpoint

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
)

app = FastAPI(title="Voice Assistant Biometrics Server", version="2.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(enrollment.router)
app.include_router(mood.router)
app.include_router(analytics.router)
app.include_router(burnout.router)
app.include_router(coaching.router)

app.websocket("/ws")(websocket_endpoint)


@app.on_event("startup")
async def startup():
    await init_db()
    logging.getLogger(__name__).info("Database initialized")


@app.get("/health")
async def health():
    return {"status": "ok"}
