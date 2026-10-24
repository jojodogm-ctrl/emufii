import { store, ScreenType, AppState } from './js/state.js';
import type { RomItem } from '../main/library/scanner.js';
import { sfx } from './js/sfx.js';
import { nav } from './js/gamepad.js';
import { renderSplashScreen } from './js/screens/SplashScreen.js';
import { renderLibraryScreen } from './js/screens/LibraryScreen.js';
import { renderJoinScreen } from './js/screens/JoinScreen.js';
import { renderSessionScreen } from './js/screens/SessionScreen.js';
import { renderFriendsScreen } from './js/screens/FriendsScreen.js';
import { renderSessionFinderScreen } from './js/screens/SessionFinderScreen.js';
import { renderSettingsScreen, resetSettingsView } from './js/screens/SettingsScreen.js';
import { openSettingsModal, resolveTheme } from './js/components/SettingsModal.js';
import {
  toggleFloatingPanel,
  closeFloatingPanel,
  isFloatingPanelOpen,
  getFloatingPanelTab,
  renderFloatingPanel,
  refreshRelayLatencies,
  updateHeaderVpsLabel,
} from './js/components/FloatingPanel.js';
import { TrayBackdrop } from './js/components/TrayBackdrop.js';
import { syncFriendsRealtime, encodeAvatarToWebpBase64 } from './js/components/FriendActionModal.js';
import { openUpdateModal } from './js/components/UpdateModal.js';

const emuApi = (window as any).emufii;

let cleanupCurrentScreen: (() => void) | null = null;
let currentRenderedScreen: ScreenType | null = null;
let trayBackdrop: TrayBackdrop | null = null;

