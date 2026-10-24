import { store } from '../state.js';
import { sfx } from '../sfx.js';

export function openProfileModal() {
  const dialog = document.getElementById('profileModal') as HTMLDialogElement;
  if (!dialog) return;

  renderProfileContent(dialog);
  dialog.showModal();
}

export function closeProfileModal() {
  const dialog = document.getElementById('profileModal') as HTMLDialogElement;
  if (!dialog || !dialog.open) return;

  dialog.setAttribute('data-closing', 'true');
  setTimeout(() => {
    dialog.close();
    dialog.removeAttribute('data-closing');
  }, 220);
}

function renderProfileContent(dialog: HTMLDialogElement) {
  const state = store.get();
  const profile = state.profile;
  const emuApi = (window as any).emufii;

  const content = dialog.querySelector('.dialog-content') as HTMLElement;
  if (!content) return;

  content.innerHTML = `
    <div class="cloud-card">
      <!-- Modal Header -->
      <div class="cloud-header">
        <div style="display: flex; align-items: center; gap: 10px;">
          <div class="cloud-icon-badge">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"/></svg>
          </div>
          <div>
            <h2 class="cloud-title">Player Profile</h2>
            <p class="cloud-subtitle">Your identity and friend code across sessions</p>
          </div>
        </div>
      </div>

      <!-- Modal Body -->
      <div class="cloud-body">
        <!-- Avatar Section -->
        <div class="profile-avatar-row">
          <div class="profile-avatar-large">
            ${
              profile.avatarDataUrl
                ? `<img src="${profile.avatarDataUrl}" alt="${profile.name}" style="width: 100%; height: 100%; object-fit: cover; border-radius: 50%;">`
                : `<span style="font-size: 32px; font-weight: 800; color: var(--accent-ink);">${(profile.name || 'J').slice(0, 2).toUpperCase()}</span>`
            }
          </div>
          <div style="flex: 1; display: flex; flex-direction: column; gap: 8px;">
            <div style="font-weight: 700; font-size: 15px;">Profile Picture</div>
            <p style="font-size: 12px; color: var(--ink-secondary); line-height: 1.4;">
              Shown to your friends in lobbies and multiplayer rooms.
            </p>
            <div style="display: flex; gap: 8px;">
              <button class="btn-secondary plate-button" id="btnChangeAvatar" style="width: auto; height: 32px; padding: 0 14px; font-size: 12px;">
                Change Photo
              </button>
              ${
                profile.avatarDataUrl
                  ? `<button class="btn-secondary plate-button" id="btnRemoveAvatar" style="width: auto; height: 32px; padding: 0 14px; font-size: 12px; color: var(--compat-broken);">Remove</button>`
                  : ''
              }
            </div>
          </div>
        </div>

        <!-- Nickname Edit -->
        <div class="profile-field-block">
          <label style="font-size: 12px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; color: var(--ink-secondary);">Nickname</label>
          <div style="display: flex; gap: 8px; margin-top: 6px;">
            <input type="text" id="profileNicknameInput" value="${profile.name || 'Joueur'}" maxlength="24" class="cloud-input" placeholder="Enter your player name">
            <button class="btn-primary plate-button" id="btnSaveNickname" style="width: auto; height: 38px; padding: 0 16px; font-size: 12px;">Save</button>
          </div>
        </div>

        <!-- Friend Code Card -->
        <div class="friend-code-plate">
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <div style="font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; color: var(--ink-secondary);">Your Friend Code</div>
              <div class="friend-code-display" id="profileFriendCodeDisplay">${formatCode(profile.id)}</div>
            </div>
            <button class="btn-secondary plate-button" id="btnCopyFriendCode" style="width: auto; height: 34px; padding: 0 16px; font-size: 12px;">
              Copy Code
            </button>
          </div>
          <div style="font-size: 11px; color: var(--ink-secondary); margin-top: 8px; line-height: 1.4;">
            Share this 12-character code so your friends can invite you to wireless and internet games.
          </div>
        </div>

        <!-- Presence Activity: Share Last Game -->
        <div style="background: var(--plate-bg-low); border: 1px solid var(--plate-border); border-radius: var(--radius-card); padding: 12px 16px; margin-top: 10px; display: flex; align-items: center; justify-content: space-between;">
          <div>
            <div style="font-size: 13px; font-weight: 700; color: var(--ink-primary);">Share Last Game</div>
            <div style="font-size: 11px; color: var(--ink-secondary); margin-top: 2px;">Broadcast current game to friends</div>
          </div>
          <label class="clean-switch">
            <input type="checkbox" id="chkModalShareLastGame" ${state.settings.shareLastGame !== false ? 'checked' : ''}>
            <span class="slider"></span>
          </label>
        </div>

        <!-- Status Card -->
        <div class="profile-status-bar">
          <span class="lamp active" style="box-shadow: 0 0 10px var(--compat-perfect);"></span>
          <span style="font-size: 12px; font-weight: 700;">Identity Verified &bull; WireGuard Key Active</span>
        </div>
      </div>
    </div>
  `;

  // Change avatar handler
  content.querySelector('#btnChangeAvatar')?.addEventListener('click', async () => {
    sfx.play('click');
    const avatarData = await emuApi.dialog.selectAvatar();
    if (avatarData) {
      await emuApi.config.updateProfile({ avatarDataUrl: avatarData });
      store.set({ profile: { ...store.get().profile, avatarDataUrl: avatarData } });
      const base64 = avatarData.includes(',') ? avatarData.split(',')[1] : avatarData;
      const pid = store.get().profile.id;
      if (pid) {
        emuApi.coordinator.uploadAvatar(pid, pid, base64).catch(() => {});
      }
      renderProfileContent(dialog);
    }
  });

  // Remove avatar handler
  content.querySelector('#btnRemoveAvatar')?.addEventListener('click', async () => {
    sfx.play('click');
    await emuApi.config.updateProfile({ avatarDataUrl: null });
    const pid = store.get().profile.id;
    if (pid) {
      emuApi.coordinator.deleteAvatar(pid, pid).catch(() => {});
    }
    store.set({ profile: { ...store.get().profile, avatarDataUrl: null } });
    renderProfileContent(dialog);
  });

  // Save nickname handler
  const saveNickname = async () => {
    const input = content.querySelector('#profileNicknameInput') as HTMLInputElement;
    const newName = input?.value.trim() || 'Joueur';
    sfx.play('click');
    await emuApi.config.updateProfile({ name: newName });
    store.set({ profile: { ...store.get().profile, name: newName } });
    const btn = content.querySelector('#btnSaveNickname') as HTMLButtonElement;
    if (btn) {
      btn.textContent = 'Saved!';
      setTimeout(() => {
        if (btn) btn.textContent = 'Save';
      }, 1500);
    }
  };

  content.querySelector('#btnSaveNickname')?.addEventListener('click', saveNickname);
  content.querySelector('#profileNicknameInput')?.addEventListener('keydown', (e) => {
    if ((e as KeyboardEvent).key === 'Enter') {
      saveNickname();
    }
  });

  // Copy Friend Code handler
  content.querySelector('#btnCopyFriendCode')?.addEventListener('click', () => {
    sfx.play('click');
    navigator.clipboard.writeText(profile.id);
    const btn = content.querySelector('#btnCopyFriendCode') as HTMLButtonElement;
    if (btn) {
      btn.textContent = 'Copied!';
      setTimeout(() => {
        if (btn) btn.textContent = 'Copy Code';
      }, 1800);
    }
  });

  content.querySelector('#chkModalShareLastGame')?.addEventListener('change', async (e) => {
    const checked = (e.target as HTMLInputElement).checked;
    await emuApi.config.updateSettings({ shareLastGame: checked });
    store.set({ settings: { ...store.get().settings, shareLastGame: checked } });
    sfx.play('toggle');
  });

  // Backdrop click to close
  dialog.onclick = (e) => {
    if (e.target === dialog) {
      sfx.play('click');
      closeProfileModal();
    }
  };
}

function formatCode(id: string): string {
  if (!id) return '----';
  const clean = id.replace(/-/g, '');
  if (clean.length === 12) {
    return `${clean.slice(0, 4)}-${clean.slice(4, 8)}-${clean.slice(8, 12)}`;
  }
  return id;
}
