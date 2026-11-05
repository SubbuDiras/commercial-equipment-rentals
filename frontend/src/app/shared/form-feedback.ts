import { FormGroup } from '@angular/forms';

/**
 * For a submit that found the form invalid: marks every control touched (so the global style outlines the bad ones)
 * and returns a message naming them, e.g. "Please check the highlighted fields: Title, Daily rate (₹)."
 * Returns null when the form is valid. Forms keep their submit button enabled and show this message instead.
 */
export function invalidFieldsMessage(form: FormGroup, labels: Record<string, string>): string | null {
  if (form.valid) {
    return null;
  }
  form.markAllAsTouched();
  const names = Object.entries(form.controls)
    .filter(([, control]) => control.invalid)
    .map(([name]) => labels[name] ?? name);
  return `Please check the highlighted ${names.length === 1 ? 'field' : 'fields'}: ${names.join(', ')}.`;
}
