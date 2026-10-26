import { store, ScreenType, AppState } from './js/state.js';
import type { RomItem } from '../main/library/scanner.js';
import { sfx } from './js/sfx.js';
import { nav, isEditable } from './js/navigation.js';
import { renderSplashScreen } from './js/screens/SplashScreen.js';
import {
  renderLibraryScreen,
  setLibraryLayout,
  consoleGroups,
  paletteFor,
  getConsoleFriendlyName,
  getConsoleSlug,
  addRomFiles,
  addRomFolder,
} from './js/screens/LibraryScreen.js';
import { renderSessionScreen } from './js/screens/SessionScreen.js';
import { renderSettingsScreen, resetSettingsView } from './js/screens/SettingsScreen.js';
import { resolveTheme } from './js/theme.js';
import {
  toggleFloatingPanel,
  closeFloatingPanel,
  isFloatingPanelOpen,
  getFloatingPanelTab,
  renderFloatingPanel,
  refreshRelayLatencies,
  getPresence,
} from './js/components/FloatingPanel.js';
import { TrayBackdrop } from './js/components/TrayBackdrop.js';
import { syncFriendsRealtime, encodeAvatarToWebpBase64 } from './js/components/FriendActionModal.js';
import { openUpdateModal } from './js/components/UpdateModal.js';
import { closeContextMenu } from './js/contextMenu.js';
import { toast } from './js/toast.js';
import { withTransition } from './js/motion.js';
import { esc } from './js/html.js';
import { renderGameScreen } from './js/screens/GameScreen.js';
import { renderPanelPage } from './js/screens/PanelPages.js';
import { openContextMenuAt } from './js/contextMenu.js';

const emuApi = (window as any).emufii;

let cleanupCurrentScreen: (() => void) | null = null;
let currentRenderedScreen: ScreenType | null = null;
let trayBackdrop: TrayBackdrop | null = null;

function goBack() {
  if (closeContextMenu()) return;
  if (document.querySelector('.modal-backdrop')) return;
  if (isFloatingPanelOpen()) {
    closeFloatingPanel();
    return;
  }
  const dialog = document.querySelector<HTMLDialogElement>('dialog[open]');
  if (dialog) {
    sfx.play('click');
    dialog.close();
    return;
  }

  const state = store.get();
  if (state.currentScreen === 'session' || state.currentScreen === 'splash') return;
  if (state.currentScreen === 'library') {
    if (state.searchQuery) {
      store.set({ searchQuery: '' });
    } else if (state.consoleFilter !== 'ALL') {
      sfx.play('click');
      store.set({ consoleFilter: 'ALL' });
    }
    return;
  }
  sfx.play('click');
  if (!store.goBack()) store.navigate('library');
}

function toggleSettings() {
  closeFloatingPanel();
  if (store.get().currentScreen === 'settings') {
    store.goBack();
  } else if (store.get().currentScreen !== 'splash') {
    store.navigate('settings');
  }
}

function focusLibrarySearch() {
  const focus = () => {
    const input = document.getElementById('librarySearch') as HTMLInputElement | null;
    input?.focus();
    input?.select();
  };
  closeFloatingPanel();
  if (store.get().currentScreen === 'library') {
    focus();
  } else if (store.get().currentScreen === 'settings') {
    store.goBack();
    setTimeout(focus, 260);
  }
}

