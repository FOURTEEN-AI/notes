import { expect, test, type Page } from '@playwright/test';

test.skip(
  !process.env.E2E_VISUAL_IMAGE,
  'snapshots are only valid in the pinned Playwright image (e2e/visual.sh)',
);

const notes = [
  { id: 2, text: 'Milch, Brot und Eier kaufen', createdAt: '2026-01-15T10:30:00Z' },
  { id: 1, text: 'Zahnarzt anrufen\nTermin im Februar', createdAt: '2026-01-14T08:00:00Z' },
];
const ideas = [
  {
    number: 11,
    title: 'Dunkles Design',
    description: 'Die App soll dunkel sein.',
    status: 'WAITING_FOR_ANSWER',
    previewUrl: null,
    previewReady: false,
  },
];

async function mockApi(page: Page) {
  await page.route('**/api/notes', (route) => route.fulfill({ json: notes }));
  await page.route('**/api/preview', (route) => route.fulfill({ status: 204 }));
  await page.route('**/api/ideas', (route) => route.fulfill({ json: ideas }));
}

test('notes view', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');
  await expect(page.getByText('Milch, Brot und Eier kaufen')).toBeVisible();
  await expect(page).toHaveScreenshot('notes.png', { mask: [page.locator('time')] });
});

test('ideas dialog', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');
  await page.getByRole('button', { name: /^Ideen/ }).click();
  await expect(page.getByRole('button', { name: /#11 Dunkles Design/ })).toBeVisible();
  await expect(page).toHaveScreenshot('ideas.png');
});
