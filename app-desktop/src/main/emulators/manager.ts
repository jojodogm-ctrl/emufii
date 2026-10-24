import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { EmulatorDownloader } from './downloader.js';

export interface EmulatorInfo {
  name: string;
  console: string;
  executablePath?: string | null;
  isInstalled: boolean;
  notes?: string;
}

export interface LaunchOptions {
  romPath: string;
  isHost: boolean;
  code: string;
  hostIp?: string | null;
  port?: number | null;
  discId?: string | null;
  ps2ElfCrc?: string | null;
  customExecutable?: string | null;
  appResourceDir?: string;
}

export class EmulatorManager {
  private static activeChild: any = null;
  private static onExitCallback: (() => void) | null = null;

  static setOnGameExit(cb: () => void) {
    this.onExitCallback = cb;
  }

  static killActiveGame() {
    if (this.activeChild && !this.activeChild.killed) {
      try {
        this.activeChild.kill();
      } catch {}
      this.activeChild = null;
    }
  }

  private static findBinary(candidates: string[]): string | null {
    for (const candidate of candidates) {
      if (candidate.startsWith('flatpak')) {
        const parts = candidate.split(' ');
        const appId = parts[parts.length - 1];
        const sysPath = path.join('/var/lib/flatpak/app', appId);
        const userPath = path.join(os.homedir(), '.local/share/flatpak/app', appId);
        if (fs.existsSync(sysPath) || fs.existsSync(userPath)) {
          return candidate;
        }
        continue;
      }
      const expanded = candidate.replace(/^~(?=$|\/|\\)/, os.homedir());
      if (fs.existsSync(expanded)) {
        return expanded;
      }
    }
    return null;
  }

  // --- PPSSPP ---
  static detectPpsspp(customPath?: string): EmulatorInfo {
    if (customPath && fs.existsSync(customPath)) {
      return { name: 'PPSSPP', console: 'PSP', executablePath: customPath, isInstalled: true };
    }

    const platform = os.platform();
    const managed = EmulatorDownloader.getManagedPath('PSP');
    let candidates: string[] = [];

    if (platform === 'linux') {
      candidates = [
        ...(managed ? [managed] : []),
        '/usr/bin/ppsspp',
        '/usr/bin/PPSSPPSDL',
        '/usr/local/bin/ppsspp',
        'flatpak run org.ppsspp.PPSSPP',
      ];
    } else if (platform === 'win32') {
      const progFiles = process.env.ProgramFiles || 'C:\\Program Files';
      const localApp = process.env.LOCALAPPDATA || 'C:\\Users\\User\\AppData\\Local';
      candidates = [
        ...(managed ? [managed] : []),
        path.join(progFiles, 'PPSSPP', 'PPSSPPWindows64.exe'),
        path.join(localApp, 'PPSSPP', 'PPSSPPWindows64.exe'),
      ];
    } else if (platform === 'darwin') {
      candidates = ['/Applications/PPSSPPSDL.app/Contents/MacOS/PPSSPPSDL'];
    }

    const found = this.findBinary(candidates);
    return {
      name: 'PPSSPP',
      console: 'PSP',
      executablePath: found,
      isInstalled: !!found,
      notes: 'Auto-configures ad hoc networking and proAdhocServer=10.66.1.1',
    };
  }

  static getPpssppMemstickDir(): string {
    const platform = os.platform();
    const home = os.homedir();
    if (platform === 'linux') {
      return path.join(home, '.config', 'ppsspp', 'PSP');
    } else if (platform === 'win32') {
      const docs = process.env.USERPROFILE
        ? path.join(process.env.USERPROFILE, 'Documents', 'PPSSPP', 'PSP')
        : path.join(home, 'Documents', 'PPSSPP', 'PSP');
      return docs;
    } else {
      return path.join(home, 'Library', 'Application Support', 'PPSSPP', 'PSP');
    }
  }

