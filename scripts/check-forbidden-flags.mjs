// No preview-feature compiler or launcher flag, and no Java agent, in any build, container, compose, CI or
// script file. ArchUnit reads bytecode and cannot see either; this scan is the gate. ForbiddenFlagsTest runs the
// same check over the build files from inside the build.
import fs from 'node:fs';
import path from 'node:path';
import { capture, lines, main, Fail } from './_lib.mjs';

main(() => {
  const root = path.resolve(import.meta.dirname, '..');
  process.chdir(root);
  const tokens = /(--enable-preview|-javaagent|opentelemetry-javaagent|otel-javaagent|aws-opentelemetry-agent)/;
  // Relative to this directory, whether it is the template root or a project's backend/ (git ls-files is cwd-relative).
  let files = lines(capture('git', ['ls-files', '--', 'pom.xml', '.mvn/*', 'Dockerfile', 'scripts/*', '.github/workflows/*', 'project-root/*']))
    .filter((f) => !f.endsWith('check-forbidden-flags.mjs'));
  // Vendored into a project: the project's compose and workflows one level up are deploy files too.
  const top = capture('git', ['rev-parse', '--show-toplevel']);
  if (path.relative(top, fs.realpathSync(root)) !== '') {
    files = files.concat(lines(capture('git', ['ls-files', '--', '../compose*.yaml', '../.github/workflows/*'], { check: false })));
  }
  if (files.length === 0) throw new Fail('no build or deploy files found to scan');
  let found = false;
  for (const file of files) {
    lines(fs.readFileSync(file, 'utf8')).forEach((line, i) => {
      if (tokens.test(line)) {
        console.log(`${file}:${i + 1}:${line}`);
        found = true;
      }
    });
  }
  if (found) throw new Fail('forbidden flag found (see lines above)');
  console.log('no preview or agent flags in build and deploy files');
});
