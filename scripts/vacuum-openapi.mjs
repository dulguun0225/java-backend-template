// The committed OpenAPI document lint: vacuum over rulesets/openapi.yaml, gated on exit code. The binary comes
// from mise.toml's exact pin (`mise install`), one entry for every platform, rather than a per-OS download here.
// `-n error` makes every rule's severity a failure; `-d` prints the offending path so the message is actionable.
// The document is generated (OpenApiSnapshotIT) and never hand-edited: a failure here is fixed in the code.
import path from 'node:path';
import { main, run, Fail } from './_lib.mjs';

main(() => {
  process.chdir(path.resolve(import.meta.dirname, '..'));
  try {
    run('vacuum', ['lint', '-r', 'rulesets/openapi.yaml', '-d', '-a', '-n', 'error', '--no-clip', '-b', '-q', 'openapi/v1.json']);
  } catch (e) {
    if (e instanceof Fail && e.message.endsWith('not on PATH')) throw new Fail('vacuum not on PATH; run `mise install` (mise.toml pins it)');
    throw e;
  }
});
