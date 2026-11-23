import { defineConfig, devices } from '@playwright/test';

// Screenshots of the built frontend with mocked data, chromium only. Pixels depend on the OS and
// its fonts, so the snapshots are made and compared in the pinned Playwright image only:
// e2e/visual.sh (also called by `./gradlew e2eVisualTest` and CI).
export default defineConfig({
  testDir: 'e2e/visual',
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report-visual' }]],
  outputDir: 'test-results-visual',
  use: {
    baseURL: 'http://127.0.0.1:18095',
    locale: 'de-DE',
    timezoneId: 'Europe/Berlin',
    viewport: { width: 900, height: 700 },
  },
  expect: { toHaveScreenshot: { animations: 'disabled', maxDiffPixelRatio: 0.002 } },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 900, height: 700 } },
    },
  ],
  webServer: {
    command: 'node e2e/visual/static-server.mjs',
    url: 'http://127.0.0.1:18095',
    reuseExistingServer: !process.env.CI,
  },
});
