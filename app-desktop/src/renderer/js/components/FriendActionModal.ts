import { store } from '../state.js';
import { sfx } from '../sfx.js';
import {
  formatFriendCode,
  normalizeFriendCode,
  deriveFriendInboxId,
} from '../../../main/profile/friendCode.js';

function escapeHtml(str: string): string {
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * Resolves detailed display name info for a friend.
 */
export function resolveFriendNameDetails(code: string): {
  displayName: string;
  deviceName: string | null;
  customNick: string | null;
} {
  const state = store.get();
  const customNick = state.settings.friendNicknames?.[code]?.trim() || null;
  const liveName = state.friendStatuses?.present?.[code]?.name?.trim() || null;
  const mapName = state.friendStatuses?.names?.[code]?.trim() || null;
  const savedName = state.settings.friendNames?.[code]?.trim() || null;
  const deviceName = liveName || mapName || savedName || null;
  const displayName = customNick || deviceName || formatFriendCode(code);
  return { displayName, deviceName, customNick };
}

/**
 * Resolves the display name string for a friend:
 * 1. Custom nickname set by user (if any)
 * 2. Live presence name from coordinator
 * 3. Persisted device username from coordinator
 * 4. Formatted friend code fallback
 */
export function resolveFriendDisplayName(code: string): string {
  return resolveFriendNameDetails(code).displayName;
}

/**
 * Resolves the avatar data URL for a friend if available.
 */
export function resolveFriendAvatar(code: string): string | null {
  const state = store.get();
  return state.settings.friendAvatars?.[code] || null;
}

/**
 * Resolves the real-time status label and game info for a friend.
 */
export function resolveFriendActivity(code: string): {
  isOnline: boolean;
  isInGame: boolean;
  isPlaying: boolean;
  sessionCode: string | null;
  gameTitle: string | null;
  lastGameTitle: string | null;
  statusText: string;
} {
  const state = store.get();
  const presence = state.friendStatuses?.present?.[code];
  const lastGame = state.friendStatuses?.lastGames?.[code];
  const isOnline = !!presence;
  const sessionCode = presence?.sessionCode || null;

  // Check if friend is currently in a session or playing a recent game launched within the last 2 hours while online
  const recentLaunch =
    isOnline && lastGame?.title && (!lastGame.at || Date.now() - lastGame.at < 2 * 3600 * 1000)
      ? lastGame.title
      : null;

  const gameTitle = presence?.romTitle || recentLaunch || null;
  const isInGame = isOnline && !!(sessionCode || gameTitle);
  const lastGameTitle = lastGame?.title || null;

  let statusText = 'Offline';
  if (isInGame && gameTitle) {
    statusText = `Online · Playing ${gameTitle}`;
  } else if (isInGame) {
    statusText = 'Online · In a game';
  } else if (isOnline) {
    statusText = lastGameTitle ? `Online · Last played: ${lastGameTitle}` : 'Online';
  } else if (lastGameTitle) {
    statusText = `Offline · Last played: ${lastGameTitle}`;
  }

  return {
    isOnline,
    isInGame,
    isPlaying: isInGame,
    sessionCode,
    gameTitle,
    lastGameTitle,
    statusText,
  };
}

/**
 * Encodes a data URL image into a 256x256 WebP base64 string (<= 40 KB)
 * matching AvatarSync.kt in app-android so the coordinator accepts it.
 */
export async function encodeAvatarToWebpBase64(dataUrl: string): Promise<string | null> {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      try {
        const canvas = document.createElement('canvas');
        const side = 256;
        canvas.width = side;
        canvas.height = side;
        const ctx = canvas.getContext('2d');
        if (!ctx) return resolve(null);

        const minDim = Math.min(img.width, img.height);
        const sx = Math.floor((img.width - minDim) / 2);
        const sy = Math.floor((img.height - minDim) / 2);
        ctx.drawImage(img, sx, sy, minDim, minDim, 0, 0, side, side);

        let quality = 0.85;
        while (quality >= 0.3) {
          const webpUrl = canvas.toDataURL('image/webp', quality);
          const base64 = webpUrl.includes(',') ? webpUrl.split(',')[1] : webpUrl;
          const byteLen = Math.ceil((base64.length * 3) / 4);
          if (byteLen <= 40 * 1024) {
            return resolve(base64);
          }
          quality -= 0.15;
        }
        const fallback = canvas.toDataURL('image/webp', 0.25);
        resolve(fallback.includes(',') ? fallback.split(',')[1] : fallback);
      } catch {
        resolve(null);
      }
    };
    img.onerror = () => resolve(null);
    img.src = dataUrl;
  });
}

/**
 * Emufii-themed confirmation modal when removing a friend (matching PadDialog in FriendsScreen.kt).
 */
