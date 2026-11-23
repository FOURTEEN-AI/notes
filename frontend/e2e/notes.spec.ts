import { expect, test, type APIRequestContext } from '@playwright/test';

// The tests share one database: every test starts without notes.
async function deleteAllNotes(request: APIRequestContext) {
  const notes = (await (await request.get('/api/notes')).json()) as { id: number }[];
  for (const note of notes) await request.delete(`/api/notes/${note.id}`);
}

test.beforeEach(async ({ request, page }) => {
  await deleteAllNotes(request);
  await page.goto('/');
});

test('a new note appears at the top, the input is empty, and it survives a reload', async ({
  page,
}) => {
  const input = page.getByPlaceholder('Neue Notiz');
  const save = page.getByRole('button', { name: 'Speichern' });
  await expect(save).toBeDisabled();

  await input.fill('Erste Notiz');
  await save.click();
  const items = page.getByRole('listitem').filter({ hasText: 'Notiz' });
  await expect(items).toHaveCount(1);
  await input.fill('Zweite Notiz');
  await save.click();

  await expect(items).toHaveCount(2);
  await expect(items.first()).toContainText('Zweite Notiz');
  await expect(input).toHaveValue('');

  await page.reload();
  await expect(items).toHaveCount(2);
  await expect(items.first()).toContainText('Zweite Notiz');
});

test('a deleted note is gone, also after a reload', async ({ page }) => {
  await page.getByPlaceholder('Neue Notiz').fill('Wegwerfnotiz');
  await page.getByRole('button', { name: 'Speichern' }).click();
  const note = page.getByRole('listitem').filter({ hasText: 'Wegwerfnotiz' });
  await expect(note).toBeVisible();

  await note.getByRole('button', { name: 'Löschen' }).click();
  await expect(note).toHaveCount(0);

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Notizen' })).toBeVisible();
  await expect(note).toHaveCount(0);
});

test('the app asks for the login', async ({ browser }) => {
  const context = await browser.newContext({ httpCredentials: undefined });
  const response = await context.request.get('http://127.0.0.1:18080/');
  expect(response.status()).toBe(401);
  await context.close();
});
