import { store } from '../state.js';
import { sfx } from '../sfx.js';
import {
  formatFriendCode,
  attachFriendCodeInputFormatter,
} from '../../../main/profile/friendCode.js';
import type { OpenSession } from '../../../main/network/coordinator.js';
import {
  resolveFriendDisplayName,
  resolveFriendAvatar,
  resolveFriendActivity,
  confirmRemoveFriendModal,
  promptRenameFriendModal,
  addFriendWithSync,
  syncFriendsRealtime,
  acceptFriendRequest,
  declineFriendRequest,
  acceptSessionInvite,
  dismissSessionInvite,
  encodeAvatarToWebpBase64,
} from './FriendActionModal.js';

export type FloatingTab = 'sessions' | 'join' | 'friends' | 'profile' | 'server';

let currentTab: FloatingTab = 'sessions';
let isOpen = false;
let enteredJoinCode = '';
let keydownListener: ((e: KeyboardEvent) => void) | null = null;
let friendStatuses: any = { present: {}, names: {}, lastGames: {} };
let publicSessions: OpenSession[] = [];
let loadingSessions = false;
let sessionsLoaded = false;
let currentPresence: 'online' | 'idle' | 'offline' = (localStorage.getItem('emufii_presence') as any) || 'online';
let presenceMenuOpen = false;
let relayLatencies: Record<string, number | null> = {};
let measuringRelays = false;

export function isFloatingPanelOpen(): boolean {
  return isOpen;
}

export function getFloatingPanelTab(): FloatingTab | null {
  return isOpen ? currentTab : null;
}

