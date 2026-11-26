#!/bin/sh
set -e
# Seed the persistent model volume with the model baked at build time (first boot only).
if [ ! -f "$ML_MODEL_DIR/categorizer.joblib" ] && [ -f /app/base-model/categorizer.joblib ]; then
  cp /app/base-model/categorizer.* "$ML_MODEL_DIR/"
fi
exec "$@"
