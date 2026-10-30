import { AbstractControl, ValidationErrors } from '@angular/forms';

const MULTIPLY = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 2, 3, 4, 0, 6, 7, 8, 9, 5], [2, 3, 4, 0, 1, 7, 8, 9, 5, 6],
  [3, 4, 0, 1, 2, 8, 9, 5, 6, 7], [4, 0, 1, 2, 3, 9, 5, 6, 7, 8], [5, 9, 8, 7, 6, 0, 4, 3, 2, 1],
  [6, 5, 9, 8, 7, 1, 0, 4, 3, 2], [7, 6, 5, 9, 8, 2, 1, 0, 4, 3], [8, 7, 6, 5, 9, 3, 2, 1, 0, 4],
  [9, 8, 7, 6, 5, 4, 3, 2, 1, 0]
];
const PERMUTE = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 5, 7, 6, 2, 8, 3, 0, 9, 4], [5, 8, 0, 3, 7, 9, 6, 1, 4, 2],
  [8, 9, 1, 6, 0, 4, 3, 5, 2, 7], [9, 4, 5, 3, 1, 2, 6, 8, 7, 0], [4, 2, 8, 6, 5, 7, 3, 9, 0, 1],
  [2, 7, 9, 3, 8, 0, 6, 4, 1, 5], [7, 0, 4, 6, 9, 1, 3, 2, 5, 8]
];

/** 12 digits, not starting with 0 or 1, passing the Verhoeff checksum; the backend applies the same rule. */
export function isValidAadhaar(number: string): boolean {
  if (!/^[2-9][0-9]{11}$/.test(number)) {
    return false;
  }
  let check = 0;
  [...number].reverse().forEach((digit, i) => check = MULTIPLY[check][PERMUTE[i % 8][Number(digit)]]);
  return check === 0;
}

/** Spaces are allowed as typed on the card (2341 2341 2346); an empty value is left to Validators.required. */
export function aadhaarValidator(control: AbstractControl<string>): ValidationErrors | null {
  const digits = (control.value ?? '').replace(/\s/g, '');
  return digits === '' || isValidAadhaar(digits) ? null : { aadhaar: true };
}