export function positionFloatingPanel(tab: FloatingTab) {
  const panel = document.getElementById('floatingCloudPanel');
  if (!panel) return;

  const screenWidth = window.innerWidth;
  const isServer = tab === 'server';
  const isProfile = tab === 'profile';
  const isJoin = tab === 'join';
  const targetWidth = isServer ? 620 : (isProfile ? 440 : 520);
  const panelWidth = Math.min(targetWidth, screenWidth - 32);
  panel.style.width = `${panelWidth}px`;

  // Centered in window for Join Session panel
  if (isJoin) {
    const left = Math.round((screenWidth - panelWidth) / 2);
    const top = Math.max(76, Math.round((window.innerHeight - 520) / 2));
    panel.style.top = `${top}px`;
    panel.style.left = `${left}px`;
    panel.style.right = 'auto';
    panel.style.transformOrigin = '50% 10%';
    return;
  }

  let triggerEl: HTMLElement | null = null;
  if (tab === 'profile') {
    triggerEl = document.getElementById('headerProfileBtn');
  } else if (tab === 'server') {
    triggerEl = document.getElementById('vpsStatusChip');
  } else if (tab === 'friends') {
    triggerEl = document.getElementById('headerFriendsBtn');
  } else if (tab === 'sessions') {
    triggerEl = document.getElementById('headerFinderBtn');
  }

  if (triggerEl) {
    const rect = triggerEl.getBoundingClientRect();
    const triggerCenter = rect.left + rect.width / 2;

    let left = triggerCenter - panelWidth / 2;
    const padding = 16;
    if (left < padding) left = padding;
    if (left + panelWidth > screenWidth - padding) {
      left = screenWidth - panelWidth - padding;
    }

    panel.style.top = `${Math.round(rect.bottom + 8)}px`;
    panel.style.left = `${Math.round(left)}px`;
    panel.style.right = 'auto';

    const originPercent = Math.max(5, Math.min(95, Math.round(((triggerCenter - left) / panelWidth) * 100)));
    panel.style.transformOrigin = `${originPercent}% 0%`;
  } else {
    panel.style.top = '56px';
    panel.style.left = 'auto';
    panel.style.right = '24px';
    panel.style.transformOrigin = 'top right';
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener('resize', () => {
    if (isOpen) {
      positionFloatingPanel(currentTab);
    }
  });
  window.addEventListener('emufii:friends-panel-refresh', () => {
    if (isOpen && currentTab === 'friends') {
      renderFloatingPanel();
    }
  });
}

export function toggleFloatingPanel(tab: FloatingTab) {
  if (isOpen && currentTab === tab) {
    closeFloatingPanel();
  } else {
    openFloatingPanel(tab);
  }
}

export function openFloatingPanel(tab: FloatingTab = 'sessions') {
  currentTab = tab;
  isOpen = true;
  presenceMenuOpen = false;
  sfx.play('toggle');

  const panel = document.getElementById('floatingCloudPanel');
  const backdrop = document.getElementById('floatingPanelBackdrop');
  if (!panel) return;

  // Highlight active header button
  updateHeaderActiveState(tab);

  // Render content
  renderFloatingPanel();

  // Position dynamically below respective trigger button
  positionFloatingPanel(tab);

  // If opening sessions and not yet loaded, load now
  if (tab === 'sessions' && !sessionsLoaded && !loadingSessions) {
    refreshSessions();
  }
  if (tab === 'friends') {
    const existingToast = document.getElementById('emufiiFriendToast');
    if (existingToast) existingToast.remove();
    syncFriendsRealtime(() => {
      if (isOpen && currentTab === 'friends') {
        renderFloatingPanel();
      }
    });
  }
  if (tab === 'server') {
    refreshRelayLatencies();
  }

  // Show panel with smooth arrive animation
  panel.classList.remove('closing');
  panel.classList.add('open');
  panel.setAttribute('aria-hidden', 'false');

  if (backdrop) {
    backdrop.classList.add('open');
  }

  // Attach global keyboard listener for typing in Join and Escape
  attachKeyboard();
}

export function closeFloatingPanel() {
  if (!isOpen) return;
  isOpen = false;
  sfx.play('toggle');

  const panel = document.getElementById('floatingCloudPanel');
  const backdrop = document.getElementById('floatingPanelBackdrop');

  updateHeaderActiveState(null);
  detachKeyboard();

  if (panel) {
    panel.classList.remove('open');
    panel.classList.add('closing');
    panel.setAttribute('aria-hidden', 'true');
    setTimeout(() => {
      panel.classList.remove('closing');
    }, 220);
  }

  if (backdrop) {
    backdrop.classList.remove('open');
  }
}

function updateHeaderActiveState(tab: FloatingTab | null) {
  document.getElementById('headerFinderBtn')?.classList.toggle('active', tab === 'sessions');
  document.getElementById('headerJoinBtn')?.classList.toggle('active', tab === 'join');
  document.getElementById('headerFriendsBtn')?.classList.toggle('active', tab === 'friends');
  document.getElementById('headerProfileBtn')?.classList.toggle('active', tab === 'profile');
  document.getElementById('vpsStatusChip')?.classList.toggle('active', tab === 'server');
}

function attachKeyboard() {
  detachKeyboard();
  keydownListener = (e: KeyboardEvent) => {
    if (!isOpen) return;

    if (e.key === 'Escape') {
      closeFloatingPanel();
      return;
    }

    // Only process keypad input if currently on 'join' tab and not typing into an input/textarea
    if (currentTab === 'join') {
      const activeEl = document.activeElement;
      if (activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA')) {
        return;
      }

      if (e.key === 'Backspace') {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        removeJoinChar();
        return;
      }
      if (e.key === 'Enter' && enteredJoinCode.length === 6) {
        e.preventDefault();
        e.stopPropagation();
        submitJoinSession();
        return;
      }
      if (e.key.length === 1 && /[a-zA-Z0-9]/.test(e.key)) {
        e.preventDefault();
        e.stopPropagation();
        addJoinChar(e.key.toUpperCase());
      }
    }
  };
  window.addEventListener('keydown', keydownListener, true);
}

function detachKeyboard() {
  if (keydownListener) {
    window.removeEventListener('keydown', keydownListener, true);
    keydownListener = null;
  }
}

function getTabHeaderInfo(tab: FloatingTab, profile: any) {
  switch (tab) {
    case 'server':
      return {
        title: 'Emufii server',
        subtitle: 'Select relay region for coordinator',
        icon: `<span style="font-size: 19px; line-height: 1; display: inline-flex; align-items: center; justify-content: center;">🌐</span>`,
        action: '',
      };
    case 'sessions':
      return {
        title: 'Open Sessions',
        subtitle: 'Active netplay rooms on coordinator',
        icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" class="sessions-icon" style="color: var(--accent-bright); stroke: var(--accent-bright);"><rect x="2" y="3" width="9.5" height="7.5" rx="2" stroke="currentColor" stroke-width="2" fill="none" /><rect x="12.5" y="13.5" width="9.5" height="7.5" rx="2" stroke="currentColor" stroke-width="2" fill="none" /><line x1="6.5" y1="10.5" x2="17.5" y2="13.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-dasharray="0.1 3.8" /></svg>`,
        action: `
          <button class="btn-secondary plate-button" id="btnRefreshFloatingSessions" style="height: 30px; padding: 0 12px; font-size: 11px; display: inline-flex; align-items: center; gap: 6px;">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
            <span>Refresh</span>
          </button>
        `,
      };
    case 'join':
      return {
        title: 'Join Session',
        subtitle: "Enter friend's 6-character room code",
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>`,
        action: '',
      };
    case 'friends':
      return {
        title: 'Friends',
        subtitle: 'Online status and friend codes',
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`,
        action: `
          <button class="btn-secondary plate-button" id="btnFloatingRefreshFriends" style="height: 30px; padding: 0 12px; font-size: 11px; display: inline-flex; align-items: center; gap: 6px;">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
            <span>Refresh</span>
          </button>
        `,
      };
    case 'profile':
      return {
        title: profile.name || 'Player Profile',
        subtitle: 'Visible to other players in lobbies',
        icon: profile.avatarDataUrl
          ? `<img src="${profile.avatarDataUrl}" style="width: 26px; height: 26px; border-radius: 50%; object-fit: cover;">`
          : `<div class="chip-avatar" style="width: 26px; height: 26px; font-size: 11px; margin: 0;">${(profile.name || 'J').slice(0, 2).toUpperCase()}</div>`,
        action: '',
      };
  }
}

export function renderFloatingPanel() {
  const panel = document.getElementById('floatingCloudPanel');
  if (!panel) return;

  const activeEl = document.activeElement as HTMLInputElement | null;
  const activeId = activeEl && panel.contains(activeEl) ? activeEl.id : null;
  const existingCodeVal = (panel.querySelector('#inputFloatingFriendCode') as HTMLInputElement | null)?.value || '';
  const selStart = activeEl?.selectionStart ?? null;
  const selEnd = activeEl?.selectionEnd ?? null;

  const profile = store.get().profile;
  const headerInfo = getTabHeaderInfo(currentTab, profile);

  panel.innerHTML = `
    <div class="floating-panel-container">
      <!-- Floating Panel Header -->
      <div class="floating-panel-header">
        <div class="floating-panel-heading-wrap">
          <div class="floating-panel-icon-bubble">
            ${headerInfo.icon}
          </div>
          <div>
            <h3 class="floating-pane-title">${headerInfo.title}</h3>
            <p class="floating-pane-subtitle">${headerInfo.subtitle}</p>
          </div>
        </div>
        <div class="floating-panel-actions">
          ${headerInfo.action}
        </div>
      </div>

      <!-- Floating Body View -->
      <div class="floating-panel-body" id="floatingPanelBody">
        ${
          currentTab === 'server'
            ? renderServerTab()
            : currentTab === 'sessions'
            ? renderSessionsTab()
            : currentTab === 'join'
            ? renderJoinTab()
            : currentTab === 'friends'
            ? renderFriendsTab()
            : renderProfileTab()
        }
      </div>
    </div>
  `;

  // Attach tab-specific listeners
  if (currentTab === 'server') {
    attachServerListeners(panel);
  } else if (currentTab === 'sessions') {
    attachSessionsListeners(panel);
  } else if (currentTab === 'join') {
    attachJoinListeners(panel);
  } else if (currentTab === 'friends') {
    attachFriendsListeners(panel);
  } else if (currentTab === 'profile') {
    attachProfileListeners(panel);
  }

  if (existingCodeVal) {
    const codeInput = panel.querySelector('#inputFloatingFriendCode') as HTMLInputElement | null;
    if (codeInput) codeInput.value = existingCodeVal;
  }
  if (activeId) {
    const toFocus = panel.querySelector(`#${activeId}`) as HTMLInputElement | null;
    if (toFocus) {
      toFocus.focus();
      if (selStart !== null && selEnd !== null) {
        try {
          toFocus.setSelectionRange(selStart, selEnd);
        } catch {}
      }
    }
  }
}

// -------------------------------------------------------------
// SERVER PICKER TAB (Matching Android ServerPicker & VpsLamp)
// -------------------------------------------------------------
function renderSignalBars(rtt: number | null): string {
  const lit = rtt === null ? 0 : rtt < 60 ? 3 : rtt < 130 ? 2 : 1;
  const onColor = rtt === null || rtt < 60 ? '#10B981' : rtt < 130 ? '#F2B233' : '#EF4444';
  const offColor = 'rgba(255, 255, 255, 0.15)';

  const heights = [8, 13, 18];
  const barsHtml = heights
    .map(
      (h, i) =>
        `<span class="signal-bar" style="height: ${h}px; background-color: ${i < lit ? onColor : offColor};"></span>`
    )
    .join('');

  return `<div class="server-signal-bars" title="${rtt !== null ? `${Math.round(rtt)} ms` : 'Latency'}">${barsHtml}</div>`;
}

const REGION_META: Record<string, { title: string; badge: string }> = {
  eu: { title: 'Europe', badge: '🇪🇺' },
  na: { title: 'Canada (Montréal)', badge: '🇨🇦' },
  usw: { title: 'US West (Seattle)', badge: '🇺🇸' },
  jp: { title: 'Japan (Tokyo)', badge: '🇯🇵' },
  use: { title: 'US East (NY)', badge: '🇺🇸' },
};

function getBestAutoRegion(): { code: string; title: string; rtt: number | null } {
  let bestCode = 'use';
  let bestRtt: number | null = null;
  for (const [code, rtt] of Object.entries(relayLatencies)) {
    if (typeof rtt === 'number' && rtt >= 0 && (bestRtt === null || rtt < bestRtt)) {
      bestCode = code;
      bestRtt = rtt;
    }
  }
  return {
    code: bestCode,
    title: REGION_META[bestCode]?.title || 'US East (NY)',
    rtt: bestRtt,
  };
}

export function updateHeaderVpsLabel() {
  const currentRegion = localStorage.getItem('emufii_server_region') || 'auto';
  const vpsText = document.getElementById('vpsText');
  if (!vpsText) return;

  if (currentRegion === 'auto') {
    const best = getBestAutoRegion();
    vpsText.innerHTML =
      best.rtt !== null
        ? `Online &bull; ${best.title} (${best.rtt} ms)`
        : `Online &bull; ${best.title}`;
  } else {
    const title = REGION_META[currentRegion]?.title || 'US East (NY)';
    const rtt = relayLatencies[currentRegion] ?? null;
    vpsText.innerHTML = rtt !== null ? `Online &bull; ${title} (${rtt} ms)` : `Online &bull; ${title}`;
  }
}

export async function refreshRelayLatencies() {
  if (measuringRelays) return;
  const emuApi = (window as any).emufii;
  if (!emuApi?.coordinator?.measureRelays) return;

  measuringRelays = true;
  if (isOpen && currentTab === 'server') {
    renderFloatingPanel();
  }

  try {
    const measured: Record<string, { host: string; rttMs: number | null }> =
      await emuApi.coordinator.measureRelays();
    if (measured && typeof measured === 'object') {
      const next: Record<string, number | null> = {};
      for (const [code, info] of Object.entries(measured)) {
        next[code] = typeof info?.rttMs === 'number' ? info.rttMs : null;
      }
      relayLatencies = next;
    }
  } catch {
    // Keep previous measurements if offline
  } finally {
    measuringRelays = false;
    updateHeaderVpsLabel();
    if (isOpen && currentTab === 'server') {
      renderFloatingPanel();
    }
  }
}

function renderServerTab(): string {
  const currentRegion = localStorage.getItem('emufii_server_region') || 'auto';
  const bestAuto = getBestAutoRegion();
  const autoDetail =
    bestAuto.rtt !== null
      ? `Now: ${bestAuto.title} (${bestAuto.rtt} ms)`
      : measuringRelays
      ? 'Measuring closest relay...'
      : `Now: ${bestAuto.title}`;

  const regionOrder = ['eu', 'na', 'usw', 'jp', 'use'];
  const servers = [
    { id: 'auto', title: 'Automatic', badge: '🌐', rtt: bestAuto.rtt, detail: autoDetail, isAuto: true },
    ...regionOrder.map((code) => {
      const meta = REGION_META[code];
      const rtt = relayLatencies[code] ?? null;
      const detail =
        rtt !== null ? `${Math.round(rtt)} ms` : measuringRelays ? 'Pinging...' : 'Ready';
      return {
        id: code,
        title: meta.title,
        badge: meta.badge,
        rtt,
        detail,
        isAuto: false,
      };
    }),
  ];

  const renderCard = (opt: any) => {
    const isChosen = opt.id === currentRegion;
    return `
      <div class="server-picker-row ${isChosen ? 'chosen' : ''}" data-region="${opt.id}" tabindex="0">
        <div class="server-picker-badge ${isChosen ? 'chosen' : ''}">
          <span>${opt.badge}</span>
        </div>
        <div class="server-picker-meta">
          <div class="server-picker-name" title="${opt.title}">${opt.title}</div>
          <div class="server-picker-detail" title="${opt.detail}">${opt.detail}</div>
        </div>
        <div class="server-signal-bars-slot">
          ${!opt.isAuto ? renderSignalBars(opt.rtt) : ''}
        </div>
        <div class="server-picker-check-slot">
          ${isChosen ? `<div class="server-picker-check-bead">✓</div>` : ''}
        </div>
      </div>
    `;
  };

  return `
    <div class="floating-tab-pane server-picker-pane">
      <div class="server-picker-grid">
        ${servers.map(renderCard).join('')}
      </div>
    </div>
  `;
}

function attachServerListeners(panel: HTMLElement) {
  panel.querySelectorAll<HTMLElement>('.server-picker-row').forEach((row) => {
    row.addEventListener('click', () => {
      const regionId = row.dataset.region || 'auto';
      sfx.play('click');
      localStorage.setItem('emufii_server_region', regionId);
      updateHeaderVpsLabel();
      closeFloatingPanel();
    });
  });
}

// -------------------------------------------------------------
// SESSIONS TAB
// -------------------------------------------------------------
function renderSessionsTab(): string {
  return `
    <div class="floating-tab-pane">
      <div id="floatingSessionsList" style="display: flex; flex-direction: column; gap: 8px; max-height: 360px; overflow-y: auto;">
        ${
          loadingSessions
            ? `
          <div style="display: flex; justify-content: center; padding: 40px;">
            <div class="spinner"></div>
          </div>
        `
            : publicSessions.length === 0
            ? `
          <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 36px 16px; gap: 8px; text-align: center; color: var(--ink-secondary);">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" class="sessions-icon" style="opacity: 0.45; color: var(--accent-bright); stroke: var(--accent-bright);"><rect x="2" y="3" width="9.5" height="7.5" rx="2" stroke="currentColor" stroke-width="2" fill="none" /><rect x="12.5" y="13.5" width="9.5" height="7.5" rx="2" stroke="currentColor" stroke-width="2" fill="none" /><line x1="6.5" y1="10.5" x2="17.5" y2="13.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-dasharray="0.1 3.8" /></svg>
            <div style="font-weight: 700; font-size: 14px;">No Open Sessions Active</div>
            <div style="font-size: 12px;">Create a session from your library or join with a 6-character room code.</div>
          </div>
        `
            : publicSessions
                .map(
                  (s) => `
            <div class="floating-session-row plate plate-button" data-code="${s.code}">
              <div style="display: flex; align-items: center; gap: 12px; min-width: 0; flex: 1 1 auto; overflow: hidden; margin-right: 12px;">
                <span class="floating-session-code" style="flex-shrink: 0;">${s.code}</span>
                <div style="min-width: 0; flex: 1 1 auto; overflow: hidden;">
                  <div style="font-weight: 800; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${s.romTitle || 'Custom Session'}</div>
                  <div style="font-size: 11px; color: var(--ink-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                    Hosted by ${s.hostName || 'Player'} · ${s.players} player(s) · ${Math.floor(s.ageSeconds / 60)}m ago
                  </div>
                </div>
              </div>
              <button class="btn-primary plate-button btn-join-floating-session" data-code="${s.code}">
                Join
              </button>
            </div>
          `
                )
                .join('')
        }
      </div>
    </div>
  `;
}

function attachSessionsListeners(panel: HTMLElement) {
  panel.querySelector('#btnRefreshFloatingSessions')?.addEventListener('click', () => {
    sfx.play('click');
    refreshSessions();
  });

  panel.querySelectorAll('.btn-join-floating-session').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const code = (e.currentTarget as HTMLElement).dataset.code;
      if (code) {
        connectToSession(code);
      }
    });
  });

  panel.querySelectorAll('.floating-session-row').forEach((row) => {
    row.addEventListener('click', (e) => {
      const code = (e.currentTarget as HTMLElement).dataset.code;
      if (code) {
        connectToSession(code);
      }
    });
  });
}

