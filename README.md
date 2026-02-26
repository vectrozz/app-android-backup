# ZK Backup — Zero-Knowledge Mobile Backup

Open-source Android app that backs up your phone to **your own server** with client-side encryption.  
The server never sees your files, filenames, or folder structure — only encrypted blobs.

## Architecture

```
┌──────────┐          HTTPS / chunked          ┌───────────┐
│  Android  │  ─── SHA-256 → AES-256-GCM ──▶  │  FastAPI   │
│   Client  │        encrypt → chunk → upload   │  Server    │
└──────────┘                                    └─────┬─────┘
     Room DB (local metadata)                         │
                                            ┌─────────┴─────────┐
                                            │  Postgres (meta)   │
                                            │  Disk / S3 (blobs) │
                                            └───────────────────┘
```

**Two modes:**
- **Self-host (MVP):** one-way backup → your VPS. You own the data; no recovery if you lose your password.
- **Managed SaaS (future):** we host the server; adds restore, billing, GDPR compliance.

## Quick Start

### Server (Docker)

```bash
cd server
cp .env.example .env        # edit DATABASE_URL, JWT_SECRET, STORAGE_PATH
docker-compose up -d         # starts FastAPI + Postgres
# API available at http://localhost:8000/api/v1/health
```

Generate initial DB migration (first time only):
```bash
docker-compose exec api alembic revision --autogenerate -m "initial"
docker-compose exec api alembic upgrade head
```

### Android

Prerequisites: Android Studio Hedgehog+ (or Iguana), JDK 17.

```bash
cd android
# Open in Android Studio — Gradle sync will pull all dependencies.
# Set your server URL in the app's setup screen.
# Min SDK 30 (Android 11).
```

## Project Structure

```
app-android-backup/
├── android/                        # Kotlin Android client
│   ├── app/src/main/java/com/zkbackup/app/
│   │   ├── di/                     # Hilt dependency injection
│   │   ├── data/                   # Room DB, API service, repository
│   │   ├── crypto/                 # AES-256-GCM + Argon2 key derivation
│   │   ├── service/                # Foreground service, file watcher
│   │   ├── worker/                 # WorkManager (periodic scan, upload)
│   │   ├── upload/                 # Chunking + upload orchestration
│   │   └── ui/                     # Activities + ViewModels
│   └── app/build.gradle.kts
├── server/                         # Python FastAPI server
│   ├── app/
│   │   ├── routers/                # auth, upload, health endpoints
│   │   ├── models/                 # SQLAlchemy models
│   │   ├── storage/                # local disk + S3 adapters
│   │   └── auth/                   # JWT token handling
│   ├── docker-compose.yml
│   └── requirements.txt
└── overview.md                     # Design document
```

## API Endpoints (v1)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/auth/register` | Create account |
| `POST` | `/api/v1/auth/login` | Get JWT tokens |
| `POST` | `/api/v1/auth/refresh` | Refresh access token |
| `POST` | `/api/v1/auth/devices` | Register a device |
| `POST` | `/api/v1/upload/init` | Start a file upload |
| `PUT`  | `/api/v1/upload/{id}/chunk/{n}` | Upload a chunk |
| `POST` | `/api/v1/upload/{id}/complete` | Finalize upload |
| `GET`  | `/api/v1/upload/{id}/status` | Check upload status |
| `GET`  | `/api/v1/health` | Health check |

## Security Model

1. User enters master password on the phone.
2. Client derives a 256-bit key: `Argon2id(password, salt)` → **master key**.
3. Each file is encrypted with AES-256-GCM using the master key (one key per user).
4. Only encrypted blobs reach the server — filenames are replaced with hashes.
5. Server authenticates via JWT (account password bcrypt-hashed separately).

**Password loss = data loss** (self-host mode). SaaS mode will add optional key recovery.

## License

- Android client: **AGPLv3** — fully open source.
- Server: open source for self-host `docker-compose`; proprietary additions for SaaS.
