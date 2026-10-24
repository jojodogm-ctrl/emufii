import { store } from '../state.js';
import { sfx } from '../sfx.js';

export function renderSplashScreen(container: HTMLElement) {
  container.innerHTML = `
    <div class="splash-view" style="
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
      gap: 32px;
      text-align: center;
      cursor: pointer;
      user-select: none;
    ">
      <img src="./assets/logo.png" alt="Emufii" style="
        width: 168px;
        height: 168px;
        object-fit: contain;
        animation: logo-pulse 2.2s infinite ease-in-out;
      ">
      <!-- Signal Pips Loading Animation matching SplashScreen.kt -->
      <div class="signal-pips" aria-label="Loading...">
        <span class="signal-pip"></span>
        <span class="signal-pip"></span>
        <span class="signal-pip"></span>
        <span class="signal-pip"></span>
        <span class="signal-pip"></span>
      </div>
    </div>
  `;

  let hasAdvanced = false;
  const advance = () => {
    if (hasAdvanced) return;
    hasAdvanced = true;
    sfx.play('confirm');
    store.navigate('library');
  };

  // Auto-advance after loading animation completes (matching MIN_MS in app-android SplashScreen.kt)
  const autoTimer = setTimeout(() => {
    advance();
  }, 2600);

  // Clicking anywhere or pressing Enter/Space can also proceed
  container.querySelector('.splash-view')?.addEventListener('click', advance);

  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      advance();
    }
  };
  window.addEventListener('keydown', onKey);

  return () => {
    clearTimeout(autoTimer);
    window.removeEventListener('keydown', onKey);
  };
}