async function refreshSessions() {
  if (loadingSessions) return;
  const emuApi = (window as any).emufii;
  loadingSessions = true;
  renderFloatingPanel();

  try {
    const timeout = new Promise<OpenSession[]>((_, reject) =>
      setTimeout(() => reject(new Error('Timeout')), 6000)
    );
    publicSessions = await Promise.race([
      emuApi.coordinator.listSessions(),
      timeout,
    ]);
  } catch {
    publicSessions = [];
  } finally {
    loadingSessions = false;
    sessionsLoaded = true;
    renderFloatingPanel();
  }
}

// -------------------------------------------------------------
// JOIN TAB
// -------------------------------------------------------------
function renderJoinTab(): string {
  return `
    <div class="floating-tab-pane" style="display: flex; flex-direction: column; align-items: center; padding-top: 4px;">
      <!-- 6-character Code Boxes -->
      <div style="display: flex; gap: 8px; margin: 4px 0 12px 0;" id="floatingJoinBoxes">
        ${Array.from({ length: 6 })
          .map(
            (_, i) => `
          <div class="join-code-box plate ${enteredJoinCode[i] ? 'filled' : ''}" id="fJoinBox-${i}">
            ${enteredJoinCode[i] || ''}
          </div>
        `
          )
          .join('')}
      </div>

      <!-- Quick Paste Button -->
      <button class="btn-secondary plate-button" id="btnFloatingPaste" style="height: 28px; padding: 0 12px; font-size: 11px; display: flex; align-items: center; gap: 6px; margin-bottom: 12px;">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1" ry="1"/></svg>
        Paste from Clipboard
      </button>

      <!-- QWERTY Keypad -->
      <div class="code-keyboard-container">
        <div class="code-keyboard-row">
          ${['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
        </div>
        <div class="code-keyboard-row">
          ${['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
        </div>
        <div class="code-keyboard-row">
          ${['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
        </div>
        <div class="code-keyboard-row">
          <div style="flex: 0.5; max-width: 20px;"></div>
          ${['Z', 'X', 'C', 'V', 'B', 'N', 'M'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
          <button class="code-key wide plate-button" id="btnFloatingKeypadBack" title="Backspace" style="font-size: 16px; font-weight: bold;">&larr;</button>
        </div>
      </div>

      <button class="btn-primary plate-button" id="btnSubmitFloatingJoin" style="width: 100%; max-width: 440px; height: 40px; margin-top: 14px; opacity: ${enteredJoinCode.length === 6 ? '1' : '0.5'};" ${enteredJoinCode.length === 6 ? '' : 'disabled'}>
        Join Session
      </button>
    </div>
  `;
}

