import fs from 'node:fs';
import path from 'node:path';
import { identifyRom, RomIdentity } from './discIdentity.js';
import { extractEmbeddedRomIcon, getLibretroBoxartUrl, getLibretroFallbackUrl, getGameTdbUrl, getGameTdbFallbackUrl } from './embeddedIcons.js';

export type ConsoleType = '3DS' | 'PSP' | 'DS' | 'Switch' | 'GameCube' | 'Wii' | 'PS2';

export interface RomItem {
  id: string;
  filePath: string;
  fileName: string;
  displayName: string;
  console: ConsoleType;
  titleId?: string | null;
  productCode?: string | null;
  ps2ElfCrc?: string | null;
  discId?: string | null;
  fileSizeBytes: number;
  addedAt: number;
  compatRating?: string | null;
  compatNote?: string | null;
  artworkUrl?: string | null;
  fallbackArtworkUrl?: string | null;
}

const CONSOLE_EXTENSIONS: Record<string, ConsoleType> = {
  // 3DS
  '3ds': '3DS',
  'cci': '3DS',
  'cxi': '3DS',
  'cia': '3DS',
  '3dsx': '3DS',
  // DS & GBA (grouped under Nintendo DS)
  'nds': 'DS',
  'dsi': 'DS',
  'ids': 'DS',
  'srl': 'DS',
  'gba': 'DS',
  'agb': 'DS',
  // Switch
  'nsp': 'Switch',
  'xci': 'Switch',
  // GameCube & Wii
  'gcm': 'GameCube',
  'rvz': 'Wii',
  'wia': 'Wii',
  'wbfs': 'Wii',
  // PSP
  'cso': 'PSP',
  'pbp': 'PSP',
  // Ambiguous: iso and chd are handled via folder + header identification
};

const FOLDER_CONSOLES: Record<string, ConsoleType> = {
  'gba': 'DS',
  'gameboyadvance': 'DS',
  'mgba': 'DS',
  'ps2': 'PS2',
  'playstation2': 'PS2',
  'sonyps2': 'PS2',
  'sonyplaystation2': 'PS2',
  'psp': 'PSP',
  'playstationportable': 'PSP',
  'nds': 'DS',
  'ds': 'DS',
  'nintendods': 'DS',
  '3ds': '3DS',
  'n3ds': '3DS',
  'nintendo3ds': '3DS',
  'switch': 'Switch',
  'nintendoswitch': 'Switch',
  'wii': 'Wii',
  'nintendowii': 'Wii',
  'gc': 'GameCube',
  'ngc': 'GameCube',
  'gamecube': 'GameCube',
  'nintendogamecube': 'GameCube',
};

const IGNORED_EXTENSIONS = new Set([
  'sav', 'srm', 'state', 'dsv', 'bkr', 'bak', 'cht', 'mcr', 'txt', 'nfo',
  'jpg', 'jpeg', 'png', 'webp', 'mp3', 'flac', 'wav', 'ogg', 'json', 'xml',
  'ini', 'cfg', 'log', 'db', 'tmp', 'git', 'md', 'rar', '7z', 'tar', 'gz'
]);

export function detectConsole(filePath: string): ConsoleType | null {
  const ext = path.extname(filePath).toLowerCase().replace(/^\./, '');
  const fileName = path.basename(filePath).toLowerCase();

  // Exclude non-ROM extensions
  if (IGNORED_EXTENSIONS.has(ext) || ext.startsWith('state')) {
    return null;
  }

  // Exclude non-game media files and photos
  if (
    fileName.startsWith('photo') ||
    fileName.includes('photos-') ||
    fileName.includes('screenshot') ||
    fileName.startsWith('img_') ||
    fileName.startsWith('dsc_')
  ) {
    return null;
  }

  // Compound extensions like .nkit.iso, .nkit.gcz
  if (fileName.includes('.nkit.')) {
    return 'Wii';
  }

  // 1. Explicit single-console extensions
  if (CONSOLE_EXTENSIONS[ext]) {
    return CONSOLE_EXTENSIONS[ext];
  }

  // 2. Ambiguous extensions: .iso, .chd
  if (ext === 'iso' || ext === 'chd') {
    const lowerPath = filePath.toLowerCase();
    if (lowerPath.includes('wii') || fileName.includes('.nkit.')) return 'Wii';
    if (lowerPath.includes('gamecube') || lowerPath.includes('gc') || fileName.includes('.gcz')) return 'GameCube';
    if (lowerPath.includes('psp')) return 'PSP';
    if (lowerPath.includes('ps2') || lowerPath.includes('playstation 2')) return 'PS2';
    // Fallback: will be verified by identifyRom magic sniffing
    return 'Wii';
  }

  // 3. DS zip archive (only if the filename itself mentions nds)
  if (ext === 'zip') {
    if (fileName.includes('.nds') || fileName.includes('nds.') || fileName.endsWith('.nds.zip')) {
      return 'DS';
    }
  }

  // 4. Folder names (only for generic disc/cartridge formats like .bin, .rom, .img)
  const genericRomExts = ['bin', 'rom', 'img', 'raw'];
  if (genericRomExts.includes(ext)) {
    const parts = filePath.toLowerCase().split(/[\\/]/);
    for (const part of parts.reverse()) {
      const cleaned = part.replace(/[^a-z0-9]/g, '');
      if (FOLDER_CONSOLES[cleaned]) {
        return FOLDER_CONSOLES[cleaned];
      }
    }
  }

  return null;
}

