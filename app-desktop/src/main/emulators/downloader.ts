import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { exec } from 'node:child_process';
import { promisify } from 'node:util';

const execAsync = promisify(exec);

export interface EmulatorDownloadInfo {
  name: string;
  console: string;
  linuxUrl: string;
  archiveType: 'direct' | 'zip';
  binaryName: string;
  extractedName?: string;
  winUrl: string;
  winArchiveType: 'zip' | '7z';
  winFallbackInstallerUrl?: string;
  winInstallerFlags?: string;
  winBinaryNames: string[];
}

export const EMULATOR_CATALOG: Record<string, EmulatorDownloadInfo> = {
  DS: {
    name: 'melonDS',
    console: 'DS',
    linuxUrl: 'https://github.com/melonDS-emu/melonDS/releases/download/1.1/melonDS-1.1-appimage-x86_64.zip',
    archiveType: 'zip',
    binaryName: 'melonDS.AppImage',
    extractedName: 'melonDS-x86_64.AppImage',
    winUrl: 'https://github.com/melonDS-emu/melonDS/releases/download/1.1/melonDS-1.1-windows-x86_64.zip',
    winArchiveType: 'zip',
    winBinaryNames: ['melonDS.exe'],
  },
  GBA: {
    name: 'mGBA',
    console: 'GBA',
    linuxUrl: 'https://github.com/mgba-emu/mgba/releases/download/0.10.5/mGBA-0.10.5-appimage-x64.appimage',
    archiveType: 'direct',
    binaryName: 'mgba.AppImage',
    winUrl: 'https://github.com/mgba-emu/mgba/releases/download/0.10.5/mGBA-0.10.5-win64.7z',
    winArchiveType: '7z',
    winFallbackInstallerUrl: 'https://github.com/mgba-emu/mgba/releases/download/0.10.5/mGBA-0.10.5-win64-installer.exe',
    winInstallerFlags: '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART',
    winBinaryNames: ['mGBA.exe', 'mgba.exe'],
  },
  PSP: {
    name: 'PPSSPP',
    console: 'PSP',
    linuxUrl: 'https://github.com/hrydgard/ppsspp/releases/download/v1.20.4/PPSSPP-v1.20.4-anylinux-x86_64.AppImage',
    archiveType: 'direct',
    binaryName: 'ppsspp.AppImage',
    winUrl: 'https://github.com/hrydgard/ppsspp/releases/download/v1.20.4/PPSSPP-v1.20.4-Windows-x64.zip',
    winArchiveType: 'zip',
    winBinaryNames: ['PPSSPPWindows64.exe', 'PPSSPPWindows.exe'],
  },
  '3DS': {
    name: 'Azahar',
    console: '3DS',
    linuxUrl: 'https://github.com/azahar-emu/azahar/releases/download/2126.2/azahar.AppImage',
    archiveType: 'direct',
    binaryName: 'azahar.AppImage',
    winUrl: 'https://github.com/azahar-emu/azahar/releases/download/2126.2/azahar-windows-msvc-2126.2.zip',
    winArchiveType: 'zip',
    winBinaryNames: ['azahar.exe', 'azahar-qt.exe'],
  },
  PS2: {
    name: 'PCSX2',
    console: 'PS2',
    linuxUrl: 'https://github.com/PCSX2/pcsx2/releases/download/v2.8.2/pcsx2-v2.8.2-linux-appimage-x64-Qt.AppImage',
    archiveType: 'direct',
    binaryName: 'pcsx2.AppImage',
    winUrl: 'https://github.com/PCSX2/pcsx2/releases/download/v2.8.2/pcsx2-v2.8.2-windows-x64-Qt.7z',
    winArchiveType: '7z',
    winFallbackInstallerUrl: 'https://github.com/PCSX2/pcsx2/releases/download/v2.8.2/pcsx2-v2.8.2-windows-x64-installer.exe',
    winInstallerFlags: '/S',
    winBinaryNames: ['pcsx2-qt.exe', 'pcsx2.exe'],
  },
  GameCube: {
    name: 'Dolphin',
    console: 'GameCube',
    linuxUrl: 'https://github.com/pkgforge-dev/Dolphin-emu-AppImage/releases/download/2609%402026-10-01_1790876487/Dolphin_Emulator-2609-anylinux-x86_64.AppImage',
    archiveType: 'direct',
    binaryName: 'dolphin.AppImage',
    winUrl: 'https://dl.dolphin-emu.org/releases/2412/dolphin-2412-x64.7z',
    winArchiveType: '7z',
    winBinaryNames: ['Dolphin.exe'],
  },
  Wii: {
    name: 'Dolphin',
    console: 'Wii',
    linuxUrl: 'https://github.com/pkgforge-dev/Dolphin-emu-AppImage/releases/download/2609%402026-10-01_1790876487/Dolphin_Emulator-2609-anylinux-x86_64.AppImage',
    archiveType: 'direct',
    binaryName: 'dolphin.AppImage',
    winUrl: 'https://dl.dolphin-emu.org/releases/2412/dolphin-2412-x64.7z',
    winArchiveType: '7z',
    winBinaryNames: ['Dolphin.exe'],
  },
};