function flashKey(selector: string) {
  const btn = document.querySelector<HTMLElement>(selector);
  if (btn) {
    btn.classList.add('key-pressed');
    setTimeout(() => {
      btn.classList.remove('key-pressed');
    }, 140);
  }
}

function attachJoinListeners(panel: HTMLElement) {
  panel.querySelectorAll<HTMLElement>('.code-key[data-char]').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const c = (e.currentTarget as HTMLElement).dataset.char;
      (e.currentTarget as HTMLElement).blur();
      if (c) addJoinChar(c);
    });
  });

  panel.querySelector('#btnFloatingKeypadBack')?.addEventListener('click', (e) => {
    (e.currentTarget as HTMLElement).blur();
    removeJoinChar();
  });

  panel.querySelector('#btnFloatingPaste')?.addEventListener('click', async () => {
    try {
      const text = await navigator.clipboard.readText();
      const cleaned = text.trim().replace(/[^a-zA-Z0-9]/g, '').slice(0, 6).toUpperCase();
      if (cleaned) {
        enteredJoinCode = cleaned;
        sfx.play('pop');
        updateJoinBoxesUI();
      }
    } catch {}
  });

  panel.querySelector('#btnSubmitFloatingJoin')?.addEventListener('click', () => {
    submitJoinSession();
  });
}

function addJoinChar(c: string) {
  const upper = c.toUpperCase();
  flashKey(`.code-key[data-char="${upper}"]`);
  if (enteredJoinCode.length < 6) {
    enteredJoinCode += upper;
    sfx.play('tick');
    updateJoinBoxesUI();
    if (enteredJoinCode.length === 6) {
      sfx.play('pop');
    }
  }
}

function removeJoinChar() {
  flashKey('#btnFloatingKeypadBack');
  if (enteredJoinCode.length > 0) {
    enteredJoinCode = enteredJoinCode.slice(0, -1);
    sfx.play('tick');
    updateJoinBoxesUI();
  }
}

