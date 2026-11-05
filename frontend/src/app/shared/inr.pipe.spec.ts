import { InrPipe } from './inr.pipe';

describe('InrPipe', () => {
  const pipe = new InrPipe();

  it('formats backend amounts as rupees with Indian digit grouping', () => {
    expect(pipe.transform(1234567.5)).toBe('₹12,34,567.50');
    expect(pipe.transform('98.4')).toBe('₹98.40');
    expect(pipe.transform(0)).toBe('₹0.00');
  });

  it('shows a dash for missing or invalid amounts', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(undefined)).toBe('—');
    expect(pipe.transform('abc')).toBe('—');
  });
});
