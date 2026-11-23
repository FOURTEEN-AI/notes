import { expect, test } from '@playwright/test';

test('the JGiven report is served behind the login', async ({ page, browser }) => {
  const anonymous = await browser.newContext({ httpCredentials: undefined });
  expect((await anonymous.request.get('http://127.0.0.1:18080/jgiven/index.html')).status()).toBe(
    401,
  );
  await anonymous.close();

  const response = await page.goto('/jgiven/index.html');
  expect(response?.status()).toBe(200);
  await expect(page).toHaveTitle(/JGiven/i);
});