export function confirmRemoveFriendModal(params: {
  code: string;
  name?: string;
  avatarUrl?: string | null;
  onRemoved?: () => void;
}): Promise<boolean> {
  return new Promise((resolve) => {
    const { displayName, deviceName } = resolveFriendNameDetails(params.code);
    const name = params.name || displayName;
    const avatar = params.avatarUrl !== undefined ? params.avatarUrl : resolveFriendAvatar(params.code);
    const formattedCode = formatFriendCode(params.code);

    const overlay = document.createElement('div');
    overlay.className = 'modal-backdrop visible';
    overlay.style.cssText = `
      position: fixed;
      inset: 0;
      z-index: 10000;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(8, 6, 14, 0.72);
      backdrop-filter: blur(10px);
      animation: fadeIn 160ms ease-out;
    `;

    const avatarHtml = avatar
      ? `<img src="${avatar}" alt="${escapeHtml(name)}" style="width: 100%; height: 100%; object-fit: cover; border-radius: 50%;">`
      : `<span>${escapeHtml(name.slice(0, 2).toUpperCase())}</span>`;

    overlay.innerHTML = `
      <div class="plate modal-window" role="dialog" aria-modal="true" aria-labelledby="removeFriendTitle" style="width: 420px; max-width: calc(100vw - 32px); padding: 26px; border-radius: 26px; display: flex; flex-direction: column; align-items: center; text-align: center; gap: 16px; border: 1px solid var(--plate-border); box-shadow: 0 24px 48px rgba(0, 0, 0, 0.55);">
        <!-- Friend Badge -->
        <div style="position: relative;">
          <div style="width: 68px; height: 68px; border-radius: 50%; background: var(--bg-shelf); display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: 900; color: var(--accent-bright); border: 2px solid rgba(239, 68, 68, 0.45); box-shadow: 0 0 24px rgba(239, 68, 68, 0.18); overflow: hidden;">
            ${avatarHtml}
          </div>
          <div style="position: absolute; bottom: -2px; right: -2px; width: 24px; height: 24px; border-radius: 50%; background: #ef4444; color: #ffffff; display: flex; align-items: center; justify-content: center; border: 2px solid var(--plate-bg);">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          </div>
        </div>

        <!-- Title & Code -->
        <div>
          <h3 id="removeFriendTitle" style="margin: 0 0 4px 0; font-size: 19px; font-weight: 800; color: var(--ink-primary);">
            Remove ${escapeHtml(name)}?
          </h3>
          ${
            deviceName && deviceName !== name
              ? `<div style="font-size: 12px; color: var(--ink-secondary); margin-bottom: 4px;">Device username: <strong>${escapeHtml(deviceName)}</strong></div>`
              : ''
          }
          <div style="display: inline-block; font-family: var(--font-family); font-size: 12px; font-weight: 800; letter-spacing: 0.6px; color: var(--accent-bright); background: var(--plate-bg-low); padding: 4px 10px; border-radius: 9999px; border: 1px solid var(--plate-border);">
            ${formattedCode}
          </div>
        </div>

        <p style="margin: 0; font-size: 13px; color: var(--ink-secondary); line-height: 1.45;">
          This friend will be removed from your friends list and you will no longer receive their real-time online or game alerts.
        </p>

        <!-- Tactile Plate Buttons -->
        <div style="display: flex; gap: 10px; width: 100%; margin-top: 4px;">
          <button class="btn-secondary plate-button" id="btnCancelRemoveFriend" style="flex: 1; height: 42px; font-size: 13px; font-weight: 700; border-radius: 9999px;">
            Cancel
          </button>
          <button class="btn-primary plate-button" id="btnConfirmRemoveFriend" style="flex: 1; height: 42px; font-size: 13px; font-weight: 800; border-radius: 9999px; background: #ef4444; color: #ffffff; border-color: rgba(239, 68, 68, 0.6);">
            Remove Friend
          </button>
        </div>
      </div>
    `;

    document.body.appendChild(overlay);

    const cleanup = async (result: boolean) => {
      window.removeEventListener('keydown', onKeyDown, true);
      overlay.remove();
      if (result) {
        const emuApi = (window as any).emufii;
        if (emuApi?.config?.removeFriend) {
          await emuApi.config.removeFriend(params.code);
          const updated = await emuApi.config.get();
          store.set({ settings: updated.settings });
        }
        params.onRemoved?.();
      }
      resolve(result);
    };

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        sfx.play('click');
        cleanup(false);
      } else if (e.key === 'Enter') {
        e.preventDefault();
        e.stopPropagation();
        sfx.play('confirm');
        cleanup(true);
      }
    };
    window.addEventListener('keydown', onKeyDown, true);

    overlay.querySelector('#btnCancelRemoveFriend')?.addEventListener('click', () => {
      sfx.play('click');
      cleanup(false);
    });

    overlay.querySelector('#btnConfirmRemoveFriend')?.addEventListener('click', () => {
      sfx.play('confirm');
      cleanup(true);
    });

    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) {
        sfx.play('click');
        cleanup(false);
      }
    });

    setTimeout(() => {
      (overlay.querySelector('#btnCancelRemoveFriend') as HTMLButtonElement)?.focus();
    }, 30);
  });
}

