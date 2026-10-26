import { store } from '../state.js';
import { sfx } from '../sfx.js';
import { esc, initials } from '../html.js';
import { toast, errorMessage } from '../toast.js';
import { withTransition } from '../motion.js';
import { formatFriendCode, attachFriendCodeInputFormatter } from '../../../main/profile/friendCode.js';
import { normalizeSessionCode } from '../../../main/network/sessionCodes.js';
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
type Presence = 'online' | 'idle' | 'offline';

const TRIGGERS: Record<FloatingTab, string> = {
  sessions: 'headerFinderBtn',
  join: 'headerFinderBtn',
  friends: 'headerFriendsBtn',
  profile: 'headerProfileBtn',
  server: 'vpsStatusChip',
};

const REFRESH_ICON =
  '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>';
const SESSIONS_ICON =
  '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" class="sessions-icon"><rect x="2" y="3" width="9.5" height="7.5" rx="2" stroke="currentColor" stroke-width="2" fill="none" /><rect x="12.5" y="13.5" width="9.5" height="7.5" rx="2" stroke="currentColor" stroke-width="2" fill="none" /><line x1="6.5" y1="10.5" x2="17.5" y2="13.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-dasharray="0.1 3.8" /></svg>';

let currentTab: FloatingTab = 'sessions';
let isOpen = false;
let enteredJoinCode = '';
let joining = false;
let keydownListener: ((e: KeyboardEvent) => void) | null = null;
let publicSessions: OpenSession[] = [];
let loadingSessions = false;
let sessionsLoaded = false;
let currentPresence: Presence = (localStorage.getItem('emufii_presence') as Presence) || 'online';
let presenceMenuOpen = false;
let relayLatencies: Record<string, number | null> = {};
let measuringRelays = false;
let restoreFocusTo: HTMLElement | null = null;

export function isFloatingPanelOpen(): boolean {
  return isOpen;
}

export function getFloatingPanelTab(): FloatingTab | null {
  return isOpen ? currentTab : null;
}

export function getPresence(): Presence {
  return currentPresence;
}

export function positionFloatingPanel(tab: FloatingTab) {
  const panel = document.getElementById('floatingCloudPanel');
  if (!panel) return;

  const screenWidth = window.innerWidth;
  const targetWidth = tab === 'server' ? 480 : tab === 'profile' ? 420 : tab === 'join' ? 440 : 500;
  const panelWidth = Math.min(targetWidth, screenWidth - 32);
  panel.style.width = `${panelWidth}px`;
  panel.style.maxHeight = '';

  if (tab === 'join') {
    panel.style.left = `${Math.round((screenWidth - panelWidth) / 2)}px`;
    panel.style.top = `${Math.max(84, Math.round(window.innerHeight * 0.2))}px`;
    panel.style.transformOrigin = '50% 0%';
    return;
  }

  const trigger = document.getElementById(TRIGGERS[tab]);
  if (!trigger) return;
  const rect = trigger.getBoundingClientRect();

  // Triggers in the navigation rail open their flyout beside it, anchored to the trigger.
  if (trigger.closest('.app-rail')) {
    const height = Math.min(panel.scrollHeight || 420, window.innerHeight - 32);
    const top = Math.max(16, Math.min(rect.top, window.innerHeight - height - 16));
    panel.style.left = `${Math.round(rect.right + 12)}px`;
    panel.style.top = `${Math.round(top)}px`;
    panel.style.maxHeight = `${window.innerHeight - top - 16}px`;
    panel.style.transformOrigin = rect.top > window.innerHeight / 2 ? '0% 100%' : '0% 0%';
    return;
  }

  const center = rect.left + rect.width / 2;
  const left = Math.max(16, Math.min(center - panelWidth / 2, screenWidth - panelWidth - 16));
  const top = Math.round(rect.bottom + 10);
  panel.style.top = `${top}px`;
  panel.style.left = `${Math.round(left)}px`;
  panel.style.maxHeight = `${window.innerHeight - top - 16}px`;
  const origin = Math.max(5, Math.min(95, Math.round(((center - left) / panelWidth) * 100)));
  panel.style.transformOrigin = `${origin}% 0%`;
}

if (typeof window !== 'undefined') {
  window.addEventListener('resize', () => {
    if (isOpen) positionFloatingPanel(currentTab);
  });
  window.addEventListener('emufii:friends-panel-refresh', () => {
    if (isShowing('friends')) renderFloatingPanel();
  });
}

function pageFor(tab: FloatingTab): PanelPage | null {
  return tab === 'friends' ? 'friends' : tab === 'sessions' || tab === 'join' ? 'sessions' : null;
}

export function toggleFloatingPanel(tab: FloatingTab) {
  const page = pageFor(tab);
  if (page) {
    closeFloatingPanel();
    store.navigate(page);
    if (tab === 'join') setTimeout(() => (document.getElementById('joinCodeInput') as HTMLInputElement | null)?.focus(), 380);
    return;
  }
  if (isOpen && currentTab === tab) {
    closeFloatingPanel();
  } else if (isOpen) {
    // Morph the open panel into the next one rather than redrawing it in place.
    withTransition(() => openFloatingPanel(tab), 'panel');
  } else {
    openFloatingPanel(tab);
  }
}

