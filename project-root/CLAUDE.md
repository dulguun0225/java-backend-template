# CLAUDE.md

One repo, one service, one micro-frontend.

- `backend/` is the Java service, API-only, created from `dulguun0225/java-backend-template`. Its own
  `CLAUDE.md` and `docs/GATES.md` say what is decided there; `node backend/scripts/wall.mjs` is its definition of done.
- `frontend/` is the micro-frontend, a separate static deploy. Not started; `frontend/README.md` records the
  decisions taken and the gates to wire, and the `frontend` CI job refuses frontend code that has no `check` script.
- The contract between them is `backend/openapi/v1.json`.
- `node backend/scripts/check-traceability.mjs` is the spec↔code gate and a step in the wall: it refuses a bare
  requirement id, resolves every `NNN/FR-nnn` against `specs/<NNN>-<name>/spec.md`, and holds every id of a
  feature whose `tasks.md` is closed to a test citation and every id of a feature that has a `tasks.md` to a
  task naming it. Four lists under `specs/` feed it — `trace-waivers.tsv` (an id no test claims, `external` or
  `deferred`, with a reason), `trace-legacy-files.tsv` (files whose bare ids can never move, a shipped
  migration above all), `trace-upstreams.tsv` (the source documents each feature was specified from, whose own
  ids are cited as `<QUALIFIER>/FR-nnn`; each row pins a committed copy of its document at
  `specs/upstream/<QUALIFIER>.md` by the source commit and the snapshot's blob sha, re-taken only by
  `node backend/scripts/refresh-upstream-snapshot.mjs`) and `trace-upstream-dropped.tsv` (an upstream
  requirement this service does not take, `dropped` or `deferred`, with the committed decision named). None of
  the four is shipped and none is required: the first feature that needs a row is what creates the file.
  `--report` prints the coverage matrix, the deferred ids and the upstream documents with their pins.
- `.github/workflows/ci.yml` has two jobs, `backend` and `frontend`, both required on `main` by
  `.github/rulesets/main.json` (`node scripts/apply-ruleset.mjs` applies it). Nothing is advisory.
- `.gitlab-ci.yml` mirrors those two jobs for a GitLab remote (a docker-executor runner with `privileged = true`
  for docker:dind). Whichever forge this repo is not on, its file stays: both are deploy files
  `check-forbidden-flags.mjs` scans, and the ruleset script only means something on GitHub.
- `.specify/memory/constitution.md` is pre-filled for spec-kit. `/speckit.constitution` amends Article VII
  only, the project's own decisions; Articles I–VI restate what `backend/` already enforces and are not
  re-planned. `/speckit.plan` reads it and must not re-plan the stack or the gates; a plan's Technical
  Context inherits them.
- `compose.yaml` runs PostgreSQL and the service locally: `docker compose up --build`.
- `.claude/settings.json` pins `worktree.baseRef: head`: an agent run in an isolated worktree starts from the
  branch you are on, not from `main`. Feature work lives on `feature/<NNN>-<name>` ahead of `main`, so a
  worktree cut from `main` lacks the files earlier tasks created and the agent silently works on the wrong tree.
  `.claude/worktrees/` is ignored; those worktrees are merged and removed, never committed.

Install the skills once per machine: `npx skills add dulguun0225/skills -a claude-code -y`.
