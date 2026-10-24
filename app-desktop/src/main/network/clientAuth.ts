import crypto from 'node:crypto';

function defaultClientToken(): string {
  const a = [
    0xfa, 0xd6, 0x80, 0x9e, 0xf7, 0x73, 0x81, 0x48,
    0x3a, 0xdc, 0x51, 0x7d, 0xe0, 0xd5, 0x96, 0x65,
    0x4a, 0xea, 0x44, 0xb3, 0xc7, 0x49, 0xfa, 0xe7,
    0x8d, 0x87, 0xcb, 0x39, 0xe4, 0x6c, 0xb8, 0x45,
  ];
  return Buffer.from(a.map((v) => (v ^ 0xa2) & 0xff)).toString('hex');
}

/**
 * Client authentication matching ClientAuth.kt from app-android:
 * HMAC-SHA256(secret, method\npath\ntimestamp\nSHA-256(body)), lowercase hex.
 */
export class ClientAuth {
  static readonly HEADER_AUTH = 'X-Emufii-Auth';
  static readonly HEADER_TIMESTAMP = 'X-Emufii-Ts';
  static readonly HEADER_CLIENT = 'X-Emufii-Client';

  private static secret: string = process.env.EMUFII_CLIENT_SECRET || defaultClientToken();
  static clientVersion: string = '55'; // VersionCode 55 (matches Emufii v2.0.4)

  private static clockOffsetMs: number = 0;
  private static readonly CLOCK_TOLERANCE_MS = 5000;

  static setSecret(secret: string) {
    this.secret = secret;
  }

  static isConfigured(): boolean {
    return this.secret.length > 0;
  }

  static nowSeconds(): number {
    return Math.floor((Date.now() + this.clockOffsetMs) / 1000);
  }

  static learnServerClock(serverDateMs: number, sentAtMs: number, receivedAtMs: number): boolean {
    if (serverDateMs <= 0) return false;
    const offset = serverDateMs - Math.floor((sentAtMs + receivedAtMs) / 2);
    if (Math.abs(offset - this.clockOffsetMs) < this.CLOCK_TOLERANCE_MS) return false;
    this.clockOffsetMs = offset;
    return true;
  }

  static sign(method: string, path: string, body?: string | null, timestampSeconds = this.nowSeconds()) {
    if (!this.isConfigured()) return null;

    const bodyHash = crypto.createHash('sha256').update(body || '', 'utf8').digest('hex');
    const payload = `${method.toUpperCase()}\n${path}\n${timestampSeconds}\n${bodyHash}`;
    const signature = crypto.createHmac('sha256', this.secret).update(payload, 'utf8').digest('hex');

    return {
      value: signature,
      timestamp: timestampSeconds.toString(),
    };
  }
}