export function openFloatingPanel(tab: FloatingTab = 'sessions') {
  if (pageFor(tab)) {
    toggleFloatingPanel(tab);
    return;
  }
  const panel = document.getElementById('floatingCloudPanel');
  const backdrop = document.getElementById('floatingPanelBackdrop');
  if (!panel) return;

  const wasOpen = isOpen;
  if (!wasOpen) restoreFocusTo = document.activeElement as HTMLElement | null;
  currentTab = tab;
  isOpen = true;
  presenceMenuOpen = false;
  sfx.play('toggle');

  updateHeaderActiveState(tab);
  panel.dataset.tab = tab;
  renderFloatingPanel();
  positionFloatingPanel(tab);

  if (tab === 'sessions') refreshSessions();
  if (tab === 'friends') {
    document.getElementById('emufiiFriendToast')?.remove();
    syncFriendsRealtime(() => {
      if (isShowing('friends')) renderFloatingPanel();
    });
  }
  if (tab === 'server') refreshRelayLatencies();

  panel.classList.remove('closing');
  panel.classList.add('open');
  panel.setAttribute('aria-hidden', 'false');
  panel.removeAttribute('inert');
  backdrop?.classList.add('open');
  backdrop?.classList.toggle('dim', tab === 'join');

  attachKeyboard();

  requestAnimationFrame(() => {
    const target =
      panel.querySelector<HTMLElement>('[data-autofocus]') ||
      (document.body.dataset.input !== 'mouse' ? panel.querySelector<HTMLElement>('button, input, [tabindex="0"]') : null);
    target?.focus({ preventScroll: true });
  });
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
    const hadFocus = panel.contains(document.activeElement);
    panel.classList.remove('open');
    panel.classList.add('closing');
    panel.setAttribute('aria-hidden', 'true');
    panel.setAttribute('inert', '');
    setTimeout(() => panel.classList.remove('closing'), 200);
    if (hadFocus) {
      (restoreFocusTo && document.contains(restoreFocusTo) ? restoreFocusTo : document.getElementById(TRIGGERS[currentTab]))?.focus({
        preventScroll: true,
      });
    }
  }
  restoreFocusTo = null;
  backdrop?.classList.remove('open', 'dim');
}

function updateHeaderActiveState(tab: FloatingTab | null) {
  for (const t of ['sessions', 'friends', 'profile', 'server'] as FloatingTab[]) {
    const el = document.getElementById(TRIGGERS[t]);
    el?.classList.toggle('active', tab === t || (t === 'sessions' && tab === 'join'));
    el?.setAttribute('aria-expanded', String(tab === t));
  }
}

function attachKeyboard() {
  detachKeyboard();
  keydownListener = (e: KeyboardEvent) => {
    if (!isOpen || e.key !== 'Escape') return;
    if (document.querySelector('.modal-backdrop, .context-menu')) return;
    if (presenceMenuOpen) {
      presenceMenuOpen = false;
      renderFloatingPanel();
    } else {
      closeFloatingPanel();
    }
    e.preventDefault();
    e.stopPropagation();
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
        title: 'Relay region',
        subtitle: 'Where your sessions are relayed',
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>',
        action: `<button class="panel-header-btn plate-button" id="btnRefreshRelays" title="Measure again">${REFRESH_ICON}<span>Ping</span></button>`,
      };
    case 'sessions':
      return {
        title: 'Open sessions',
        subtitle: 'Public rooms you can join right now',
        icon: SESSIONS_ICON,
        action: `
          <button class="panel-header-btn plate-button" id="btnOpenJoinFromSessions" title="Join with a code (Ctrl+J)">Join with code</button>
          <button class="panel-header-btn icon-only plate-button" id="btnRefreshFloatingSessions" title="Refresh">${REFRESH_ICON}</button>`,
      };
    case 'join':
      return {
        title: 'Join a session',
        subtitle: 'Enter the code your friend shared',
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>',
        action: '',
      };
    case 'friends':
      return {
        title: 'Friends',
        subtitle: 'Live status and friend codes',
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
        action: `<button class="panel-header-btn icon-only plate-button" id="btnFloatingRefreshFriends" title="Refresh">${REFRESH_ICON}</button>`,
      };
    case 'profile':
      return {
        title: 'Your profile',
        subtitle: 'What other players see in lobbies',
        icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>',
        action: '',
      };
  }
}

// Sessions and Friends are full pages on desktop; they reuse the panel renderers inside a host.
export type PanelPage = 'sessions' | 'friends';
let embedded: { host: HTMLElement; page: PanelPage } | null = null;

const PAGE_TABS: Record<PanelPage, FloatingTab[]> = {
  sessions: ['join', 'sessions'],
  friends: ['friends'],
};

function isShowing(tab: FloatingTab): boolean {
  return (isOpen && currentTab === tab) || (!!embedded && PAGE_TABS[embedded.page].includes(tab));
}

export function mountPanelPage(host: HTMLElement, page: PanelPage): () => void {
  embedded = { host, page };
  if (page === 'sessions') refreshSessions();
  else syncFriendsRealtime(() => renderFloatingPanel());
  renderEmbedded();
  return () => {
    if (embedded?.host === host) embedded = null;
  };
}

