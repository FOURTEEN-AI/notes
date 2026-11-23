// The model call of the per-method check. Thin on purpose: `stages.mjs` takes it as a function,
// so everything else is tested with a fake decider.

import { spawn } from 'node:child_process';

// Haiku via the Claude Code CLI (CLAUDE_CODE_OAUTH_TOKEN). First line YES or NO, then the reason.
export async function askHaiku({ state }) {
  const prompt =
    'A BDD acceptance test step is implemented by a Java method. Does the method, together with ' +
    'the methods it calls, do exactly what the step says, no less and nothing else that the step ' +
    'does not say? A "then" step must really check the outcome and fail when it is wrong. It does ' +
    'not, if it has no assertion, compares a value with itself or with what the method just set, ' +
    'accepts any value (only "not null" or "not empty"), swallows an exception, or only reads from ' +
    'the fake and never from what the service produced. It also does not, if it checks more than ' +
    'the step says. A parameter of the step (shown in brackets) stands for the value the method ' +
    'receives. Answer with YES or NO on the first line. After NO give up to three short ' +
    'sentences: what exactly differs between the step and the method, and what to change in the ' +
    'method so that it fits.\n\n' +
    JSON.stringify(state, null, 2);
  const output = await new Promise((resolve, reject) => {
    const child = spawn('claude', ['-p', '--model', 'claude-haiku-5-5'], { stdio: ['pipe', 'pipe', 'inherit'] });
    let out = '';
    child.stdout.on('data', (chunk) => (out += chunk));
    child.on('error', reject);
    child.on('close', (code) => (code === 0 ? resolve(out) : reject(new Error(`claude exited with ${code}`))));
    child.stdin.end(prompt);
  });
  const [first, ...rest] = output.trim().split('\n');
  if (!/^(YES|NO)\b/i.test(first)) throw new Error(`Unexpected answer of Haiku: ${output}`);
  return { pass: /^YES/i.test(first), reason: rest.join(' ').trim() || first };
}
