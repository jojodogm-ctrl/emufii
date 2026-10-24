import { store } from '../state.js';
import { sfx } from '../sfx.js';
import { normalizeSessionCode } from '../../../main/network/sessionCodes.js';

export function renderJoinScreen(container: HTMLElement) {
  let enteredCode = '';
  const emuApi = (window as any).emufii;

  container.innerHTML = `
    <div style="flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 32px; gap: 24px;">
      <div style="text-align: center;">
        <h1 style="font-size: 28px; font-weight: 900; margin-bottom: 8px;">Join a Session</h1>
        <p style="font-size: 14px; color: var(--ink-secondary);">Enter your friend's six-character code</p>
      </div>

      <!-- 6-character Code Boxes -->
      <div style="display: flex; gap: 12px; margin: 12px 0;" id="codeBoxes">
        ${Array.from({ length: 6 })
          .map(
            (_, i) => `
          <div class="plate" style="
            width: 56px;
            height: 68px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 32px;
            font-weight: 900;
            color: var(--accent-bright);
            font-family: var(--font-family);
          " id="box-${i}"></div>
        `
          )
          .join('')}
      </div>

      <!-- Keypad Matching CodeKeyboard.kt -->
      <div class="code-keyboard-container" id="joinKeypad">
        <div class="code-keyboard-row">
          ${['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
        </div>
        <div class="code-keyboard-row">
          ${['K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
        </div>
        <div class="code-keyboard-row">
          ${['U', 'V', 'W', 'X', 'Y', 'Z'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
          <button class="code-key wide plate-button" id="btnKeypadBack" title="Backspace">&larr;</button>
          <button class="code-key wide plate-button" id="btnKeypadClear" style="color: var(--compat-broken);" title="Clear">Clear</button>
        </div>
        <div class="code-keyboard-row">
          ${['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'].map((c) => `<button class="code-key plate-button" data-char="${c}">${c}</button>`).join('')}
        </div>
      </div>

      <!-- Action Buttons -->
      <div style="display: flex; gap: 16px; margin-top: 12px;">
        <button class="btn-secondary plate-button" id="btnCancelJoin">
          Back
        </button>
        <button class="btn-primary plate-button" id="btnSubmitJoin" style="padding: 0 36px; width: auto;" disabled>
          Join Session
        </button>
      </div>
    </div>
  `;

  const updateBoxes = () => {
    for (let i = 0; i < 6; i++) {
      const box = container.querySelector(`#box-${i}`);
      if (box) {
        box.textContent = enteredCode[i] || '';
      }
    }
    const submitBtn = container.querySelector('#btnSubmitJoin') as HTMLButtonElement;
    if (submitBtn) {
      submitBtn.disabled = enteredCode.length !== 6;
      submitBtn.style.opacity = enteredCode.length === 6 ? '1' : '0.5';
    }
  };

  const addChar = (c: string) => {
    if (enteredCode.length < 6) {
      enteredCode += c.toUpperCase();
      sfx.play('tick');
      updateBoxes();
      if (enteredCode.length === 6) {
        sfx.play('pop');
      }
    }
  };

  const removeChar = () => {
    if (enteredCode.length > 0) {
      enteredCode = enteredCode.slice(0, -1);
      sfx.play('tick');
      updateBoxes();
    }
  };

  container.querySelectorAll('.code-key[data-char]').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const char = (e.currentTarget as HTMLElement).dataset.char;
      if (char) addChar(char);
    });
  });

  container.querySelector('#btnKeypadBack')?.addEventListener('click', removeChar);
  container.querySelector('#btnKeypadClear')?.addEventListener('click', () => {
    enteredCode = '';
    sfx.play('tick');
    updateBoxes();
  });

  // Physical Keyboard input listener
  const onKeyDown = (e: KeyboardEvent) => {
    if (store.get().currentScreen !== 'join') return;
    if (e.key === 'Backspace') {
      removeChar();
    } else if (e.key.length === 1 && /[2-9A-Za-z0-1]/.test(e.key)) {
      addChar(e.key);
    } else if (e.key === 'Enter' && enteredCode.length === 6) {
      container.querySelector('#btnSubmitJoin')?.dispatchEvent(new MouseEvent('click'));
    }
  };
  window.addEventListener('keydown', onKeyDown);

  container.querySelector('#btnCancelJoin')?.addEventListener('click', () => {
    sfx.play('click');
    window.removeEventListener('keydown', onKeyDown);
    store.goBack();
  });

  container.querySelector('#btnSubmitJoin')?.addEventListener('click', async () => {
    if (enteredCode.length !== 6) return;
    sfx.play('click');
    window.removeEventListener('keydown', onKeyDown);

    const code = normalizeSessionCode(enteredCode);
    const profile = store.get().profile;
    const selectedRom = store.get().selectedRom;
    const isPs2 = selectedRom?.console === 'PS2';
    const dns = isPs2 ? '10.67.0.1' : null;

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
      store.navigate('session');
    } catch (err: any) {
      alert('Could not join session: ' + (err.message || 'Session not found'));
    }
  });

  updateBoxes();

  return () => {
    window.removeEventListener('keydown', onKeyDown);
  };
}
