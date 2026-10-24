import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { app } from 'electron';
import { generateFriendCode, isValidFriendCode, normalizeFriendCode } from '../profile/friendCode.js';
import { WireGuardManager, WgKeys } from '../network/wireguard.js';

export interface FriendRequestItem {
  code: string;
  name: string;
  avatarDataUrl?: string | null;
  at: number;
}

export interface SessionInviteEntry {
  from: string;
  name: string;
  code: string;
  romTitle: string | null;
  romTitleId: string | null;
  at: number;
}

export interface AppConfig {
  profile: {
    id: string;
    name: string;
    avatarDataUrl?: string | null;
    ownerKey?: string;
  };
  wgKeys: WgKeys;
  settings: {
    romDirectories: string[];
    romFiles?: string[];
    theme: 'system' | 'light' | 'dark' | 'oled' | 'day' | 'night';
    accent: 'teal' | 'coral' | 'cyan' | 'purple' | 'green' | 'yellow' | 'red' | 'blue';
    animations: boolean;
    soundEffects: boolean;
    libraryLayout: 'grid' | 'carousel' | 'list';
    librarySort: 'az' | 'date' | 'console';
    coordinatorUrl: string;
    customEmulatorPaths: Record<string, string>;
    hiddenRoms: string[];
    customTitles: Record<string, string>;
    friends: string[];
    friendNicknames: Record<string, string>;
    friendNames?: Record<string, string>;
    friendAvatars?: Record<string, string>;
    friendAvatarHashes?: Record<string, string>;
    pendingFriendRequests?: FriendRequestItem[];
    pendingSessionInvites?: SessionInviteEntry[];
    steamGridDbKey?: string;
    language: 'system' | 'en' | 'es' | 'fr';
    hiddenConsoles: string[];
    hideIncompatible: boolean;
    notifyFriends: boolean;
    shareLastGame: boolean;
    autoDownloadUpdates: boolean;
  };
}

export function getSpecialRomDirectory(): string {
  const home = app.getPath('home');
  return path.join(home, 'Emufii', 'ROMs');
}

export function ensureSpecialRomDirectory(): string {
  const romDir = getSpecialRomDirectory();
  try {
    if (!fs.existsSync(romDir)) {
      fs.mkdirSync(romDir, { recursive: true });
    }
    const subfolders = ['Wii', 'GameCube', 'PS2', 'PSP', 'DS', 'GBA', '3DS', 'Switch'];
    for (const sub of subfolders) {
      const subPath = path.join(romDir, sub);
      if (!fs.existsSync(subPath)) {
        fs.mkdirSync(subPath, { recursive: true });
      }
    }
  } catch (err) {
    console.error('Failed to create special ROM directory:', err);
  }
  return romDir;
}

export class Storage {
  private configPath: string;
  private config: AppConfig;

  constructor() {
    const userData = app.getPath('userData');
    this.configPath = path.join(userData, 'config.json');
    this.config = this.load();
  }

