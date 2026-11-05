import { expect, Locator, Page } from '@playwright/test';
import { tinyPdf, tinyPng } from '../support/files';

export interface KycDetails {
  firstName: string;
  lastName: string;
  phone: string;
  pan: string;
  aadhaar: string;
  accountHolder: string;
  bankName: string;
  accountNumber: string;
  ifsc: string;
}

/** /owner/onboarding — the owner application (KYC details and documents). */
export class OwnerOnboardingPage {
  readonly pan: Locator;
  readonly submit: Locator;

  constructor(readonly page: Page) {
    this.pan = page.getByRole('textbox', { name: 'PAN', exact: true });
    this.submit = page.getByRole('button', { name: 'Submit for verification' });
  }

  async open(): Promise<void> {
    await this.page.goto('/owner/onboarding');
  }

  /** The help line under a field, which turns into the error once the field is left with a bad value. */
  hint(fieldName: string): Locator {
    return this.page.locator(`#${fieldName}-hint`);
  }

  async fillDetails(kyc: KycDetails): Promise<void> {
    await this.page.getByLabel('First name').fill(kyc.firstName);
    await this.page.getByLabel('Last name').fill(kyc.lastName);
    await this.page.getByLabel('Mobile number').fill(kyc.phone);
    await this.pan.fill(kyc.pan);
    await this.page.getByLabel('Aadhaar number').fill(kyc.aadhaar);
    await this.page.getByLabel('Account holder name').fill(kyc.accountHolder);
    await this.page.getByLabel('Bank name').fill(kyc.bankName);
    await this.page.getByLabel('Bank account number').fill(kyc.accountNumber);
    await this.page.getByLabel('Confirm account number').fill(kyc.accountNumber);
    await this.page.getByLabel('IFSC code').fill(kyc.ifsc);
  }

  /** Chooses all four required documents. */
  async chooseDocuments(names = { aadhaar: 'aadhaar.pdf', pan: 'pan.png', signature: 'signature.png', bank: 'cheque.pdf' }): Promise<void> {
    const pdf = (name: string) => ({ name, mimeType: 'application/pdf', buffer: tinyPdf });
    const png = (name: string) => ({ name, mimeType: 'image/png', buffer: tinyPng });
    const file = (name: string) => (name.endsWith('.pdf') ? pdf(name) : png(name));
    await this.page.getByLabel('Choose Aadhaar card file').setInputFiles(file(names.aadhaar));
    await this.page.getByLabel('Choose PAN card file').setInputFiles(file(names.pan));
    await this.page.getByLabel('Choose Signature file').setInputFiles(file(names.signature));
    await this.page.getByLabel('Choose Bank proof file').setInputFiles(file(names.bank));
  }

  async expectUnderReview(): Promise<void> {
    await expect(this.page.getByText('Under review').first()).toBeVisible();
  }
}
