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
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { Fail, main } from './_lib.mjs';

const here = import.meta.dirname;
const GATE = path.join(here, 'check-traceability.mjs');
const FIXTURES = path.join(here, 'fixtures', 'traceability');

// Assembled, never written out: this file sits inside the gate's scan root and must carry no bare id itself.
const fr = (n) => `FR-${n}`;
const sc = (n) => `SC-${n}`;
const q = (feature, id) => `${feature}/${id}`;
// The two upstream qualifiers the fixtures declare. Both end in digits on purpose: `CAP-NC02-04` ends in the
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
    dir: 'upstream-accepted',
    pass: true,
    contains: ['traceability green'],
    what: `a declared upstream qualifier is accepted inside a feature directory and in a scan root, and the tail of ${CAP} is not read as the feature 04`,
  },
  {
    dir: 'upstream-accepted',
    args: ['--report'],
    pass: true,
    contains: [
      `${CAP} -> netos-spec:specs/nc-02/04-product-gl-config/spec.md`,
      '2 citation(s) from: 001-alpha',
      `${DOC} -> netos-spec:specs/nc-02/04-product-gl-config/checklists/requirements.md`,
      'Upstream.java',
      '2 requirement(s): 2 cited, 0 dropped, 0 deferred, 0 unaccounted',
    ],
    what: 'the report gives each declared qualifier its location, its citation count, the feature directories citing it and its accounting',
  },
  {
    dir: 'dropped-accepted',
    pass: true,
    contains: ['traceability green'],
    what: 'an upstream requirement this repo does not take is accounted for by a dropped row, in both its kinds',
  },
  {
    dir: 'dropped-accepted',
    args: ['--report'],
    pass: true,
    contains: [
      '3 requirement(s): 1 cited, 1 dropped, 1 deferred, 0 unaccounted',
      `dropped: ${fr('034a')}`,
      `deferred: ${sc('001')}`,
      `  ${CAP}/${sc('001')}: a later feature here takes it`,
    ],
    what: 'the report counts the accounting per qualifier and lists the deferred upstream id with the waived ones at the end',
  },
  { dir: 'tasks-waived', pass: true, contains: ['traceability green'], what: 'a requirement no task names is closed by a waiver' },
  {
    dir: 'clean',
    args: ['--report'],
    pass: true,
    contains: ['Spec -> tasks', '3 of 3 id(s) named by a task, 0 not'],
    what: 'the report prints the spec -> tasks gap per feature',
  },

  // --- the gate says no, on the upstream form ---------------------------------------------------------
  {
    dir: 'upstream-citation-dangling',
    pass: false,
    contains: [`${CAP}/${fr('999')} is not defined in`, `upstream/${CAP}.md`],
    what: 'an upstream citation naming an id the qualifier\'s snapshot does not define',
  },
  {
    dir: 'upstream-unaccounted',
    pass: false,
    contains: [`${CAP}/${fr('034a')}: defined at`, 'carrying no row in trace-upstream-dropped.tsv'],
    what: 'an upstream requirement nothing cites and no dropped row carries',
  },
  {
    dir: 'upstream-snapshot-missing',
    pass: false,
    contains: [`${CAP} has no snapshot at`, 'refresh-upstream-snapshot.mjs'],
    what: 'a declared upstream whose document is not committed here',
  },
  {
    dir: 'upstream-snapshot-edited',
    pass: false,
    contains: ['hashes to', 'is never edited here'],
    what: 'a snapshot edited by hand instead of re-taken, caught on its blob sha',
  },
  {
    dir: 'upstream-snapshot-bad-sha',
    pass: false,
    contains: [`${CAP} carries the snapshot sha "-"`, 'expected a full 40-character git blob sha'],
    what: 'a snapshot sha column that is not a blob sha at all',
  },
  {
    dir: 'upstream-bad-source-sha',
    pass: false,
    contains: [`${CAP} carries the source revision "a1b2c3d"`, 'expected a full 40-character commit sha'],
    what: 'an abbreviated source revision, which names more than one revision',
  },
  {
    dir: 'upstream-bad-feature',
    pass: false,
    contains: [`${CAP} names the feature "alpha"`, 'expected a three-digit feature prefix'],
    what: 'an upstream row whose feature column is not a feature prefix',
  },
  {
    dir: 'dropped-stale',
    pass: false,
    contains: [`${CAP}/${fr('002')} is listed here but is cited from`, 'the row is stale and goes'],
    what: 'a dropped row for an upstream id the feature does cite',
  },
  {
    dir: 'dropped-dangling',
    pass: false,
    contains: [`${CAP}/${fr('999')} is defined in no`, 'the row is stale and goes'],
    what: 'a dropped row for an id the snapshot does not define',
  },
  {
    dir: 'dropped-unknown-kind',
    pass: false,
    contains: [`${CAP}/${fr('034a')} carries the kind "someday"`, 'dropped or deferred'],
    what: 'a dropped-row kind outside the two accepted ones',
  },

  // --- the gate says no, on spec -> tasks --------------------------------------------------------------
  {
    dir: 'tasks-missing-id',
    pass: false,
    contains: [`${q('001', fr('002'))}: defined at`, 'named by no task in'],
    what: 'a requirement no task in its own feature names, and no waiver covers',
  },

  {
    dir: 'upstream-not-coverage',
    pass: false,
    contains: [`${q('001', fr('002'))}: defined at`, 'no file under a test root'],
    what: 'an upstream citation from a test covers nothing: the local id of the same number is still uncovered',
  },
  {
    dir: 'upstream-undeclared',
    pass: false,
    contains: [`${CAP}/${fr('034a')} names the qualifier ${CAP}`, 'plan.md'],
    what: 'an upstream citation whose qualifier no row declares',
  },
  {
    dir: 'upstream-bad-qualifier',
    pass: false,
    contains: ['4CAP is not a qualifier'],
    what: 'a declared qualifier that breaks the grammar, so it could be read as a feature prefix',
  },
  { dir: 'upstream-stale', pass: false, contains: [`${CAP} is cited nowhere`], what: 'a declared qualifier nothing cites' },
  { dir: 'upstream-unsorted', pass: false, contains: [`${CAP} sorts before ${DOC}`], what: 'upstream rows out of order' },
  { dir: 'upstream-duplicate', pass: false, contains: [`${CAP} is listed twice`], what: 'a duplicated upstream row' },
  { dir: 'upstream-no-location', pass: false, contains: [`${CAP} carries no location`], what: 'an upstream row naming no document' },
  {
    dir: 'upstream-malformed',
    pass: false,
    contains: ['expected exactly 5 tab-separated column(s), found 2'],
    what: 'an upstream row in the retired two-column spelling, which fails on its width rather than being read as a row with no feature and no pin',
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
  // A fixture supplies a waiver, legacy, upstream or dropped list only when its case is about one; the gate
  // treats all four as optional. The two upstream lists live *inside* the fixture's specs tree, as they do
  // in a real repo, so the snapshots the upstream list pins sit at `specs/upstream/` -- inside the tree the
  // gate walks for citations. Every passing upstream fixture therefore also proves that the snapshots are
  // skipped there: their requirement ids are written bare, as the other repository writes them, and a gate
  // that read them would report every one as a bare citation.
  const waivers = path.join(root, 'trace-waivers.tsv');
  const legacy = path.join(root, 'trace-legacy-files.tsv');
  const upstreams = path.join(root, 'specs', 'trace-upstreams.tsv');
  const dropped = path.join(root, 'specs', 'trace-upstream-dropped.tsv');
  if (fs.existsSync(waivers)) args.push('--waivers', waivers);
  if (fs.existsSync(legacy)) args.push('--legacy', legacy);
  if (fs.existsSync(upstreams)) args.push('--upstreams', upstreams);
  if (fs.existsSync(dropped)) args.push('--dropped', dropped);
  const r = spawnSync(process.execPath, [...args, ...extra], { encoding: 'utf8' });
  if (r.error) throw r.error;
  return { status: r.status ?? 1, output: `${r.stdout}${r.stderr}` };
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

  // The completeness half, as BanListNegativeControlTest does it for the ban rules: a fixture nobody asserts
  // is a defect class nobody checks, so it fails here rather than sitting in the tree looking like coverage.
  const onDisk = fs.readdirSync(FIXTURES, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => e.name).sort();
  const exercised = new Set(CASES.map((c) => c.dir));
  for (const dir of onDisk) if (!exercised.has(dir)) failures.push(`fixtures/traceability/${dir} is exercised by no case in this file`);
  for (const dir of exercised) if (!onDisk.includes(dir)) failures.push(`case ${dir} names no fixture directory`);

  if (failures.length > 0) throw new Fail(failures.map((f) => `  - ${f}`).join('\n\n'));
  console.log(`check-traceability.mjs: ${CASES.length} case(s) over ${onDisk.length} fixture(s), every failure class caught`);
});

