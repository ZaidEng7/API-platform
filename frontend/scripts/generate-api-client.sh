#!/usr/bin/env bash
# Generates a typed Angular API client from a live OpenAPI spec via
# openapi-generator, into the shared library — the mechanism guide §5.2
# names explicitly ("API client generation from OpenAPI specs").
#
# Usage: scripts/generate-api-client.sh <service-name> <spec-url>
#   scripts/generate-api-client.sh gateway http://localhost:8080/v3/api-docs
#   scripts/generate-api-client.sh portfolio-service http://localhost:8080/api-docs/portfolio-service/v3/api-docs
#
# Requires the target service (or the Gateway, for its own spec, or via its
# /api-docs/<service>/** proxy routes for the business services) actually
# running and reachable at <spec-url> — see gateway/README.md's "API
# catalog" section for the full list of proxied service specs.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <service-name> <spec-url>" >&2
  exit 1
fi

SERVICE_NAME="$1"
SPEC_URL="$2"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/../projects/shared/src/lib/generated-api/${SERVICE_NAME}"

echo "Generating ${SERVICE_NAME} client from ${SPEC_URL} into ${OUTPUT_DIR}..."

npx --prefix "${SCRIPT_DIR}/.." @openapitools/openapi-generator-cli generate \
  -i "${SPEC_URL}" \
  -g typescript-angular \
  -o "${OUTPUT_DIR}" \
  --additional-properties=ngVersion=22.1.0,providedInRoot=true,serviceSuffix=Client,fileNaming=kebab-case,withInterfaces=true

echo "Done. Add an export for it in projects/shared/src/public-api.ts if this is a new service."
