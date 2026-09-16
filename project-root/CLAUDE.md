# CLAUDE.md

One repo, one service, one micro-frontend.

- `backend/` is the Java service, API-only, created from `dulguun0225/java-backend-template`. Its own
  `CLAUDE.md` and `docs/GATES.md` say what is decided there; `node backend/scripts/wall.mjs` is its definition of done.
- `frontend/` is the micro-frontend, a separate static deploy. Not started; `frontend/README.md` records the
  decisions taken and the gates to wire, and the `frontend` CI job refuses frontend code that has no `check` script.
- The contract between them is `backend/openapi/v1.json`.
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

Install the skills once per machine: `npx skills add dulguun0225/skills -a claude-code -y`.
