import { store } from '../state.js';
import { sfx } from '../sfx.js';
import { formatFriendCode, attachFriendCodeInputFormatter } from '../../../main/profile/friendCode.js';
import {
  resolveFriendDisplayName,
  resolveFriendAvatar,
  resolveFriendActivity,
  confirmRemoveFriendModal,
  promptRenameFriendModal,
  addFriendWithSync,
  syncFriendsRealtime,
} from './FriendActionModal.js';

let friendsModalKeydownHandler: ((e: KeyboardEvent) => void) | null = null;

export async function openFriendsModal() {
  const dialog = document.getElementById('friendsModal') as HTMLDialogElement;
  if (!dialog) return;

  await syncFriendsRealtime();

  renderFriendsContent(dialog);
  dialog.showModal();

  if (friendsModalKeydownHandler) {
    window.removeEventListener('keydown', friendsModalKeydownHandler);
  }
  friendsModalKeydownHandler = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      closeFriendsModal();
    }
  };
  window.addEventListener('keydown', friendsModalKeydownHandler);
}

export function closeFriendsModal() {
  const dialog = document.getElementById('friendsModal') as HTMLDialogElement;
  if (friendsModalKeydownHandler) {
    window.removeEventListener('keydown', friendsModalKeydownHandler);
    friendsModalKeydownHandler = null;
  }
  if (!dialog || !dialog.open) return;

  dialog.setAttribute('data-closing', 'true');
  setTimeout(() => {
    dialog.close();
    dialog.removeAttribute('data-closing');
  }, 220);
}

