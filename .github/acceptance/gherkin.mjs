// Reads the German Gherkin of an `## Acceptance criteria` comment into scenarios.

const KEYWORDS = {
  'gegeben sei': 'given',
  angenommen: 'given',
  wenn: 'when',
  dann: 'then',
  und: 'and',
  aber: 'but',
};
const STEP = new RegExp(`^(${Object.keys(KEYWORDS).join('|')})\\s+(.*)$`, 'i');

// A step text with its values as placeholders: `ich speichere "x"` -> text `ich speichere $`,
// params ['x']. Same shape as a step of the JGiven report.
export function parameterize(text) {
  const params = [];
  const replaced = text.replace(/"([^"]*)"/g, (_, value) => {
    params.push(value);
    return '$';
  });
  return { text: replaced.trim(), params };
}

// Returns scenarios: { title, tags: { ui, removed, replaces }, steps: [{ keyword, text, params }] }.
// `replaces` is the old title of a changed scenario (`@ersetzt("<old title>")`), else null.
export function parseGherkin(source) {
  const scenarios = [];
  let tags = [];
  let current = null;
  for (const raw of source.split(/\r?\n/)) {
    const line = raw.trim();
    if (line === '' || line.startsWith('#')) continue;
    if (line.startsWith('@')) {
      tags.push(...line.match(/@[^\s(]+(\([^)]*\))?/g));
      continue;
    }
    const header = line.match(/^(?:Szenario|Szenariogrundriss):\s*(.+)$/i);
    if (header) {
      current = { title: header[1].trim(), tags: readTags(tags), steps: [] };
      scenarios.push(current);
      tags = [];
      continue;
    }
    const step = line.match(STEP);
    if (step && current) {
      current.steps.push({ keyword: KEYWORDS[step[1].toLowerCase()], ...parameterize(step[2]) });
    }
    // Everything else (Funktionalität, Hintergrund, descriptions) is not a scenario.
  }
  return scenarios;
}

function readTags(tags) {
  const replaces = tags.find((tag) => tag.startsWith('@ersetzt('));
  return {
    ui: tags.includes('@ui'),
    removed: tags.includes('@entfällt'),
    replaces: replaces ? replaces.match(/^@ersetzt\("(.*)"\)$/)?.[1] ?? null : null,
  };
}
