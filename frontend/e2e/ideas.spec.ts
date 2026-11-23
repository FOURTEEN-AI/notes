import { expect, test } from '@playwright/test';

test.beforeEach(async ({ request }) => {
  await request.post('http://127.0.0.1:18090/__reset');
});

test('the ideas open in a dialog, list the ideas of GitHub and take a new one', async ({
  page,
}) => {
  await page.goto('/');
  const button = page.getByRole('button', { name: /^Ideen \(\d+\)$/ });
  await expect(button).toBeVisible();
  await button.click();

  const dialog = page.getByRole('dialog', { name: 'Ideen' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: /#11 Dunkles Design/ })).toContainText(
    'Rückfrage an dich',
  );
  await expect(dialog.getByRole('button', { name: /#12 Notizen sortieren/ })).toBeVisible();

  await dialog.getByPlaceholder('Neue Idee …').fill('Tags für Notizen');
  await dialog.getByRole('button', { name: 'Senden' }).click();
  await expect(dialog.getByRole('heading', { name: /Tags für Notizen/ })).toBeVisible();

  await dialog.getByRole('button', { name: '← Ideen' }).click();
  await expect(dialog.getByRole('button', { name: /Tags für Notizen/ })).toBeVisible();

  await dialog.getByRole('button', { name: 'Schließen' }).click();
  await expect(dialog).toBeHidden();
});

test('the chat of an idea shows its messages and takes an answer', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: /^Ideen/ }).click();
  const dialog = page.getByRole('dialog', { name: /Ideen|#11/ });
  await dialog.getByRole('button', { name: /#11 Dunkles Design/ }).click();

  await expect(dialog.getByText('Wie dunkel soll es sein?')).toBeVisible();
  await dialog.getByPlaceholder('Antworte dem Refine-Agent …').fill('Ganz dunkel');
  await dialog.getByRole('button', { name: 'Senden' }).click();
  await expect(dialog.locator('p', { hasText: 'Ganz dunkel' })).toBeVisible();
});
