import { OwnerOnboardingPage } from '../pages/owner-onboarding.page';
import { uniqueEmail } from '../support/env';
import { KeycloakAdmin } from '../support/keycloak-admin';
import { expect, test } from '../support/test';
import { signedInPage } from '../support/ui';

/** Each badly formatted KYC field says exactly what is wrong once the user leaves it, and nothing is submitted. */
test('owner application explains badly formatted PAN, Aadhaar, IFSC, phone and account numbers', async ({ browser }) => {
  const applicant = { email: uniqueEmail('e2e-kyc-check'), firstName: 'Vik', lastName: 'Validation' };
  await (await KeycloakAdmin.connect()).ensureUser(applicant, ['ROLE_RENTER']);
  const onboarding = new OwnerOnboardingPage(await signedInPage(browser, applicant.email));
  await onboarding.open();
  const page = onboarding.page;

  const cases: [label: string, value: string, field: string, message: string][] = [
    ['Mobile number', '12345', 'phone', 'Enter a 10-digit mobile number starting with 6, 7, 8 or 9, e.g. 9876543210.'],
    ['Aadhaar number', '1234 56', 'aadhaarNumber', 'An Aadhaar number is 12 digits; you entered 6.'],
    ['Aadhaar number', '1234 5678 90AB', 'aadhaarNumber', 'Use digits only: an Aadhaar number is 12 digits.'],
    ['Bank account number', '12345', 'bankAccountNumber', 'Enter exactly 10 digits, e.g. 1234567890.'],
    ['IFSC code', 'HDFC123', 'bankIfsc', 'An IFSC is 11 characters; you entered 7. For SBI it looks like SBIN0001234.'],
    ['IFSC code', 'HDFC1001234', 'bankIfsc', 'The 5th character must be a zero (0), e.g. SBIN0001234.']
  ];
  for (const [label, value, field, message] of cases) {
    await page.getByLabel(label).fill(value);
    await page.getByLabel(label).blur();
    await expect(onboarding.hint(field), `${value} in ${label}`).toHaveText(message);
  }

  await onboarding.pan.fill('ABC123');
  await onboarding.pan.blur();
  await expect(onboarding.hint('pan')).toHaveText('A PAN looks like ABCDE1234F: 5 letters, 4 digits, 1 letter.');

  await page.getByLabel('Bank account number').fill('1234567890');
  await page.getByLabel('Confirm account number').fill('1234567899');
  await page.getByLabel('Confirm account number').blur();
  await expect(onboarding.hint('confirmBankAccountNumber')).toHaveText('The account numbers do not match.');

  await onboarding.submit.click();
  await expect(page.getByText('Under review')).toHaveCount(0);
});
