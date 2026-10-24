import { sfx } from './sfx.js';
import { isFloatingPanelOpen, getFloatingPanelTab } from './components/FloatingPanel.js';

export interface FocusDelegate {
  onConfirm?: (element: HTMLElement) => void;
  onBack?: () => void;
  onMenu?: (element: HTMLElement) => void;
}

export class NavigationManager {
  private currentFocused: HTMLElement | null = null;
  private focusRing: HTMLElement | null = null;
  private delegate: FocusDelegate | null = null;

  private lastGamepadPoll = 0;
  private lastGamepadDirectionTime = 0;
  private prevButtonStates: boolean[] = [];

  constructor() {
    this.handleKeyDown = this.handleKeyDown.bind(this);
    this.pollGamepads = this.pollGamepads.bind(this);
  }

  init(delegate?: FocusDelegate) {
    this.delegate = delegate || null;

    window.addEventListener('keydown', this.handleKeyDown);
    window.addEventListener('focusin', (e) => {
      const target = e.target as HTMLElement;
      if (
        target &&
        target !== this.currentFocused &&
        target.matches(
          'button, [role="button"], input, .rom-cell, .rom-tile, .carousel-card, .list-row, .chip, .filter-tab, .icon-btn, .keypad-btn'
        )
      ) {
        this.setFocus(target, false);
      }
    });
    requestAnimationFrame(this.pollGamepads);
  }

  setDelegate(delegate: FocusDelegate) {
    this.delegate = delegate;
  }

  getCurrentFocus(): HTMLElement | null {
    return this.currentFocused;
  }

  setFocus(el: HTMLElement | null, playSound = true) {
    if (this.currentFocused) {
      this.currentFocused.classList.remove('focused');
      this.currentFocused.setAttribute('tabindex', '-1');
    }

    this.currentFocused = el;

    if (!el) return;

    el.classList.add('focused');
    el.setAttribute('tabindex', '0');
    el.focus({ preventScroll: true });
    el.scrollIntoView({ block: 'nearest', inline: 'nearest' });

    if (playSound) {
      sfx.play('hover');
    }
  }

  updateFocusRing(_el = this.currentFocused) {
    // Intrinsic focus ring is now applied via CSS on .focused and :focus-visible
  }

  /**
   * Spatial 2D direction navigation adhering to modern-web-guidance
   */
  navigate(direction: 'up' | 'down' | 'left' | 'right') {
    const candidates = Array.from(
      document.querySelectorAll<HTMLElement>(
        'button, [role="button"], input, .rom-tile, .carousel-card, .list-row, .chip, .filter-tab, .icon-btn, .keypad-btn'
      )
    ).filter((el) => {
      if (!el.checkVisibility()) return false;
      if (el.closest('dialog:not([open])')) return false;
      const openDialog = document.querySelector('dialog[open]');
      if (openDialog && !el.closest('dialog[open]')) return false;
      return true;
    });

    if (candidates.length === 0) return;

    if (!this.currentFocused || !candidates.includes(this.currentFocused)) {
      this.setFocus(candidates[0]);
      return;
    }

    const currentRect = this.currentFocused.getBoundingClientRect();
    let bestCandidate: HTMLElement | null = null;
    let minDistance = Infinity;

    for (const cand of candidates) {
      if (cand === this.currentFocused) continue;
      const candRect = cand.getBoundingClientRect();

      let inDirection = false;
      let gap = 0;
      let perpOffset = 0;

      switch (direction) {
        case 'right':
          inDirection = candRect.left >= currentRect.right - 4;
          gap = candRect.left - currentRect.right;
          perpOffset = Math.abs((candRect.top + candRect.height / 2) - (currentRect.top + currentRect.height / 2));
          break;
        case 'left':
          inDirection = candRect.right <= currentRect.left + 4;
          gap = currentRect.left - candRect.right;
          perpOffset = Math.abs((candRect.top + candRect.height / 2) - (currentRect.top + currentRect.height / 2));
          break;
        case 'down':
          inDirection = candRect.top >= currentRect.bottom - 4;
          gap = candRect.top - currentRect.bottom;
          perpOffset = Math.abs((candRect.left + candRect.width / 2) - (currentRect.left + currentRect.width / 2));
          break;
        case 'up':
          inDirection = candRect.bottom <= currentRect.top + 4;
          gap = currentRect.top - candRect.bottom;
          perpOffset = Math.abs((candRect.left + candRect.width / 2) - (currentRect.left + currentRect.width / 2));
          break;
      }

      if (inDirection) {
        // Weighted scoring as recommended by guidance: gap + 2 * perpOffset
        const score = gap + 2.5 * perpOffset;
        if (score < minDistance) {
          minDistance = score;
          bestCandidate = cand;
        }
      }
    }

    if (bestCandidate) {
      this.setFocus(bestCandidate);
    }
  }