export class EmulatorDownloader {
  private static activeDownloads = new Map<string, Promise<{ success: boolean; path?: string; message: string }>>();

  static getBaseDir(): string {
    if (os.platform() === 'win32') {
      const localApp = process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local');
      return path.join(localApp, 'Emufii', 'emulators');
    }
    return path.join(os.homedir(), '.config', 'emufii', 'emulators');
  }

  private static findBinaryInDir(dir: string, candidateNames: string[], maxDepth = 3): string | null {
    if (!fs.existsSync(dir) || maxDepth < 0) return null;
    const lowerTargets = new Set(candidateNames.map((n) => n.toLowerCase()));

    try {
      const entries = fs.readdirSync(dir, { withFileTypes: true });
      for (const entry of entries) {
        if (entry.isFile() && lowerTargets.has(entry.name.toLowerCase())) {
          return path.join(dir, entry.name);
        }
      }
      if (maxDepth > 0) {
        for (const entry of entries) {
          if (entry.isDirectory()) {
            const found = this.findBinaryInDir(path.join(dir, entry.name), candidateNames, maxDepth - 1);
            if (found) return found;
          }
        }
      }
    } catch {}
    return null;
  }

  static getManagedPath(consoleType: string): string | null {
    const info = EMULATOR_CATALOG[consoleType];
    if (!info) return null;
    const targetFolder = consoleType === 'Wii' ? 'GameCube' : consoleType;
    const folderPath = path.join(this.getBaseDir(), targetFolder);

    if (os.platform() === 'win32') {
      const found = this.findBinaryInDir(folderPath, info.winBinaryNames);
      if (found) return found;
      return path.join(folderPath, info.winBinaryNames[0]);
    }

    return path.join(folderPath, info.binaryName);
  }

  static isDownloaded(consoleType: string): boolean {
    const p = this.getManagedPath(consoleType);
    return !!p && fs.existsSync(p);
  }

  /**
   * Download a single emulator with progress callback.
   */
  static async downloadEmulator(
    consoleType: string,
    onProgress?: (percent: number, status: string) => void
  ): Promise<{ success: boolean; path?: string; message: string }> {
    const key = consoleType === 'Wii' ? 'GameCube' : consoleType;
    const existing = this.activeDownloads.get(key);
    if (existing) {
      return existing;
    }

    const task = this.runDownload(key, onProgress).finally(() => {
      this.activeDownloads.delete(key);
    });

    this.activeDownloads.set(key, task);
    return task;
  }

  private static async streamFileDownload(
    url: string,
    destPath: string,
    label: string,
    onProgress?: (percent: number, status: string) => void
  ): Promise<void> {
    const response = await fetch(url, {
      headers: { 'User-Agent': 'EmuFii-Desktop' },
      redirect: 'follow',
    });

    if (!response.ok || !response.body) {
      throw new Error(`HTTP ${response.status}`);
    }

    const totalBytes = Number(response.headers.get('content-length')) || 0;
    let receivedBytes = 0;
    const fileStream = fs.createWriteStream(destPath);

    for await (const chunk of response.body as any) {
      fileStream.write(chunk);
      receivedBytes += chunk.length;
      if (totalBytes > 0) {
        const percent = Math.min(92, Math.round((receivedBytes / totalBytes) * 85) + 5);
        onProgress?.(
          percent,
          `Downloading ${label}... ${Math.round(receivedBytes / 1024 / 1024)}MB / ${Math.round(totalBytes / 1024 / 1024)}MB`
        );
      } else {
        onProgress?.(50, `Downloading ${label}... ${Math.round(receivedBytes / 1024 / 1024)}MB`);
      }
    }

    await new Promise<void>((resolve, reject) => {
      fileStream.end((err?: Error | null) => (err ? reject(err) : resolve()));
    });
  }

  private static async extractWindowsArchive(
    archivePath: string,
    targetDir: string,
    archiveType: 'zip' | '7z'
  ): Promise<void> {
    if (archiveType === 'zip') {
      try {
        await execAsync(`tar -xf "${archivePath}" -C "${targetDir}"`, { timeout: 90000 });
        return;
      } catch {
        const psCmd = `powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '${archivePath.replace(/'/g, "''")}' -DestinationPath '${targetDir.replace(/'/g, "''")}' -Force"`;
        await execAsync(psCmd, { timeout: 120000 });
        return;
      }
    }

    // For .7z: Windows 10/11 built-in bsdtar (tar.exe) supports .7z natively via libarchive
    try {
      await execAsync(`tar -xf "${archivePath}" -C "${targetDir}"`, { timeout: 120000 });
      return;
    } catch {
      // Try 7z CLI if installed on PATH or Program Files
      const sevenZipCandidates = [
        '7z',
        path.join(process.env.ProgramFiles || 'C:\\Program Files', '7-Zip', '7z.exe'),
        path.join(process.env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)', '7-Zip', '7z.exe'),
      ];
      for (const cmd of sevenZipCandidates) {
        try {
          await execAsync(`"${cmd}" x "${archivePath}" -o"${targetDir}" -y`, { timeout: 120000 });
          return;
        } catch {}
      }
      throw new Error('7z extraction requires Windows 11 tar or 7-Zip');
    }
  }