  static async configurePpsspp(discId: string): Promise<string | null> {
    const memstickDir = this.getPpssppMemstickDir();
    const systemDir = path.join(memstickDir, 'SYSTEM');
    try {
      fs.mkdirSync(systemDir, { recursive: true });
      const cleanDiscId = discId.replace(/[-_.]/g, '').toUpperCase();
      const iniPath = path.join(systemDir, `${cleanDiscId}_ppsspp.ini`);

      let content = '';
      if (fs.existsSync(iniPath)) {
        content = fs.readFileSync(iniPath, 'utf8');
      }

      // Merge or update [Network] section
      const networkConfig = [
        '[Network]',
        'EnableWlan = True',
        'EnableAdhocServer = True',
        'proAdhocServer = 10.66.1.1',
        'AdhocServerRelayMode = 2',
      ].join('\n');

      if (content.includes('[Network]')) {
        content = content.replace(
          /\[Network\][\s\S]*?(?=\n\[|$)/,
          networkConfig
        );
      } else {
        content = content + '\n\n' + networkConfig + '\n';
      }

      fs.writeFileSync(iniPath, content, 'utf8');
      return iniPath;
    } catch {
      return null;
    }
  }

  // --- PCSX2 ---
  static detectPcsx2(customPath?: string): EmulatorInfo {
    if (customPath && fs.existsSync(customPath)) {
      return { name: 'PCSX2', console: 'PS2', executablePath: customPath, isInstalled: true };
    }

    const platform = os.platform();
    const managed = EmulatorDownloader.getManagedPath('PS2');
    let candidates: string[] = [];

    if (platform === 'linux') {
      candidates = [
        ...(managed ? [managed] : []),
        '/usr/bin/pcsx2-qt',
        '/usr/bin/pcsx2',
        '/usr/local/bin/pcsx2-qt',
        'flatpak run net.pcsx2.PCSX2',
      ];
    } else if (platform === 'win32') {
      const progFiles = process.env.ProgramFiles || 'C:\\Program Files';
      const localApp = process.env.LOCALAPPDATA || 'C:\\Users\\User\\AppData\\Local';
      candidates = [
        ...(managed ? [managed] : []),
        path.join(progFiles, 'PCSX2', 'pcsx2-qt.exe'),
        path.join(localApp, 'Programs', 'PCSX2', 'pcsx2-qt.exe'),
      ];
    } else if (platform === 'darwin') {
      candidates = ['/Applications/PCSX2.app/Contents/MacOS/PCSX2'];
    }

    const found = this.findBinary(candidates);
    return {
      name: 'PCSX2',
      console: 'PS2',
      executablePath: found,
      isInstalled: !!found,
      notes: 'Auto-configures DEV9 Local Link and bundles EmuFii_Network.ps2',
    };
  }

  static getPcsx2ConfigDir(): string {
    const platform = os.platform();
    const home = os.homedir();
    if (platform === 'linux') {
      const flatpakDir = path.join(home, '.var', 'app', 'net.pcsx2.PCSX2', 'config', 'PCSX2');
      if (fs.existsSync(flatpakDir)) return flatpakDir;
      return path.join(home, '.config', 'PCSX2');
    } else if (platform === 'win32') {
      const appdata = process.env.APPDATA || path.join(home, 'AppData', 'Roaming');
      return path.join(appdata, 'PCSX2');
    } else {
      return path.join(home, 'Library', 'Application Support', 'PCSX2');
    }
  }

  static async configurePcsx2(
    serial: string,
    crc: string,
    isHost: boolean,
    code: string,
    appResourceDir?: string
  ): Promise<string | null> {
    const configDir = this.getPcsx2ConfigDir();
    const gamesettingsDir = path.join(configDir, 'inis', 'gamesettings');
    const memcardsDir = path.join(configDir, 'memcards');

    try {
      fs.mkdirSync(gamesettingsDir, { recursive: true });
      fs.mkdirSync(memcardsDir, { recursive: true });

      // Copy PS2 network memory card if bundled
      const targetCard = path.join(memcardsDir, 'EmuFii_Network.ps2');
      if (!fs.existsSync(targetCard) && appResourceDir) {
        const srcCard = path.join(appResourceDir, 'ps2', 'emufii-ps2-net.ps2');
        if (fs.existsSync(srcCard)) {
          fs.copyFileSync(srcCard, targetCard);
        }
      }

      let cleanSerial = serial.trim().toUpperCase();
      if (/^[A-Z]{4}\d{5}$/.test(cleanSerial)) {
        cleanSerial = `${cleanSerial.slice(0, 4)}-${cleanSerial.slice(4)}`;
      }
      const cleanCrc = crc.toUpperCase();
      const iniFileName = `${cleanSerial}_${cleanCrc}.ini`;
      const iniPath = path.join(gamesettingsDir, iniFileName);

      const lines = [
        '[DEV9/Eth]',
        'EthEnable = true',
        'EthApi = Local Link',
        `LocalLinkHost = ${isHost ? 'true' : 'false'}`,
        ...(isHost ? [] : ['LocalLinkAddress = emufii']),
        'LocalLinkPort = 19072',
        `LocalLinkRoomCode = ${code.toUpperCase()}`,
        '',
        '[MemoryCards]',
        'Slot1_Enable = true',
        'Slot1_Filename = EmuFii_Network.ps2',
      ];

      fs.writeFileSync(iniPath, lines.join('\n') + '\n', 'utf8');
      return iniPath;
    } catch {
      return null;
    }
  }

  // --- Dolphin ---
  static detectDolphin(customPath?: string): EmulatorInfo {
    if (customPath && fs.existsSync(customPath)) {
      return { name: 'Dolphin', console: 'GameCube / Wii', executablePath: customPath, isInstalled: true };
    }
    const platform = os.platform();
    const managed = EmulatorDownloader.getManagedPath('GameCube');
    let candidates: string[] = [];

    if (platform === 'linux') {
      candidates = [
        ...(managed ? [managed] : []),
        '/usr/bin/dolphin-emu',
        '/usr/local/bin/dolphin-emu',
        'flatpak run org.DolphinEmu.dolphin-emu',
      ];
    } else if (platform === 'win32') {
      const progFiles = process.env.ProgramFiles || 'C:\\Program Files';
      const localApp = process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local');
      candidates = [
        ...(managed ? [managed] : []),
        path.join(progFiles, 'Dolphin-x64', 'Dolphin.exe'),
        path.join(progFiles, 'Dolphin', 'Dolphin.exe'),
        path.join(localApp, 'Dolphin', 'Dolphin.exe'),
      ];
    } else if (platform === 'darwin') {
      candidates = ['/Applications/Dolphin.app/Contents/MacOS/Dolphin'];
    }

    const found = this.findBinary(candidates);
    return {
      name: 'Dolphin',
      console: 'GameCube / Wii',
      executablePath: found,
      isInstalled: !!found,
      notes: 'Launches GameCube and Wii games directly with Netplay connection credentials',
    };
  }

  // --- melonDS ---
  static detectMelonDs(customPath?: string): EmulatorInfo {
    if (customPath && fs.existsSync(customPath)) {
      return { name: 'melonDS', console: 'DS', executablePath: customPath, isInstalled: true };
    }
    const platform = os.platform();
    const managed = EmulatorDownloader.getManagedPath('DS');
    let candidates: string[] = [];

    if (platform === 'linux') {
      candidates = [
        ...(managed ? [managed] : []),
        '/usr/bin/melonds',
        '/usr/bin/watermelonds',
        'flatpak run net.kuribo64.melonDS',
      ];
    } else if (platform === 'win32') {
      const progFiles = process.env.ProgramFiles || 'C:\\Program Files';
      const localApp = process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local');
      candidates = [
        ...(managed ? [managed] : []),
        path.join(progFiles, 'melonDS', 'melonDS.exe'),
        path.join(localApp, 'melonDS', 'melonDS.exe'),
      ];
    } else if (platform === 'darwin') {
      candidates = ['/Applications/melonDS.app/Contents/MacOS/melonDS'];
    }

    const found = this.findBinary(candidates);
    return {
      name: 'melonDS',
      console: 'DS',
      executablePath: found,
      isInstalled: !!found,
      notes: 'Supports direct local wireless or Kaeru WFC multiplayer',
    };
  }

  // --- Azahar / Citra / Lime3DS ---
  static detectAzahar(customPath?: string): EmulatorInfo {
    if (customPath && fs.existsSync(customPath)) {
      return { name: 'Azahar / Citra', console: '3DS', executablePath: customPath, isInstalled: true };
    }
    const platform = os.platform();
    const managed = EmulatorDownloader.getManagedPath('3DS');
    let candidates: string[] = [];

    if (platform === 'linux') {
      candidates = [
        ...(managed ? [managed] : []),
        '/usr/bin/azahar',
        '/usr/bin/lime3ds',
        '/usr/bin/citra-qt',
        'flatpak run io.github.lime3ds.Lime3DS',
      ];
    } else if (platform === 'win32') {
      const local = process.env.LOCALAPPDATA || 'C:\\Users\\User\\AppData\\Local';
      const progFiles = process.env.ProgramFiles || 'C:\\Program Files';
      candidates = [
        ...(managed ? [managed] : []),
        path.join(local, 'Azahar', 'azahar.exe'),
        path.join(local, 'Azahar', 'azahar-qt.exe'),
        path.join(progFiles, 'Azahar', 'azahar.exe'),
        path.join(local, 'Citra', 'citra-qt.exe'),
        path.join(local, 'Lime3DS', 'lime3ds-qt.exe'),
      ];
    } else if (platform === 'darwin') {
      candidates = ['/Applications/Lime3DS.app/Contents/MacOS/lime3ds-qt'];
    }

    const found = this.findBinary(candidates);
    return {
      name: 'Azahar / Citra',
      console: '3DS',
      executablePath: found,
      isInstalled: !!found,
      notes: 'Multiplayer room host & client support',
    };
  }

  // --- Eden / Ryujinx / Yuzu ---
  static detectEden(customPath?: string): EmulatorInfo {
    if (customPath && fs.existsSync(customPath)) {
      return { name: 'Eden / Ryujinx', console: 'Switch', executablePath: customPath, isInstalled: true };
    }
    const platform = os.platform();
    let candidates: string[] = [];

    if (platform === 'linux') {
      const managed = EmulatorDownloader.getManagedPath('Switch');
      candidates = [
        ...(managed ? [managed] : []),
        '/usr/bin/eden',
        'flatpak run dev.eden_emu.eden',
        '/usr/bin/ryujinx',
        'flatpak run io.github.ryubing.Ryujinx',
        'flatpak run org.ryujinx.Ryujinx',
      ];
    } else if (platform === 'win32') {
      const progFiles = process.env.ProgramFiles || 'C:\\Program Files';
      const localApp = process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local');
      candidates = [
        path.join(progFiles, 'Ryujinx', 'Ryujinx.exe'),
        path.join(progFiles, 'Eden', 'eden.exe'),
        path.join(localApp, 'Ryujinx', 'Ryujinx.exe'),
        path.join(localApp, 'Eden', 'eden.exe'),
      ];
    } else if (platform === 'darwin') {
      candidates = [
        '/Applications/Ryujinx.app/Contents/MacOS/Ryujinx',
        '/Applications/Eden.app/Contents/MacOS/Eden',
        '/Applications/yuzu.app/Contents/MacOS/yuzu',
      ];
    }

    const found = this.findBinary(candidates);
    return {
      name: 'Eden / Ryujinx',
      console: 'Switch',
      executablePath: found,
      isInstalled: !!found,
      notes: 'Switch Local Wireless / LDN multiplayer',
    };
  }

  static getConnectedGamepad(): string | null {
    try {
      if (os.platform() === 'linux') {
        const devPath = '/proc/bus/input/devices';
        if (fs.existsSync(devPath)) {
          const content = fs.readFileSync(devPath, 'utf8');
          const sections = content.split('\n\n');
          for (const sec of sections) {
            if (/Handlers=.*(js\d+)/.test(sec)) {
              const nameMatch = sec.match(/N:\s*Name="([^"]+)"/);
              if (nameMatch) {
                return nameMatch[1];
              }
            }
          }
        }
      }
    } catch {}
    return null;
  }