/**
 * Emufii-themed modal for setting or clearing a friend's custom nickname
 * (replaces window.prompt which is unsupported in Electron).
 */
export function promptRenameFriendModal(params: {
  code: string;
  currentNick?: string;
  onRenamed?: (newNick: string) => void;
}): Promise<string | null> {
  return new Promise((resolve) => {
    const { displayName, deviceName, customNick } = resolveFriendNameDetails(params.code);
    const initialVal = params.currentNick !== undefined ? params.currentNick : customNick || '';

    const overlay = document.createElement('div');
    overlay.className = 'modal-backdrop visible';
    overlay.style.cssText = `
      position: fixed;
      inset: 0;
      z-index: 10000;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(8, 6, 14, 0.72);
      backdrop-filter: blur(10px);
    `;

    overlay.innerHTML = `
      <div class="plate modal-window" role="dialog" aria-modal="true" style="width: 400px; max-width: calc(100vw - 32px); padding: 24px; border-radius: 24px; display: flex; flex-direction: column; gap: 14px; border: 1px solid var(--plate-border); box-shadow: 0 24px 48px rgba(0, 0, 0, 0.55);">
        <div>
          <h3 style="margin: 0 0 4px 0; font-size: 18px; font-weight: 800; color: var(--ink-primary);">
            Set Friend Nickname
          </h3>
          <p style="margin: 0; font-size: 12px; color: var(--ink-secondary);">
            ${
              deviceName
                ? `Device username: <strong>${escapeHtml(deviceName)}</strong>. Leave empty to use their device username.`
                : `Friend code: ${formatFriendCode(params.code)}. Leave empty to use their device username.`
            }
          </p>
        </div>

        <input type="text" id="inputRenameFriendModal" class="cloud-input" maxlength="24" placeholder="${escapeHtml(deviceName || displayName)}" value="${escapeHtml(initialVal)}" style="height: 40px; font-size: 14px; font-weight: 700;">

        <div style="display: flex; gap: 10px; margin-top: 4px;">
          <button class="btn-secondary plate-button" id="btnCancelRenameFriend" style="flex: 1; height: 38px; font-size: 13px; border-radius: 9999px;">
            Cancel
          </button>
          <button class="btn-primary plate-button" id="btnSaveRenameFriend" style="flex: 1; height: 38px; font-size: 13px; border-radius: 9999px;">
            Save
          </button>
        </div>
      </div>
    `;

    document.body.appendChild(overlay);
    const input = overlay.querySelector('#inputRenameFriendModal') as HTMLInputElement;

    const cleanup = async (val: string | null) => {
      window.removeEventListener('keydown', onKeyDown, true);
      overlay.remove();
      if (val !== null) {
        const emuApi = (window as any).emufii;
        if (emuApi?.config?.addFriend) {
          await emuApi.config.addFriend(params.code, val || undefined);
          const updated = await emuApi.config.get();
          store.set({ settings: updated.settings });
        }
        params.onRenamed?.(val);
      }
      resolve(val);
    };

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        sfx.play('click');
        cleanup(null);
      } else if (e.key === 'Enter') {
        e.preventDefault();
        e.stopPropagation();
        sfx.play('confirm');
        cleanup(input.value.trim());
      }
    };
    window.addEventListener('keydown', onKeyDown, true);

    overlay.querySelector('#btnCancelRenameFriend')?.addEventListener('click', () => {
      sfx.play('click');
      cleanup(null);
    });

    overlay.querySelector('#btnSaveRenameFriend')?.addEventListener('click', () => {
      sfx.play('confirm');
      cleanup(input.value.trim());
    });

    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) {
        sfx.play('click');
        cleanup(null);
      }
    });

    setTimeout(() => {
      input?.focus();
      input?.select();
    }, 30);
  });
}

/**
 * Checks whether the Friends floating panel is currently open.
 */
export function isFriendsFloatingPanelOpen(): boolean {
  const panelEl = document.getElementById('floatingCloudPanel');
  const friendsBtn = document.getElementById('headerFriendsBtn');
  return !!(
    panelEl &&
    panelEl.classList.contains('open') &&
    friendsBtn &&
    friendsBtn.classList.contains('active')
  );
}

let inPanelNoticeTimer: ReturnType<typeof setTimeout> | null = null;

/**
 * Displays a themed top-right Friend Alert or Friend Request toast (matching FriendAlert.kt).
 * If the Friends floating panel is open, displays the notification inside the panel instead
 * of spawning an external floating toast.
 */
