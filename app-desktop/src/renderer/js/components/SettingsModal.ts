import { store } from '../state.js';
import { sfx } from '../sfx.js';
import { t } from '../i18n.js';
import type { EmulatorInfo } from '../../../main/emulators/manager.js';
import { CONSOLES_LIST, updateSegmentedGlider, setupAllSegmentedGliders } from '../screens/SettingsScreen.js';
import { ensureEmulator } from './EmulatorDownloadModal.js';
import { openUpdateModal } from './UpdateModal.js';

export type SettingsModalTab = 'general' | 'library' | 'consoles' | 'emulators' | 'appearance' | 'about' | 'crashlogs';

let activeTab: SettingsModalTab = 'general';
let modalKeydownHandler: ((e: KeyboardEvent) => void) | null = null;

export function resolveTheme(theme: string): string {
  if (theme === 'system') {
    const isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    return isDark ? 'dark' : 'light';
  }
  if (theme === 'day') return 'light';
  if (theme === 'night') return 'dark';
  return theme || 'dark';
}

export function openSettingsModal(defaultTab?: SettingsModalTab) {
  const dialog = document.getElementById('settingsModal') as HTMLDialogElement;
  if (!dialog) return;

  if (defaultTab) {
    activeTab = defaultTab;
  }
  renderSettingsContent(dialog);
  dialog.showModal();

  if (modalKeydownHandler) {
    window.removeEventListener('keydown', modalKeydownHandler);
  }
  modalKeydownHandler = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      closeSettingsModal();
    }
  };
  window.addEventListener('keydown', modalKeydownHandler);
}

export function closeSettingsModal() {
  const dialog = document.getElementById('settingsModal') as HTMLDialogElement;
  if (modalKeydownHandler) {
    window.removeEventListener('keydown', modalKeydownHandler);
    modalKeydownHandler = null;
  }
  if (!dialog || !dialog.open) return;

  dialog.setAttribute('data-closing', 'true');
  setTimeout(() => {
    dialog.close();
    dialog.removeAttribute('data-closing');
  }, 220);
}

async function renderSettingsContent(dialog: HTMLDialogElement) {
  const state = store.get();
  const settings = state.settings;
  const emuApi = (window as any).emufii;

  const content = dialog.querySelector('.dialog-content') as HTMLElement;
  if (!content) return;

  let detectedEmulators: Record<string, EmulatorInfo> = {};
  if (activeTab === 'emulators' && emuApi?.emulators?.detectAll) {
    try {
      detectedEmulators = await emuApi.emulators.detectAll();
    } catch {
      detectedEmulators = {};
    }
  }

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

  content.innerHTML = `
    <div class="cloud-card settings-cloud-card" style="max-height: calc(100vh - 64px); height: min(600px, calc(100vh - 64px)); display: flex; flex-direction: column; overflow: hidden;">
      <!-- Modal Header -->
      <div class="cloud-header" style="flex-shrink: 0;">
        <div style="display: flex; align-items: center; gap: 10px;">
          <div class="cloud-icon-badge">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
          </div>
          <div>
            <h2 class="cloud-title">${t('settings_title')}</h2>
            <p class="cloud-subtitle">${t('settings_subtitle')}</p>
          </div>
        </div>
        <button class="cloud-close-btn plate-button" id="btnCloseSettingsModal" title="Close">&times;</button>
      </div>

      <!-- Settings Navigation Tabs in exact requested order -->
      <div class="settings-modal-tabs" style="flex-shrink: 0; overflow-x: auto; gap: 4px;">
        <button class="settings-tab-btn ${activeTab === 'general' ? 'active' : ''}" data-tab="general">${t('general')}</button>
        <button class="settings-tab-btn ${activeTab === 'library' ? 'active' : ''}" data-tab="library">${t('library')}</button>
        <button class="settings-tab-btn ${activeTab === 'consoles' ? 'active' : ''}" data-tab="consoles">${t('consoles')}</button>
        <button class="settings-tab-btn ${activeTab === 'emulators' ? 'active' : ''}" data-tab="emulators">${t('emulators')}</button>
        <button class="settings-tab-btn ${activeTab === 'appearance' ? 'active' : ''}" data-tab="appearance">${t('appearance')}</button>
        <button class="settings-tab-btn ${activeTab === 'about' ? 'active' : ''}" data-tab="about">${t('about')}</button>
        <button class="settings-tab-btn ${activeTab === 'crashlogs' ? 'active' : ''}" data-tab="crashlogs">${t('crash_logs')}</button>
      </div>

      <!-- Settings Content Body -->
      <div class="cloud-body settings-cloud-body" style="overflow-y: auto; flex: 1 1 auto; min-height: 0; padding-bottom: 34px;">
        ${renderModalTabBody(activeTab, state, settings, accents, detectedEmulators)}
      </div>
    </div>
  `;

  // Attach tab switching
  content.querySelectorAll('.settings-tab-btn').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      sfx.play('click');
      const tab = (e.currentTarget as HTMLElement).dataset.tab as SettingsModalTab;
      if (tab) {
        activeTab = tab;
        renderSettingsContent(dialog);
      }
    });
  });

  // Attach close button
  content.querySelector('#btnCloseSettingsModal')?.addEventListener('click', () => {
    sfx.play('click');
    closeSettingsModal();
  });

  // Attach tab listeners
  attachModalTabListeners(activeTab, content, dialog, state, emuApi);
  requestAnimationFrame(() => {
    setupAllSegmentedGliders(dialog);
    setTimeout(() => setupAllSegmentedGliders(dialog), 40);
  });

  // Backdrop click to close
  dialog.onclick = (e) => {
    if (e.target === dialog) {
      sfx.play('click');
      closeSettingsModal();
    }
  };
}

