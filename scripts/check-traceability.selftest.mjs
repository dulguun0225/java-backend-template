// The canary for check-traceability.mjs, in the spirit of BanListNegativeControlTest and migration-fixtures/:
// a gate that would pass over anything is not a gate, and a traceability gate is the easy shape to get wrong
// that way -- tighten a regex by one character and it stops matching, reports nothing, and reads green.
//
// So every failure class the gate claims gets a fixture that exhibits exactly it, and this script asserts two
// things per fixture: the exit status, and that the offending token appears in the output. A defect the gate
// misses fails here. The passing fixtures are the other half of the control: a gate that fails on everything
// is no more useful than one that fails on nothing.
//
// The fixtures live in fixtures/traceability/<case>/ and are excluded from the gate's own default scan by
// explicit path, because they are deliberately full of the defects it catches. Nothing in this file spells a
// bare requirement id literally either -- the ids are assembled from parts, so this script needs no exemption.
//
// One case cannot be a committed fixture and is built instead; see `deletedTrackedFileCase` at the end.
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { Fail, main } from './_lib.mjs';

const here = import.meta.dirname;
const GATE = path.join(here, 'check-traceability.mjs');
const FIXTURES = path.join(here, 'fixtures', 'traceability');

// Assembled, never written out: this file sits inside the gate's scan root and must carry no bare id itself.
const fr = (n) => `FR-${n}`;
const sc = (n) => `SC-${n}`;
const q = (feature, id) => `${feature}/${id}`;
// The two foreign qualifiers the fixtures write. Both end in digits on purpose: `CAP-NC02-04` ends in the
// two that would read as a feature if the tail rather than the whole run were taken, and `DOC-001` ends in
// exactly the three digits a feature qualifier is spelled with.
const CAP = 'CAP-NC02-04';
const DOC = 'DOC-001';

/**
 * Every fixture, what the gate must do with it, and the text that proves it looked at the right thing.
 * `pass` is an exit status of 0. `contains` is checked against stdout and stderr together.
 */