function preserveAndRender(root: HTMLElement, html: string, attach: () => void) {
  const activeEl = document.activeElement as HTMLElement | null;
  const activeId = activeEl && root.contains(activeEl) ? activeEl.id : null;
  const activeSel = activeEl instanceof HTMLInputElement ? [activeEl.selectionStart, activeEl.selectionEnd] : null;
  const scrolls = Array.from(root.querySelectorAll<HTMLElement>('[data-scroll-key]')).map((el) => [el.dataset.scrollKey, el.scrollTop] as const);
  const existingCodeVal = (root.querySelector('#inputFloatingFriendCode') as HTMLInputElement | null)?.value || '';

  root.innerHTML = html;
  attach();

  for (const [key, top] of scrolls) {
    const el = root.querySelector<HTMLElement>(`[data-scroll-key="${key}"]`);
    if (el) el.scrollTop = top;
  }
  if (existingCodeVal) {
    const codeInput = root.querySelector('#inputFloatingFriendCode') as HTMLInputElement | null;
    if (codeInput) codeInput.value = existingCodeVal;
  }
  if (activeId) {
    const toFocus = root.querySelector<HTMLElement>(`#${CSS.escape(activeId)}`);
    toFocus?.focus({ preventScroll: true });
    if (toFocus instanceof HTMLInputElement && activeSel) {
      try {
        toFocus.setSelectionRange(activeSel[0], activeSel[1]);
      } catch {}
    }
  }
}

function renderEmbedded() {
  if (!embedded || !document.contains(embedded.host)) {
    embedded = null;
    return;
  }
  const { host, page } = embedded;
  if (page === 'sessions') {
    preserveAndRender(
      host,
      `<section class="page-card join-card">
         <div class="page-card-head"><h2>Join with a code</h2><p>Paste or type the code your friend shared.</p></div>
         ${renderJoinTab()}
       </section>
       <section class="page-card">
         <div class="page-card-head">
           <h2>Open sessions</h2><p>Public rooms you can jump into right now.</p>
           <button class="panel-header-btn plate-button" id="btnRefreshFloatingSessions" title="Refresh (F5)">${REFRESH_ICON}<span>Refresh</span></button>
         </div>
         <div data-scroll-key="sessions-page">${renderSessionsTab()}</div>
       </section>`,
      () => {
        attachJoinListeners(host);
        attachSessionsListeners(host);
      }
    );
  } else {
    preserveAndRender(host, `<div class="friends-page">${renderFriendsTab()}</div>`, () => attachFriendsListeners(host));
  }
}

export function renderFloatingPanel() {
  renderEmbedded();
  const panel = document.getElementById('floatingCloudPanel');
  if (!panel || !isOpen) return;

  const header = getTabHeaderInfo(currentTab, store.get().profile);

  panel.setAttribute('role', 'dialog');
  panel.setAttribute('aria-label', currentTab);
  preserveAndRender(panel, `
    <div class="floating-panel-container">
      <div class="floating-panel-header">
        <div class="floating-panel-heading-wrap">
          <div class="floating-panel-icon-bubble">${header.icon}</div>
          <div class="floating-panel-heading-text">
            <h3 class="floating-pane-title">${header.title}</h3>
            <p class="floating-pane-subtitle">${header.subtitle}</p>
          </div>
        </div>
        <div class="floating-panel-actions">${header.action}</div>
      </div>
      <div class="floating-panel-body" id="floatingPanelBody" data-scroll-key="body">
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
  `, () => {
    if (currentTab === 'server') attachServerListeners(panel);
    else if (currentTab === 'sessions') attachSessionsListeners(panel);
    else if (currentTab === 'join') attachJoinListeners(panel);
    else if (currentTab === 'friends') attachFriendsListeners(panel);
    else attachProfileListeners(panel);
  });
}

function avatarHtml(url: string | null | undefined, name: string, size = 34): string {
  return `
    <div class="panel-avatar" style="width: ${size}px; height: ${size}px;">
      ${url ? `<img src="${esc(url)}" alt="">` : `<span>${initials(name, 'F')}</span>`}
    </div>`;
}

// -------------------------------------------------------------
// SERVER PICKER
// -------------------------------------------------------------
const REGION_META: Record<string, { title: string; badge: string }> = {
  eu: { title: 'Europe', badge: 'EU' },
  na: { title: 'Canada (Montréal)', badge: 'CA' },
  usw: { title: 'US West (Seattle)', badge: 'US' },
  jp: { title: 'Japan (Tokyo)', badge: 'JP' },
  use: { title: 'US East (New York)', badge: 'US' },
};

function rttClass(rtt: number | null): string {
  if (rtt === null) return 'unknown';
  return rtt < 60 ? 'good' : rtt < 130 ? 'fair' : 'poor';
}

function renderSignalBars(rtt: number | null): string {
  const lit = rtt === null ? 0 : rtt < 60 ? 3 : rtt < 130 ? 2 : 1;
  return `<span class="signal-bars ${rttClass(rtt)}" aria-hidden="true">${[0, 1, 2]
    .map((i) => `<span class="${i < lit ? 'lit' : ''}"></span>`)
    .join('')}</span>`;
}

function getBestAutoRegion(): { code: string; title: string; rtt: number | null } {
  let bestCode = 'use';
  let bestRtt: number | null = null;
  for (const [code, rtt] of Object.entries(relayLatencies)) {
    if (typeof rtt === 'number' && rtt >= 0 && (bestRtt === null || rtt < bestRtt)) {
      bestCode = code;
      bestRtt = rtt;
    }
  }
  return { code: bestCode, title: REGION_META[bestCode]?.title || REGION_META.use.title, rtt: bestRtt };
}

