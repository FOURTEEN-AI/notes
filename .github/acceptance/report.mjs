import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

// Reads the JGiven JSON results of a directory into scenarios:
// { title, steps: [{ keyword, text, params }] }. The title is the test's @Description.
export function readReport(dir) {
  const scenarios = [];
  for (const file of readdirSync(dir).filter((name) => name.endsWith('.json'))) {
    const report = JSON.parse(readFileSync(join(dir, file), 'utf8'));
    for (const scenario of report.scenarios) {
      const steps = scenario.scenarioCases[0].steps.filter((step) => step.depth === 0);
      scenarios.push({ title: scenario.description, steps: steps.map(toStep) });
    }
  }
  return scenarios;
}

function toStep(step) {
  const [intro, ...words] = step.words;
  const params = words.filter((word) => word.argumentInfo).map((w) => w.argumentInfo.formattedValue);
  const text = words.map((word) => (word.argumentInfo ? '$' : word.value)).join(' ');
  return { keyword: intro.value.toLowerCase(), text, params };
}
