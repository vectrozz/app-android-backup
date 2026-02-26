"""
Upload endpoints: init, chunk, complete, status.

Protocol:
  1. POST /upload/init         → returns upload_id
  2. PUT  /upload/{id}/chunk/n → upload encrypted chunk bytes
  3. POST /upload/{id}/complete → finalize
  4. GET  /upload/{id}/status   → check progress
"""

import hashlib
import uuid

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.dependencies import get_current_user
from app.config import settings
from app.database import get_db
from app.models.chunk import Chunk
from app.models.file import BackupFile
from app.models.user import User
from app.storage.base import get_storage

router = APIRouter(prefix="/upload")


# ── Schemas ────────────────────────────────────────────────────

class UploadInitRequest(BaseModel):
    file_hash: str
    encrypted_size: int
    chunk_count: int
    device_id: str

class UploadInitResponse(BaseModel):
    upload_id: str
    already_exists: bool

class UploadCompleteResponse(BaseModel):
    status: str

class UploadStatusResponse(BaseModel):
    upload_id: str
    status: str
    chunks_received: list[int]


# ── Endpoints ──────────────────────────────────────────────────

@router.post("/init", response_model=UploadInitResponse)
async def upload_init(
    req: UploadInitRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Initialize a file upload. If file_hash already exists for this user,
    returns already_exists=true (client can skip).
    """
    # Check for duplicate (same user, same hash, status=complete)
    result = await db.execute(
        select(BackupFile).where(
            BackupFile.user_id == user.id,
            BackupFile.file_hash == req.file_hash,
            BackupFile.status == "complete",
        )
    )
    existing = result.scalar_one_or_none()
    if existing:
        return UploadInitResponse(upload_id=str(existing.id), already_exists=True)

    # Validate device belongs to user
    device_id = uuid.UUID(req.device_id)
    # (In production, verify device ownership — skipped for brevity)

    # Create file record
    backup_file = BackupFile(
        user_id=user.id,
        device_id=device_id,
        file_hash=req.file_hash,
        encrypted_size=req.encrypted_size,
        chunk_count=req.chunk_count,
        status="uploading",
    )
    db.add(backup_file)
    await db.flush()

    return UploadInitResponse(upload_id=str(backup_file.id), already_exists=False)


@router.put("/{upload_id}/chunk/{chunk_index}")
async def upload_chunk(
    upload_id: str,
    chunk_index: int,
    request: Request,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Upload a single encrypted chunk (binary body)."""
    file_id = uuid.UUID(upload_id)

    # Verify file exists and belongs to user
    result = await db.execute(
        select(BackupFile).where(BackupFile.id == file_id, BackupFile.user_id == user.id)
    )
    backup_file = result.scalar_one_or_none()
    if not backup_file:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Upload not found")

    if backup_file.status != "uploading":
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Upload already complete")

    if chunk_index < 0 or chunk_index >= backup_file.chunk_count:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid chunk index")

    # Read binary body
    body = await request.body()
    if len(body) > settings.MAX_CHUNK_SIZE:
        raise HTTPException(status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, detail="Chunk too large")

    # Compute hash of encrypted chunk
    chunk_hash = hashlib.sha256(body).hexdigest()

    # Store chunk blob via storage adapter
    storage = get_storage()
    storage_path = storage.chunk_path(str(user.id), upload_id, chunk_index)
    await storage.write(storage_path, body)

    # Upsert chunk record
    existing_chunk = await db.execute(
        select(Chunk).where(Chunk.file_id == file_id, Chunk.chunk_index == chunk_index)
    )
    chunk = existing_chunk.scalar_one_or_none()
    if chunk:
        chunk.chunk_hash = chunk_hash
        chunk.size = len(body)
        chunk.storage_path = storage_path
    else:
        chunk = Chunk(
            file_id=file_id,
            chunk_index=chunk_index,
            chunk_hash=chunk_hash,
            size=len(body),
            storage_path=storage_path,
        )
        db.add(chunk)

    return {"status": "ok", "chunk_index": chunk_index, "chunk_hash": chunk_hash}


@router.post("/{upload_id}/complete", response_model=UploadCompleteResponse)
async def upload_complete(
    upload_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Finalize an upload. Verifies all chunks are present."""
    file_id = uuid.UUID(upload_id)

    result = await db.execute(
        select(BackupFile).where(BackupFile.id == file_id, BackupFile.user_id == user.id)
    )
    backup_file = result.scalar_one_or_none()
    if not backup_file:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Upload not found")

    # Count received chunks
    chunk_result = await db.execute(
        select(Chunk).where(Chunk.file_id == file_id)
    )
    chunks = chunk_result.scalars().all()

    if len(chunks) != backup_file.chunk_count:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Expected {backup_file.chunk_count} chunks, got {len(chunks)}",
        )

    # Mark complete
    from datetime import datetime, timezone
    backup_file.status = "complete"
    backup_file.completed_at = datetime.now(timezone.utc)

    return UploadCompleteResponse(status="complete")


@router.get("/{upload_id}/status", response_model=UploadStatusResponse)
async def upload_status(
    upload_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Check which chunks have been received for a given upload."""
    file_id = uuid.UUID(upload_id)

    result = await db.execute(
        select(BackupFile).where(BackupFile.id == file_id, BackupFile.user_id == user.id)
    )
    backup_file = result.scalar_one_or_none()
    if not backup_file:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Upload not found")

    chunk_result = await db.execute(
        select(Chunk.chunk_index).where(Chunk.file_id == file_id)
    )
    received = [row[0] for row in chunk_result.all()]

    return UploadStatusResponse(
        upload_id=upload_id,
        status=backup_file.status,
        chunks_received=sorted(received),
    )
