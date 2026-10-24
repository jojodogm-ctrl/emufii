import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { exec } from 'node:child_process';
import { promisify } from 'node:util';
import type { WgTunnelInfo } from './coordinator.js';

const execAsync = promisify(exec);

export interface WgKeys {
  privateKey: string;
  publicKey: string;
}

export class WireGuardManager {
  static readonly KEEPALIVE_SECONDS = 10;
  static readonly MTU = 1420;
  static readonly RELAY_ADDRESS = '10.67.0.1';
  static readonly PS2_HOST_NAME = 'emufii';

  private activeConfigPath: string | null = null;
  private isTunnelUp = false;

  /**
   * Generates or loads persistent WireGuard keypair using Curve25519 (X25519).
   */
  static generateKeyPair(): WgKeys {
    const { privateKey, publicKey } = crypto.generateKeyPairSync('x25519', {
      privateKeyEncoding: { format: 'der', type: 'pkcs8' },
      publicKeyEncoding: { format: 'der', type: 'spki' },
    });

    // In DER format, raw 32-byte key is at the end:
    // PKCS#8 private key for X25519: last 32 bytes are the raw private key (or last 32 after header)
    // SPKI public key for X25519: last 32 bytes are the raw public key
    const rawPriv = privateKey.subarray(privateKey.length - 32);
    const rawPub = publicKey.subarray(publicKey.length - 32);

    return {
      privateKey: rawPriv.toString('base64'),
      publicKey: rawPub.toString('base64'),
    };
  }

  static renderConfig(info: WgTunnelInfo, privateKeyBase64: string, dns?: string | null): string {
    const addresses = [info.address, info.hairpinAddress].filter(Boolean);
    const prefix = info.subnet.includes('/') ? info.subnet.split('/')[1] : '24';
    const addrLine = addresses.map((a) => `${a}/${prefix}`).join(', ');

    const lines: string[] = [
      '[Interface]',
      `PrivateKey = ${privateKeyBase64}`,
      `Address = ${addrLine}`,
      `MTU = ${WireGuardManager.MTU}`,
    ];

    if (dns) {
      lines.push(`DNS = ${dns}`);
    }

    lines.push(
      '',
      '[Peer]',
      `PublicKey = ${info.relayPublicKey}`,
      `Endpoint = ${info.relayEndpoint}`,
      `AllowedIPs = ${info.relayAllowedIps}`,
      `PersistentKeepalive = ${WireGuardManager.KEEPALIVE_SECONDS}`
    );

    return lines.join('\n');
  }

  /**
   * Starts or writes the WireGuard tunnel configuration.
   */
  async up(info: WgTunnelInfo, privateKeyBase64: string, appDataDir: string, dns?: string | null): Promise<{
    configPath: string;
    configContent: string;
    isNativeActive: boolean;
  }> {
    const configContent = WireGuardManager.renderConfig(info, privateKeyBase64, dns);
    const tunnelDir = path.join(appDataDir, 'tunnels');
    fs.mkdirSync(tunnelDir, { recursive: true });
    const configPath = path.join(tunnelDir, 'emufii0.conf');
    fs.writeFileSync(configPath, configContent, 'utf8');
    this.activeConfigPath = configPath;

    let isNativeActive = false;
    const platform = os.platform();

    if (platform === 'linux') {
      // 1. Try NetworkManager (nmcli) - native, rootless on modern Linux desktops (Arch, Fedora, Ubuntu, Debian)
      try {
        const connName = 'emufii0';
        await execAsync(`nmcli connection delete "${connName}"`, { timeout: 2000 }).catch(() => {});
        await execAsync(`nmcli connection import --temporary type wireguard file "${configPath}"`, { timeout: 4000 });
        await execAsync(`nmcli connection up "${connName}"`, { timeout: 4000 });
        isNativeActive = true;
        this.isTunnelUp = true;
      } catch {
        // 2. Fallback to wg-quick if nmcli didn't succeed
        try {
          await execAsync(`wg-quick up "${configPath}"`, { timeout: 4000 });
          isNativeActive = true;
          this.isTunnelUp = true;
        } catch {
          isNativeActive = false;
        }
      }
    } else if (platform === 'darwin') {
      try {
        await execAsync(`wg-quick up "${configPath}"`, { timeout: 5000 });
        isNativeActive = true;
        this.isTunnelUp = true;
      } catch {
        isNativeActive = false;
      }
    } else if (platform === 'win32') {
      try {
        await execAsync(`wireguard /installtunnelservice "${configPath}"`, { timeout: 5000 });
        isNativeActive = true;
        this.isTunnelUp = true;
      } catch {
        isNativeActive = false;
      }
    }

    return { configPath, configContent, isNativeActive };
  }

  async down(): Promise<void> {
    if (!this.activeConfigPath) return;
    const configPath = this.activeConfigPath;
    this.activeConfigPath = null;
    this.isTunnelUp = false;

    const platform = os.platform();
    try {
      if (platform === 'linux') {
        await execAsync(`nmcli connection down emufii0`, { timeout: 2000 }).catch(() => {});
        await execAsync(`nmcli connection delete emufii0`, { timeout: 2000 }).catch(() => {});
        await execAsync(`wg-quick down "${configPath}"`, { timeout: 2000 }).catch(() => {});
      } else if (platform === 'darwin') {
        await execAsync(`wg-quick down "${configPath}"`, { timeout: 4000 });
      } else if (platform === 'win32') {
        await execAsync(`wireguard /uninstalltunnelservice emufii0`, { timeout: 4000 });
      }
    } catch {
      // Ignore teardown errors
    }
  }

  getStatus(): { isTunnelUp: boolean; activeConfigPath: string | null } {
    return {
      isTunnelUp: this.isTunnelUp,
      activeConfigPath: this.activeConfigPath,
    };
  }
}
