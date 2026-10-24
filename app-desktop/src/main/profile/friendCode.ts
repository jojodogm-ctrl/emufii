/**
 * Eleven Crockford base32 symbols plus a check symbol.
 * Exactly mirrors FriendCode.kt from app-android.
 * Safe for both Node.js and Browser environments.
 */
export const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
export const RANDOM_SYMBOLS = 11;
export const LENGTH = RANDOM_SYMBOLS + 1;
const GROUP = 4;

export function generateFriendCode(): string {
  let body = '';
  const bytes = new Uint8Array(RANDOM_SYMBOLS);
  if (typeof globalThis.crypto?.getRandomValues === 'function') {
    globalThis.crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < RANDOM_SYMBOLS; i++) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }

  for (let i = 0; i < RANDOM_SYMBOLS; i++) {
    body += ALPHABET[bytes[i] % ALPHABET.length];
  }
  return body + checksum(body);
}

export function formatFriendCode(code: string): string {
  const clean = code.replace(/[^0-9A-Za-z]/g, '').toUpperCase().slice(0, LENGTH);
  const parts: string[] = [];
  for (let i = 0; i < clean.length; i += GROUP) {
    parts.push(clean.substring(i, i + GROUP));
  }
  return parts.join('-');
}

/**
 * Formats live user input for friend code fields:
 * - Always uppercase
 * - Automatically appends '-' immediately after the 4th and 8th characters
 */
export function formatFriendCodeLiveInput(rawInput: string, isDeleting = false): string {
  const clean = rawInput.replace(/[^0-9A-Za-z]/g, '').toUpperCase().slice(0, LENGTH);
  if (clean.length === 0) return '';
  if (clean.length < 4) return clean;
  if (clean.length === 4) return isDeleting ? clean : `${clean}-`;
  if (clean.length < 8) return `${clean.slice(0, 4)}-${clean.slice(4)}`;
  if (clean.length === 8) return isDeleting ? `${clean.slice(0, 4)}-${clean.slice(4, 8)}` : `${clean.slice(0, 4)}-${clean.slice(4, 8)}-`;
  return `${clean.slice(0, 4)}-${clean.slice(4, 8)}-${clean.slice(8, 12)}`;
}

/**
 * Attaches live uppercase + auto-hyphenation after 4th/8th char to an <input> element.
 */
export function attachFriendCodeInputFormatter(input: HTMLInputElement | null): void {
  if (!input) return;

  input.addEventListener('keydown', (e: KeyboardEvent) => {
    if (e.key === 'Backspace') {
      const val = input.value;
      const selStart = input.selectionStart ?? val.length;
      const selEnd = input.selectionEnd ?? val.length;
      if (selStart === selEnd && selStart === val.length && val.endsWith('-')) {
        e.preventDefault();
        const stripped = val.slice(0, -2);
        input.value = formatFriendCodeLiveInput(stripped, true);
      }
    }
  });

  input.addEventListener('input', (e: Event) => {
    const ie = e as InputEvent;
    const isDeleting = ie?.inputType ? ie.inputType.startsWith('delete') : false;
    const formatted = formatFriendCodeLiveInput(input.value, isDeleting);
    if (input.value !== formatted) {
      input.value = formatted;
      try {
        input.setSelectionRange(formatted.length, formatted.length);
      } catch {}
    }
  });

  input.addEventListener('paste', (e: ClipboardEvent) => {
    e.preventDefault();
    const pasted = e.clipboardData?.getData('text') || '';
    const formatted = formatFriendCodeLiveInput(pasted, false);
    input.value = formatted;
    try {
      input.setSelectionRange(formatted.length, formatted.length);
    } catch {}
  });
}

export function normalizeFriendCode(input: string): string | null {
  let cleaned = '';
  for (const raw of input) {
    if (raw === '-' || raw === ' ' || raw === '\t' || raw === '\n' || raw === '\r') {
      continue;
    }
    const upper = raw.toUpperCase();
    if (upper === 'O') {
      cleaned += '0';
    } else if (upper === 'I' || upper === 'L') {
      cleaned += '1';
    } else {
      cleaned += upper;
    }
  }

  if (cleaned.length !== LENGTH) return null;
  for (const char of cleaned) {
    if (!ALPHABET.includes(char)) return null;
  }
  const body = cleaned.substring(0, RANDOM_SYMBOLS);
  if (cleaned[LENGTH - 1] !== checksum(body)) return null;
  return cleaned;
}

export function isValidFriendCode(input: string): boolean {
  return normalizeFriendCode(input) !== null;
}

/**
 * Derives a deterministic 12-character Crockford inbox ID for receiving friend requests
 * via the coordinator without colliding with the user's primary presence record.
 */
export function deriveFriendInboxId(friendCode: string): string {
  const clean = normalizeFriendCode(friendCode) || friendCode.replace(/[^0-9A-Z]/gi, '').toUpperCase();
  let h1 = 0x811c9dc5;
  let h2 = 0x01000193;
  const seed = `EMUFII_FREQ_INBOX_${clean}`;
  for (let i = 0; i < seed.length; i++) {
    const c = seed.charCodeAt(i);
    h1 ^= c;
    h1 = Math.imul(h1, 0x01000193) >>> 0;
    h2 ^= (c + i * 17);
    h2 = Math.imul(h2, 0x85ebca6b) >>> 0;
  }
  let body = 'FRQ';
  let state = h1 ^ h2;
  for (let i = 0; i < RANDOM_SYMBOLS - 3; i++) {
    state = (Math.imul(state ^ (i + 1), 0x27d4eb2d) + ((h1 >>> (i % 16)) ^ (h2 << (i % 8)))) >>> 0;
    body += ALPHABET[state % ALPHABET.length];
  }
  return body + checksum(body);
}

function checksum(body: string): string {
  let acc = 0;
  for (let index = 0; index < body.length; index++) {
    acc += ALPHABET.indexOf(body[index]) * (index + 1);
  }
  return ALPHABET[acc % ALPHABET.length];
}
