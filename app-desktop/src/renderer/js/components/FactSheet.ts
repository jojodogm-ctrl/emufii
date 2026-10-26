import { store } from '../state.js';
import { sfx } from '../sfx.js';
import { getSelectedRegion } from './FloatingPanel.js';
import { ensureEmulator } from './EmulatorDownloadModal.js';
import { generateSessionCode } from '../../../main/network/sessionCodes.js';
import { toast, errorMessage } from '../toast.js';
import type { RomItem } from '../../../main/library/scanner.js';

export function openFactSheet(rom: RomItem) {
  store.set({ selectedRom: rom });
  if (store.get().currentScreen !== 'game') store.navigate('game');
}

export async function launchOnline(rom: RomItem) {
  const emuApi = (window as any).emufii;

  // Ensure emulator is ready (auto-downloading if missing)
  const isReady = await ensureEmulator(rom.console);
  if (!isReady) return;

  store.set({
    activeSession: {
      code: 'ONLINE',
      isHost: true,
      rom,
      token: null,
      remote: null,
      wireguard: null,
      wireguardConfigPath: null,
      wireguardNative: true,
      step1Done: true,
      step2Done: false,
      launching: true,
      netplayBusy: false,
    },
  });

  store.navigate('session');

  try {
    const res = await emuApi.emulators.launchGame(rom.console, {
      romPath: rom.filePath,
      isHost: true,
      code: 'ONLINE',
      onlineMode: true,
      discId: rom.discId || rom.titleId,
      ps2ElfCrc: rom.ps2ElfCrc,
    });

    const active = store.get().activeSession;
    if (active && active.code === 'ONLINE') {
      store.set({
        activeSession: {
          ...active,
          step2Done: res?.success === true,
          launching: false,
          netplayBusy: false,
        },
      });
    }

    if (res && !res.success) {
      toast(res.message || 'Emulator not found. Set its path in Settings › Emulators.', 'error');
    }
  } catch (err: any) {
    console.error('Failed to launch online:', err);
    toast(`Could not start online play: ${errorMessage(err, 'unknown error')}`, 'error');
    const active = store.get().activeSession;
    if (active && active.code === 'ONLINE') {
      store.set({
        activeSession: {
          ...active,
          netplayBusy: false,
        },
      });
    }
  }
}

export async function startHostSession(rom: RomItem, isPrivate: boolean = false) {
  // Ensure emulator is ready (auto-downloading if missing)
  const isReady = await ensureEmulator(rom.console);
  if (!isReady) return;

  const code = generateSessionCode();
  const profile = store.get().profile;
  const emuApi = (window as any).emufii;

  // Default netplay port matching Console.kt backend.defaultNetplayPort:
  // Dolphin: 2626, PS2: 19072, MelonDS: 7000, 3DS/Switch: 24872
  const defaultPort =
    rom.console === 'GameCube' || rom.console === 'Wii'
      ? 2626
      : rom.console === 'PS2'
      ? 19072
      : rom.console === 'DS'
      ? 7000
      : 24872;

  store.set({
    activeSession: {
      code,
      isHost: true,
      rom,
      token: null,
      remote: null,
      wireguard: null,
      wireguardConfigPath: null,
      wireguardNative: true,
      step1Done: false,
      step2Done: false,
      netplayBusy: true,
    },
  });

  store.navigate('session');

  try {
    // 1. Create session on coordinator
    const created = await emuApi.coordinator.createSession({
      code,
      romTitleId: rom.titleId || rom.productCode,
      romTitle: rom.displayName,
      hostName: profile.name,
      hostId: profile.id,
      console: rom.console.toLowerCase(),
      isPrivate,
      region: getSelectedRegion(),
    });

    const active = store.get().activeSession;
    if (active && active.code === code) {
      store.set({
        activeSession: {
          ...active,
          token: created.token,
        },
      });
    }

    // 2. Claim WireGuard address
    const config = await emuApi.config.get();
    const wgInfo = await emuApi.coordinator.claimAddress(
      code,
      config.wgKeys.publicKey,
      profile.name,
      profile.id
    );

    // 3. Bring up WireGuard tunnel (PS2 uses relay DNS for local hostname resolution)
    const dns = rom.console === 'PS2' ? '10.67.0.1' : null;
    const wgRes = await emuApi.wireguard.up(wgInfo, dns);

    // 4. Update session with host IP and proper backend netplay port
    await emuApi.coordinator.patchSession(code, wgInfo.address, defaultPort, created.token);
    await emuApi.coordinator.setHostReady(code, true, created.token);

    // Step 1 done
    sfx.play('confirm');
    const updated = store.get().activeSession;
    if (updated && updated.code === code) {
      store.set({
        activeSession: {
          ...updated,
          wireguard: wgInfo,
          wireguardConfigPath: wgRes?.configPath || null,
          wireguardNative: wgRes?.isNativeActive !== false,
          step1Done: true,
          netplayBusy: false,
        },
      });
    }

    // 5. Auto launch emulator for host
    const preLaunch = store.get().activeSession;
    if (preLaunch && preLaunch.code === code) {
      store.set({ activeSession: { ...preLaunch, launching: true } });
    }

    if (store.get().settings.shareLastGame !== false) {
      emuApi.coordinator.announceLastGame(profile.id, rom.displayName, rom.titleId || rom.productCode).catch(() => {});
    }

    const res = await emuApi.emulators.launchGame(rom.console, {
      romPath: rom.filePath,
      isHost: true,
      code,
      hostIp: wgInfo.address,
      port: defaultPort,
      discId: rom.discId || rom.titleId,
      ps2ElfCrc: rom.ps2ElfCrc,
    });

    const step2 = store.get().activeSession;
    if (step2 && step2.code === code) {
      store.set({
        activeSession: {
          ...step2,
          step2Done: res?.success === true,
          launching: false,
        },
      });
    }

    if (res && !res.success) {
      toast(res.message || 'Emulator not found. Set its path in Settings › Emulators, then click Launch.', 'error');
    }
  } catch (err: any) {
    console.error('Failed to host session:', err);
    toast(`Could not host the session: ${errorMessage(err, 'unknown error')}`, 'error');
    const active = store.get().activeSession;
    if (active && active.code === code) {
      store.set({
        activeSession: {
          ...active,
          netplayBusy: false,
        },
      });
    }
  }
}