function updateJoinBoxesUI() {
  for (let i = 0; i < 6; i++) {
    const box = document.getElementById(`fJoinBox-${i}`);
    if (box) {
      box.textContent = enteredJoinCode[i] || '';
      box.classList.toggle('filled', !!enteredJoinCode[i]);
    }
  }
  const submitBtn = document.getElementById('btnSubmitFloatingJoin') as HTMLButtonElement;
  if (submitBtn) {
    submitBtn.disabled = enteredJoinCode.length !== 6;
    submitBtn.style.opacity = enteredJoinCode.length === 6 ? '1' : '0.5';
  }
}

async function submitJoinSession() {
  if (enteredJoinCode.length !== 6) return;
  connectToSession(enteredJoinCode);
}

async function connectToSession(code: string) {
  sfx.play('click');
  const emuApi = (window as any).emufii;
  const profile = store.get().profile;

  try {
    // 1. Fetch remote session
    const remote = await emuApi.coordinator.getSession(code);

    // 2. Claim WireGuard address
    const config = await emuApi.config.get();
    const wgInfo = await emuApi.coordinator.claimAddress(
      code,
      config.wgKeys.publicKey,
      profile.name,
      profile.id
    );

    // 3. Up tunnel
    const wgRes = await emuApi.wireguard.up(wgInfo);

    // 4. Update store and navigate
    store.set({
      activeSession: {
        code,
        isHost: false,
        token: null,
        remote,
        wireguard: wgInfo,
        wireguardConfigPath: wgRes?.configPath || null,
        wireguardNative: wgRes?.isNativeActive !== false,
        step1Done: true,
        step2Done: false,
        netplayBusy: false,
      },
    });

    sfx.play('confirm');
    closeFloatingPanel();
    store.navigate('session');
  } catch (err: any) {
    alert('Could not join session: ' + (err.message || 'Session not found'));
  }
}

