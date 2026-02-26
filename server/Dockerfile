FROM python:3.12-slim

WORKDIR /app

# Install system deps for asyncpg
RUN apt-get update && \
    apt-get install -y --no-install-recommends gcc libpq-dev && \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Create storage directory and non-root user
RUN mkdir -p /data/storage && \
    groupadd -r zkbackup && \
    useradd -r -g zkbackup -d /app -s /sbin/nologin zkbackup && \
    chown -R zkbackup:zkbackup /app /data/storage

USER zkbackup

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--forwarded-allow-ips", "*"]
