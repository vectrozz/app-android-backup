"""
Abstract storage interface for encrypted blob storage.

Concrete implementations: LocalStorage (disk) and S3Storage (S3-compatible).
Selected at runtime via the STORAGE_BACKEND env var.
"""

from abc import ABC, abstractmethod

from app.config import settings


class StorageBackend(ABC):
    """Interface for blob storage backends."""

    def chunk_path(self, user_id: str, upload_id: str, chunk_index: int) -> str:
        """
        Generate a deterministic storage path for a chunk.

        Layout: {user_id}/{upload_id[0:2]}/{upload_id}/chunk_{index:05d}
        The two-character prefix avoids too many entries in one directory.
        """
        prefix = upload_id[:2]
        return f"{user_id}/{prefix}/{upload_id}/chunk_{chunk_index:05d}"

    @abstractmethod
    async def write(self, path: str, data: bytes) -> None:
        """Write bytes to storage at the given path."""
        ...

    @abstractmethod
    async def read(self, path: str) -> bytes:
        """Read bytes from storage at the given path."""
        ...

    @abstractmethod
    async def delete(self, path: str) -> None:
        """Delete a blob at the given path."""
        ...

    @abstractmethod
    async def exists(self, path: str) -> bool:
        """Check if a blob exists at the given path."""
        ...


# ── Factory ────────────────────────────────────────────────────

_storage_instance: StorageBackend | None = None


def get_storage() -> StorageBackend:
    """Return the configured storage backend (singleton)."""
    global _storage_instance
    if _storage_instance is not None:
        return _storage_instance

    if settings.STORAGE_BACKEND == "s3":
        from app.storage.s3 import S3Storage
        _storage_instance = S3Storage()
    else:
        from app.storage.local import LocalStorage
        _storage_instance = LocalStorage(settings.STORAGE_PATH)

    return _storage_instance