  private load(): AppConfig {
    let loaded: Partial<AppConfig> = {};
    try {
      if (fs.existsSync(this.configPath)) {
        loaded = JSON.parse(fs.readFileSync(this.configPath, 'utf8'));
      }
    } catch {
      loaded = {};
    }

    // Default Profile
    let id = loaded.profile?.id;
    if (!id || !isValidFriendCode(id)) {
      id = generateFriendCode();
    }
    const ownerKey =
      loaded.profile?.ownerKey && /^[0-9a-f]{64}$/i.test(loaded.profile.ownerKey)
        ? loaded.profile.ownerKey
        : crypto.randomBytes(32).toString('hex');

    const profile = {
      id,
      name: loaded.profile?.name || 'Joueur',
      avatarDataUrl: loaded.profile?.avatarDataUrl || null,
      ownerKey,
    };

    // Default WireGuard keys
    let wgKeys = loaded.wgKeys;
    if (!wgKeys || !wgKeys.privateKey || !wgKeys.publicKey) {
      wgKeys = WireGuardManager.generateKeyPair();
    }

    // Ensure Special ROM Directory exists and is registered in settings
    const specialRomDir = ensureSpecialRomDirectory();
    let romDirs: string[] = loaded.settings?.romDirectories || [];
    if (!romDirs.includes(specialRomDir)) {
      romDirs = [specialRomDir, ...romDirs];
    }

    // Filter out self from friends list if ever present
    const rawFriends = loaded.settings?.friends || [];
    const cleanFriends = rawFriends
      .map((c) => normalizeFriendCode(c) || c)
      .filter((c, idx, arr) => c && c !== id && arr.indexOf(c) === idx);

    // Default Settings
    const settings: AppConfig['settings'] = {
      romDirectories: romDirs,
      romFiles: loaded.settings?.romFiles || [],
      theme: (loaded.settings?.theme as any) || 'dark',
      accent: (loaded.settings?.accent as any) || 'teal',
      animations: loaded.settings?.animations !== false,
      soundEffects: loaded.settings?.soundEffects !== false,
      libraryLayout: loaded.settings?.libraryLayout || 'grid',
      librarySort: loaded.settings?.librarySort || 'console',
      coordinatorUrl: loaded.settings?.coordinatorUrl || 'https://coord.emufii.xyz',
      customEmulatorPaths: loaded.settings?.customEmulatorPaths || {},
      hiddenRoms: loaded.settings?.hiddenRoms || [],
      customTitles: loaded.settings?.customTitles || {},
      friends: cleanFriends,
      friendNicknames: loaded.settings?.friendNicknames || {},
      friendNames: loaded.settings?.friendNames || {},
      friendAvatars: loaded.settings?.friendAvatars || {},
      friendAvatarHashes: loaded.settings?.friendAvatarHashes || {},
      pendingFriendRequests: loaded.settings?.pendingFriendRequests || [],
      pendingSessionInvites: loaded.settings?.pendingSessionInvites || [],
      steamGridDbKey: loaded.settings?.steamGridDbKey || '',
      language: (loaded.settings?.language as any) || 'system',
      hiddenConsoles: loaded.settings?.hiddenConsoles || [],
      hideIncompatible: !!loaded.settings?.hideIncompatible,
      notifyFriends: loaded.settings?.notifyFriends !== false,
      shareLastGame: loaded.settings?.shareLastGame !== false,
      autoDownloadUpdates: loaded.settings?.autoDownloadUpdates !== false,
    };

    const config: AppConfig = { profile, wgKeys, settings };
    this.save(config);
    return config;
  }

  save(config = this.config) {
    this.config = config;
    try {
      const dir = path.dirname(this.configPath);
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(this.configPath, JSON.stringify(this.config, null, 2), 'utf8');
    } catch (err) {
      console.error('Failed to save config:', err);
    }
  }

  getConfig(): AppConfig {
    return this.config;
  }

  updateProfile(partial: Partial<AppConfig['profile']>) {
    this.config.profile = { ...this.config.profile, ...partial };
    this.save();
    return this.config.profile;
  }

  updateSettings(partial: Partial<AppConfig['settings']>) {
    this.config.settings = { ...this.config.settings, ...partial };
    this.save();
    return this.config.settings;
  }

  addFriend(code: string, nickname?: string): { ok: boolean; error?: 'invalid' | 'self' | 'duplicate'; code?: string } {
    const normalized = normalizeFriendCode(code);
    if (!normalized) {
      return { ok: false, error: 'invalid' };
    }
    const myCode = normalizeFriendCode(this.config.profile.id) || this.config.profile.id;
    if (normalized === myCode) {
      return { ok: false, error: 'self' };
    }

    const alreadyExisted = this.config.settings.friends.includes(normalized);
    if (!alreadyExisted) {
      this.config.settings.friends.push(normalized);
    }

    if (nickname !== undefined) {
      const trimmed = nickname.trim();
      if (trimmed) {
        this.config.settings.friendNicknames[normalized] = trimmed;
      } else {
        delete this.config.settings.friendNicknames[normalized];
      }
    }

    // Remove from pending requests if present
    if (this.config.settings.pendingFriendRequests) {
      this.config.settings.pendingFriendRequests = this.config.settings.pendingFriendRequests.filter(
        (r) => r.code !== normalized
      );
    }

    this.save();
    return { ok: true, code: normalized };
  }

