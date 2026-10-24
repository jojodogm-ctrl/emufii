import { ipcMain, dialog, BrowserWindow, app, shell, nativeImage } from 'electron';
import path from 'node:path';
import fs from 'node:fs';
import { Storage, ensureSpecialRomDirectory, getSpecialRomDirectory } from './library/storage.js';
import { scanDirectory, scanFile, detectConsole, RomItem } from './library/scanner.js';
import { CoordinatorClient } from './network/coordinator.js';
import { WireGuardManager } from './network/wireguard.js';
import { EmulatorManager } from './emulators/manager.js';
import { EmulatorDownloader } from './emulators/downloader.js';

function parseSemver(v: string): number[] {
  const clean = (v || '').replace(/^v/i, '').split('-')[0];
  return clean.split('.').map((n) => parseInt(n, 10) || 0);
}

function isNewerSemver(current: string, latest: string): boolean {
  const c = parseSemver(current);
  const l = parseSemver(latest);
  for (let i = 0; i < Math.max(c.length, l.length); i++) {
    const cv = c[i] || 0;
    const lv = l[i] || 0;
    if (lv > cv) return true;
    if (lv < cv) return false;
  }
  return false;
}

export function setupIpc(
  mainWindow: BrowserWindow,
  storage: Storage,
  onLaunchInstalledApp?: () => void
) {
  function safeHandle(channel: string, handler: (event: any, ...args: any[]) => any) {
    ipcMain.removeHandler(channel);
    ipcMain.handle(channel, handler);
  }

  const initialConfig = storage.getConfig();
  CoordinatorClient.identityKey = initialConfig.profile.ownerKey || null;
  const coordinator = new CoordinatorClient(initialConfig.settings.coordinatorUrl);
  const wireguard = new WireGuardManager();

  // --- Window controls ---
  safeHandle('window:minimize', (e) => {
    const win = BrowserWindow.fromWebContents(e.sender) || mainWindow;
    win?.minimize();
  });
  safeHandle('window:maximize', (e) => {
    const win = BrowserWindow.fromWebContents(e.sender) || mainWindow;
    if (!win) return;
    if (win.isMaximized()) {
      win.unmaximize();
    } else {
      win.maximize();
    }
  });
  safeHandle('window:close', (e) => {
    const win = BrowserWindow.fromWebContents(e.sender) || mainWindow;
    win?.close();
  });
  safeHandle('window:isMaximized', (e) => {
    const win = BrowserWindow.fromWebContents(e.sender) || mainWindow;
    return win?.isMaximized() ?? false;
  });
  safeHandle('app:openExternal', async (_, url: string) => {
    if (url && (url.startsWith('https://') || url.startsWith('http://'))) {
      await shell.openExternal(url);
    }
  });

  // --- Installer Actions ---
  safeHandle('installer:launchInstalledApp', () => {
    if (onLaunchInstalledApp) {
      onLaunchInstalledApp();
    }
  });

  // --- Storage & Config ---
  safeHandle('config:get', () => storage.getConfig());
  safeHandle('config:updateProfile', (_, partial) => storage.updateProfile(partial));
  safeHandle('config:updateSettings', (_, partial) => {
    const updated = storage.updateSettings(partial);
    if (partial.coordinatorUrl) {
      coordinator.setBaseUrl(partial.coordinatorUrl);
    }
    return updated;
  });
  safeHandle('config:addFriend', (_, code, nickname) => storage.addFriend(code, nickname));
  safeHandle('config:removeFriend', (_, code) => storage.removeFriend(code));
  safeHandle('config:noteFriendMetadata', (_, names, avatars, hashes) =>
    storage.noteFriendMetadata(names, avatars, hashes)
  );
  safeHandle('config:renameRom', (_, id, title) => storage.renameRom(id, title));
  safeHandle('config:toggleHideRom', (_, id) => storage.toggleHideRom(id));
  safeHandle('config:restoreHiddenRoms', () => storage.restoreHiddenRoms());
  safeHandle('config:resetProfile', () => {
    const resetCfg = storage.resetProfile();
    CoordinatorClient.identityKey = resetCfg.profile.ownerKey || null;
    return resetCfg;
  });
  safeHandle('config:setConsoleVisible', (_, consoleName, visible) =>
    storage.setConsoleVisible(consoleName, visible)
  );

  // --- File Dialogs ---
  safeHandle('dialog:selectDirectory', async (e) => {
    const win = BrowserWindow.fromWebContents(e.sender) || mainWindow;
    const res = await dialog.showOpenDialog(win, {
      properties: ['openDirectory'],
      title: 'Select ROM Folder',
    });
    return res.canceled ? null : res.filePaths[0];
  });

  safeHandle('dialog:selectRomFile', async (e) => {
    const win = BrowserWindow.fromWebContents(e.sender) || mainWindow;
    const res = await dialog.showOpenDialog(win, {
      properties: ['openFile'],
      filters: [
        {
          name: 'Game ROMs',
          extensions: [
            'gba', 'agb', '3ds', 'cci', 'cxi', 'cia', '3dsx',
            'nds', 'dsi', 'ids', 'srl',
            'nsp', 'xci',
            'iso', 'cso', 'pbp', 'chd',
            'gcm', 'rvz', 'wia', 'wbfs', 'zip', '7z'
          ],
        },
        { name: 'All Files', extensions: ['*'] },
      ],
      title: 'Select Game ROM File',
    });
    return res.canceled ? null : res.filePaths[0];
  });

  safeHandle('dialog:selectRomFiles', async (e) => {
    const win = BrowserWindow.fromWebContents(e.sender) || mainWindow;
    const res = await dialog.showOpenDialog(win, {
      properties: ['openFile', 'multiSelections'],
      filters: [
        {
          name: 'Game ROMs',
          extensions: [
            'gba', 'agb', '3ds', 'cci', 'cxi', 'cia', '3dsx',
            'nds', 'dsi', 'ids', 'srl',
            'nsp', 'xci',
            'iso', 'cso', 'pbp', 'chd',
            'gcm', 'rvz', 'wia', 'wbfs', 'zip', '7z'
          ],
        },
        { name: 'All Files', extensions: ['*'] },
      ],
      title: 'Select Game ROM Files to Import',
    });
    return res.canceled ? [] : res.filePaths;
  });

  safeHandle('dialog:selectExecutable', async (e, emulatorName: string) => {
    const win = BrowserWindow.fromWebContents(e.sender) || mainWindow;
    const res = await dialog.showOpenDialog(win, {
      properties: ['openFile'],
      title: `Select ${emulatorName} Executable`,
    });
    return res.canceled ? null : res.filePaths[0];
  });

  safeHandle('dialog:selectAvatar', async (e) => {
    const win = BrowserWindow.fromWebContents(e.sender) || mainWindow;
    const res = await dialog.showOpenDialog(win, {
      properties: ['openFile'],
      filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }],
      title: 'Select Profile Picture',
    });
    if (res.canceled || !res.filePaths[0]) return null;
    const file = res.filePaths[0];
    try {
      const img = nativeImage.createFromPath(file);
      if (!img.isEmpty()) {
        const size = img.getSize();
        const side = Math.min(size.width, size.height);
        const cropped = img.crop({
          x: Math.floor((size.width - side) / 2),
          y: Math.floor((size.height - side) / 2),
          width: side,
          height: side,
        });
        const resized = cropped.resize({ width: 256, height: 256, quality: 'better' });
        const jpegBuf = resized.toJPEG(85);
        return `data:image/jpeg;base64,${jpegBuf.toString('base64')}`;
      }
    } catch {}
    const data = fs.readFileSync(file);
    const mime = file.endsWith('.png') ? 'image/png' : file.endsWith('.webp') ? 'image/webp' : 'image/jpeg';
    return `data:${mime};base64,${data.toString('base64')}`;
  });

  // --- Library Scanning & Metadata ---
  safeHandle('library:scan', async () => {
    const cfg = storage.getConfig().settings;
    const dirs = cfg.romDirectories || [];
    const files = cfg.romFiles || [];
    const items: RomItem[] = [];
    const seenPaths = new Set<string>();

    for (const dir of dirs) {
      const scanned = await scanDirectory(dir);
      for (const item of scanned) {
        if (!seenPaths.has(item.filePath)) {
          seenPaths.add(item.filePath);
          items.push(item);
        }
      }
    }

    for (const file of files) {
      if (!seenPaths.has(file)) {
        const item = await scanFile(file);
        if (item) {
          seenPaths.add(file);
          items.push(item);
        }
      }
    }

    return items;
  });

  safeHandle('library:addDirectory', async (_, dir: string) => {
    storage.addRomDirectory(dir);
    return true;
  });

  safeHandle('library:removeDirectory', async (_, dir: string) => {
    storage.removeRomDirectory(dir);
    return true;
  });

  safeHandle('library:getSpecialRomDirectory', () => {
    const specialRomDir = ensureSpecialRomDirectory();
    return {
      path: specialRomDir,
      exists: fs.existsSync(specialRomDir),
    };
  });

  safeHandle('library:openPath', async (_, targetPath?: string) => {
    const p = targetPath || ensureSpecialRomDirectory();
    if (fs.existsSync(p)) {
      await shell.openPath(p);
      return true;
    }
    return false;
  });

  safeHandle('library:importRomFiles', async (_, filePaths: string[], targetDir?: string) => {
    if (!filePaths || !Array.isArray(filePaths) || filePaths.length === 0) {
      return { success: false, count: 0, error: 'No files provided' };
    }

    const specialDir = ensureSpecialRomDirectory();
    const destDir = (targetDir && fs.existsSync(targetDir)) ? targetDir : specialDir;
    fs.mkdirSync(destDir, { recursive: true });

    const importedFiles: string[] = [];

    for (const src of filePaths) {
      if (!fs.existsSync(src)) continue;
      const fileName = path.basename(src);
      const consoleType = detectConsole(src);

      // Check if console-specific subfolder exists inside destDir
      let finalDestDir = destDir;
      if (consoleType) {
        const consoleSubDir = path.join(destDir, consoleType);
        if (fs.existsSync(consoleSubDir)) {
          finalDestDir = consoleSubDir;
        }
      }

      const destFile = path.join(finalDestDir, fileName);
      try {
        if (path.resolve(src) !== path.resolve(destFile)) {
          await fs.promises.copyFile(src, destFile);
        }
        importedFiles.push(destFile);
      } catch (err) {
        console.error(`Failed to copy ROM ${src} to ${destFile}:`, err);
      }
    }

    // Ensure destDir is in romDirectories
    storage.addRomDirectory(destDir);

    return {
      success: true,
      count: importedFiles.length,
      destination: destDir,
      importedFiles,
    };
  });

  safeHandle('library:addFile', async (_, filePath: string) => {
    storage.addRomFile(filePath);
    return await scanFile(filePath);
  });

  safeHandle('library:removeFile', async (_, filePath: string) => {
    storage.removeRomFile(filePath);
    return true;
  });

  safeHandle('library:fetchCompat', async () => {
    try {
      const url = `${coordinator.getBaseUrl()}/compat`;
      const res = await fetch(url, { signal: AbortSignal.timeout(5000) });
      if (res.ok) {
        return await res.json();
      }
    } catch {
      // Fallback
    }
    return null;
  });

  safeHandle('library:fetchMeta', async () => {
    try {
      const url = `${coordinator.getBaseUrl()}/meta`;
      const res = await fetch(url, { signal: AbortSignal.timeout(5000) });
      if (res.ok) {
        return await res.json();
      }
    } catch {
      // Fallback
    }
    return null;
  });

  // --- Coordinator Client ---
  safeHandle('coordinator:checkHealth', () => coordinator.checkHealth());
  safeHandle('coordinator:createSession', (_, params) => coordinator.createSession(params));
  safeHandle('coordinator:patchSession', (_, code, ip, port, token) =>
    coordinator.patchSession(code, ip, port, token)
  );
  safeHandle('coordinator:setHostReady', (_, code, ready, token) =>
    coordinator.setHostReady(code, ready, token)
  );
  safeHandle('coordinator:getSession', (_, code) => coordinator.getSession(code));
  safeHandle('coordinator:listRelays', () => coordinator.listRelays());
  safeHandle('coordinator:listSessions', () => coordinator.listSessions());
  safeHandle('coordinator:heartbeat', (_, code, id, name, rttMs) =>
    coordinator.heartbeat(code, id, name, rttMs)
  );
  safeHandle('coordinator:leaveSession', (_, code, id, token) =>
    coordinator.leaveSession(code, id, token)
  );
  safeHandle('coordinator:deleteSession', (_, code, token) => coordinator.deleteSession(code, token));
  safeHandle('coordinator:announcePresence', (_, id, name, inSession) =>
    coordinator.announcePresence(id, name, inSession)
  );
  safeHandle('coordinator:announceLastGame', (_, id, title, titleId) => {
    const profileName = storage.getConfig().profile?.name || null;
    return coordinator.announceLastGame(id, title, titleId, profileName);
  });
  safeHandle('coordinator:friendStatuses', (_, codes) => coordinator.friendStatuses(codes));
  safeHandle('coordinator:claimAddress', (_, code, pubKey, name, profileId) =>
    coordinator.claimAddress(code, pubKey, name, profileId)
  );
  safeHandle('coordinator:uploadAvatar', (_, id, key, imageBase64) => {
    const effectiveKey = key && key.length >= 32 ? key : storage.getConfig().profile.ownerKey || key;
    return coordinator.uploadAvatar(id, effectiveKey, imageBase64);
  });
  safeHandle('coordinator:deleteAvatar', (_, id, key) => {
    const effectiveKey = key && key.length >= 32 ? key : storage.getConfig().profile.ownerKey || key;
    return coordinator.deleteAvatar(id, effectiveKey);
  });
  safeHandle('coordinator:fetchAvatar', (_, id: string) => coordinator.fetchAvatar(id));
  safeHandle('coordinator:fetchMemberAvatar', (_, code: string, handle: string) =>
    coordinator.fetchMemberAvatar(code, handle)
  );
  safeHandle('coordinator:sendFriendRequest', (_, targetCode: string, fromCode: string, fromName: string) =>
    coordinator.sendFriendRequest(targetCode, fromCode, fromName)
  );
  safeHandle('coordinator:fetchFriendInbox', (_, myCode: string) =>
    coordinator.fetchFriendInbox(myCode)
  );
  safeHandle('coordinator:answerFriendRequest', (_, myCode: string, fromCode: string, accept: boolean, myName?: string) =>
    coordinator.answerFriendRequest(myCode, fromCode, accept, myName || storage.getConfig().profile?.name || 'Joueur')
  );
  safeHandle('coordinator:sendSessionInvite', (_, fromCode: string, toCode: string, sessionCode: string, fromName: string) =>
    coordinator.sendSessionInvite(fromCode, toCode, sessionCode, fromName)
  );
  safeHandle('coordinator:dismissSessionInvite', (_, myCode: string, fromCode: string, sessionCode: string) =>
    coordinator.dismissSessionInvite(myCode, fromCode, sessionCode)
  );
  safeHandle('coordinator:clearFriendRequestInbox', (_, myCode: string) =>
    coordinator.clearFriendRequestInbox(myCode)
  );
  safeHandle('coordinator:pingHost', (_, host: string) =>
    coordinator.pingHost(host)
  );
  safeHandle('coordinator:measureRelays', () =>
    coordinator.measureRelays()
  );
  safeHandle('coordinator:reportJoinFailure', (_, reason, consoleType, code) =>
    coordinator.reportJoinFailure(reason, consoleType, code)
  );
  safeHandle('coordinator:reportEvent', (_, params) =>
    coordinator.reportEvent(params)
  );

  // --- Crash Logs ---
  safeHandle('crashLogs:list', () => {
    try {
      const dir = path.join(app.getPath('userData'), 'crashes');
      if (!fs.existsSync(dir)) return [];
      return fs
        .readdirSync(dir)
        .filter((f) => f.startsWith('crash_') && f.endsWith('.txt'))
        .map((f) => {
          const full = path.join(dir, f);
          const stat = fs.statSync(full);
          const content = fs.readFileSync(full, 'utf8');
          return {
            name: f,
            path: full,
            mtime: stat.mtimeMs,
            size: stat.size,
            content,
          };
        })
        .sort((a, b) => b.mtime - a.mtime)
        .slice(0, 10);
    } catch {
      return [];
    }
  });
  safeHandle('crashLogs:clear', () => {
    try {
      const dir = path.join(app.getPath('userData'), 'crashes');
      if (fs.existsSync(dir)) {
        for (const f of fs.readdirSync(dir)) {
          if (f.startsWith('crash_') && f.endsWith('.txt')) {
            try {
              fs.unlinkSync(path.join(dir, f));
            } catch {}
          }
        }
      }
      return true;
    } catch {
      return false;
    }
  });
  safeHandle('crashLogs:openFolder', async () => {
    try {
      const dir = path.join(app.getPath('userData'), 'crashes');
      fs.mkdirSync(dir, { recursive: true });
      await shell.openPath(dir);
      return true;
    } catch {
      return false;
    }
  });

  // --- WireGuard ---
  safeHandle('wireguard:up', (_, info, dns) => {
    const appData = app.getPath('userData');
    const privateKey = storage.getConfig().wgKeys.privateKey;
    return wireguard.up(info, privateKey, appData, dns);
  });
  safeHandle('wireguard:down', () => wireguard.down());
  safeHandle('wireguard:status', () => wireguard.getStatus());

  // --- Emulators ---
  EmulatorManager.setOnGameExit(() => {
    try {
      if (!mainWindow.isDestroyed()) {
        mainWindow.webContents.send('emulators:game-closed');
      }
    } catch {}
  });

  safeHandle('emulators:killActiveGame', () => {
    EmulatorManager.killActiveGame();
  });
  safeHandle('emulators:detectAll', () => {
    const custom = storage.getConfig().settings.customEmulatorPaths;
    return EmulatorManager.detectAll(custom);
  });
  safeHandle('emulators:download', async (_, consoleType: string) => {
    return EmulatorDownloader.downloadEmulator(consoleType, (percent, status) => {
      mainWindow.webContents.send('emulators:download-progress', { consoleType, percent, status });
    });
  });
  safeHandle('emulators:downloadAll', async () => {
    return EmulatorDownloader.downloadAll((consoleType, percent, status) => {
      mainWindow.webContents.send('emulators:download-progress', { consoleType, percent, status });
    });
  });
  safeHandle('emulators:isDownloaded', (_, consoleType: string) => {
    return EmulatorDownloader.isDownloaded(consoleType);
  });
  safeHandle('emulators:launchGame', (_, consoleType, options) => {
    const cfg = storage.getConfig();
    const custom = cfg.settings.customEmulatorPaths[consoleType];
    const appRes = app.isPackaged
      ? path.join(process.resourcesPath)
      : path.join(__dirname, '..', '..', 'src', 'renderer', 'assets');

    if (cfg.settings.shareLastGame !== false && cfg.profile?.id && options?.romPath) {
      const fallbackName = path.basename(options.romPath, path.extname(options.romPath));
      const gameTitle = options.romTitle || options.displayName || fallbackName;
      if (gameTitle) {
        coordinator
          .announceLastGame(cfg.profile.id, gameTitle, options.titleId || null, cfg.profile.name)
          .catch(() => {});
      }
    }

    return EmulatorManager.launchGame(consoleType, {
      ...options,
      customExecutable: custom,
      appResourceDir: appRes,
    });
  });

  // --- Application Updates ---
  safeHandle('app:checkForUpdates', async () => {
    const currentVersion = app.getVersion() || '2.0.4';
    const repos = ['Noobiez16/emufii', 'jojodogm-ctrl/emufii'];
    let releaseData: any = null;

    for (const repo of repos) {
      try {
        const res = await fetch(`https://api.github.com/repos/${repo}/releases/latest`, {
          headers: { 'User-Agent': 'Emufii-Desktop' },
          signal: AbortSignal.timeout(6000),
        });
        if (res.ok) {
          const json = await res.json();
          if (json && json.tag_name) {
            releaseData = json;
            break;
          }
        }
      } catch {}
    }

    if (!releaseData) {
      return {
        ok: true,
        currentVersion,
        latestVersion: currentVersion,
        updateAvailable: false,
        releaseUrl: 'https://github.com/Noobiez16/emufii/releases',
        downloadUrl: 'https://github.com/Noobiez16/emufii/releases',
        assetName: null,
        notes: 'Your Emufii Desktop installation is up to date.',
      };
    }

    const rawTag: string = releaseData.tag_name || currentVersion;
    const latestVersion = rawTag.replace(/^v/i, '').replace(/-desktop.*$/i, '');
    const updateAvailable = isNewerSemver(currentVersion, latestVersion);
    const assets: any[] = Array.isArray(releaseData.assets) ? releaseData.assets : [];
    const platform = process.platform;

    let matchedAsset = null;
    if (platform === 'win32') {
      matchedAsset = assets.find((a) => typeof a.name === 'string' && a.name.toLowerCase().endsWith('.exe'));
    } else if (platform === 'linux') {
      matchedAsset =
        assets.find((a) => typeof a.name === 'string' && a.name.toLowerCase().endsWith('.appimage')) ||
        assets.find((a) => typeof a.name === 'string' && a.name.toLowerCase().endsWith('.deb'));
    } else if (platform === 'darwin') {
      matchedAsset = assets.find((a) => typeof a.name === 'string' && a.name.toLowerCase().endsWith('.dmg'));
    }

    return {
      ok: true,
      currentVersion,
      latestVersion,
      updateAvailable,
      releaseUrl: releaseData.html_url || 'https://github.com/Noobiez16/emufii/releases',
      downloadUrl: matchedAsset?.browser_download_url || releaseData.html_url || 'https://github.com/Noobiez16/emufii/releases',
      assetName: matchedAsset?.name || null,
      notes: releaseData.body || 'Includes the latest Emufii Desktop improvements, real-time friend status updates, and emulator configurations.',
    };
  });

  safeHandle('app:performUpdate', async (_, info: { downloadUrl?: string; assetName?: string | null; releaseUrl?: string }) => {
    const url = info?.downloadUrl || info?.releaseUrl || 'https://github.com/Noobiez16/emufii/releases';
    const assetName = info?.assetName;

    if (!assetName || !url.startsWith('http') || (!assetName.endsWith('.exe') && !assetName.endsWith('.AppImage') && !assetName.endsWith('.deb') && !assetName.endsWith('.dmg'))) {
      await shell.openExternal(info?.releaseUrl || url);
      return { success: true, openedBrowser: true };
    }

    try {
      const downloadsDir = app.getPath('downloads');
      const destPath = path.join(downloadsDir, assetName);

      mainWindow.webContents.send('app:update-progress', { percent: 5, status: `Connecting to download ${assetName}...` });

      const response = await fetch(url, {
        headers: { 'User-Agent': 'Emufii-Desktop' },
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
        const mbDone = (receivedBytes / 1024 / 1024).toFixed(1);
        if (totalBytes > 0) {
          const mbTotal = (totalBytes / 1024 / 1024).toFixed(1);
          const percent = Math.min(96, Math.round((receivedBytes / totalBytes) * 90) + 5);
          mainWindow.webContents.send('app:update-progress', {
            percent,
            status: `Downloading update (${mbDone} MB / ${mbTotal} MB)...`,
          });
        } else {
          mainWindow.webContents.send('app:update-progress', {
            percent: 55,
            status: `Downloading update (${mbDone} MB)...`,
          });
        }
      }

      await new Promise<void>((resolve, reject) => {
        fileStream.end((err?: Error | null) => (err ? reject(err) : resolve()));
      });

      if (process.platform === 'linux' && assetName.endsWith('.AppImage')) {
        try {
          fs.chmodSync(destPath, 0o755);
        } catch {}
      }

      mainWindow.webContents.send('app:update-progress', {
        percent: 100,
        status: 'Update downloaded! Opening installer...',
      });

      if (process.platform === 'win32' && assetName.endsWith('.exe')) {
        await shell.openPath(destPath);
      } else {
        shell.showItemInFolder(destPath);
      }

      return { success: true, filePath: destPath };
    } catch (err: any) {
      await shell.openExternal(info?.releaseUrl || url);
      return { success: true, openedBrowser: true, fallbackReason: err.message };
    }
  });
}