// -------------------------------------------------------------
// FRIENDS TAB
// -------------------------------------------------------------
function renderFriendsTab(): string {
  const state = store.get();
  const friends = state.settings.friends || [];
  const pendingRequests = state.settings.pendingFriendRequests || [];
  const pendingInvites = state.settings.pendingSessionInvites || [];
  const inPanelNotice = state.inPanelFriendNotice || null;

  return `
    <div class="floating-tab-pane">
      ${
        inPanelNotice
          ? `
        <!-- In-Panel Live Notification Banner (shown inside Friends panel when open) -->
        <div class="plate" style="display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 12px; margin-bottom: 12px; border-radius: var(--radius-sm); border: 1px solid var(--accent-bright); background: var(--accent-subtle);">
          <div style="display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1;">
            <div style="width: 32px; height: 32px; border-radius: 50%; background: var(--bg-shelf); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 12px; color: var(--accent-bright); border: 1px solid var(--plate-border); overflow: hidden; flex-shrink: 0;">
              ${
                inPanelNotice.avatarUrl
                  ? `<img src="${inPanelNotice.avatarUrl}" alt="${inPanelNotice.name}" style="width: 100%; height: 100%; object-fit: cover;">`
                  : (inPanelNotice.name || 'F').slice(0, 2).toUpperCase()
              }
            </div>
            <div style="min-width: 0; flex: 1;">
              <div style="font-weight: 800; font-size: 12px; color: var(--ink-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${inPanelNotice.name}</div>
              <div style="font-size: 11px; color: var(--ink-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${inPanelNotice.subtitle}</div>
            </div>
          </div>
          <button id="btnDismissInPanelNotice" style="background: transparent; border: none; color: var(--ink-secondary); cursor: pointer; font-size: 15px; line-height: 1; padding: 4px;" title="Dismiss">&times;</button>
        </div>
      `
          : ''
      }

      <!-- Add Friend Box -->
      <div style="background: var(--plate-bg-low); border: 1px solid var(--plate-border); border-radius: var(--radius-sm); padding: 12px 14px; margin-bottom: 14px;">
        <div style="font-size: 11px; font-weight: 800; text-transform: uppercase; color: var(--ink-secondary); margin-bottom: 8px;">Add a Friend</div>
        <div style="display: flex; gap: 8px;">
          <input type="text" id="inputFloatingFriendCode" class="cloud-input" placeholder="XXXX-XXXX-XXXX" maxlength="14" autocomplete="off" spellcheck="false" style="font-family: var(--font-family); font-weight: 700; text-transform: uppercase; letter-spacing: 0.6px; height: 36px; font-size: 13px; flex: 1;">
          <button class="btn-primary plate-button" id="btnFloatingAddFriend" style="width: auto; height: 36px; padding: 0 18px; font-size: 12px;">
            Add
          </button>
        </div>
        <div id="floatingFriendError" style="font-size: 11px; color: var(--compat-broken); margin-top: 6px; display: none; font-weight: 600;">
          Invalid Crockford friend code or checksum.
        </div>
      </div>

      ${
        pendingInvites.length > 0
          ? `
        <!-- Incoming Session Invites -->
        <div style="margin-bottom: 14px;">
          <div style="margin-bottom: 6px;">
            <label class="cloud-section-label" style="color: var(--accent-bright);">Session Invites (${pendingInvites.length})</label>
          </div>
          <div style="display: flex; flex-direction: column; gap: 6px;">
            ${pendingInvites
              .map((inv) => {
                const invAvatar = resolveFriendAvatar(inv.from);
                const invName = inv.name || resolveFriendDisplayName(inv.from);
                return `
                  <div class="plate" style="display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; border-radius: var(--radius-sm); border-color: var(--accent-bright);">
                    <div style="display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1;">
                      <div style="width: 32px; height: 32px; border-radius: 50%; background: var(--bg-shelf); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 12px; color: var(--accent-bright); border: 1px solid var(--plate-border); overflow: hidden; flex-shrink: 0;">
                        ${
                          invAvatar
                            ? `<img src="${invAvatar}" alt="${invName}" style="width: 100%; height: 100%; object-fit: cover;">`
                            : invName.slice(0, 2).toUpperCase()
                        }
                      </div>
                      <div style="min-width: 0; flex: 1;">
                        <div style="font-weight: 700; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${invName}</div>
                        <div style="font-size: 11px; color: var(--accent-bright); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                          Invited you to play ${inv.romTitle || inv.code} (${inv.code})
                        </div>
                      </div>
                    </div>
                    <div style="display: flex; gap: 4px; flex-shrink: 0; margin-left: 8px;">
                      <button class="btn-primary plate-button btn-f-accept-invite" data-from="${inv.from}" data-code="${inv.code}" style="height: 26px; padding: 0 10px; font-size: 10px; width: auto;">
                        Join
                      </button>
                      <button class="btn-secondary plate-button btn-f-dismiss-invite" data-from="${inv.from}" data-code="${inv.code}" style="height: 26px; padding: 0 8px; font-size: 10px;">
                        Dismiss
                      </button>
                    </div>
                  </div>
                `;
              })
              .join('')}
          </div>
        </div>
      `
          : ''
      }

      ${
        pendingRequests.length > 0
          ? `
        <!-- Pending Friend Requests -->
        <div style="margin-bottom: 14px;">
          <div style="margin-bottom: 6px;">
            <label class="cloud-section-label" style="color: var(--accent-bright);">Friend Requests (${pendingRequests.length})</label>
          </div>
          <div style="display: flex; flex-direction: column; gap: 6px;">
            ${pendingRequests
              .map((req) => {
                const reqAvatar = resolveFriendAvatar(req.code);
                const reqName = req.name || resolveFriendDisplayName(req.code);
                return `
                  <div class="plate" style="display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; border-radius: var(--radius-sm); border-color: var(--accent-bright);">
                    <div style="display: flex; align-items: center; gap: 10px; min-width: 0;">
                      <div style="width: 32px; height: 32px; border-radius: 50%; background: var(--bg-shelf); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 12px; color: var(--accent-bright); border: 1px solid var(--plate-border); overflow: hidden; flex-shrink: 0;">
                        ${
                          reqAvatar
                            ? `<img src="${reqAvatar}" alt="${reqName}" style="width: 100%; height: 100%; object-fit: cover;">`
                            : reqName.slice(0, 2).toUpperCase()
                        }
                      </div>
                      <div style="min-width: 0;">
                        <div style="font-weight: 700; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${reqName}</div>
                        <div style="font-size: 10px; font-family: var(--font-family); color: var(--ink-secondary);">${formatFriendCode(req.code)} · Sent you a friend request</div>
                      </div>
                    </div>
                    <div style="display: flex; gap: 4px; flex-shrink: 0;">
                      <button class="btn-primary plate-button btn-f-accept-req" data-code="${req.code}" data-name="${reqName}" style="height: 26px; padding: 0 10px; font-size: 10px; width: auto;">
                        Accept
                      </button>
                      <button class="btn-secondary plate-button btn-f-decline-req" data-code="${req.code}" style="height: 26px; padding: 0 8px; font-size: 10px;">
                        Decline
                      </button>
                    </div>
                  </div>
                `;
              })
              .join('')}
          </div>
        </div>
      `
          : ''
      }

      <!-- Friends List -->
      <div>
        <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
          <label class="cloud-section-label">Friends (${friends.length})</label>
          <span style="font-size: 10px; color: var(--ink-tertiary); font-weight: 600;">Live updates</span>
        </div>

        <div style="display: flex; flex-direction: column; gap: 6px; max-height: 250px; overflow-y: auto;">
          ${
            friends.length === 0
              ? `
            <div style="padding: 28px 12px; text-align: center; color: var(--ink-secondary); font-size: 12px;">
              No friends added yet. Enter a friend code above!
            </div>
          `
              : friends
                  .map((code) => {
                    const name = resolveFriendDisplayName(code);
                    const avatar = resolveFriendAvatar(code);
                    const activity = resolveFriendActivity(code);

                    return `
                <div class="plate" style="display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; border-radius: var(--radius-sm);">
                  <div style="display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1;">
                    <div style="position: relative; flex-shrink: 0;">
                      <div style="width: 34px; height: 34px; border-radius: 50%; background: var(--bg-shelf); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 12px; color: var(--accent-bright); border: 1.5px solid var(--plate-border); overflow: hidden;">
                        ${
                          avatar
                            ? `<img src="${avatar}" alt="${name}" style="width: 100%; height: 100%; object-fit: cover;">`
                            : name.slice(0, 2).toUpperCase()
                        }
                      </div>
                      <div style="position: absolute; bottom: -1px; right: -1px; width: 9px; height: 9px; border-radius: 50%; background: ${activity.isOnline ? 'var(--compat-perfect)' : 'var(--ink-tertiary)'}; border: 1.5px solid var(--plate-bg);"></div>
                    </div>
                    <div style="min-width: 0; flex: 1;">
                      <div style="display: flex; align-items: center; gap: 6px;">
                        <span style="font-weight: 700; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${name}</span>
                        <span style="font-size: 10px; font-family: var(--font-family); color: var(--ink-secondary); flex-shrink: 0;">${formatFriendCode(code)}</span>
                      </div>
                      <div style="font-size: 11px; font-weight: ${activity.isPlaying ? '700' : '500'}; color: ${activity.isPlaying ? 'var(--accent-bright)' : activity.isOnline ? 'var(--compat-perfect)' : 'var(--ink-secondary)'}; margin-top: 1px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                        ${activity.statusText}
                      </div>
                    </div>
                  </div>

                  <div style="display: flex; gap: 4px; flex-shrink: 0; margin-left: 8px;">
                    ${
                      activity.sessionCode
                        ? `
                      <button class="btn-primary plate-button btn-f-join" data-code="${activity.sessionCode}" style="height: 24px; padding: 0 8px; font-size: 10px; width: auto;">
                        Join
                      </button>
                    `
                        : ''
                    }
                    <button class="btn-secondary plate-button btn-f-rename" data-code="${code}" style="height: 24px; padding: 0 8px; font-size: 10px;">
                      Rename
                    </button>
                    <button class="btn-secondary plate-button btn-f-remove" data-code="${code}" style="height: 24px; padding: 0 8px; font-size: 10px; color: var(--compat-broken);">
                      Remove
                    </button>
                  </div>
                </div>
              `;
                  })
                  .join('')
          }
        </div>
      </div>
    </div>
  `;
}

