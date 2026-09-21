// Turn the template into a service: base package, Maven groupId and artifact name. Run once, then commit.
//
//   node scripts/init.mjs --package com.acme.someservice1 --name some_service_1 [--group com.acme]
//
// Two modes, detected from git:
//   standalone  this directory is the repository root: rename only.
//   vendored    this directory is <project>/backend (added with `git subtree add --prefix backend ...`): rename,
//               then lift project-root/ one level up — root CI with backend+frontend jobs, ruleset, compose,
//               frontend/ stub, spec-kit constitution, project CLAUDE.md — never overwriting a file that exists,
//               and remove the template's own .github/ and renovate.json, which only mean something at a root.
// Everything else (the gates, the scripts) is deliberately identical across services.
import fs from 'node:fs';
import path from 'node:path';
import { parseArgs } from 'node:util';
import { capture, lines, main, Fail } from './_lib.mjs';

const escape = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

main(() => {
  const root = path.resolve(import.meta.dirname, '..');
  process.chdir(root);
  let opts;
  try {
    ({ values: opts } = parseArgs({ options: { package: { type: 'string' }, name: { type: 'string' }, group: { type: 'string' } } }));
  } catch (e) {
    throw new Fail(e.message, 2);
  }
  const pkg = opts.package ?? '';
  const name = opts.name ?? '';
  if (!/^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$/.test(pkg)) throw new Fail('--package must be a lowercase dotted java package', 2);
  if (!/^[a-z][a-z0-9_-]*$/.test(name)) throw new Fail('--name must be lowercase letters, digits, hyphens or underscores', 2);
  const group = opts.group || pkg.slice(0, pkg.lastIndexOf('.'));

  const top = capture('git', ['rev-parse', '--show-toplevel']);
  const here = fs.realpathSync(root);
  let mode = 'standalone';
  if (path.relative(top, here) !== '') {
    if (path.relative(top, path.dirname(here)) !== '') throw new Fail(`vendored mode expects this directory to sit directly under the project root (${top})`, 2);
    mode = 'vendored';
  }

  const oldPkg = 'com.example.starter';
  const oldPath = 'com/example/starter';
  const oldGroup = 'com.example';
  const oldName = 'starter';
  const newPath = pkg.replaceAll('.', '/');
  const oldLeaf = path.posix.basename(oldPath);
  const newLeaf = path.posix.basename(newPath);

  // every file here, tracked or not, except this script
  const files = lines(capture('git', ['ls-files', '-co', '--exclude-standard', '.'])).filter((f) => f !== 'scripts/init.mjs');

  // Whole-file substitutions, every occurrence.
  const global = [
    // 1. package and group strings (generated jOOQ included: it carries the package in every file)
    [new RegExp(escape(oldPkg), 'g'), pkg],
    [new RegExp(escape(oldPath), 'g'), newPath],
    [new RegExp(`<groupId>${escape(oldGroup)}</groupId>`, 'g'), `<groupId>${group}</groupId>`],
    [new RegExp(`<exclude>${escape(oldGroup)}:\\*</exclude>`, 'g'), `<exclude>${group}:*</exclude>`],
    [new RegExp(`<ignore>${escape(oldGroup)}:\\*</ignore>`, 'g'), `<ignore>${group}:*</ignore>`],
    // 3. prose that names the test-only sibling packages by their leaf
    [new RegExp(`\\b${escape(oldLeaf)}(fixtures|test)\\b`, 'g'), `${newLeaf}$1`],
  ];
  // 2. the service's name wherever it is a name (artifact, jar, image, compose, application, OpenAPI title); the
  //    spring-boot-starter-* artifacts share the word and are excluded by anchoring. First match per line.
  const perLine = [
    [new RegExp(`<artifactId>${oldName}</artifactId>`), `<artifactId>${name}</artifactId>`],
    [new RegExp(`<name>${oldName}</name>`), `<name>${name}</name>`],
    [new RegExp(`^(\\s*name): ${oldName}$`), `$1: ${name}`],
    [new RegExp(`"title" : "${oldName}"`), `"title" : "${name}"`],
    [new RegExp(`POSTGRES_(DB|USER|PASSWORD): ${oldName}$`), `POSTGRES_$1: ${name}`],
    [new RegExp(`-U ${oldName} -d ${oldName}`), `-U ${name} -d ${name}`],
    [new RegExp(`postgresql://postgres:5432/${oldName}`), `postgresql://postgres:5432/${name}`],
    [new RegExp(`(USERNAME|PASSWORD): ${oldName}$`), `$1: ${name}`],
    [new RegExp(`image: ${oldName}:dev`), `image: ${name}:dev`],
    [new RegExp(`target/${oldName}-\\*\\.jar`), `target/${name}-*.jar`],
    [new RegExp(`title\\("${oldName}"\\)`), `title("${name}")`],
  ];
  for (const file of files) {
    const bytes = fs.readFileSync(file);
    if (bytes.includes(0)) continue; // binary
    const before = bytes.toString('utf8');
    let after = before;
    for (const [re, to] of global.slice(0, 5)) after = after.replace(re, to);
    // Line-wise, so `^` and `$` mean the line and only its first match moves, as sed without /g does.
    after = after
      .split('\n')
      .map((line) => {
        const eol = line.endsWith('\r') ? '\r' : '';
        let l = eol ? line.slice(0, -1) : line;
        for (const [re, to] of perLine) l = l.replace(re, to);
        return l + eol;
      })
      .join('\n');
    after = after.replace(global[5][0], global[5][1]);
    if (after !== before) fs.writeFileSync(file, after);
  }

  // 4. directories: the base package tree and its test-only siblings, which share the leaf name
  const oldParent = path.posix.dirname(oldPath);
  const newParent = path.posix.dirname(newPath);
  for (const src of ['main', 'test']) {
    const parent = path.join('src', src, 'java', oldParent);
    if (!fs.existsSync(parent)) continue;
    for (const entry of fs.readdirSync(parent, { withFileTypes: true })) {
      if (!entry.isDirectory() || !entry.name.startsWith(oldLeaf)) continue;
      const suffix = entry.name.slice(oldLeaf.length);
      const target = path.join('src', src, 'java', newParent, newLeaf + suffix);
      fs.mkdirSync(path.dirname(target), { recursive: true });
      fs.renameSync(path.join(parent, entry.name), target);
    }
  }
  pruneEmptyDirs('src');

  console.log(`renamed: package ${pkg}, group ${group}, artifact ${name} (${mode})`);

  if (mode === 'vendored') {
    // 5. lift the project-level files to the project root, never overwriting; report what was left alone
    for (const f of fs.readdirSync('project-root', { recursive: true, withFileTypes: true })) {
      if (!f.isFile()) continue;
      const rel = path.relative('project-root', path.join(f.parentPath, f.name));
      const dest = path.join('..', rel);
      if (fs.existsSync(dest)) {
        const shown = rel.split(path.sep).join('/');
        if (shown === '.specify/memory/constitution.md') {
          console.log(
            `WARNING: kept existing ../${shown}; the template's platform articles (I–VI) were NOT applied. ` +
              'spec-kit ran before the scaffold: merge them in by hand, or /speckit.plan will re-plan the stack.',
          );
        } else {
          console.log(`kept existing ../${shown} (template copy not applied)`);
        }
      } else {
        fs.mkdirSync(path.dirname(dest), { recursive: true });
        const source = path.join(f.parentPath, f.name);
        fs.copyFileSync(source, dest);
        const st = fs.statSync(source);
        fs.chmodSync(dest, st.mode);
        fs.utimesSync(dest, st.atime, st.mtime);
      }
    }
    for (const p of ['project-root', '.github', 'renovate.json']) fs.rmSync(p, { recursive: true, force: true });
    console.log(`lifted project-root/ to ${path.dirname(here)}; removed the template's own .github/ and renovate.json from ${path.basename(here)}/`);
    console.log('next: mvn -Pcodegen generate-sources && mvn spotless:apply && mvn verify here (the rename moves imports and re-wraps lines, so format before the wall); then at the project root: git add -A, commit, node scripts/apply-ruleset.mjs');
  } else {
    console.log('next: mvn -Pcodegen generate-sources && mvn spotless:apply && mvn verify, then commit (the rename moves imports and re-wraps lines, so format before the wall)');
  }
});

/** Remove every empty directory under dir, deepest first, dir itself included if it ends up empty. */
function pruneEmptyDirs(dir) {
  if (!fs.existsSync(dir)) return;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) pruneEmptyDirs(path.join(dir, entry.name));
  }
  if (fs.readdirSync(dir).length === 0) fs.rmdirSync(dir);
}
