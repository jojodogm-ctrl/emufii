import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import type { ConsoleType } from './scanner.js';

/**
 * Creates a valid PNG buffer from raw RGBA (8-bit per channel) pixel bytes.
 * Uses Node's built-in zlib for zero-dependency PNG encoding.
 */
export function createPngBuffer(width: number, height: number, rgbaBuffer: Buffer): Buffer {
  function crc32(buf: Buffer): number {
    let crc = 0xffffffff;
    for (let i = 0; i < buf.length; i++) {
      crc ^= buf[i];
      for (let j = 0; j < 8; j++) {
        crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
      }
    }
    return (crc ^ 0xffffffff) >>> 0;
  }

  function chunk(type: string, data: Buffer): Buffer {
    const typeBuf = Buffer.from(type, 'ascii');
    const lenBuf = Buffer.alloc(4);
    lenBuf.writeUInt32BE(data.length, 0);
    const crcBuf = Buffer.alloc(4);
    const crc = crc32(Buffer.concat([typeBuf, data]));
    crcBuf.writeUInt32BE(crc, 0);
    return Buffer.concat([lenBuf, typeBuf, data, crcBuf]);
  }

  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // 8 bits per channel
  ihdr[9] = 6; // RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const scanlines: Buffer[] = [];
  for (let y = 0; y < height; y++) {
    scanlines.push(Buffer.from([0])); // Filter type 0 (None)
    scanlines.push(rgbaBuffer.subarray(y * width * 4, (y + 1) * width * 4));
  }
  const rawData = Buffer.concat(scanlines);
  const idat = zlib.deflateSync(rawData);

  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', idat), chunk('IEND', Buffer.alloc(0))]);
}

/**
 * Decodes NDS 32x32 4bpp tiled icon with 16-color BGR555 palette.
 * Exact algorithm from Android NdsBanner.kt.
 */
function decodeNdsBannerIcon(banner: Buffer): Buffer | null {
  const PALETTE_OFFSET = 0x220;
  const BITMAP_OFFSET = 0x020;
  const ICON_DIM = 32;

  if (banner.length < PALETTE_OFFSET + 32) return null;

  // 16 colors in BGR555
  const paletteR: number[] = new Array(16);
  const paletteG: number[] = new Array(16);
  const paletteB: number[] = new Array(16);
  const paletteA: number[] = new Array(16);

  for (let i = 0; i < 16; i++) {
    const lo = banner[PALETTE_OFFSET + i * 2];
    const hi = banner[PALETTE_OFFSET + i * 2 + 1];
    const val = (hi << 8) | lo;
    if (i === 0) {
      // First color is transparent
      paletteR[i] = 0;
      paletteG[i] = 0;
      paletteB[i] = 0;
      paletteA[i] = 0;
    } else {
      const r5 = val & 0x1f;
      const g5 = (val >> 5) & 0x1f;
      const b5 = (val >> 10) & 0x1f;
      paletteR[i] = (r5 << 3) | (r5 >> 2);
      paletteG[i] = (g5 << 3) | (g5 >> 2);
      paletteB[i] = (b5 << 3) | (b5 >> 2);
      paletteA[i] = 255;
    }
  }

  const rgba = Buffer.alloc(ICON_DIM * ICON_DIM * 4);
  const tilesPerRow = ICON_DIM / 8; // 4 tiles

  for (let tileY = 0; tileY < tilesPerRow; tileY++) {
    for (let tileX = 0; tileX < tilesPerRow; tileX++) {
      const tileBase = BITMAP_OFFSET + (tileY * tilesPerRow + tileX) * 32;
      for (let row = 0; row < 8; row++) {
        for (let pair = 0; pair < 4; pair++) {
          const byte = banner[tileBase + row * 4 + pair];
          const left = byte & 0x0f;
          const right = (byte >> 4) & 0x0f;
          const x = tileX * 8 + pair * 2;
          const y = tileY * 8 + row;

          const idx1 = (y * ICON_DIM + x) * 4;
          rgba[idx1] = paletteR[left];
          rgba[idx1 + 1] = paletteG[left];
          rgba[idx1 + 2] = paletteB[left];
          rgba[idx1 + 3] = paletteA[left];

          const idx2 = (y * ICON_DIM + x + 1) * 4;
          rgba[idx2] = paletteR[right];
          rgba[idx2 + 1] = paletteG[right];
          rgba[idx2 + 2] = paletteB[right];
          rgba[idx2 + 3] = paletteA[right];
        }
      }
    }
  }

  return createPngBuffer(ICON_DIM, ICON_DIM, rgba);
}