function handleShortcuts(e: KeyboardEvent) {
  if (e.defaultPrevented || document.querySelector('.modal-backdrop, dialog[open]')) return;
  const mod = e.ctrlKey || e.metaKey;
  const screen = store.get().currentScreen;
  if (screen === 'splash') return;

  if (mod && e.shiftKey && e.key.toLowerCase() === 'f') {
    e.preventDefault();
    toggleFloatingPanel('friends');
  } else if (mod && e.key.toLowerCase() === 'f') {
    e.preventDefault();
    focusLibrarySearch();
  } else if (e.key === '/' && !isEditable(e.target as Element)) {
    e.preventDefault();
    focusLibrarySearch();
  } else if (mod && e.key === ',') {
    e.preventDefault();
    toggleSettings();
  } else if (mod && e.key.toLowerCase() === 'j') {
    e.preventDefault();
    toggleFloatingPanel('join');
  } else if (mod && e.key.toLowerCase() === 'o') {
    e.preventDefault();
    toggleFloatingPanel('sessions');
  } else if (mod && ['1', '2', '3'].includes(e.key) && screen === 'library') {
    e.preventDefault();
    setLibraryLayout((['grid', 'carousel', 'list'] as const)[Number(e.key) - 1]);
  } else if (e.key === 'F5') {
    e.preventDefault();
    rescanLibrary(true);
  }
}

async function rescanLibrary(announce = false) {
  try {
    const scanned = await emuApi.library.scan();
    store.set({ roms: scanned });
    if (announce) toast(`Library refreshed · ${scanned.length} ${scanned.length === 1 ? 'game' : 'games'}`, 'success', 2500);
  } catch (err) {
    console.error('Scan failed:', err);
    if (announce) toast('Could not scan your ROM folders.', 'error');
  }
}

function setupDragAndDrop() {
  let dragDepth = 0;
  const overlay = document.getElementById('dropOverlay');

  const hasFiles = (e: DragEvent) => Array.from(e.dataTransfer?.types || []).includes('Files');

  window.addEventListener('dragenter', (e) => {
    if (!hasFiles(e)) return;
    e.preventDefault();
    dragDepth++;
    overlay?.classList.add('visible');
  });
  window.addEventListener('dragleave', (e) => {
    if (!hasFiles(e)) return;
    dragDepth = Math.max(0, dragDepth - 1);
    if (dragDepth === 0) overlay?.classList.remove('visible');
  });
  window.addEventListener('dragover', (e) => {
    e.preventDefault();
    if (e.dataTransfer) e.dataTransfer.dropEffect = hasFiles(e) ? 'copy' : 'none';
  });
  window.addEventListener('drop', async (e) => {
    e.preventDefault();
    dragDepth = 0;
    overlay?.classList.remove('visible');
    const files = Array.from(e.dataTransfer?.files || []);
    if (files.length === 0) return;

    let added = 0;
    for (const file of files) {
      const filePath: string = emuApi.getPathForFile?.(file) || '';
      if (!filePath) continue;
      try {
        const item = await emuApi.library.addFile(filePath);
        if (item) {
          added++;
          continue;
        }
        await emuApi.library.removeFile(filePath);
        await emuApi.library.addDirectory(filePath);
        added++;
      } catch {}
    }

    if (added > 0) {
      sfx.play('confirm');
      const config = await emuApi.config.get();
      store.set({ settings: { ...store.get().settings, ...config.settings } });
      await rescanLibrary();
      toast(`Added ${added} ${added === 1 ? 'item' : 'items'} to your library`, 'success', 2500);
    } else {
      toast('Nothing to add: drop ROM files or game folders.', 'info');
    }
  });
}

function setupImageFallbacks() {
  // Embedded DS/3DS icons are tiny pixel art: scale them crisply instead of blurring them.
  document.addEventListener(
    'load',
    (e) => {
      const img = e.target;
      if (img instanceof HTMLImageElement && img.naturalWidth > 0 && img.naturalWidth <= 64) {
        img.classList.add('pixel-art');
      }
    },
    true
  );
  document.addEventListener(
    'error',
    (e) => {
      const img = e.target;
      if (!(img instanceof HTMLImageElement)) return;
      const fallback = img.dataset.fallback;
      if (fallback && !img.dataset.triedFallback) {
        img.dataset.triedFallback = '1';
        img.src = fallback;
        return;
      }
      img.hidden = true;
      const placeholder = img.nextElementSibling as HTMLElement | null;
      if (placeholder?.hasAttribute('data-img-fallback')) placeholder.hidden = false;
    },
    true
  );
}

