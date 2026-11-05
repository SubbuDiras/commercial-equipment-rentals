/** A valid 1×1 PNG, used for inspection and listing photos. */
export const tinyPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64'
);

/** A minimal valid PDF, used for KYC documents. */
export const tinyPdf = Buffer.from(
  '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
  + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n'
);

export function photos(count: number, prefix = 'photo'): { name: string; mimeType: string; buffer: Buffer }[] {
  return Array.from({ length: count }, (_, index) => ({ name: `${prefix}-${index + 1}.png`, mimeType: 'image/png', buffer: tinyPng }));
}