async function bootstrap() {
  // 1. Initialize sound engine
  await sfx.init();

  // 2. Initialize navigation
  nav.init({
    onBack: () => {
      sfx.play('click');
      if (isFloatingPanelOpen()) {
        closeFloatingPanel();
        return;
      }
      const detailBackBtn = document.getElementById('btnDetailBack');
      if (detailBackBtn) {
        detailBackBtn.click();
        return;
      }
      const dialog = document.querySelector('dialog[open]') as HTMLDialogElement;
      if (dialog) {
        dialog.close();
      } else if (store.get().currentScreen === 'library' && store.get().consoleFilter !== 'ALL') {
        store.set({ consoleFilter: 'ALL' });
      } else {
        store.goBack();
      }
    },
  });

  // 3. Window Controls
  document.getElementById('btnWinMinimize')?.addEventListener('click', () => {
    emuApi.window.minimize();
  });
  document.getElementById('btnWinMaximize')?.addEventListener('click', () => {
    emuApi.window.maximize();
  });
  document.getElementById('btnWinClose')?.addEventListener('click', () => {
    emuApi.window.close();
  });

  const updateMaximizeUI = (isMax: boolean) => {
    document.body.classList.toggle('is-maximized', isMax);
    const maxBtn = document.getElementById('btnWinMaximize');
    if (maxBtn) {
      maxBtn.title = isMax ? 'Restore Down' : 'Maximize';
      maxBtn.innerHTML = isMax
        ? `<svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1"><rect x="2.5" y="0.5" width="7" height="7"/><rect x="0.5" y="2.5" width="7" height="7"/></svg>`
        : `<svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1"><rect x="0.5" y="0.5" width="9" height="9"/></svg>`;
    }
    trayBackdrop?.updateDimensions();
    window.dispatchEvent(new Event('resize'));
  };

  emuApi.window.onStateChanged?.(({ maximized }: { maximized: boolean }) => {
    updateMaximizeUI(maximized);
  });

  window.addEventListener('resize', () => {
    trayBackdrop?.updateDimensions();
  });

  emuApi.window.isMaximized?.().then((isMax: boolean) => {
    if (isMax) updateMaximizeUI(true);
  }).catch(() => { });

  // 4. Header Navigation Buttons -> Floating Cloud Panel
  document.getElementById('headerFinderBtn')?.addEventListener('click', () => {
    sfx.play('click');
    toggleFloatingPanel('sessions');
  });

  document.getElementById('headerJoinBtn')?.addEventListener('click', () => {
    sfx.play('click');
    toggleFloatingPanel('join');
  });

  document.getElementById('headerFriendsBtn')?.addEventListener('click', () => {
    sfx.play('click');
    toggleFloatingPanel('friends');
  });

  document.getElementById('headerFolderBackBtn')?.addEventListener('click', () => {
    sfx.play('click');
    store.set({ consoleFilter: 'ALL' });
  });

  document.getElementById('headerProfileBtn')?.addEventListener('click', () => {
    sfx.play('click');
    toggleFloatingPanel('profile');
  });

  document.getElementById('headerStatusDot')?.addEventListener('click', () => {
    sfx.play('click');
    toggleFloatingPanel('profile');
  });

  document.getElementById('vpsStatusChip')?.addEventListener('click', () => {
    sfx.play('click');
    toggleFloatingPanel('server');
  });

  document.getElementById('headerSettingsBtn')?.addEventListener('click', () => {
    sfx.play('click');
    closeFloatingPanel();
    if (store.get().currentScreen === 'settings') {
      store.goBack();
    } else {
      store.navigate('settings');
    }
  });

  // Close floating panel on backdrop click
  document.getElementById('floatingPanelBackdrop')?.addEventListener('click', () => {
    closeFloatingPanel();
  });

  // Global Drag & Drop for ROM files and folders
  window.addEventListener('dragover', (e) => {
    e.preventDefault();
    e.stopPropagation();
  });

  window.addEventListener('drop', async (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (!e.dataTransfer?.files?.length) return;

    let addedAny = false;
    for (let i = 0; i < e.dataTransfer.files.length; i++) {
      const file = e.dataTransfer.files[i];
      const filePath = (file as any).path;
      if (!filePath) continue;

      try {
        const item = await emuApi.library.addFile(filePath);
        if (item) {
          addedAny = true;
          continue;
        }
      } catch {
        // ignore
      }

      try {
        await emuApi.library.addDirectory(filePath);
        addedAny = true;
      } catch {
        // ignore
      }
    }

    if (addedAny) {
      sfx.play('click');
      const scanned = await emuApi.library.scan();
      store.set({ roms: scanned });
    }
  });

  // 5. Load Config from disk
  const config = await emuApi.config.get();
  const cachedSgdbKey = localStorage.getItem('emufii_sgdb_key');
  if (!config.settings.steamGridDbKey && cachedSgdbKey) {
    config.settings.steamGridDbKey = cachedSgdbKey;
    emuApi.config.updateSettings({ steamGridDbKey: cachedSgdbKey });
  } else if (config.settings.steamGridDbKey) {
    localStorage.setItem('emufii_sgdb_key', config.settings.steamGridDbKey);
  }

  sfx.setEnabled(config.settings.soundEffects !== false);

  document.body.dataset.theme = resolveTheme(config.settings.theme || 'dark');
  document.body.dataset.accent = config.settings.accent || 'teal';

  // Listen to OS system color-scheme changes when theme is set to 'system'
  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      const currentSetting = store.get().settings.theme;
      if (currentSetting === 'system') {
        document.body.dataset.theme = resolveTheme('system');
        trayBackdrop?.update();
      }
    });
  }

  // Initialize Animated Signature Tray Backdrop
  const backdropCanvas = document.getElementById('trayBackdropCanvas') as HTMLCanvasElement;
  if (backdropCanvas) {
    trayBackdrop = new TrayBackdrop(backdropCanvas);
  }

  store.set({
    profile: config.profile,
    settings: config.settings,
  });

  updateHeader(store.get());

  // 6. Background metadata & library scan
  loadMetadataAndScan();

  // 7. Dynamic Coordinator Connection Lamp Check
  checkCoordinatorStatus();
  setInterval(checkCoordinatorStatus, 30000);

  // Periodic background presence announcement (matching EmufiiApp.kt PRESENCE_MS = 45s)
  const syncPresence = async () => {
    const s = store.get();
    if (s.currentScreen === 'session' || s.activeSession) return;
    if (s.profile.id && s.profile.name) {
      try {
        await emuApi.coordinator.announcePresence(s.profile.id, s.profile.name, false);
      } catch { }
    }
  };
  syncPresence();
  setInterval(syncPresence, 45000);

  // Sync user's own avatar to coordinator on startup if set
  if (config.profile?.id && config.profile?.avatarDataUrl) {
    encodeAvatarToWebpBase64(config.profile.avatarDataUrl)
      .then((webpBase64) => {
        if (webpBase64) {
          emuApi.coordinator
            .uploadAvatar(
              config.profile.id,
              config.profile.ownerKey || config.profile.id,
              webpBase64
            )
            .catch(() => {});
        }
      })
      .catch(() => {});
  }

  // Real-time friend status & friend request polling (every 5 seconds)
  const pollFriendsRealtime = () => {
    syncFriendsRealtime(() => {
      if (isFloatingPanelOpen() && getFloatingPanelTab() === 'friends') {
        renderFloatingPanel();
      }
    }).catch(() => {});
  };
  pollFriendsRealtime();
  setInterval(pollFriendsRealtime, 5000);

  // Auto-check for updates on launch if Auto-Download Updates is enabled
  if (config.settings?.autoDownloadUpdates !== false && emuApi?.updates?.check) {
    setTimeout(() => {
      emuApi.updates
        .check()
        .then((res: any) => {
          if (res?.updateAvailable) {
            openUpdateModal(res);
          }
        })
        .catch(() => {});
    }, 3500);
  }

  // 8. Subscribe to state changes and render screens
  let lastFilter = store.get().consoleFilter;
  let lastRomsCount = store.get().roms.length;
  let lastQuery = store.get().searchQuery;
  let lastLayout = store.get().settings.libraryLayout;
  let lastSort = store.get().settings.librarySort;

  store.subscribe((state) => {
    updateHeader(state);

    const filterChanged = lastFilter !== state.consoleFilter;
    const romsChanged = lastRomsCount !== state.roms.length;
    const queryChanged = lastQuery !== state.searchQuery;
    const layoutChanged = lastLayout !== state.settings.libraryLayout;
    const sortChanged = lastSort !== state.settings.librarySort;

    lastFilter = state.consoleFilter;
    lastRomsCount = state.roms.length;
    lastQuery = state.searchQuery;
    lastLayout = state.settings.libraryLayout;
    lastSort = state.settings.librarySort;

    const resolvedTheme = resolveTheme(state.settings.theme || 'dark');
    if (resolvedTheme !== document.body.dataset.theme) {
      document.body.dataset.theme = resolvedTheme;
      trayBackdrop?.update();
    }

    if (currentRenderedScreen !== state.currentScreen) {
      currentRenderedScreen = state.currentScreen;
      renderCurrentScreen(state.currentScreen, true);
    } else if (
      currentRenderedScreen === 'library' &&
      (filterChanged || romsChanged || queryChanged || layoutChanged || sortChanged)
    ) {
      renderCurrentScreen('library', false);
    }
  });

  // Start with Splash Screen
  renderCurrentScreen('splash', true);
}