/**
 * Decodes 3DS SMDH 48x48 icon from RGB565 tiled with Morton order.
 * Exact algorithm from Android SmdhIcon.kt.
 */
function decodeSmdhIcon(smdh: Buffer): Buffer | null {
  const SMDH_LARGE_ICON_OFFSET = 0x24c0;
  const ICON_DIM = 48;

  if (smdh.length < SMDH_LARGE_ICON_OFFSET + 0x1200) return null;

  function mortonIndex(x: number, y: number): number {
    return (
      (x & 1) |
      ((y & 1) << 1) |
      ((x & 2) << 1) |
      ((y & 2) << 2) |
      ((x & 4) << 2) |
      ((y & 4) << 3)
    );
  }

  const rgba = Buffer.alloc(ICON_DIM * ICON_DIM * 4);
  const tilesPerRow = ICON_DIM / 8; // 6 tiles

  for (let tileY = 0; tileY < tilesPerRow; tileY++) {
    for (let tileX = 0; tileX < tilesPerRow; tileX++) {
      for (let py = 0; py < 8; py++) {
        for (let px = 0; px < 8; px++) {
          const morton = mortonIndex(px, py);
          const tileIdx = tileY * tilesPerRow + tileX;
          const byteOffset = SMDH_LARGE_ICON_OFFSET + (tileIdx * 64 + morton) * 2;
          const lo = smdh[byteOffset];
          const hi = smdh[byteOffset + 1];
          const rgb565 = (hi << 8) | lo;

          const r5 = (rgb565 >> 11) & 0x1f;
          const g6 = (rgb565 >> 5) & 0x3f;
          const b5 = rgb565 & 0x1f;

          const r8 = (r5 << 3) | (r5 >> 2);
          const g8 = (g6 << 2) | (g6 >> 4);
          const b8 = (b5 << 3) | (b5 >> 2);

          const x = tileX * 8 + px;
          const y = tileY * 8 + py;
          const outIdx = (y * ICON_DIM + x) * 4;

          rgba[outIdx] = r8;
          rgba[outIdx + 1] = g8;
          rgba[outIdx + 2] = b8;
          rgba[outIdx + 3] = 255;
        }
      }
    }
  }

  return createPngBuffer(ICON_DIM, ICON_DIM, rgba);
}

/**
 * Extracts ICON0.PNG from PSP ISO9660 filesystem.
 * Searches PSP_GAME/ICON0.PNG matching UmdIso.kt.
 */
function extractPspIsoIcon(filePath: string): Buffer | null {
  const SECTOR = 2048;
  const PVD_SECTOR = 16;
  const ROOT_RECORD_AT = 156;

  try {
    const fd = fs.openSync(filePath, 'r');
    const pvd = Buffer.alloc(SECTOR);
    fs.readSync(fd, pvd, 0, SECTOR, PVD_SECTOR * SECTOR);

    if (pvd.subarray(1, 6).toString('ascii') !== 'CD001') {
      fs.closeSync(fd);
      return null;
    }

    const rootLba = pvd.readUInt32LE(ROOT_RECORD_AT + 2);
    const rootSize = pvd.readUInt32LE(ROOT_RECORD_AT + 10);
    if (rootSize <= 0 || rootSize > 2 * 1024 * 1024) {
      fs.closeSync(fd);
      return null;
    }

    const rootDir = Buffer.alloc(rootSize);
    fs.readSync(fd, rootDir, 0, rootSize, rootLba * SECTOR);

    let pspGameLba = 0;
    let pspGameSize = 0;

    let i = 0;
    while (i < rootDir.length) {
      const len = rootDir[i];
      if (len === 0) {
        const next = Math.floor(i / SECTOR + 1) * SECTOR;
        if (next <= i || next >= rootDir.length) break;
        i = next;
        continue;
      }
      if (i + len > rootDir.length) break;

      const nameLen = rootDir[i + 32];
      const name = rootDir.subarray(i + 33, i + 33 + nameLen).toString('ascii').split(';')[0].toUpperCase();
      if (name === 'PSP_GAME') {
        pspGameLba = rootDir.readUInt32LE(i + 2);
        pspGameSize = rootDir.readUInt32LE(i + 10);
        break;
      }
      i += len;
    }

    if (!pspGameLba || !pspGameSize || pspGameSize > 2 * 1024 * 1024) {
      fs.closeSync(fd);
      return null;
    }

    const pspDir = Buffer.alloc(pspGameSize);
    fs.readSync(fd, pspDir, 0, pspGameSize, pspGameLba * SECTOR);

    let iconLba = 0;
    let iconSize = 0;

    i = 0;
    while (i < pspDir.length) {
      const len = pspDir[i];
      if (len === 0) {
        const next = Math.floor(i / SECTOR + 1) * SECTOR;
        if (next <= i || next >= pspDir.length) break;
        i = next;
        continue;
      }
      if (i + len > pspDir.length) break;

      const nameLen = pspDir[i + 32];
      const name = pspDir.subarray(i + 33, i + 33 + nameLen).toString('ascii').split(';')[0].toUpperCase();
      if (name === 'ICON0.PNG') {
        iconLba = pspDir.readUInt32LE(i + 2);
        iconSize = pspDir.readUInt32LE(i + 10);
        break;
      }
      i += len;
    }

    if (!iconLba || !iconSize || iconSize > 1024 * 1024) {
      fs.closeSync(fd);
      return null;
    }

    const iconBuf = Buffer.alloc(iconSize);
    fs.readSync(fd, iconBuf, 0, iconSize, iconLba * SECTOR);
    fs.closeSync(fd);

    if (iconBuf.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) {
      return iconBuf;
    }
  } catch {
    // Ignore read errors
  }

  return null;
}