// Region sent to the coordinator when hosting: the user's pick, or the lowest-RTT relay once measured.
export function getSelectedRegion(): string | null {
  const region = localStorage.getItem('emufii_server_region') || 'auto';
  if (region !== 'auto') return region;
  const best = getBestAutoRegion();
  return best.rtt !== null ? best.code : null;
}

export function updateHeaderVpsLabel() {
  const vpsText = document.getElementById('vpsText');
  if (!vpsText) return;
  const lampOn = document.getElementById('vpsLamp')?.classList.contains('active');
  const region = localStorage.getItem('emufii_server_region') || 'auto';
  const picked = region === 'auto' ? getBestAutoRegion() : { title: REGION_META[region]?.title || REGION_META.use.title, rtt: relayLatencies[region] ?? null };
  vpsText.textContent = `${lampOn === false ? 'Offline' : 'Online'} • ${picked.title}${picked.rtt !== null ? ` · ${picked.rtt} ms` : ''}`;
}

export async function refreshRelayLatencies() {
  if (measuringRelays) return;
  const emuApi = (window as any).emufii;
  if (!emuApi?.coordinator?.measureRelays) return;

  measuringRelays = true;
  if (isShowing('server')) renderFloatingPanel();

  try {
    const measured: { id: string; rttMs: number | null }[] = await emuApi.coordinator.measureRelays();
    if (Array.isArray(measured) && measured.length) {
      const next: Record<string, number | null> = {};
      for (const relay of measured) {
        if (relay?.id) next[relay.id] = typeof relay.rttMs === 'number' ? Math.round(relay.rttMs) : null;
      }
      relayLatencies = next;
    }
  } catch {
  } finally {
    measuringRelays = false;
    updateHeaderVpsLabel();
    if (isShowing('server')) renderFloatingPanel();
  }
}

function renderServerTab(): string {
  const currentRegion = localStorage.getItem('emufii_server_region') || 'auto';
  const best = getBestAutoRegion();
  const rows = [
    {
      id: 'auto',
      title: 'Automatic',
      badge: '',
      rtt: best.rtt,
      detail: measuringRelays && best.rtt === null ? 'Finding the closest relay…' : `Closest: ${best.title}`,
    },
    ...['eu', 'use', 'usw', 'na', 'jp'].map((code) => {
      const rtt = relayLatencies[code] ?? null;
      return {
        id: code,
        title: REGION_META[code].title,
        badge: REGION_META[code].badge,
        rtt,
        detail: rtt !== null ? `${rtt} ms` : measuringRelays ? 'Measuring…' : 'Unreachable',
      };
    }),
  ];

  return `
    <div class="server-picker-list" role="radiogroup" aria-label="Relay region">
      ${rows
        .map((r) => {
          const chosen = r.id === currentRegion;
          return `
          <button class="server-picker-row ${chosen ? 'chosen' : ''}" data-region="${r.id}" role="radio" aria-checked="${chosen}">
            <span class="server-picker-badge">${
              r.badge ||
              '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="12" cy="12" r="10"/><polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"/></svg>'
            }</span>
            <span class="server-picker-meta">
              <span class="server-picker-name">${r.title}</span>
              <span class="server-picker-detail ${rttClass(r.rtt)}">${r.detail}</span>
            </span>
            ${r.id === 'auto' ? '' : renderSignalBars(r.rtt)}
            <span class="server-picker-check">${chosen ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>' : ''}</span>
          </button>`;
        })
        .join('')}
    </div>
  `;
}

function attachServerListeners(panel: HTMLElement) {
  const rows = Array.from(panel.querySelectorAll<HTMLElement>('.server-picker-row'));
  rows.forEach((row, idx) => {
    row.addEventListener('click', () => {
      sfx.play('click');
      localStorage.setItem('emufii_server_region', row.dataset.region || 'auto');
      updateHeaderVpsLabel();
      closeFloatingPanel();
    });
    row.addEventListener('keydown', (e) => {
      if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
      e.preventDefault();
      rows[(idx + (e.key === 'ArrowDown' ? 1 : rows.length - 1)) % rows.length].focus();
    });
  });
  panel.querySelector('#btnRefreshRelays')?.addEventListener('click', () => {
    sfx.play('click');
    refreshRelayLatencies();
  });
}

// -------------------------------------------------------------
// SESSIONS
// -------------------------------------------------------------
function formatAge(seconds: number): string {
  const m = Math.floor((seconds || 0) / 60);
  if (m < 1) return 'just now';
  if (m < 60) return `${m} min ago`;
  return `${Math.floor(m / 60)} h ago`;
}

function renderSessionsTab(): string {
  if (loadingSessions && publicSessions.length === 0) {
    return '<div class="panel-empty"><div class="spinner"></div></div>';
  }
  if (publicSessions.length === 0) {
    return `
      <div class="panel-empty">
        ${SESSIONS_ICON}
        <strong>No open sessions right now</strong>
        <span>Host one from your library, or join a friend with their code.</span>
      </div>`;
  }
  return `
    <div class="panel-list" data-scroll-key="sessions">
      ${publicSessions
        .map(
          (s) => `
        <div class="panel-row session-row" data-code="${esc(s.code)}">
          <span class="session-code-chip">${esc(s.code)}</span>
          <div class="panel-row-main">
            <div class="panel-row-title">${esc(s.romTitle || 'Custom session')}</div>
            <div class="panel-row-sub">${esc(s.hostName || 'Player')} · ${s.players} ${s.players === 1 ? 'player' : 'players'} · ${formatAge(s.ageSeconds)}</div>
          </div>
          <button class="btn-primary plate-button btn-small btn-join-floating-session" data-code="${esc(s.code)}">Join</button>
        </div>`
        )
        .join('')}
    </div>`;
}

function attachSessionsListeners(panel: HTMLElement) {
  panel.querySelector('#btnRefreshFloatingSessions')?.addEventListener('click', () => {
    sfx.play('click');
    refreshSessions();
  });
  panel.querySelector('#btnOpenJoinFromSessions')?.addEventListener('click', () => {
    sfx.play('click');
    withTransition(() => openFloatingPanel('join'), 'panel');
  });
  panel.querySelectorAll<HTMLElement>('.btn-join-floating-session').forEach((btn) => {
    btn.addEventListener('click', () => connectToSession(btn.dataset.code || ''));
  });
  panel.querySelectorAll<HTMLElement>('.session-row').forEach((row) => {
    row.addEventListener('dblclick', () => connectToSession(row.dataset.code || ''));
  });
}

async function refreshSessions() {
  if (loadingSessions) return;
  const emuApi = (window as any).emufii;
  loadingSessions = true;
  if (isShowing('sessions')) renderFloatingPanel();

  try {
    const timeout = new Promise<OpenSession[]>((_, reject) => setTimeout(() => reject(new Error('Timeout')), 6000));
    publicSessions = (await Promise.race([emuApi.coordinator.listSessions(), timeout])) || [];
  } catch {
    if (!sessionsLoaded) publicSessions = [];
  } finally {
    loadingSessions = false;
    sessionsLoaded = true;
    if (isShowing('sessions')) renderFloatingPanel();
  }
}

// -------------------------------------------------------------
// JOIN BY CODE
// -------------------------------------------------------------
function cleanJoinInput(raw: string): string {
  return raw.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6);
}

