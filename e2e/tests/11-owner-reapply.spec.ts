import { expect, test } from '@playwright/test';
import { OwnerOnboardingPage } from '../pages/owner-onboarding.page';
import { Api } from '../support/api';
import { uniqueEmail, users } from '../support/env';
import { tinyPng } from '../support/files';
import { register } from '../support/ui';

test('a rejected applicant corrects their details and documents, and applies again', async ({ browser }) => {
  const applicant = { email: uniqueEmail('reapply'), firstName: 'Rekha', lastName: 'Reapply' };
  const page = await register(browser, applicant);

  // 1. Apply, with a PAN the reviewer will query.
  const onboarding = new OwnerOnboardingPage(page);
  await onboarding.open();
  await onboarding.fillDetails({
    firstName: applicant.firstName, lastName: applicant.lastName, phone: `97${Date.now().toString().slice(-8)}`,
    pan: 'AAAAA1111A', aadhaar: '5674 8765 1234', accountHolder: 'Rekha Reapply', bankName: 'State Bank of India',
    accountNumber: '1234567890', ifsc: 'SBIN0001234'
  });
  await onboarding.chooseDocuments({ aadhaar: 'aadhaar.pdf', pan: 'blurry-pan.png', signature: 'signature.png', bank: 'cheque.pdf' });
  await onboarding.submit.click();
  await onboarding.expectUnderReview();

  // 2. The admin rejects it.
  const admin = Api.as(users.admin.email);
  const queue: { ownerProfileId: string; email: string }[] = await admin.get('/api/v1/admin/owners?status=UNDER_REVIEW');
  const application = queue.find(owner => owner.email === applicant.email)!;
  await admin.patch(`/api/v1/admin/owners/${application.ownerProfileId}/reject`, { reason: 'The PAN card photo is blurry.' });

  // 3. The applicant sees why, and reapplies from a form filled with their earlier answers.
  await page.reload();
  await expect(page.getByText('The PAN card photo is blurry.')).toBeVisible();
  await page.getByRole('button', { name: 'Update details and reapply' }).click();
  await expect(page.getByRole('textbox', { name: 'PAN', exact: true })).toHaveValue('AAAAA1111A');
  await expect(page.getByLabel('IFSC code')).toHaveValue('SBIN0001234');
  await expect(page.getByLabel('First name')).toHaveValue(applicant.firstName);
  await expect(page.getByLabel('Aadhaar number')).toHaveValue(''); // typed again, for security
  await expect(page.getByRole('group', { name: 'Signature' })).toContainText('Keeping signature.png, uploaded earlier');

  await page.getByRole('textbox', { name: 'PAN', exact: true }).fill('BBBBB2222B');
  await page.getByLabel('Aadhaar number').fill('5674 8765 1234');
  await page.getByLabel('Bank account number').fill('1234567890');
  await page.getByLabel('Confirm account number').fill('1234567890');
  await page.getByLabel('Choose PAN card file').setInputFiles({ name: 'clear-pan.png', mimeType: 'image/png', buffer: tinyPng });
  await expect(page.getByRole('group', { name: 'PAN card' })).toContainText('clear-pan.png (replaces blurry-pan.png)');
  await page.getByRole('button', { name: 'Reapply for verification' }).click();

  // 4. Back under review, with the corrected PAN and the new PAN card next to the earlier documents.
  await expect(page.getByText('Under review').first()).toBeVisible();
  await expect(page.getByRole('definition').filter({ hasText: 'BBBBB2222B' })).toBeVisible();
  await expect(page.getByRole('group', { name: 'PAN card' })).toContainText('clear-pan.png');
  const again: { ownerProfileId: string; email: string; kyc: { pan: string } }[] =
    await admin.get('/api/v1/admin/owners?status=UNDER_REVIEW');
  expect(again.find(owner => owner.email === applicant.email)).toMatchObject({
    ownerProfileId: application.ownerProfileId,
    kyc: { pan: 'BBBBB2222B' }
  });
});
