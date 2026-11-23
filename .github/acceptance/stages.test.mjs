import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildQuestions, judge, parseMethods, unmatchedSteps } from './stages.mjs';

const service = `package x;
public class NoteService {
    public Note create(String text) {
        return notes.add(text);
    }
}`;
const stage = (body) => `package x;
public class NotizenStage {
    @ScenarioState private Note last;

    @As("ich speichere die Notiz $")
    public NotizenStage ich_speichere_die_Notiz_$(String text) {
        ${body}
        return self();
    }

    public NotizenStage die_Liste_ist_leer() {
        assertThat(notizen.list()).isEmpty();
        return self();
    }
}`;
const fake = (body) => `package x;
class InMemoryNoteRepository {
    @Override
    public Note add(String text) {
        ${body}
    }
}`;
const files = (stageBody, fakeBody) => ({
  'src/test/java/x/acceptance/NotizenStage.java': stage(stageBody),
  'src/test/java/x/acceptance/InMemoryNoteRepository.java': fake(fakeBody),
  'src/main/java/x/application/NoteService.java': service,
});
const step = (text) => ({ keyword: 'when', text, params: [] });
const scenarios = [
  { title: 'Speichern', steps: [step('ich speichere die Notiz $'), step('die Liste ist leer')] },
  { title: 'Leer', steps: [step('die Liste ist leer')] },
];
const base = files('last = notizen.create(text);', 'return null;');

test('parses methods with their annotations and ignores fields', () => {
  const methods = parseMethods('NotizenStage.java', stage('x();'));
  assert.deepEqual(methods.map((m) => m.key), ['NotizenStage.ich_speichere_die_Notiz_$', 'NotizenStage.die_Liste_ist_leer']);
  assert.equal(methods[0].text, 'ich speichere die Notiz $');
  assert.equal(methods[1].text, 'die Liste ist leer');
  assert.match(methods[0].source, /^ {4}@As/);
});

test('nothing changed, nothing to judge', () => {
  assert.deepEqual(buildQuestions({ scenarios, base, head: base }), []);
});

test('a changed stage method is judged in each scenario that uses it', () => {
  const head = files('last = null;', 'return null;');
  const questions = buildQuestions({ scenarios: [...scenarios, { title: 'Nochmal', steps: [step('die Liste ist leer')] }], base, head });
  assert.deepEqual(questions.map((q) => q.method), ['NotizenStage.ich_speichere_die_Notiz_$']);
  const head2 = { ...base, 'src/test/java/x/acceptance/NotizenStage.java': stage('last = notizen.create(text);').replace('isEmpty()', 'isNull()') };
  const twice = buildQuestions({ scenarios, base, head: head2 });
  assert.deepEqual(twice.map((q) => q.label), ['"when die Liste ist leer" in "Speichern"', '"when die Liste ist leer" in "Leer"']);
});

test('a changed fake method reaches the steps that call it, with the called sources', () => {
  const head = files('last = notizen.create(text);', 'return new Note();');
  const [question] = buildQuestions({ scenarios, base, head });
  assert.deepEqual(question.changed, ['InMemoryNoteRepository.add']);
  assert.deepEqual(Object.keys(question.state.called), ['NoteService.create', 'InMemoryNoteRepository.add']);
  assert.equal(question.state.scenario.title, 'Speichern');
});

test('the methods of the other steps of the scenario are part of the question', () => {
  const head = { ...base, 'src/test/java/x/acceptance/NotizenStage.java': stage('last = notizen.create(text);').replace('isEmpty()', 'isNull()') };
  const [question] = buildQuestions({ scenarios, base, head });
  assert.equal(question.method, 'NotizenStage.die_Liste_ist_leer');
  assert.deepEqual(Object.keys(question.state.others), ['NotizenStage.ich_speichere_die_Notiz_$', 'NoteService.create', 'InMemoryNoteRepository.add']);
  assert.deepEqual(question.state.called, {});
});

test('a step that no stage method stands for is a finding, not skipped', () => {
  assert.deepEqual(unmatchedSteps({ scenarios, head: base }), []);
  // A parameter without `$` in the name: JGiven appends its value, the texts differ.
  const appended = [{ title: 'Anhängen', steps: [step('eine Notiz existiert $')] }];
  const head = { ...base, 'src/test/java/x/acceptance/MehrStage.java': 'class MehrStage {\n    public MehrStage eine_Notiz_existiert(String text) {\n        return self();\n    }\n}' };
  const [finding] = unmatchedSteps({ scenarios: appended, head });
  assert.match(finding, /No stage method stands for "when eine Notiz existiert \$" in "Anhängen"/);
});

test('Haiku decides and its reason goes into the finding', async () => {
  const q = (label) => ({ label, method: 'S.m', changed: ['S.m'], state: {} });
  const run = (pass) => judge([q('a')], { haiku: async () => ({ pass, reason: 'because' }) });
  assert.deepEqual(await run(true), []);
  const [finding] = await run(false);
  assert.match(finding, /does not implement its step\. a: S\.m \(changed: S\.m\)\. Reason: because/);
});

test('a pending scenario is skipped, its steps do not run', () => {
  const head = files('last = null;', 'return null;');
  const pending = scenarios.map((scenario) => ({ ...scenario, pending: true }));
  assert.deepEqual(buildQuestions({ scenarios: pending, base, head }), []);
});
