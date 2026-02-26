"""
Local disk storage adapter.

Stores encrypted chunk blobs under STORAGE_PATH on the local filesystem.
Best for self-hosted single-VPS deployments.
"""

import os
from pathlib import Path

import aiofiles

from app.storage.base import StorageBackend


class LocalStorage(StorageBackend):
    def __init__(self, base_path: str):
        self.base = Path(base_path)
        self.base.mkdir(parents=True, exist_ok=True)

    def _resolve(self, path: str) -> Path:
        return self.base / path

    async def write(self, path: str, data: bytes) -> None:
        full_path = self._resolve(path)
        full_path.parent.mkdir(parents=True, exist_ok=True)
        async with aiofiles.open(full_path, "wb") as f:
            await f.write(data)

    async def read(self, path: str) -> bytes:
        full_path = self._resolve(path)
        if not full_path.exists():
            raise FileNotFoundError(f"Blob not found: {path}")
        async with aiofiles.open(full_path, "rb") as f:
            return await f.read()

    async def delete(self, path: str) -> None:
        full_path = self._resolve(path)
        if full_path.exists():
            full_path.unlink()

    async def exists(self, path: str) -> bool:
        return self._resolve(path).exists()