function renderJoinTab(): string {
  return `
    <div class="join-pane">
      <label class="join-code-field">
        <input id="joinCodeInput" class="join-code-input" data-autofocus value="${esc(enteredJoinCode)}" maxlength="8"
          autocomplete="off" spellcheck="false" aria-label="Session code" ${joining ? 'disabled' : ''}>
        <span class="join-code-boxes" aria-hidden="true">
          ${Array.from({ length: 6 })
            .map((_, i) => `${i === 3 ? '<span class="join-code-dash">–</span>' : ''}<span class="join-code-box" data-i="${i}"></span>`)
            .join('')}
        </span>
      </label>
      <p class="join-hint">Type or paste a code like <strong>ABC-234</strong>. Press <kbd>Enter</kbd> to join.</p>
      <div class="join-actions">
        <button class="btn-secondary plate-button" id="btnFloatingPaste">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1"/></svg>
          Paste
        </button>
        <button class="btn-primary plate-button" id="btnSubmitFloatingJoin" disabled>${joining ? '<span class="spinner small"></span> Joining…' : 'Join session'}</button>
      </div>
    </div>`;
}

function syncJoinUi(panel: ParentNode) {
  const input = panel.querySelector<HTMLInputElement>('#joinCodeInput');
  const caret = input && document.activeElement === input ? enteredJoinCode.length : -1;
  panel.querySelectorAll<HTMLElement>('.join-code-box').forEach((box) => {
    const i = Number(box.dataset.i);
    box.textContent = enteredJoinCode[i] || '';
    box.classList.toggle('filled', !!enteredJoinCode[i]);
    box.classList.toggle('caret', i === Math.min(caret, 5) && caret >= 0 && enteredJoinCode.length < 6);
  });
  const submit = panel.querySelector<HTMLButtonElement>('#btnSubmitFloatingJoin');
  if (submit) submit.disabled = joining || enteredJoinCode.length !== 6;
}

function attachJoinListeners(panel: HTMLElement) {
  const input = panel.querySelector<HTMLInputElement>('#joinCodeInput');
  if (!input) return;

  const setCode = (raw: string) => {
    const next = cleanJoinInput(raw);
    if (next.length > enteredJoinCode.length) sfx.play(next.length === 6 ? 'pop' : 'tick');
    enteredJoinCode = next;
    input.value = next;
    syncJoinUi(panel);
  };

  input.addEventListener('input', () => setCode(input.value));
  input.addEventListener('focus', () => syncJoinUi(panel));
  input.addEventListener('blur', () => syncJoinUi(panel));
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      submitJoinSession();
    }
  });

  panel.querySelector('#btnFloatingPaste')?.addEventListener('click', async () => {
    try {
      setCode(await navigator.clipboard.readText());
      input.focus();
    } catch {}
  });
  panel.querySelector('#btnSubmitFloatingJoin')?.addEventListener('click', submitJoinSession);
  syncJoinUi(panel);
}

function submitJoinSession() {
  if (enteredJoinCode.length !== 6 || joining) return;
  connectToSession(enteredJoinCode);
}

