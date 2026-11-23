// Writes api.github.com.json: the part of GitHub's official OpenAPI description (github/rest-api-
// description, api.github.com) that GitHubClient uses, at a pinned commit. Run `node trim.mjs`
// after changing COMMIT, SHA256 or OPERATIONS; needs network (the build itself never downloads).
// Kept: the listed operations and every component they reference. Dropped: prose and examples.
import { createHash } from 'node:crypto';
import { writeFileSync } from 'node:fs';

const COMMIT = 'c9c95fe34ced05fe076001f5c55704742bc8e7f8';
const SHA256 = '6d9bbb20d8865b01464028937f2403954ad46598b4ebd3513dcb1a15b1a84aa7'; // of the full file
const SOURCE = `https://raw.githubusercontent.com/github/rest-api-description/${COMMIT}/descriptions/api.github.com/api.github.com.json`;
const OPERATIONS = {
  '/repos/{owner}/{repo}/issues': ['get', 'post'],
  '/repos/{owner}/{repo}/issues/{issue_number}/comments': ['get', 'post'],
  '/repos/{owner}/{repo}/issues/{issue_number}/labels': ['post'],
  '/repos/{owner}/{repo}/issues/{issue_number}/labels/{name}': ['delete'],
  '/repos/{owner}/{repo}/pulls': ['get'],
  '/repos/{owner}/{repo}/pulls/{pull_number}': ['get'],
  '/repos/{owner}/{repo}/pulls/{pull_number}/merge': ['put'],
  '/repos/{owner}/{repo}/actions/workflows/{workflow_id}/runs': ['get'],
  '/repos/{owner}/{repo}/commits/{ref}/check-runs': ['get'],
};
const DROPPED = new Set(['description', 'example', 'examples', 'externalDocs', 'x-github', 'tags']);

const bytes = Buffer.from(await (await fetch(SOURCE)).arrayBuffer());
const sha = createHash('sha256').update(bytes).digest('hex');
if (sha !== SHA256) throw new Error(`checksum ${sha} is not ${SHA256}`);
const full = JSON.parse(bytes.toString());

const strip = (node) =>
  Array.isArray(node)
    ? node.map(strip)
    : node && typeof node === 'object'
      ? Object.fromEntries(
          Object.entries(node)
            .filter(([key]) => !DROPPED.has(key))
            .map(([key, value]) => [key, strip(value)]),
        )
      : node;

const paths = {};
for (const [path, methods] of Object.entries(OPERATIONS)) {
  paths[path] = {};
  for (const method of methods) paths[path][method] = full.paths[path][method];
}
const result = {
  openapi: full.openapi,
  info: { title: full.info.title, version: full.info.version },
  paths: strip(paths),
  components: {},
};

// Copies every component that is referenced, and those it references in turn.
const queue = [JSON.stringify(result.paths)];
while (queue.length > 0) {
  for (const [, section, name] of queue.pop().matchAll(/#\/components\/([^/"]+)\/([^"]+)"/g)) {
    if (result.components[section]?.[name]) continue;
    (result.components[section] ??= {})[name] = strip(full.components[section][name]);
    queue.push(JSON.stringify(result.components[section][name]));
  }
}
writeFileSync(new URL('api.github.com.json', import.meta.url), JSON.stringify(result, null, 1) + '\n');
