// Spec ids and the code cite each other, and until this gate nothing read either direction. The spec files
// under `specs/<NNN>-<name>/spec.md` define requirement ids as bullets -- `- **FR-nnn**: ...` and
// `- **SC-nnn**: ...` -- the ids are per feature and they collide across features (every feature here defines
// an FR whose number is 001), so a citation written bare names nothing: it resolves to four different
// requirements at once and to none of them in particular. The one citation form outside a feature's own spec
// directory is therefore qualified: `NNN/FR-nnn` / `NNN/SC-nnn`, where NNN is the feature directory's numeric
// prefix. (Spelled with placeholders, here and everywhere below: this file sits inside the gate's own scan
// root, so a qualified example written with real digits would have to resolve against a feature that
// happens to exist -- and it would stop resolving in the upstream template, which ships no specs tree at
// all.) The legacy spelling with a space instead of the slash counts as bare, because a
// reader and a grep both have to guess whether the number in front is a feature or a sentence.
//
// What this gate reads:
//   a. no bare id anywhere in the scan roots, so every citation names exactly one requirement;
//   b. every qualified citation resolves -- the feature directory exists and its spec.md defines that id;
//   b2. inside `specs/<NNN>-<name>/` a bare id is legal, because there it means that feature's own -- so it
//      has to be one that feature's spec.md actually defines. A reference to another feature's id is written
//      qualified there as everywhere else, and the legacy spaced form fails there too: `NNN FR-nnn` inside a
//      feature directory reads either as that feature's own id or as a cross-reference, and a reader cannot
//      tell which. This is the check that found the ids 004's own spec cites and no spec.md defines -- see
//      the `Ids inside a feature directory` section it prints;
//   c. every requirement of a *complete* feature is cited from at least one file under a test root, or is
//      waived with a written reason;
//   d. the waiver file is well formed and carries no waiver that is no longer needed. A waiver row is three
//      columns -- `NNN/ID<TAB>kind<TAB>reason` -- because "no test cites this" has exactly two honest causes
//      and they are not the same fact. `external` means the criterion cannot be witnessed from inside this
//      repository at all: a consumer service's behaviour, the caller topology, a production baseline, an
//      organisational outcome. No test here could ever close it, so it is closed by the reason. `deferred`
//      means the requirement is specified and deliberately not built yet, and its reason has to name where
//      that deferral is written down -- a named-gap row in docs/GATES.md, a scope boundary in the feature's
//      plan.md, the owning capability -- so a reader can go and check that the deferral is real. The two are
//      kept apart because the deferred set is a debt this repo owes and the external set is not, so `--report`
//      prints the deferred ids as a list of their own at the end. A requirement that is merely untested is
//      neither: it gets a test;
//   e. the legacy-file list is well formed and carries no row that is no longer needed;
//   f. the spec tree itself is sane -- a feature directory has a spec.md, it defines at least one id, no id
//      is defined twice inside one spec, and no two feature directories share one NNN;
//   g. every *upstream* citation names a qualifier declared in `specs/trace-upstreams.tsv`, and every
//      declared qualifier is cited somewhere.
//
// Why an upstream citation form exists at all. Each feature here was specified from a document in another
// repository, and that document numbers its own requirements in exactly this repo's spelling -- its `FR-nnn`
// and this feature's `FR-nnn` are two different requirements wearing one name. Written bare, such a citation
// reads as the feature's own: visible to this gate only while the number happens to define nothing local,
// and silently wrong the moment it does. So an upstream id is never written bare anywhere, the feature's own
// spec directory included. It is written `<QUALIFIER>/FR-nnn`, and the qualifier is a row in
// `specs/trace-upstreams.tsv` (`QUALIFIER<TAB>location`, sorted). The qualifier grammar is an uppercase
// letter followed by uppercase letters, digits and hyphens, so a qualifier can never be confused with the
// three-digit feature prefix in either direction. The gate accepts such a citation on its qualifier alone:
// it does not resolve it, because the upstream repository is not checked out in CI, it does not treat it as
// bare, and it counts as coverage of nothing.
//
// Why a legacy-file list exists at all. A shipped Flyway migration is append-only (R-16,
// check-migrations-append-only.mjs), so the bare ids inside `V2`..`V8` cannot be rewritten into the qualified
// form without moving a checksum Flyway validates at the first deploy against an existing database. Those
// files are listed once, with a reason, and their bare ids are ignored entirely rather than silently
// tolerated everywhere.
//
// What this gate cannot read is printed on every run, pass or fail: a citation is a *claim* that a test
// covers a requirement, and no static check can tell a claim from a coverage. See the block at the end.
//
// Layout is discovered, not assumed: the backend root is this script's parent, the project root is the git
// toplevel, and the specs tree is `<project root>/specs`. In the upstream template the backend *is* the git
// toplevel and there is no specs tree at all; the gate still runs there, with zero defined ids -- so every
// citation is dangling and every bare id still fails -- and says that it found no specs directory.
import fs from 'node:fs';
import path from 'node:path';
import { Fail, capture, lines, main } from './_lib.mjs';

