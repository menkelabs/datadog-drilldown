import { defineConfig, devices } from '@playwright/test';

const PORT = process.env.E2E_PORT ?? '18081';
const baseURL = `http://127.0.0.1:${PORT}`;

/**
 * UI smoke tests for test-report-server.
 * Starts Spring Boot via Maven from parent module (../) so H2 paths in application.yml resolve.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `mvn -q spring-boot:run`,
    cwd: '..',
    url: `${baseURL}/`,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    env: {
      ...process.env,
      SERVER_PORT: PORT,
    },
  },
});
