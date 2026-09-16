#!/usr/bin/env bash
# Vulnerability scan over the CycloneDX SBOM with osv-scanner, pinned by version and checksum, gated on exit code.
# The JSON verdict is kept as a build artifact; nothing is uploaded to a hosted service. Usage: osv-scan.sh <sbom.json>
set -euo pipefail
cd "$(dirname "$0")/.."
sbom="${1:-backend/target/classes/META-INF/sbom/application.cdx.json}"
[[ -f "$sbom" ]] || { echo "SBOM not found at $sbom (run mvn package first; cyclonedx makeAggregateBom writes it there)" >&2; exit 1; }
OSV_VERSION=v2.6.0
OSV_SHA256=ca69b3d3cd08f889a49dc0a383122f71cc528b83803671df5fd874d97485b108   # osv-scanner_linux_amd64
bin=backend/target/osv-scanner
if [[ ! -x "$bin" ]]; then
  curl -sSfL -o "$bin" "https://github.com/google/osv-scanner/releases/download/${OSV_VERSION}/osv-scanner_linux_amd64"
  echo "${OSV_SHA256}  ${bin}" | sha256sum -c -
  chmod +x "$bin"
fi
"$bin" scan source -L "$sbom" --format=json --output-file=backend/target/osv-results.json && status=0 || status=$?
# osv-scanner exits 1 when vulnerabilities are found, 0 when clean; anything else is a tool error.
"$bin" scan source -L "$sbom" --format=table || true
exit $status