async function connectToSession(rawCode: string) {
  const code = normalizeSessionCode(rawCode);
  if (!code || joining) return;
  sfx.play('click');
  const emuApi = (window as any).emufii;
  const profile = store.get().profile;

  joining = true;
  if (isShowing('join')) renderFloatingPanel();

  try {
    const remote = await emuApi.coordinator.getSession(code);
    const config = await emuApi.config.get();
    const wgInfo = await emuApi.coordinator.claimAddress(code, config.wgKeys.publicKey, profile.name, profile.id);
    const matchedRom = store
      .get()
      .roms.find(
        (r) =>
          (remote?.romTitleId && (r.titleId === remote.romTitleId || r.productCode === remote.romTitleId)) ||
          (remote?.romTitle && r.displayName.toLowerCase() === String(remote.romTitle).toLowerCase())
      );
    const dns = matchedRom?.console === 'PS2' ? '10.67.0.1' : null;
    const wgRes = await emuApi.wireguard.up(wgInfo, dns);

    store.set({
      activeSession: {
        code,
        isHost: false,
        rom: matchedRom || store.get().selectedRom || null,
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
    enteredJoinCode = '';
    joining = false;
    closeFloatingPanel();
    store.navigate('session');
  } catch (err) {
    joining = false;
    if (isShowing('join')) renderFloatingPanel();
    toast(`Could not join ${code}: ${errorMessage(err, 'session not found')}`, 'error');
  }
}

// -------------------------------------------------------------
// FRIENDS
// -------------------------------------------------------------
function renderFriendsTab(): string {
  const state = store.get();
  const friends = state.settings.friends || [];
  const pendingRequests = state.settings.pendingFriendRequests || [];
  const pendingInvites = state.settings.pendingSessionInvites || [];
  const notice = state.inPanelFriendNotice || null;

  const sorted = [...friends].sort((a, b) => {
    const aa = resolveFriendActivity(a);
    const bb = resolveFriendActivity(b);
    return Number(bb.isOnline) - Number(aa.isOnline) || resolveFriendDisplayName(a).localeCompare(resolveFriendDisplayName(b));
  });

  return `
    ${
      notice
        ? `
      <div class="panel-notice">
        ${avatarHtml(notice.avatarUrl, notice.name, 32)}
        <div class="panel-row-main">
          <div class="panel-row-title">${esc(notice.name)}</div>
          <div class="panel-row-sub">${esc(notice.subtitle)}</div>
        </div>
        <button class="panel-icon-btn" id="btnDismissInPanelNotice" title="Dismiss" aria-label="Dismiss">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>`
        : ''
    }

    <form class="add-friend-box" id="formAddFriend" autocomplete="off">
      <label class="panel-section-label" for="inputFloatingFriendCode">Add a friend</label>
      <div class="add-friend-row">
        <input type="text" id="inputFloatingFriendCode" class="cloud-input friend-code-input" placeholder="XXXX-XXXX-XXXX" maxlength="14" spellcheck="false">
        <button type="submit" class="btn-primary plate-button btn-small" id="btnFloatingAddFriend">Add</button>
      </div>
      <div class="field-error" id="floatingFriendError" hidden></div>
    </form>

    ${
      pendingInvites.length
        ? `
      <div class="panel-section">
        <div class="panel-section-label accent">Session invites · ${pendingInvites.length}</div>
        ${pendingInvites
          .map((inv) => {
            const name = inv.name || resolveFriendDisplayName(inv.from);
            return `
            <div class="panel-row highlighted">
              ${avatarHtml(resolveFriendAvatar(inv.from), name, 32)}
              <div class="panel-row-main">
                <div class="panel-row-title">${esc(name)}</div>
                <div class="panel-row-sub accent">Invites you to ${esc(inv.romTitle || inv.code)} · ${esc(inv.code)}</div>
              </div>
              <div class="panel-row-actions">
                <button class="btn-primary plate-button btn-small btn-f-accept-invite" data-from="${esc(inv.from)}" data-code="${esc(inv.code)}">Join</button>
                <button class="btn-secondary plate-button btn-small btn-f-dismiss-invite" data-from="${esc(inv.from)}" data-code="${esc(inv.code)}">Dismiss</button>
              </div>
            </div>`;
          })
          .join('')}
      </div>`
        : ''
    }

    ${
      pendingRequests.length
        ? `
      <div class="panel-section">
        <div class="panel-section-label accent">Friend requests · ${pendingRequests.length}</div>
        ${pendingRequests
          .map((req) => {
            const name = req.name || resolveFriendDisplayName(req.code);
            return `
            <div class="panel-row highlighted">
              ${avatarHtml(resolveFriendAvatar(req.code), name, 32)}
              <div class="panel-row-main">
                <div class="panel-row-title">${esc(name)}</div>
                <div class="panel-row-sub">${esc(formatFriendCode(req.code))}</div>
              </div>
              <div class="panel-row-actions">
                <button class="btn-primary plate-button btn-small btn-f-accept-req" data-code="${esc(req.code)}" data-name="${esc(name)}">Accept</button>
                <button class="btn-secondary plate-button btn-small btn-f-decline-req" data-code="${esc(req.code)}">Decline</button>
              </div>
            </div>`;
          })
          .join('')}
      </div>`
        : ''
    }

    <div class="panel-section">
      <div class="panel-section-label">Friends · ${friends.length}</div>
      ${
        friends.length === 0
          ? '<div class="panel-empty compact"><span>No friends yet. Share your friend code from your profile, or paste theirs above.</span></div>'
          : `<div class="panel-list" data-scroll-key="friends">
          ${sorted
            .map((code) => {
              const name = resolveFriendDisplayName(code);
              const activity = resolveFriendActivity(code);
              const state = activity.isPlaying ? 'playing' : activity.isOnline ? 'online' : 'offline';
              return `
              <div class="panel-row friend-row ${state}" data-code="${esc(code)}">
                <div class="panel-avatar-wrap">
                  ${avatarHtml(resolveFriendAvatar(code), name)}
                  <span class="presence-dot ${state}"></span>
                </div>
                <div class="panel-row-main">
                  <div class="panel-row-title">${esc(name)} <span class="panel-row-code">${esc(formatFriendCode(code))}</span></div>
                  <div class="panel-row-sub ${state}">${esc(activity.statusText)}</div>
                </div>
                <div class="panel-row-actions">
                  ${activity.sessionCode ? `<button class="btn-primary plate-button btn-small btn-f-join" data-code="${esc(activity.sessionCode)}">Join</button>` : ''}
                  <button class="panel-icon-btn btn-f-rename" data-code="${esc(code)}" title="Set nickname" aria-label="Set nickname for ${esc(name)}">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>
                  </button>
                  <button class="panel-icon-btn danger btn-f-remove" data-code="${esc(code)}" title="Remove friend" aria-label="Remove ${esc(name)}">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                  </button>
                </div>
              </div>`;
            })
            .join('')}
        </div>`
      }
    </div>
  `;
}

function attachFriendsListeners(panel: HTMLElement) {
  const codeInput = panel.querySelector('#inputFloatingFriendCode') as HTMLInputElement | null;
  const errorEl = panel.querySelector('#floatingFriendError') as HTMLElement | null;

  if (codeInput) {
    attachFriendCodeInputFormatter(codeInput);
    codeInput.addEventListener('input', () => {
      if (errorEl) errorEl.hidden = true;
    });
  }

  panel.querySelector('#btnFloatingRefreshFriends')?.addEventListener('click', async () => {
    sfx.play('click');
    await syncFriendsRealtime();
    renderFloatingPanel();
  });

  panel.querySelector('#formAddFriend')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!codeInput?.value.trim()) return;
    const res = await addFriendWithSync(codeInput.value);
    if (!res.ok) {
      if (errorEl) {
        errorEl.textContent = res.error || 'Invalid friend code.';
        errorEl.hidden = false;
      }
      sfx.play('tick');
      return;
    }
    codeInput.value = '';
    sfx.play('confirm');
    renderFloatingPanel();
  });

  panel.querySelector('#btnDismissInPanelNotice')?.addEventListener('click', () => {
    sfx.play('click');
    store.set({ inPanelFriendNotice: null });
    renderFloatingPanel();
  });

  const each = (selector: string, fn: (el: HTMLElement) => void) =>
    panel.querySelectorAll<HTMLElement>(selector).forEach((el) => el.addEventListener('click', () => fn(el)));

  each('.btn-f-accept-invite', async (el) => {
    sfx.play('confirm');
    if (!el.dataset.from || !el.dataset.code) return;
    closeFloatingPanel();
    await acceptSessionInvite({ from: el.dataset.from, code: el.dataset.code });
  });
  each('.btn-f-dismiss-invite', async (el) => {
    sfx.play('click');
    if (!el.dataset.from || !el.dataset.code) return;
    await dismissSessionInvite({ from: el.dataset.from, code: el.dataset.code });
    renderFloatingPanel();
  });
  each('.btn-f-accept-req', async (el) => {
    sfx.play('confirm');
    if (!el.dataset.code) return;
    await acceptFriendRequest(el.dataset.code, el.dataset.name);
    renderFloatingPanel();
  });
  each('.btn-f-decline-req', async (el) => {
    sfx.play('click');
    if (!el.dataset.code) return;
    await declineFriendRequest(el.dataset.code);
    renderFloatingPanel();
  });
  each('.btn-f-join', (el) => el.dataset.code && connectToSession(el.dataset.code));
  each('.btn-f-rename', (el) => el.dataset.code && promptRenameFriendModal({ code: el.dataset.code, onRenamed: () => renderFloatingPanel() }));
  each('.btn-f-remove', (el) => el.dataset.code && confirmRemoveFriendModal({ code: el.dataset.code, onRemoved: () => renderFloatingPanel() }));
}

