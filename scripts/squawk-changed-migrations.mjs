// squawk over the Flyway migrations a change adds or edits, gated on exit code. Usage: squawk-changed-migrations.mjs <base-sha>
// With no base (or an unknown one) every committed migration is linted, which is the right answer on a fresh repo.
// squawk is fetched by npx at a pinned version; npm publishes it with a binary per platform.
import path from 'node:path';
import { capture, lines, main, ok, run } from './_lib.mjs';

const SQUAWK_VERSION = '2.65.0';

main(() => {
  process.chdir(path.resolve(import.meta.dirname, '..'));
  const base = process.argv[2] ?? '';
  const migrations = 'src/main/resources/db/migration/*.sql';
  const files =
    base && base !== '0000000000000000000000000000000000000000' && ok('git', ['cat-file', '-e', base])
      ? lines(capture('git', ['diff', '--relative', '--name-only', '--diff-filter=AM', `${base}...HEAD`, '--', migrations]))
      : lines(capture('git', ['ls-files', '--', migrations]));
  if (files.length === 0) {
    console.log('no migrations changed');
    return;
  }
  console.log(`linting ${files.length} migration(s)`);
  run('npx', ['--yes', `squawk-cli@${SQUAWK_VERSION}`, '-c', '.squawk.toml', ...files]);
});