/**
 * Extracts embedded icon from NDS, 3DS, or PSP games into an icon cache directory.
 */
export async function extractEmbeddedRomIcon(
  filePath: string,
  consoleType: ConsoleType,
  cacheDir: string,
  cacheKey: string
): Promise<string | null> {
  const ext = path.extname(filePath).toLowerCase();
  const iconCacheFile = path.join(cacheDir, `${consoleType.toLowerCase()}_${cacheKey}.png`);

  if (fs.existsSync(iconCacheFile)) {
    return 'file://' + iconCacheFile;
  }

  if (!fs.existsSync(cacheDir)) {
    fs.mkdirSync(cacheDir, { recursive: true });
  }

  let pngBuffer: Buffer | null = null;

  try {
    if (consoleType === 'DS' && ext === '.nds') {
      const fd = fs.openSync(filePath, 'r');
      const header = Buffer.alloc(0x200);
      fs.readSync(fd, header, 0, 0x200, 0);

      const bannerOffset = header.readUInt32LE(0x68);
      if (bannerOffset > 0 && bannerOffset < fs.statSync(filePath).size) {
        const banner = Buffer.alloc(0xa40);
        fs.readSync(fd, banner, 0, 0xa40, bannerOffset);
        pngBuffer = decodeNdsBannerIcon(banner);
      }
      fs.closeSync(fd);
    } else if (consoleType === 'PSP' && ext === '.iso') {
      pngBuffer = extractPspIsoIcon(filePath);
    } else if (consoleType === '3DS' && (ext === '.3ds' || ext === '.cci' || ext === '.cxi')) {
      // 3DS ExeFS icon search
      const fd = fs.openSync(filePath, 'r');
      // Read NCSD header at 0x100
      const ncsd = Buffer.alloc(0x200);
      fs.readSync(fd, ncsd, 0, 0x200, 0x100);

      if (ncsd.subarray(0x100, 0x104).toString('ascii') === 'NCSD' || ncsd.subarray(0, 4).toString('ascii') === 'NCCH') {
        // Read ExeFS offset from partition 0 (at 0x120: offset in media units 0x200)
        const isNcch = ncsd.subarray(0, 4).toString('ascii') === 'NCCH';
        const ncchOffset = isNcch ? 0 : ncsd.readUInt32LE(0x120) * 0x200;

        const ncchHeader = Buffer.alloc(0x200);
        fs.readSync(fd, ncchHeader, 0, 0x200, ncchOffset);

        if (ncchHeader.subarray(0x100, 0x104).toString('ascii') === 'NCCH') {
          const exefsOffset = ncchOffset + ncchHeader.readUInt32LE(0x1a0) * 0x200;
          const exefsHeader = Buffer.alloc(0x200);
          fs.readSync(fd, exefsHeader, 0, 0x200, exefsOffset);

          // Find "icon" section
          for (let s = 0; s < 10; s++) {
            const secName = exefsHeader.subarray(s * 16, s * 16 + 8).toString('ascii').replace(/\0/g, '').trim();
            if (secName === 'icon') {
              const secOffset = exefsHeader.readUInt32LE(s * 16 + 8);
              const secSize = exefsHeader.readUInt32LE(s * 16 + 12);
              if (secSize >= 0x24c0 + 0x1200) {
                const smdh = Buffer.alloc(secSize);
                fs.readSync(fd, smdh, 0, secSize, exefsOffset + 0x200 + secOffset);
                if (smdh.subarray(0, 4).toString('ascii') === 'SMDH') {
                  pngBuffer = decodeSmdhIcon(smdh);
                }
              }
              break;
            }
          }
        }
      }
      fs.closeSync(fd);
    }
  } catch {
    // Ignore extraction errors
  }

  if (pngBuffer && pngBuffer.length > 0) {
    fs.writeFileSync(iconCacheFile, pngBuffer);
    return 'file://' + iconCacheFile;
  }

  return null;
}

