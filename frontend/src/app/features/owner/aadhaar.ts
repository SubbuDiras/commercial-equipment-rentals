import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Any 12 digits; the backend applies the same rule, and the reviewer checks the number against the uploaded card. */
export function isValidAadhaar(number: string): boolean {
  return /^[0-9]{12}$/.test(number);
}

/** Spaces are allowed as typed on the card (5674 8765 1234); an empty value is left to Validators.required. */
export function aadhaarValidator(control: AbstractControl<string>): ValidationErrors | null {
  const digits = (control.value ?? '').replace(/\s/g, '');
  return digits === '' || isValidAadhaar(digits) ? null : { aadhaar: true };
}
