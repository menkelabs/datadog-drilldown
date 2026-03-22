# Playwright E2E — test-report-server

Lockfile is **here** (`e2e/package-lock.json`). From this directory:

- **`npm ci`** then **`npm run install:browsers`** (or `npx playwright install chromium`) on first setup.
- **`npm test`** — starts the app via `mvn spring-boot:run` from `../` on port **18081** (`SERVER_PORT`), then runs Chromium tests.

From **`../`** (parent `test-report-server/`): **`npm run e2e:ci`**, **`npm run e2e:browsers`**, **`npm run e2e:test`** (see parent `package.json`).

Config: [`playwright.config.ts`](playwright.config.ts) · specs: [`tests/ui.spec.ts`](tests/ui.spec.ts).
