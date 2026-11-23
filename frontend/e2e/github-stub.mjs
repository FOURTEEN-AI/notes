// A tiny stand-in for the GitHub REST API: the ideas (issues) and the preview bar (pull request)
// of the app talk to it instead of api.github.com. In memory, no login.
import { createServer } from 'node:http';

const PR_SHA = 'abc1234abc1234abc1234abc1234abc1234abc12';
const DEMO = [
  { do: 'Schreibe eine Notiz und speichere sie', expect: 'Sie steht oben', selector: 'textarea' },
  { do: 'Lade die Seite neu', expect: 'Die Notiz ist noch da' },
];

let issues, comments, nextNumber;

// The state at the start; POST /__reset brings the stub back to it between tests.
function reset() {
  issues = [
    { number: 11, title: 'Dunkles Design', body: 'Die App soll dunkel sein.', labels: ['refine'] },
    { number: 12, title: 'Notizen sortieren', body: 'Nach Datum sortieren.', labels: ['agent'] },
  ];
  comments = { 11: [{ body: '## Refine\n\nWie dunkel soll es sein?' }], 12: [] };
  nextNumber = 20;
}
reset();

const json = (response, status, body) => {
  response.writeHead(status, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify(body));
};

const pull = {
  number: 7,
  title: 'Notizen sortieren',
  body: 'Sortiert.\n\n```demo\n' + JSON.stringify(DEMO) + '\n```\n',
  html_url: 'https://github.example/pull/7',
  state: 'open',
  merged: false,
  head: { ref: 'agent/issue-12', sha: PR_SHA },
};

export function startGitHubStub(port) {
  const server = createServer((request, response) => {
    const chunks = [];
    request.on('data', (chunk) => chunks.push(chunk));
    request.on('end', () => {
      const url = new URL(request.url, 'http://stub');
      const path = url.pathname.replace(/^\/repos\/[^/]+\/[^/]+/, '');
      const body = chunks.length > 0 ? JSON.parse(Buffer.concat(chunks).toString()) : {};
      const method = request.method;
      let match;
      if (method === 'POST' && path === '/__reset') {
        reset();
        return json(response, 200, {});
      }
      if (method === 'GET' && path === '/issues') {
        return json(
          response,
          200,
          issues.map((issue) => ({ ...issue, labels: issue.labels.map((name) => ({ name })) })),
        );
      }
      if (method === 'POST' && path === '/issues') {
        const issue = {
          number: nextNumber++,
          title: body.title,
          body: body.body,
          labels: ['refine'],
        };
        issues.unshift(issue);
        comments[issue.number] = [];
        return json(response, 201, { ...issue, labels: [{ name: 'refine' }] });
      }
      if (method === 'GET' && path === '/pulls') return json(response, 200, []);
      if (method === 'GET' && path === '/pulls/7') return json(response, 200, pull);
      if (method === 'GET' && path.startsWith('/actions/workflows/')) {
        return json(response, 200, { workflow_runs: [] });
      }
      if (method === 'GET' && path.startsWith('/commits/')) {
        const runs = ['build', 'e2e', 'review', 'acceptance'].map((name, id) => ({
          id: id + 1,
          name,
          status: 'completed',
          conclusion: 'success',
        }));
        return json(response, 200, { check_runs: runs });
      }
      if ((match = /^\/issues\/(\d+)\/comments$/.exec(path))) {
        const list = (comments[match[1]] ??= []);
        if (method === 'POST') {
          list.push({ body: body.body });
          return json(response, 201, { body: body.body });
        }
        return json(response, 200, list);
      }
      return json(response, 404, { message: `stub: ${method} ${path}` });
    });
  });
  return new Promise((resolve) => server.listen(port, '127.0.0.1', () => resolve(server)));
}
