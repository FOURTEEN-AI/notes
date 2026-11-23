import assert from 'node:assert/strict';
import { test } from 'node:test';
import { redOldTests } from './conflict.mjs';

const scenario = (title, failed = false) => ({ title, failed, steps: [] });
const base = [scenario('Eine Notiz speichern'), scenario('Eine Notiz löschen')];
const criteria = (body) => `# language: de\nFunktionalität: Notizen\n\n${body}`;

test('green old tests are no conflict', () => {
  assert.deepEqual(redOldTests({ criteria: criteria(''), base, head: base }), []);
});

test('a red old test is a conflict', () => {
  const head = [scenario('Eine Notiz speichern'), scenario('Eine Notiz löschen', true)];
  assert.deepEqual(redOldTests({ criteria: criteria(''), base, head }), ['Eine Notiz löschen']);
});

test('red new or replacing tests are not an old conflict', () => {
  const head = [scenario('Eine Notiz speichern'), scenario('Eine Notiz entfernen', true), scenario('Neu', true)];
  const delta = criteria('@ersetzt("Eine Notiz löschen")\nSzenario: Eine Notiz entfernen\n  Dann ok\n\nSzenario: Neu\n  Dann ok');
  assert.deepEqual(redOldTests({ criteria: delta, base, head }), []);
});