// A requirement id, with the optional feature qualifier handled by hand below rather than in the pattern:
// the qualifier's own left edge has to be checked, and a lookbehind that did it would be unreadable.
// The trailing guard keeps a longer word from being read as an id with a suffix.
const TOKEN = /(FR|SC)-\d{3}[a-z]?(?![0-9A-Za-z])/g;
// A definition, as spec.md writes one. Bold, at the head of a list item, nothing else counts.
const DEFINITION = /^\s*[-*]\s+\*\*((?:FR|SC)-\d{3}[a-z]?)\*\*/;
// A feature directory: the numeric prefix is the qualifier a citation has to spell.
const FEATURE_DIR = /^(\d{3})-.+/;
// An upstream qualifier: an uppercase letter, then uppercase letters, digits and hyphens. It starts with a
// letter, so it can never be read as the three-digit feature prefix, and the feature prefix can never be
// read as one.
const UPSTREAM_QUALIFIER = /^[A-Z][A-Z0-9-]*$/;
// The shape of a requirement id, used only to refuse a qualifier that *is* one: in `FR-nnn/FR-mmm` the run
// of qualifier-shaped characters in front of the slash is the id in front of it, and a slash written between
// two ids is prose, not a citation of an upstream document.
const ID_SHAPE = /(?:FR|SC)-\d{3}/;
// The legacy qualifier: the feature number with a space where the slash belongs, optionally wrapped in a
// Markdown code span or a Javadoc `{@code}` -- `NNN <id>`, `` `NNN` <id> ``, `{@code NNN} <id>`. Anchored at
// the token's left edge. (Spelled with placeholders: this file is inside the gate's own scan root.)
const SPACED = /(?:^|[^0-9A-Za-z/-])(?:\{@code )?`?(\d{3})`?\}?[ \t]$/;
// An unfinished task line, as tasks.md writes one.
const OPEN_TASK = /^\s*[-*]\s*\[ \]/m;

const WAIVERS = 'trace-waivers.tsv';
const LEGACY = 'trace-legacy-files.tsv';
const UPSTREAMS = 'trace-upstreams.tsv';

/**
 * The only two kinds a waiver row may carry, each with the sentence a reader is entitled to hold it to.
 * Nothing else is accepted: a third kind would be the place every untestable-feeling requirement collects.
 */
const WAIVER_KINDS = new Map([
  ['external', 'the criterion cannot be witnessed from inside this repository at all (a consumer service\'s behaviour, caller topology, a production baseline, an organisational outcome)'],
  ['deferred', 'the requirement is specified and deliberately not built yet, and the reason names where that deferral is recorded'],
]);

/**
 * Where a citation counts as coverage. The frontend is a separate static deploy and is not started (the
 * project's CLAUDE.md and frontend/README.md); the moment it has tests, its test root is added here and the
 * "does not decide" block below stops naming it. Kept as a function because the path is project-root relative.
 */
const frontendTestRoots = (projectRoot) => [/* e.g. path.join(projectRoot, 'frontend', 'src', '__tests__') */];

/** Paths never scanned by default: this gate's own fixtures are deliberately full of the defects it catches. */
const excludedFromDefaultScan = (backendRoot) => [path.join(backendRoot, 'scripts', 'fixtures', 'traceability')];

// ---------------------------------------------------------------------------------------------------------
// Reading files

/** Tracked files under `root` (git ls-files, so target/ and anything ignored never enters), or a plain walk. */
function filesUnder(root, walk) {
  if (!fs.existsSync(root)) return [];
  if (!walk) return lines(capture('git', ['ls-files'], { cwd: root })).map((rel) => path.join(root, rel));
  const out = [];
  const visit = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => (a.name < b.name ? -1 : 1))) {
      if (entry.name === '.git' || entry.name === 'node_modules' || entry.name === 'target') continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) visit(full);
      else if (entry.isFile()) out.push(full);
    }
  };
  visit(root);
  return out;
}

/** The file's text, or null when it is binary -- a NUL byte in the first 8 KiB is the usual tell. */
function readText(file) {
  const buf = fs.readFileSync(file);
  return buf.subarray(0, 8192).includes(0) ? null : buf.toString('utf8');
}