const CASES = [
  // --- the gate says yes -----------------------------------------------------------------------------
  { dir: 'clean', pass: true, contains: ['traceability green'], what: 'a complete feature whose every id is cited from a test' },
  { dir: 'suffixed', pass: true, contains: ['traceability green'], what: `a suffixed id (${fr('006a')}) resolves and counts as covered` },
  { dir: 'suffixed', args: ['--report'], pass: true, contains: [q('001', fr('006a')), 'SuffixTest.java'], what: 'the report names the suffixed id and the test that cites it' },
  {
    dir: 'in-flight',
    pass: true,
    contains: ['IN-FLIGHT', q('001', fr('002')), 'open tasks'],
    what: 'an uncovered id of a feature with an open task is printed loudly and not gated',
  },
  { dir: 'legacy-exempt', pass: true, contains: ['traceability green'], what: "a listed shipped migration's bare id is ignored entirely" },
  {
    dir: 'in-spec-cross-feature',
    pass: true,
    contains: ['traceability green'],
    what: 'a qualified cross-feature reference inside another feature\'s spec directory is legal',
  },
  {
    dir: 'waiver-accepted',
    pass: true,
    contains: ['traceability green'],
    what: 'the two accepted waiver kinds close an uncovered id',
  },
  {
    dir: 'waiver-accepted',
    args: ['--report'],
    pass: true,
    contains: [`${q('001', fr('002'))}  waived (deferred)`, `${q('001', sc('001'))}  waived (external)`, 'Deferred -- specified', `  ${q('001', fr('002'))}: the notification half`],
    what: 'the report marks each waiver with its kind and lists the deferred ids on their own at the end',
  },
  {
    dir: 'foreign-accepted',
    pass: true,
    contains: ['traceability green'],
    what: `a foreign-qualified token is prose both inside a feature directory (${CAP}/...) and in a file inside none (${DOC}/...): neither refused as bare nor resolved, and the tail of ${DOC} is not read as the feature 001`,
  },
  { dir: 'tasks-waived', pass: true, contains: ['traceability green'], what: 'a requirement no task names is closed by a waiver' },
  {
    dir: 'clean',
    args: ['--report'],
    pass: true,
    contains: ['Spec -> tasks', '3 of 3 id(s) named by a task, 0 not'],
    what: 'the report prints the spec -> tasks gap per feature',
  },

  // --- the gate says no, on spec -> tasks --------------------------------------------------------------
  {
    dir: 'tasks-missing-id',
    pass: false,
    contains: [`${q('001', fr('002'))}: defined at`, 'named by no task in'],
    what: 'a requirement no task in its own feature names, and no waiver covers',
  },
  {
    dir: 'foreign-not-tasks',
    pass: false,
    contains: [`${q('001', fr('002'))}: defined at`, 'named by no task in'],
    what: `a task naming a local id only through a foreign-qualified token (${CAP}/...) plans nothing: the local id of that number is still unplanned`,
  },

  {
    dir: 'foreign-not-coverage',
    pass: false,
    contains: [`${q('001', fr('002'))}: defined at`, 'no file under a test root'],
    what: `a foreign-qualified token in a test covers nothing: the local id of the same number is still uncovered`,
  },

  // --- the gate says no, on everything else -----------------------------------------------------------
  { dir: 'bare-id', pass: false, contains: [`${fr('002')} is bare`, 'Bare.java'], what: 'a bare id in a scan root' },
  { dir: 'legacy-spaced', pass: false, contains: [`${fr('002')} is bare`, 'Spaced.java'], what: 'the legacy spaced form counts as bare' },
  {
    dir: 'slash-between-ids',
    pass: false,
    contains: [`${fr('002')} is bare`, 'Range.java'],
    what: 'the digits of an id in front of a slash are not a feature qualifier',
  },
  {
    dir: 'in-spec-undefined',
    pass: false,
    contains: [`${fr('777')} is bare inside 001-alpha`, 'plan.md'],
    what: "a bare id inside a feature directory that the feature's own spec does not define",
  },
  {
    dir: 'in-spec-spaced',
    pass: false,
    contains: [`001 ${fr('001')} is the legacy spaced form`, 'plan.md'],
    what: 'the legacy spaced form inside a feature directory',
  },
  { dir: 'dangling-feature', pass: false, contains: [`${q('009', fr('001'))} names no feature directory`], what: 'a citation of a feature directory that does not exist' },
  { dir: 'dangling-id', pass: false, contains: [`${q('001', fr('777'))} is not defined`], what: 'a citation of an id the feature does not define' },
  { dir: 'uncovered-complete', pass: false, contains: [`${q('001', fr('002'))}: defined at`, 'no file under a test root'], what: 'an uncovered id of a complete feature' },
  { dir: 'stale-waiver', pass: false, contains: [`${q('001', fr('002'))} is waived but is cited from a test`], what: 'a waiver whose id is in fact covered' },
  { dir: 'waiver-no-reason', pass: false, contains: [`${q('001', fr('002'))} carries no reason`], what: 'a waiver with an empty reason' },
  { dir: 'waiver-dangling', pass: false, contains: [`${q('001', fr('999'))} resolves to no requirement`], what: 'a waiver of an id that does not exist' },
  {
    dir: 'waiver-unknown-kind',
    pass: false,
    contains: [`${q('001', fr('002'))} carries the kind "someday"`, 'external or deferred'],
    what: 'a waiver kind outside the two accepted ones',
  },
  {
    dir: 'waiver-two-column',
    pass: false,
    contains: ['expected exactly 3 tab-separated column(s), found 2'],
    what: 'a waiver row in the legacy two-column spelling, with no kind',
  },
  { dir: 'waiver-unsorted', pass: false, contains: [`${q('001', fr('002'))} sorts before ${q('001', sc('001'))}`], what: 'waiver rows out of order' },
  { dir: 'waiver-duplicate', pass: false, contains: [`${q('001', fr('002'))} is listed twice`], what: 'a duplicated waiver row' },
  { dir: 'legacy-stale', pass: false, contains: ['V1__shipped.sql contains no bare id'], what: 'a legacy row for a file that no longer needs one' },
  { dir: 'legacy-missing', pass: false, contains: ['V9__gone.sql does not exist'], what: 'a legacy row for a file that is not there' },
  { dir: 'zero-def-spec', pass: false, contains: ['defines no requirement id at all'], what: 'a spec.md that defines nothing' },
  { dir: 'duplicate-def', pass: false, contains: [`${fr('001')} is defined twice`], what: 'one id defined twice inside one spec' },
  { dir: 'duplicate-prefix', pass: false, contains: ['two feature directories share the prefix 001'], what: 'two feature directories with one numeric prefix' },
];

