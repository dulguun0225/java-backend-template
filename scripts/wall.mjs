// The backend wall, as one command. Both the template's own CI and a project's `backend` job run exactly this,
// so the two workflows cannot drift on what "green" means. Needs Docker (Testcontainers) and network.
// Usage: node scripts/wall.mjs [base-sha]   (the base sha scopes the migration lint to the change; omit for all)
import fs from 'node:fs';
import path from 'node:path';
import { capture, Fail, main, run } from './_lib.mjs';

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

  step('Project checks: the scripts listed in <project root>/scripts/wall-checks.txt');
  projectChecks();

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

  step('OpenAPI document passes the vacuum ruleset (no offset/page, no PATCH, limit maximum, problem schema, temporal naming, closed request bodies)');
  script('vacuum-openapi.mjs');

  step('Dependency vulnerabilities (osv-scanner over the SBOM)');
  script('osv-scan.mjs');

  step('backend wall green');
});

/**
 * Run the Node scripts a project lists in scripts/wall-checks.txt under its root, in file order, with the
 * project root as the working directory. The project root is the parent of this service directory when the
 * service is vendored into a project (the directory is not the git toplevel), otherwise this directory. One
 * path per line, relative to the project root; blank lines and lines starting with # are skipped. A listed
 * path that does not exist fails the wall, as does a script that exits non-zero. No list, no checks.
 */
function projectChecks() {
  const service = fs.realpathSync(path.resolve(here, '..'));
  const top = fs.realpathSync(capture('git', ['rev-parse', '--show-toplevel'], { cwd: service }));
  const root = path.relative(top, service) === '' ? service : path.dirname(service);
  const list = path.join(root, 'scripts', 'wall-checks.txt');
  if (!fs.existsSync(list)) {
    console.log(`no project checks listed (${list} does not exist)`);
    return;
  }
  const entries = fs
    .readFileSync(list, 'utf8')
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l !== '' && !l.startsWith('#'));
  for (const entry of entries) {
    const file = path.resolve(root, entry);
    if (!fs.existsSync(file)) throw new Fail(`${list}: ${entry} does not exist under ${root}`);
    console.log(`-- ${entry}`);
    run(process.execPath, [file], { cwd: root });
  }
  if (entries.length === 0) console.log(`no project checks listed (${list} lists none)`);
}
