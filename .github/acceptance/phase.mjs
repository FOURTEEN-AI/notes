// The phases of the coding agent. Phase 1 writes the acceptance tests, stages, fakes and compiling
// stubs; once `acceptance` is green, the checked commit is recorded in a comment
// "## Acceptance checked <sha>". Phase 2 writes the production code and must leave the acceptance
// tests as they are in the checked commit. A commit whose message has the line "Phase: 1" goes back
// to phase 1 and may change them.
//
// Usage: node phase.mjs <head sha> <checked sha or empty> <findings file>   (in the repository)
// Prints `phase=1|2` and `violation=true|false` (for $GITHUB_OUTPUT); findings go to the file.

import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';

export const PROTECTED = 'src/test/java/de/fourteen/notes/acceptance/';

// checked: the checked commit is known and an ancestor of head; null if there is none.
// changed: files under PROTECTED that differ between the checked commit and head.
export function decide({ checked, changed, message }) {
  if (!checked) return { phase: 1, findings: [] };
  if (changed.length === 0) return { phase: 2, findings: [] };
  if (/^Phase: 1$/m.test(message)) return { phase: 1, findings: [] };
  return {
    phase: 2,
    findings: [
      `Phase 2 changed the acceptance tests, stages or fakes that \`acceptance\` has checked (${checked}):`,
      '',
      ...changed.map((file) => `- ${file}`),
      '',
      'Restore them as they are in that commit. If they really need a change, go back to phase 1: ' +
        'commit the change to the tests, stages, fakes and stubs only, with the line `Phase: 1` ' +
        'at the end of the commit message.',
    ],
  };
}

const git = (...args) => execFileSync('git', args, { encoding: 'utf8' });

function main([head, checkedSha, findingsFile]) {
  let checked = null;
  let changed = [];
  if (checkedSha) {
    try {
      git('merge-base', '--is-ancestor', checkedSha, head);
      checked = checkedSha;
      changed = git('diff', '--name-only', checkedSha, head, '--', PROTECTED).split('\n').filter(Boolean);
    } catch {
      // The checked commit is gone (rewritten history): check from phase 1 again.
    }
  }
  const { phase, findings } = decide({ checked, changed, message: git('log', '-1', '--format=%B', head) });
  writeFileSync(findingsFile, findings.join('\n'));
  console.log(`phase=${phase}`);
  console.log(`violation=${findings.length > 0}`);
}

if (process.argv[1] === new URL(import.meta.url).pathname) main(process.argv.slice(2));