function renderModalTabBody(
  tab: SettingsModalTab,
  state: any,
  settings: any,
  accents: any[],
  detected: Record<string, EmulatorInfo>
): string {
  switch (tab) {
    case 'general':
      const currentLang = settings.language || 'system';
      return `
        <div style="display: flex; flex-direction: column; gap: 14px;">
          <div class="settings-clean-group" style="margin-bottom: 0;">
            <div class="settings-clean-row">
              <div>
                <div class="settings-label">${t('app_language')}</div>
                <div class="settings-sublabel">${t('app_language_sub')}</div>
              </div>
              <div class="segmented-control">
                <span class="segmented-glider"></span>
                ${[
                  { id: 'system', name: 'System' },
                  { id: 'en', name: 'EN' },
                  { id: 'es', name: 'ES' },
                  { id: 'fr', name: 'FR' },
                ]
                  .map(
                    (l) => `
                  <button class="segmented-item ${currentLang === l.id ? 'active' : ''}" data-modal-lang="${l.id}">
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
                <input type="checkbox" id="chkModalNotify" ${settings.notifyFriends !== false ? 'checked' : ''}>
                <span class="slider"></span>
              </label>
            </div>

            <div class="settings-clean-row">
              <div>
                <div class="settings-label">${t('coordinator_url')}</div>
                <div class="settings-sublabel">${t('coordinator_url_sub')}</div>
              </div>
              <div style="display: flex; gap: 6px;">
                <input type="text" id="inputModalCoordUrl" value="${settings.coordinatorUrl || 'https://coord.emufii.xyz'}" class="plate" style="font-size: 11px; padding: 6px 10px; width: 160px; border-radius: 8px; color: var(--ink-primary);">
                <button class="btn-secondary plate-button" id="btnSaveModalCoordUrl" style="height: 28px; padding: 0 10px; font-size: 11px; border-radius: 9999px;">${t('save')}</button>
              </div>
            </div>
          </div>
        </div>
      `;

    case 'library':
      const dirs: string[] = settings.romDirectories || [];
      const hiddenCount = (settings.hiddenRoms || []).length;
      return `
        <div style="display: flex; flex-direction: column; gap: 14px; padding-bottom: 20px;">
          <div style="display: flex; gap: 8px; flex-wrap: wrap;">
            <button class="btn-primary plate-button" id="btnModalAddRoms" style="height: 32px; padding: 0 14px; font-size: 12px; border-radius: 9999px; display: inline-flex; align-items: center; gap: 6px;">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
              <span>${t('add_roms_btn')}</span>
            </button>
            <button class="btn-secondary plate-button" id="btnModalAddDir" style="height: 32px; padding: 0 14px; font-size: 12px; border-radius: 9999px;">${t('add_folder')}</button>
            <button class="btn-secondary plate-button" id="btnModalOpenSpecial" style="height: 32px; padding: 0 14px; font-size: 12px; border-radius: 9999px; color: var(--accent-bright);">⭐ ${t('open_folder')}</button>
            <button class="btn-secondary plate-button" id="btnModalRescan" style="height: 32px; padding: 0 14px; font-size: 12px; border-radius: 9999px;">${t('rescan_library')}</button>
          </div>

          <div class="settings-clean-group" style="margin-bottom: 0;">
            ${
              dirs.length === 0
                ? `<div style="padding: 14px 18px; color: var(--ink-secondary); font-size: 12px;">${t('no_directories')}</div>`
                : dirs
                    .map(
                      (d) => `
                <div class="path-clean-row">
                  <div style="display: flex; align-items: center; gap: 8px; overflow: hidden; flex: 1;">
                    ${
                      d.includes('Emufii/ROMs') || d.endsWith('/ROMs') || d.endsWith('\\ROMs')
                        ? `<span class="badge" style="font-size: 9px; font-weight: 800; background: rgba(56, 225, 255, 0.15); color: var(--accent-bright); border: 1px solid var(--accent-subtle);">⭐</span>`
                        : ''
                    }
                    <span style="font-family: var(--font-family); font-size: 12px; color: var(--ink-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${d}</span>
                  </div>
                  <div style="display: flex; align-items: center; gap: 4px;">
                    <button class="btnModalOpenPath" data-dir="${d}" title="${t('open_folder')}" style="background: transparent; border: none; color: var(--ink-secondary); font-size: 14px; cursor: pointer; padding: 2px 6px;">
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>
                    </button>
                    <button class="btn-remove-path" data-dir="${d}" title="${t('remove')}" style="background: transparent; border: none; color: var(--compat-broken); font-size: 16px; cursor: pointer; padding: 2px 6px;">&times;</button>
                  </div>
                </div>
              `
                    )
                    .join('')
            }
          </div>

          <div class="settings-clean-group" style="margin-bottom: 0;">
            <div class="settings-clean-row">
              <div>
                <div class="settings-label">${t('sgdb_key')}</div>
                <div class="settings-sublabel">${t('sgdb_key_sub')}</div>
              </div>
              <div style="display: flex; gap: 6px;">
                <input type="password" id="inputModalSgdb" class="plate" placeholder="Key..." value="${settings.steamGridDbKey || ''}" style="padding: 6px 10px; font-size: 11px; width: 140px; border-radius: 8px; color: var(--ink-primary);">
                <button class="btn-secondary plate-button" id="btnSaveModalSgdb" style="height: 28px; padding: 0 10px; font-size: 11px; border-radius: 9999px;">${t('save')}</button>
              </div>
            </div>

            <div class="settings-clean-row">
              <div>
                <div class="settings-label">${t('hidden_games')} (${hiddenCount})</div>
                <div class="settings-sublabel">${t('restore_hidden')}</div>
              </div>
              <button class="btn-secondary plate-button" id="btnModalRestoreHidden" style="height: 28px; padding: 0 12px; font-size: 11px; border-radius: 9999px;" ${hiddenCount === 0 ? 'disabled style="opacity: 0.5;"' : ''}>${t('restore')}</button>
            </div>

            <div class="settings-clean-row">
              <div>
                <div class="settings-label">${t('hide_incompatible')}</div>
                <div class="settings-sublabel">${t('hide_incompatible_sub')}</div>
              </div>
              <label class="clean-switch">
                <input type="checkbox" id="chkModalHideIncompatible" ${settings.hideIncompatible ? 'checked' : ''}>
                <span class="slider"></span>
              </label>
            </div>
          </div>
        </div>
      `;

    case 'consoles':
      const hiddenConsoles: string[] = settings.hiddenConsoles || [];
      return `
        <div style="display: flex; flex-direction: column; gap: 10px;">
          <p style="font-size: 12px; color: var(--ink-secondary); margin-bottom: 4px;">
            ${t('consoles_note')}
          </p>
          <div class="settings-clean-group" style="margin-bottom: 0;">
            ${CONSOLES_LIST.map((c) => {
              const isVisible = !hiddenConsoles.includes(c.id);
              return `
                <div class="settings-clean-row" style="padding: 10px 16px;">
                  <div>
                    <strong style="font-size: 13px; color: var(--ink-primary);">${c.name}</strong>
                    <div style="font-size: 11px; color: var(--ink-secondary);">${c.desc}</div>
                  </div>
                  <label class="clean-switch">
                    <input type="checkbox" class="chkModalConsole" data-console="${c.id}" ${isVisible ? 'checked' : ''}>
                    <span class="slider"></span>
                  </label>
                </div>
              `;
            }).join('')}
          </div>
        </div>
      `;

    case 'emulators':
      const emuEntries = Object.entries(detected || {});
      return `
        <div style="display: flex; flex-direction: column; gap: 10px;">
          <div style="display: flex; justify-content: flex-end;">
            <button class="btn-primary plate-button" id="btnModalDownloadAllEmus" style="height: 28px; padding: 0 12px; font-size: 11px; border-radius: 9999px; width: auto;">
              ${t('download_all')}
            </button>
          </div>
          <div class="settings-clean-group" style="margin-bottom: 0;">
            ${emuEntries.length === 0 ? '<div style="padding: 16px; text-align: center; font-size: 12px; color: var(--ink-secondary);">Scanning emulators...</div>' : ''}
            ${emuEntries
              .map(([consoleKey, emu]) => {
                const isInstalled = emu.isInstalled;
                const pathLower = (emu.executablePath || '').toLowerCase();
                const isManaged = pathLower.includes('emufii/emulators') || pathLower.includes('emufii\\emulators');
                return `
                  <div class="settings-clean-row" style="padding: 10px 16px;">
                    <div>
                      <div style="display: flex; align-items: center; gap: 8px;">
                        <strong style="font-size: 13px; color: var(--ink-primary);">${emu.name}</strong>
                        <span style="font-size: 10px; color: var(--ink-secondary); font-weight: 600;">(${consoleKey})</span>
                        <span class="badge" style="font-size: 9px; font-weight: 800; background: ${
                          isInstalled ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)'
                        }; color: ${isInstalled ? '#10b981' : '#ef4444'};">
                          ${isManaged ? 'AUTO-CONFIGURED' : isInstalled ? t('ready').toUpperCase() : t('missing').toUpperCase()}
                        </span>
                      </div>
                      <div style="font-size: 11px; color: var(--ink-secondary); max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                        ${emu.executablePath || 'Auto-Download Ready (Windows & Linux)'}
                      </div>
                    </div>
                    <div style="display: flex; gap: 6px;">
                      <button class="btn-secondary plate-button btnModalBrowseEmu" data-console="${consoleKey}" data-name="${emu.name}" style="height: 28px; padding: 0 10px; font-size: 11px; border-radius: 9999px;">
                        ${t('browse_binary')}
                      </button>
                      ${
                        !isInstalled
                          ? `<button class="btn-primary plate-button btnModalDownloadEmu" data-console="${consoleKey}" style="height: 28px; padding: 0 10px; font-size: 11px; border-radius: 9999px; width: auto;">
                              ${t('download_btn')}
                            </button>`
                          : ''
                      }
                    </div>
                  </div>
                `;
              })
              .join('')}
          </div>
        </div>
      `;

    case 'appearance':
      const rawTheme = settings.theme;
      const currentTheme =
        rawTheme === 'day' ? 'light' : rawTheme === 'night' ? 'dark' : rawTheme || 'dark';
      return `
        <div style="display: flex; flex-direction: column; gap: 14px;">
          <div class="settings-clean-group" style="margin-bottom: 0;">
            <div class="settings-clean-row">
              <div>
                <div class="settings-label">${t('theme_floor')}</div>
                <div class="settings-sublabel">${t('theme_floor_sub')}</div>
              </div>
              <div class="segmented-control">
                <span class="segmented-glider"></span>
                ${['system', 'light', 'dark', 'oled']
                  .map(
                    (themeVal) => `
                  <button class="segmented-item ${currentTheme === themeVal ? 'active' : ''}" data-theme="${themeVal}">
                    ${themeVal === 'system' ? 'System' : themeVal === 'light' ? 'Light' : themeVal === 'dark' ? 'Dark' : 'OLED'}
                  </button>
                `
                  )
                  .join('')}
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
                    (acc) => `
                  <div class="accent-dot ${settings.accent === acc.id ? 'active' : ''}" data-accent="${acc.id}" style="background-color: ${acc.hex};" title="${acc.name}"></div>
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
                <input type="checkbox" id="chkModalSfx" ${settings.soundEffects !== false ? 'checked' : ''}>
                <span class="slider"></span>
              </label>
            </div>

            <div class="settings-clean-row">
              <div>
                <div class="settings-label">${t('fluid_animations')}</div>
                <div class="settings-sublabel">${t('fluid_animations_sub')}</div>
              </div>
              <label class="clean-switch">
                <input type="checkbox" id="chkModalAnim" ${settings.animations !== false ? 'checked' : ''}>
                <span class="slider"></span>
              </label>
            </div>
          </div>
        </div>
      `;

    case 'about':
      return `
        <div style="display: flex; flex-direction: column; gap: 14px;">
          <div class="settings-clean-group" style="padding: 18px; margin-bottom: 0; display: flex; flex-direction: column; gap: 8px;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <strong style="color: var(--ink-primary); font-size: 16px;">${t('about_card_title')}</strong>
              <span class="badge" style="font-size: 10px; font-weight: 800; background: var(--plate-bg);">v2.0.4 (55)</span>
            </div>
            <div style="color: var(--ink-secondary); font-size: 12px; line-height: 1.4;">
              ${t('about_card_body')}
            </div>
            <div style="color: var(--ink-tertiary); font-size: 11px;">
              ${t('about_license')}
            </div>
          </div>

          <div class="settings-clean-group" style="margin-bottom: 0;">
            <div class="settings-clean-row">
              <div>
                <div class="settings-label">${t('auto_download_updates')}</div>
                <div class="settings-sublabel">${t('auto_download_updates_sub')}</div>
              </div>
              <label class="clean-switch">
                <input type="checkbox" id="chkModalAutoDownloadUpdates" ${settings.autoDownloadUpdates !== false ? 'checked' : ''}>
                <span class="slider"></span>
              </label>
            </div>
            <div class="settings-clean-row">
              <div>
                <div class="settings-label">${t('check_updates_title')}</div>
                <div class="settings-sublabel">${t('check_updates_sub')}</div>
              </div>
              <button class="btn-primary plate-button" id="btnModalCheckForUpdates" style="height: 30px; padding: 0 14px; font-size: 11px; border-radius: 9999px; width: auto;">
                ${t('check_updates_btn')}
              </button>
            </div>
          </div>

          <div style="display: flex; gap: 8px;">
            <button class="btn-primary plate-button" id="btnModalDiscord" style="flex: 1; height: 32px; font-size: 11px; border-radius: 9999px;">Discord</button>
            <button class="btn-secondary plate-button" id="btnModalKofi" style="flex: 1; height: 32px; font-size: 11px; border-radius: 9999px;">Ko-fi</button>
          </div>
        </div>
      `;

    case 'crashlogs':
      return `
        <div style="display: flex; flex-direction: column; gap: 14px;">
          <div class="settings-clean-group" style="padding: 20px; margin-bottom: 0; text-align: center; display: flex; flex-direction: column; gap: 8px;">
            <strong style="font-size: 14px; color: var(--ink-primary);">${t('crash_logs_empty')}</strong>
            <p style="font-size: 11px; color: var(--ink-secondary);">${t('crash_logs_desc')}</p>
          </div>
          <div style="display: flex; gap: 8px;">
            <button class="btn-primary plate-button" id="btnModalReportIssue" style="flex: 1; height: 32px; font-size: 11px; border-radius: 9999px;">${t('report_issue')}</button>
            <button class="btn-secondary plate-button" id="btnModalClearLogs" style="flex: 1; height: 32px; font-size: 11px; border-radius: 9999px;">${t('clear_logs')}</button>
          </div>
        </div>
      `;
  }
}

