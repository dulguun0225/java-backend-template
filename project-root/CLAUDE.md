# CLAUDE.md

One repo, one service, one micro-frontend.

Base branch: `main`

The line above names the trunk: feature branches are cut from it and merged back into it, and `build-feature`
reads it as written, in that one form, unindented and once. Rename the trunk and change the name between the
backticks in the same commit; the CI trigger and the branch ruleset name the trunk too.

- `backend/` is the Java service, API-only, created from `dulguun0225/java-backend-template`. Its own
  `CLAUDE.md` and `docs/GATES.md` say what is decided there; `node backend/scripts/wall.mjs` is its definition of done.
- `frontend/` is the micro-frontend, a separate static deploy. Not started; `frontend/README.md` records the
  decisions taken and the gates to wire, and the `frontend` CI job refuses frontend code that has no `check` script.
- The contract between them is `backend/openapi/v1.json`.
- A feature's `spec.md` is written here and owned here: a domain expert writes `specs/<NNN>-<name>/spec.md`
  with stock spec-kit (`/speckit-specify`, `/speckit-clarify`), build work picks up at `/speckit-plan`, and no
  later stage edits `spec.md`. Nothing is derived from a document in another repository.
- `node backend/scripts/check-traceability.mjs` is the spec↔code gate and a step in the wall: it refuses a bare
  requirement id, resolves every `NNN/FR-nnn` against `specs/<NNN>-<name>/spec.md`, and holds every id of a
  feature whose `tasks.md` is closed to a test citation and every id of a feature that has a `tasks.md` to a
  task naming it. Two lists under `specs/` feed it — `trace-waivers.tsv` (an id no test claims, `external` or
  `deferred`, with a reason) and `trace-legacy-files.tsv` (files whose bare ids can never move, a shipped
  migration above all). A token qualified by something that is not a feature prefix — `CAP-NC02-04/FR-034a` —
  names another document's requirement and is **prose**: it resolves nothing, covers nothing and is not a bare
  id. The gate recognises the shape only so that it can drop it, which is what keeps it from being read as the
  bare id it wraps and resolving against the local requirement of that number. Neither list is shipped and
  neither is required: the first feature that needs a row is what creates the file. `--report` prints the
  coverage matrix, the spec→tasks gap and the deferred ids.
- `.github/workflows/ci.yml` has two jobs, `backend` and `frontend`, both required on `main` by
  `.github/rulesets/main.json` (`node scripts/apply-ruleset.mjs` applies it). Nothing is advisory.
- `.gitlab-ci.yml` mirrors those two jobs for a GitLab remote (a docker-executor runner with `privileged = true`
  for docker:dind). Whichever forge this repo is not on, its file stays: both are deploy files
  `check-forbidden-flags.mjs` scans, and the ruleset script only means something on GitHub.
- `.specify/memory/constitution.md` is pre-filled for spec-kit. Articles I–VI restate what `backend/` already
  enforces and are not re-planned. Article VII is an optional slot that starts empty: nothing reads whether it
  is filled, nobody is owed a `/speckit.constitution` run, and it is amended by a commit with its reason when
  a feature's plan produces a rule that binds more than that feature. `/speckit.plan` reads the file and must not re-plan the stack or the gates; a plan's Technical
  Context inherits them.
- `compose.yaml` runs PostgreSQL and the service locally: `docker compose up --build`.
- `.claude/settings.json` pins `worktree.baseRef: head`: an agent run in an isolated worktree starts from the
  branch you are on, not from `main`. Feature work lives on `feature/<NNN>-<name>` ahead of `main`, so a
  worktree cut from `main` lacks the files earlier tasks created and the agent silently works on the wrong tree.
  `.claude/worktrees/` is ignored; those worktrees are merged and removed, never committed.

Install the skills once per machine: `npx skills add dulguun0225/skills -a claude-code -y`.
