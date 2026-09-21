// The backend wall, as one command. Both the template's own CI and a project's `backend` job run exactly this,
// so the two workflows cannot drift on what "green" means. Needs Docker (Testcontainers) and network.
// Usage: node scripts/wall.mjs [base-sha]   (the base sha scopes the migration lint to the change; omit for all)
import path from 'node:path';
import { main, run } from './_lib.mjs';

const here = import.meta.dirname;
const script = (name, ...args) => run(process.execPath, [path.join(here, name), ...args]);
const step = (title) => console.log(`\n==> ${title}`);

main(() => {
  process.chdir(path.resolve(here, '..'));
  const base = process.argv[2] ?? '';

  step('No preview features, no Java agents, in any build or deploy file');
  script('check-forbidden-flags.mjs');

  step('Migration lint (squawk)');
  script('squawk-changed-migrations.mjs', base);

  // The canary runs first, and the gate it proves runs straight after: a gate that cannot fail proves
  // nothing, so the order here is the argument for believing the next step's verdict.
  step('Traceability gate canary: check-traceability.mjs catches every failure class it claims, on fixtures');
  script('check-traceability.selftest.mjs');

  step('Spec<->code traceability: no bare requirement id, every citation resolves, every requirement of a complete feature claimed or waived');
  script('check-traceability.mjs');

  step('Build wall: compile wall, formatter, architecture tests, unit and integration tests, coverage, licence gate');
  run('mvn', ['-B', '-ntp', 'verify']);

  step('jOOQ classes match the committed migrations (regenerate twice, byte-identical)');
  script('check-codegen-drift.mjs');

  step('OpenAPI document is byte-reproducible under a different timezone and locale');
  run(
    'mvn',
    ['-B', '-ntp', 'verify', '-Dit.test=OpenApiSnapshotIT', '-Dtest=NoSuchTest', '-Dsurefire.failIfNoSpecifiedTests=false',
      '-Djacoco.skip=true', '-Dspotless.check.skip=true', '-Dlicense.skip=true', '-Dcyclonedx.skip=true', '-Denforcer.skip=true'],
    { env: { TZ: 'Pacific/Kiritimati', LANG: 'tr_TR.UTF-8', LC_ALL: 'tr_TR.UTF-8' } },
  );

  step('OpenAPI document passes the vacuum ruleset (no offset/page, no PATCH, limit maximum, problem schema, temporal naming)');
  script('vacuum-openapi.mjs');

  step('Dependency vulnerabilities (osv-scanner over the SBOM)');
  script('osv-scan.mjs');

  step('backend wall green');
});
