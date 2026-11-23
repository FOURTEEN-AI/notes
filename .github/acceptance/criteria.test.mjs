import assert from 'node:assert/strict';
import { test } from 'node:test';
import { latestCriteria } from './criteria.mjs';

const comment = (body, authorAssociation) => ({ body, authorAssociation });

test('the latest criteria of a human count', () => {
  const comments = [
    comment('## Acceptance criteria\nalt', 'MEMBER'),
    comment('## Idea chat\nPasst', 'MEMBER'),
    comment('## Acceptance criteria\nneu', 'OWNER'),
  ];
  assert.equal(latestCriteria(comments), '## Acceptance criteria\nneu');
});

test('criteria that an agent posts are no approval', () => {
  const comments = [
    comment('## Acceptance criteria\nfreigegeben', 'MEMBER'),
    comment('## Acceptance criteria\nselbst gemacht', 'CONTRIBUTOR'),
    comment('## Acceptance criteria\nvon irgendwem', 'NONE'),
  ];
  assert.equal(latestCriteria(comments), '## Acceptance criteria\nfreigegeben');
  assert.equal(latestCriteria([comments[1]]), '');
});
