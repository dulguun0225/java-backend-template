// Re-take one upstream snapshot from a local checkout of the repository that owns it, and move the pin.
//
// `check-traceability.mjs` resolves every `<QUALIFIER>/FR-nnn` citation against a committed copy of the
// source document at `specs/upstream/<QUALIFIER>.md`, and refuses a copy whose bytes do not hash to the blob
// sha its row in `specs/trace-upstreams.tsv` pins. That is deliberate: a snapshot is a copy of another
// repository's document, so the only honest way to change one is to take it again from that repository at a
// named revision. This script is that way, and it is the only one.
//
// Usage:  node scripts/refresh-upstream-snapshot.mjs <QUALIFIER> <checkout> [<revision>]
//
//   QUALIFIER   a qualifier that already has a row in specs/trace-upstreams.tsv. Adding a new upstream means
//               writing the row first -- qualifier, the feature here that reads it, `<repo>:<path>` -- with
//               `-` in the two sha columns, and then running this to fill them in.
//   checkout    a local clone of the repository the row's location names. It is read and never written.
//   revision    anything `git rev-parse` accepts there; HEAD when it is left out. It is resolved to a full
//               commit sha, and that sha is what the row records.
//
// Nothing about this script runs in CI, and nothing in CI needs the other repository: the gate reads the
// committed snapshot and the committed row, and a moved pin arrives as a reviewable diff of the document.
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { Fail, capture, main } from './_lib.mjs';

const UPSTREAMS = 'trace-upstreams.tsv';
const SNAPSHOT_DIR = 'upstream';
const COLUMNS = 5;

/** The git blob name of `bytes`, exactly as `check-traceability.mjs` recomputes it and `git hash-object` prints it. */
const blobSha = (bytes) => crypto.createHash('sha1').update(`blob ${bytes.length}\0`, 'latin1').update(bytes).digest('hex');

main(() => {
  const [qualifier, checkout, revision = 'HEAD'] = process.argv.slice(2);
  if (!qualifier || !checkout) {
    throw new Fail('usage: node scripts/refresh-upstream-snapshot.mjs <QUALIFIER> <checkout of the upstream repository> [<revision>]');
  }
  const backendRoot = path.resolve(import.meta.dirname, '..');
  const projectRoot = path.resolve(capture('git', ['rev-parse', '--show-toplevel'], { cwd: backendRoot }));
  const specsDir = path.join(projectRoot, 'specs');
  const listFile = path.join(specsDir, UPSTREAMS);
  if (!fs.existsSync(listFile)) throw new Fail(`${listFile} does not exist; there is no upstream to refresh`);

  const text = fs.readFileSync(listFile, 'utf8');
  const rows = text.split(/\r?\n/);
  const index = rows.findIndex((raw) => !raw.trimStart().startsWith('#') && raw.split('\t')[0]?.trim() === qualifier);
  if (index < 0) {
    throw new Fail(
      `${qualifier} has no row in ${listFile}. Add one first -- ${qualifier}<TAB><the feature here that reads it><TAB><repo>:<path><TAB>-<TAB>- -- and run this again to fill in the two shas.`,
    );
  }
  const cells = rows[index].split('\t').map((c) => c.trim());
  if (cells.length !== COLUMNS) throw new Fail(`${listFile}:${index + 1}: expected exactly ${COLUMNS} tab-separated columns, found ${cells.length}`);
  const location = cells[2];
  const sourcePath = location.includes(':') ? location.slice(location.indexOf(':') + 1) : location;
  if (sourcePath.length === 0) throw new Fail(`${listFile}:${index + 1}: ${qualifier} names no path inside its repository; expected <repo>:<path>`);

  const checkoutRoot = path.resolve(checkout);
  if (!fs.existsSync(path.join(checkoutRoot, '.git'))) throw new Fail(`${checkoutRoot} is not a git checkout`);
  const sha = capture('git', ['-C', checkoutRoot, 'rev-parse', `${revision}^{commit}`]);
  if (!/^[0-9a-f]{40}$/.test(sha)) throw new Fail(`${revision} in ${checkoutRoot} did not resolve to a commit sha, it gave ${JSON.stringify(sha)}`);

  // `git show <sha>:<path>` rather than reading the working tree, so an uncommitted edit in the checkout can
  // never become a snapshot: what lands here is what that repository has at that revision, and nothing else.
  const content = capture('git', ['-C', checkoutRoot, 'show', `${sha}:${sourcePath}`]);
  if (content.length === 0) throw new Fail(`${sourcePath} is empty at ${sha} in ${checkoutRoot}`);
  // capture() trims, and a document that ends without a newline is not a document: one trailing newline,
  // always, so the bytes written here depend on the source and not on how it happened to be saved.
  const bytes = Buffer.from(`${content}\n`, 'utf8');

  const snapshot = path.join(specsDir, SNAPSHOT_DIR, `${qualifier}.md`);
  fs.mkdirSync(path.dirname(snapshot), { recursive: true });
  const before = fs.existsSync(snapshot) ? blobSha(fs.readFileSync(snapshot)) : null;
  fs.writeFileSync(snapshot, bytes);
  const blob = blobSha(bytes);

  cells[3] = sha;
  cells[4] = blob;
  rows[index] = cells.join('\t');
  fs.writeFileSync(listFile, rows.join('\n'));

  const rel = (f) => path.relative(projectRoot, f).split(path.sep).join('/');
  console.log(`${qualifier}: ${location} @ ${sha}`);
  console.log(`  ${rel(snapshot)}  ${before === blob ? `unchanged (${blob})` : `${before ?? '(new)'} -> ${blob}`}`);
  console.log(`  ${rel(listFile)}:${index + 1} rewritten. Review the diff of the snapshot: it is the source document changing under this repo's reading of it.`);
});
