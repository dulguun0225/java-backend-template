// Vulnerability scan over the CycloneDX SBOM with osv-scanner, gated on exit code. The binary comes from
// mise.toml's exact pin (`mise install`), one entry for every platform, rather than a per-OS download here.
// The JSON verdict is kept as a build artifact; nothing is uploaded to a hosted service. Usage: osv-scan.mjs <sbom.json>
import fs from 'node:fs';
import path from 'node:path';
import { main, run, Fail } from './_lib.mjs';

main(() => {
  process.chdir(path.resolve(import.meta.dirname, '..'));
  const sbom = process.argv[2] ?? 'target/classes/META-INF/sbom/application.cdx.json';
  if (!fs.existsSync(sbom)) throw new Fail(`SBOM not found at ${sbom} (run mvn package first; cyclonedx makeAggregateBom writes it there)`);
  let status;
  try {
    status = run('osv-scanner', ['scan', 'source', '-L', sbom, '--format=json', '--output-file=target/osv-results.json'], { check: false });
  } catch (e) {
    if (e instanceof Fail && e.message.endsWith('not on PATH')) throw new Fail('osv-scanner not on PATH; run `mise install` (mise.toml pins it)');
    throw e;
  }
  // osv-scanner exits 1 when vulnerabilities are found, 0 when clean; anything else is a tool error.
  run('osv-scanner', ['scan', 'source', '-L', sbom, '--format=table'], { check: false });
  process.exit(status);
});
