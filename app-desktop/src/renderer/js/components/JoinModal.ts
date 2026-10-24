import { store } from '../state.js';
import { sfx } from '../sfx.js';
import { normalizeSessionCode } from '../../../main/network/sessionCodes.js';

let enteredCode = '';
let keydownListener: ((e: KeyboardEvent) => void) | null = null;

export function openJoinModal() {
  const dialog = document.getElementById('joinModal') as HTMLDialogElement;
  if (!dialog) return;

  enteredCode = '';
  renderJoinContent(dialog);
  dialog.showModal();

  // Attach global keyboard listener when dialog is open
  if (keydownListener) {
    window.removeEventListener('keydown', keydownListener);
  }
  keydownListener = (e: KeyboardEvent) => {
    if (!dialog.open) return;
    if (e.key === 'Escape') {
      closeJoinModal();
      return;
    }
    if (e.key === 'Backspace') {
      removeChar(dialog);
      return;
    }
    if (e.key === 'Enter' && enteredCode.length === 6) {
      submitJoin(dialog);
      return;
    }
    if (e.key.length === 1 && /[a-zA-Z0-9]/.test(e.key)) {
      addChar(dialog, e.key.toUpperCase());
    }
  };
  window.addEventListener('keydown', keydownListener);
}

export function closeJoinModal() {
  const dialog = document.getElementById('joinModal') as HTMLDialogElement;
  if (!dialog || !dialog.open) return;

  if (keydownListener) {
    window.removeEventListener('keydown', keydownListener);
    keydownListener = null;
  }

  dialog.setAttribute('data-closing', 'true');
  setTimeout(() => {
    dialog.close();
    dialog.removeAttribute('data-closing');
  }, 220);
}

function updateBoxes(dialog: HTMLDialogElement) {
  for (let i = 0; i < 6; i++) {
    const box = dialog.querySelector(`#joinBox-${i}`);
    if (box) {
      box.textContent = enteredCode[i] || '';
      box.classList.toggle('filled', !!enteredCode[i]);
    }
  }
  const submitBtn = dialog.querySelector('#btnSubmitJoinModal') as HTMLButtonElement;
  if (submitBtn) {
    submitBtn.disabled = enteredCode.length !== 6;
    submitBtn.style.opacity = enteredCode.length === 6 ? '1' : '0.5';
  }
}

function addChar(dialog: HTMLDialogElement, c: string) {
  if (enteredCode.length < 6) {
    enteredCode += c.toUpperCase();
    sfx.play('tick');
    updateBoxes(dialog);
    if (enteredCode.length === 6) {
      sfx.play('pop');
    }
  }
}

function removeChar(dialog: HTMLDialogElement) {
  if (enteredCode.length > 0) {
    enteredCode = enteredCode.slice(0, -1);
    sfx.play('tick');
    updateBoxes(dialog);
  }
}