/**
 * The upstream qualifier immediately in front of a token at `at`, or null.
 *
 * The run taken is the *maximal* one of qualifier-shaped characters ending at the slash, which is what keeps
 * a qualifier that ends in digits from being read through its own tail: the run is the whole qualifier, not
 * its last few characters, and the feature-prefix rule beside it sees a hyphen in front of those digits and
 * refuses them. Two things then disqualify the run: a character in front of it that is a letter, a digit or
 * a hyphen (so the run is not maximal after all, or is glued to a word), and a run that is itself a
 * requirement id (`FR-nnn/FR-mmm` is prose, not a citation of an upstream document).
 */
function upstreamQualifierBefore(text, at) {
  if (at < 1 || text[at - 1] !== '/') return null;
  let start = at - 1;
  while (start > 0 && /[A-Z0-9-]/.test(text[start - 1])) start--;
  const run = text.slice(start, at - 1);
  const before = start > 0 ? text[start - 1] : '';
  if (!UPSTREAM_QUALIFIER.test(run)) return null;
  if (/[A-Za-z0-9-]/.test(before)) return null;
  if (ID_SHAPE.test(run)) return null;
  return run;
}

/**
 * Every requirement id mentioned in `text`, each with its line, its feature qualifier or null, its upstream
 * qualifier or null, and the feature of a legacy *spaced* prefix or null.
 *
 * The feature qualifier is the four characters in front, `NNN/`, and only when the character before *those*
 * is not a letter, a digit or a hyphen. That last exclusion is what keeps a slash written between two ids --
 * a real shape in prose, `<id>/<id>` for "this one and that one" -- from reading the digits of the id in
 * front as a directory prefix, and it is also what keeps the tail of an upstream qualifier that happens to
 * end in three digits from being read as a feature. A token glued to the right of a word is not an id at all
 * and is skipped. (No id is spelled literally in this file: it sits inside the gate's own scan root and must
 * carry no bare citation.)
 */
function tokensIn(text) {
  const found = [];
  let line = 1;
  let cursor = 0;
  TOKEN.lastIndex = 0;
  for (let m; (m = TOKEN.exec(text)) !== null; ) {
    for (; cursor < m.index; cursor++) if (text[cursor] === '\n') line++;
    if (/[A-Za-z0-9]/.test(text[m.index - 1] ?? '')) continue;
    const prefix = text.slice(Math.max(0, m.index - 4), m.index);
    const beforePrefix = m.index >= 5 ? text[m.index - 5] : '';
    const qualified = m.index >= 4 && /^\d{3}\/$/.test(prefix) && !/[A-Za-z0-9-]/.test(beforePrefix);
    const upstream = qualified ? null : upstreamQualifierBefore(text, m.index);
    // The legacy spelling: the same three digits with a space (or a code/backtick wrapper and a space) in
    // place of the slash. Recorded rather than acted on here; only the spec-tree pass below reads it.
    const spacedM = SPACED.exec(text.slice(Math.max(0, m.index - 12), m.index));
    found.push({
      line,
      id: m[0],
      feature: qualified ? prefix.slice(0, 3) : null,
      upstream,
      spaced: qualified || upstream !== null ? null : (spacedM?.[1] ?? null),
    });
  }
  return found;
}

// ---------------------------------------------------------------------------------------------------------
// The spec tree

/** Feature directories keyed by their NNN, each with its defined ids and whether it still has open tasks. */
function loadFeatures(specsDir, walk, problems) {
  const features = new Map();
  if (!fs.existsSync(specsDir)) return features;
  const entries = fs.readdirSync(specsDir, { withFileTypes: true }).filter((e) => e.isDirectory() && FEATURE_DIR.test(e.name));
  for (const entry of entries.sort((a, b) => (a.name < b.name ? -1 : 1))) {
    const nnn = FEATURE_DIR.exec(entry.name)[1];
    const dir = path.join(specsDir, entry.name);
    const previous = features.get(nnn);
    if (previous) {
      problems.push(`${entry.name}: two feature directories share the prefix ${nnn} (${previous.name} is the other); a citation ${nnn}/... cannot name one of them`);
      continue;
    }
    const specFile = path.join(dir, 'spec.md');
    const ids = new Map();
    if (!fs.existsSync(specFile)) {
      problems.push(`${entry.name}: no spec.md, so no citation of ${nnn}/... can ever resolve`);
    } else {
      const text = readText(specFile) ?? '';
      text.split(/\r?\n/).forEach((l, i) => {
        const m = DEFINITION.exec(l);
        if (!m) return;
        if (ids.has(m[1])) {
          problems.push(`${path.join(entry.name, 'spec.md')}:${i + 1}: ${m[1]} is defined twice (first at line ${ids.get(m[1])}); one id, one requirement`);
          return;
        }
        ids.set(m[1], i + 1);
      });
      if (ids.size === 0) {
        problems.push(`${path.join(entry.name, 'spec.md')}: defines no requirement id at all; either the bullets lost their bold form or the file is not a spec`);
      }
    }
    const tasksFile = path.join(dir, 'tasks.md');
    const tasksText = fs.existsSync(tasksFile) ? (readText(tasksFile) ?? '') : null;
    features.set(nnn, {
      nnn,
      name: entry.name,
      dir,
      ids,
      // Complete means the plan says so: tasks.md exists and every box in it is ticked. Anything else is in
      // flight, and an in-flight feature is not held to coverage -- it is printed instead.
      inFlight: tasksText === null || OPEN_TASK.test(tasksText),
      inFlightReason: tasksText === null ? 'no tasks.md' : 'open tasks',
      walk,
    });
  }
  return features;
}

