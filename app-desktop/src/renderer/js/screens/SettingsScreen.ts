import { store } from '../state.js';
import { sfx } from '../sfx.js';
import { t, getActiveLanguage } from '../i18n.js';
import { resolveTheme } from '../components/SettingsModal.js';
import { ensureEmulator } from '../components/EmulatorDownloadModal.js';
import { openUpdateModal } from '../components/UpdateModal.js';

export type SettingsHubView = 'hub' | 'general' | 'library' | 'consoles' | 'emulators' | 'appearance' | 'about' | 'crashlogs';

let currentView: SettingsHubView = 'hub';
let selectedTargetDir: string = '';

function getDirBasename(dirPath: string): string {
  if (!dirPath) return '';
  const parts = dirPath.split(/[\\/]/).filter(Boolean);
  return parts.pop() || dirPath;
}

export const CONSOLES_LIST = [
  { id: 'Wii', name: 'Nintendo Wii', short: 'Wii', desc: 'RVL • Dolphin (Wiimote & GCPad)' },
  { id: 'GameCube', name: 'Nintendo GameCube', short: 'GC', desc: 'DOL • Dolphin GameCube' },
  { id: 'PS2', name: 'PlayStation 2', short: 'PS2', desc: 'Sony PS2 • PCSX2 DEV9 Local Link' },
  { id: 'PSP', name: 'PlayStation Portable', short: 'PSP', desc: 'Sony PSP • PPSSPP Adhoc P2P' },
  { id: '3DS', name: 'Nintendo 3DS', short: '3DS', desc: 'CTR • Azahar / Citra / Lime3DS' },
  { id: 'DS', name: 'Nintendo DS', short: 'DS', desc: 'NTR • melonDS (Kaeru WFC & Direct)' },
  { id: 'GBA', name: 'Game Boy Advance', short: 'GBA', desc: 'AGB • mGBA (SDL Input)' },
  { id: 'Switch', name: 'Nintendo Switch', short: 'Switch', desc: 'HAC • Eden / Ryujinx (LDN Wireless)' },
];

export function resetSettingsView() {
  currentView = 'hub';
}

export function renderSettingsScreen(container: HTMLElement) {
  const state = store.get();
  const emuApi = (window as any).emufii;

  const accents: { id: string; name: string; hex: string }[] = [
    { id: 'teal', name: 'Signature Teal', hex: '#3FCFC0' },
    { id: 'coral', name: 'Coral', hex: '#EE6FA3' },
    { id: 'cyan', name: 'Electric Cyan', hex: '#38E1FF' },
    { id: 'purple', name: 'Violet Plum', hex: '#8E93EC' },
    { id: 'green', name: 'Emerald Green', hex: '#3BC4A6' },
    { id: 'yellow', name: 'Amber Glow', hex: '#E3A83C' },
    { id: 'red', name: 'Ruby Sunset', hex: '#F0796A' },
    { id: 'blue', name: 'Sky Blue', hex: '#82AFE6' },
  ];

  if (currentView === 'hub') {
    renderHubView(container, state);
  } else {
    renderDetailView(container, currentView, state, accents, emuApi);
  }
}

// Hub view
function renderHubView(container: HTMLElement, state: any) {
  const hiddenConsoles: string[] = state.settings.hiddenConsoles || [];
  const visibleConsolesCount = CONSOLES_LIST.length - hiddenConsoles.length;
  const totalRoms = state.roms.filter((r: any) => !state.settings.hiddenRoms?.includes(r.id)).length;
  const currentTheme = state.settings.theme || 'dark';
  const currentAccent = state.settings.accent || 'teal';
  const themeDisplay = currentTheme.charAt(0).toUpperCase() + currentTheme.slice(1);
  const accentDisplay = currentAccent.charAt(0).toUpperCase() + currentAccent.slice(1);

  container.innerHTML = `
    <div class="settings-hub-container">
      <!-- Top Header -->
      <div class="settings-hub-header">
        <button class="settings-round-back-btn" id="btnHubBack" title="${t('back')}">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        <h1 class="settings-hub-title">${t('settings_title')}</h1>
      </div>

      <!-- 2-Column Grid in exact requested order:
           Row 1: General | Library
           Row 2: Consoles | Emulators
           Row 3: Appearance | About
           Row 4: Crash Logs
      -->
      <div class="settings-hub-grid">
        <!-- 1. General -->
        <div class="settings-hub-tile" data-hub-page="general" tabindex="0">
          <div class="settings-hub-tile-left">
            <div class="settings-hub-icon-wrap">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></svg>
            </div>
            <div class="settings-hub-tile-info">
              <span class="settings-hub-tile-title">${t('general')}</span>
              <span class="settings-hub-tile-sub">${t('general_sub')}</span>
            </div>
          </div>
          <div class="settings-hub-tile-right">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="opacity: 0.35;"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>

        <!-- 2. Library -->
        <div class="settings-hub-tile" data-hub-page="library" tabindex="0">
          <div class="settings-hub-tile-left">
            <div class="settings-hub-icon-wrap">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1-2.5-2.5Z"/><path d="M6 6h10"/><path d="M6 10h10"/></svg>
            </div>
            <div class="settings-hub-tile-info">
              <span class="settings-hub-tile-title">${t('library')}</span>
              <span class="settings-hub-tile-sub">${t('library_sub')}</span>
            </div>
          </div>
          <div class="settings-hub-tile-right">
            <span class="tile-status-pill good">${totalRoms} ${totalRoms === 1 ? 'game' : 'games'}</span>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="opacity: 0.35;"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>

        <!-- 3. Consoles -->
        <div class="settings-hub-tile" data-hub-page="consoles" tabindex="0">
          <div class="settings-hub-tile-left">
            <div class="settings-hub-icon-wrap">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="2" y="6" width="20" height="12" rx="4"/><path d="M6 12h4"/><path d="M8 10v4"/><circle cx="17" cy="10" r="1.5" fill="currentColor"/><circle cx="15" cy="13" r="1.5" fill="currentColor"/></svg>
            </div>
            <div class="settings-hub-tile-info">
              <span class="settings-hub-tile-title">${t('consoles')}</span>
              <span class="settings-hub-tile-sub">${t('consoles_sub', { count: visibleConsolesCount, total: CONSOLES_LIST.length })}</span>
            </div>
          </div>
          <div class="settings-hub-tile-right">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="opacity: 0.35;"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>

        <!-- 4. Emulators -->
        <div class="settings-hub-tile" data-hub-page="emulators" tabindex="0">
          <div class="settings-hub-tile-left">
            <div class="settings-hub-icon-wrap">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><path d="M9 1v3"/><path d="M15 1v3"/><path d="M9 20v3"/><path d="M15 20v3"/><path d="M20 9h3"/><path d="M20 14h3"/><path d="M1 9h3"/><path d="M1 14h3"/></svg>
            </div>
            <div class="settings-hub-tile-info">
              <span class="settings-hub-tile-title">${t('emulators')}</span>
              <span class="settings-hub-tile-sub">${t('emulators_sub')}</span>
            </div>
          </div>
          <div class="settings-hub-tile-right">
            <span class="tile-status-pill warn" id="hubEmuPill"><span class="pill-exclamation">!</span> ...</span>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="opacity: 0.35;"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>

        <!-- 5. Appearance -->
        <div class="settings-hub-tile" data-hub-page="appearance" tabindex="0">
          <div class="settings-hub-tile-left">
            <div class="settings-hub-icon-wrap">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M12 2.69l5.66 5.66a8 8 0 1 1-11.31 0z"/></svg>
            </div>
            <div class="settings-hub-tile-info">
              <span class="settings-hub-tile-title">${t('appearance')}</span>
              <span class="settings-hub-tile-sub">${t('appearance_sub', { theme: themeDisplay, accent: accentDisplay })}</span>
            </div>
          </div>
          <div class="settings-hub-tile-right">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="opacity: 0.35;"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>

        <!-- 6. About -->
        <div class="settings-hub-tile" data-hub-page="about" tabindex="0">
          <div class="settings-hub-tile-left">
            <div class="settings-hub-icon-wrap">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            </div>
            <div class="settings-hub-tile-info">
              <span class="settings-hub-tile-title">${t('about')}</span>
              <span class="settings-hub-tile-sub">${t('about_sub')}</span>
            </div>
          </div>
          <div class="settings-hub-tile-right">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="opacity: 0.35;"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>

        <!-- 7. Crash Logs -->
        <div class="settings-hub-tile" data-hub-page="crashlogs" tabindex="0">
          <div class="settings-hub-tile-left">
            <div class="settings-hub-icon-wrap">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect width="8" height="14" x="8" y="6" rx="4"/><path d="m19 7-3 2"/><path d="m5 7 3 2"/><path d="m19 19-3-2"/><path d="m5 19 3-2"/><path d="M20 13h-4"/><path d="M4 13h4"/><path d="m10 4 1 2"/><path d="m14 4-1 2"/></svg>
            </div>
            <div class="settings-hub-tile-info">
              <span class="settings-hub-tile-title">${t('crash_logs')}</span>
              <span class="settings-hub-tile-sub">${t('crash_logs_sub')}</span>
            </div>
          </div>
          <div class="settings-hub-tile-right">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="opacity: 0.35;"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>
      </div>
    </div>
  `;

  // Dynamically query installed emulators count for the Hub tile pill
  const emuApi = (window as any).emufii;
  if (emuApi?.emulators?.detectAll) {
    emuApi.emulators.detectAll().then((all: any) => {
      const emus = Object.values(all || {}) as any[];
      const installed = emus.filter((e) => e.isInstalled).length;
      const total = emus.length;
      const pill = container.querySelector('#hubEmuPill');
      if (pill) {
        if (installed === total && total > 0) {
          pill.className = 'tile-status-pill good';
          pill.innerHTML = `✓ ${installed} / ${total}`;
        } else {
          pill.className = 'tile-status-pill warn';
          pill.innerHTML = `<span class="pill-exclamation">!</span> ${installed} / ${total}`;
        }
      }
    }).catch(() => {});
  }

  // Attach navigation
  container.querySelectorAll<HTMLElement>('.settings-hub-tile').forEach((tile) => {
    tile.addEventListener('click', (e) => {
      sfx.play('click');
      const page = (e.currentTarget as HTMLElement).dataset.hubPage as SettingsHubView;
      if (page) {
        currentView = page;
        renderSettingsScreen(container);
      }
    });

    // Enter / Space to open
    tile.addEventListener('keydown', (e: KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        tile.click();
      }
    });
  });

  const goBack = () => {
    sfx.play('click');
    store.goBack();
  };

  container.querySelector('#btnHubBack')?.addEventListener('click', goBack);
}

