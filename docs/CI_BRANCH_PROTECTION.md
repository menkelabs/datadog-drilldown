# GitHub branch protection — recommended checks (M2 follow-up)

This repo cannot set branch protection rules from code; **org/repo admins** configure them in GitHub **Settings → Branches → Branch protection rules**.

## Required status checks (recommended)

Use these workflows as **required** for `main` (or your default branch):

| Workflow | Purpose |
|----------|---------|
| [`dice-leap-poc.yml`](../.github/workflows/dice-leap-poc.yml) | Python PoC: `requirements.txt` + `pytest` (local classical only). |
| [`java-modules.yml`](../.github/workflows/java-modules.yml) | Java reactor: `mvn test` for `embabel-dice-rca`, `dice-server`, `test-report-server`. |
| [`test-report-server.yml`](../.github/workflows/test-report-server.yml) | `mvn test` in `test-report-server` plus **Playwright** UI/API smoke (`e2e-ui` job). |
| [`docs-site.yml`](../.github/workflows/docs-site.yml) | **MkDocs** `build --strict` for [documentation-generator](documentation-generator/) when docs / milestones / root README change. Optional as a required check ([Milestone 3](../milestones/milestone-3.md)). |

## Do **not** require by default

| Workflow | Why |
|----------|-----|
| [`dice-leap-poc-leap.yml`](../.github/workflows/dice-leap-poc-leap.yml) | Optional Leap smoke; needs `DWAVE_API_TOKEN` secret. Fails closed on forks / repos without token. **Do not** add to required checks unless every contributor has Leap configured. |

## Optional: Embabel / other modules

If you add workflows for `embabel-dice-rca` alone, align them with `java-modules.yml` to avoid duplicate required checks unless intentional.

## Secrets policy

- **`DWAVE_API_TOKEN`**: repository secret for optional Leap job only; never required for merge unless the team explicitly standardizes on Leap in CI.
