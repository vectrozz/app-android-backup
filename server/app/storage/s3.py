"""
S3-compatible object storage adapter.

Works with MinIO, Scaleway, Wasabi, Infomaniak, OVH S3, AWS S3, etc.
Configure via S3_ENDPOINT, S3_ACCESS_KEY, S3_SECRET_KEY, S3_BUCKET env vars.
"""

import aioboto3

from app.config import settings
from app.storage.base import StorageBackend


class S3Storage(StorageBackend):
    def __init__(self):
        self.session = aioboto3.Session()
        self.bucket = settings.S3_BUCKET
        self._client_kwargs = {
            "endpoint_url": settings.S3_ENDPOINT or None,
            "aws_access_key_id": settings.S3_ACCESS_KEY,
            "aws_secret_access_key": settings.S3_SECRET_KEY,
            "region_name": settings.S3_REGION,
        }

    def _client(self):
        return self.session.client("s3", **self._client_kwargs)

    async def write(self, path: str, data: bytes) -> None:
        async with self._client() as s3:
            await s3.put_object(Bucket=self.bucket, Key=path, Body=data)

    async def read(self, path: str) -> bytes:
        async with self._client() as s3:
            response = await s3.get_object(Bucket=self.bucket, Key=path)
            return await response["Body"].read()

    async def delete(self, path: str) -> None:
        async with self._client() as s3:
            await s3.delete_object(Bucket=self.bucket, Key=path)

    async def exists(self, path: str) -> bool:
        async with self._client() as s3:
            try:
                await s3.head_object(Bucket=self.bucket, Key=path)
                return True
            except Exception:
                return False