async function bootstrap() {
  await sfx.init();

  nav.init({ onBack: goBack });
  window.addEventListener('keydown', handleShortcuts);
  setupImageFallbacks();
  setupDragAndDrop();

  document.getElementById('btnWinMinimize')?.addEventListener('click', () => emuApi.window.minimize());
  document.getElementById('btnWinMaximize')?.addEventListener('click', () => emuApi.window.maximize());
  document.getElementById('btnWinClose')?.addEventListener('click', () => emuApi.window.close());
  document.getElementById('appTitlebar')?.addEventListener('dblclick', (e) => {
    if (!(e.target as HTMLElement).closest('button')) emuApi.window.maximize();
  });

  const updateMaximizeUI = (isMax: boolean) => {
    document.body.classList.toggle('is-maximized', isMax);
    const maxBtn = document.getElementById('btnWinMaximize');
    if (maxBtn) {
      maxBtn.title = isMax ? 'Restore' : 'Maximize';
      maxBtn.innerHTML = isMax
        ? `<svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1"><rect x="2.5" y="0.5" width="7" height="7"/><rect x="0.5" y="2.5" width="7" height="7"/></svg>`
        : `<svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1"><rect x="0.5" y="0.5" width="9" height="9"/></svg>`;
    }
    trayBackdrop?.updateDimensions();
  };

  emuApi.window.onStateChanged?.(({ maximized }: { maximized: boolean }) => updateMaximizeUI(maximized));
  window.addEventListener('resize', () => trayBackdrop?.updateDimensions());
  emuApi.window
    .isMaximized?.()
    .then((isMax: boolean) => isMax && updateMaximizeUI(true))
    .catch(() => {});

  document.getElementById('railNav')?.addEventListener('click', (e) => {
    const item = (e.target as HTMLElement).closest<HTMLElement>('[data-nav]');
    if (!item) return;
    const target = item.dataset.nav as ScreenType;
    sfx.play('click');
    closeFloatingPanel();
    if (target === 'library') store.set({ consoleFilter: 'ALL', searchQuery: '' });
    if (target === 'sessions' && store.get().activeSession && store.get().currentScreen !== 'session') {
      store.navigate('session');
      return;
    }
    store.navigate(target);
  });
  document.getElementById('railConsoles')?.addEventListener('click', (e) => {
    const item = (e.target as HTMLElement).closest<HTMLElement>('[data-console-filter]');
    if (!item) return;
    sfx.play('click');
    closeFloatingPanel();
    store.set({ consoleFilter: item.dataset.consoleFilter || 'ALL', searchQuery: '' });
    store.navigate('library');
  });
  document.getElementById('appRail')?.addEventListener('keydown', (e) => {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    const items = Array.from(document.querySelectorAll<HTMLElement>('#railNav [data-nav], #railConsoles [data-console-filter]'));
    const idx = items.indexOf(document.activeElement as HTMLElement);
    if (idx < 0) return;
    e.preventDefault();
    items[Math.max(0, Math.min(items.length - 1, idx + (e.key === 'ArrowDown' ? 1 : -1)))].focus();
  });
  document.getElementById('headerProfileBtn')?.addEventListener('click', () => {
    sfx.play('click');
    toggleFloatingPanel('profile');
  });
  document.getElementById('vpsStatusChip')?.addEventListener('click', () => {
    sfx.play('click');
    toggleFloatingPanel('server');
  });
  document.getElementById('btnRailAdd')?.addEventListener('click', (e) => {
    sfx.play('click');
    openContextMenuAt(e.currentTarget as HTMLElement, [
      {
        label: 'Add game files…',
        icon: '<rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
        action: addRomFiles,
      },
      {
        label: 'Add a folder…',
        icon: '<path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>',
        action: addRomFolder,
      },
    ]);
  });
  document.getElementById('floatingPanelBackdrop')?.addEventListener('click', () => closeFloatingPanel());

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
  document.body.classList.toggle('reduce-motion', config.settings.animations === false);

  window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (store.get().settings.theme === 'system') {
      document.body.dataset.theme = resolveTheme('system');
      trayBackdrop?.update();
    }
  });

  const backdropCanvas = document.getElementById('trayBackdropCanvas') as HTMLCanvasElement;
  if (backdropCanvas) trayBackdrop = new TrayBackdrop(backdropCanvas);

  store.set({ profile: config.profile, settings: config.settings });
  updateRail(store.get());

  loadMetadataAndScan();

  checkCoordinatorStatus();
  setInterval(checkCoordinatorStatus, 30000);

  // Matches EmufiiApp.kt PRESENCE_MS = 45s
  const syncPresence = async () => {
    const s = store.get();
    if (s.currentScreen === 'session' || s.activeSession || getPresence() === 'offline') return;
    if (s.profile.id && s.profile.name) {
      try {
        await emuApi.coordinator.announcePresence(s.profile.id, s.profile.name, false);
      } catch {}
    }
  };
  syncPresence();
  setInterval(syncPresence, 45000);

  if (config.profile?.id && config.profile?.avatarDataUrl) {
    encodeAvatarToWebpBase64(config.profile.avatarDataUrl)
      .then((webpBase64) => {
        if (webpBase64) {
          emuApi.coordinator
            .uploadAvatar(config.profile.id, config.profile.ownerKey || config.profile.id, webpBase64)
            .catch(() => {});
        }
      })
      .catch(() => {});
  }

  const pollFriendsRealtime = () => {
    syncFriendsRealtime(() => {
      if (isFloatingPanelOpen() && getFloatingPanelTab() === 'friends') renderFloatingPanel();
    }).catch(() => {});
  };
  pollFriendsRealtime();
  setInterval(pollFriendsRealtime, 5000);

  if (config.settings?.autoDownloadUpdates !== false && emuApi?.updates?.check) {
    setTimeout(() => {
      emuApi.updates
        .check()
        .then((res: any) => res?.updateAvailable && openUpdateModal(res))
        .catch(() => {});
    }, 3500);
  }

  let last = libraryKey(store.get());
  let lastView = '';
  store.subscribe((state) => {
    updateRail(state);
    document.body.classList.toggle('reduce-motion', state.settings.animations === false);

    const resolvedTheme = resolveTheme(state.settings.theme || 'dark');
    if (resolvedTheme !== document.body.dataset.theme) {
      withTransition(() => {
        document.body.dataset.theme = resolvedTheme;
        trayBackdrop?.update();
      }, 'theme');
    }

    const key = libraryKey(state);
    const libraryChanged = key !== last;
    last = key;
    const viewKey = `${state.consoleFilter}|${state.settings.libraryLayout}|${state.settings.librarySort}`;
    const viewChanged = viewKey !== lastView;
    lastView = viewKey;

    if (currentRenderedScreen !== state.currentScreen) {
      currentRenderedScreen = state.currentScreen;
      const screen = state.currentScreen;
      withTransition(() => renderCurrentScreen(screen), 'screen');
    } else if (currentRenderedScreen === 'library' && libraryChanged) {
      // Typing in the search box re-renders on every key: only cross-fade real view changes.
      if (viewChanged) withTransition(() => renderCurrentScreen('library', false));
      else renderCurrentScreen('library', false);
    }
  });

  renderCurrentScreen('splash');
}