function renderFriendsContent(dialog: HTMLDialogElement) {
  const state = store.get();
  const emuApi = (window as any).emufii;
  const friends = state.settings.friends || [];

  const content = dialog.querySelector('.dialog-content') as HTMLElement;
  if (!content) return;

  content.innerHTML = `
    <div class="cloud-card" style="width: 580px; max-height: 85vh;">
      <!-- Modal Header -->
      <div class="cloud-header">
        <div style="display: flex; align-items: center; gap: 10px;">
          <div class="cloud-icon-badge">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
          </div>
          <div>
            <h2 class="cloud-title">Friends</h2>
            <p class="cloud-subtitle">Online presence and multiplayer buddies</p>
          </div>
        </div>
      </div>

      <!-- Modal Body -->
      <div class="cloud-body" style="display: flex; flex-direction: column; gap: 16px;">
        <!-- My Code Banner -->
        <div class="friend-code-plate" style="display: flex; align-items: center; justify-content: space-between; padding: 12px 16px;">
          <div>
            <div style="font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; color: var(--ink-secondary);">Your Friend Code</div>
            <div class="friend-code-display" style="font-size: 16px; margin-top: 2px;">
              ${state.profile.id ? formatFriendCode(state.profile.id) : '----'}
            </div>
          </div>
          <button class="btn-secondary plate-button" id="btnCopyMyCodeModal" style="height: 32px; padding: 0 14px; font-size: 12px;">
            Copy Code
          </button>
        </div>

        <!-- Add Friend Form -->
        <div style="background: var(--plate-bg-low); border: 1px solid var(--plate-border); border-radius: var(--radius-card); padding: 14px 16px;">
          <label class="cloud-section-label" style="margin-bottom: 8px;">Add a Friend</label>
          <div style="display: flex; gap: 8px;">
            <input type="text" id="inputModalFriendCode" class="cloud-input" placeholder="XXXX-XXXX-XXXX" maxlength="14" autocomplete="off" spellcheck="false" style="font-family: var(--font-family); font-weight: 700; text-transform: uppercase;">
            <input type="text" id="inputModalFriendNick" class="cloud-input" placeholder="Nickname (optional)" maxlength="24" style="max-width: 140px;">
            <button class="btn-primary plate-button" id="btnConfirmAddFriendModal" style="width: auto; height: 38px; padding: 0 18px; font-size: 12px;">
              Add
            </button>
          </div>
          <div id="modalFriendCodeError" style="font-size: 12px; color: var(--compat-broken); margin-top: 6px; display: none;">
            Invalid Crockford friend code or checksum.
          </div>
        </div>

        <!-- Friends List -->
        <div>
          <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
            <label class="cloud-section-label">Your Friends (${friends.length})</label>
            <button class="btn-secondary plate-button" id="btnRefreshFriendsModal" style="height: 24px; padding: 0 8px; font-size: 11px;">
              Refresh
            </button>
          </div>

          <div class="friends-list-container" style="display: flex; flex-direction: column; gap: 8px; max-height: 240px; overflow-y: auto;">
            ${
              friends.length === 0
                ? `
              <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 32px 16px; gap: 8px; text-align: center; color: var(--ink-secondary);">
                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="opacity: 0.4;"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                <div style="font-weight: 700; font-size: 14px;">No friends added yet</div>
                <div style="font-size: 12px;">Enter a friend's 12-character code above to see their online status!</div>
              </div>
            `
                : friends
                    .map((code) => {
                      const name = resolveFriendDisplayName(code);
                      const avatar = resolveFriendAvatar(code);
                      const activity = resolveFriendActivity(code);

                      return `
                  <div class="friend-row plate" style="display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; border-radius: var(--radius-sm);">
                    <div style="display: flex; align-items: center; gap: 12px;">
                      <div style="position: relative;">
                        <div style="width: 38px; height: 38px; border-radius: 50%; background: var(--bg-shelf); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 14px; color: var(--accent-bright); border: 1.5px solid var(--plate-border); overflow: hidden;">
                          ${
                            avatar
                              ? `<img src="${avatar}" alt="${name}" style="width: 100%; height: 100%; object-fit: cover;">`
                              : name.slice(0, 2).toUpperCase()
                          }
                        </div>
                        <div style="position: absolute; bottom: 0; right: 0; width: 10px; height: 10px; border-radius: 50%; background: ${activity.isOnline ? 'var(--compat-perfect)' : 'var(--ink-tertiary)'}; border: 2px solid var(--plate-bg);"></div>
                      </div>
                      <div>
                        <div style="display: flex; align-items: center; gap: 8px;">
                          <span style="font-weight: 700; font-size: 14px;">${name}</span>
                          <span style="font-size: 11px; font-family: var(--font-family); color: var(--ink-secondary);">${formatFriendCode(code)}</span>
                        </div>
                        <div style="font-size: 11px; color: ${activity.isPlaying ? 'var(--accent-bright)' : activity.isOnline ? 'var(--compat-perfect)' : 'var(--ink-secondary)'}; margin-top: 2px;">
                          ${activity.statusText}
                        </div>
                      </div>
                    </div>

                    <div style="display: flex; gap: 6px;">
                      ${
                        activity.sessionCode
                          ? `
                      <button class="btn-primary plate-button btn-friend-join" data-code="${activity.sessionCode}" style="height: 28px; padding: 0 10px; font-size: 11px;">
                        Join
                      </button>
                      `
                          : ''
                      }
                      <button class="btn-secondary plate-button btn-friend-rename" data-code="${code}" style="height: 28px; padding: 0 10px; font-size: 11px;">
                        Rename
                      </button>
                      <button class="btn-secondary plate-button btn-friend-remove" data-code="${code}" style="height: 28px; padding: 0 10px; font-size: 11px; color: var(--compat-broken);">
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
    </div>
  `;

  const codeInputModal = dialog.querySelector('#inputModalFriendCode') as HTMLInputElement | null;
  if (codeInputModal) {
    attachFriendCodeInputFormatter(codeInputModal);
  }

  // Attach copy code listener
  dialog.querySelector('#btnCopyMyCodeModal')?.addEventListener('click', () => {
    if (state.profile.id) {
      navigator.clipboard.writeText(formatFriendCode(state.profile.id));
      sfx.play('confirm');
      const btn = dialog.querySelector('#btnCopyMyCodeModal') as HTMLButtonElement;
      if (btn) {
        btn.textContent = 'Copied!';
        setTimeout(() => (btn.textContent = 'Copy Code'), 2000);
      }
    }
  });

  // Refresh statuses
  dialog.querySelector('#btnRefreshFriendsModal')?.addEventListener('click', async () => {
    sfx.play('click');
    await syncFriendsRealtime();
    renderFriendsContent(dialog);
  });

  // Add friend listener
  dialog.querySelector('#btnConfirmAddFriendModal')?.addEventListener('click', async () => {
    const codeInput = dialog.querySelector('#inputModalFriendCode') as HTMLInputElement;
    const nickInput = dialog.querySelector('#inputModalFriendNick') as HTMLInputElement;
    const errorEl = dialog.querySelector('#modalFriendCodeError') as HTMLElement;

    const nick = nickInput.value.trim() || undefined;
    const res = await addFriendWithSync(codeInput.value, nick);
    if (!res.ok) {
      if (errorEl) {
        errorEl.textContent = res.error || 'Invalid friend code.';
        errorEl.style.display = 'block';
      }
      sfx.play('tick');
      return;
    }

    if (errorEl) errorEl.style.display = 'none';
    sfx.play('confirm');
    renderFriendsContent(dialog);
  });

  // Join friend session listener
  dialog.querySelectorAll('.btn-friend-join').forEach((btn) => {
    btn.addEventListener('click', async (e) => {
      const code = (e.currentTarget as HTMLElement).dataset.code;
      if (!code) return;
      sfx.play('click');
      closeFriendsModal();

      try {
        const remote = await emuApi.coordinator.getSession(code);
        const allRoms = store.get().roms || [];
        const matchedRom = allRoms.find(
          (r: any) =>
            (remote.romTitleId && (r.titleId === remote.romTitleId || r.productCode === remote.romTitleId)) ||
            (remote.romTitle && r.displayName.toLowerCase() === remote.romTitle.toLowerCase())
        );

        const config = await emuApi.config.get();
        const wgInfo = await emuApi.coordinator.claimAddress(
          code,
          config.wgKeys.publicKey,
          state.profile.name,
          state.profile.id
        );

        const isPs2 = matchedRom?.console === 'PS2';
        const dns = isPs2 ? '10.67.0.1' : null;
        const wgRes = await emuApi.wireguard.up(wgInfo, dns);

        store.set({
          activeSession: {
            code,
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
        alert('Could not join friend room: ' + err.message);
      }
    });
  });

  // Rename friend listener
  dialog.querySelectorAll('.btn-friend-rename').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const code = (e.currentTarget as HTMLElement).dataset.code;
      if (!code) return;
      promptRenameFriendModal({
        code,
        onRenamed: () => renderFriendsContent(dialog),
      });
    });
  });

  // Remove friend listener
  dialog.querySelectorAll('.btn-friend-remove').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const code = (e.currentTarget as HTMLElement).dataset.code;
      if (!code) return;
      confirmRemoveFriendModal({
        code,
        onRemoved: () => renderFriendsContent(dialog),
      });
    });
  });
}