function attachModalTabListeners(
  tab: SettingsModalTab,
  content: HTMLElement,
  dialog: HTMLDialogElement,
  state: any,
  emuApi: any
) {
  if (tab === 'general') {
    content.querySelectorAll('[data-modal-lang]').forEach((btn) => {
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
        const val = target.dataset.modalLang as any;
        await emuApi.config.updateSettings({ language: val });
        store.set({ settings: { ...state.settings, language: val } });
        setTimeout(() => {
          renderSettingsContent(dialog);
        }, 340);
      });
    });

    content.querySelector('#chkModalNotify')?.addEventListener('change', async (e) => {
      const checked = (e.target as HTMLInputElement).checked;
      await emuApi.config.updateSettings({ notifyFriends: checked });
      store.set({ settings: { ...state.settings, notifyFriends: checked } });
      sfx.play('toggle');
    });

    content.querySelector('#btnSaveModalCoordUrl')?.addEventListener('click', async () => {
      const input = content.querySelector('#inputModalCoordUrl') as HTMLInputElement;
      const url = input?.value.trim() || 'https://coord.emufii.xyz';
      await emuApi.config.updateSettings({ coordinatorUrl: url });
      store.set({ settings: { ...state.settings, coordinatorUrl: url } });
      sfx.play('confirm');
      alert(t('save') + '!');
    });
  } else if (tab === 'library') {
    content.querySelector('#btnModalAddRoms')?.addEventListener('click', async () => {
      sfx.play('click');
      const files = await emuApi.dialog.selectRomFiles();
      if (files && files.length > 0) {
        const dirs = state.settings.romDirectories || [];
        const targetDir = dirs[0] || (await emuApi.library.getSpecialRomDirectory())?.path;
        await emuApi.library.importRomFiles(files, targetDir);
        const scanned = await emuApi.library.scan();
        store.set({ roms: scanned });
        sfx.play('confirm');
        renderSettingsContent(dialog);
      }
    });

    content.querySelector('#btnModalOpenSpecial')?.addEventListener('click', async () => {
      sfx.play('click');
      const special = await emuApi.library.getSpecialRomDirectory();
      if (special && special.path) {
        await emuApi.library.openPath(special.path);
      }
    });

    content.querySelectorAll('.btnModalOpenPath').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        sfx.play('click');
        const dir = (e.currentTarget as HTMLElement).dataset.dir;
        if (dir) {
          await emuApi.library.openPath(dir);
        }
      });
    });

    content.querySelector('#btnModalAddDir')?.addEventListener('click', async () => {
      sfx.play('click');
      const selected = await emuApi.dialog.selectDirectory();
      if (selected) {
        await emuApi.library.addDirectory(selected);
        const scanned = await emuApi.library.scan();
        const updatedDirs = [...(state.settings.romDirectories || []), selected];
        store.set({ roms: scanned, settings: { ...state.settings, romDirectories: updatedDirs } });
        renderSettingsContent(dialog);
      }
    });

    content.querySelector('#btnModalRescan')?.addEventListener('click', async () => {
      sfx.play('click');
      const scanned = await emuApi.library.scan();
      store.set({ roms: scanned });
      renderSettingsContent(dialog);
    });

    content.querySelectorAll('.btn-remove-path').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        sfx.play('click');
        const dir = (e.currentTarget as HTMLElement).dataset.dir;
        if (dir) {
          await emuApi.library.removeDirectory(dir);
          const scanned = await emuApi.library.scan();
          const updatedDirs = (state.settings.romDirectories || []).filter((d: string) => d !== dir);
          store.set({ roms: scanned, settings: { ...state.settings, romDirectories: updatedDirs } });
          renderSettingsContent(dialog);
        }
      });
    });

    content.querySelector('#btnSaveModalSgdb')?.addEventListener('click', async () => {
      const input = content.querySelector('#inputModalSgdb') as HTMLInputElement;
      const key = input?.value.trim() || '';
      await emuApi.config.updateSettings({ steamGridDbKey: key });
      store.set({ settings: { ...state.settings, steamGridDbKey: key } });
      sfx.play('confirm');
      alert(t('save') + '!');
    });

    content.querySelector('#btnModalRestoreHidden')?.addEventListener('click', async () => {
      sfx.play('confirm');
      await emuApi.config.restoreHiddenRoms();
      store.set({ settings: { ...state.settings, hiddenRoms: [] } });
      renderSettingsContent(dialog);
      alert(t('restore_hidden') + '!');
    });

    content.querySelector('#chkModalHideIncompatible')?.addEventListener('change', async (e) => {
      const checked = (e.target as HTMLInputElement).checked;
      await emuApi.config.updateSettings({ hideIncompatible: checked });
      store.set({ settings: { ...state.settings, hideIncompatible: checked } });
      sfx.play('toggle');
    });
  } else if (tab === 'consoles') {
    content.querySelectorAll('.chkModalConsole').forEach((chk) => {
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
  } else if (tab === 'emulators') {
    content.querySelectorAll('.btnModalBrowseEmu').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        sfx.play('click');
        const targetConsole = (e.currentTarget as HTMLElement).dataset.console || '';
        const targetName = (e.currentTarget as HTMLElement).dataset.name || 'Emulator';
        const selected = await emuApi.dialog.selectExecutable(targetName);
        if (selected) {
          const customPaths = {
            ...state.settings.customEmulatorPaths,
            [targetName]: selected,
            ...(targetConsole ? { [targetConsole]: selected } : {}),
          };
          await emuApi.config.updateSettings({ customEmulatorPaths: customPaths });
          store.set({ settings: { ...state.settings, customEmulatorPaths: customPaths } });
          renderSettingsContent(dialog);
        }
      });
    });

    content.querySelectorAll('.btnModalDownloadEmu').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        sfx.play('click');
        const targetConsole = (e.currentTarget as HTMLElement).dataset.console;
        if (targetConsole) {
          await ensureEmulator(targetConsole);
          renderSettingsContent(dialog);
        }
      });
    });

    content.querySelector('#btnModalDownloadAllEmus')?.addEventListener('click', async () => {
      sfx.play('click');
      const supportedDownloads = ['GameCube', 'PS2', 'PSP', 'DS', 'GBA', '3DS'];
      for (const c of supportedDownloads) {
        await ensureEmulator(c);
      }
      renderSettingsContent(dialog);
    });
  } else if (tab === 'appearance') {
    content.querySelectorAll('[data-theme]').forEach((btn) => {
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
        const theme = target.dataset.theme as any;
        document.body.dataset.theme = resolveTheme(theme);
        await emuApi.config.updateSettings({ theme });
        store.set({ settings: { ...store.get().settings, theme } });
      });
    });

    content.querySelectorAll('[data-accent]').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        const target = e.currentTarget as HTMLElement;
        if (target.classList.contains('active')) return;
        sfx.play('toggle');
        const bar = target.closest<HTMLElement>('.accent-dots-bar');
        if (bar) {
          bar.querySelectorAll('.accent-dot').forEach((d) => d.classList.remove('active'));
          target.classList.add('active');
        }
        const accent = target.dataset.accent as any;
        document.body.dataset.accent = accent;
        await emuApi.config.updateSettings({ accent });
        store.set({ settings: { ...store.get().settings, accent } });
        setupAllSegmentedGliders(dialog);
      });
    });

    content.querySelector('#chkModalSfx')?.addEventListener('change', async (e) => {
      const val = (e.target as HTMLInputElement).checked;
      sfx.setEnabled(val);
      await emuApi.config.updateSettings({ soundEffects: val });
      store.set({ settings: { ...store.get().settings, soundEffects: val } });
    });

    content.querySelector('#chkModalAnim')?.addEventListener('change', async (e) => {
      const val = (e.target as HTMLInputElement).checked;
      await emuApi.config.updateSettings({ animations: val });
      store.set({ settings: { ...store.get().settings, animations: val } });
    });
  } else if (tab === 'about') {
    content.querySelector('#chkModalAutoDownloadUpdates')?.addEventListener('change', async (e) => {
      const checked = (e.target as HTMLInputElement).checked;
      await emuApi.config.updateSettings({ autoDownloadUpdates: checked });
      store.set({ settings: { ...store.get().settings, autoDownloadUpdates: checked } });
      sfx.play('toggle');
    });
    content.querySelector('#btnModalCheckForUpdates')?.addEventListener('click', async () => {
      sfx.play('click');
      try {
        const res = await emuApi.updates.check();
        if (res) {
          openUpdateModal(res);
        }
      } catch {}
    });
    content.querySelector('#btnModalDiscord')?.addEventListener('click', () => {
      window.open('https://discord.gg/tvWcb28vBZ', '_blank');
    });
    content.querySelector('#btnModalKofi')?.addEventListener('click', () => {
      window.open('https://ko-fi.com/emufii', '_blank');
    });
  } else if (tab === 'crashlogs') {
    content.querySelector('#btnModalReportIssue')?.addEventListener('click', () => {
      window.open('https://github.com/emufii/emufii/issues', '_blank');
    });
    content.querySelector('#btnModalClearLogs')?.addEventListener('click', async () => {
      sfx.play('confirm');
      await emuApi?.crashLogs?.clear?.();
      alert('Crash logs cleared.');
    });
  }
}