function libraryKey(state: AppState): string {
  const s = state.settings;
  return [
    state.consoleFilter,
    state.searchQuery,
    state.roms.length,
    state.roms.map((r) => r.id).join(','),
    s.libraryLayout,
    s.librarySort,
    s.theme,
    (s.hiddenRoms || []).join(','),
    (s.hiddenConsoles || []).join(','),
    s.hideIncompatible,
    JSON.stringify(s.customTitles || {}),
  ].join('|');
}

function updateRail(state: AppState) {
  const avatarEl = document.getElementById('headerProfileAvatar');
  const dotEl = document.getElementById('headerStatusDot');
  const subtitleEl = document.getElementById('headerSubtitle');

  if (avatarEl) {
    const url = state.profile.avatarDataUrl;
    const img = avatarEl.querySelector('img');
    if (url) {
      if (!img || img.src !== url) {
        const next = document.createElement('img');
        next.src = url;
        next.alt = '';
        avatarEl.replaceChildren(next);
      }
    } else {
      avatarEl.textContent = (state.profile.name || 'J').slice(0, 2).toUpperCase();
    }
  }

  if (dotEl) dotEl.className = `user-status-dot ${getPresence()}`;

  if (subtitleEl) {
    const friendsList = state.settings.friends || [];
    const onlineCount = friendsList.filter((code) => !!state.friendStatuses?.present?.[code]).length;
    subtitleEl.textContent =
      getPresence() === 'offline'
        ? 'Invisible'
        : friendsList.length === 0
        ? 'Online'
        : onlineCount > 0
        ? `${onlineCount} ${onlineCount === 1 ? 'friend' : 'friends'} online`
        : `0 of ${friendsList.length} friends online`;
  }

  const nameEl = document.getElementById('railProfileName');
  if (nameEl) nameEl.textContent = state.profile.name || 'Player';

  const navScreen: Record<string, string> = { game: 'library', session: 'sessions' };
  const activeNav = navScreen[state.currentScreen] || state.currentScreen;
  document.querySelectorAll<HTMLElement>('#railNav [data-nav]').forEach((item) => {
    const active = item.dataset.nav === activeNav;
    item.classList.toggle('active', active);
    item.setAttribute('aria-current', active ? 'page' : 'false');
  });

  const visible = state.roms.filter((r: RomItem) => !state.settings.hiddenRoms?.includes(r.id));
  const libCount = document.getElementById('railLibraryCount');
  if (libCount) libCount.textContent = visible.length ? String(visible.length) : '';

  const friendsBadge = document.getElementById('railFriendsBadge');
  if (friendsBadge) {
    const friends = state.settings.friends || [];
    const online = friends.filter((code) => !!state.friendStatuses?.present?.[code]).length;
    const pending = (state.settings.pendingFriendRequests || []).length + (state.settings.pendingSessionInvites || []).length;
    friendsBadge.textContent = pending ? String(pending) : online ? `${online} on` : '';
    friendsBadge.classList.toggle('alert', pending > 0);
  }

  const sessionBadge = document.getElementById('railSessionBadge');
  if (sessionBadge) {
    const session = state.activeSession;
    sessionBadge.hidden = !session;
    sessionBadge.textContent = session ? (session.code === 'ONLINE' ? 'Live' : session.code) : '';
  }

  const consolesEl = document.getElementById('railConsoles');
  if (consolesEl) {
    const groups = consoleGroups(state);
    const filter = state.currentScreen === 'library' || state.currentScreen === 'game' ? state.consoleFilter : null;
    const key = groups.map((g) => `${g.console}:${g.roms.length}`).join(',') + `|${filter}|${document.body.dataset.theme}`;
    if (consolesEl.dataset.key !== key) {
      consolesEl.dataset.key = key;
      const light = document.body.dataset.theme === 'light';
      consolesEl.innerHTML = groups
        .map((g) => {
          const [c1, c2] = paletteFor(g.console);
          return `
          <button class="rail-console ${filter === g.console ? 'active' : ''}" data-console-filter="${esc(g.console)}" title="${esc(getConsoleFriendlyName(g.console))}">
            <span class="rail-console-thumb" style="background: linear-gradient(135deg, ${c1}, ${c2});">
              <img src="assets/consoles/console_${getConsoleSlug(g.console)}_${light ? 'light' : 'dark'}.webp" alt="" draggable="false">
            </span>
            <span class="rail-console-name">${esc(getConsoleFriendlyName(g.console))}</span>
            <span class="rail-console-count">${g.roms.length}</span>
          </button>`;
        })
        .join('');
    }
  }
}

