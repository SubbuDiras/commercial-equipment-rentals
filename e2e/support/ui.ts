import { Browser, expect, Page } from '@playwright/test';
import { existsSync } from 'node:fs';
import { authFile, env, TestUser } from './env';
import { BookingDetailsPage } from '../pages/booking-details.page';
import { OwnerListingsPage } from '../pages/owner-listings.page';

/**
 * Opens a fresh browser context and signs in through the real Keycloak login page. For a fixed user whose state
 * auth.setup.ts saved, Keycloak still knows the session, so it sends the browser straight back without the form.
 */
export async function signedInPage(browser: Browser, email: string, path = '/'): Promise<Page> {
  const saved = authFile(email);
  const context = await browser.newContext(existsSync(saved) ? { storageState: saved } : {});
  const page = await context.newPage();
  // Start from the public catalogue: a protected path would send us to Keycloak before the header renders.
  await page.goto('/');
  await page.locator('#btn-header-login').click();
  const signOut = page.getByRole('button', { name: 'Sign Out' });
  const username = page.locator('#username');
  await expect(signOut.or(username)).toBeVisible();
  if (await username.isVisible()) {
    await username.fill(email);
    await page.locator('#password').fill(env.password);
    await page.locator('#kc-login').click();
  }
  await expect(signOut).toBeVisible();
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

/** Picks the first category once My Listings has loaded (see OwnerListingsPage.waitUntilLoaded). */
export const chooseListingCategory = (page: Page) => new OwnerListingsPage(page).chooseFirstCategory();

/** Value for an <input type="datetime-local">, in the browser's local time. */
export function localDateTime(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** A time `hours` from now, on the hour, so a form entry and the API agree. */
export function hoursFromNow(hours: number): Date {
  const date = new Date(Date.now() + hours * 3_600_000);
  date.setMinutes(0, 0, 0);
  return date;
}

/** Fills the booking request form with a period of `days` from `start` and submits it. */
export async function requestInForm(page: Page, start: Date, days: number): Promise<void> {
  await page.getByLabel('Start').fill(localDateTime(start));
  await page.getByLabel('End').fill(localDateTime(new Date(start.getTime() + days * 86_400_000)));
  await page.getByRole('button', { name: 'Request booking' }).click();
}

export function dialog(page: Page) {
  return page.getByRole('dialog');
}

// Booking page shortcuts for older specs; new specs use BookingDetailsPage directly.

export const openBooking = (page: Page, bookingId: string) => new BookingDetailsPage(page).open(bookingId);
export const expectStatus = (page: Page, label: string) => new BookingDetailsPage(page).expectStatus(label);
export const generateCode = (page: Page) => new BookingDetailsPage(page).generateCode();
export const uploadInspectionPhotos = (page: Page, count = 4) => new BookingDetailsPage(page).uploadInspectionPhotos(count);
export const confirmInspection = (page: Page, myCode: string, otherCode: string, type: 'handover' | 'return') =>
  new BookingDetailsPage(page).confirmInspection(myCode, otherCode, type);
export const cancelFromBookingPage = (page: Page, reason: string) => new BookingDetailsPage(page).cancel(reason);