// ---------------------------------------------------------------------------------------------------------
// The two committed lists

/**
 * Parse a tab-separated committed list with `#` comments, checking emptiness, duplication and ordering as it
 * goes. `columns` is the exact width: 2 for the legacy list (path, reason) and the upstream list (qualifier,
 * location), 3 for the waiver list (id, kind, reason). The width is checked rather than inferred, so a row
 * written in the previous two-column waiver spelling fails loudly instead of being read as a kindless
 * waiver. `emptyLast` is the sentence the last column earns when it is blank, because what a blank means is
 * not the same fact in all three files.
 */
function loadTable(file, label, problems, columns = 2, emptyLast = (key) => `${key} carries no reason; a row with no reason is an exemption nobody has to defend`) {
  const rows = [];
  if (!fs.existsSync(file)) return rows;
  const text = readText(file);
  if (text === null) throw new Fail(`${file} is binary; expected a ${columns}-column TSV`);
  const seen = new Map();
  let previousKey = null;
  text.split(/\r?\n/).forEach((raw, i) => {
    const lineNo = i + 1;
    if (raw.trim().length === 0 || raw.trimStart().startsWith('#')) return;
    const parts = raw.split('\t');
    if (parts.length !== columns) {
      problems.push(`${file}:${lineNo}: expected exactly ${columns} tab-separated column(s), found ${parts.length}`);
      return;
    }
    const key = parts[0].trim();
    const kind = columns === 3 ? parts[1].trim() : null;
    const reason = parts[columns - 1].trim();
    if (key.length === 0) {
      problems.push(`${file}:${lineNo}: empty ${label}`);
      return;
    }
    if (reason.length === 0) {
      problems.push(`${file}:${lineNo}: ${emptyLast(key)}`);
    }
    if (seen.has(key)) {
      problems.push(`${file}:${lineNo}: ${key} is listed twice (first at line ${seen.get(key)})`);
    } else {
      seen.set(key, lineNo);
    }
    if (previousKey !== null && key < previousKey) {
      problems.push(`${file}:${lineNo}: ${key} sorts before ${previousKey} on the line above; the rows are kept sorted so two changes to this file conflict rather than interleave`);
    }
    previousKey = key;
    rows.push({ key, kind, reason, line: lineNo });
  });
  return rows;
}

// ---------------------------------------------------------------------------------------------------------

function parseArgs(argv) {
  const opts = { specs: null, scan: [], testRoots: [], waivers: null, legacy: null, upstreams: null, report: false };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    const value = () => {
      const v = argv[++i];
      if (v === undefined) throw new Fail(`${arg} needs a value`);
      return path.resolve(v);
    };
    if (arg === '--report') opts.report = true;
    else if (arg === '--specs') opts.specs = value();
    else if (arg === '--scan') opts.scan.push(value());
    else if (arg === '--test-root') opts.testRoots.push(value());
    else if (arg === '--waivers') opts.waivers = value();
    else if (arg === '--legacy') opts.legacy = value();
    else if (arg === '--upstreams') opts.upstreams = value();
    else throw new Fail(`unknown argument ${JSON.stringify(arg)}; expected --specs/--scan/--test-root/--waivers/--legacy/--upstreams/--report`);
  }
  return opts;
}

