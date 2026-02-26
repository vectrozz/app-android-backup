# Copilot instructions

## Mission
Zero-knowledge mobile backup: open-source Android client + FastAPI server. Self-host (one-way backup to user VPS) is the MVP; SaaS mode adds restore, billing, multi-tenant isolation. Source: [overview.md](../overview.md).

## Repo layout (monorepo)
- `android/` — Kotlin Android app (min SDK 30 / Android 11).
- `server/` — Python FastAPI REST API + Postgres (metadata) + local-disk/S3 (blobs).
- Root: docs, CI, copilot instructions.

## Resolved design decisions
| Area | Decision |
|---|---|
| Server lang | **FastAPI (Python)** |
| Android min SDK | **30 (Android 11)** — avoids legacy scoped-storage hacks |
| DI framework | **Hilt** (Google-recommended, Play Store proven) |
| Encryption | AES-256-GCM; key = Argon2id(master_password + salt). **One key per user** (all files same key). Server never sees plaintext or real filenames. |
| Key recovery | **None** for self-host ("your data, your responsibility"). Recovery option planned for SaaS mode only. |
| Chunk size | **8 MB** |
| Resume protocol | Simple re-upload: `POST /upload/init` → `PUT /upload/{id}/chunk/{n}` → `POST /upload/{id}/complete`. Server tracks received chunks; client re-sends failed ones. |
| Auth | **JWT** (access 15 min + refresh 30 days). Account password bcrypt-hashed server-side; encryption master password processed only on client. |
| Storage | Postgres for **metadata only**. Encrypted blobs on **local disk** (self-host) or **S3-compatible** (SaaS: Infomaniak/Scaleway/Wasabi/MinIO). Never store blobs in Postgres. |
| Watch dirs | **User-configurable** (default: DCIM, Pictures, Downloads, Documents) |
| Docker | Ship `docker-compose.yml` (FastAPI + Postgres) from day 1 |
| License | Client: **AGPLv3** (open source). Server: open source for self-host docker-compose; proprietary additions for SaaS. |

## Architecture (client → `android/`)
- **FileObserver** for real-time detection + **WorkManager** periodic scan as fallback.
- **Foreground Service** with persistent notification (Android 13/14 requirement).
- Pipeline per file: SHA-256 → dedup check (Room) → AES-256-GCM encrypt → chunk 8 MB → upload with retry/backoff → mark synced.
- Stack: Kotlin, coroutines, Hilt, Room, OkHttp, WorkManager, BouncyCastle (Argon2).
- Key files: `ZkBackupApplication.kt`, `di/AppModule.kt`, `data/db/AppDatabase.kt`, `crypto/CryptoManager.kt`, `service/BackupForegroundService.kt`, `upload/UploadManager.kt`.

## Architecture (server → `server/`)
- FastAPI + SQLAlchemy 2.0 + Alembic migrations + Postgres.
- Data model: `users → devices → files → chunks`; every query scoped by `user_id`.
- Storage adapters: `storage/local.py` (disk), `storage/s3.py` (S3-compatible). Selected via `STORAGE_BACKEND` env var.
- Key files: `app/main.py`, `app/routers/upload.py`, `app/auth/jwt.py`, `app/storage/base.py`.

## Conventions
- Kotlin-first; coroutines for all async; no RxJava.
- Hilt `@Module` / `@AndroidEntryPoint` / `@HiltWorker` everywhere — keep code testable.
- Server endpoints versioned: `/api/v1/...`.
- All commits must keep `docker-compose up` working for the server.
- Prefer explicit file paths and shell commands in docs so contributors can build/test immediately.

## Implementation phases
1. **Phase 1 (current):** Android skeleton + permissions + FileObserver + Room + upload queue + server CRUD + docker-compose.
2. **Phase 2:** Retry queue hardening + chunked upload resume + server hash validation + auth flow polish.
3. **Phase 3:** Full encryption pipeline, SaaS restore API, logging/UI improvements, key recovery for SaaS.