async function loadMetadataAndScan() {
  const indexByKeys = (games: any[]) => {
    const map: Record<string, any> = {};
    for (const g of games) for (const k of g.keys || []) map[k] = g;
    return map;
  };
  try {
    const [compat, meta] = await Promise.all([
      emuApi.library.fetchCompat().catch(() => null),
      emuApi.library.fetchMeta().catch(() => null),
    ]);
    if (compat?.games) store.set({ compatDb: indexByKeys(compat.games) });
    if (meta?.games) store.set({ metaDb: indexByKeys(meta.games) });
  } catch {}
  await rescanLibrary();
}

function renderCurrentScreen(screen: ScreenType, fresh = true) {
  if (cleanupCurrentScreen) {
    cleanupCurrentScreen();
    cleanupCurrentScreen = null;
  }

  document.body.dataset.screen = screen;

  const titlebarBrand = document.querySelector<HTMLElement>('.titlebar-brand');
  if (titlebarBrand) titlebarBrand.style.visibility = screen === 'splash' ? 'hidden' : 'visible';

  const host = document.getElementById('screenHost');
  if (!host) return;

  let targetView: HTMLElement;

  if (fresh) {
    // The old screen is captured by the view transition, so it can go right away.
    document.getElementById('currentScreenView')?.remove();
    const newView = document.createElement('div');
    newView.className = 'screen';
    newView.id = 'currentScreenView';
    host.appendChild(newView);
    targetView = newView;
    setTimeout(() => nav.focusFirst(newView), 120);
  } else {
    let currentView = document.getElementById('currentScreenView');
    if (!currentView) {
      currentView = document.createElement('div');
      currentView.className = 'screen';
      currentView.id = 'currentScreenView';
      host.appendChild(currentView);
    }
    if (screen !== 'library') currentView.innerHTML = '';
    targetView = currentView;
  }

  switch (screen) {
    case 'splash':
      cleanupCurrentScreen = renderSplashScreen(targetView) || null;
      break;
    case 'library':
      cleanupCurrentScreen = renderLibraryScreen(targetView) || null;
      break;
    case 'game':
      cleanupCurrentScreen = renderGameScreen(targetView) || null;
      break;
    case 'sessions':
    case 'friends':
      cleanupCurrentScreen = renderPanelPage(targetView, screen);
      break;
    case 'session':
      cleanupCurrentScreen = renderSessionScreen(targetView) || null;
      break;
    case 'settings':
      if (fresh) resetSettingsView();
      renderSettingsScreen(targetView);
      break;
  }
}

const REGION_NAMES: Record<string, string> = {
  auto: 'US East (NY)',
  use: 'US East (NY)',
  usw: 'US West (Seattle)',
  eu: 'Europe',
  na: 'Canada (Montréal)',
  jp: 'Japan (Tokyo)',
};

async function checkCoordinatorStatus() {
  const lamp = document.getElementById('vpsLamp');
  const chip = document.getElementById('vpsStatusChip');
  const text = document.getElementById('vpsText');
  const region = REGION_NAMES[localStorage.getItem('emufii_server_region') || 'auto'] || REGION_NAMES.auto;
  let online = false;
  let present = 0;
  try {
    const health = await emuApi.coordinator.checkHealth();
    online = !!health?.ok;
    present = health?.present || 0;
  } catch {}

  if (lamp) lamp.className = online ? 'lamp active' : 'lamp';
  if (text) text.textContent = `${online ? 'Online' : 'Offline'} • ${region}`;
  if (chip) {
    chip.title = online
      ? `Coordinator online${present ? ` (${present} players)` : ''} · Click to pick a relay region`
      : 'Cannot reach the Emufii coordinator';
  }
  if (online) refreshRelayLatencies().catch(() => {});
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', bootstrap);
} else {
  bootstrap();
}