main(() => {
  const opts = parseArgs(process.argv.slice(2));
  const backendRoot = path.resolve(import.meta.dirname, '..');
  const projectRoot = path.resolve(capture('git', ['rev-parse', '--show-toplevel'], { cwd: backendRoot }));
  const vendored = path.relative(projectRoot, backendRoot) !== '';

  const specsDir = opts.specs ?? path.join(projectRoot, 'specs');
  // A flag-supplied root is enumerated by walking it, because the self-test's fixtures are pointed at
  // directories whose shape, not whose git status, is the subject. A default root goes through git ls-files.
  const specsWalk = opts.specs !== null;
  const scanRoots =
    opts.scan.length > 0
      ? opts.scan.map((dir) => ({ dir, walk: true }))
      : [
          { dir: backendRoot, walk: false },
          ...(vendored ? [path.join(projectRoot, 'frontend'), path.join(projectRoot, '.specify', 'memory')] : [])
            .filter((dir) => fs.existsSync(dir))
            .map((dir) => ({ dir, walk: false })),
        ];
  const testRoots =
    opts.testRoots.length > 0 ? opts.testRoots : [path.join(backendRoot, 'src', 'test'), ...frontendTestRoots(projectRoot)];
  const excluded = opts.scan.length > 0 ? [] : excludedFromDefaultScan(backendRoot);
  const waiverFile = opts.waivers ?? path.join(specsDir, WAIVERS);
  const legacyFile = opts.legacy ?? path.join(specsDir, LEGACY);
  const upstreamFile = opts.upstreams ?? path.join(specsDir, UPSTREAMS);
  // Rows in the legacy list name a file relative to the project root. When the list comes in on --legacy it
  // names one relative to its own directory instead, so a fixture is self-contained.
  const legacyBase = opts.legacy ? path.dirname(legacyFile) : projectRoot;

  // Project-relative for readability, absolute when the path is outside the project (a fixture run, mostly),
  // because a printed `../../../..` is harder to act on than the full path.
  const rel = (file) => {
    const r = path.relative(projectRoot, file);
    return r.startsWith('..') ? file : r.split(path.sep).join('/');
  };
  const problems = [];
  const notes = [];

  // --- the spec tree (check f) ---------------------------------------------------------------------------
  if (!fs.existsSync(specsDir)) notes.push(`no specs directory at ${rel(specsDir)}: zero requirement ids are defined, so every citation is dangling and every bare id still fails`);
  const features = loadFeatures(specsDir, specsWalk, problems);

  // --- the legacy list (check e) -------------------------------------------------------------------------
  const legacyRows = loadTable(legacyFile, 'path', problems);
  const legacyPaths = new Set();
  for (const row of legacyRows) {
    const file = path.resolve(legacyBase, row.key);
    if (!fs.existsSync(file)) {
      problems.push(`${legacyFile}:${row.line}: ${row.key} does not exist; a row here exempts a file that is not there`);
      continue;
    }
    legacyPaths.add(file);
    const text = readText(file);
    const bare = text === null ? [] : tokensIn(text).filter((t) => t.feature === null && t.upstream === null);
    if (bare.length === 0) {
      problems.push(`${legacyFile}:${row.line}: ${row.key} contains no bare id; the row is stale and goes`);
    }
  }

  // --- the upstream list (check g) -----------------------------------------------------------------------
  // Declared, never resolved: the document a qualifier names lives in another repository that CI does not
  // check out, so the only thing this file can buy is that the citation says *which* document it means.
  const upstreamRows = loadTable(
    upstreamFile,
    'qualifier',
    problems,
    2,
    (key) => `${key} carries no location; a qualifier with no location names a document nobody can go and read`,
  );
  /** qualifier -> { location, line, count, where: Set<string> } */
  const upstreams = new Map();
  for (const row of upstreamRows) {
    if (!UPSTREAM_QUALIFIER.test(row.key)) {
      problems.push(
        `${upstreamFile}:${row.line}: ${row.key} is not a qualifier; expected an uppercase letter followed by uppercase letters, digits or hyphens, so that no qualifier can ever be read as a feature's three-digit prefix`,
      );
      continue;
    }
    upstreams.set(row.key, { location: row.reason, line: row.line, count: 0, where: new Set() });
  }
  const upstreamUnknown = [];
  /** Where an upstream citation was seen: the feature directory when it is one, else the file itself. */
  const whereOf = (file) => {
    const r = path.relative(specsDir, file);
    if (r.startsWith('..')) return rel(file);
    const first = r.split(path.sep)[0];
    return FEATURE_DIR.test(first) ? first : rel(file);
  };
  const noteUpstream = (token, file) => {
    const entry = upstreams.get(token.upstream);
    if (!entry) {
      upstreamUnknown.push(
        `${rel(file)}:${token.line}: ${token.upstream}/${token.id} names the qualifier ${token.upstream}, which no row in ${rel(upstreamFile)} declares`,
      );
      return;
    }
    entry.count++;
    entry.where.add(whereOf(file));
  };

  // --- collect every citation ----------------------------------------------------------------------------
  const isUnder = (file, dir) => file === dir || file.startsWith(dir + path.sep);
  const inTestRoot = (file) => testRoots.some((root) => isUnder(file, root));

  const bareHits = [];
  const inSpecHits = [];
  const danglingFeature = [];
  const danglingId = [];
  /** `NNN/ID` -> { tests: Set<path>, other: Set<path> } over the scan roots only. */
  const citations = new Map();
  const noteCitation = (key, file) => {
    const entry = citations.get(key) ?? { tests: new Set(), other: new Set() };
    (inTestRoot(file) ? entry.tests : entry.other).add(rel(file));
    citations.set(key, entry);
  };
  const resolve = (token, file) => {
    const feature = features.get(token.feature);
    if (!feature) {
      danglingFeature.push(`${rel(file)}:${token.line}: ${token.feature}/${token.id} names no feature directory under ${rel(specsDir)}`);
      return false;
    }
    if (!feature.ids.has(token.id)) {
      danglingId.push(`${rel(file)}:${token.line}: ${token.feature}/${token.id} is not defined in ${rel(path.join(feature.dir, 'spec.md'))}`);
      return false;
    }
    return true;
  };

  let scanned = 0;
  let citationCount = 0;
  for (const root of scanRoots) {
    for (const file of filesUnder(root.dir, root.walk)) {
      if (excluded.some((dir) => isUnder(file, dir))) continue;
      const text = readText(file);
      if (text === null) continue;
      scanned++;
      const exempt = legacyPaths.has(file);
      for (const token of tokensIn(text)) {
        // check g. An upstream citation is accepted on its declared qualifier and goes no further: it is
        // not bare, it resolves to nothing here, and it is coverage of nothing.
        if (token.upstream !== null) {
          noteUpstream(token, file);
          continue;
        }
        if (token.feature === null) {
          // check a. A legacy-listed file's bare ids are ignored entirely, not merely tolerated.
          if (!exempt) bareHits.push(`${rel(file)}:${token.line}: ${token.id} is bare; write it as NNN/${token.id}`);
          continue;
        }
        citationCount++;
        // check b, and the coverage evidence for check c.
        if (resolve(token, file)) noteCitation(`${token.feature}/${token.id}`, file);
      }
    }
  }
  // check b and b2, over the spec tree. A qualified cross-reference resolves or it is a broken pointer like
  // any other. A bare id inside specs/<NNN>-<name>/ is that feature's own and is legal only if that feature
  // defines it; the legacy spaced form is not a citation there either; and a file under specs/ that is in no
  // feature directory has no "own feature", so a bare id in it is bare exactly as in a scan root.
  const ownerOf = (file) => {
    const r = path.relative(specsDir, file);
    if (r.startsWith('..')) return null;
    const m = FEATURE_DIR.exec(r.split(path.sep)[0]);
    return m ? (features.get(m[1]) ?? null) : null;
  };
  for (const file of filesUnder(specsDir, specsWalk)) {
    const text = readText(file);
    if (text === null) continue;
    const owner = ownerOf(file);
    for (const token of tokensIn(text)) {
      if (token.upstream !== null) {
        noteUpstream(token, file);
      } else if (token.feature !== null) {
        resolve(token, file);
      } else if (token.spaced !== null) {
        inSpecHits.push(
          `${rel(file)}:${token.line}: ${token.spaced} ${token.id} is the legacy spaced form; write it as ${token.spaced}/${token.id}`,
        );
      } else if (owner === null) {
        bareHits.push(`${rel(file)}:${token.line}: ${token.id} is bare; write it as NNN/${token.id}`);
      } else if (!owner.ids.has(token.id)) {
        inSpecHits.push(
          `${rel(file)}:${token.line}: ${token.id} is bare inside ${owner.name}, so it names that feature's own id -- but ${rel(path.join(owner.dir, 'spec.md'))} defines no ${token.id}; another feature's id is written NNN/${token.id}`,
        );
      }
    }
  }

  // check g, the other direction. A declared qualifier nothing cites is a pointer to a document this repo no
  // longer reads, and a list of those is a list a reader stops trusting.
  for (const [qualifier, entry] of upstreams) {
    if (entry.count === 0) {
      problems.push(`${upstreamFile}:${entry.line}: ${qualifier} is cited nowhere; the row is stale and goes`);
    }
  }

  // --- waivers (check d) ---------------------------------------------------------------------------------
  const waiverRows = loadTable(waiverFile, 'id', problems, 3);
  const waived = new Map();
  for (const row of waiverRows) {
    const m = /^(\d{3})\/((?:FR|SC)-\d{3}[a-z]?)$/.exec(row.key);
    if (!m) {
      problems.push(`${waiverFile}:${row.line}: ${row.key} is not a qualified id; expected NNN/FR-nnn or NNN/SC-nnn`);
      continue;
    }
    if (!WAIVER_KINDS.has(row.kind)) {
      problems.push(
        `${waiverFile}:${row.line}: ${row.key} carries the kind ${JSON.stringify(row.kind)}; a waiver is ${[...WAIVER_KINDS.keys()].join(' or ')}, nothing else` +
          [...WAIVER_KINDS].map(([k, meaning]) => `\n      ${k}: ${meaning}`).join(''),
      );
      continue;
    }
    const feature = features.get(m[1]);
    if (!feature || !feature.ids.has(m[2])) {
      problems.push(`${waiverFile}:${row.line}: ${row.key} resolves to no requirement; a waiver of nothing waives nothing`);
      continue;
    }
    if ((citations.get(row.key)?.tests.size ?? 0) > 0) {
      problems.push(`${waiverFile}:${row.line}: ${row.key} is waived but is cited from a test; the waiver is stale and goes`);
      continue;
    }
    waived.set(row.key, { kind: row.kind, reason: row.reason });
  }

  // --- coverage (check c) --------------------------------------------------------------------------------
  const uncovered = [];
  const inFlight = [];
  for (const feature of [...features.values()].sort((a, b) => (a.nnn < b.nnn ? -1 : 1))) {
    const missing = [...feature.ids.keys()]
      .sort()
      .filter((id) => (citations.get(`${feature.nnn}/${id}`)?.tests.size ?? 0) === 0 && !waived.has(`${feature.nnn}/${id}`));
    if (feature.inFlight) {
      inFlight.push({ feature, missing });
      continue;
    }
    for (const id of missing) {
      uncovered.push(`${feature.nnn}/${id}: defined at ${rel(path.join(feature.dir, 'spec.md'))}:${feature.ids.get(id)}, cited from no file under a test root and not waived`);
    }
  }

  // --- report mode ---------------------------------------------------------------------------------------
  if (opts.report) {
    for (const feature of [...features.values()].sort((a, b) => (a.nnn < b.nnn ? -1 : 1))) {
      console.log(`\n${feature.name}  (${feature.inFlight ? `in flight -- ${feature.inFlightReason}` : 'complete'}, ${feature.ids.size} id(s))`);
      for (const id of [...feature.ids.keys()].sort()) {
        const key = `${feature.nnn}/${id}`;
        const entry = citations.get(key) ?? { tests: new Set(), other: new Set() };
        const tests = [...entry.tests].sort();
        const other = [...entry.other].sort();
        const mark = waived.has(key) ? `  waived (${waived.get(key).kind}): ${waived.get(key).reason}` : '';
        console.log(`  ${key}${mark}`);
        console.log(`      tests: ${tests.length > 0 ? tests.join(', ') : '-'}`);
        console.log(`      other: ${other.length > 0 ? other.join(', ') : '-'}`);
      }
    }
    console.log('');
    printUpstreams(upstreams, rel(upstreamFile));
    printInFlight(inFlight);
    printDoesNotDecide({ notes, scanned, citationCount, features, testRoots, rel, inFlight, upstreams });
    printDeferred(waived);
    return;
  }

  // --- verdict -------------------------------------------------------------------------------------------
  printInFlight(inFlight);
  const sections = [
    ['Bare requirement ids (a citation that names four requirements at once names none of them)', bareHits],
    ['Citations naming a feature directory that does not exist', danglingFeature],
    ['Citations naming an id that feature does not define', danglingId],
    ['Ids inside a feature directory that do not resolve there (an undefined bare id, or the legacy spaced form)', inSpecHits],
    ['Requirements of a complete feature with no test citation and no waiver', uncovered],
    [`Upstream citations whose qualifier no row in ${UPSTREAMS} declares`, upstreamUnknown],
    [`Problems in the spec tree, ${WAIVERS}, ${LEGACY} and ${UPSTREAMS}`, problems],
  ];
  const failed = sections.filter(([, hits]) => hits.length > 0);
  const upstreamCount = [...upstreams.values()].reduce((n, e) => n + e.count, 0);
  console.log(`${scanned} file(s) scanned, ${citationCount} qualified citation(s), ${upstreamCount} upstream citation(s) across ${upstreams.size} declared qualifier(s), ${[...features.values()].reduce((n, f) => n + f.ids.size, 0)} requirement id(s) across ${features.size} feature(s)`);
  printDoesNotDecide({ notes, scanned, citationCount, features, testRoots, rel, inFlight, upstreams });

  if (failed.length > 0) {
    throw new Fail(
      failed
        .map(([title, hits]) => `\n${title} (${hits.length}):\n` + hits.map((h) => `  - ${h}`).join('\n'))
        .join('\n'),
    );
  }
  console.log('spec<->code traceability green');
});