export function updateSegmentedGlider(segEl: HTMLElement, animate: boolean = true) {
  let glider = segEl.querySelector<HTMLElement>('.segmented-glider');
  if (!glider) {
    glider = document.createElement('span');
    glider.className = 'segmented-glider';
    segEl.prepend(glider);
  }
  let activeItem = segEl.querySelector<HTMLElement>('.segmented-item.active');
  if (!activeItem) {
    activeItem = segEl.querySelector<HTMLElement>('.segmented-item');
    if (activeItem) {
      activeItem.classList.add('active');
    } else {
      return;
    }
  }

  const left = activeItem.offsetLeft;
  const top = activeItem.offsetTop;
  const width = activeItem.offsetWidth;
  const height = activeItem.offsetHeight;

  if (width === 0 || height === 0) {
    requestAnimationFrame(() => updateSegmentedGlider(segEl, false));
    return;
  }

  if (!animate) {
    glider.style.transition = 'none';
  } else {
    glider.style.transition =
      'left 300ms cubic-bezier(0.2, 0.9, 0.3, 1.15), width 300ms cubic-bezier(0.2, 0.9, 0.3, 1.15), opacity 150ms ease';
  }

  glider.style.top = `${top}px`;
  glider.style.height = `${height}px`;
  glider.style.left = `${left}px`;
  glider.style.width = `${width}px`;
  glider.style.transform = 'none';
  glider.style.opacity = '1';
}

export function setupAllSegmentedGliders(container: HTMLElement) {
  const controls = container.querySelectorAll<HTMLElement>('.segmented-control');
  controls.forEach((ctrl) => {
    updateSegmentedGlider(ctrl, false);
  });
  if (typeof document !== 'undefined' && (document as any).fonts?.ready) {
    (document as any).fonts.ready.then(() => {
      controls.forEach((ctrl) => updateSegmentedGlider(ctrl, false));
    });
  }
  if (typeof window !== 'undefined' && !(container as any)._gliderWinResizeBound) {
    (container as any)._gliderWinResizeBound = true;
    window.addEventListener(
      'resize',
      () => {
        if (document.body.contains(container)) {
          controls.forEach((ctrl) => updateSegmentedGlider(ctrl, false));
        }
      },
      { passive: true }
    );
  }
}

// Detail view (opens when clicking any Hub tile)
function renderDetailView(
  container: HTMLElement,
  view: SettingsHubView,
  state: any,
  accents: any[],
  emuApi: any
) {
  const titles: Record<SettingsHubView, string> = {
    hub: t('settings_title'),
    general: t('general'),
    library: t('library'),
    consoles: t('consoles'),
    emulators: t('emulators'),
    appearance: t('appearance'),
    about: t('about'),
    crashlogs: t('crash_logs'),
  };

  container.innerHTML = `
    <div class="settings-hub-container">
      <!-- Detail Top Header with Back to Hub -->
      <div class="settings-hub-header">
        <button class="settings-round-back-btn" id="btnDetailBack" title="${t('back')}">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        <h1 class="settings-hub-title">${titles[view] || t('settings_title')}</h1>
      </div>

      <!-- Detail Page Content Body -->
      <div id="settingsDetailBody">
        ${renderDetailBody(view, state, accents)}
      </div>
    </div>
  `;

  const returnToHub = () => {
    sfx.play('click');
    currentView = 'hub';
    renderSettingsScreen(container);
  };

  container.querySelector('#btnDetailBack')?.addEventListener('click', returnToHub);

  // Attach detail listeners
  attachDetailListeners(view, container, state, emuApi);
  requestAnimationFrame(() => {
    setupAllSegmentedGliders(container);
    setTimeout(() => setupAllSegmentedGliders(container), 40);
  });
}

