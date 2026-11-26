#!/usr/bin/env bash
# Runs Maven for the backend inside a JDK 21 container (the host has no Java 21/Maven).
# Testcontainers talks to the host Docker daemon through the mounted socket.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec docker run --rm --name "finpilot-mvn-$$" \
  -v finpilot-m2:/root/.m2 \
  -v "$ROOT/backend":/src -w /src \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  --add-host=host.docker.internal:host-gateway \
  maven:3-eclipse-temurin-21 mvn -B "$@"