  private static async runDownload(
    consoleType: string,
    onProgress?: (percent: number, status: string) => void
  ): Promise<{ success: boolean; path?: string; message: string }> {
    const info = EMULATOR_CATALOG[consoleType];
    if (!info) {
      return { success: false, message: `No automatic download available for console: ${consoleType}` };
    }

    const platform = os.platform();
    if (platform !== 'linux' && platform !== 'win32') {
      return {
        success: false,
        message: `Auto-download is configured for Windows and Linux. Please install ${info.name} manually on macOS.`,
      };
    }

    const targetDir = path.join(this.getBaseDir(), consoleType);

    try {
      fs.mkdirSync(targetDir, { recursive: true });
      onProgress?.(5, `Connecting to download ${info.name}...`);

      if (platform === 'win32') {
        const tempExt = info.winArchiveType === 'zip' ? 'zip' : '7z';
        const tempPath = path.join(targetDir, `${consoleType}_temp.${tempExt}`);

        await this.streamFileDownload(info.winUrl, tempPath, info.name, onProgress);

        onProgress?.(94, `Extracting and configuring ${info.name}...`);
        try {
          await this.extractWindowsArchive(tempPath, targetDir, info.winArchiveType);
        } catch (extractErr) {
          // If .7z extraction failed on an older Windows 10 build and a fallback installer is available, use it
          if (info.winFallbackInstallerUrl) {
            onProgress?.(60, `Downloading ${info.name} Windows installer...`);
            const installerPath = path.join(targetDir, `${consoleType}_setup.exe`);
            await this.streamFileDownload(info.winFallbackInstallerUrl, installerPath, info.name, onProgress);
            onProgress?.(95, `Installing ${info.name}...`);
            const flags = info.winInstallerFlags === '/S'
              ? `/S /D=${targetDir}`
              : `${info.winInstallerFlags || '/S'} /DIR="${targetDir}"`;
            await execAsync(`"${installerPath}" ${flags}`, { timeout: 120000 });
            try { fs.unlinkSync(installerPath); } catch {}
          } else {
            throw extractErr;
          }
        } finally {
          try { fs.unlinkSync(tempPath); } catch {}
        }

        const resolvedExe = this.findBinaryInDir(targetDir, info.winBinaryNames);
        if (!resolvedExe || !fs.existsSync(resolvedExe)) {
          return {
            success: false,
            message: `Downloaded ${info.name}, but executable (${info.winBinaryNames[0]}) was not found after extraction.`,
          };
        }

        onProgress?.(100, `${info.name} installed and configured!`);
        return {
          success: true,
          path: resolvedExe,
          message: `${info.name} downloaded and ready.`,
        };
      }

      // Linux download path
      const finalBinaryPath = path.join(targetDir, info.binaryName);
      const isZip = info.archiveType === 'zip';
      const tempPath = isZip
        ? path.join(targetDir, `${consoleType}_temp.zip`)
        : path.join(targetDir, `${consoleType}_temp.AppImage`);

      await this.streamFileDownload(info.linuxUrl, tempPath, info.name, onProgress);

      if (isZip) {
        onProgress?.(96, `Extracting ${info.name}...`);
        await execAsync(`unzip -q -o "${tempPath}" -d "${targetDir}"`, { timeout: 30000 });
        try { fs.unlinkSync(tempPath); } catch {}

        if (info.extractedName && info.extractedName !== info.binaryName) {
          const extractedPath = path.join(targetDir, info.extractedName);
          if (fs.existsSync(extractedPath)) {
            try { fs.unlinkSync(finalBinaryPath); } catch {}
            fs.renameSync(extractedPath, finalBinaryPath);
          }
        }
      } else {
        try { fs.unlinkSync(finalBinaryPath); } catch {}
        fs.renameSync(tempPath, finalBinaryPath);
      }

      fs.chmodSync(finalBinaryPath, 0o755);

      onProgress?.(100, `${info.name} installed successfully!`);
      return {
        success: true,
        path: finalBinaryPath,
        message: `${info.name} downloaded and ready.`,
      };
    } catch (err: any) {
      return { success: false, message: `Failed to download ${info.name}: ${err.message}` };
    }
  }

  /**
   * Download all missing emulators sequentially with progress reporting.
   */
  static async downloadAll(
    onProgress?: (consoleType: string, percent: number, status: string) => void
  ): Promise<{ success: boolean; results: Record<string, boolean> }> {
    const consoles = ['DS', 'GBA', 'PSP', '3DS', 'PS2', 'GameCube'];
    const results: Record<string, boolean> = {};

    for (const c of consoles) {
      if (this.isDownloaded(c)) {
        results[c] = true;
        onProgress?.(c, 100, 'Already installed');
        continue;
      }

      const res = await this.downloadEmulator(c, (percent, status) => {
        onProgress?.(c, percent, status);
      });
      results[c] = res.success;
    }

    return {
      success: Object.values(results).every(Boolean),
      results,
    };
  }
}