function renderDetailBody(view: SettingsHubView, state: any, accents: any[]): string {
  switch (view) {
    case 'general':
      return renderGeneralPage(state);
    case 'library':
      return renderLibraryPage(state);
    case 'consoles':
      return renderConsolesPage(state);
    case 'emulators':
      return renderEmulatorsPage();
    case 'appearance':
      return renderAppearancePage(state, accents);
    case 'about':
      return renderAboutPage(state);
    case 'crashlogs':
      return renderCrashLogsPage();
    default:
      return '';
  }
}

// 1. General page
function renderGeneralPage(state: any): string {
  const currentLang = state.settings.language || 'system';

  return `
    <div class="settings-section-header">${t('general')}</div>
    <div class="settings-clean-group">
      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('app_language')}</div>
          <div class="settings-sublabel">${t('app_language_sub')}</div>
        </div>
        <div class="segmented-control">
          <span class="segmented-glider"></span>
          ${[
            { id: 'system', name: 'System' },
            { id: 'en', name: 'English' },
            { id: 'es', name: 'Español' },
            { id: 'fr', name: 'Français' },
          ]
            .map(
              (l) => `
            <button class="segmented-item ${currentLang === l.id ? 'active' : ''}" data-lang-val="${l.id}">
              ${l.name}
            </button>
          `
            )
            .join('')}
        </div>
      </div>

      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('friend_alerts')}</div>
          <div class="settings-sublabel">${t('friend_alerts_sub')}</div>
        </div>
        <label class="clean-switch">
          <input type="checkbox" id="chkNotifyFriends" ${state.settings.notifyFriends !== false ? 'checked' : ''}>
          <span class="slider"></span>
        </label>
      </div>
    </div>

    <div class="settings-section-header">${t('coordinator_url')}</div>
    <div class="settings-clean-group">
      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('coordinator_url')}</div>
          <div class="settings-sublabel">${t('coordinator_url_sub')}</div>
        </div>
        <div style="display: flex; gap: 8px;">
          <input type="text" id="inputCoordinatorUrl" class="plate" value="${state.settings.coordinatorUrl}" style="padding: 7px 12px; font-size: 12px; width: 220px; border-radius: 10px; color: var(--ink-primary);">
          <button class="btn-secondary plate-button" id="btnSaveCoordUrl" style="height: 32px; padding: 0 14px; font-size: 12px; border-radius: 9999px;">${t('save')}</button>
        </div>
      </div>

      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('server_health')}</div>
          <div class="settings-sublabel">${t('server_health_sub')}</div>
        </div>
        <span class="badge" id="coordHealthBadge" style="font-size: 11px; font-weight: 800; background: rgba(59, 130, 246, 0.15); color: #3b82f6;">${t('checking')}</span>
      </div>
    </div>

    <div class="settings-section-header">WireGuard</div>
    <div class="settings-clean-group">
      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('virtual_interface')}</div>
          <div class="settings-sublabel">${t('virtual_interface_sub')}</div>
        </div>
        <span class="badge" style="font-family: monospace; font-size: 12px; font-weight: 800;">emufii0</span>
      </div>

      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('tunnel_specs')}</div>
          <div class="settings-sublabel">${t('tunnel_specs_sub')}</div>
        </div>
        <span class="badge" style="font-size: 11px; font-weight: 800; background: rgba(16, 185, 129, 0.15); color: #10b981;">OPTIMIZED</span>
      </div>
    </div>
  `;
}

