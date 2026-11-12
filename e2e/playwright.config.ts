import { defineConfig, devices } from '@playwright/test';
import { env, workerCount } from './support/env';

const ci = !!process.env.CI;

/**
 * Chromium fails the Google Fonts hosts at once, so pages use system fonts. The app's scripts wait for that
 * stylesheet, and when Google answers slowly (18 s has been seen) page.goto times out.
 */
const noWebFonts = {
  launchOptions: { args: ['--host-resolver-rules=MAP fonts.googleapis.com ~NOTFOUND, MAP fonts.gstatic.com ~NOTFOUND'] }
};

/**
 * Runs against an already running local stack; see docs/deployment/local-dev-guide.md.
 * One worker by default. With E2E_WORKERS=N, spec files run on N workers, each with its own fixed users
 * (support/env.ts); tests inside a file still run in order.
 */
export default defineConfig({
  testDir: './tests',
  globalSetup: require.resolve('./global-setup'),
  globalTeardown: require.resolve('./global-teardown'),
  fullyParallel: false,
  workers: workerCount,
  forbidOnly: ci,
  retries: ci ? 1 : 0,
  timeout: 180_000,
  expect: { timeout: 15_000 },
  reporter: ci
    ? [['github'], ['list'], ['html', { open: 'never' }], ['junit', { outputFile: 'results.xml' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: env.appUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 30_000
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/, use: { ...noWebFonts } },
    { name: 'chromium', use: { ...devices['Desktop Chrome'], ...noWebFonts }, dependencies: ['setup'] },
    // Other browsers and a phone run the smoke tests only: npx playwright test --project=firefox
    { name: 'firefox', use: { ...devices['Desktop Firefox'] }, dependencies: ['setup'], grep: /@smoke/ },
    { name: 'webkit', use: { ...devices['Desktop Safari'] }, dependencies: ['setup'], grep: /@smoke/ },
    { name: 'mobile', use: { ...devices['Pixel 7'], ...noWebFonts }, dependencies: ['setup'], grep: /@smoke/ }
  ]
});
