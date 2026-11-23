// The two model calls of the per-method check. Thin on purpose: `stages.mjs` takes them as
// functions, so everything else is tested with fake deciders.

import { spawn } from 'node:child_process';

// Probability from Jev (TypeSafe, key in TYPESAFE_API_KEY) that the method implements the step.
export async function askJev({ state }) {
  const response = await fetch('https://api.typesafe.ai/v1/systemone', {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: `Bearer ${process.env.TYPESAFE_API_KEY}` },
    body: JSON.stringify({
      model: 'jev-latest',
      state,
      questions: {
        implements: {
          type: 'noul',
          instructions:
            'Does `method.source`, together with the methods in `called`, do exactly what `step` says in the scenario `scenario`?',
          criteria: {
            true: 'Carrying out the method does what the step says, no less and nothing else that the step does not say. A "Dann" step really checks the outcome and fails when the outcome is wrong',
            false:
              'The method does something different, only a part of it, or something more than the step says. Or it is a "Dann" step whose assertion cannot fail or checks less than the step says: no assertion at all, one that compares a value with itself or with what the method just set, one that accepts any value (for example only "not null" or "not empty"), a swallowed exception, or a check that only reads from the fake and never from what the service produced',
          },
        },
      },
    }),
  });
  if (!response.ok) throw new Error(`TypeSafe answered ${response.status}: ${await response.text()}`);
  return (await response.json()).answers.implements.noul;
}

// Haiku via the Claude Code CLI (CLAUDE_CODE_OAUTH_TOKEN). First line YES or NO, then the reason.
export async function askHaiku({ state }) {
  const prompt =
    'A BDD acceptance test step is implemented by a Java method. Does the method, together with ' +
    'the methods it calls, do exactly what the step says, no less and nothing else? Answer with ' +
    'YES or NO on the first line, then one short sentence of reason.\n\n' +
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
