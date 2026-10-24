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
} from '../components/FriendActionModal.js';

export function renderFriendsScreen(container: HTMLElement) {
  const state = store.get();
  const emuApi = (window as any).emufii;
  let selectedIndex = 0;

  container.innerHTML = `
    <div style="flex: 1; display: flex; flex-direction: column; padding: 32px; gap: 24px; overflow-y: auto;">
      <!-- Header -->
      <div style="display: flex; align-items: center; justify-content: space-between;">
        <div>
          <h1 style="font-size: 26px; font-weight: 900; margin-bottom: 4px;">Friends</h1>
          <div style="display: flex; align-items: center; gap: 8px;">
            <span style="font-size: 13px; color: var(--ink-secondary);">Your Friend Code:</span>
            <button class="chip plate-button" id="btnCopyMyCode" style="height: 30px; font-family: var(--font-family); font-size: 13px;">
              ${state.profile.id ? formatFriendCode(state.profile.id) : '----'}
            </button>
          </div>
        </div>

        <div style="display: flex; gap: 12px;">
          <button class="btn-primary plate-button" id="btnAddFriendModal" style="width: auto; height: 40px; padding: 0 20px; font-size: 13px;">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            Add Friend
          </button>
          <button class="btn-secondary plate-button" id="btnBackFriends">
            Back
          </button>
        </div>
      </div>

      <!-- Friends Content -->
      <div id="friendsContent" style="display: flex; flex-direction: column; gap: 24px;">
        <div style="display: flex; justify-content: center; padding: 48px;">
          <div class="spinner"></div>
        </div>
      </div>
    </div>

    <!-- Add Friend Dialog -->
    <dialog id="addFriendDialog" closedby="any">
      <div class="plate dialog-content" style="width: 440px; padding: 28px; display: flex; flex-direction: column; gap: 16px;">
        <h2 style="font-size: 20px; font-weight: 800;">Add a Friend</h2>
        <p style="font-size: 13px; color: var(--ink-secondary);">Enter their 12-character Crockford friend code</p>
        <input type="text" id="inputFriendCode" class="plate" placeholder="XXXX-XXXX-XXXX" maxlength="14" autocomplete="off" spellcheck="false" style="padding: 12px 16px; font-family: var(--font-family); font-size: 16px; font-weight: 700; color: var(--accent-bright); text-align: center; text-transform: uppercase;">
        <div id="friendCodeError" style="font-size: 12px; color: var(--compat-broken); display: none;">Invalid friend code format or checksum.</div>
        <div style="display: flex; gap: 12px; margin-top: 8px;">
          <button class="btn-secondary plate-button" id="btnCancelAddFriend" style="flex: 1;">Cancel</button>
          <button class="btn-primary plate-button" id="btnConfirmAddFriend" style="flex: 1;">Add</button>
        </div>
      </div>
    </dialog>
  `;

  const fetchStatuses = async () => {
    await syncFriendsRealtime();
    renderFriendsList();
  };

  const renderFriendsList = () => {
    const listEl = container.querySelector('#friendsContent');
    if (!listEl) return;

    const currentState = store.get();
    const friends = currentState.settings.friends;
    if (friends.length === 0) {
      listEl.innerHTML = `
        <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 260px; gap: 12px; text-align: center;">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="opacity: 0.35;"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
          <div style="font-weight: 700; font-size: 16px;">No Friends Added Yet</div>
          <div style="font-size: 13px; color: var(--ink-secondary);">Share your friend code or add your friends to see when they are online and playing!</div>
        </div>
      `;
      return;
    }

    const currentFriend = friends[selectedIndex] || friends[0];
    const name = resolveFriendDisplayName(currentFriend);
    const avatar = resolveFriendAvatar(currentFriend);
    const activity = resolveFriendActivity(currentFriend);

    listEl.innerHTML = `
      <!-- Friend Showcase Card (3DS-style) -->
      <div class="plate" style="display: flex; gap: 24px; padding: 28px; align-items: center;">
        <div style="width: 100px; height: 100px; border-radius: var(--radius-xl); background: var(--bg-shelf); display: flex; align-items: center; justify-content: center; font-size: 36px; font-weight: 900; color: var(--accent-bright); border: 2px solid var(--plate-border); overflow: hidden; flex-shrink: 0;">
          ${
            avatar
              ? `<img src="${avatar}" alt="${name}" style="width: 100%; height: 100%; object-fit: cover;">`
              : name.slice(0, 2).toUpperCase()
          }
        </div>

        <div style="flex: 1;">
          <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 4px;">
            <span style="font-size: 22px; font-weight: 800;">${name}</span>
            <span class="lamp ${activity.isOnline ? 'active' : ''}"></span>
            <span style="font-size: 12px; color: var(--ink-secondary); font-weight: 700;">${activity.isOnline ? 'ONLINE' : 'OFFLINE'}</span>
          </div>
          <div style="font-family: var(--font-family); font-size: 14px; color: var(--ink-secondary); margin-bottom: 12px;">
            ${formatFriendCode(currentFriend)}
          </div>

          ${
            activity.sessionCode
              ? `
            <div style="display: flex; align-items: center; gap: 12px;">
              <span class="fact-tag" style="background: var(--accent-subtle); color: var(--accent-bright);">${activity.statusText}</span>
              <button class="btn-primary plate-button" id="btnJoinFriendSession" data-code="${activity.sessionCode}" style="height: 32px; padding: 0 16px; font-size: 12px; width: auto;">
                Join Room
              </button>
            </div>
          `
              : `<div style="font-size: 13px; font-weight: ${activity.isPlaying ? '700' : '500'}; color: ${activity.isPlaying ? 'var(--accent-bright)' : 'var(--ink-secondary)'};">${activity.statusText}</div>`
          }
        </div>

        <div style="display: flex; gap: 8px;">
          <button class="btn-secondary plate-button" id="btnRenameFriendScreen" style="height: 36px; padding: 0 14px; font-size: 12px;">
            Rename
          </button>
          <button class="icon-btn plate-button" id="btnRemoveFriend" title="Remove Friend" style="color: var(--compat-broken);">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          </button>
        </div>
      </div>

      <!-- Friends Selector Row -->
      <div style="display: flex; gap: 16px; overflow-x: auto; padding: 8px 0;">
        ${friends
          .map((f, idx) => {
            const fName = resolveFriendDisplayName(f);
            const fAvatar = resolveFriendAvatar(f);
            return `
          <div class="plate plate-button ${idx === selectedIndex ? 'focused' : ''}" style="width: 140px; padding: 14px; text-align: center; cursor: pointer; flex-shrink: 0;" data-index="${idx}">
            <div style="width: 48px; height: 48px; border-radius: var(--radius-lg); background: var(--bg-shelf); margin: 0 auto 8px auto; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 18px; color: var(--accent-bright); overflow: hidden;">
              ${
                fAvatar
                  ? `<img src="${fAvatar}" alt="${fName}" style="width: 100%; height: 100%; object-fit: cover;">`
                  : fName.slice(0, 2).toUpperCase()
              }
            </div>
            <div style="font-weight: 700; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
              ${fName}
            </div>
          </div>
        `;
          })
          .join('')}
      </div>
    `;

    // Friend selection
    listEl.querySelectorAll('[data-index]').forEach((card) => {
      card.addEventListener('click', (e) => {
        sfx.play('click');
        selectedIndex = parseInt((e.currentTarget as HTMLElement).dataset.index || '0');
        renderFriendsList();
      });
    });

    // Rename friend
    listEl.querySelector('#btnRenameFriendScreen')?.addEventListener('click', () => {
      promptRenameFriendModal({
        code: currentFriend,
        onRenamed: () => renderFriendsList(),
      });
    });

    // Remove friend
    listEl.querySelector('#btnRemoveFriend')?.addEventListener('click', () => {
      confirmRemoveFriendModal({
        code: currentFriend,
        onRemoved: () => {
          selectedIndex = 0;
          renderFriendsList();
        },
      });
    });

    // Join friend's room
    listEl.querySelector('#btnJoinFriendSession')?.addEventListener('click', async (e) => {
      const code = (e.currentTarget as HTMLElement).dataset.code;
      if (code) {
        sfx.play('click');
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
      }
    });
  };

  container.querySelector('#btnCopyMyCode')?.addEventListener('click', () => {
    sfx.play('click');
    navigator.clipboard.writeText(formatFriendCode(state.profile.id));
  });

  container.querySelector('#btnBackFriends')?.addEventListener('click', () => {
    sfx.play('click');
    store.goBack();
  });

  // Add friend dialog
  const dialog = container.querySelector('#addFriendDialog') as HTMLDialogElement;
  const codeInputEl = dialog.querySelector('#inputFriendCode') as HTMLInputElement | null;
  if (codeInputEl) {
    attachFriendCodeInputFormatter(codeInputEl);
  }

  container.querySelector('#btnAddFriendModal')?.addEventListener('click', () => {
    sfx.play('click');
    if (codeInputEl) codeInputEl.value = '';
    const errEl = dialog.querySelector('#friendCodeError') as HTMLElement | null;
    if (errEl) errEl.style.display = 'none';
    dialog.showModal();
  });

  dialog.querySelector('#btnCancelAddFriend')?.addEventListener('click', () => {
    sfx.play('click');
    dialog.close();
  });

  dialog.querySelector('#btnConfirmAddFriend')?.addEventListener('click', async () => {
    const input = (dialog.querySelector('#inputFriendCode') as HTMLInputElement).value;
    const errEl = dialog.querySelector('#friendCodeError') as HTMLElement;
    const res = await addFriendWithSync(input);
    if (!res.ok) {
      errEl.textContent = res.error || 'Invalid friend code format or checksum.';
      errEl.style.display = 'block';
      sfx.play('notify');
      return;
    }

    errEl.style.display = 'none';
    sfx.play('confirm');
    dialog.close();
    renderFriendsList();
  });

  fetchStatuses();
}
