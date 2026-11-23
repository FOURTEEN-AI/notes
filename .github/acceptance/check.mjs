// The deterministic part of the check `acceptance`: do the acceptance tests of the pull request
// match the approved scenarios, as a delta against the tests of `main`?

import { parseGherkin } from './gherkin.mjs';
import { readReport } from './report.mjs';

const show = (step) => `${step.keyword} ${step.text} [${step.params.join(' | ')}]`;

function sameSteps(expected, actual) {
  return expected.length === actual.length && expected.every((s, i) => show(s) === show(actual[i]));
}

// Returns a list of findings (strings); empty means the tests match.
export function check({ criteria, base, head }) {
  const findings = [];
  const byTitle = (scenarios, where) => {
    const map = new Map();
    for (const scenario of scenarios) {
      if (map.has(scenario.title)) findings.push(`The title "${scenario.title}" exists twice ${where}`);
      map.set(scenario.title, scenario);
    }
    return map;
  };
  const before = byTitle(base, 'on main');
  const after = byTitle(head, 'in the pull request');
  const approved = parseGherkin(criteria);

  const expectedAdded = new Set();
  const expectedGone = new Set();
  for (const scenario of approved) {
    const { ui, removed, replaces } = scenario.tags;
    if (removed) {
      if (!before.has(scenario.title)) findings.push(`"${scenario.title}" is removed but does not exist on main`);
      expectedGone.add(scenario.title);
      continue;
    }
    if (replaces) {
      if (!before.has(replaces)) findings.push(`"${replaces}" is replaced but does not exist on main`);
      // A scenario that keeps its title and only changes its steps replaces itself.
      if (replaces !== scenario.title) expectedGone.add(replaces);
    } else if (before.has(scenario.title)) {
      findings.push(`"${scenario.title}" is new but exists on main already`);
    }
    if (ui) {
      if (after.has(scenario.title)) findings.push(`"${scenario.title}" is @ui and must have no test`);
      continue;
    }
    expectedAdded.add(scenario.title);
    const test = after.get(scenario.title);
    if (!test) {
      findings.push(`"${scenario.title}" has no test`);
    } else if (!sameSteps(scenario.steps, test.steps)) {
      findings.push(
        `The steps of "${scenario.title}" differ from the approved ones.\n` +
          `  approved: ${scenario.steps.map(show).join(' / ')}\n  test:     ${test.steps.map(show).join(' / ')}`,
      );
    }
  }

  for (const title of after.keys()) {
    if (!before.has(title) && !expectedAdded.has(title)) findings.push(`The test "${title}" is not an approved scenario`);
  }
  for (const [title, scenario] of before) {
    if (expectedAdded.has(title)) continue; // changed in place, compared with the approved steps above
    if (expectedGone.has(title)) {
      if (after.has(title)) findings.push(`The test "${title}" must be gone`);
    } else if (!after.has(title)) {
      findings.push(`The test "${title}" was removed outside the approved delta`);
    } else if (!sameSteps(scenario.steps, after.get(title).steps)) {
      findings.push(`The steps of "${title}" changed outside the approved delta`);
    }
  }
  return findings;
}

// node check.mjs <criteria file> <report dir of main> <report dir of the pull request>
if (import.meta.main) {
  const { readFileSync } = await import('node:fs');
  const [criteria, base, head] = process.argv.slice(2);
  const findings = check({
    criteria: readFileSync(criteria, 'utf8'),
    base: readReport(base),
    head: readReport(head),
  });
  for (const finding of findings) console.log(`- ${finding}`);
  process.exit(findings.length === 0 ? 0 : 1);
}