function attachFriendsListeners(panel: HTMLElement) {
  const codeInput = panel.querySelector('#inputFloatingFriendCode') as HTMLInputElement | null;
  const errorEl = panel.querySelector('#floatingFriendError') as HTMLElement | null;

  if (codeInput) {
    attachFriendCodeInputFormatter(codeInput);
    codeInput.addEventListener('input', () => {
      if (errorEl && errorEl.style.display !== 'none') {
        errorEl.style.display = 'none';
      }
    });
  }

  panel.querySelector('#btnFloatingRefreshFriends')?.addEventListener('click', async () => {
    sfx.play('click');
    await syncFriendsRealtime();
    renderFloatingPanel();
  });

  const submitAddFriend = async () => {
    if (!codeInput) return;
    const res = await addFriendWithSync(codeInput.value);
    if (!res.ok) {
      if (errorEl) {
        errorEl.textContent = res.error || 'Invalid friend code.';
        errorEl.style.display = 'block';
      }
      sfx.play('tick');
      return;
    }

    if (errorEl) errorEl.style.display = 'none';
    codeInput.value = '';
    sfx.play('confirm');
    renderFloatingPanel();
  };

  panel.querySelector('#btnFloatingAddFriend')?.addEventListener('click', submitAddFriend);
  codeInput?.addEventListener('keydown', (e: KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      submitAddFriend();
    }
  });

  panel.querySelector('#btnDismissInPanelNotice')?.addEventListener('click', () => {
    sfx.play('click');
    store.set({ inPanelFriendNotice: null });
    renderFloatingPanel();
  });

  panel.querySelectorAll('.btn-f-accept-invite').forEach((btn) => {
    btn.addEventListener('click', async (e) => {
      sfx.play('confirm');
      const target = e.currentTarget as HTMLElement;
      const from = target.dataset.from;
      const code = target.dataset.code;
      if (!from || !code) return;
      closeFloatingPanel();
      await acceptSessionInvite({ from, code });
    });
  });

  panel.querySelectorAll('.btn-f-dismiss-invite').forEach((btn) => {
    btn.addEventListener('click', async (e) => {
      sfx.play('click');
      const target = e.currentTarget as HTMLElement;
      const from = target.dataset.from;
      const code = target.dataset.code;
      if (!from || !code) return;
      await dismissSessionInvite({ from, code });
      renderFloatingPanel();
    });
  });

  panel.querySelectorAll('.btn-f-accept-req').forEach((btn) => {
    btn.addEventListener('click', async (e) => {
      sfx.play('confirm');
      const target = e.currentTarget as HTMLElement;
      const code = target.dataset.code;
      const name = target.dataset.name;
      if (!code) return;
      await acceptFriendRequest(code, name);
      renderFloatingPanel();
    });
  });

  panel.querySelectorAll('.btn-f-decline-req').forEach((btn) => {
    btn.addEventListener('click', async (e) => {
      sfx.play('click');
      const code = (e.currentTarget as HTMLElement).dataset.code;
      if (!code) return;
      await declineFriendRequest(code);
      renderFloatingPanel();
    });
  });

  panel.querySelectorAll('.btn-f-join').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const sessionCode = (e.currentTarget as HTMLElement).dataset.code;
      if (sessionCode) {
        connectToSession(sessionCode);
      }
    });
  });

  panel.querySelectorAll('.btn-f-rename').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const code = (e.currentTarget as HTMLElement).dataset.code;
      if (!code) return;
      promptRenameFriendModal({
        code,
        onRenamed: () => renderFloatingPanel(),
      });
    });
  });

  panel.querySelectorAll('.btn-f-remove').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const code = (e.currentTarget as HTMLElement).dataset.code;
      if (!code) return;
      confirmRemoveFriendModal({
        code,
        onRemoved: () => renderFloatingPanel(),
      });
    });
  });
}

// -------------------------------------------------------------
// PROFILE TAB
// -------------------------------------------------------------
function renderProfileTab(): string {
  const profile = store.get().profile;

  return `
    <div class="floating-tab-pane">
      <!-- Unified Seamless Profile Card (No separated boxes, no divider lines) -->
      <div class="profile-unified-card">
        <!-- 1. Header: Avatar + Nickname + Presence -->
        <div class="profile-unified-top">
          <div class="profile-unified-user">
            <!-- Profile Avatar -->
            <div class="profile-avatar-large" style="width: 44px; height: 44px; font-size: 18px; flex-shrink: 0;">
              ${
                profile.avatarDataUrl
                  ? `<img src="${profile.avatarDataUrl}" alt="${profile.name}" style="width: 100%; height: 100%; object-fit: cover; border-radius: 50%;">`
                  : `<span>${(profile.name || 'J').slice(0, 2).toUpperCase()}</span>`
              }
            </div>

            <!-- Presence Light between Icon and Nickname -->
            <span class="user-status-dot ${currentPresence}" id="panelProfileStatusDot"></span>

            <!-- Inline Clickable Nickname -->
            <div id="inlineNickWrap" style="display: flex; align-items: center;">
              <span id="inlineNickDisplay" class="profile-nickname-clickable" title="Click to rename" style="font-weight: 800; font-size: 16px; cursor: pointer; display: flex; align-items: center; gap: 6px;">
                <span>${profile.name || 'Joueur'}</span>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="opacity: 0.4;"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              </span>
              <input type="text" id="inlineNickInput" value="${profile.name || 'Joueur'}" maxlength="24" class="cloud-input" style="display: none; height: 30px; font-size: 14px; font-weight: 800; width: 140px; padding: 2px 8px;">
            </div>
          </div>

          <!-- Presence Status Selector Button -->
          <div style="position: relative;" id="presenceDropdownContainer">
            <button class="chip plate-button" id="btnTogglePresence" style="height: 30px; padding: 0 12px; font-size: 11px; gap: 7px; border: none; background: rgba(255, 255, 255, 0.06);">
              <span class="user-status-dot ${currentPresence}"></span>
              <span id="btnPresenceText" style="text-transform: capitalize; font-weight: 700;">${currentPresence}</span>
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9"/></svg>
            </button>

            <!-- Presence Menu Dropdown -->
            <div id="presenceMenu" class="plate" style="display: ${presenceMenuOpen ? 'flex' : 'none'}; flex-direction: column; position: absolute; top: calc(100% + 6px); right: 0; z-index: 120; min-width: 130px; padding: 4px; gap: 2px; border-radius: var(--radius-sm); box-shadow: 0 8px 24px rgba(0,0,0,0.5);">
              <button class="presence-option-btn plate-button ${currentPresence === 'online' ? 'active' : ''}" data-presence="online">
                <span class="user-status-dot online"></span>
                <span>Online</span>
              </button>
              <button class="presence-option-btn plate-button ${currentPresence === 'idle' ? 'active' : ''}" data-presence="idle">
                <span class="user-status-dot idle"></span>
                <span>Idle</span>
              </button>
              <button class="presence-option-btn plate-button ${currentPresence === 'offline' ? 'active' : ''}" data-presence="offline">
                <span class="user-status-dot offline"></span>
                <span>Offline</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Avatar Controls -->
        <div style="display: flex; gap: 8px;">
          <button class="btn-secondary plate-button" id="btnFloatingChangeAvatar" style="height: 26px; padding: 0 10px; font-size: 11px; border: none; background: rgba(255, 255, 255, 0.06);">
            Change Photo
          </button>
          ${
            profile.avatarDataUrl
              ? `<button class="btn-secondary plate-button" id="btnFloatingRemoveAvatar" style="height: 26px; padding: 0 10px; font-size: 11px; color: var(--compat-broken); border: none; background: rgba(255, 255, 255, 0.06);">Remove</button>`
              : ''
          }
        </div>

        <!-- 2. Friend Code Row (Seamless, No line, No separate box) -->
        <div class="profile-unified-code-row">
          <div>
            <div style="font-size: 10px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.6px; color: var(--ink-secondary);">Your Friend Code</div>
            <div class="friend-code-display" style="font-size: 15px; margin-top: 3px; letter-spacing: 0.5px;">
              ${profile.id ? formatFriendCode(profile.id) : '----'}
            </div>
          </div>
          <button class="btn-secondary plate-button" id="btnCopyFloatingProfileCode" style="height: 28px; padding: 0 12px; font-size: 11px; border: none; background: rgba(255, 255, 255, 0.06);">
            Copy Code
          </button>
        </div>

        <!-- 3. Share Last Game (Seamless, No line, No separate box) -->
        <div class="profile-unified-share-row">
          <div>
            <div style="font-size: 13px; font-weight: 700; color: var(--ink-primary);">Share Last Game</div>
            <div style="font-size: 11px; color: var(--ink-secondary); margin-top: 2px;">Broadcast current game to friends</div>
          </div>
          <label class="clean-switch">
            <input type="checkbox" id="chkFloatingShareLastGame" ${store.get().settings.shareLastGame !== false ? 'checked' : ''}>
            <span class="slider"></span>
          </label>
        </div>
      </div>
    </div>
  `;
}

