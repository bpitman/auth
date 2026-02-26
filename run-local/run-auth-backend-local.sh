#!/usr/bin/env bash
# Run auth-backend locally on port 8443 (HTTPS) using mock clients (no real AWS or Redis needed).
set -euo pipefail
cd "$(dirname "$0")/.."

# Build
sbt auth-backend/Test/package

# Get classpath from sbt
CP=$(sbt --no-colors 'export auth-backend/Test/fullClasspath' 2>/dev/null | tail -1)

# Run with local config
exec java -cp "$CP" \
  -Dconfig.file=run-local/backend-application.conf \
  com.pcpitman.auth.Main