/**
 * The repo's list of specified-but-unbuilt requirements, printed at the end of `--report` as a list of its
 * own. An `external` waiver is settled -- nothing here will ever witness it -- but a `deferred` one is a debt,
 * and a debt nobody can read off in one place is a debt nobody pays.
 */
function printDeferred(waived) {
  const deferred = [...waived].filter(([, w]) => w.kind === 'deferred').sort(([a], [b]) => (a < b ? -1 : 1));
  console.log('\nDeferred -- specified, deliberately not built here yet (waived `deferred`):');
  if (deferred.length === 0) {
    console.log('  (none: every waiver on this run is `external`, or there are no waivers at all)');
    return;
  }
  for (const [key, w] of deferred) console.log(`  ${key}: ${w.reason}`);
}

/**
 * The declared upstream documents and what still cites them, printed under `--report`. The count is the
 * weight a qualifier carries; the feature directories beside it are what a reader has to re-read when the
 * upstream document moves, since nothing here resolves a single one of those ids.
 */
function printUpstreams(upstreams, listPath) {
  console.log(`Upstream documents (${listPath}) -- cited, never resolved:`);
  if (upstreams.size === 0) {
    console.log('  (none declared)');
    return;
  }
  for (const [qualifier, entry] of [...upstreams].sort(([a], [b]) => (a < b ? -1 : 1))) {
    console.log(`  ${qualifier} -> ${entry.location}`);
    console.log(`      ${entry.count} citation(s) from: ${entry.where.size > 0 ? [...entry.where].sort().join(', ') : '-'}`);
  }
}

