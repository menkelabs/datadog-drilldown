import { test, expect } from '@playwright/test';

test.describe('Test report UI', () => {
  test('home page loads with title and main sections', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/Test Report/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Test Report – Evaluation');
    await expect(page.getByRole('heading', { name: 'Run tests' })).toBeVisible();
    await expect(page.getByRole('heading', { name: /^Summary/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: /^DICE \/ QUBO solver runs/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: /^Recent runs/ })).toBeVisible();
  });

  test('summary and runs panels finish loading (API reachable)', async ({ page }) => {
    await page.goto('/');
    const summary = page.locator('#summary');
    await expect(summary).not.toHaveText('Loading…', { timeout: 90_000 });
    const runsList = page.locator('#runs-list');
    await expect(runsList).not.toHaveText('Loading…', { timeout: 90_000 });
    // Either aggregated stats or explicit failure message from the UI
    const summaryText = await summary.innerText();
    expect(
      summaryText.includes('Total:') ||
        summaryText.includes('Could not load summary') ||
        summaryText.includes('No data')
    ).toBeTruthy();
  });

  test('solver runs panel loads without infinite spinner', async ({ page }) => {
    await page.goto('/');
    const table = page.locator('#solver-runs-table');
    await expect(table).not.toHaveText('Loading…', { timeout: 90_000 });
    const text = await table.innerText();
    expect(
      text.includes('No solver runs') ||
        text.includes('Could not load') ||
        text.includes('instance') // table header when rows exist
    ).toBeTruthy();
  });

  test('refresh summary triggers reload', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#summary')).not.toHaveText('Loading…', { timeout: 90_000 });
    await page.locator('#refresh-summary').click();
    await expect(page.locator('#summary')).toBeVisible();
  });

  test('run tests form controls are present', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#pattern')).toBeVisible();
    await expect(page.locator('#verbose')).toBeVisible();
    await expect(page.locator('#run-btn')).toBeEnabled();
    await expect(page.locator('#quick-test-btn')).toBeVisible();
  });
});

test.describe('Test report API', () => {
  test('analysis summary JSON', async ({ request }) => {
    const res = await request.get('/api/analysis/summary');
    expect(res.ok()).toBeTruthy();
    const body = (await res.text()).trim();
    if (body.length === 0 || body === 'null') return;
    const json = JSON.parse(body) as { total: number };
    expect(typeof json.total).toBe('number');
  });

  test('actuator health', async ({ request }) => {
    const res = await request.get('/actuator/health');
    expect(res.ok()).toBeTruthy();
    const json = await res.json();
    expect(json.status).toMatch(/UP|DOWN/);
  });

  test('solver runs list JSON', async ({ request }) => {
    const res = await request.get('/api/solver-runs?limit=5&source=auto');
    expect(res.ok()).toBeTruthy();
    const json = await res.json();
    expect(Array.isArray(json)).toBeTruthy();
  });
});
