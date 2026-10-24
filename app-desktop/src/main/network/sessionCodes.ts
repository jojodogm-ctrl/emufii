/**
 * Session code generator and normalizer matching SessionCodes.kt from app-android:
 * Format: "XXX-123" (three uppercase letters, hyphen, three digits, e.g. "HMM-295").
 */
export const SESSION_CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
export const SESSION_CODE_DIGITS = '23456789';
export const SESSION_CODE_LETTERS = 3;
export const SESSION_CODE_NUMBERS = 3;

function getRandomInt(max: number): number {
  if (typeof globalThis.crypto?.getRandomValues === 'function') {
    const arr = new Uint32Array(1);
    globalThis.crypto.getRandomValues(arr);
    return arr[0] % max;
  }
  return Math.floor(Math.random() * max);
}

function pick(from: string, n: number): string {
  let res = '';
  for (let i = 0; i < n; i++) {
    res += from[getRandomInt(from.length)];
  }
  return res;
}

export function generateSessionCode(): string {
  return `${pick(SESSION_CODE_ALPHABET, SESSION_CODE_LETTERS)}-${pick(SESSION_CODE_DIGITS, SESSION_CODE_NUMBERS)}`;
}

export function normalizeSessionCode(typed: string): string {
  if (!typed) return '';
  const body = typed.toUpperCase().replace(/[^A-Z0-9]/g, '');
  let letters = 0;
  while (letters < body.length && /[A-Z]/.test(body[letters])) {
    letters++;
  }
  const digits = body.length - letters;
  const known =
    (letters === SESSION_CODE_LETTERS && digits === SESSION_CODE_NUMBERS) ||
    (letters === 4 && digits === 4);
  if (!known) {
    return typed.toUpperCase().trim();
  }
  return `${body.slice(0, letters)}-${body.slice(letters)}`;
}
