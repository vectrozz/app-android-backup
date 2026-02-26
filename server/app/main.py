"""
FastAPI application entrypoint.
"""

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.middleware import SlowAPIMiddleware

from app.config import settings
from app.database import engine, Base
from app.routers import auth, upload, health


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Create tables on startup (dev convenience — use Alembic in production)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield


app = FastAPI(
    title="ZK Backup API",
    description="Zero-knowledge encrypted backup server",
    version="0.1.0",
    lifespan=lifespan,
)

# Rate limiting (handles 429 responses)
app.state.limiter = auth.limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)
app.add_middleware(SlowAPIMiddleware)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routers
app.include_router(health.router, prefix=settings.API_V1_PREFIX, tags=["health"])
app.include_router(auth.router, prefix=settings.API_V1_PREFIX, tags=["auth"])
app.include_router(upload.router, prefix=settings.API_V1_PREFIX, tags=["upload"])

# Trusted host (reject requests with forged Host headers)
if settings.ALLOWED_ORIGINS != ["*"]:
    allowed_hosts = [o.replace("https://", "").replace("http://", "") for o in settings.ALLOWED_ORIGINS]
    allowed_hosts.append("localhost")
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=allowed_hosts)
