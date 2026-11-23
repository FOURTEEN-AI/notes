import { defineConfig, devices } from '@playwright/test';

// The real app (the jar with PostgreSQL and a GitHub stub, e2e/stack.mjs): production on 18080 and
// a preview app on 18081. Run it with `./gradlew e2eTest` (builds the jar) or `pnpm e2e`.
export default defineConfig({
  testDir: 'e2e',
  testIgnore: 'visual/**',
  // The tests share one database, so they run one after the other and clean up the notes they write.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:18080',
    httpCredentials: { username: 'dev', password: 'dev' },
    locale: 'de-DE',
    timezoneId: 'Europe/Berlin',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: {
    command: 'node e2e/stack.mjs',
    url: 'http://127.0.0.1:18091',
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    gracefulShutdown: { signal: 'SIGTERM', timeout: 20_000 },
  },
});
