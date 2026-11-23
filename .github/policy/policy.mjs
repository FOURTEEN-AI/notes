// Compliance policy of the workflows: "Secrets per job" in docs/process.md, as data (policy.json,
// one entry per job, `<file>#<job id>`), checked against .github/workflows/*.yml.
//
// A job may use only the listed secrets (`secretSteps`: only in the named step), only the listed
// token permissions (at most the listed level; none declared at all is a finding), and runs code
// of the pull request as `pullRequestCode` says: "no" (no step does), "jar" (steps may run the
// scripts of the checkout, but no secret is in scope of such a step), "yes" (anything; the secrets
// of the job are then exposed to that code, so list it only deliberately). `noCache`: no cache.
//
// Heuristics, not a proof (see docs/process.md): a step runs code of the pull request when the job
// checks it out (`ref` is the head of the pull request, or no `ref` in a workflow with the trigger
// `pull_request`, which is the merge commit; `gh pr checkout`, `git checkout`) and the step runs a
// build tool, a script of the checkout or a local action, or lets the agent run one
// (`--allowedTools "Bash(./gradlew:*)"`). `node` on a script from `$RUNNER_TEMP` or `base/` (taken
// from `main`) does not count. Secrets in scope of a step: its own, those of its job and workflow.

import { readdirSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

// `yaml` is a pinned devDependency of the frontend (`./gradlew frontendInstall`).
export const { parse } = createRequire(new URL('../../frontend/package.json', import.meta.url))('yaml');

const LEVEL = { none: 0, read: 1, write: 2 };
const PR_REF = /github\.(head_ref|event\.pull_request\.(head|merge)|event\.workflow_run\.head)|refs\/pull\//;
const EXECUTORS = new Set(['gradlew', 'pnpm', 'npm', 'npx', 'yarn', 'node', 'bash', 'sh', 'python', 'python3', 'make', 'mvn']);
const FROM_MAIN = /^"?(\$RUNNER_TEMP|\$\{\{ runner\.temp \}\}|base\/|\.\.\/base\/)/;

export function loadWorkflows(dir) {
  return Object.fromEntries(
    readdirSync(dir)
      .filter((name) => /\.ya?ml$/.test(name))
      .map((name) => [name, parse(readFileSync(new URL(name, dir), 'utf8'))]),
  );
}

export function check(policy, workflows) {
  const findings = [];
  const jobs = [];
  for (const [file, doc] of Object.entries(workflows)) {
    for (const [id, job] of Object.entries(doc.jobs ?? {})) {
      const key = `${file}#${id}`;
      jobs.push(key);
      if (!policy[key]) findings.push(`${key}: no entry in the policy (every job needs one)`);
      else checkJob(key, policy[key], doc, job, findings);
    }
  }
  for (const key of Object.keys(policy)) {
    if (!jobs.includes(key)) findings.push(`${key}: the policy has an entry for a job that does not exist`);
  }
  return findings;
}

function checkJob(key, rule, doc, job, findings) {
  const found = (message) => findings.push(`${key}: ${message}`);
  const steps = job.steps ?? [];
  const wide = secretsIn({ ...doc, jobs: undefined });
  const narrow = new Set([...wide, ...secretsIn({ ...job, steps: undefined, secrets: undefined }), ...(job.secrets === 'inherit' ? ['(inherit)'] : [])]);
  const used = new Set([...narrow, ...steps.flatMap((step) => [...secretsIn(step)])]);

  for (const name of used) {
    if (!rule.secrets.includes(name)) found(`uses the secret ${name}, which the policy does not allow`);
  }
  for (const [name, step] of Object.entries(rule.secretSteps ?? {})) {
    if (narrow.has(name)) found(`${name} is set for the whole job, the policy allows it only in the step "${step}"`);
    for (const other of steps.filter((s) => secretsIn(s).has(name) && s.name !== step)) {
      found(`${name} is used in the step "${other.name ?? other.uses ?? other.run}", the policy allows it only in "${step}"`);
    }
  }

  const permissions = job.permissions ?? doc.permissions;
  if (permissions === undefined) found('declares no permissions (the default of the repository would apply)');
  else if (typeof permissions === 'string') found(`permissions: ${permissions}, list the scopes instead`);
  else {
    for (const [scope, level] of Object.entries(permissions)) {
      if (LEVEL[level] > LEVEL[rule.permissions[scope] ?? 'none']) {
        found(`token permission ${scope}: ${level} is wider than the policy (${scope}: ${rule.permissions[scope] ?? 'none'})`);
      }
    }
  }

  const dirs = pullRequestDirs(doc, steps);
  if (rule.pullRequestCode !== 'yes') {
    for (const step of steps.filter((s) => runsCode(s, dirs))) {
      const name = step.name ?? step.uses ?? step.run;
      if (rule.pullRequestCode === 'no') found(`the step "${name}" runs code of the pull request, the policy says no`);
      else if ([...narrow, ...secretsIn(step)].length > 0) found(`the step "${name}" runs code of the pull request with a secret in scope`);
    }
  }

  if (rule.noCache) {
    for (const step of steps.filter((s) => /^(actions\/cache|gradle\/actions\/setup-gradle)@/.test(s.uses ?? '') || s.with?.cache)) {
      found(`the step "${step.name ?? step.uses}" uses a cache, the policy says none`);
    }
  }
}

// Secret names (upper case) referenced anywhere in a value; GITHUB_TOKEN is the job token, limited by `permissions`.
function secretsIn(value) {
  const text = JSON.stringify(value ?? null);
  const names = new Set([...text.matchAll(/secrets\.([A-Za-z0-9_-]+)/g)].map((m) => m[1].toUpperCase()));
  names.delete('GITHUB_TOKEN');
  if (/secrets\s*\[|toJSON\(\s*secrets\s*\)/.test(text)) names.add('(dynamic access)');
  return names;
}

// Directories of the workspace that hold code of the pull request.
function pullRequestDirs(doc, steps) {
  const on = typeof doc.on === 'string' ? [doc.on] : Array.isArray(doc.on) ? doc.on : Object.keys(doc.on ?? {});
  const dirs = new Set();
  for (const step of steps) {
    if (/^actions\/checkout@/.test(step.uses ?? '')) {
      const ref = String(step.with?.ref ?? '');
      if (ref ? PR_REF.test(ref) : on.includes('pull_request')) dirs.add(step.with?.path ?? '.');
    }
    if (/\bgh pr checkout\b|\bgit (checkout|switch)\b/.test(step.run ?? '')) dirs.add('.');
  }
  return dirs;
}

function runsCode(step, dirs) {
  if (dirs.size === 0) return false;
  if (step.uses) {
    return dirs.has('.') && (step.uses.startsWith('./') || (/claude-code-action/.test(step.uses) && allowsCode(step.with?.claude_args)));
  }
  if (typeof step.run !== 'string') return false;
  const text = commands(step.run);
  const runs = executes(text);
  return [...dirs].some((dir) => {
    if (dir === '.') return runs;
    const inside = (step['working-directory'] ?? '').replace(/^\.\//, '').startsWith(dir);
    return (inside && runs) || (new RegExp(`(^|[\\s;&|(])cd\\s+["']?(\\./)?${dir.replace(/\W/g, '\\$&')}(["'/\\s;&|)]|$)`).test(text) && runs);
  });
}

// The script without comments; separators inside quotes cannot start a command.
function commands(script) {
  return script
    .split('\n')
    .filter((line) => !/^\s*#/.test(line))
    .join('\n')
    .replace(/'[^']*'|"[^"]*"/g, (quoted) => quoted.replace(/[;&|()`]/g, ' '));
}

function executes(text) {
  for (const segment of text.split(/&&|\|\||[;|\n]|\$\(|[()]/)) {
    const words = segment.trim().split(/\s+/);
    while (/^(if|then|else|elif|do|while|until|time|!|\{)$/.test(words[0] ?? '') || /^\w+=/.test(words[0] ?? '')) words.shift();
    const [command, ...args] = words;
    if (!command) continue;
    if (command === 'node' && FROM_MAIN.test(args.find((arg) => !arg.startsWith('-')) ?? '')) continue;
    if (EXECUTORS.has(command.replace(/^\.\//, '')) || /^\.{1,2}\/|^\.github\//.test(command)) return true;
  }
  return false;
}

// `--allowedTools "Read,Bash(./gradlew:*),..."`: may the agent run a build tool or a script?
function allowsCode(args = '') {
  const list = /--allowedTools\s+"([^"]*)"/.exec(args)?.[1] ?? '';
  return list.split(/,(?![^()]*\))/).some((tool) => {
    if (tool.trim() === 'Bash') return true;
    const command = /^Bash\((.*)\)$/.exec(tool.trim())?.[1].split(/[:\s]/)[0];
    return command !== undefined && (command === '*' || EXECUTORS.has(command.replace(/^\.\//, '')) || /^\.{1,2}\//.test(command));
  });
}