async function submitJoin(dialog: HTMLDialogElement) {
  if (enteredCode.length !== 6) return;
  sfx.play('click');

  const submitBtn = dialog.querySelector('#btnSubmitJoinModal') as HTMLButtonElement;
  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.textContent = 'Connecting...';
  }

  const code = normalizeSessionCode(enteredCode);
  const profile = store.get().profile;
  const emuApi = (window as any).emufii;

  try {
    // 1. Fetch session from coordinator
    const remote = await emuApi.coordinator.getSession(code);

    // 2. Claim WireGuard peer address
    const config = await emuApi.config.get();
    const wgInfo = await emuApi.coordinator.claimAddress(
      code,
      config.wgKeys.publicKey,
      profile.name,
      profile.id
    );

    // 3. Up WireGuard tunnel (PS2 uses relay DNS for local hostname resolution)
    const selectedRom = store.get().selectedRom;
    const isPs2 = selectedRom?.console === 'PS2';
    const dns = isPs2 ? '10.67.0.1' : null;
    const wgRes = await emuApi.wireguard.up(wgInfo, dns);

    // 4. Set state and navigate to SessionScreen
    store.set({
      activeSession: {
        code,
        isHost: false,
        rom: selectedRom || null,
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
    closeJoinModal();
    store.navigate('session');
  } catch (err: any) {
    alert('Could not join session: ' + (err.message || 'Session not found'));
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Join Session';
    }
  }
}

function renderJoinContent(dialog: HTMLDialogElement) {
  const content = dialog.querySelector('.dialog-content') as HTMLElement;
  if (!content) return;

  content.innerHTML = `
    <div class="cloud-card" style="width: 540px;">
      <!-- Modal Header -->
      <div class="cloud-header">
        <div style="display: flex; align-items: center; gap: 10px;">
          <div class="cloud-icon-badge">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>
          </div>
          <div>
            <h2 class="cloud-title">Join a Session</h2>
            <p class="cloud-subtitle">Enter your friend's 6-character room code</p>
          </div>
        </div>
      </div>

      <!-- Modal Body -->
      <div class="cloud-body" style="display: flex; flex-direction: column; align-items: center; gap: 18px; padding: 24px 20px;">
        <!-- 6-character Code Boxes -->
        <div style="display: flex; gap: 10px; margin: 4px 0;" id="modalCodeBoxes">
          ${Array.from({ length: 6 })
            .map(
              (_, i) => `
            <div class="join-code-box plate" id="joinBox-${i}"></div>
          `
            )
            .join('')}
        </div>

        <!-- Quick Paste Button -->
        <button class="btn-secondary plate-button" id="btnPasteJoinCode" style="height: 30px; padding: 0 14px; font-size: 12px; display: flex; align-items: center; gap: 6px;">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1" ry="1"/></svg>
          Paste Code from Clipboard
        </button>

        <!-- Keypad Matching CodeKeyboard.kt -->
        <div class="code-keyboard-container" id="modalJoinKeypad" style="width: 100%; max-width: 480px;">
          <div class="code-keyboard-row">
            ${['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
          </div>
          <div class="code-keyboard-row">
            ${['K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
          </div>
          <div class="code-keyboard-row">
            ${['U', 'V', 'W', 'X', 'Y', 'Z'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
            <button class="code-key wide plate-button" id="btnModalKeypadBack" title="Backspace">&larr;</button>
            <button class="code-key wide plate-button" id="btnModalKeypadClear" style="color: var(--compat-broken);" title="Clear">Clear</button>
          </div>
          <div class="code-keyboard-row">
            ${['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
          </div>
        </div>

        <!-- Action Buttons -->
        <div style="display: flex; gap: 12px; width: 100%; max-width: 480px; margin-top: 6px;">
          <button class="btn-secondary plate-button" id="btnCancelJoinModal" style="flex: 1; height: 42px;">
            Cancel
          </button>
          <button class="btn-primary plate-button" id="btnSubmitJoinModal" style="flex: 2; height: 42px; opacity: 0.5;" disabled>
            Join Session
          </button>
        </div>
      </div>
    </div>
  `;

  dialog.querySelector('#btnCancelJoinModal')?.addEventListener('click', () => {
    sfx.play('click');
    closeJoinModal();
  });

  // Attach keypad buttons
  dialog.querySelectorAll('.code-key[data-char]').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const c = (e.currentTarget as HTMLElement).dataset.char;
      if (c) addChar(dialog, c);
    });
  });

  dialog.querySelector('#btnModalKeypadBack')?.addEventListener('click', () => {
    removeChar(dialog);
  });

  dialog.querySelector('#btnModalKeypadClear')?.addEventListener('click', () => {
    if (enteredCode.length > 0) {
      enteredCode = '';
      sfx.play('pop');
      updateBoxes(dialog);
    }
  });

  dialog.querySelector('#btnPasteJoinCode')?.addEventListener('click', async () => {
    try {
      const text = await navigator.clipboard.readText();
      const cleaned = text.trim().replace(/[^a-zA-Z0-9]/g, '').slice(0, 6).toUpperCase();
      if (cleaned) {
        enteredCode = cleaned;
        sfx.play('pop');
        updateBoxes(dialog);
      }
    } catch {}
  });

  dialog.querySelector('#btnSubmitJoinModal')?.addEventListener('click', () => {
    submitJoin(dialog);
  });

  updateBoxes(dialog);
}
