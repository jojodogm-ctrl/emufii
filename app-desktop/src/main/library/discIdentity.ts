import fs from 'node:fs';
import path from 'node:path';

export interface RomIdentity {
  title?: string;
  titleId?: string;
  productCode?: string;
  ps2ElfCrc?: string;
  discId?: string;
  hash?: string;
  detectedConsole?: '3DS' | 'PSP' | 'DS' | 'Switch' | 'GameCube' | 'Wii' | 'PS2';
}

const SECTOR = 2048;
const PVD_OFFSET = 16 * SECTOR; // 32768
const MAX_DIRECTORY_BYTES = 16 * 1024 * 1024;
const MAX_CNF_BYTES = 4096;

interface IsoDirectoryRecord {
  lba: number;
  size: number;
  isDirectory: boolean;
  name: string;
}

function readRecord(buffer: Buffer, at: number): IsoDirectoryRecord | null {
  if (at < 0 || at + 34 > buffer.length) return null;
  const length = buffer.readUInt8(at);
  if (length < 34 || at + length > buffer.length) return null;
  const lba = buffer.readUInt32LE(at + 2);
  const size = buffer.readUInt32LE(at + 10);
  const flags = buffer.readUInt8(at + 25);
  const nameLen = buffer.readUInt8(at + 32);
  if (at + 33 + nameLen > buffer.length) return null;
  const rawName = buffer.subarray(at + 33, at + 33 + nameLen).toString('binary');
  const normalized = rawName.split(';')[0].replace(/\.$/, '').trim();
  return {
    lba,
    size,
    isDirectory: (flags & 0x02) !== 0,
    name: normalized,
  };
}

function parseDirectory(buffer: Buffer): IsoDirectoryRecord[] {
  const records: IsoDirectoryRecord[] = [];
  let at = 0;
  while (at < buffer.length) {
    const length = buffer.readUInt8(at);
    if (length === 0) {
      // ISO9660 records never straddle sector boundary; remainder is padding
      at = Math.floor(at / SECTOR + 1) * SECTOR;
      continue;
    }
    const rec = readRecord(buffer, at);
    if (rec) {
      records.push(rec);
    }
    at += length;
  }
  return records;
}

function readFully(fd: number, offset: number, length: number): Buffer | null {
  try {
    const buf = Buffer.alloc(length);
    const bytesRead = fs.readSync(fd, buf, 0, length, offset);
    if (bytesRead < length) return buf.subarray(0, bytesRead);
    return buf;
  } catch {
    return null;
  }
}

/**
 * Searches a buffer for a regex pattern and returns matched string.
 */
function searchPattern(buffer: Buffer, pattern: RegExp): string | null {
  const str = buffer.toString('binary');
  const match = str.match(pattern);
  return match ? match[1] : null;
}

/**
 * Parses PS2 ISO9660 filesystem to extract serial and real boot ELF CRC.
 * Faithfully mirrors Ps2DiscIdentityReader.kt and ARMSX2-AUTOCONFIG.md.
 */
function readPs2IdentityFromIso(fd: number, pvd: Buffer): { serial: string; elfCrc: string } | null {
  try {
    // Root directory record is at PVD offset 156
    const rootRec = readRecord(pvd, 156);
    if (!rootRec || rootRec.size <= 0 || rootRec.size > MAX_DIRECTORY_BYTES) return null;

    // Read Root Directory Table
    const rootDirBuf = readFully(fd, rootRec.lba * SECTOR, rootRec.size);
    if (!rootDirBuf) return null;
    const rootEntries = parseDirectory(rootDirBuf);

    // Locate SYSTEM.CNF
    const cnfEntry = rootEntries.find((r) => r.name.toUpperCase() === 'SYSTEM.CNF');
    if (!cnfEntry || cnfEntry.size <= 0) return null;

    const cnfBuf = readFully(fd, cnfEntry.lba * SECTOR, Math.min(cnfEntry.size, MAX_CNF_BYTES));
    if (!cnfBuf) return null;
    const cnfStr = cnfBuf.toString('latin1');

    // Parse BOOT2 = cdrom0:\<PATH>;1
    const bootLine = cnfStr.split(/\r?\n/).find((line) => line.trim().toUpperCase().startsWith('BOOT2'));
    if (!bootLine) return null;

    const rawPath = bootLine.split('=')[1]?.trim() || '';
    const cleanPath = rawPath.replace(/^[a-zA-Z0-9]+:/, '').replace(/^\\+/, '').split(';')[0];
    const parts = cleanPath.split(/[\\/]/).filter((p) => p.length > 0);
    if (parts.length === 0) return null;

    // Serial from executable filename (e.g. SLUS_213.55 -> SLUS-21355)
    const filename = parts[parts.length - 1];
    const serialRaw = filename.replace(/\./g, '').replace(/_/g, '-').toUpperCase().trim();
    const serialMatch = serialRaw.match(/^[A-Z]{4}-\d{5}$/);
    const serial = serialMatch ? serialMatch[0] : serialRaw;

    // Follow directory path components to the boot ELF
    let currentDirEntries = rootEntries;
    let targetEntry: IsoDirectoryRecord | null = null;

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i].toUpperCase();
      const isLast = i === parts.length - 1;
      const found = currentDirEntries.find((r) => r.name.toUpperCase() === part);
      if (!found) return null;

      if (isLast) {
        if (found.isDirectory || found.size < 4) return null;
        targetEntry = found;
      } else {
        if (!found.isDirectory) return null;
        const subDirBuf = readFully(fd, found.lba * SECTOR, found.size);
        if (!subDirBuf) return null;
        currentDirEntries = parseDirectory(subDirBuf);
      }
    }

    if (!targetEntry) return null;

    // Stream boot ELF and calculate XOR of complete 32-bit LE words
    let crc = 0;
    let offset = targetEntry.lba * SECTOR;
    let wordsLeft = Math.floor(targetEntry.size / 4);
    const chunkBytes = 64 * 1024;
    const chunkBuf = Buffer.alloc(chunkBytes);

    while (wordsLeft > 0) {
      const wordsToRead = Math.min(Math.floor(chunkBytes / 4), wordsLeft);
      const bytesToRead = wordsToRead * 4;
      const n = fs.readSync(fd, chunkBuf, 0, bytesToRead, offset);
      if (n < bytesToRead) break;

      for (let i = 0; i < wordsToRead; i++) {
        const w = chunkBuf.readUInt32LE(i * 4);
        crc = (crc ^ w) >>> 0;
      }
      wordsLeft -= wordsToRead;
      offset += bytesToRead;
    }

    const elfCrc = crc.toString(16).toUpperCase().padStart(8, '0');
    return { serial, elfCrc };
  } catch {
    return null;
  }
}