// 2. Library page
function renderLibraryPage(state: any): string {
  const dirs: string[] = state.settings.romDirectories || [];
  const hiddenCount = (state.settings.hiddenRoms || []).length;

  if (!selectedTargetDir || (!dirs.includes(selectedTargetDir) && dirs.length > 0)) {
    selectedTargetDir = dirs[0] || '';
  }

  const targetBasename = getDirBasename(selectedTargetDir) || 'Directory';

  return `
    <!-- 1. Game Directories Header with Action Buttons -->
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
      <div class="settings-section-header" style="margin-top: 0; margin-bottom: 0;">${t('game_directories')}</div>
      <div style="display: flex; gap: 6px; align-items: center; flex-wrap: wrap;">
        <button class="btn-primary plate-button" id="btnAddRomGames" style="height: 28px; padding: 0 12px; font-size: 11px; font-weight: 700; border-radius: 9999px; display: inline-flex; align-items: center; gap: 5px;" title="${t('add_roms')}">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
          <span>${t('add_roms_btn')}</span>
        </button>
        <button class="btn-secondary plate-button" id="btnSettingsAddDir" style="height: 28px; padding: 0 10px; font-size: 11px; font-weight: 700; border-radius: 9999px;">
          ${t('add_folder')}
        </button>
        <button class="btn-secondary plate-button" id="btnOpenSpecialDir" style="height: 28px; padding: 0 10px; font-size: 11px; font-weight: 700; border-radius: 9999px; color: var(--accent-bright); border-color: var(--accent-subtle);" title="${t('open_special_folder')}">
          ⭐ Vault
        </button>
        <button class="btn-secondary plate-button" id="btnRescan" style="height: 28px; padding: 0 10px; font-size: 11px; font-weight: 700; border-radius: 9999px;">
          ${t('rescan_library')}
        </button>
      </div>
    </div>

    <!-- Directories List Card -->
    <div class="settings-clean-group" style="margin-bottom: 14px;">
      <div style="display: flex; flex-direction: column;">
        ${
          dirs.length === 0
            ? `<div style="font-size: 12px; color: var(--ink-secondary); padding: 12px 16px;">${t('no_directories')}</div>`
            : dirs
                .map((dir, idx) => {
                  const isSpecial = dir.includes('Emufii/ROMs') || dir.endsWith('/ROMs') || dir.endsWith('\\ROMs');
                  const isTarget = dir === selectedTargetDir;
                  return `
                    <div class="path-clean-row ${isTarget ? 'target-active' : ''}" style="padding: 8px 14px;">
                      <div class="btnSelectTargetDir" data-dir="${dir}" style="display: flex; align-items: center; gap: 10px; overflow: hidden; flex: 1; cursor: pointer;" title="${t('set_as_target')}">
                        <div class="target-radio-indicator ${isTarget ? 'active' : ''}">
                          ${isTarget ? '<div class="target-radio-dot"></div>' : ''}
                        </div>
                        ${
                          isSpecial
                            ? `<span class="badge" style="font-size: 9.5px; font-weight: 800; background: rgba(56, 225, 255, 0.15); color: var(--accent-bright); border: 1px solid var(--accent-subtle);">⭐ VAULT</span>`
                            : `<span class="badge" style="font-size: 9.5px; font-weight: 800; background: var(--plate-bg);">${idx === 0 ? 'PRIMARY' : 'FOLDER'}</span>`
                        }
                        ${
                          isTarget
                            ? `<span class="badge" style="font-size: 9px; font-weight: 800; background: var(--accent-bright); color: var(--accent-ink);">${t('selected_target')}</span>`
                            : ''
                        }
                        <span style="font-family: var(--font-family); font-size: 12px; color: var(--ink-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${dir}">${dir}</span>
                      </div>
                      <div style="display: flex; align-items: center; gap: 5px; flex-shrink: 0;">
                        <button class="btnPathAddRom plate-button" data-dir="${dir}" style="height: 24px; padding: 0 8px; font-size: 10.5px; font-weight: 700; border-radius: 9999px; background: rgba(255, 255, 255, 0.06); color: var(--ink-primary); display: inline-flex; align-items: center; gap: 3px;" title="${t('add_roms')}">
                          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
                          ROM
                        </button>
                        <button class="btnPathOpenFolder plate-button" data-dir="${dir}" style="height: 24px; width: 24px; border-radius: 50%; background: rgba(255, 255, 255, 0.06); color: var(--ink-secondary); display: inline-flex; align-items: center; justify-content: center;" title="${t('open_folder')}">
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>
                        </button>
                        ${
                          !isSpecial
                            ? `<button class="btnRemoveDir" data-dir="${dir}" style="background: transparent; border: none; cursor: pointer; color: var(--compat-broken); font-size: 16px; padding: 0 4px; line-height: 1;" title="${t('remove')}">&times;</button>`
                            : ''
                        }
                      </div>
                    </div>
                  `;
                })
                .join('')
        }
      </div>
    </div>

    <!-- 2. Library Preferences Header -->
    <div class="settings-section-header" style="margin-top: 14px; margin-bottom: 8px;">${t('library')}</div>

    <!-- Library Preferences Card (Matching General & Appearance) -->
    <div class="settings-clean-group" style="margin-bottom: 0;">
      <!-- SteamGridDB API Key -->
      <div class="settings-clean-row" style="padding: 10px 16px;">
        <div>
          <div class="settings-label">${t('sgdb_key')}</div>
          <div class="settings-sublabel">${t('sgdb_key_sub')}</div>
        </div>
        <div style="display: flex; gap: 6px;">
          <input type="password" id="inputSgdbKey" class="plate" placeholder="API Key..." value="${state.settings.steamGridDbKey || (typeof localStorage !== 'undefined' ? localStorage.getItem('emufii_sgdb_key') : '') || ''}" style="padding: 5px 10px; font-size: 11px; width: 170px; border-radius: 8px; color: var(--ink-primary);">
          <button class="btn-secondary plate-button" id="btnSaveSgdbKey" style="height: 28px; padding: 0 12px; font-size: 11px; border-radius: 9999px;">${t('save')}</button>
        </div>
      </div>

      <!-- Default Layout -->
      <div class="settings-clean-row" style="padding: 10px 16px;">
        <div>
          <div class="settings-label">${t('default_layout')}</div>
          <div class="settings-sublabel">${t('default_layout_sub')}</div>
        </div>
        <div class="segmented-control">
          <span class="segmented-glider"></span>
          ${(() => {
            const currentLayout = state.settings.libraryLayout || 'grid';
            return ['grid', 'carousel', 'list']
              .map(
                (l) => `
            <button class="segmented-item ${currentLayout === l ? 'active' : ''}" data-layout-val="${l}">
              ${l.charAt(0).toUpperCase() + l.slice(1)}
            </button>
          `
              )
              .join('');
          })()}
        </div>
      </div>

      <!-- Default Sorting -->
      <div class="settings-clean-row" style="padding: 10px 16px;">
        <div>
          <div class="settings-label">${t('default_sorting')}</div>
          <div class="settings-sublabel">${t('default_sorting_sub')}</div>
        </div>
        <div class="segmented-control">
          <span class="segmented-glider"></span>
          ${(() => {
            const currentSort = state.settings.librarySort || 'console';
            return [
              { id: 'console', name: 'Console' },
              { id: 'az', name: 'A - Z' },
              { id: 'date', name: 'Date' },
            ]
              .map(
                (s) => `
            <button class="segmented-item ${currentSort === s.id ? 'active' : ''}" data-sort-val="${s.id}">
              ${s.name}
            </button>
          `
              )
              .join('');
          })()}
        </div>
      </div>

      <!-- Hide Incompatible -->
      <div class="settings-clean-row" style="padding: 10px 16px;">
        <div>
          <div class="settings-label">${t('hide_incompatible')}</div>
          <div class="settings-sublabel">${t('hide_incompatible_sub')}</div>
        </div>
        <label class="clean-switch">
          <input type="checkbox" id="chkHideIncompatible" ${state.settings.hideIncompatible ? 'checked' : ''}>
          <span class="slider"></span>
        </label>
      </div>

      <!-- Hidden Games -->
      <div class="settings-clean-row" style="padding: 10px 16px;">
        <div>
          <div class="settings-label">${t('hidden_games')} (${hiddenCount})</div>
          <div class="settings-sublabel">${t('hidden_games_sub', { count: hiddenCount })}</div>
        </div>
        <button class="btn-secondary plate-button" id="btnRestoreHidden" style="height: 28px; padding: 0 14px; font-size: 11px; border-radius: 9999px;" ${hiddenCount === 0 ? 'disabled style="opacity: 0.5;"' : ''}>
          ${t('restore_hidden')}
        </button>
      </div>
    </div>
  `;
}

// 3. Consoles page
function renderConsolesPage(state: any): string {
  const hiddenConsoles: string[] = state.settings.hiddenConsoles || [];

  return `
    <div class="settings-section-header">${t('supported_systems')}</div>
    <p style="font-size: 13px; color: var(--ink-secondary); margin-bottom: 14px; padding-left: 4px;">
      ${t('consoles_note')}
    </p>
    <div class="settings-clean-group">
      ${CONSOLES_LIST.map((c) => {
        const isVisible = !hiddenConsoles.includes(c.id);
        return `
          <div class="settings-clean-row">
            <div style="display: flex; align-items: center; gap: 12px;">
              <span class="badge" style="font-size: 11px; font-weight: 800; min-width: 54px; text-align: center; background: var(--plate-bg);">${c.short}</span>
              <div>
                <div class="settings-label">${c.name}</div>
                <div class="settings-sublabel">${c.desc}</div>
              </div>
            </div>
            <label class="clean-switch">
              <input type="checkbox" class="chkConsoleToggle" data-console="${c.id}" ${isVisible ? 'checked' : ''}>
              <span class="slider"></span>
            </label>
          </div>
        `;
      }).join('')}
    </div>
  `;
}

// 4. Emulators page
function renderEmulatorsPage(): string {
  return `
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
      <div class="settings-section-header" style="margin-bottom: 0;">${t('backends_drivers')}</div>
      <button class="btn-primary plate-button" id="btnDownloadAllEmus" style="height: 28px; min-height: 28px; font-size: 11px; padding: 0 14px; border-radius: 9999px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; box-shadow: none;">
        ${t('download_all')}
      </button>
    </div>
    <div class="settings-clean-group" id="emulatorsListContainer" style="overflow: hidden; width: 100%;">
      <div style="padding: 24px; text-align: center;"><div class="spinner"></div></div>
    </div>
  `;
}