const indent = (text) => text.split('\n').map((l) => `      ${l}`).join('\n');

/** Run the gate against one fixture, pointed at it entirely by flag, and return its status and its output. */
function runGate(dir, extra = []) {
  const root = path.join(FIXTURES, dir);
  const args = [
    GATE,
    '--specs', path.join(root, 'specs'),
    '--scan', path.join(root, 'scan'),
    '--test-root', path.join(root, 'scan', 'src', 'test'),
  ];
  // A fixture supplies a waiver or a legacy list only when its case is about one; the gate treats both as
  // optional.
  const waivers = path.join(root, 'trace-waivers.tsv');
  const legacy = path.join(root, 'trace-legacy-files.tsv');
  if (fs.existsSync(waivers)) args.push('--waivers', waivers);
  if (fs.existsSync(legacy)) args.push('--legacy', legacy);
  const r = spawnSync(process.execPath, [...args, ...extra], { encoding: 'utf8' });
  if (r.error) throw r.error;
  return { status: r.status ?? 1, output: `${r.stdout}${r.stderr}` };
}

/**
 * git, run hermetically: no global and no system configuration is read, the identity is supplied inline and
 * nothing is signed, so this case behaves the same on a machine whose git is configured and on one whose is
 * not. `GIT_CONFIG_GLOBAL` is pointed at a path inside the throwaway tree that is never created, which is the
 * portable spelling of "there is no global config" -- `/dev/null` is not a path on Windows.
 */
function git(cwd, noGlobal, args) {
  const r = spawnSync(
    'git',
    ['-c', 'user.name=canary', '-c', 'user.email=canary@example.invalid', '-c', 'commit.gpgsign=false', ...args],
    { cwd, encoding: 'utf8', env: { ...process.env, GIT_CONFIG_GLOBAL: noGlobal, GIT_CONFIG_NOSYSTEM: '1' } },
  );
  if (r.error) throw r.error;
  if ((r.status ?? 1) !== 0) throw new Fail(`git ${args.join(' ')} exited ${r.status}\n${indent(`${r.stdout}${r.stderr}`)}`);
  return r.stdout;
}

const write = (file, text) => {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, text);
};

/**
 * The one case no committed fixture can carry, and the reason it is built instead.
 *
 * A default scan root is enumerated with `git ls-files`, which lists the *index*: a file deleted from the
 * working tree and not yet staged is still in the index, so it is listed and is not on disk. No committed
 * fixture tree can hold a file that is at once tracked and absent, and the flag-supplied roots every fixture
 * case uses are walked rather than listed, so only a run on the gate's *default* roots reads git's listing at
 * all. So this case builds a throwaway repository laid out the way the gate discovers one -- the script under
 * `backend/scripts/`, the spec tree at `specs/` beside it -- commits it, deletes one tracked file from each
 * listed root, and asserts the gate still returns its normal verdict instead of dying on the absent path.
 *
 * Both deleted files carry text that would fail the gate if it were read, so the case also pins which copy is
 * authoritative: the gate reads the working tree, never the blob the index still holds.
 */
