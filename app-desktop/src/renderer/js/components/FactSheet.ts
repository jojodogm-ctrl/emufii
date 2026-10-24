import { store } from '../state.js';
import { sfx } from '../sfx.js';
import { paletteFor, shortLabel } from '../screens/LibraryScreen.js';
import { toggleFloatingPanel } from './FloatingPanel.js';
import { ensureEmulator } from './EmulatorDownloadModal.js';
import { generateSessionCode } from '../../../main/network/sessionCodes.js';
import type { RomItem } from '../../../main/library/scanner.js';

export function openFactSheet(rom: RomItem) {
  const dialog = document.getElementById('factSheetDialog') as HTMLDialogElement;
  if (!dialog) return;

  const compat = store.get().compatDb[rom.titleId || ''] || store.get().compatDb[rom.productCode || ''];
  const rating = compat?.rating || 'perfect';

  const [c1, c2] = paletteFor(rom.displayName);
  const initials = shortLabel(rom.displayName);

  let currentMode: 'wireless' | 'online' = 'wireless';
  let isPrivate = false;
  const hasModes = rom.console === 'PSP' || rom.console === 'DS' || rom.console === 'PS2';

  const dialogContent = dialog.querySelector('.dialog-content') as HTMLElement;
  dialogContent.innerHTML = `
    <div class="game-launch-modal">
      <button id="btnCloseLaunchDialog" class="icon-btn plate-button launch-close-btn" title="Close">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="18" y1="6" x2="6" y2="18"></line>
          <line x1="6" y1="6" x2="18" y2="18"></line>
        </svg>
      </button>

      <!-- Left Column: Artwork + Compat Badge -->
      <div class="launch-left-col">
        <div class="launch-artwork">
          ${
            rom.artworkUrl
              ? `<img src="${rom.artworkUrl}" data-fallback="${(rom as any).fallbackArtworkUrl || ''}" class="launch-art-img" alt="${rom.displayName}" loading="lazy" onerror="if (this.dataset.fallback && this.src !== this.dataset.fallback) { this.src = this.dataset.fallback; } else { this.style.display='none'; const p = this.nextElementSibling; if (p) p.style.display='flex'; }">`
              : ''
          }
          <div class="launch-art-placeholder" style="background: linear-gradient(135deg, ${c1}, ${c2}); ${rom.artworkUrl ? 'display: none;' : 'display: flex;'} align-items: center; justify-content: center; width: 100%; height: 100%; border-radius: 14px;">
            <span style="font-size: 44px; font-weight: 900; color: white; letter-spacing: 2px;">${initials}</span>
          </div>
        </div>

        <div class="launch-compat-pill">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="#1FA98B" stroke-width="2.2" fill="none"/>
            <path d="M7.5 12l3 3 6-6" stroke="#1FA98B" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span>${rating === 'broken' ? 'Incompatible' : rating === 'partial' ? 'Playable' : 'Compatible'}</span>
        </div>
      </div>

      <!-- Right Column: Mode Switch, Privacy Toggle, Launch Buttons -->
      <div class="launch-right-col">
        <div class="launch-title-block">
          <div class="launch-game-title">${rom.displayName}</div>
          <div class="launch-game-subtitle" id="launchGameSubtitle">${rom.console} &bull; multiplayer session</div>
        </div>

        ${
          hasModes
            ? `
        <!-- Mode Switch (Wireless play / Online) -->
        <div class="launch-mode-switch" id="launchModeSwitch">
          <div class="launch-mode-glider" id="launchModeGlider"></div>
          <button class="launch-mode-tab active" id="btnModeWireless" type="button">Wireless play</button>
          <button class="launch-mode-tab" id="btnModeOnline" type="button">Online</button>
        </div>
        `
            : ''
        }

        <!-- Mode View Container -->
        <div class="launch-mode-body" id="launchModeBody">
          <!-- Privacy Toggle (Collapsible with smooth grid height) -->
          <div class="launch-collapsible" id="launchPrivacyCollapsible">
            <div>
              <div class="launch-privacy-row" id="launchPrivacyRow">
                <div>
                  <div class="launch-privacy-title" id="launchPrivacyTitle">Open session</div>
                  <div class="launch-privacy-desc" id="launchPrivacyDesc">Shows in the session list. Anyone can join.</div>
                </div>
                <button class="launch-switch" id="launchPrivacySwitch" role="switch" aria-checked="false" title="Toggle Private Session">
                  <span class="launch-switch-thumb"></span>
                </button>
              </div>
            </div>
          </div>

          <!-- Actions Stack -->
          <div class="launch-actions-stack">
            <button class="launch-btn-primary plate-button" id="btnLaunchPrimary">
              Create a session
            </button>
            <div class="launch-collapsible" id="launchSecondaryCollapsible">
              <div>
                <button class="launch-btn-secondary plate-button" id="btnLaunchSecondary">
                  Join with a code
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;

  const subtitleEl = dialogContent.querySelector('#launchGameSubtitle') as HTMLElement;
  const modeSwitchEl = dialogContent.querySelector('#launchModeSwitch') as HTMLElement | null;
  const btnWireless = dialogContent.querySelector('#btnModeWireless') as HTMLElement | null;
  const btnOnline = dialogContent.querySelector('#btnModeOnline') as HTMLElement | null;
  const privacyCollapsible = dialogContent.querySelector('#launchPrivacyCollapsible') as HTMLElement;
  const secondaryCollapsible = dialogContent.querySelector('#launchSecondaryCollapsible') as HTMLElement;
  const privacyRow = dialogContent.querySelector('#launchPrivacyRow') as HTMLElement;
  const privacyTitle = dialogContent.querySelector('#launchPrivacyTitle') as HTMLElement;
  const privacyDesc = dialogContent.querySelector('#launchPrivacyDesc') as HTMLElement;
  const privacySwitch = dialogContent.querySelector('#launchPrivacySwitch') as HTMLElement;
  const btnPrimary = dialogContent.querySelector('#btnLaunchPrimary') as HTMLElement;
  const btnSecondary = dialogContent.querySelector('#btnLaunchSecondary') as HTMLElement;

  const updateView = (skipAnim: boolean = false) => {
    if (currentMode === 'wireless') {
      modeSwitchEl?.classList.remove('mode-online');
      btnWireless?.classList.add('active');
      btnOnline?.classList.remove('active');
      if (skipAnim) {
        subtitleEl.innerHTML = `${rom.console} &bull; multiplayer session`;
      } else {
        subtitleEl.style.opacity = '0';
        setTimeout(() => {
          subtitleEl.innerHTML = `${rom.console} &bull; multiplayer session`;
          subtitleEl.style.opacity = '1';
        }, 120);
      }
      privacyCollapsible.classList.remove('collapsed');
      secondaryCollapsible.classList.remove('collapsed');
      btnPrimary.textContent = 'Create a session';

      if (isPrivate) {
        privacyTitle.textContent = 'Private session';
        privacyDesc.textContent = 'Hidden from the session list. Your code lets people in.';
        privacySwitch.classList.add('checked');
        privacySwitch.setAttribute('aria-checked', 'true');
      } else {
        privacyTitle.textContent = 'Open session';
        privacyDesc.textContent = 'Shows in the session list. Anyone can join.';
        privacySwitch.classList.remove('checked');
        privacySwitch.setAttribute('aria-checked', 'false');
      }
    } else {
      modeSwitchEl?.classList.add('mode-online');
      btnWireless?.classList.remove('active');
      btnOnline?.classList.add('active');
      if (skipAnim) {
        subtitleEl.innerHTML = `${rom.console} &bull; online play`;
      } else {
        subtitleEl.style.opacity = '0';
        setTimeout(() => {
          subtitleEl.innerHTML = `${rom.console} &bull; online play`;
          subtitleEl.style.opacity = '1';
        }, 120);
      }
      privacyCollapsible.classList.add('collapsed');
      secondaryCollapsible.classList.add('collapsed');
      btnPrimary.textContent = 'Play online';
    }
  };

  btnWireless?.addEventListener('click', () => {
    if (currentMode !== 'wireless') {
      sfx.play('toggle');
      currentMode = 'wireless';
      updateView();
    }
  });

  btnOnline?.addEventListener('click', () => {
    if (currentMode !== 'online') {
      sfx.play('toggle');
      currentMode = 'online';
      updateView();
    }
  });

  privacyRow.addEventListener('click', () => {
    sfx.play('toggle');
    isPrivate = !isPrivate;
    updateView();
  });

  btnPrimary.addEventListener('click', async () => {
    sfx.play('confirm');
    dialog.close();
    if (currentMode === 'wireless') {
      await startHostSession(rom, isPrivate);
    } else {
      await launchOnline(rom);
    }
  });

  btnSecondary.addEventListener('click', () => {
    sfx.play('click');
    dialog.close();
    store.set({ selectedRom: rom });
    toggleFloatingPanel('join');
  });

  dialogContent.querySelector('#btnCloseLaunchDialog')?.addEventListener('click', () => {
    sfx.play('click');
    dialog.close();
  });

  dialog.onclick = (e) => {
    if (e.target === dialog) {
      sfx.play('click');
      dialog.close();
    }
  };

  dialog.showModal();
}

async function launchOnline(rom: RomItem) {
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
      alert(res.message || 'Emulator executable not found. Please configure its path in Settings.');
    }
  } catch (err: any) {
    console.error('Failed to launch online:', err);
    alert('Failed to launch online: ' + (err.message || err));
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

async function startHostSession(rom: RomItem, isPrivate: boolean = false) {
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
      alert(res.message || 'Emulator executable not found. Please configure its path in Settings, then click Launch.');
    }
  } catch (err: any) {
    console.error('Failed to host session:', err);
    alert('Failed to host session: ' + (err.message || err));
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