/**
 * Extract identity and console type from disc/ROM files.
 */
export async function identifyRom(filePath: string, ext: string): Promise<RomIdentity> {
  const cleanExt = ext.toLowerCase().replace(/^\./, '');
  const result: RomIdentity = {};

  try {
    const fd = fs.openSync(filePath, 'r');
    const header = Buffer.alloc(65536); // Read 64KB to cover sector 16 (0x8000 = 32768)
    const bytesRead = fs.readSync(fd, header, 0, 65536, 0);

    // 1. Nintendo DS (.nds, .dsi)
    if (cleanExt === 'nds' || cleanExt === 'dsi' || cleanExt === 'ids') {
      result.detectedConsole = 'DS';
      if (bytesRead >= 0x20) {
        const rawTitle = header.subarray(0, 12).toString('ascii').replace(/\0/g, '').trim();
        const gameCode = header.subarray(12, 16).toString('ascii').replace(/\0/g, '').trim();
        if (rawTitle) result.title = rawTitle;
        if (gameCode) {
          result.productCode = gameCode;
          result.titleId = gameCode;
        }
      }
    }

    // 2. Nintendo 3DS (.3ds, .cci, .cxi, .cia)
    else if (['3ds', 'cci', 'cxi', 'cia', '3dsx'].includes(cleanExt)) {
      result.detectedConsole = '3DS';
      const ncchOffset = 0x100;
      if (header.subarray(ncchOffset, ncchOffset + 4).toString('ascii') === 'NCCH') {
        const titleId = header.readBigUInt64LE(ncchOffset + 0x08).toString(16).toUpperCase().padStart(16, '0');
        const prod = header.subarray(ncchOffset + 0x50, ncchOffset + 0x60).toString('ascii').replace(/\0/g, '').trim();
        result.titleId = titleId;
        if (prod) result.productCode = prod;
      } else {
        const ctrMatch = searchPattern(header, /(CTR-[A-Z]-[A-Z0-9]{4})/);
        if (ctrMatch) {
          result.productCode = ctrMatch;
          result.titleId = ctrMatch;
        }
      }
    }

    // 3. GameCube / Wii magic detection (WBFS, RVZ, WIA, GCM, NKit, ISO)
    const isWiiGcCandidate =
      ['gcm', 'rvz', 'wia', 'wbfs', 'iso', 'cso'].includes(cleanExt) ||
      filePath.toLowerCase().includes('.nkit.');

    if (isWiiGcCandidate && bytesRead >= 0x20) {
      const tag = header.subarray(0, 4).toString('ascii');
      if (tag.startsWith('RVZ') || tag.startsWith('WIA')) {
        const type = header.readUInt8(0x48);
        result.detectedConsole = type === 1 ? 'GameCube' : 'Wii';
      } else if (tag.startsWith('WBFS')) {
        result.detectedConsole = 'Wii';
      } else {
        const wiiMagic = header.readUInt32BE(0x18);
        const gcMagic = header.readUInt32BE(0x1C);
        if (wiiMagic === 0x5D1C9EA3) {
          result.detectedConsole = 'Wii';
        } else if (gcMagic === 0xC2339F3D) {
          result.detectedConsole = 'GameCube';
        } else if (cleanExt === 'gcm') {
          result.detectedConsole = 'GameCube';
        } else if (['rvz', 'wia', 'wbfs'].includes(cleanExt) || filePath.toLowerCase().includes('.nkit.')) {
          result.detectedConsole = 'Wii';
        }
      }

      if (result.detectedConsole === 'Wii' || result.detectedConsole === 'GameCube') {
        const code = header.subarray(0, 6).toString('ascii').replace(/[^A-Za-z0-9]/g, '');
        if (code.length >= 4) {
          result.productCode = code;
          result.titleId = code;
          result.discId = code;
        }
        const discTitle = header.subarray(0x20, 0x60).toString('ascii').replace(/\0/g, '').trim();
        if (discTitle) result.title = discTitle;
      }
    }

    // 4. ISO & CHD handling with ISO9660 PVD verification (PSP / PS2)
    if (!result.detectedConsole && (cleanExt === 'iso' || cleanExt === 'chd')) {
      if (bytesRead >= PVD_OFFSET + SECTOR) {
        const pvd = header.subarray(PVD_OFFSET, PVD_OFFSET + SECTOR);
        if (pvd[0] === 1 && pvd.subarray(1, 6).toString('ascii') === 'CD001') {
          const sysId = pvd.subarray(8, 40).toString('latin1').trim().toUpperCase();

          if (sysId.includes('PSP GAME')) {
            result.detectedConsole = 'PSP';
            // Search for PSP DISC_ID in the first 64KB
            const match = searchPattern(header, /([Uu][Ll][A-Za-z][A-Za-z][-_ ]?[0-9]{5})/);
            if (match) {
              const discId = match.replace(/[-_ ]/g, '').toUpperCase();
              result.discId = discId;
              result.titleId = discId;
              result.productCode = discId;
            }
          } else if (sysId.includes('PLAYSTATION')) {
            result.detectedConsole = 'PS2';
            const ps2Info = readPs2IdentityFromIso(fd, pvd);
            if (ps2Info) {
              result.productCode = ps2Info.serial;
              result.titleId = ps2Info.serial;
              result.discId = ps2Info.serial;
              result.ps2ElfCrc = ps2Info.elfCrc;
            }
          }
        }
      }

      // Fallback for PSP/PS2 ISO/CHD when PVD is compressed (e.g. CHD) or non-standard
      if (!result.titleId) {
        const pspMatch = searchPattern(header, /([Uu][Ll][A-Za-z][A-Za-z][-_ ]?[0-9]{5})/);
        if (pspMatch) {
          const discId = pspMatch.replace(/[-_ ]/g, '').toUpperCase();
          result.detectedConsole = 'PSP';
          result.discId = discId;
          result.titleId = discId;
          result.productCode = discId;
        } else {
          const ps2Match = searchPattern(header, /([Ss][Ll][A-Za-z][A-Za-z][-_ ]?[0-9]{3,5}\.?[0-9]{2})/);
          if (ps2Match) {
            const serial = ps2Match.toUpperCase().replace(/\./g, '').replace(/_/g, '-');
            result.detectedConsole = 'PS2';
            result.productCode = serial;
            result.titleId = serial;
            result.discId = serial;
          }
        }
      }
    }

    // 5. GBA cartridge ROM header
    else if (cleanExt === 'gba' || cleanExt === 'agb' || cleanExt === 'bin') {
      if (bytesRead >= 0xC0) {
        // GBA header: 0xA0..0xAB (12 bytes title), 0xAC..0xAF (4 bytes game code)
        const rawCode = header.subarray(0xAC, 0xB0).toString('ascii').replace(/[^A-Za-z0-9]/g, '').trim();
        const rawTitle = header.subarray(0xA0, 0xAC).toString('ascii').replace(/\0/g, '').trim();
        if (rawCode.length === 4) {
          result.detectedConsole = 'DS';
          result.productCode = rawCode;
          result.titleId = rawCode;
          const fileTitle = path.basename(filePath, path.extname(filePath))
            .replace(/[\(\[\{].*?[\)\]\}]/g, '')
            .replace(/\s*[-_]+\s*/g, ' - ')
            .replace(/\s{2,}/g, ' ')
            .trim();
          result.title = fileTitle.length > rawTitle.length ? fileTitle : (rawTitle || fileTitle);
        }
      }
    }

    // 6. Nintendo Switch (.xci, .nsp)
    else if (cleanExt === 'xci' || cleanExt === 'nsp') {
      result.detectedConsole = 'Switch';
      const titleIdMatch = filePath.match(/\[([0-9A-Fa-f]{16})\]/) || filePath.match(/\b(0100[0-9A-Fa-f]{12})\b/);
      if (titleIdMatch) {
        const id = titleIdMatch[1].toUpperCase();
        result.titleId = id;
        result.productCode = id;
      }
    }

    fs.closeSync(fd);
  } catch {
    // Return whatever was collected
  }

  // Default fallback title from filename
  if (!result.title) {
    result.title = path.basename(filePath, path.extname(filePath))
      .replace(/[\(\[\{].*?[\)\]\}]/g, '')
      .replace(/\s*[-_]+\s*/g, ' - ')
      .replace(/\s{2,}/g, ' ')
      .trim();
  }

  return result;
}
