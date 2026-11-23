import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { decide, PROTECTED } from './phase.mjs';

const file = `${PROTECTED}NotizenStage.java`;

test('without a checked commit it is phase 1', () => {
  assert.deepEqual(decide({ checked: null, changed: [file], message: 'x' }), { phase: 1, findings: [] });
});

test('unchanged tests after the checked commit are phase 2', () => {
  assert.deepEqual(decide({ checked: 'abc', changed: [], message: 'x' }), { phase: 2, findings: [] });
});

test('changed tests in phase 2 are a violation that names the files', () => {
  const { phase, findings } = decide({ checked: 'abc', changed: [file], message: 'feat: x' });
  assert.equal(phase, 2);
  assert.ok(findings.join('\n').includes(file));
});

test('the line "Phase: 1" goes back to phase 1', () => {
  const message = 'test: change the stage\n\nPhase: 1\n\nCo-Authored-By: x';
  assert.deepEqual(decide({ checked: 'abc', changed: [file], message }), { phase: 1, findings: [] });
});

test('the cli compares head with the checked commit in git', () => {
  const dir = mkdtempSync(join(tmpdir(), 'phase-'));
  const git = (...args) => execFileSync('git', ['-C', dir, '-c', 'user.name=t', '-c', 'user.email=t@t', ...args], { encoding: 'utf8' });
  const run = (head, checked) =>
    execFileSync('node', [new URL('./phase.mjs', import.meta.url).pathname, head, checked, join(dir, 'f.md')], {
      cwd: dir,
      encoding: 'utf8',
    });
  git('init', '-q');
  mkdirSync(join(dir, PROTECTED), { recursive: true });
  writeFileSync(join(dir, file), 'a');
  git('add', '.');
  git('commit', '-qm', 'phase 1');
  const checked = git('rev-parse', 'HEAD').trim();
  writeFileSync(join(dir, 'Prod.java'), 'p');
  git('add', '.');
  git('commit', '-qm', 'feat: production');
  assert.equal(run('HEAD', checked), 'phase=2\nviolation=false\n');
  assert.equal(run('HEAD', ''), 'phase=1\nviolation=false\n');
  writeFileSync(join(dir, file), 'b');
  git('commit', '-qam', 'feat: sneaky');
  assert.equal(run('HEAD', checked), 'phase=2\nviolation=true\n');
  assert.ok(readFileSync(join(dir, 'f.md'), 'utf8').includes(file));
});
