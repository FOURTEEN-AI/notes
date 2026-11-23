import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildQuestions, judge, parseMethods } from './stages.mjs';

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

test('Jev decides at 0.9 and 0.5, Haiku in between', async () => {
  const q = (label) => ({ label, method: 'S.m', changed: ['S.m'], state: {} });
  const run = (p, pass) =>
    judge([q('a')], { jev: async () => p, haiku: async () => ({ pass, reason: 'because' }) });
  assert.deepEqual(await run(0.9, false), []);
  assert.match((await run(0.49, true))[0], /Jev p=0.49/);
  assert.deepEqual(await run(0.5, true), []);
  assert.match((await run(0.7, false))[0], /Jev p=0.70, Haiku: because/);
});