// 5. Appearance page
function renderAppearancePage(state: any, accents: any[]): string {
  return `
    <div class="settings-section-header">${t('theme_floor')}</div>
    <div class="settings-clean-group">
      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('theme_floor')}</div>
          <div class="settings-sublabel">${t('theme_floor_sub')}</div>
        </div>
        <div class="segmented-control">
          <span class="segmented-glider"></span>
          ${(() => {
            const rawTheme = state.settings.theme;
            const currentTheme =
              rawTheme === 'day' ? 'light' : rawTheme === 'night' ? 'dark' : rawTheme || 'dark';
            return ['system', 'light', 'dark', 'oled']
              .map(
                (tVal) => `
            <button class="segmented-item ${currentTheme === tVal ? 'active' : ''}" data-theme-val="${tVal}">
              ${tVal === 'system' ? 'System' : tVal === 'light' ? 'Light' : tVal === 'dark' ? 'Dark' : 'OLED'}
            </button>
          `
              )
              .join('');
          })()}
        </div>
      </div>

      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('accent_signature')}</div>
          <div class="settings-sublabel">${t('accent_signature_sub')}</div>
        </div>
        <div class="accent-dots-bar">
          ${accents
            .map(
              (a) => `
            <div class="accent-dot ${state.settings.accent === a.id ? 'active' : ''}" data-accent-val="${a.id}" style="background-color: ${a.hex};" title="${a.name}"></div>
          `
            )
            .join('')}
        </div>
      </div>

      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('interface_sounds')}</div>
          <div class="settings-sublabel">${t('interface_sounds_sub')}</div>
        </div>
        <label class="clean-switch">
          <input type="checkbox" id="chkSoundEffects" ${state.settings.soundEffects !== false ? 'checked' : ''}>
          <span class="slider"></span>
        </label>
      </div>

      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('fluid_animations')}</div>
          <div class="settings-sublabel">${t('fluid_animations_sub')}</div>
        </div>
        <label class="clean-switch">
          <input type="checkbox" id="chkAnimations" ${state.settings.animations !== false ? 'checked' : ''}>
          <span class="slider"></span>
        </label>
      </div>
    </div>
  `;
}

// 6. About page
function renderAboutPage(state: any): string {
  const autoDownload = state.settings?.autoDownloadUpdates !== false;
  return `
    <div class="settings-section-header">${t('about')}</div>
    <div class="settings-clean-group" style="padding: 24px; display: flex; flex-direction: column; gap: 12px;">
      <div style="display: flex; align-items: center; gap: 12px;">
        <strong style="color: var(--ink-primary); font-size: 18px; font-weight: 900;">${t('about_card_title')}</strong>
        <span style="font-size: 11px; font-weight: 800; background: var(--plate-bg); padding: 3px 10px; border-radius: 9999px; border: 1px solid var(--plate-border); color: var(--accent-bright);">v2.0.4 • Build 55</span>
      </div>
      <div style="color: var(--ink-secondary); font-size: 13.5px; line-height: 1.5;">
        ${t('about_card_body')}
      </div>
      <div style="color: var(--ink-tertiary); font-size: 12px; margin-top: 4px;">
        ${t('about_license')}
      </div>
    </div>

    <div class="settings-section-header">${t('updates_section')}</div>
    <div class="settings-clean-group">
      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('auto_download_updates')}</div>
          <div class="settings-sublabel">${t('auto_download_updates_sub')}</div>
        </div>
        <label class="clean-switch">
          <input type="checkbox" id="chkAutoDownloadUpdates" ${autoDownload ? 'checked' : ''}>
          <span class="slider"></span>
        </label>
      </div>

      <div class="settings-clean-row">
        <div>
          <div style="display: flex; align-items: center; gap: 8px;">
            <span class="settings-label">${t('check_updates_title')}</span>
            <span class="badge" id="updateStatusBadge" style="font-size: 10px; font-weight: 800; background: rgba(16, 185, 129, 0.15); color: #10b981; padding: 2px 8px; border-radius: 9999px;">
              ${t('up_to_date_badge')}
            </span>
          </div>
          <div class="settings-sublabel" id="updateStatusSublabel">${t('check_updates_sub')}</div>
        </div>
        <button class="btn-primary plate-button settings-fixed-btn" id="btnCheckForUpdates">
          ${t('check_updates_btn')}
        </button>
      </div>
    </div>

    <div class="settings-section-header">${t('community_support')}</div>
    <div class="settings-clean-group">
      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('discord_title')}</div>
          <div class="settings-sublabel">${t('discord_sub')}</div>
        </div>
        <button class="btn-primary plate-button settings-fixed-btn" id="btnOpenDiscord">
          Discord
        </button>
      </div>

      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('kofi_title')}</div>
          <div class="settings-sublabel">${t('kofi_sub')}</div>
        </div>
        <button class="btn-secondary plate-button settings-fixed-btn" id="btnOpenKofi">
          Ko-fi
        </button>
      </div>

      <div class="settings-clean-row">
        <div>
          <div class="settings-label">${t('github_title')}</div>
          <div class="settings-sublabel">${t('github_sub')}</div>
        </div>
        <button class="btn-secondary plate-button settings-fixed-btn" id="btnOpenGithub">
          GitHub
        </button>
      </div>
    </div>
  `;
}

// 7. Crash logs page
function renderCrashLogsPage(): string {
  return `
    <div class="settings-section-header">${t('crash_logs_title')}</div>
    <div class="settings-clean-group" id="crashLogsGroup" style="padding: 24px; display: flex; flex-direction: column; gap: 14px; text-align: center;">
      <div style="width: 52px; height: 52px; border-radius: 50%; background: rgba(56, 225, 255, 0.12); color: #38E1FF; display: flex; align-items: center; justify-content: center; margin: 0 auto;">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
      </div>
      <div id="crashLogsStatusArea">
        <strong style="font-size: 15px; color: var(--ink-primary);">${t('crash_logs_empty')}</strong>
        <p style="font-size: 12px; color: var(--ink-secondary); margin-top: 4px;">${t('crash_logs_desc')}</p>
      </div>
      <div id="crashLogsListArea" style="display: none; flex-direction: column; gap: 10px; text-align: left; max-height: 320px; overflow-y: auto;"></div>
      <div style="display: flex; gap: 10px; justify-content: center; margin-top: 8px; flex-wrap: wrap;">
        <button class="btn-primary plate-button" id="btnReportIssue" style="height: 34px; padding: 0 18px; font-size: 12px; border-radius: 9999px;">
          ${t('report_issue')}
        </button>
        <button class="btn-secondary plate-button" id="btnOpenCrashFolder" style="height: 34px; padding: 0 18px; font-size: 12px; border-radius: 9999px;">
          ${t('open_folder')}
        </button>
        <button class="btn-secondary plate-button" id="btnClearCrashLogs" style="height: 34px; padding: 0 18px; font-size: 12px; border-radius: 9999px;">
          ${t('clear_logs')}
        </button>
      </div>
    </div>
  `;
}