// -------------------------------------------------------------
// PROFILE
// -------------------------------------------------------------
const PRESENCE_LABELS: Record<Presence, string> = { online: 'Online', idle: 'Away', offline: 'Invisible' };

function renderProfileTab(): string {
  const { profile, settings } = store.get();
  return `
    <div class="profile-card">
      <div class="profile-top">
        <button class="profile-avatar-btn" id="btnFloatingChangeAvatar" title="Change photo">
          ${profile.avatarDataUrl ? `<img src="${esc(profile.avatarDataUrl)}" alt="">` : `<span>${initials(profile.name, 'J')}</span>`}
          <span class="profile-avatar-edit" aria-hidden="true">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>
          </span>
        </button>
        <div class="profile-name-block">
          <label class="panel-section-label" for="inlineNickInput">Nickname</label>
          <input type="text" id="inlineNickInput" class="profile-name-input" value="${esc(profile.name || 'Joueur')}" maxlength="24" spellcheck="false" title="Click to rename">
        </div>
        <div class="presence-picker" id="presenceDropdownContainer">
          <button class="presence-btn" id="btnTogglePresence" aria-haspopup="menu" aria-expanded="${presenceMenuOpen}">
            <span class="user-status-dot ${currentPresence}"></span>
            <span>${PRESENCE_LABELS[currentPresence]}</span>
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9"/></svg>
          </button>
          <div class="presence-menu" role="menu" ${presenceMenuOpen ? '' : 'hidden'}>
            ${(['online', 'idle', 'offline'] as Presence[])
              .map(
                (p) => `
              <button class="presence-option-btn ${currentPresence === p ? 'active' : ''}" data-presence="${p}" role="menuitemradio" aria-checked="${currentPresence === p}">
                <span class="user-status-dot ${p}"></span><span>${PRESENCE_LABELS[p]}</span>
              </button>`
              )
              .join('')}
          </div>
        </div>
      </div>

      ${profile.avatarDataUrl ? '<button class="link-btn danger" id="btnFloatingRemoveAvatar">Remove photo</button>' : ''}

      <div class="profile-row">
        <div>
          <div class="panel-section-label">Your friend code</div>
          <div class="friend-code-display">${profile.id ? esc(formatFriendCode(profile.id)) : '----'}</div>
        </div>
        <button class="btn-secondary plate-button btn-small" id="btnCopyFloatingProfileCode">Copy</button>
      </div>

      <label class="profile-row toggle-row">
        <div>
          <div class="profile-row-title">Share what I'm playing</div>
          <div class="profile-row-sub">Friends see the last game you launched</div>
        </div>
        <span class="clean-switch">
          <input type="checkbox" id="chkFloatingShareLastGame" ${settings.shareLastGame !== false ? 'checked' : ''}>
          <span class="slider"></span>
        </span>
      </label>
    </div>
  `;
}

