import { expect, test } from '@playwright/test';

// The preview app (PULL_REQUEST=7) on its own port: it shows the preview bar and no ideas.
test.use({ baseURL: 'http://127.0.0.1:18081' });

test('the preview bar shows the pull request, its checks and the tour', async ({ page }) => {
  await page.goto('/');
  const bar = page.getByRole('complementary');
  await expect(bar).toContainText('Preview #7: Notizen sortieren');
  await expect(bar).toContainText('build grün');
  await expect(bar).toContainText('review grün');
  await expect(bar).toContainText('acceptance grün');
  await expect(bar.getByRole('button', { name: 'Mergen' })).toBeEnabled();
  await expect(bar.getByText('Schreibe eine Notiz und speichere sie')).toBeVisible();
  await expect(page.getByRole('button', { name: /^Ideen/ })).toHaveCount(0);
});

test('the preview bar sends feedback', async ({ page }) => {
  await page.goto('/');
  const bar = page.getByRole('complementary');
  const send = bar.getByRole('button', { name: 'Feedback senden' });
  await expect(send).toBeDisabled();
  await bar.getByPlaceholder('Feedback an den Agenten').fill('Sieht gut aus');
  await send.click();
  await expect(bar.getByText('Feedback gesendet, der Agent arbeitet daran.')).toBeVisible();
});
