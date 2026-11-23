import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

// Reads the JGiven JSON results of a directory into scenarios:
// { title, failed, pending, reason, steps: [{ keyword, text, params }] }. The title is the test's @Description.
// A pending test (`@Pending`) has the status SCENARIO_PENDING; `reason` is the value of its `@Manuell("<reason>")` tag, else null.
export function readReport(dir) {
  const scenarios = [];
  for (const file of readdirSync(dir).filter((name) => name.endsWith('.json'))) {
    const report = JSON.parse(readFileSync(join(dir, file), 'utf8'));
    for (const scenario of report.scenarios) {
      const steps = scenario.scenarioCases[0].steps.filter((step) => step.depth === 0);
      const tag = (scenario.tagIds ?? []).map((id) => report.tagMap?.[id]).find((t) => t?.name === 'manuell');
      scenarios.push({
        title: scenario.description,
        failed: scenario.scenarioCases[0].status === 'FAILED',
        pending: scenario.scenarioCases[0].status === 'SCENARIO_PENDING',
        reason: tag ? (tag.value ?? '') : null,
        steps: steps.map(toStep),
      });
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