function updateHeader(state: AppState) {
  const avatarEl = document.getElementById('headerProfileAvatar');
  const dotEl = document.getElementById('headerStatusDot');
  const subtitleEl = document.getElementById('headerSubtitle');
  const titleEl = document.getElementById('headerTitle');
  const countEl = document.getElementById('headerCount');
  const backBtn = document.getElementById('headerFolderBackBtn');

  if (avatarEl) {
    if (state.profile.avatarDataUrl) {
      avatarEl.innerHTML = `<img src="${state.profile.avatarDataUrl}" style="width: 100%; height: 100%; border-radius: 50%; object-fit: cover;">`;
    } else {
      avatarEl.textContent = (state.profile.name || 'J').slice(0, 2).toUpperCase();
    }
  }

  if (dotEl) {
    const presence = localStorage.getItem('emufii_presence') || 'online';
    dotEl.className = `user-status-dot ${presence}`;
  }

  if (subtitleEl) {
    const friendsList = state.settings.friends || [];
    const onlineCount = friendsList.filter((code) => !!state.friendStatuses?.present?.[code]).length;
    if (friendsList.length === 0) {
      subtitleEl.textContent = 'No friends online';
    } else if (onlineCount > 0) {
      subtitleEl.textContent = `${onlineCount} ${onlineCount === 1 ? 'friend' : 'friends'} online`;
    } else {
      subtitleEl.textContent = `0 of ${friendsList.length} friends online`;
    }
  }

  // When inside a session (Create a Session or Play Online), remove the "< Library" button
  if (state.currentScreen === 'session') {
    if (titleEl) {
      titleEl.textContent = state.activeSession?.rom?.displayName || (state.activeSession?.code === 'ONLINE' ? 'Online' : 'Session');
    }
    if (countEl) {
      countEl.textContent = state.activeSession?.code === 'ONLINE' ? 'Online play' : (state.activeSession?.isHost ? 'Host' : 'Guest');
    }
    if (backBtn) {
      backBtn.style.display = 'none';
    }
    return;
  }

  const isInFolder = state.currentScreen === 'library' && state.consoleFilter !== 'ALL';
  const totalRoms = state.roms.filter((r: RomItem) => !state.settings.hiddenRoms?.includes(r.id));
  const displayedCount = isInFolder
    ? totalRoms.filter((r: RomItem) => r.console === state.consoleFilter).length
    : totalRoms.length;
  const countLabel = `${displayedCount} ${displayedCount === 1 ? 'game' : 'games'}`;

  if (titleEl) {
    titleEl.textContent = isInFolder ? state.consoleFilter : 'Library';
  }
  if (countEl) {
    countEl.textContent = countLabel;
  }
  if (backBtn) {
    backBtn.style.display = isInFolder ? 'inline-flex' : 'none';
  }
}

