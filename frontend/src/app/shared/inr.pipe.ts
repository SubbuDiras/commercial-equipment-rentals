import { Pipe, PipeTransform } from '@angular/core';

const FORMAT = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

/**
 * Formats an amount the backend already calculated, e.g. `1234.5` → `₹1,234.50`. Display only: the UI never
 * computes prices, refunds or fees itself.
 */
@Pipe({ name: 'inr', standalone: true })
export class InrPipe implements PipeTransform {
  transform(value: number | string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    const amount = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(amount) ? FORMAT.format(amount) : '—';
  }
}