export function showFriendToast(params: {
  type: 'online' | 'playing' | 'request' | 'added' | 'accepted' | 'invite';
  code: string;
  name: string;
  avatarUrl?: string | null;
  game?: string | null;
  sessionCode?: string | null;
  onAccept?: () => void;
  onDecline?: () => void;
}) {
  sfx.play('notify');

  let subtitle = 'is online';
  if (params.type === 'playing') {
    subtitle = params.game ? `is playing ${params.game}` : 'is in a game';
  } else if (params.type === 'request') {
    subtitle = `sent you a friend request (${formatFriendCode(params.code)})`;
  } else if (params.type === 'accepted') {
    subtitle = `accepted your friend request (${formatFriendCode(params.code)})`;
  } else if (params.type === 'invite') {
    subtitle = params.game
      ? `invited you to play ${params.game}`
      : `invited you to room ${params.sessionCode || ''}`;
  } else if (params.type === 'added') {
    subtitle = params.game ? `Added! Playing ${params.game}` : 'Added to your friends list!';
  }

  const existing = document.getElementById('emufiiFriendToast');
  if (existing) existing.remove();

  // Requirement: Only show the external floating notification toast if the Friends floating panel is CLOSED.
  // When the Friends floating panel is OPEN, render the notification inside the Friends floating panel.
  if (isFriendsFloatingPanelOpen()) {
    const noticeAt = Date.now();
    store.set({
      inPanelFriendNotice: {
        type: params.type === 'added' ? 'accepted' : params.type,
        name: params.name,
        subtitle,
        avatarUrl: params.avatarUrl || null,
        code: params.code,
        sessionCode: params.sessionCode || undefined,
        at: noticeAt,
      },
    });
    window.dispatchEvent(new CustomEvent('emufii:friends-panel-refresh'));

    if (inPanelNoticeTimer) clearTimeout(inPanelNoticeTimer);
    inPanelNoticeTimer = setTimeout(() => {
      const current = store.get().inPanelFriendNotice;
      if (current && current.at === noticeAt) {
        store.set({ inPanelFriendNotice: null });
        window.dispatchEvent(new CustomEvent('emufii:friends-panel-refresh'));
      }
    }, 6500);
    return;
  }

  const toast = document.createElement('div');
  toast.id = 'emufiiFriendToast';
  toast.className = 'plate';
  toast.style.cssText = `
    position: fixed;
    top: 56px;
    right: 24px;
    z-index: 9950;
    width: 340px;
    max-width: calc(100vw - 32px);
    padding: 14px 16px;
    border-radius: 20px;
    border: 1px solid var(--accent-subtle);
    box-shadow: 0 16px 36px rgba(0, 0, 0, 0.55);
    display: flex;
    flex-direction: column;
    gap: 10px;
    animation: fadeIn 200ms cubic-bezier(0.2, 0.9, 0.3, 1.15);
  `;

  const avatarHtml = params.avatarUrl
    ? `<img src="${params.avatarUrl}" alt="${escapeHtml(params.name)}" style="width: 100%; height: 100%; object-fit: cover; border-radius: 50%;">`
    : `<span>${escapeHtml((params.name || 'F').slice(0, 2).toUpperCase())}</span>`;

  const hasActions = params.type === 'request' || params.type === 'invite';
  const acceptLabel = params.type === 'invite' ? 'Join Room' : 'Accept Request';
  const declineLabel = params.type === 'invite' ? 'Dismiss' : 'Decline';

  toast.innerHTML = `
    <div style="display: flex; align-items: center; gap: 12px;">
      <div style="position: relative; flex-shrink: 0;">
        <div style="width: 42px; height: 42px; border-radius: 50%; background: var(--bg-shelf); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 15px; color: var(--accent-bright); border: 1.5px solid var(--plate-border); overflow: hidden;">
          ${avatarHtml}
        </div>
        <div style="position: absolute; bottom: 0; right: 0; width: 11px; height: 11px; border-radius: 50%; background: var(--compat-perfect); border: 2px solid var(--plate-bg);"></div>
      </div>
      <div style="flex: 1; min-width: 0;">
        <div style="font-weight: 800; font-size: 14px; color: var(--ink-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
          ${escapeHtml(params.name)}
        </div>
        <div style="font-size: 12px; color: var(--ink-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
          ${escapeHtml(subtitle)}
        </div>
      </div>
      <button id="btnDismissFriendToast" style="background: transparent; border: none; color: var(--ink-secondary); cursor: pointer; font-size: 16px; line-height: 1; padding: 4px;">&times;</button>
    </div>
    ${
      hasActions
        ? `
      <div style="display: flex; gap: 8px; margin-top: 2px;">
        <button class="btn-secondary plate-button" id="btnDeclineToastReq" style="flex: 1; height: 30px; font-size: 11px; border-radius: 9999px;">
          ${declineLabel}
        </button>
        <button class="btn-primary plate-button" id="btnAcceptToastReq" style="flex: 1; height: 30px; font-size: 11px; border-radius: 9999px;">
          ${acceptLabel}
        </button>
      </div>
    `
        : ''
    }
  `;

  document.body.appendChild(toast);

  const timeoutMs = hasActions ? 12000 : 4500;
  const timer = setTimeout(() => {
    toast.remove();
  }, timeoutMs);

  toast.querySelector('#btnDismissFriendToast')?.addEventListener('click', () => {
    clearTimeout(timer);
    toast.remove();
  });

  toast.querySelector('#btnAcceptToastReq')?.addEventListener('click', () => {
    clearTimeout(timer);
    toast.remove();
    params.onAccept?.();
  });

  toast.querySelector('#btnDeclineToastReq')?.addEventListener('click', () => {
    clearTimeout(timer);
    toast.remove();
    params.onDecline?.();
  });
}

