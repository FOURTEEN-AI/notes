// Late conflict of a pull request in phase 2: a test of `main` that the approved delta neither
// replaces nor removes is red in the pull request. That is not for the coding agent to fix; the
// idea goes back to the idea chat (see docs/plan.md, step 6).
//
// Usage: node conflict.mjs <criteria file> <report dir of main> <report dir of the pull request>
// Prints one finding per red test; exit 1 if there is any.

import { parseGherkin } from './gherkin.mjs';
import { readReport } from './report.mjs';

export function redOldTests({ criteria, base, head }) {
  const changed = new Set();
  for (const { title, tags } of parseGherkin(criteria)) {
    changed.add(title);
    if (tags.replaces) changed.add(tags.replaces);
  }
  const old = new Set(base.map((scenario) => scenario.title));
  return head
    .filter((scenario) => scenario.failed && old.has(scenario.title) && !changed.has(scenario.title))
    .map((scenario) => scenario.title);
}

if (import.meta.main) {
  const { readFileSync } = await import('node:fs');
  const [criteria, base, head] = process.argv.slice(2);
  const titles = redOldTests({
    criteria: readFileSync(criteria, 'utf8'),
    base: readReport(base),
    head: readReport(head),
  });
  for (const title of titles) console.log(`- The test "${title}" of main is red`);
  process.exit(titles.length === 0 ? 0 : 1);
}