function attachProfileListeners(panel: HTMLElement) {
  const emuApi = (window as any).emufii;
  const nickInput = panel.querySelector<HTMLInputElement>('#inlineNickInput');

  const saveNick = async () => {
    if (!nickInput) return;
    const newName = nickInput.value.trim() || 'Joueur';
    nickInput.value = newName;
    if (newName === store.get().profile.name) return;
    await emuApi.config.updateProfile({ name: newName });
    store.set({ profile: { ...store.get().profile, name: newName } });
    sfx.play('confirm');
    toast('Nickname saved', 'success', 2000);
  };

  nickInput?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      nickInput.blur();
    } else if (e.key === 'Escape') {
      nickInput.value = store.get().profile.name || 'Joueur';
    }
  });
  nickInput?.addEventListener('blur', saveNick);

  panel.querySelector('#btnTogglePresence')?.addEventListener('click', (e) => {
    e.stopPropagation();
    presenceMenuOpen = !presenceMenuOpen;
    renderFloatingPanel();
    if (presenceMenuOpen) panel.querySelector<HTMLElement>('.presence-option-btn.active')?.focus();
  });

  panel.querySelectorAll<HTMLElement>('.presence-option-btn').forEach((btn) => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      sfx.play('click');
      const p = btn.dataset.presence as Presence;
      currentPresence = p;
      localStorage.setItem('emufii_presence', p);
      presenceMenuOpen = false;
      const headerDot = document.getElementById('headerStatusDot');
      if (headerDot) headerDot.className = `user-status-dot ${p}`;
      const profile = store.get().profile;
      if (p !== 'offline' && profile.id) {
        emuApi.coordinator.announcePresence(profile.id, profile.name, false).catch(() => {});
      }
      renderFloatingPanel();
      panel.querySelector<HTMLElement>('#btnTogglePresence')?.focus();
    });
  });

  panel.addEventListener('click', (e) => {
    if (presenceMenuOpen && !(e.target as HTMLElement).closest('#presenceDropdownContainer')) {
      presenceMenuOpen = false;
      renderFloatingPanel();
    }
  });

  panel.querySelector('#btnFloatingChangeAvatar')?.addEventListener('click', async () => {
    sfx.play('click');
    const dataUrl = await emuApi.dialog.selectAvatar();
    if (!dataUrl) return;
    await emuApi.config.updateProfile({ avatarDataUrl: dataUrl });
    store.set({ profile: { ...store.get().profile, avatarDataUrl: dataUrl } });
    const { id, ownerKey } = store.get().profile;
    if (id) {
      encodeAvatarToWebpBase64(dataUrl)
        .then((webp) => webp && emuApi.coordinator.uploadAvatar(id, ownerKey || id, webp).catch(() => {}))
        .catch(() => {});
    }
    renderFloatingPanel();
  });

  panel.querySelector('#btnFloatingRemoveAvatar')?.addEventListener('click', async () => {
    sfx.play('click');
    await emuApi.config.updateProfile({ avatarDataUrl: null });
    const { id, ownerKey } = store.get().profile;
    if (id) emuApi.coordinator.deleteAvatar(id, ownerKey || id).catch(() => {});
    store.set({ profile: { ...store.get().profile, avatarDataUrl: null } });
    renderFloatingPanel();
  });

  panel.querySelector('#btnCopyFloatingProfileCode')?.addEventListener('click', (e) => {
    const id = store.get().profile.id;
    if (!id) return;
    navigator.clipboard.writeText(formatFriendCode(id));
    sfx.play('confirm');
    const btn = e.currentTarget as HTMLButtonElement;
    btn.textContent = 'Copied';
    setTimeout(() => (btn.textContent = 'Copy'), 1600);
  });

  panel.querySelector('#chkFloatingShareLastGame')?.addEventListener('change', async (e) => {
    const checked = (e.target as HTMLInputElement).checked;
    await emuApi.config.updateSettings({ shareLastGame: checked });
    store.set({ settings: { ...store.get().settings, shareLastGame: checked } });
    sfx.play('toggle');
  });
}
