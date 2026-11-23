// The approved acceptance criteria of an idea: the latest comment that starts with
// "## Acceptance criteria" and is written by a human with write access. The app posts it with the
// token of an org member; the agents (bots, `CONTRIBUTOR`) can comment on the issue too, but their
// comment is no approval.

const HUMANS = new Set(['OWNER', 'MEMBER', 'COLLABORATOR']);

// comments: [{ body, authorAssociation }] as `gh issue view --json comments` gives them.
export function latestCriteria(comments) {
  return (
    comments
      .filter((comment) => comment.body.startsWith('## Acceptance criteria') && HUMANS.has(comment.authorAssociation))
      .at(-1)?.body ?? ''
  );
}

// gh issue view <n> --json comments | node criteria.mjs > criteria.md
if (import.meta.main) {
  const { readFileSync } = await import('node:fs');
  process.stdout.write(latestCriteria(JSON.parse(readFileSync(0, 'utf8')).comments));
}
