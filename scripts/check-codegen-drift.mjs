// The committed jOOQ tree equals what the committed migrations generate, and generation is byte-reproducible:
// regenerate twice under a different timezone and locale, assert both runs identical to each other and to the
// committed tree. Needs Docker (Testcontainers PostgreSQL).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { main, run, Fail } from './_lib.mjs';

const OTHER_LOCALE = { TZ: 'Pacific/Kiritimati', LANG: 'tr_TR.UTF-8', LC_ALL: 'tr_TR.UTF-8' };

/** Every file under dir, as paths relative to it, sorted. */
function tree(dir) {
  return fs.readdirSync(dir, { recursive: true, withFileTypes: true })
    .filter((d) => d.isFile())
    .map((d) => path.relative(dir, path.join(d.parentPath, d.name)))
    .sort();
}

/** Paths that differ between two trees: missing on one side, or different bytes. */
function differences(a, b) {
  const files = [...new Set([...tree(a), ...tree(b)])].sort();
  return files.filter((f) => {
    const fa = path.join(a, f);
    const fb = path.join(b, f);
    if (!fs.existsSync(fa) || !fs.existsSync(fb)) return true;
    return !fs.readFileSync(fa).equals(fs.readFileSync(fb));
  });
}

function assertSame(a, b, message) {
  const diff = differences(a, b);
  if (diff.length > 0) {
    for (const f of diff) console.error(`differs: ${f}`);
    throw new Fail(message);
  }
}

main(() => {
  process.chdir(path.resolve(import.meta.dirname, '..'));
  const gen = 'src/main/java/com/example/starter/db';
  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'codegen-drift-'));
  try {
    fs.cpSync(gen, path.join(work, 'committed'), { recursive: true });
    run('mvn', ['-B', '-ntp', '-q', '-Pcodegen', 'generate-sources']);
    fs.cpSync(gen, path.join(work, 'run1'), { recursive: true });
    run('mvn', ['-B', '-ntp', '-q', '-Pcodegen', 'generate-sources'], { env: OTHER_LOCALE });
    fs.cpSync(gen, path.join(work, 'run2'), { recursive: true });
    assertSame(path.join(work, 'run1'), path.join(work, 'run2'), 'codegen is not byte-reproducible across timezone/locale');
    assertSame(path.join(work, 'committed'), path.join(work, 'run1'), 'committed jOOQ tree drifted from the migrations; run codegen and commit');
    console.log('jOOQ tree matches the migrations and regenerates byte-identically');
  } finally {
    fs.rmSync(work, { recursive: true, force: true });
  }
});
