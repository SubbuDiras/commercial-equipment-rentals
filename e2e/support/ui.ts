import { Browser, expect, Page } from '@playwright/test';
import { env, TestUser } from './env';
import { photos } from './files';

/** Opens a fresh browser context and signs in through the real Keycloak login page. */
export async function signedInPage(browser: Browser, email: string, path = '/'): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  // Start from the public catalogue: a protected path would send us to Keycloak before the header renders.
  await page.goto('/');
  await page.locator('#btn-header-login').click();
  await page.locator('#username').fill(email);
  await page.locator('#password').fill(env.password);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('button', { name: 'Sign Out' })).toBeVisible();
  if (path !== '/') {
    await page.goto(path);
  }
  return page;
}

/** Self-registers a new account on the Keycloak registration page; the user lands back in the app signed in. */
export async function register(browser: Browser, user: TestUser): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto('/');
  await page.locator('#btn-header-login').click();
  await page.getByRole('link', { name: /register/i }).click();
  const username = page.locator('#username');
  if (await username.isVisible()) {
    await username.fill(user.email);
  }
  await page.locator('#email').fill(user.email);
  await page.locator('#firstName').fill(user.firstName);
  await page.locator('#lastName').fill(user.lastName);
  await page.locator('#password').fill(env.password);
  await page.locator('#password-confirm').fill(env.password);
  await page.locator('input[type=submit], button[type=submit]').first().click();
  await expect(page.getByRole('button', { name: 'Sign Out' })).toBeVisible();
  return page;
}

/** Signs out and back in, so the token carries roles granted since the last sign-in. */
export async function signInAgain(page: Page, email: string): Promise<void> {
  await page.getByRole('button', { name: 'Sign Out' }).click();
  await page.goto('/');
  await page.locator('#btn-header-login').click();
  const username = page.locator('#username');
  if (await username.isVisible()) {
    await username.fill(email);
    await page.locator('#password').fill(env.password);
    await page.locator('#kc-login').click();
  }
  await expect(page.getByRole('button', { name: 'Sign Out' })).toBeVisible();
}

export function dialog(page: Page) {
  return page.getByRole('dialog');
}

export async function openBooking(page: Page, bookingId: string): Promise<void> {
  await page.goto(`/bookings/${bookingId}`);
  await expect(page.getByText(/^Booking BKG-/)).toBeVisible();
}

export async function expectStatus(page: Page, label: string): Promise<void> {
  await expect(page.locator('header app-status-badge')).toHaveText(label);
}

/** Generates this user's handover/return code on the booking page and returns it (shown in development only). */
export async function generateCode(page: Page): Promise<string> {
  await page.getByRole('button', { name: /Generate (my|a new) code/ }).click();
  const code = page.getByText(/Development only/).locator('strong');
  await expect(code).toHaveText(/^\d{6}$/);
  return (await code.textContent())!.trim();
}

export async function uploadInspectionPhotos(page: Page, count = 4): Promise<void> {
  await page.locator('app-inspection-wizard input[type=file]').setInputFiles(photos(count));
  await expect(page.getByText(new RegExp(`Photos \\(${count} of at least 4\\)`))).toBeVisible();
}

/** Fills and submits the inspection confirmation, entering this user's code and the other side's code. */
export async function confirmInspection(page: Page, myCode: string, otherCode: string, type: 'handover' | 'return'): Promise<void> {
  await page.getByLabel('Hour meter reading').fill(type === 'handover' ? '100' : '112.5');
  await page.getByLabel('All accessories present').check();
  await page.getByLabel('Overall condition acceptable').check();
  await page.getByLabel('Your code').fill(myCode);
  await page.getByLabel(/^(Renter|Owner)'s code/).fill(otherCode);
  await page.getByRole('button', { name: `Confirm ${type}` }).click();
}

/** Cancels from the booking page and returns the quoted total refund text. */
export async function cancelFromBookingPage(page: Page, reason: string): Promise<string> {
  await page.getByRole('button', { name: 'Cancel booking' }).click();
  const modal = dialog(page);
  await expect(modal.getByText('Total refund').or(modal.getByText('No payment has been taken'))).toBeVisible();
  const totalCell = modal.locator('dt:has-text("Total refund") + dd');
  const total = (await totalCell.count()) > 0 ? await totalCell.textContent() : null;
  await modal.getByLabel('Reason for cancelling').fill(reason);
  await modal.getByRole('button', { name: 'Cancel booking' }).click();
  await expect(page.getByText('The booking has been cancelled.')).toBeVisible();
  return (total ?? '').trim();
}
