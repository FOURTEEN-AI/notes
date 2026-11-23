// The per-method part of the check `acceptance`: every stage and fake method that changed against
// `main` is judged in every scenario that uses it. The model calls are passed in (`models.mjs`).

import { basename } from 'node:path';
import { readReport } from './report.mjs';

const ACCEPTANCE = '/acceptance/';

// Methods of one Java file: { key: 'Class.name', name, text, source }. `text` is the step text
// the method stands for (its @As value, else its name with spaces).
export function parseMethods(file, source) {
  const cls = basename(file, '.java');
  const lines = source.split('\n');
  const methods = [];
  for (let i = 0; i < lines.length; i++) {
    if (!/^ {4}[^\s@/*}]/.test(lines[i])) continue;
    let head = '';
    let end = i;
    for (; end < lines.length; end++) {
      head += `${lines[end]}\n`;
      if (/[{;]/.test(lines[end])) break;
    }
    const cut = head.search(/[{;]/);
    const name = cut >= 0 && head[cut] === '{' && /([\w$]+)\s*\([^)]*\)\s*(throws[\w\s,.]*)?$/.exec(head.slice(0, cut))?.[1];
    if (!name) continue;
    let depth = 0;
    let last = end;
    for (let k = end; k < lines.length; k++) {
      const from = k === end ? lines[k].indexOf('{') : 0;
      for (const ch of lines[k].slice(from)) depth += ch === '{' ? 1 : ch === '}' ? -1 : 0;
      last = k;
      if (depth === 0) break;
    }
    let first = i;
    while (first > 0 && /^ {4}@/.test(lines[first - 1])) first--;
    const lead = lines.slice(first, i).join('\n');
    const as = /@As\("([^"]*)"\)/.exec(lead)?.[1];
    methods.push({
      key: `${cls}.${name}`,
      name,
      text: as ?? name.replaceAll('_', ' '),
      source: lines.slice(first, last + 1).join('\n'),
    });
  }
  return methods;
}

const parseAll = (sources) =>
  Object.entries(sources).flatMap(([file, source]) => parseMethods(file, source).map((m) => ({ ...m, file })));

const squash = (source) => source.replace(/\s+/g, ' ').trim();
const showStep = (step) => `${step.keyword} ${step.text}${step.params.length ? ` [${step.params.join(' | ')}]` : ''}`;

// Sources are { path: text } of the Java files (acceptance tests of the repository and the
// application and domain code they call). Returns one question per step whose method, or one
// of the methods it calls, changed in the acceptance directory against `main`.
export function buildQuestions({ scenarios, base, head }) {
  const before = new Map(parseAll(base).map((m) => [m.key, squash(m.source)]));
  const methods = parseAll(head);
  const byKey = new Map(methods.map((m) => [m.key, m]));
  const byName = Map.groupBy(methods, (m) => m.name);
  const changed = new Set(
    methods.filter((m) => m.file.includes(ACCEPTANCE) && before.get(m.key) !== squash(m.source)).map((m) => m.key),
  );

  const reached = (start) => {
    const seen = new Set([start.key]);
    let frontier = [start];
    for (let depth = 0; depth < 3; depth++) {
      const next = [];
      for (const method of frontier) {
        for (const [, name] of method.source.matchAll(/([\w$]+)\s*\(/g)) {
          for (const callee of byName.get(name) ?? []) {
            if (!seen.has(callee.key)) {
              seen.add(callee.key);
              next.push(callee);
            }
          }
        }
      }
      frontier = next;
    }
    return seen;
  };

  const questions = [];
  for (const scenario of scenarios) {
    for (const step of scenario.steps) {
      const method = methods.find((m) => m.file.includes(ACCEPTANCE) && m.text === step.text);
      if (!method) continue;
      const keys = reached(method);
      const touched = [...keys].filter((key) => changed.has(key));
      if (touched.length === 0) continue;
      const called = Object.fromEntries([...keys].filter((key) => key !== method.key).map((key) => [key, byKey.get(key).source]));
      questions.push({
        label: `"${showStep(step)}" in "${scenario.title}"`,
        method: method.key,
        changed: touched,
        state: {
          scenario: { title: scenario.title, steps: scenario.steps.map(showStep) },
          step: showStep(step),
          method: { name: method.key, source: method.source },
          called,
        },
      });
    }
  }
  return questions;
}

// Steps of the report that no stage method stands for (e.g. a parameter without `$` in the name,
// whose value JGiven appends, or a method the parser does not see). They could not be judged, so
// each is a finding instead of being skipped.
export function unmatchedSteps({ scenarios, head }) {
  const texts = new Set(parseAll(head).filter((m) => m.file.includes(ACCEPTANCE)).map((m) => m.text));
  return scenarios.flatMap((scenario) =>
    scenario.steps
      .filter((step) => !texts.has(step.text))
      .map(
        (step) =>
          `No stage method stands for "${showStep(step)}" in "${scenario.title}", so it cannot be judged. ` +
          'Name the method like the step, with `$` where a parameter goes, or give it an `@As` with that text.',
      ),
  );
}

// `jev(question)` returns the probability that the method implements the step, `haiku(question)`
// decides { pass, reason } between 0.5 and 0.9. Returns findings; empty means all pass.
export async function judge(questions, { jev, haiku }) {
  const findings = [];
  for (const question of questions) {
    const p = await jev(question);
    if (p >= 0.9) continue;
    const where = `${question.label}: ${question.method} (changed: ${question.changed.join(', ')})`;
    if (p < 0.5) {
      findings.push(`The method does not implement its step, Jev p=${p.toFixed(2)}. ${where}`);
      continue;
    }
    const verdict = await haiku(question);
    if (!verdict.pass) {
      findings.push(`The method does not implement its step, Jev p=${p.toFixed(2)}, Haiku: ${verdict.reason}. ${where}`);
    }
  }
  return findings;
}

// node stages.mjs <repo of main> <repo of the pull request> <its JGiven report dir>
// (Java files, read as text only)
if (import.meta.main) {
  const { readFileSync, readdirSync } = await import('node:fs');
  const { join } = await import('node:path');
  const { askJev, askHaiku } = await import('./models.mjs');
  const [base, head, headReport] = process.argv.slice(2);
  const javaFiles = (root) => {
    const sources = {};
    for (const dir of ['src/test/java/de/fourteen/notes/acceptance', 'src/main/java/de/fourteen/notes/application', 'src/main/java/de/fourteen/notes/domain']) {
      for (const file of readdirSync(join(root, dir)).filter((name) => name.endsWith('.java'))) {
        sources[`${dir}/${file}`] = readFileSync(join(root, dir, file), 'utf8');
      }
    }
    return sources;
  };
  try {
    const scenarios = readReport(headReport);
    const sources = { base: javaFiles(base), head: javaFiles(head) };
    const questions = buildQuestions({ scenarios, ...sources });
    const findings = [
      ...unmatchedSteps({ scenarios, head: sources.head }),
      ...(await judge(questions, { jev: askJev, haiku: askHaiku })),
    ];
    console.error(`${questions.length} step(s) judged.`);
    for (const finding of findings) console.log(`- ${finding}`);
    process.exit(findings.length === 0 ? 0 : 1);
  } catch (error) {
    // A broken model call is an error of the check, not a finding for the coding agent.
    console.error(error);
    process.exit(2);
  }
}
