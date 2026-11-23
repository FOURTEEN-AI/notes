import assert from 'node:assert/strict';
import { test } from 'node:test';
import { check } from './check.mjs';
import { parseGherkin } from './gherkin.mjs';

const step = (keyword, text, params = []) => ({ keyword, text, params });
const save = [step('when', 'ich speichere die Notiz $', ['Milch']), step('then', 'die Liste ist leer')];
const base = [{ title: 'Eine Notiz speichern', steps: save }, { title: 'Eine Notiz löschen', steps: save }];

const gherkin = (body) => `# language: de\nFunktionalität: Notizen\n\n${body}`;

test('parses tags, titles and steps with placeholders', () => {
  const [scenario] = parseGherkin(
    gherkin('@ersetzt("Alt")\nSzenario: Neu\n  Wenn ich speichere "Milch"\n  Dann ist alles gut'),
  );
  assert.equal(scenario.title, 'Neu');
  assert.equal(scenario.tags.replaces, 'Alt');
  assert.deepEqual(scenario.steps[0], step('when', 'ich speichere $', ['Milch']));
  assert.equal(scenario.steps[1].keyword, 'then');
});

test('an unchanged state without a delta passes', () => {
  assert.deepEqual(check({ criteria: gherkin(''), base, head: base }), []);
});

test('a new scenario needs a test with the same steps', () => {
  const criteria = gherkin('Szenario: Eine Notiz kürzen\n  Wenn ich speichere "x"\n  Dann ist alles gut');
  const test = { title: 'Eine Notiz kürzen', steps: [step('when', 'ich speichere $', ['x']), step('then', 'ist alles gut')] };
  assert.deepEqual(check({ criteria, base, head: [...base, test] }), []);
  assert.match(check({ criteria, base, head: base })[0], /has no test/);
  const weak = { ...test, steps: [step('when', 'ich speichere $', ['x'])] };
  assert.match(check({ criteria, base, head: [...base, weak] })[0], /differ from the approved/);
});

test('a changed scenario replaces the old one, a removed one is gone', () => {
  const criteria = gherkin(
    '@ersetzt("Eine Notiz löschen")\nSzenario: Eine Notiz entfernen\n  Wenn ich speichere "Milch"\n  Dann ist die Liste leer\n\n@entfällt\nSzenario: Eine Notiz speichern',
  );
  const head = [{ title: 'Eine Notiz entfernen', steps: [step('when', 'ich speichere $', ['Milch']), step('then', 'ist die Liste leer')] }];
  assert.deepEqual(check({ criteria, base, head }), []);
});

test('a changed scenario may keep its title', () => {
  const criteria = gherkin('@ersetzt("Eine Notiz löschen")\nSzenario: Eine Notiz löschen\n  Wenn ich speichere "Brot"');
  const head = [base[0], { title: 'Eine Notiz löschen', steps: [step('when', 'ich speichere $', ['Brot'])] }];
  assert.deepEqual(check({ criteria, base, head }), []);
  assert.match(check({ criteria, base, head: base })[0], /differ from the approved/);
});

test('a test removed or changed outside the delta fails', () => {
  assert.match(check({ criteria: gherkin(''), base, head: [base[0]] })[0], /removed outside/);
  const changed = [{ ...base[0], steps: [save[0]] }, base[1]];
  assert.match(check({ criteria: gherkin(''), base, head: changed })[0], /changed outside/);
});

test('a test that is not approved fails', () => {
  const extra = { title: 'Heimlich', steps: save };
  assert.match(check({ criteria: gherkin(''), base, head: [...base, extra] })[0], /not an approved/);
});

test('a @manuell scenario needs a pending test with the approved reason', () => {
  const criteria = gherkin('@manuell("Nur das Frontend")\nSzenario: Der Knopf ist blau\n  Dann ist er blau');
  assert.equal(parseGherkin(criteria)[0].tags.manual, 'Nur das Frontend');
  const test = { title: 'Der Knopf ist blau', steps: [step('then', 'ist er blau')], pending: true, reason: 'Nur das Frontend' };
  assert.deepEqual(check({ criteria, base, head: [...base, test] }), []);
  assert.match(check({ criteria, base, head: base })[0], /has no test/);
  assert.match(check({ criteria, base, head: [...base, { ...test, pending: false }] })[0], /must be @Pending/);
  assert.match(check({ criteria, base, head: [...base, { ...test, reason: 'Anders' }] })[0], /reason/);
});

test('a pending test without @manuell fails, and so does an old test that becomes pending', () => {
  const criteria = gherkin('Szenario: Der Knopf ist blau\n  Dann ist er blau');
  const test = { title: 'Der Knopf ist blau', steps: [step('then', 'ist er blau')], pending: true, reason: null };
  assert.match(check({ criteria, base, head: [...base, test] })[0], /not @manuell/);
  const pendingOld = [{ ...base[0], pending: true, reason: null }, base[1]];
  assert.match(check({ criteria: gherkin(''), base, head: pendingOld })[0], /became pending outside/);
});

test('the report reader finds the pending status and the reason of the @Manuell tag', async () => {
  const { mkdtempSync, writeFileSync } = await import('node:fs');
  const { tmpdir } = await import('node:os');
  const { join } = await import('node:path');
  const { readReport } = await import('./report.mjs');
  const dir = mkdtempSync(join(tmpdir(), 'report-'));
  const scenario = (description, status, tagIds) => ({
    description,
    tagIds,
    scenarioCases: [{ status, steps: [] }],
  });
  const tagMap = { t: { name: 'manuell', value: 'Nur das Frontend' } };
  writeFileSync(
    join(dir, 'a.json'),
    JSON.stringify({ tagMap, scenarios: [scenario('A', 'SCENARIO_PENDING', ['t']), scenario('B', 'SUCCESS')] }),
  );
  const [a, b] = readReport(dir);
  assert.deepEqual([a.pending, a.reason, b.pending, b.reason], [true, 'Nur das Frontend', false, null]);
});
