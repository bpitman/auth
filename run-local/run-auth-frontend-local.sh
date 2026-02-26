#!/usr/bin/env bash
# Run auth-frontend locally on port 3000 (HTTPS) using the same certs as auth-backend.
set -euo pipefail
cd "$(dirname "$0")/.."

# Build UI and compile
(cd auth-frontend/ui && npm run build)
sbt auth-frontend/package

# Get classpath from sbt
CP=$(sbt --no-colors 'export auth-frontend/fullClasspath' 2>/dev/null | tail -1)

# Run with local config
exec java -cp "$CP" \
  -Dspring.config.location=file:run-local/frontend-application.properties \
  com.pcpitman.auth.frontend.Main