  static configureDolphinControllers(gamepadName?: string | null): void {
    try {
      const dolphinDir = path.join(os.homedir(), '.config', 'dolphin-emu');
      fs.mkdirSync(dolphinDir, { recursive: true });

      // 1. Configure Dolphin.ini with [Controls] and [Core] to activate Emulated Wiimote and GCPad
      const dolphinIniPath = path.join(dolphinDir, 'Dolphin.ini');
      let dolphinIni = fs.existsSync(dolphinIniPath) ? fs.readFileSync(dolphinIniPath, 'utf8') : '';

      const controlsBlock = [
        '[Controls]',
        'WiimoteSource0 = 1',
        'WiimoteSource1 = 0',
        'WiimoteSource2 = 0',
        'WiimoteSource3 = 0',
        'PadType0 = 6',
        'PadType1 = 0',
        'PadType2 = 0',
        'PadType3 = 0',
      ].join('\n');

      if (dolphinIni.includes('[Controls]')) {
        dolphinIni = dolphinIni.replace(/\[Controls\][\s\S]*?(?=\n\[|$)/, controlsBlock);
      } else {
        dolphinIni = dolphinIni.trim() + '\n\n' + controlsBlock + '\n';
      }

      if (dolphinIni.includes('[Core]')) {
        if (!dolphinIni.includes('CPUThread')) {
          dolphinIni = dolphinIni.replace('[Core]', '[Core]\nCPUThread = True\nFastmem = True\nDSPHLE = True\nSyncGPU = False');
        }
        if (!dolphinIni.includes('WiimoteSource0')) {
          dolphinIni = dolphinIni.replace('[Core]', '[Core]\nWiimoteSource0 = 1\nSIDevice0 = 6');
        }
      } else {
        dolphinIni += '\n[Core]\nCPUThread = True\nFastmem = True\nDSPHLE = True\nSyncGPU = False\nWiimoteSource0 = 1\nSIDevice0 = 6\n';
      }

      fs.writeFileSync(dolphinIniPath, dolphinIni, 'utf8');

      // 1b. GFX.ini performance optimization (Asynchronous Ubershaders for 60 FPS without shader stutter)
      const gfxIniPath = path.join(dolphinDir, 'GFX.ini');
      let gfxIni = fs.existsSync(gfxIniPath) ? fs.readFileSync(gfxIniPath, 'utf8') : '';
      if (!gfxIni.includes('[Settings]')) {
        gfxIni += '\n[Settings]\nShaderCompilationMode = 2\nWaitForShadersBeforeStarting = False\n';
        fs.writeFileSync(gfxIniPath, gfxIni, 'utf8');
      } else if (!gfxIni.includes('ShaderCompilationMode')) {
        gfxIni = gfxIni.replace('[Settings]', '[Settings]\nShaderCompilationMode = 2\nWaitForShadersBeforeStarting = False');
        fs.writeFileSync(gfxIniPath, gfxIni, 'utf8');
      }

      const devName = gamepadName || 'StadiaP6KS-64d4';
      const evdevDevice = `evdev/0/${devName}`;

      // 2. WiimoteNew.ini
      const wiimotePath = path.join(dolphinDir, 'WiimoteNew.ini');
      const wiimoteContent = [
        '[Wiimote1]',
        `Device = ${evdevDevice}`,
        'Buttons/A = `SOUTH` | `Button A` | `Button 3` | `Button 0` | `Click 1` | A',
        'Buttons/B = `EAST` | `Button B` | `Trigger R` | `Right Shoulder` | `TR` | `Button 4` | `Button 1` | `Click 3` | B',
        'Buttons/1 = `WEST` | `Button X` | `Button 6` | `Button 2` | `1` | X',
        'Buttons/2 = `NORTH` | `Button Y` | `Button 5` | `Button 3` | `2` | Y',
        'Buttons/- = `SELECT` | `Back` | `Button 6` | `Select` | Q',
        'Buttons/+ = `START` | `Start` | `Button 7` | E',
        'Buttons/Home = `MODE` | `Guide` | Return',
        'D-Pad/Up = `Axis 1-` | `Axis 7-` | `BtnDPadUp` | `DPad Up` | Up',
        'D-Pad/Down = `Axis 1+` | `Axis 7+` | `BtnDPadDown` | `DPad Down` | Down',
        'D-Pad/Left = `Axis 0-` | `Axis 6-` | `BtnDPadLeft` | `DPad Left` | Left',
        'D-Pad/Right = `Axis 0+` | `Axis 6+` | `BtnDPadRight` | `DPad Right` | Right',
        'IR/Up = `Axis 4-` | `Right Y-` | `Cursor Y-`',
        'IR/Down = `Axis 4+` | `Right Y+` | `Cursor Y+`',
        'IR/Left = `Axis 3-` | `Right X-` | `Cursor X-`',
        'IR/Right = `Axis 3+` | `Right X+` | `Cursor X+`',
        'Shake/X = `TR` | `Right Shoulder` | `Button 5` | `Click 2`',
        'Shake/Y = `TR` | `Right Shoulder` | `Button 5` | `Click 2`',
        'Shake/Z = `TR` | `Right Shoulder` | `Button 5` | `Click 2`',
        'Extension = Nunchuk',
        'Nunchuk/Buttons/C = `TL` | `Left Shoulder` | `Button 7` | `Button 4` | Control_L',
        'Nunchuk/Buttons/Z = `Axis 2+` | `Trigger L` | `Button 6` | Shift_L',
        'Nunchuk/Stick/Up = `Axis 1-` | `Left Y-` | W',
        'Nunchuk/Stick/Down = `Axis 1+` | `Left Y+` | S',
        'Nunchuk/Stick/Left = `Axis 0-` | `Left X-` | A',
        'Nunchuk/Stick/Right = `Axis 0+` | `Left X+` | D',
        '',
        '[Wiimote2]',
        'Device = XInput2/0/Virtual core pointer',
        '[Wiimote3]',
        'Device = XInput2/0/Virtual core pointer',
        '[Wiimote4]',
        'Device = XInput2/0/Virtual core pointer',
      ].join('\n');
      fs.writeFileSync(wiimotePath, wiimoteContent + '\n', 'utf8');

      // 3. GCPadNew.ini
      const gcPadPath = path.join(dolphinDir, 'GCPadNew.ini');
      const gcPadContent = [
        '[GCPad1]',
        `Device = ${evdevDevice}`,
        'Buttons/A = `SOUTH` | `Button A` | `Button 3` | `Button 0` | `X`',
        'Buttons/B = `EAST` | `Button B` | `Button 4` | `Button 1` | `Z`',
        'Buttons/X = `WEST` | `Button X` | `Button 6` | `Button 2` | `C`',
        'Buttons/Y = `NORTH` | `Button Y` | `Button 5` | `Button 3` | `S`',
        'Buttons/Z = `TR` | `Right Shoulder` | `Button 8` | `Button 5` | `D`',
        'Buttons/Start = `START` | `Start` | `Button 7` | `Return`',
        'Main Stick/Up = `Axis 1-` | `Left Y-` | `Up`',
        'Main Stick/Down = `Axis 1+` | `Left Y+` | `Down`',
        'Main Stick/Left = `Axis 0-` | `Left X-` | `Left`',
        'Main Stick/Right = `Axis 0+` | `Left X+` | `Right`',
        'Main Stick/Modifier = `Shift`',
        'Main Stick/Calibration = 100.00 141.42 100.00 141.42 100.00 141.42 100.00 141.42',
        'C-Stick/Up = `Axis 4-` | `Right Y-` | `I`',
        'C-Stick/Down = `Axis 4+` | `Right Y+` | `K`',
        'C-Stick/Left = `Axis 3-` | `Right X-` | `J`',
        'C-Stick/Right = `Axis 3+` | `Right X+` | `L`',
        'C-Stick/Modifier = `Ctrl`',
        'C-Stick/Calibration = 100.00 141.42 100.00 141.42 100.00 141.42 100.00 141.42',
        'Triggers/L = `TL` | `Left Shoulder` | `Axis 2+` | `Trigger L` | `Q`',
        'Triggers/R = `TR` | `Right Shoulder` | `Axis 5+` | `Trigger R` | `W`',
        'D-Pad/Up = `Axis 7-` | `DPad Up` | `T`',
        'D-Pad/Down = `Axis 7+` | `DPad Down` | `G`',
        'D-Pad/Left = `Axis 6-` | `DPad Left` | `F`',
        'D-Pad/Right = `Axis 6+` | `DPad Right` | `H`',
        '',
        '[GCPad2]',
        'Device = XInput2/0/Virtual core pointer',
        '[GCPad3]',
        'Device = XInput2/0/Virtual core pointer',
        '[GCPad4]',
        'Device = XInput2/0/Virtual core pointer',
      ].join('\n');
      fs.writeFileSync(gcPadPath, gcPadContent + '\n', 'utf8');
    } catch {}
  }

  static configureMgbaGamepad(): void {
    try {
      const mgbaDir = path.join(os.homedir(), '.config', 'mgba');
      fs.mkdirSync(mgbaDir, { recursive: true });
      const configPath = path.join(mgbaDir, 'config.ini');
      let content = fs.existsSync(configPath) ? fs.readFileSync(configPath, 'utf8') : '';

      const sdlbSection = [
        '[gba.input.SDLB]',
        'keyA=0',
        'keyB=1',
        'keySelect=6',
        'keyStart=7',
        'keyRight=14',
        'keyLeft=13',
        'keyUp=11',
        'keyDown=12',
        'keyR=5',
        'keyL=4',
        'hat0Up=6',
        'hat0Down=7',
        'hat0Left=5',
        'hat0Right=4',
        'axisLeftAxis=0',
        'axisLeftValue=-16384',
        'axisRightAxis=0',
        'axisRightValue=16384',
        'axisUpAxis=1',
        'axisUpValue=-16384',
        'axisDownAxis=1',
        'axisDownValue=16384',
      ].join('\n');

      if (content.includes('[gba.input.SDLB]')) {
        content = content.replace(/\[gba\.input\.SDLB\][\s\S]*?(?=\n\[|$)/, sdlbSection);
      } else {
        content = content.trim() + '\n\n' + sdlbSection + '\n';
      }

      fs.writeFileSync(configPath, content, 'utf8');
    } catch {}
  }

  static configureMelonDsGamepad(): void {
    try {
      const melonDir = path.join(os.homedir(), '.config', 'melonDS');
      fs.mkdirSync(melonDir, { recursive: true });
      const tomlPath = path.join(melonDir, 'melonDS.toml');
      let content = fs.existsSync(tomlPath) ? fs.readFileSync(tomlPath, 'utf8') : '';

      if (!content.includes('JoystickID')) {
        content = 'JoystickID = 0\n' + content;
      } else {
        content = content.replace(/JoystickID\s*=\s*\d+/g, 'JoystickID = 0');
      }

      const joystickBlock = [
        '[Joystick]',
        'A = 0',
        'B = 1',
        'Select = 6',
        'Start = 7',
        'Right = 258',
        'Left = 264',
        'Up = 257',
        'Down = 260',
        'R = 5',
        'L = 4',
        'X = 2',
        'Y = 3',
      ].join('\n');

      if (content.includes('[Joystick]')) {
        content = content.replace(/\[Joystick\][\s\S]*?(?=\n\[|$)/, joystickBlock);
      } else {
        content = content.trim() + '\n\n' + joystickBlock + '\n';
      }

      fs.writeFileSync(tomlPath, content, 'utf8');
    } catch {}
  }

  static configurePcsx2Gamepad(): void {
    try {
      const configDir = this.getPcsx2ConfigDir();
      const iniPath = path.join(configDir, 'inis', 'PCSX2.ini');
      if (!fs.existsSync(iniPath)) return;
      let content = fs.readFileSync(iniPath, 'utf8');

      if (content.includes('[Pad1]')) {
        const pad1Config = [
          '[Pad1]',
          'Type = DualShock2',
          'InvertL = 0',
          'InvertR = 0',
          'Deadzone = 0',
          'AxisScale = 1.33',
          'LargeMotorScale = 1',
          'SmallMotorScale = 1',
          'ButtonDeadzone = 0',
          'PressureModifier = 0.5',
          'Up = SDL-0/DPadUp',
          'Right = SDL-0/DPadRight',
          'Down = SDL-0/DPadDown',
          'Left = SDL-0/DPadLeft',
          'Triangle = SDL-0/Y',
          'Circle = SDL-0/B',
          'Cross = SDL-0/A',
          'Square = SDL-0/X',
          'Select = SDL-0/Back',
          'Start = SDL-0/Start',
          'L1 = SDL-0/LeftShoulder',
          'L2 = SDL-0/+TriggerLeft',
          'R1 = SDL-0/RightShoulder',
          'R2 = SDL-0/+TriggerRight',
          'L3 = SDL-0/LeftStick',
          'R3 = SDL-0/RightStick',
          'LUp = SDL-0/-LeftY',
          'LRight = SDL-0/+LeftX',
          'LDown = SDL-0/+LeftY',
          'LLeft = SDL-0/-LeftX',
          'RUp = SDL-0/-RightY',
          'RRight = SDL-0/+RightX',
          'RDown = SDL-0/+RightY',
          'RLeft = SDL-0/-RightX',
        ].join('\n');
        content = content.replace(/\[Pad1\][\s\S]*?(?=\n\[|$)/, pad1Config);
        fs.writeFileSync(iniPath, content, 'utf8');
      }
    } catch {}
  }

  static configureMgbaEscape(): void {
    try {
      const mgbaDir = path.join(os.homedir(), '.config', 'mgba');
      fs.mkdirSync(mgbaDir, { recursive: true });
      const qtIniPath = path.join(mgbaDir, 'qt.ini');
      let content = '';
      if (fs.existsSync(qtIniPath)) {
        content = fs.readFileSync(qtIniPath, 'utf8');
      }

      // Ensure maximized=true so when exiting fullscreen it enters Window-Fullscreen
      if (content.includes('maximized=')) {
        content = content.replace(/maximized=false/g, 'maximized=true');
      } else if (content.includes('[General]')) {
        content = content.replace('[General]', '[General]\nmaximized=true');
      } else {
        content = '[General]\nmaximized=true\n\n' + content;
      }

      // Ensure [shortcutKey] fullscreen=Esc
      if (content.includes('[shortcutKey]')) {
        if (!content.includes('fullscreen=')) {
          content = content.replace('[shortcutKey]', '[shortcutKey]\nfullscreen=Esc');
        } else {
          content = content.replace(/fullscreen=[^\n]*/g, 'fullscreen=Esc');
        }
      } else {
        content += '\n[shortcutKey]\nfullscreen=Esc\n';
      }

      fs.writeFileSync(qtIniPath, content, 'utf8');
    } catch {}
  }

  static configureMelonDsEscape(): void {
    try {
      const melonDir = path.join(os.homedir(), '.config', 'melonDS');
      fs.mkdirSync(melonDir, { recursive: true });
      const tomlPath = path.join(melonDir, 'melonDS.toml');
      let content = '';
      if (fs.existsSync(tomlPath)) {
        content = fs.readFileSync(tomlPath, 'utf8');
      }

      // HK_FullscreenToggle = 16777216 is Qt Key_Escape
      if (content.includes('HK_FullscreenToggle')) {
        content = content.replace(/HK_FullscreenToggle\s*=\s*\d+/g, 'HK_FullscreenToggle = 16777216');
      } else if (content.includes('[Keyboard]')) {
        content = content.replace('[Keyboard]', '[Keyboard]\nHK_FullscreenToggle = 16777216');
      } else {
        content += '\n[Keyboard]\nHK_FullscreenToggle = 16777216\n';
      }

      fs.writeFileSync(tomlPath, content, 'utf8');
    } catch {}
  }

  // --- mGBA ---
  static detectMgba(customPath?: string): EmulatorInfo {
    if (customPath && fs.existsSync(customPath)) {
      return { name: 'mGBA', console: 'GBA', executablePath: customPath, isInstalled: true };
    }
    const platform = os.platform();
    const managed = EmulatorDownloader.getManagedPath('GBA');
    let candidates: string[] = [];

    if (platform === 'linux') {
      candidates = [
        ...(managed ? [managed] : []),
        '/usr/bin/mgba-qt',
        '/usr/bin/mgba',
        '/usr/local/bin/mgba-qt',
        '/usr/local/bin/mgba',
        'flatpak run io.mgba.mGBA',
      ];
    } else if (platform === 'win32') {
      const progFiles = process.env.ProgramFiles || 'C:\\Program Files';
      const localApp = process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local');
      candidates = [
        ...(managed ? [managed] : []),
        path.join(progFiles, 'mGBA', 'mGBA.exe'),
        path.join(progFiles, 'mGBA', 'mgba.exe'),
        path.join(localApp, 'mGBA', 'mGBA.exe'),
      ];
    } else if (platform === 'darwin') {
      candidates = ['/Applications/mGBA.app/Contents/MacOS/mGBA'];
    }

    const found = this.findBinary(candidates);
    return {
      name: 'mGBA',
      console: 'GBA',
      executablePath: found,
      isInstalled: !!found,
      notes: 'Game Boy Advance multiplayer link cable support',
    };
  }

  static detectAll(customPaths: Record<string, string> = {}): Record<string, EmulatorInfo> {
    return {
      PSP: this.detectPpsspp(customPaths.PSP),
      PS2: this.detectPcsx2(customPaths.PS2),
      GameCube: this.detectDolphin(customPaths.GameCube),
      Wii: this.detectDolphin(customPaths.Wii),
      DS: this.detectMelonDs(customPaths.DS),
      GBA: this.detectMgba(customPaths.GBA),
      '3DS': this.detectAzahar(customPaths['3DS']),
      Switch: this.detectEden(customPaths.Switch),
    };
  }

  /**
   * Launch game in the designated emulator, applying auto-configurations.
   */
  static async launchGame(consoleType: string, options: LaunchOptions): Promise<{ success: boolean; message: string }> {
    let emuInfo: EmulatorInfo;

    switch (consoleType) {
      case 'PSP':
        emuInfo = this.detectPpsspp(options.customExecutable || undefined);
        if (options.discId) {
          await this.configurePpsspp(options.discId);
        }
        break;
      case 'PS2':
        emuInfo = this.detectPcsx2(options.customExecutable || undefined);
        if (options.discId && options.ps2ElfCrc) {
          await this.configurePcsx2(
            options.discId,
            options.ps2ElfCrc,
            options.isHost,
            options.code,
            options.appResourceDir
          );
        }
        break;
      case 'GameCube':
      case 'Wii':
        emuInfo = this.detectDolphin(options.customExecutable || undefined);
        break;
      case 'DS':
        if (options.romPath.toLowerCase().endsWith('.gba') || options.romPath.toLowerCase().endsWith('.agb')) {
          emuInfo = this.detectMgba(options.customExecutable || undefined);
          if (!emuInfo.executablePath) {
            emuInfo = this.detectMelonDs(options.customExecutable || undefined);
          }
        } else {
          emuInfo = this.detectMelonDs(options.customExecutable || undefined);
        }
        break;
      case 'GBA':
        emuInfo = this.detectMgba(options.customExecutable || undefined);
        break;
      case '3DS':
        emuInfo = this.detectAzahar(options.customExecutable || undefined);
        break;
      case 'Switch':
        emuInfo = this.detectEden(options.customExecutable || undefined);
        break;
      default:
        return { success: false, message: `Unknown console: ${consoleType}` };
    }

    if (!emuInfo.executablePath) {
      return {
        success: false,
        message: `${emuInfo.name} executable not found. Please install it or set its path in Settings.`,
      };
    }

    try {
      const gamepad = this.getConnectedGamepad();

      // Build console-specific arguments including automatic fullscreen
      const args: string[] = [];

      switch (consoleType) {
        case 'DS':
          if (emuInfo.name === 'mGBA') {
            this.configureMgbaEscape();
            this.configureMgbaGamepad();
          } else {
            this.configureMelonDsEscape();
            this.configureMelonDsGamepad();
          }
          args.push('-f', options.romPath);
          break;
        case 'GBA':
          // mGBA: -f / --fullscreen with Escape toggle and gamepad configured
          this.configureMgbaEscape();
          this.configureMgbaGamepad();
          args.push('-f', options.romPath);
          break;
        case 'PSP':
          // PPSSPP: --fullscreen
          args.push('--fullscreen', options.romPath);
          break;
        case 'PS2':
          // PCSX2: -batch -fullscreen with gamepad configured
          this.configurePcsx2Gamepad();
          args.push('-batch', '-fullscreen', options.romPath);
          break;
        case 'GameCube':
        case 'Wii':
          // Dolphin: -C Dolphin.Display.Fullscreen=True -e <rom> with gamepad configured
          this.configureDolphinControllers(gamepad);
          args.push('-C', 'Dolphin.Display.Fullscreen=True', '-e', options.romPath);
          break;
        case '3DS':
          // Azahar / Citra: -f
          args.push('-f', options.romPath);
          break;
        case 'Switch':
          if (emuInfo.executablePath.toLowerCase().includes('eden') || emuInfo.executablePath.toLowerCase().includes('yuzu')) {
            args.push('-f', '-g', options.romPath);
          } else {
            args.push('--fullscreen', options.romPath);
          }
          break;
        default:
          args.push(options.romPath);
      }

      // Kill any previous game process if still active
      this.killActiveGame();

      let child: any;
      if (emuInfo.executablePath.startsWith('flatpak')) {
        const flatpakArgs = emuInfo.executablePath.split(' ').slice(1);
        child = spawn('flatpak', [...flatpakArgs, ...args], { stdio: 'ignore' });
      } else {
        child = spawn(emuInfo.executablePath, args, { stdio: 'ignore' });
      }

      this.activeChild = child;

      child.on('exit', () => {
        this.activeChild = null;
        this.onExitCallback?.();
      });

      child.on('error', () => {
        this.activeChild = null;
        this.onExitCallback?.();
      });

      return {
        success: true,
        message: `Launched ${emuInfo.name} in Fullscreen with ${path.basename(options.romPath)}`,
      };
    } catch (err: any) {
      return { success: false, message: `Failed to launch ${emuInfo.name}: ${err.message}` };
    }
  }
}