// -------------------------------------------------------------
// REAL-TIME FRIEND WATCHER & SYNCHRONIZATION (5s Interval)
// -------------------------------------------------------------
let previousOnlineState: Record<string, { online: boolean; game: string | null }> = {};
let hasInitializedWatchState = false;
const declinedRequestCodes = new Set<string>();
const notifiedInviteKeys = new Set<string>();
let pollInFlight = false;

export async function syncFriendsRealtime(onUpdated?: () => void): Promise<void> {
  if (pollInFlight) return;
  const emuApi = (window as any).emufii;
  if (!emuApi?.coordinator?.friendStatuses) return;

  pollInFlight = true;
  try {
    const state = store.get();
    let friends = [...(state.settings.friends || [])];
    const myCode = normalizeFriendCode(state.profile.id || '') || state.profile.id;
    const myInboxId = myCode ? deriveFriendInboxId(myCode) : null;

    const discoveredNames: Record<string, string> = { ...(state.settings.friendNames || {}) };
    const discoveredAvatars: Record<string, string> = { ...(state.settings.friendAvatars || {}) };
    const discoveredHashes: Record<string, string> = { ...(state.settings.friendAvatarHashes || {}) };
    let metadataChanged = false;

    let pendingRequests = [...(state.settings.pendingFriendRequests || [])];
    let pendingInvites = [...(state.settings.pendingSessionInvites || [])];
    let requestsChanged = false;
    let invitesChanged = false;

    // 0. Poll official v2.0.4 /friends/inbox endpoint (requests, accepted, invites)
    if (myCode && emuApi.coordinator.fetchFriendInbox) {
      try {
        const inbox = await emuApi.coordinator.fetchFriendInbox(myCode);
        if (inbox) {
          // A. Process mutual accepted friendships (popped by coordinator on read)
          if (Array.isArray(inbox.accepted)) {
            for (const acc of inbox.accepted) {
              const accCode = normalizeFriendCode(acc?.id || '');
              if (accCode && accCode !== myCode) {
                if (!friends.includes(accCode)) {
                  await emuApi.config.addFriend(accCode);
                  friends.push(accCode);
                }
                if (acc.name && acc.name.trim()) {
                  discoveredNames[accCode] = acc.name.trim();
                  metadataChanged = true;
                }
                let accAvatar = discoveredAvatars[accCode] || null;
                if (!accAvatar && emuApi.coordinator.fetchAvatar) {
                  try {
                    accAvatar = await emuApi.coordinator.fetchAvatar(accCode);
                    if (accAvatar) {
                      discoveredAvatars[accCode] = accAvatar;
                      metadataChanged = true;
                    }
                  } catch {}
                }
                showFriendToast({
                  type: 'accepted',
                  code: accCode,
                  name: acc.name?.trim() || discoveredNames[accCode] || formatFriendCode(accCode),
                  avatarUrl: accAvatar,
                });
              }
            }
          }

          // B. Process incoming friend requests from /friends/inbox
          if (Array.isArray(inbox.requests)) {
            for (const req of inbox.requests) {
              const fromCode = normalizeFriendCode(req?.from || '');
              const fromName = (req?.name || 'Player').trim();
              if (
                fromCode &&
                fromCode !== myCode &&
                !friends.includes(fromCode) &&
                !declinedRequestCodes.has(fromCode) &&
                !pendingRequests.some((r) => r.code === fromCode)
              ) {
                let reqAvatar: string | null = discoveredAvatars[fromCode] || null;
                if (!reqAvatar && emuApi.coordinator.fetchAvatar) {
                  try {
                    reqAvatar = await emuApi.coordinator.fetchAvatar(fromCode);
                    if (reqAvatar) {
                      discoveredAvatars[fromCode] = reqAvatar;
                      metadataChanged = true;
                    }
                  } catch {}
                }
                if (fromName && fromName !== 'Player') {
                  discoveredNames[fromCode] = fromName;
                  metadataChanged = true;
                }
                const newReq = {
                  code: fromCode,
                  name: fromName,
                  avatarDataUrl: reqAvatar,
                  at: req.at || Date.now(),
                };
                pendingRequests = [newReq, ...pendingRequests];
                requestsChanged = true;

                showFriendToast({
                  type: 'request',
                  code: fromCode,
                  name: fromName,
                  avatarUrl: reqAvatar,
                  onAccept: () => acceptFriendRequest(fromCode, fromName, reqAvatar),
                  onDecline: () => declineFriendRequest(fromCode),
                });
              }
            }
          }

          // C. Process incoming session invites from /friends/inbox
          if (Array.isArray(inbox.invites)) {
            const nextInvites = inbox.invites
              .filter((inv: any) => inv && inv.from && inv.code)
              .map((inv: any) => {
                const fromCode = normalizeFriendCode(inv.from) || inv.from;
                return {
                  from: fromCode,
                  name: (inv.name || discoveredNames[fromCode] || formatFriendCode(fromCode)).trim(),
                  code: String(inv.code).toUpperCase(),
                  romTitle: inv.rom_title || null,
                  romTitleId: inv.rom_title_id || null,
                  at: inv.at || Date.now(),
                };
              });

            const prevKeys = pendingInvites.map((i) => `${i.from}:${i.code}`).join(',');
            const nextKeys = nextInvites.map((i: any) => `${i.from}:${i.code}`).join(',');
            if (prevKeys !== nextKeys) {
              pendingInvites = nextInvites;
              invitesChanged = true;
            }

            for (const inv of nextInvites) {
              const invKey = `${inv.from}:${inv.code}`;
              if (!notifiedInviteKeys.has(invKey)) {
                notifiedInviteKeys.add(invKey);
                showFriendToast({
                  type: 'invite',
                  code: inv.from,
                  name: inv.name,
                  avatarUrl: discoveredAvatars[inv.from] || null,
                  game: inv.romTitle || inv.code,
                  sessionCode: inv.code,
                  onAccept: () => acceptSessionInvite(inv),
                  onDecline: () => dismissSessionInvite(inv),
                });
              }
            }
          }
        }
      } catch {}
    }

    const queryIds = [...friends];
    if (myInboxId && !queryIds.includes(myInboxId)) {
      queryIds.push(myInboxId);
    }

    const reply =
      queryIds.length > 0
        ? await emuApi.coordinator.friendStatuses(queryIds)
        : { present: {}, names: {}, avatars: {}, lastGames: {} };

    if (!reply) return;

    // 1. Collect device usernames from reply.names and reply.present
    for (const code of friends) {
      const nameFromMap = reply.names?.[code]?.trim();
      const nameFromPresence = reply.present?.[code]?.name?.trim();
      const resolvedDeviceName = nameFromPresence || nameFromMap;
      if (resolvedDeviceName && discoveredNames[code] !== resolvedDeviceName) {
        discoveredNames[code] = resolvedDeviceName;
        metadataChanged = true;
      }
    }

    // 2. Fetch friend avatars if hash changed or not yet cached
    for (const code of friends) {
      const remoteHash = reply.avatars?.[code];
      const needFetch =
        (remoteHash && discoveredHashes[code] !== remoteHash) ||
        (!discoveredAvatars[code] && (remoteHash || reply.present?.[code]));

      if (needFetch && emuApi.coordinator.fetchAvatar) {
        try {
          const dataUrl = await emuApi.coordinator.fetchAvatar(code);
          if (dataUrl) {
            discoveredAvatars[code] = dataUrl;
            if (remoteHash) discoveredHashes[code] = remoteHash;
            metadataChanged = true;
          }
        } catch {}
      }
    }

    // 3. Fallback check for legacy Friend Request inbox
    if (myInboxId) {
      const inboxGame = reply.lastGames?.[myInboxId];
      const fromCodeRaw = inboxGame?.titleId || null;
      const fromCode = fromCodeRaw ? normalizeFriendCode(fromCodeRaw) : null;
      const fromName = inboxGame?.title || reply.present?.[myInboxId]?.name || reply.names?.[myInboxId] || 'Player';

      if (
        fromCode &&
        fromCode !== myCode &&
        !friends.includes(fromCode) &&
        !declinedRequestCodes.has(fromCode) &&
        !pendingRequests.some((r) => r.code === fromCode)
      ) {
        let reqAvatar: string | null = null;
        try {
          if (emuApi.coordinator.fetchAvatar) {
            reqAvatar = await emuApi.coordinator.fetchAvatar(fromCode);
          }
        } catch {}

        const newReq = {
          code: fromCode,
          name: fromName,
          avatarDataUrl: reqAvatar,
          at: inboxGame?.at || Date.now(),
        };
        pendingRequests = [newReq, ...pendingRequests];
        requestsChanged = true;

        showFriendToast({
          type: 'request',
          code: fromCode,
          name: fromName,
          avatarUrl: reqAvatar,
          onAccept: () => acceptFriendRequest(fromCode, fromName, reqAvatar),
          onDecline: () => declineFriendRequest(fromCode),
        });
      }
    }

    // Filter out any pending requests that are already in friends
    const filteredPendingRequests = pendingRequests.filter((r) => !friends.includes(r.code));
    if (filteredPendingRequests.length !== pendingRequests.length) {
      pendingRequests = filteredPendingRequests;
      requestsChanged = true;
    }

    // 4. Persist any new device usernames / avatars / pending requests / invites
    if (metadataChanged && emuApi.config?.noteFriendMetadata) {
      await emuApi.config.noteFriendMetadata(discoveredNames, discoveredAvatars, discoveredHashes);
    }
    if ((requestsChanged || invitesChanged) && emuApi.config?.updateSettings) {
      await emuApi.config.updateSettings({
        pendingFriendRequests: pendingRequests,
        pendingSessionInvites: pendingInvites,
      });
    }

    const updatedSettings = {
      ...store.get().settings,
      friends,
      friendNames: discoveredNames,
      friendAvatars: discoveredAvatars,
      friendAvatarHashes: discoveredHashes,
      pendingFriendRequests: pendingRequests,
      pendingSessionInvites: pendingInvites,
    };

    store.set({
      friendStatuses: reply,
      settings: updatedSettings,
    });

    // 5. Emit real-time Online / Playing alerts (matching FriendWatcher.kt)
    const nextOnlineState: Record<string, { online: boolean; game: string | null }> = {};
    for (const code of friends) {
      const activity = resolveFriendActivity(code);
      const currentGame = activity.isInGame ? activity.gameTitle || 'Game' : null;
      nextOnlineState[code] = { online: activity.isOnline, game: currentGame };

      if (hasInitializedWatchState && updatedSettings.notifyFriends !== false) {
        const prev = previousOnlineState[code];
        const displayName = resolveFriendDisplayName(code);
        const avatarUrl = discoveredAvatars[code] || null;

        if ((!prev || !prev.online) && activity.isOnline) {
          showFriendToast({
            type: currentGame ? 'playing' : 'online',
            code,
            name: displayName,
            avatarUrl,
            game: currentGame,
          });
        } else if (prev && prev.online && currentGame && prev.game !== currentGame) {
          showFriendToast({
            type: 'playing',
            code,
            name: displayName,
            avatarUrl,
            game: currentGame,
          });
        }
      }
    }
    previousOnlineState = nextOnlineState;
    hasInitializedWatchState = true;

    if (requestsChanged || invitesChanged) {
      window.dispatchEvent(new CustomEvent('emufii:friends-panel-refresh'));
    }

    onUpdated?.();
  } catch {
    // Ignore transient network errors during poll
  } finally {
    pollInFlight = false;
  }
}