/**
 * Maps console type to Libretro Thumbnails GitHub repository name.
 */
export function getLibretroThumbnailsRepo(consoleType: ConsoleType, filename?: string): string | null {
  if (filename && (filename.toLowerCase().endsWith('.gba') || filename.toLowerCase().endsWith('.agb'))) {
    return 'Nintendo_-_Game_Boy_Advance';
  }
  const map: Record<ConsoleType, string> = {
    DS: 'Nintendo_-_Nintendo_DS',
    '3DS': 'Nintendo_-_Nintendo_3DS',
    PSP: 'Sony_-_PlayStation_Portable',
    PS2: 'Sony_-_PlayStation_2',
    GameCube: 'Nintendo_-_GameCube',
    Wii: 'Nintendo_-_Wii',
    Switch: 'Nintendo_-_Nintendo_Switch',
  };
  return map[consoleType] || null;
}

/**
 * Returns the public Libretro Thumbnails URL for a game if available.
 */
export function getLibretroBoxartUrl(consoleType: ConsoleType, filename: string): string | null {
  const repo = getLibretroThumbnailsRepo(consoleType, filename);
  if (!repo) return null;

  const baseName = filename
    .replace(/\.nkit(\.iso|\.gcz|\.cso|\.chd)?$/i, '')
    .replace(/\.[^/.]+$/, '')
    .replace(/\.nkit$/i, '')
    .replace(/&/g, '_');
  return `https://raw.githubusercontent.com/libretro-thumbnails/${repo}/master/Named_Boxarts/${encodeURIComponent(baseName)}.png`;
}

/**
 * Returns a fallback Libretro Thumbnails URL using a sanitized/region fallback.
 */
export function getLibretroFallbackUrl(consoleType: ConsoleType, filename: string, cleanTitle: string): string | null {
  const repo = getLibretroThumbnailsRepo(consoleType, filename);
  if (!repo) return null;

  const clean = cleanTitle.replace(/&/g, '_').trim();
  return `https://raw.githubusercontent.com/libretro-thumbnails/${repo}/master/Named_Boxarts/${encodeURIComponent(clean + ' (USA)')}.png`;
}

/**
 * Returns official GameTDB high-resolution cover art for Nintendo consoles (Wii, GameCube, DS, 3DS).
 */
export function getGameTdbUrl(consoleType: ConsoleType, titleId?: string | null): string | null {
  if (!titleId || titleId.length < 4) return null;
  const cleanId = titleId.toUpperCase().trim();
  const regionCode = cleanId[3];
  let region = 'US';
  if (regionCode === 'P') region = 'EN';
  else if (regionCode === 'J') region = 'JA';
  else if (regionCode === 'K') region = 'KO';
  else if (regionCode === 'E') region = 'US';

  const slugMap: Record<string, string> = {
    Wii: 'wii',
    GameCube: 'gamecube',
    DS: 'ds',
    '3DS': '3ds',
  };
  const slug = slugMap[consoleType];
  if (!slug) return null;
  return `https://art.gametdb.com/${slug}/cover/${region}/${cleanId}.png`;
}

export function getGameTdbFallbackUrl(consoleType: ConsoleType, titleId?: string | null): string | null {
  if (!titleId || titleId.length < 4) return null;
  const cleanId = titleId.toUpperCase().trim();
  const regionCode = cleanId[3];
  const fallbackRegion = regionCode === 'P' ? 'US' : 'EN';
  const slugMap: Record<string, string> = {
    Wii: 'wii',
    GameCube: 'gamecube',
    DS: 'ds',
    '3DS': '3ds',
  };
  const slug = slugMap[consoleType];
  if (!slug) return null;
  return `https://art.gametdb.com/${slug}/cover/${fallbackRegion}/${cleanId}.png`;
}
