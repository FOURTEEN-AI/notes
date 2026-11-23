// The whole app for the E2E tests, started for Playwright's webServer: a PostgreSQL container, the
// GitHub stub and two instances of the built jar (build/libs/notes.jar, so /jgiven/ is in it):
// production on APP_PORT and a preview app (PULL_REQUEST=7) on PREVIEW_PORT. Login: dev / dev.
// Stops everything on SIGTERM/SIGINT.
import { execFileSync, spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import { startGitHubStub } from './github-stub.mjs';

const APP_PORT = 18080;
const PREVIEW_PORT = 18081;
const STUB_PORT = 18090;
const READY_PORT = 18091; // answers 200 once both apps are up: the URL Playwright waits for
const DB = 'notes-e2e-postgres';
const jar = fileURLToPath(new URL('../../build/libs/notes.jar', import.meta.url));
if (!existsSync(jar)) throw new Error(`${jar} is missing: run ./gradlew bootJar`);

const docker = (...args) => execFileSync('docker', args, { encoding: 'utf8' }).trim();
const children = [];

function stop() {
  for (const child of children) child.kill('SIGTERM');
  try {
    docker('rm', '-f', DB);
  } catch {
    // already gone
  }
  process.exit(0);
}
process.on('SIGTERM', stop);
process.on('SIGINT', stop);

try {
  docker('rm', '-f', DB);
} catch {
  // none left over
}
docker(
  'run',
  '-d',
  '--rm',
  '--name',
  DB,
  '-p',
  '127.0.0.1::5432',
  '-e',
  'POSTGRES_USER=notes',
  '-e',
  'POSTGRES_PASSWORD=notes',
  '-e',
  'POSTGRES_DB=notes',
  'postgres:17-alpine',
);
const dbPort = docker('port', DB, '5432/tcp').split('\n')[0].split(':').pop();
for (let i = 0; ; i++) {
  try {
    docker('exec', DB, 'pg_isready', '-U', 'notes', '-h', '127.0.0.1');
    break;
  } catch {
    if (i > 60) throw new Error('PostgreSQL does not start');
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}

await startGitHubStub(STUB_PORT);

function start(port, extra) {
  const child = spawn(
    'java',
    [
      '-jar',
      jar,
      `--server.port=${port}`,
      `--spring.datasource.url=jdbc:postgresql://127.0.0.1:${dbPort}/notes`,
      '--spring.datasource.username=notes',
      '--spring.datasource.password=notes',
      '--spring.security.user.name=dev',
      '--spring.security.user.password=dev',
      `--github.api-url=http://127.0.0.1:${STUB_PORT}`,
      '--github.token=stub',
      ...extra,
    ],
    { stdio: ['ignore', process.env.E2E_APP_LOG ? 'inherit' : 'ignore', 'inherit'] },
  );
  children.push(child);
  child.on('exit', (code) => {
    if (code) {
      console.error(`app on ${port} exited with ${code}`);
      stop();
    }
  });
}

async function healthy(port) {
  for (let i = 0; ; i++) {
    try {
      if ((await fetch(`http://127.0.0.1:${port}/actuator/health`)).ok) return;
    } catch {
      // not listening yet
    }
    if (i > 240) throw new Error(`the app on ${port} does not start`);
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}

// One after the other: both create the schema in the same database on start.
start(APP_PORT, []);
await healthy(APP_PORT);
start(PREVIEW_PORT, [
  '--pull-request=7',
  '--pull-request-sha=abc1234abc1234abc1234abc1234abc1234abc12',
]);
await healthy(PREVIEW_PORT);
createServer((_, response) => response.end('ready')).listen(READY_PORT, '127.0.0.1');
