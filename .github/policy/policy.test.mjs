import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { check, loadWorkflows, parse } from './policy.mjs';

const policy = JSON.parse(readFileSync(new URL('policy.json', import.meta.url), 'utf8'));

test('the workflows keep to the policy (secrets, token permissions, code of the pull request)', () => {
  const findings = check(policy, loadWorkflows(new URL('../workflows/', import.meta.url)));
  assert.deepEqual(findings, [], `\n${findings.join('\n')}\n`);
});

// One job `j` in `w.yml`, checked against `rule`; `steps` is the YAML of its steps.
const run = (steps, rule = {}, { permissions = 'contents: read', on = 'pull_request' } = {}) =>
  check(
    { 'w.yml#j': { secrets: [], permissions: { contents: 'read' }, pullRequestCode: 'no', ...rule } },
    {
      'w.yml': parse(
        `on: ${on}\njobs:\n  j:\n    runs-on: ubuntu-latest\n${permissions ? `    permissions: { ${permissions} }\n` : ''}    steps:\n${steps}`,
      ),
    },
  );
const checkout = '      - uses: actions/checkout@v5\n';
const headCheckout = (path = '') =>
  `${checkout}        with:\n          ref: \${{ github.event.pull_request.head.sha }}\n${path}`;
const secretEnv = (name, step = '') => `      - ${step}run: echo\n        env:\n          T: \${{ secrets.${name} }}\n`;

test('a job without an entry, and an entry without a job', () => {
  assert.match(check({}, { 'w.yml': parse('on: push\njobs:\n  j: { steps: [] }') }).join(), /w\.yml#j: no entry/);
  assert.match(check({ 'x.yml#k': {} }, {}).join(), /x\.yml#k: .*does not exist/);
});

test('secrets: not listed, dynamic access, only in the named step', () => {
  assert.deepEqual(run(secretEnv('A'), { secrets: ['A'] }), []);
  assert.match(run(secretEnv('B'), { secrets: ['A'] }).join(), /uses the secret B/);
  assert.match(run('      - run: echo "${{ toJSON(secrets) }}"\n').join(), /dynamic access/);
  const named = secretEnv('A', 'name: Judge\n        ');
  assert.deepEqual(run(named, { secrets: ['A'], secretSteps: { A: 'Judge' } }), []);
  assert.match(run(secretEnv('A'), { secrets: ['A'], secretSteps: { A: 'Judge' } }).join(), /only in "Judge"/);
});

test('token permissions: wider than listed, none declared', () => {
  const step = '      - run: echo\n';
  assert.deepEqual(run(step, {}, { permissions: 'contents: read' }), []);
  assert.match(run(step, {}, { permissions: 'contents: write' }).join(), /contents: write is wider/);
  assert.match(run(step, {}, { permissions: 'issues: read' }).join(), /issues: read is wider .*none/);
  assert.match(run(step, {}, { permissions: '' }).join(), /declares no permissions/);
});

test('code of the pull request: the merge commit and the head count, the base does not', () => {
  const build = '      - run: ./gradlew build\n';
  assert.match(run(checkout + build).join(), /runs code of the pull request/);
  assert.match(run(headCheckout() + build, {}, { on: 'pull_request_target' }).join(), /runs code of the pull request/);
  assert.deepEqual(run(checkout + build, {}, { on: 'push' }), []);
  assert.deepEqual(run(`${checkout}        with:\n          ref: \${{ github.base_ref }}\n${build}`), []);
  assert.match(run('      - run: gh pr checkout 1\n' + build, {}, { on: 'push' }).join(), /runs code/);
});

test('code of the pull request: what is a build or run step', () => {
  const runs = (script) => run(`${checkout}      - run: |\n          ${script}\n`).length > 0;
  for (const script of ['pnpm install', 'npm ci', 'node --test x.mjs', 'cd a && ./gradlew test', 'x=$(.github/smoke.sh a)', 'if true; then bash x; fi']) {
    assert.ok(runs(script), script);
  }
  const text = ["gh pr view 1 --jq '.[] | select(.a == \"node\")'", 'git show origin/main:x > "$RUNNER_TEMP/p.mjs"', 'out=$(node "$RUNNER_TEMP/p.mjs" "$A")', '# ./gradlew', 'echo ./gradlew'];
  for (const script of text) assert.ok(!runs(script), script);
  assert.match(run(`${checkout}      - uses: ./.github/actions/x\n`).join(), /runs code/);
});

test('code of the pull request: a checkout in a subdirectory is code only where the step goes', () => {
  const head = headCheckout('          path: head\n');
  assert.deepEqual(run(`${head}      - run: ./gradlew test\n`), []);
  assert.deepEqual(run(`${head}      - run: cd head && node ../base/x.mjs\n`), []);
  assert.match(run(`${head}      - run: cd head && ./gradlew test\n`).join(), /runs code/);
  assert.match(run(`${head}      - run: pnpm install\n        working-directory: head\n`).join(), /runs code/);
});

test('code of the pull request: "jar" allows scripts, but no secret in their scope', () => {
  const script = `${checkout}      - run: .github/smoke.sh a\n`;
  const rule = { secrets: ['A'], pullRequestCode: 'jar' };
  assert.deepEqual(run(script, rule), []);
  assert.deepEqual(run(secretEnv('A') + script, rule), []);
  assert.match(run(script.replace('run: .github/smoke.sh a\n', `run: .github/smoke.sh a\n        env:\n          T: \${{ secrets.A }}\n`), rule).join(), /with a secret in scope/);
  const jobEnv = parse(`on: pull_request\njobs:\n  j:\n    permissions: { contents: read }\n    env:\n      T: \${{ secrets.A }}\n    steps:\n${script}`);
  assert.match(check({ 'w.yml#j': { permissions: { contents: 'read' }, ...rule } }, { 'w.yml': jobEnv }).join(), /with a secret in scope/);
  assert.deepEqual(run(secretEnv('A') + script, { ...rule, pullRequestCode: 'yes' }), []);
});

test('code of the pull request: an agent that may run a build tool', () => {
  const agent = (tools) =>
    `${checkout}      - uses: anthropics/claude-code-action@v1\n        with:\n          claude_args: >-\n            --max-turns 3\n            --allowedTools "${tools}"\n`;
  assert.match(run(agent('Read,Bash(./gradlew:*),Bash(git:*)')).join(), /runs code/);
  assert.match(run(agent('Read,Bash')).join(), /runs code/);
  assert.deepEqual(run(agent('Read,Write,Bash(git diff:*),Bash(gh pr view:*)')), []);
});

test('no cache where the policy says so', () => {
  const cached = `${checkout}      - uses: actions/setup-java@v5\n        with:\n          cache: gradle\n`;
  assert.match(run(cached, { noCache: true }).join(), /uses a cache/);
  assert.deepEqual(run(cached), []);
});