// Detail listeners
function attachDetailListeners(view: SettingsHubView, container: HTMLElement, state: any, emuApi: any) {
  if (view === 'general') {
    // Language
    container.querySelectorAll('[data-lang-val]').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        const target = e.currentTarget as HTMLElement;
        if (target.classList.contains('active')) return;
        sfx.play('toggle');
        const seg = target.closest<HTMLElement>('.segmented-control');
        if (seg) {
          seg.querySelectorAll('.segmented-item').forEach((i) => i.classList.remove('active'));
          target.classList.add('active');
          updateSegmentedGlider(seg, true);
        }
        const val = target.dataset.langVal as any;
        await emuApi.config.updateSettings({ language: val });
        store.set({ settings: { ...state.settings, language: val } });
        // Let vibrant glider pill slide smoothly before re-rendering view with new language strings
        setTimeout(() => {
          renderSettingsScreen(container);
        }, 340);
      });
    });

    // Notifications
    container.querySelector('#chkNotifyFriends')?.addEventListener('change', async (e) => {
      const checked = (e.target as HTMLInputElement).checked;
      await emuApi.config.updateSettings({ notifyFriends: checked });
      store.set({ settings: { ...state.settings, notifyFriends: checked } });
      sfx.play('toggle');
    });

    // Coordinator URL
    container.querySelector('#btnSaveCoordUrl')?.addEventListener('click', async (e) => {
      const btn = e.currentTarget as HTMLButtonElement;
      const input = container.querySelector('#inputCoordinatorUrl') as HTMLInputElement;
      const url = input?.value.trim() || 'https://coord.emufii.xyz';
      await emuApi.config.updateSettings({ coordinatorUrl: url });
      store.set({ settings: { ...state.settings, coordinatorUrl: url } });
      sfx.play('confirm');
      const origText = btn.textContent;
      btn.textContent = '✓ ' + t('save');
      setTimeout(() => {
        btn.textContent = origText;
      }, 1800);
      checkHealth();
    });

    // Check health
    const checkHealth = async () => {
      const badge = container.querySelector('#coordHealthBadge') as HTMLElement;
      if (!badge) return;
      try {
        const health = await emuApi.coordinator.checkHealth();
        if (health && health.ok) {
          badge.textContent = `${t('online')} • ${health.count || 0} rooms`;
          badge.style.background = 'rgba(16, 185, 129, 0.15)';
          badge.style.color = '#10b981';
        } else {
          badge.textContent = t('offline');
          badge.style.background = 'rgba(239, 68, 68, 0.15)';
          badge.style.color = '#ef4444';
        }
      } catch {
        badge.textContent = t('offline');
        badge.style.background = 'rgba(239, 68, 68, 0.15)';
        badge.style.color = '#ef4444';
      }
    };
    checkHealth();
  } else if (view === 'library') {
    // Select Target Directory
    container.querySelectorAll('.btnSelectTargetDir').forEach((el) => {
      el.addEventListener('click', (e) => {
        sfx.play('click');
        const dir = (e.currentTarget as HTMLElement).dataset.dir;
        if (dir) {
          selectedTargetDir = dir;
          renderSettingsScreen(container);
        }
      });
    });

    // Helper to import ROM games into a directory
    const handleAddRoms = async (targetDir: string, triggerBtn?: HTMLElement | null) => {
      let finalDir = targetDir;
      if (!finalDir) {
        const special = await emuApi.library.getSpecialRomDirectory();
        finalDir = special?.path || '';
      }
      sfx.play('click');
      const files = await emuApi.dialog.selectRomFiles();
      if (files && files.length > 0) {
        const origText = triggerBtn ? triggerBtn.textContent : '';
        if (triggerBtn) {
          triggerBtn.textContent = '...';
        }
        try {
          const res = await emuApi.library.importRomFiles(files, finalDir);
          const scanned = await emuApi.library.scan();
          store.set({ roms: scanned });
          sfx.play('confirm');
          if (triggerBtn) {
            triggerBtn.textContent = `✓ ${res.count || files.length} ROMs`;
          }
          setTimeout(() => {
            renderSettingsScreen(container);
          }, 1400);
        } catch (err) {
          console.error('Failed to import ROMs:', err);
          if (triggerBtn && origText) triggerBtn.textContent = origText;
        }
      }
    };

    // Add ROM games to selected target
    container.querySelector('#btnAddRomGames')?.addEventListener('click', (e) => {
      handleAddRoms(selectedTargetDir, e.currentTarget as HTMLElement);
    });

    // Quick add ROM games directly from directory row
    container.querySelectorAll('.btnPathAddRom').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const dir = (e.currentTarget as HTMLElement).dataset.dir || selectedTargetDir;
        handleAddRoms(dir, e.currentTarget as HTMLElement);
      });
    });

    // Open Folder in OS file manager
    container.querySelectorAll('.btnPathOpenFolder').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        sfx.play('click');
        const dir = (e.currentTarget as HTMLElement).dataset.dir;
        if (dir) {
          await emuApi.library.openPath(dir);
        }
      });
    });

    // Open Special Directory
    container.querySelector('#btnOpenSpecialDir')?.addEventListener('click', async () => {
      sfx.play('click');
      const special = await emuApi.library.getSpecialRomDirectory();
      if (special && special.path) {
        await emuApi.library.openPath(special.path);
      }
    });

    // Add Folder
    container.querySelector('#btnSettingsAddDir')?.addEventListener('click', async () => {
      sfx.play('click');
      const selected = await emuApi.dialog.selectDirectory();
      if (selected && !state.settings.romDirectories.includes(selected)) {
        const dirs = [...state.settings.romDirectories, selected];
        await emuApi.config.updateSettings({ romDirectories: dirs });
        selectedTargetDir = selected;
        const scanned = await emuApi.library.scan();
        store.set({ roms: scanned, settings: { ...state.settings, romDirectories: dirs } });
        renderSettingsScreen(container);
      }
    });

    // Remove Folder
    container.querySelectorAll('.btnRemoveDir').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        sfx.play('click');
        const dir = (e.currentTarget as HTMLElement).dataset.dir;
        const dirs = state.settings.romDirectories.filter((d: string) => d !== dir);
        await emuApi.config.updateSettings({ romDirectories: dirs });
        const scanned = await emuApi.library.scan();
        store.set({ roms: scanned, settings: { ...state.settings, romDirectories: dirs } });
        renderSettingsScreen(container);
      });
    });

    // Rescan
    container.querySelector('#btnRescan')?.addEventListener('click', async (e) => {
      const btn = e.currentTarget as HTMLButtonElement;
      sfx.play('confirm');
      const origText = btn.textContent;
      btn.textContent = '...';
      btn.disabled = true;
      try {
        const scanned = await emuApi.library.scan();
        store.set({ roms: scanned });
        btn.textContent = `✓ ${scanned.length} games`;
      } finally {
        setTimeout(() => {
          btn.textContent = origText;
          btn.disabled = false;
        }, 2000);
      }
    });

    // SteamGridDB Key
    container.querySelector('#btnSaveSgdbKey')?.addEventListener('click', async (e) => {
      const btn = e.currentTarget as HTMLButtonElement;
      const input = container.querySelector('#inputSgdbKey') as HTMLInputElement;
      const key = input?.value.trim() || '';
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('emufii_sgdb_key', key);
      }
      await emuApi.config.updateSettings({ steamGridDbKey: key });
      const curSettings = store.get().settings;
      store.set({ settings: { ...curSettings, steamGridDbKey: key } });
      sfx.play('confirm');

      const origText = btn.textContent;
      const origColor = btn.style.color;
      const origBorder = btn.style.borderColor;
      btn.textContent = 'Key Saved';
      btn.style.color = '#10b981';
      btn.style.borderColor = 'rgba(16, 185, 129, 0.5)';
      setTimeout(() => {
        btn.textContent = origText;
        btn.style.color = origColor;
        btn.style.borderColor = origBorder;
      }, 2200);
    });

    // Restore Hidden Games
    container.querySelector('#btnRestoreHidden')?.addEventListener('click', async (e) => {
      const btn = e.currentTarget as HTMLButtonElement;
      sfx.play('confirm');
      btn.textContent = '✓ ' + t('restore_hidden');
      btn.disabled = true;
      await emuApi.config.restoreHiddenRoms();
      store.set({ settings: { ...state.settings, hiddenRoms: [] } });
      setTimeout(() => {
        renderSettingsScreen(container);
      }, 600);
    });

    // Hide Incompatible
    container.querySelector('#chkHideIncompatible')?.addEventListener('change', async (e) => {
      const checked = (e.target as HTMLInputElement).checked;
      await emuApi.config.updateSettings({ hideIncompatible: checked });
      store.set({ settings: { ...state.settings, hideIncompatible: checked } });
      sfx.play('toggle');
    });

    // Layout switcher
    container.querySelectorAll('[data-layout-val]').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        const target = e.currentTarget as HTMLElement;
        if (target.classList.contains('active')) return;
        sfx.play('toggle');
        const seg = target.closest<HTMLElement>('.segmented-control');
        if (seg) {
          seg.querySelectorAll('.segmented-item').forEach((i) => i.classList.remove('active'));
          target.classList.add('active');
          updateSegmentedGlider(seg, true);
        }
        const val = target.dataset.layoutVal as any;
        await emuApi.config.updateSettings({ libraryLayout: val });
        store.set({ settings: { ...state.settings, libraryLayout: val } });
      });
    });

    // Sorting switcher
    container.querySelectorAll('[data-sort-val]').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        const target = e.currentTarget as HTMLElement;
        if (target.classList.contains('active')) return;
        sfx.play('toggle');
        const seg = target.closest<HTMLElement>('.segmented-control');
        if (seg) {
          seg.querySelectorAll('.segmented-item').forEach((i) => i.classList.remove('active'));
          target.classList.add('active');
          updateSegmentedGlider(seg, true);
        }
        const val = target.dataset.sortVal as any;
        await emuApi.config.updateSettings({ librarySort: val });
        store.set({ settings: { ...state.settings, librarySort: val } });
      });
    });
  } else if (view === 'consoles') {
    // Console switches
    container.querySelectorAll('.chkConsoleToggle').forEach((chk) => {
      chk.addEventListener('change', async (e) => {
        sfx.play('toggle');
        const target = e.target as HTMLInputElement;
        const consoleId = target.dataset.console;
        if (!consoleId) return;

        const isVisible = target.checked;
        const hiddenList = await emuApi.config.setConsoleVisible(consoleId, isVisible);
        store.set({ settings: { ...state.settings, hiddenConsoles: hiddenList } });
      });
    });
  } else if (view === 'emulators') {
    // Load Emulators list
    const loadEmulators = async () => {
      try {
        const detectedEmulators = await emuApi.emulators.detectAll();
        const emuListEl = container.querySelector('#emulatorsListContainer');
        if (!emuListEl) return;

        const emus = Object.values(detectedEmulators) as any[];
        emuListEl.innerHTML = emus
          .map((emu) => {
            const isInstalled = emu.isInstalled;
            const pathLower = (emu.executablePath || '').toLowerCase();
            const isManaged = pathLower.includes('emufii/emulators') || pathLower.includes('emufii\\emulators');
            return `
              <div class="emulator-clean-row">
                <div class="emulator-clean-info">
                  <div class="emulator-clean-title-row">
                    <strong class="emulator-clean-name">${emu.name}</strong>
                    <span class="badge" style="font-size: 10px; font-weight: 800; background: var(--plate-bg); padding: 2px 8px; border-radius: 9999px;">${emu.console}</span>
                    <span class="badge" style="font-size: 10px; font-weight: 800; padding: 2px 8px; border-radius: 9999px; background: ${
                      isInstalled ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)'
                    }; color: ${isInstalled ? '#10b981' : '#ef4444'};">
                      ${isManaged ? 'AUTO-CONFIGURED' : isInstalled ? t('ready') : t('missing')}
                    </span>
                  </div>
                  <div class="emulator-clean-path">
                    ${emu.executablePath || 'Auto-Download Ready (Windows & Linux)'}
                  </div>
                </div>
                <div class="emulator-clean-actions">
                  <button class="btn-secondary plate-button btnSelectEmuPath" data-console="${emu.console}" data-name="${emu.name}">
                    ${t('browse_binary')}
                  </button>
                  ${
                    !isInstalled
                      ? `<button class="btn-primary plate-button btnDownloadEmu" data-console="${emu.console}">
                          ${t('download_btn')}
                        </button>`
                      : ''
                  }
                </div>
              </div>
            `;
          })
          .join('');

        // Attach Select path
        emuListEl.querySelectorAll('.btnSelectEmuPath').forEach((btn) => {
          btn.addEventListener('click', async (e) => {
            sfx.play('click');
            const targetName = (e.currentTarget as HTMLElement).dataset.name || 'Emulator';
            const selected = await emuApi.dialog.selectExecutable(targetName);
            if (selected) {
              const customPaths = { ...state.settings.customEmulatorPaths, [targetName]: selected };
              await emuApi.config.updateSettings({ customEmulatorPaths: customPaths });
              store.set({ settings: { ...state.settings, customEmulatorPaths: customPaths } });
              loadEmulators();
            }
          });
        });

        // Attach Download
        emuListEl.querySelectorAll('.btnDownloadEmu').forEach((btn) => {
          btn.addEventListener('click', async (e) => {
            sfx.play('click');
            const targetConsole = (e.currentTarget as HTMLElement).dataset.console;
            if (targetConsole) {
              await ensureEmulator(targetConsole);
              loadEmulators();
            }
          });
        });
      } catch (err) {
        console.error('Failed to load emulators:', err);
      }
    };

    loadEmulators();

    // Download All
    container.querySelector('#btnDownloadAllEmus')?.addEventListener('click', async (e) => {
      sfx.play('click');
      const btn = e.currentTarget as HTMLButtonElement;
      const origText = btn.textContent;
      btn.textContent = '...';
      btn.disabled = true;
      try {
        const supportedDownloads = ['GameCube', 'PS2', 'PSP', 'DS', 'GBA', '3DS'];
        for (const c of supportedDownloads) {
          await ensureEmulator(c);
        }
        await loadEmulators();
      } finally {
        btn.textContent = origText;
        btn.disabled = false;
      }
    });
  } else if (view === 'appearance') {
    // Themes
    container.querySelectorAll('[data-theme-val]').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        const target = e.currentTarget as HTMLElement;
        if (target.classList.contains('active')) return;
        sfx.play('toggle');
        const seg = target.closest<HTMLElement>('.segmented-control');
        if (seg) {
          seg.querySelectorAll('.segmented-item').forEach((i) => i.classList.remove('active'));
          target.classList.add('active');
          updateSegmentedGlider(seg, true);
        }
        const val = target.dataset.themeVal as any;
        await emuApi.config.updateSettings({ theme: val });
        store.set({ settings: { ...state.settings, theme: val } });
        document.body.dataset.theme = resolveTheme(val);
      });
    });

    // Accents
    container.querySelectorAll('[data-accent-val]').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        const target = e.currentTarget as HTMLElement;
        if (target.classList.contains('active')) return;
        sfx.play('toggle');
        const bar = target.closest<HTMLElement>('.accent-dots-bar');
        if (bar) {
          bar.querySelectorAll('.accent-dot').forEach((d) => d.classList.remove('active'));
          target.classList.add('active');
        }
        const val = target.dataset.accentVal as any;
        await emuApi.config.updateSettings({ accent: val });
        store.set({ settings: { ...state.settings, accent: val } });
        document.body.dataset.accent = val;
        setupAllSegmentedGliders(container);
      });
    });

    // Sound effects
    container.querySelector('#chkSoundEffects')?.addEventListener('change', async (e) => {
      const checked = (e.target as HTMLInputElement).checked;
      await emuApi.config.updateSettings({ soundEffects: checked });
      sfx.setEnabled(checked);
      store.set({ settings: { ...state.settings, soundEffects: checked } });
    });

    // Animations
    container.querySelector('#chkAnimations')?.addEventListener('change', async (e) => {
      const checked = (e.target as HTMLInputElement).checked;
      await emuApi.config.updateSettings({ animations: checked });
      store.set({ settings: { ...state.settings, animations: checked } });
    });
  } else if (view === 'about') {
    container.querySelector('#chkAutoDownloadUpdates')?.addEventListener('change', async (e) => {
      const checked = (e.target as HTMLInputElement).checked;
      await emuApi.config.updateSettings({ autoDownloadUpdates: checked });
      store.set({ settings: { ...store.get().settings, autoDownloadUpdates: checked } });
      sfx.play('toggle');
    });

    const runUpdateCheck = async (openModalAlways: boolean) => {
      const badge = container.querySelector('#updateStatusBadge') as HTMLElement | null;
      const sublabel = container.querySelector('#updateStatusSublabel') as HTMLElement | null;
      const btn = container.querySelector('#btnCheckForUpdates') as HTMLButtonElement | null;
      if (btn) {
        btn.disabled = true;
        btn.textContent = t('checking');
      }
      if (badge) {
        badge.textContent = t('checking');
        badge.style.background = 'rgba(59, 130, 246, 0.15)';
        badge.style.color = '#3b82f6';
      }
      try {
        const res = await emuApi.updates.check();
        if (res?.updateAvailable) {
          if (badge) {
            badge.textContent = `${t('update_available_badge')} (v${res.latestVersion})`;
            badge.style.background = 'rgba(245, 158, 11, 0.18)';
            badge.style.color = '#f59e0b';
          }
          if (sublabel) {
            sublabel.textContent = `Version ${res.latestVersion} is ready to install (current: v${res.currentVersion})`;
          }
          if (openModalAlways) {
            openUpdateModal(res);
          }
        } else {
          if (badge) {
            badge.textContent = `${t('up_to_date_badge')} (v${res?.currentVersion || '2.0.4'})`;
            badge.style.background = 'rgba(16, 185, 129, 0.15)';
            badge.style.color = '#10b981';
          }
          if (sublabel) {
            sublabel.textContent = `You are running the latest version (v${res?.currentVersion || '2.0.4'}).`;
          }
          if (openModalAlways && res) {
            openUpdateModal(res);
          }
        }
      } catch {
        if (badge) {
          badge.textContent = t('up_to_date_badge');
        }
      } finally {
        if (btn) {
          btn.disabled = false;
          btn.textContent = t('check_updates_btn');
        }
      }
    };

    runUpdateCheck(false);

    container.querySelector('#btnCheckForUpdates')?.addEventListener('click', () => {
      sfx.play('click');
      runUpdateCheck(true);
    });

    container.querySelector('#btnOpenDiscord')?.addEventListener('click', () => {
      sfx.play('click');
      const url = 'https://discord.gg/tvWcb28vBZ';
      if (emuApi?.openExternal) {
        emuApi.openExternal(url);
      } else {
        window.open(url, '_blank');
      }
    });
    container.querySelector('#btnOpenKofi')?.addEventListener('click', () => {
      sfx.play('click');
      const url = 'https://ko-fi.com/emufii';
      if (emuApi?.openExternal) {
        emuApi.openExternal(url);
      } else {
        window.open(url, '_blank');
      }
    });
    container.querySelector('#btnOpenGithub')?.addEventListener('click', () => {
      sfx.play('click');
      const url = 'https://github.com/emufii';
      if (emuApi?.openExternal) {
        emuApi.openExternal(url);
      } else {
        window.open(url, '_blank');
      }
    });
  } else if (view === 'crashlogs') {
    const loadCrashLogs = async () => {
      const statusArea = container.querySelector('#crashLogsStatusArea') as HTMLElement | null;
      const listArea = container.querySelector('#crashLogsListArea') as HTMLElement | null;
      if (!emuApi?.crashLogs?.list || !listArea || !statusArea) return;
      try {
        const logs = await emuApi.crashLogs.list();
        if (Array.isArray(logs) && logs.length > 0) {
          statusArea.innerHTML = `
            <strong style="font-size: 15px; color: var(--ink-primary);">${logs.length} ${logs.length === 1 ? 'crash log' : 'crash logs'} recorded</strong>
            <p style="font-size: 12px; color: var(--ink-secondary); margin-top: 4px;">${t('crash_logs_desc')}</p>
          `;
          listArea.style.display = 'flex';
          listArea.innerHTML = logs
            .map(
              (log: any, idx: number) => `
              <div class="plate" style="padding: 12px 14px; border-radius: 14px; background: var(--plate-bg-low);">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
                  <strong style="font-size: 12px; font-family: monospace; color: var(--ink-primary);">${log.name}</strong>
                  <button class="btn-secondary plate-button btnCopyCrashLog" data-idx="${idx}" style="height: 24px; padding: 0 10px; font-size: 10px; border-radius: 9999px;">Copy</button>
                </div>
                <pre style="margin: 0; font-size: 11px; color: var(--ink-secondary); white-space: pre-wrap; word-break: break-word; max-height: 120px; overflow-y: auto;">${String(log.content || '').replace(/</g, '&lt;')}</pre>
              </div>
            `
            )
            .join('');
          listArea.querySelectorAll('.btnCopyCrashLog').forEach((btn) => {
            btn.addEventListener('click', (e) => {
              const idx = Number((e.currentTarget as HTMLElement).dataset.idx || 0);
              const item = logs[idx];
              if (item?.content) {
                navigator.clipboard.writeText(item.content);
                sfx.play('confirm');
              }
            });
          });
        } else {
          listArea.style.display = 'none';
          listArea.innerHTML = '';
          statusArea.innerHTML = `
            <strong style="font-size: 15px; color: var(--ink-primary);">${t('crash_logs_empty')}</strong>
            <p style="font-size: 12px; color: var(--ink-secondary); margin-top: 4px;">${t('crash_logs_desc')}</p>
          `;
        }
      } catch {}
    };

    loadCrashLogs();

    container.querySelector('#btnReportIssue')?.addEventListener('click', () => {
      sfx.play('click');
      const url = 'https://github.com/emufii/emufii/issues';
      if (emuApi?.openExternal) {
        emuApi.openExternal(url);
      } else {
        window.open(url, '_blank');
      }
    });
    container.querySelector('#btnOpenCrashFolder')?.addEventListener('click', () => {
      sfx.play('click');
      emuApi?.crashLogs?.openFolder?.();
    });
    container.querySelector('#btnClearCrashLogs')?.addEventListener('click', async (e) => {
      sfx.play('confirm');
      const btn = e.currentTarget as HTMLButtonElement;
      const origText = btn.textContent;
      await emuApi?.crashLogs?.clear?.();
      await loadCrashLogs();
      btn.textContent = '✓ Cleared';
      btn.disabled = true;
      setTimeout(() => {
        btn.textContent = origText;
        btn.disabled = false;
      }, 1800);
    });
  }
}