  removeFriend(code: string) {
    const normalized = normalizeFriendCode(code) || code;
    this.config.settings.friends = this.config.settings.friends.filter((c) => c !== normalized && c !== code);
    delete this.config.settings.friendNicknames[normalized];
    delete this.config.settings.friendNicknames[code];
    if (this.config.settings.friendNames) {
      delete this.config.settings.friendNames[normalized];
    }
    if (this.config.settings.friendAvatars) {
      delete this.config.settings.friendAvatars[normalized];
    }
    if (this.config.settings.friendAvatarHashes) {
      delete this.config.settings.friendAvatarHashes[normalized];
    }
    this.save();
  }

  noteFriendMetadata(
    names: Record<string, string>,
    avatars?: Record<string, string>,
    hashes?: Record<string, string>
  ) {
    let changed = false;
    if (!this.config.settings.friendNames) this.config.settings.friendNames = {};
    if (!this.config.settings.friendAvatars) this.config.settings.friendAvatars = {};
    if (!this.config.settings.friendAvatarHashes) this.config.settings.friendAvatarHashes = {};

    for (const [code, name] of Object.entries(names || {})) {
      if (name && name.trim() && this.config.settings.friendNames[code] !== name.trim()) {
        this.config.settings.friendNames[code] = name.trim();
        changed = true;
      }
    }
    for (const [code, dataUrl] of Object.entries(avatars || {})) {
      if (dataUrl && this.config.settings.friendAvatars[code] !== dataUrl) {
        this.config.settings.friendAvatars[code] = dataUrl;
        changed = true;
      }
    }
    for (const [code, hash] of Object.entries(hashes || {})) {
      if (hash && this.config.settings.friendAvatarHashes[code] !== hash) {
        this.config.settings.friendAvatarHashes[code] = hash;
        changed = true;
      }
    }

    if (changed) {
      this.save();
    }
    return this.config.settings;
  }

  renameRom(romId: string, customTitle: string) {
    if (customTitle.trim()) {
      this.config.settings.customTitles[romId] = customTitle.trim();
    } else {
      delete this.config.settings.customTitles[romId];
    }
    this.save();
  }

  toggleHideRom(romId: string) {
    const list = this.config.settings.hiddenRoms;
    const index = list.indexOf(romId);
    if (index >= 0) {
      list.splice(index, 1);
    } else {
      list.push(romId);
    }
    this.save();
  }

  addRomDirectory(dir: string) {
    if (!this.config.settings.romDirectories.includes(dir)) {
      this.config.settings.romDirectories.push(dir);
      this.save();
    }
  }

  removeRomDirectory(dir: string) {
    this.config.settings.romDirectories = this.config.settings.romDirectories.filter((d) => d !== dir);
    this.save();
  }

  addRomFile(filePath: string) {
    if (!this.config.settings.romFiles) {
      this.config.settings.romFiles = [];
    }
    if (!this.config.settings.romFiles.includes(filePath)) {
      this.config.settings.romFiles.push(filePath);
      this.save();
    }
  }

  removeRomFile(filePath: string) {
    if (!this.config.settings.romFiles) return;
    this.config.settings.romFiles = this.config.settings.romFiles.filter((f) => f !== filePath);
    this.save();
  }

  restoreHiddenRoms() {
    this.config.settings.hiddenRoms = [];
    this.save();
    return this.config.settings.hiddenRoms;
  }

  setConsoleVisible(console: string, visible: boolean) {
    const list = this.config.settings.hiddenConsoles || [];
    if (visible) {
      this.config.settings.hiddenConsoles = list.filter((c) => c !== console);
    } else {
      if (!list.includes(console)) {
        list.push(console);
      }
      this.config.settings.hiddenConsoles = list;
    }
    this.save();
    return this.config.settings.hiddenConsoles;
  }

  resetProfile() {
    this.config.profile = {
      id: generateFriendCode(),
      name: 'Joueur',
      avatarDataUrl: null,
      ownerKey: crypto.randomBytes(32).toString('hex'),
    };
    this.config.wgKeys = WireGuardManager.generateKeyPair();
    this.config.settings.friends = [];
    this.config.settings.friendNicknames = {};
    this.config.settings.friendNames = {};
    this.config.settings.friendAvatars = {};
    this.config.settings.friendAvatarHashes = {};
    this.config.settings.pendingFriendRequests = [];
    this.save();
    return this.config;
  }
}