  private handleKeyDown(e: KeyboardEvent) {
    if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
      // Don't intercept if inside text input unless navigating away
      const target = e.target as HTMLElement;
      if (target && target.tagName === 'INPUT' && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
        return;
      }

      e.preventDefault();
      switch (e.key) {
        case 'ArrowUp': this.navigate('up'); break;
        case 'ArrowDown': this.navigate('down'); break;
        case 'ArrowLeft': this.navigate('left'); break;
        case 'ArrowRight': this.navigate('right'); break;
      }
      return;
    }

    if (e.key === 'Enter' || e.key === ' ') {
      if (this.currentFocused) {
        e.preventDefault();
        sfx.play('click');
        this.currentFocused.click();
        this.delegate?.onConfirm?.(this.currentFocused);
      }
      return;
    }

    if (e.key === 'Escape' || e.key === 'Backspace') {
      const activeEl = document.activeElement;
      if (activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA')) return;
      if (isFloatingPanelOpen() && getFloatingPanelTab() === 'join' && e.key === 'Backspace') {
        return;
      }
      this.delegate?.onBack?.();
      return;
    }
  }

  private pollGamepads(timestamp: number) {
    const gamepads = navigator.getGamepads ? navigator.getGamepads() : [];
    let pad: Gamepad | null = null;
    for (const g of gamepads) {
      if (g && g.connected) {
        pad = g;
        break;
      }
    }

    if (pad) {
      // Directional inputs (D-Pad: buttons 12-15 or axes 0-1)
      const dpadUp = pad.buttons[12]?.pressed || pad.axes[1] < -0.5;
      const dpadDown = pad.buttons[13]?.pressed || pad.axes[1] > 0.5;
      const dpadLeft = pad.buttons[14]?.pressed || pad.axes[0] < -0.5;
      const dpadRight = pad.buttons[15]?.pressed || pad.axes[0] > 0.5;

      const now = performance.now();
      if (now - this.lastGamepadDirectionTime > 180) {
        if (dpadUp) { this.navigate('up'); this.lastGamepadDirectionTime = now; }
        else if (dpadDown) { this.navigate('down'); this.lastGamepadDirectionTime = now; }
        else if (dpadLeft) { this.navigate('left'); this.lastGamepadDirectionTime = now; }
        else if (dpadRight) { this.navigate('right'); this.lastGamepadDirectionTime = now; }
      }

      // Buttons
      const btnA = pad.buttons[0]?.pressed; // Confirm (A / Cross)
      const btnB = pad.buttons[1]?.pressed; // Back (B / Circle)
      const btnY = pad.buttons[3]?.pressed; // Menu (Y / Triangle)

      if (btnA && !this.prevButtonStates[0]) {
        if (this.currentFocused) {
          sfx.play('click');
          this.currentFocused.click();
          this.delegate?.onConfirm?.(this.currentFocused);
        }
      }
      if (btnB && !this.prevButtonStates[1]) {
        this.delegate?.onBack?.();
      }
      if (btnY && !this.prevButtonStates[3]) {
        if (this.currentFocused) {
          this.delegate?.onMenu?.(this.currentFocused);
        }
      }

      this.prevButtonStates = [btnA, btnB, pad.buttons[2]?.pressed, btnY];
    }

    requestAnimationFrame(this.pollGamepads);
  }
}

export const nav = new NavigationManager();