function findLocalArtwork(filePath: string): string | null {
  const dir = path.dirname(filePath);
  const base = path.basename(filePath, path.extname(filePath));
  const extensions = ['.png', '.jpg', '.jpeg', '.webp'];

  // 1. Check same directory: base.png, base.jpg, base.webp
  for (const ext of extensions) {
    const candidate = path.join(dir, base + ext);
    if (fs.existsSync(candidate)) {
      return 'file://' + candidate;
    }
  }

  // 2. Check media/covers subfolders
  const subdirs = ['covers', 'media', 'artwork', 'boxart'];
  for (const sub of subdirs) {
    for (const ext of extensions) {
      const candidate = path.join(dir, sub, base + ext);
      if (fs.existsSync(candidate)) {
        return 'file://' + candidate;
      }
    }
  }

  return null;
}

export function cleanDisplayName(filename: string): string {
  const noExt = filename
    .replace(/\.nkit(\.iso|\.gcz|\.cso|\.chd)?$/i, '')
    .replace(/\.[^/.]+$/, '')
    .replace(/\.nkit$/i, '');
  const stripped = noExt
    .replace(/\s*\[[^\]]*\]/g, '')
    .split(' (')[0]
    .trim()
    .replace(/\s+/g, ' ');
  const withoutSceneNumber = stripped.replace(/\s+\d{4,}$/, '');
  return withoutSceneNumber || noExt;
}

export async function scanFile(filePath: string): Promise<RomItem | null> {
  if (!fs.existsSync(filePath)) return null;
  const fallbackConsole = detectConsole(filePath);
  if (!fallbackConsole) return null;

  const ext = path.extname(filePath);
  const fileName = path.basename(filePath);
  try {
    const stats = fs.statSync(filePath);
    if (!stats.isFile()) return null;
    const identity = await identifyRom(filePath, ext);
    const consoleType = identity.detectedConsole || fallbackConsole;

    const cleanTitle = cleanDisplayName(fileName);
    // Prefer clean Title Case from filename over ALL CAPS internal disc title (e.g. RHYTHM HEAVEN FEVER)
    const isInternalAllUpper = identity.title && identity.title === identity.title.toUpperCase() && identity.title.length > 5;
    const displayName = isInternalAllUpper ? cleanTitle : (identity.title && identity.title.length > 12 ? identity.title : cleanTitle);

    // 1. Check local artwork
    let artworkUrl = findLocalArtwork(filePath);
    let fallbackArtworkUrl: string | null = null;

    const isGba = ext.toLowerCase() === '.gba' || ext.toLowerCase() === '.agb';

    // 2. Check embedded ROM icon (3DS SMDH, NDS Banner, PSP ISO)
    if (!artworkUrl) {
      try {
        const { app } = await import('electron');
        const userData = app?.getPath ? app.getPath('userData') : path.join(process.cwd(), '.data');
        const iconsDir = path.join(userData, 'icons');
        const cacheKey = identity.titleId || identity.productCode || Buffer.from(filePath).toString('hex').slice(0, 16);
        artworkUrl = await extractEmbeddedRomIcon(filePath, consoleType, iconsDir, cacheKey);
      } catch {
        // Ignore embedded icon extraction errors
      }
    }

    // 3. For consoles supported by GameTDB (Wii, GameCube, 3DS, and native NDS), fetch official cover art
    if (!artworkUrl && identity.titleId && !isGba && consoleType !== 'PSP' && consoleType !== 'PS2') {
      artworkUrl = getGameTdbUrl(consoleType, identity.titleId);
      fallbackArtworkUrl = getGameTdbFallbackUrl(consoleType, identity.titleId) || getLibretroBoxartUrl(consoleType, fileName);
    }

    // 4. Libretro Thumbnails URL (for GBA, PSP, PS2, or if GameTDB is unlisted)
    if (!artworkUrl) {
      artworkUrl = getLibretroBoxartUrl(consoleType, fileName);
      fallbackArtworkUrl = getLibretroFallbackUrl(consoleType, fileName, cleanTitle);
    }

    return {
      id: Buffer.from(filePath).toString('base64url'),
      filePath: filePath,
      fileName: fileName,
      displayName: displayName,
      console: consoleType,
      titleId: identity.titleId,
      productCode: identity.productCode,
      ps2ElfCrc: identity.ps2ElfCrc,
      discId: identity.discId,
      fileSizeBytes: stats.size,
      addedAt: stats.mtimeMs,
      artworkUrl: artworkUrl,
      fallbackArtworkUrl: fallbackArtworkUrl,
    };
  } catch {
    return null;
  }
}

export async function scanDirectory(
  rootDir: string,
  maxDepth = 4,
  currentDepth = 0
): Promise<RomItem[]> {
  const results: RomItem[] = [];
  if (!fs.existsSync(rootDir) || currentDepth > maxDepth) return results;

  let entries: fs.Dirent[];
  try {
    entries = fs.readdirSync(rootDir, { withFileTypes: true });
  } catch {
    return results;
  }

  for (const entry of entries) {
    const fullPath = path.join(rootDir, entry.name);

    if (entry.isDirectory()) {
      if (!entry.name.startsWith('.') && entry.name !== 'node_modules') {
        const sub = await scanDirectory(fullPath, maxDepth, currentDepth + 1);
        results.push(...sub);
      }
    } else if (entry.isFile()) {
      const item = await scanFile(fullPath);
      if (item) {
        results.push(item);
      }
    }
  }

  return results;
}

