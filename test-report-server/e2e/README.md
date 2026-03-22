# Playwright E2E — test-report-server

- **`npm ci`** then **`npx playwright install chromium`** (first run).
- **`npm test`** — starts the app via `mvn spring-boot:run` from `../` on port **18081** (`SERVER_PORT`), then runs Chromium tests.

Config: [`playwright.config.ts`](playwright.config.ts) · specs: [`tests/ui.spec.ts`](tests/ui.spec.ts).