function attachProfileListeners(panel: HTMLElement) {
  const emuApi = (window as any).emufii;
  const profile = store.get().profile;

  const displayEl = panel.querySelector('#inlineNickDisplay') as HTMLElement;
  const inputEl = panel.querySelector('#inlineNickInput') as HTMLInputElement;

  const saveInlineNick = async () => {
    if (!inputEl) return;
    const newName = inputEl.value.trim() || 'Joueur';
    await emuApi.config.updateProfile({ name: newName });
    store.set({ profile: { ...store.get().profile, name: newName } });
    sfx.play('confirm');
    renderFloatingPanel();
  };

  displayEl?.addEventListener('click', () => {
    if (displayEl && inputEl) {
      displayEl.style.display = 'none';
      inputEl.style.display = 'block';
      inputEl.focus();
      inputEl.select();
    }
  });

  inputEl?.addEventListener('keydown', (e: KeyboardEvent) => {
    if (e.key === 'Enter') {
      saveInlineNick();
    } else if (e.key === 'Escape') {
      inputEl.value = profile.name || 'Joueur';
      inputEl.style.display = 'none';
      displayEl.style.display = 'flex';
    }
  });

  inputEl?.addEventListener('blur', () => {
    const newName = inputEl.value.trim();
    if (newName && newName !== profile.name) {
      saveInlineNick();
    } else {
      inputEl.style.display = 'none';
      displayEl.style.display = 'flex';
    }
  });

  panel.querySelector('#btnTogglePresence')?.addEventListener('click', (e) => {
    e.stopPropagation();
    presenceMenuOpen = !presenceMenuOpen;
    const menu = panel.querySelector('#presenceMenu') as HTMLElement;
    if (menu) menu.style.display = presenceMenuOpen ? 'flex' : 'none';
  });

  panel.querySelectorAll('.presence-option-btn').forEach((btn) => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      sfx.play('click');
      const p = (e.currentTarget as HTMLElement).dataset.presence as 'online' | 'idle' | 'offline';
      if (p) {
        currentPresence = p;
        localStorage.setItem('emufii_presence', p);
        presenceMenuOpen = false;

        // Update header status dot
        const headerDot = document.getElementById('headerStatusDot');
        if (headerDot) headerDot.className = `user-status-dot ${p}`;

        // Announce presence to coordinator
        try {
          await emuApi.coordinator.announcePresence(profile.id, profile.name, false);
        } catch {}

        renderFloatingPanel();
      }
    });
  });

  panel.addEventListener('click', (e) => {
    if (presenceMenuOpen && !(e.target as HTMLElement).closest('#presenceDropdownContainer')) {
      presenceMenuOpen = false;
      const menu = panel.querySelector('#presenceMenu') as HTMLElement;
      if (menu) menu.style.display = 'none';
    }
  });

  panel.querySelector('#btnFloatingChangeAvatar')?.addEventListener('click', async () => {
    sfx.play('click');
    const dataUrl = await emuApi.dialog.selectAvatar();
    if (dataUrl) {
      await emuApi.config.updateProfile({ avatarDataUrl: dataUrl });
      store.set({ profile: { ...store.get().profile, avatarDataUrl: dataUrl } });
      const pid = store.get().profile.id;
      const ownerKey = store.get().profile.ownerKey || pid;
      if (pid) {
        encodeAvatarToWebpBase64(dataUrl)
          .then((webpBase64) => {
            if (webpBase64) {
              emuApi.coordinator.uploadAvatar(pid, ownerKey, webpBase64).catch(() => {});
            }
          })
          .catch(() => {});
      }
      renderFloatingPanel();
    }
  });

  panel.querySelector('#btnFloatingRemoveAvatar')?.addEventListener('click', async () => {
    sfx.play('click');
    await emuApi.config.updateProfile({ avatarDataUrl: null });
    const pid = store.get().profile.id;
    if (pid) {
      emuApi.coordinator.deleteAvatar(pid, pid).catch(() => {});
    }
    store.set({ profile: { ...store.get().profile, avatarDataUrl: null } });
    renderFloatingPanel();
  });

  panel.querySelector('#btnCopyFloatingProfileCode')?.addEventListener('click', () => {
    const p = store.get().profile;
    if (p.id) {
      navigator.clipboard.writeText(formatFriendCode(p.id));
      sfx.play('confirm');
      const btn = panel.querySelector('#btnCopyFloatingProfileCode') as HTMLButtonElement;
      if (btn) {
        btn.textContent = 'Copied!';
        setTimeout(() => (btn.textContent = 'Copy Code'), 2000);
      }
    }
  });

  panel.querySelector('#chkFloatingShareLastGame')?.addEventListener('change', async (e) => {
    const checked = (e.target as HTMLInputElement).checked;
    await emuApi.config.updateSettings({ shareLastGame: checked });
    store.set({ settings: { ...store.get().settings, shareLastGame: checked } });
    sfx.play('toggle');
  });
}