export async function acceptFriendRequest(
  code: string,
  name?: string,
  avatarDataUrl?: string | null
): Promise<void> {
  const emuApi = (window as any).emufii;
  const state = store.get();
  const normalized = normalizeFriendCode(code);
  if (!normalized) return;

  await emuApi.config.addFriend(normalized);
  if (name) {
    await emuApi.config.noteFriendMetadata(
      { [normalized]: name },
      avatarDataUrl ? { [normalized]: avatarDataUrl } : undefined
    );
  }
  const pending = (state.settings.pendingFriendRequests || []).filter((r) => r.code !== normalized);
  await emuApi.config.updateSettings({ pendingFriendRequests: pending });

  if (state.profile.id && emuApi.coordinator?.answerFriendRequest) {
    emuApi.coordinator
      .answerFriendRequest(state.profile.id, normalized, true, state.profile.name || 'Joueur')
      .catch(() => {});
  }
  if (state.profile.id && emuApi.coordinator?.clearFriendRequestInbox) {
    emuApi.coordinator.clearFriendRequestInbox(state.profile.id).catch(() => {});
  }

  const cfg = await emuApi.config.get();
  store.set({ settings: cfg.settings });
  await syncFriendsRealtime();
}

export async function declineFriendRequest(code: string): Promise<void> {
  const emuApi = (window as any).emufii;
  const state = store.get();
  const normalized = normalizeFriendCode(code) || code;
  declinedRequestCodes.add(normalized);

  const pending = (state.settings.pendingFriendRequests || []).filter((r) => r.code !== normalized);
  await emuApi.config.updateSettings({ pendingFriendRequests: pending });

  if (state.profile.id && emuApi.coordinator?.answerFriendRequest) {
    emuApi.coordinator
      .answerFriendRequest(state.profile.id, normalized, false, state.profile.name || 'Joueur')
      .catch(() => {});
  }
  if (state.profile.id && emuApi.coordinator?.clearFriendRequestInbox) {
    emuApi.coordinator.clearFriendRequestInbox(state.profile.id).catch(() => {});
  }

  const cfg = await emuApi.config.get();
  store.set({ settings: cfg.settings });
}