function deletedTrackedFileCase(failures) {
  const label = 'deleted-tracked-file (a tracked file deleted from the working tree is listed by git and absent on disk)';
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'trace-canary-'));
  try {
    const repo = path.join(tmp, 'repo');
    const noGlobal = path.join(tmp, 'no-global-gitconfig');
    const scripts = path.join(repo, 'backend', 'scripts');
    write(path.join(scripts, 'check-traceability.mjs'), fs.readFileSync(GATE));
    write(path.join(scripts, '_lib.mjs'), fs.readFileSync(path.join(here, '_lib.mjs')));
    write(path.join(repo, 'specs', '001-alpha', 'spec.md'), `# Alpha\n\n- **${fr('001')}**: the alpha requirement\n`);
    write(path.join(repo, 'specs', '001-alpha', 'tasks.md'), `# Tasks\n\n- [x] T001 build ${fr('001')}\n`);
    write(path.join(repo, 'backend', 'src', 'test', 'AlphaTest.java'), `// covers ${q('001', fr('001'))}\nclass AlphaTest {}\n`);
    // The two files that go: one under the backend scan root, one inside the spec tree, because both are
    // listed with git and each is read by a pass of its own.
    write(path.join(repo, 'backend', 'src', 'main', 'Removed.java'), `// ${fr('001')}\nclass Removed {}\n`);
    write(path.join(repo, 'specs', '001-alpha', 'removed.md'), `Removed: ${fr('777')}\n`);

    git(repo, noGlobal, ['init', '-q', '-b', 'main']);
    git(repo, noGlobal, ['add', '-A']);
    git(repo, noGlobal, ['commit', '-q', '-m', 'canary']);
    fs.rmSync(path.join(repo, 'backend', 'src', 'main', 'Removed.java'));
    fs.rmSync(path.join(repo, 'specs', '001-alpha', 'removed.md'));
    const listed = git(repo, noGlobal, ['ls-files']);
    for (const gone of ['backend/src/main/Removed.java', 'specs/001-alpha/removed.md']) {
      if (!listed.includes(gone)) {
        failures.push(`${label}: ${gone} is not listed by git ls-files, so the case proves nothing`);
        return;
      }
    }

    const r = spawnSync(process.execPath, [path.join(scripts, 'check-traceability.mjs')], { cwd: repo, encoding: 'utf8' });
    if (r.error) throw r.error;
    const status = r.status ?? 1;
    const output = `${r.stdout}${r.stderr}`;
    if (status !== 0) {
      failures.push(`${label}: expected the gate to pass, it exited ${status}\n${indent(output)}`);
      return;
    }
    for (const needle of ['traceability green', 'This gate does not decide:']) {
      if (!output.includes(needle)) {
        failures.push(`${label}: the gate passed but never printed ${JSON.stringify(needle)}\n${indent(output)}`);
      }
    }
    // The deleted files' text is never reported: a deleted file carries no citations.
    for (const needle of [fr('777'), 'Removed.java']) {
      if (output.includes(needle)) {
        failures.push(`${label}: the gate read a file that is not on disk -- it named ${JSON.stringify(needle)}\n${indent(output)}`);
      }
    }
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

main(() => {
  if (!fs.existsSync(FIXTURES)) throw new Fail(`no fixtures at ${FIXTURES}; the canary has nothing to sing about`);
  const failures = [];

  for (const testCase of CASES) {
    const label = `${testCase.dir}${testCase.args ? ` ${testCase.args.join(' ')}` : ''} (${testCase.what})`;
    const { status, output } = runGate(testCase.dir, testCase.args);
    const passed = status === 0;
    if (passed !== testCase.pass) {
      failures.push(`${label}: expected the gate to ${testCase.pass ? 'pass' : 'fail'}, it exited ${status}\n${indent(output)}`);
      continue;
    }
    for (const needle of testCase.contains) {
      if (!output.includes(needle)) {
        failures.push(`${label}: the gate ${passed ? 'passed' : 'failed'} as expected but never named ${JSON.stringify(needle)}\n${indent(output)}`);
      }
    }
    // Every run, pass or fail, states what it does not decide; a gate read as a coverage proof is the hazard.
    if (!output.includes('This gate does not decide:')) {
      failures.push(`${label}: the run printed no "does not decide" block`);
    }
  }

  deletedTrackedFileCase(failures);

  // The completeness half, as BanListNegativeControlTest does it for the ban rules: a fixture nobody asserts
  // is a defect class nobody checks, so it fails here rather than sitting in the tree looking like coverage.
  const onDisk = fs.readdirSync(FIXTURES, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => e.name).sort();
  const exercised = new Set(CASES.map((c) => c.dir));
  for (const dir of onDisk) if (!exercised.has(dir)) failures.push(`fixtures/traceability/${dir} is exercised by no case in this file`);
  for (const dir of exercised) if (!onDisk.includes(dir)) failures.push(`case ${dir} names no fixture directory`);

  if (failures.length > 0) throw new Fail(failures.map((f) => `  - ${f}`).join('\n\n'));
  console.log(
    `check-traceability.mjs: ${CASES.length} case(s) over ${onDisk.length} fixture(s) plus 1 built case, every failure class caught`,
  );
});

