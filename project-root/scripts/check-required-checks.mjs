// The forge's required-status-checks list is not a committed file, so assert it against the committed job names
// from the API. A gate that exits non-zero but is not required does not block a merge. Needs GH_TOKEN with read
// access to the repository (the default Actions token suffices on a public repo).
import fs from 'node:fs';
import path from 'node:path';
// The shared helpers live beside the backend's scripts: at the template this file sits under project-root/ and the
// backend is the repository root; lifted into a project, the backend is backend/.
const lib = await import(fs.existsSync(new URL('../backend/scripts/_lib.mjs', import.meta.url)) ? '../backend/scripts/_lib.mjs' : '../../scripts/_lib.mjs');
const { capture, lines, main, Fail } = lib;

main(() => {
  process.chdir(path.resolve(import.meta.dirname, '..'));
  const repo = process.env.GITHUB_REPOSITORY || capture('gh', ['repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner']);
  const branch = process.env.DEFAULT_BRANCH || 'main';
  // Job names: the two-space-indented keys under `jobs:` in the committed workflow.
  const expected = [];
  let inJobs = false;
  for (const line of lines(fs.readFileSync('.github/workflows/ci.yml', 'utf8'))) {
    if (/^jobs:/.test(line)) { inJobs = true; continue; }
    const m = inJobs && /^  ([a-zA-Z0-9_-]+):$/.exec(line);
    if (m) expected.push(m[1]);
  }
  expected.sort();
  const actual = lines(capture('gh', ['api', `repos/${repo}/rules/branches/${branch}`, '--jq',
    '.[] | select(.type=="required_status_checks") | .parameters.required_status_checks[].context'])).sort();
  if (actual.length === 0) {
    throw new Fail(`no required status checks on ${repo}@${branch}; apply .github/rulesets/main.json (node scripts/apply-ruleset.mjs)`);
  }
  if (expected.join('\n') !== actual.join('\n')) {
    console.error('required checks differ from committed job names');
    console.error('expected (ci.yml jobs):'); console.error(expected.join('\n'));
    console.error('required (forge):'); console.error(actual.join('\n'));
    throw new Fail('required checks differ from committed job names');
  }
  console.log(`required status checks match ci.yml job names: ${expected.join(' ')}`);
});