export async function dismissSessionInvite(invite: { from: string; code: string }): Promise<void> {
  const emuApi = (window as any).emufii;
  const state = store.get();
  const myCode = normalizeFriendCode(state.profile.id || '') || state.profile.id;
  const fromCode = normalizeFriendCode(invite.from) || invite.from;

  const remaining = (state.settings.pendingSessionInvites || []).filter(
    (i) => !(i.from === fromCode && i.code === invite.code)
  );
  await emuApi.config.updateSettings({ pendingSessionInvites: remaining });

  if (myCode && emuApi.coordinator?.dismissSessionInvite) {
    emuApi.coordinator.dismissSessionInvite(myCode, fromCode, invite.code).catch(() => {});
  }

  const cfg = await emuApi.config.get();
  store.set({ settings: cfg.settings });
  window.dispatchEvent(new CustomEvent('emufii:friends-panel-refresh'));
}

export async function acceptSessionInvite(invite: {
  from: string;
  code: string;
  romTitle?: string | null;
  romTitleId?: string | null;
}): Promise<void> {
  const emuApi = (window as any).emufii;
  const state = store.get();
  await dismissSessionInvite(invite);

  try {
    const remote = await emuApi.coordinator.getSession(invite.code);
    const allRoms = store.get().roms || [];
    const matchedRom = allRoms.find(
      (r: any) =>
        (remote.romTitleId && (r.titleId === remote.romTitleId || r.productCode === remote.romTitleId)) ||
        (remote.romTitle && r.displayName.toLowerCase() === remote.romTitle.toLowerCase())
    );

    const config = await emuApi.config.get();
    const wgInfo = await emuApi.coordinator.claimAddress(
      invite.code,
      config.wgKeys.publicKey,
      state.profile.name,
      state.profile.id
    );

    const isPs2 = matchedRom?.console === 'PS2';
    const dns = isPs2 ? '10.67.0.1' : null;
    const wgRes = await emuApi.wireguard.up(wgInfo, dns);

    store.set({
      activeSession: {
        code: invite.code,
        isHost: false,
        rom: matchedRom || null,
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
    store.navigate('session');
  } catch (err: any) {
    alert('Could not join invited session: ' + (err?.message || 'Session ended'));
  }
}

/**
 * Adds a friend by code with full validation (format, self-check, duplicate-check),
 * sends a real-time Friend Request to the added user, and immediately fetches their
 * device username, profile picture, online status, and active game.
 */
export async function addFriendWithSync(
  rawInput: string,
  customNickname?: string
): Promise<{ ok: boolean; error?: string; code?: string }> {
  const emuApi = (window as any).emufii;
  const state = store.get();
  const normalized = normalizeFriendCode(rawInput);

  if (!normalized) {
    return { ok: false, error: 'Invalid friend code. Enter a 12-character code (XXXX-XXXX-XXXX).' };
  }

  const myCode = normalizeFriendCode(state.profile.id || '') || state.profile.id;
  if (normalized === myCode) {
    return { ok: false, error: "That's your own friend code. You cannot add yourself." };
  }

  if ((state.settings.friends || []).includes(normalized)) {
    return { ok: false, error: 'This friend is already in your friends list.' };
  }

  const res = await emuApi.config.addFriend(normalized, customNickname);
  if (res && res.ok === false) {
    if (res.error === 'self') {
      return { ok: false, error: "That's your own friend code. You cannot add yourself." };
    }
    return { ok: false, error: 'Invalid friend code.' };
  }

  // Send real-time friend request to the added user via coordinator (/friends/request)
  if (myCode && emuApi.coordinator?.sendFriendRequest) {
    emuApi.coordinator.sendFriendRequest(normalized, myCode, state.profile.name || 'Joueur').catch(() => {});
  }

  const cfg = await emuApi.config.get();
  store.set({ settings: cfg.settings });

  // Immediately fetch their real-time status, device username, avatar, and current game
  await syncFriendsRealtime();

  const displayName = resolveFriendDisplayName(normalized);
  const avatarUrl = resolveFriendAvatar(normalized);
  const activity = resolveFriendActivity(normalized);

  showFriendToast({
    type: 'added',
    code: normalized,
    name: displayName,
    avatarUrl,
    game: activity.gameTitle || activity.lastGameTitle,
  });

  return { ok: true, code: normalized };
}
