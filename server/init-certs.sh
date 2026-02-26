#!/bin/bash
# Generate self-signed TLS certificate for ZK Backup server.
# Works with both IP addresses and domain names.
#
# Usage: ./init-certs.sh
# Reads DOMAIN from .env file or uses "localhost" as default.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CERT_DIR="$SCRIPT_DIR/certs"

# Read DOMAIN from .env if it exists
if [ -f "$SCRIPT_DIR/.env" ]; then
    DOMAIN=$(grep -E '^DOMAIN=' "$SCRIPT_DIR/.env" | cut -d'=' -f2 | tr -d ' "'"'"'')
fi
DOMAIN="${DOMAIN:-localhost}"

mkdir -p "$CERT_DIR"

# Skip if certs already exist
if [ -f "$CERT_DIR/cert.pem" ] && [ -f "$CERT_DIR/key.pem" ]; then
    echo "✅ Certificates already exist in $CERT_DIR/"
    echo "   To regenerate, delete them first: rm $CERT_DIR/*.pem"
    exit 0
fi

echo "🔐 Generating self-signed certificate for: $DOMAIN"

# Determine SAN type (IP address or DNS name)
if echo "$DOMAIN" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
    SAN="IP:$DOMAIN,IP:127.0.0.1"
    echo "   Detected IP address — adding IP SAN"
else
    SAN="DNS:$DOMAIN,DNS:localhost,IP:127.0.0.1"
    echo "   Detected hostname — adding DNS SAN"
fi

openssl req -x509 \
    -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 \
    -days 3650 \
    -nodes \
    -keyout "$CERT_DIR/key.pem" \
    -out "$CERT_DIR/cert.pem" \
    -subj "/CN=$DOMAIN/O=ZK Backup" \
    -addext "subjectAltName=$SAN" \
    2>/dev/null

echo "✅ Certificate generated:"
echo "   $CERT_DIR/cert.pem"
echo "   $CERT_DIR/key.pem"
echo "   Valid for 10 years"
echo ""
echo "Now run: docker compose up -d"