async function loadMetadataAndScan() {
  try {
    const compat = await emuApi.library.fetchCompat();
    if (compat?.games) {
      const map: Record<string, any> = {};
      for (const g of compat.games) {
        for (const k of g.keys || []) {
          map[k] = g;
        }
      }
      store.set({ compatDb: map });
    }

    const meta = await emuApi.library.fetchMeta();
    if (meta?.games) {
      const map: Record<string, any> = {};
      for (const g of meta.games) {
        for (const k of g.keys || []) {
          map[k] = g;
        }
      }
      store.set({ metaDb: map });
    }

    // Scan ROMs
    const scanned = await emuApi.library.scan();
    store.set({ roms: scanned });
  } catch (err) {
    console.error('Scan failed:', err);
  }
}

function renderCurrentScreen(screen: ScreenType, animate: boolean = true) {
  if (cleanupCurrentScreen) {
    cleanupCurrentScreen();
    cleanupCurrentScreen = null;
  }

  // Toggle header status band: hidden on splash or settings, visible on launcher
  const headerBand = document.getElementById('appHeaderBand');
  if (headerBand) {
    headerBand.style.display = (screen === 'splash' || screen === 'settings') ? 'none' : 'flex';
  }

  const titlebarBrand = document.querySelector<HTMLElement>('.titlebar-brand');
  if (titlebarBrand) {
    titlebarBrand.style.visibility = screen === 'splash' ? 'hidden' : 'visible';
  }

  const host = document.getElementById('screenHost');
  if (!host) return;

  let targetView: HTMLElement;

  if (animate) {
    const currentView = document.getElementById('currentScreenView');
    if (currentView) {
      currentView.id = 'previousScreenView';
      currentView.classList.add('screen-exiting');
      setTimeout(() => {
        currentView.remove();
      }, 220);
    }

    const newView = document.createElement('div');
    newView.className = 'screen screen-entering';
    newView.id = 'currentScreenView';
    host.appendChild(newView);
    targetView = newView;

    // Trigger entering animation
    requestAnimationFrame(() => {
      newView.classList.remove('screen-entering');
    });

    // Focus the first action element
    setTimeout(() => {
      const firstFocusable = newView.querySelector<HTMLElement>(
        'button, input, .rom-tile, .chip, .keypad-btn'
      );
      if (firstFocusable) {
        nav.setFocus(firstFocusable, false);
      }
    }, 100);
  } else {
    let currentView = document.getElementById('currentScreenView');
    if (!currentView) {
      currentView = document.createElement('div');
      currentView.className = 'screen';
      currentView.id = 'currentScreenView';
      host.appendChild(currentView);
    }
    if (screen !== 'library') {
      currentView.innerHTML = '';
    }
    targetView = currentView;
  }

  // Render matching component
  switch (screen) {
    case 'splash':
      cleanupCurrentScreen = renderSplashScreen(targetView) || null;
      break;
    case 'library':
      cleanupCurrentScreen = renderLibraryScreen(targetView) || null;
      break;
    case 'join':
      cleanupCurrentScreen = renderJoinScreen(targetView) || null;
      break;
    case 'session':
      cleanupCurrentScreen = renderSessionScreen(targetView) || null;
      break;
    case 'friends':
      renderFriendsScreen(targetView);
      break;
    case 'finder':
      renderSessionFinderScreen(targetView);
      break;
    case 'settings':
      if (animate) {
        resetSettingsView();
      }
      renderSettingsScreen(targetView);
      break;
  }
}

function getRegionLabel(): string {
  const region = localStorage.getItem('emufii_server_region') || 'auto';
  const regionNames: Record<string, string> = {
    auto: 'US East (NY)',
    use: 'US East (NY)',
    usw: 'US West (Seattle)',
    eu: 'Europe',
    na: 'Canada (Montréal)',
    jp: 'Japan (Tokyo)',
  };
  return regionNames[region] || 'US East (NY)';
}

async function checkCoordinatorStatus() {
  const lamp = document.getElementById('vpsLamp');
  const chip = document.getElementById('vpsStatusChip');
  const text = document.getElementById('vpsText');
  const regLabel = getRegionLabel();
  try {
    const health = await emuApi.coordinator.checkHealth();
    const isOnline = !!health?.ok;
    if (lamp) {
      lamp.className = isOnline ? 'lamp active' : 'lamp';
    }
    if (text) {
      text.innerHTML = isOnline ? `Online &bull; ${regLabel}` : `Offline &bull; ${regLabel}`;
    }
    if (chip) {
      chip.title = isOnline
        ? `Coordinator Online${health.present ? ` (${health.present} players online)` : ''}`
        : 'Coordinator Offline';
    }
    if (isOnline) {
      refreshRelayLatencies().catch(() => {});
    }
  } catch {
    if (lamp) {
      lamp.className = 'lamp';
    }
    if (text) {
      text.innerHTML = `Offline &bull; ${regLabel}`;
    }
    if (chip) {
      chip.title = 'Cannot connect to Coordinator';
    }
  }
}

// Bootstrap on DOM ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', bootstrap);
} else {
  bootstrap();
}

