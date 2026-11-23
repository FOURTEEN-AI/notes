import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// WCAG 2.1 A and AA, including the color contrast.
async function expectNoViolations(page: Page) {
  const { violations } = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const summary = violations.flatMap((violation) =>
    violation.nodes.map(
      (node) => `${violation.id} ${node.target.join(' ')}: ${node.failureSummary}`,
    ),
  );
  expect(summary).toEqual([]);
}

test.beforeEach(async ({ request }) => {
  const notes = (await (await request.get('/api/notes')).json()) as { id: number }[];
  for (const note of notes) await request.delete(`/api/notes/${note.id}`);
  await request.post('http://127.0.0.1:18090/__reset');
  await request.post('/api/notes', { data: { text: 'Eine Notiz für den Test' } });
});

test('the notes view', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('Eine Notiz für den Test')).toBeVisible();
  await expectNoViolations(page);
});

test('the list of ideas', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: /^Ideen/ }).click();
  await expect(page.getByRole('button', { name: /#12 Notizen sortieren/ })).toBeVisible();
  await expectNoViolations(page);
});

test('the chat of an idea', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: /^Ideen/ }).click();
  await page.getByRole('button', { name: /#11 Dunkles Design/ }).click();
  await expect(page.getByText('Wie dunkel soll es sein?')).toBeVisible();
  await expectNoViolations(page);
});

test('the preview bar', async ({ page }) => {
  await page.goto('http://127.0.0.1:18081/');
  await expect(page.getByRole('complementary')).toContainText('Preview #7');
  await expect(page.getByText('Schreibe eine Notiz und speichere sie')).toBeVisible();
  await expectNoViolations(page);
});