/** Loud on every run: an in-flight feature is exempt from coverage, so its gap is printed instead of gated. */
function printInFlight(inFlight) {
  if (inFlight.length === 0) return;
  console.log('\n!! IN-FLIGHT FEATURES -- coverage is NOT gated for these, it is only reported:');
  for (const { feature, missing } of inFlight) {
    console.log(`!!   ${feature.name} (${feature.inFlightReason}): ${missing.length} of ${feature.ids.size} id(s) with no test citation`);
    for (const id of missing) console.log(`!!     ${feature.nnn}/${id}`);
  }
  console.log('!! They are gated the moment their tasks.md has no open task left.\n');
}

/** Assembled from what this run actually saw, so it cannot go stale against the gate beside it. */
function printDoesNotDecide({ notes, scanned, citationCount, features, testRoots, rel, inFlight, upstreams }) {
  const complete = [...features.values()].filter((f) => !f.inFlight).length;
  const upstreamCount = [...upstreams.values()].reduce((n, e) => n + e.count, 0);
  const out = ['', 'This gate does not decide:'];
  out.push(`  - that a cited test asserts the requirement. It read ${citationCount} citation(s) across ${scanned} file(s) and believed every one of them: a citation is a claim, and mutation testing and review are what turn a claim into evidence.`);
  out.push('  - anything about user stories, acceptance scenarios or edge cases. They carry no ids, so nothing here can point at them and nothing here notices when one is dropped.');
  out.push(
    testRoots.length === 0
      ? '  - what counts as a test, because no test root is configured on this run.'
      : `  - the frontend. Test roots on this run: ${testRoots.map(rel).join(', ')}. The frontend is a separate static deploy and is not started, so it contributes no test root and no citation of its own; frontendTestRoots() in this file is where it lands.`,
  );
  out.push(
    inFlight.length > 0
      ? `  - coverage of the ${inFlight.length} in-flight feature(s) printed above; they are held to resolution only until their tasks are closed.`
      : complete === 0
        ? '  - coverage of anything, because no feature was found to hold to it.'
        : `  - coverage of an in-flight feature. All ${complete} feature(s) here are complete on this run, so nothing was exempt.`,
  );
  out.push(
    `  - anything about the ${upstreamCount} upstream citation(s) it read across ${upstreams.size} declared qualifier(s). An upstream id is accepted on its qualifier alone: the document lives in another repository that this run never opened, so nothing here checks that the upstream defines that id, that the id still means what the citing sentence says, or that the location the row names is still where the document is.`,
  );
  out.push(
    '  - an upstream id somebody writes bare. A bare id inside a feature directory is read as that feature\'s own, so an upstream one is caught only while its number happens to define nothing locally; the moment the numbers collide it reads as a resolving local citation and nothing here can tell the difference.',
  );
  for (const note of notes) out.push(`  - anything that needs the spec tree: ${note}.`);
  console.log(out.join('\n'));
}
