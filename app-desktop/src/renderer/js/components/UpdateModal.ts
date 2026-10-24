import { sfx } from '../sfx.js';

export interface UpdateInfo {
  currentVersion: string;
  latestVersion: string;
  updateAvailable: boolean;
  releaseUrl?: string;
  downloadUrl?: string;
  assetName?: string | null;
  notes?: string;
}

function escapeHtml(str: string): string {
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * Emufii-themed Update Dialog asking if the user wants to update to version x.x.x
 * with "Skip For Now" and "Update" buttons.
 */
export function openUpdateModal(info: UpdateInfo): Promise<'update' | 'skip'> {
  return new Promise((resolve) => {
    const emuApi = (window as any).emufii;
    const targetVersion = info.latestVersion || info.currentVersion || '2.0.4';
    const currentVersion = info.currentVersion || '2.0.4';
    const isNewer = info.updateAvailable;

    const existing = document.getElementById('emufiiUpdateModalOverlay');
    if (existing) existing.remove();

    const overlay = document.createElement('div');
    overlay.id = 'emufiiUpdateModalOverlay';
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
      animation: fadeIn 180ms ease-out;
    `;

    const cleanNotes = (info.notes || 'Includes the latest Emufii Desktop improvements, real-time friend status synchronization, and emulator auto-configuration updates.')
      .replace(/[#*`]/g, '')
      .trim()
      .slice(0, 280);

    overlay.innerHTML = `
      <div class="plate modal-window" role="dialog" aria-modal="true" aria-labelledby="updateModalTitle" style="width: 460px; max-width: calc(100vw - 32px); padding: 28px; border-radius: 28px; display: flex; flex-direction: column; align-items: center; text-align: center; gap: 18px; border: 1px solid var(--plate-border); box-shadow: 0 24px 52px rgba(0, 0, 0, 0.6);">
        <!-- Update Icon Badge -->
        <div style="width: 60px; height: 60px; border-radius: 20px; background: var(--accent-subtle); display: flex; align-items: center; justify-content: center; color: var(--accent-bright); border: 1.5px solid var(--plate-border); box-shadow: 0 0 24px rgba(63, 207, 192, 0.18);">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
        </div>

        <!-- Title & Version Pills -->
        <div style="display: flex; flex-direction: column; align-items: center; gap: 8px;">
          <h3 id="updateModalTitle" style="margin: 0; font-size: 20px; font-weight: 900; color: var(--ink-primary);">
            Update to version ${escapeHtml(targetVersion)}?
          </h3>
          <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: center;">
            <span class="badge" style="font-size: 11px; font-weight: 800; background: var(--plate-bg-low); color: var(--ink-secondary); padding: 4px 10px; border-radius: 9999px; border: 1px solid var(--plate-border);">
              Current: v${escapeHtml(currentVersion)}
            </span>
            <span style="color: var(--ink-secondary); font-size: 12px;">&rarr;</span>
            <span class="badge" style="font-size: 11px; font-weight: 800; background: rgba(16, 185, 129, 0.15); color: #10b981; padding: 4px 10px; border-radius: 9999px; border: 1px solid rgba(16, 185, 129, 0.35);">
              ${isNewer ? `New: v${escapeHtml(targetVersion)}` : `Latest: v${escapeHtml(targetVersion)} (Up to date)`}
            </span>
          </div>
        </div>

        <!-- Release Notes Box -->
        <div style="width: 100%; background: var(--plate-bg-low); border: 1px solid var(--plate-border); border-radius: 16px; padding: 12px 14px; text-align: left; font-size: 12.5px; color: var(--ink-secondary); line-height: 1.45; max-height: 110px; overflow-y: auto;">
          ${escapeHtml(cleanNotes)}
        </div>

        <!-- Download Progress Section (Hidden until Update is clicked) -->
        <div id="updateProgressSection" style="width: 100%; display: none; flex-direction: column; gap: 8px;">
          <div style="width: 100%; height: 8px; background: var(--plate-bg-low); border-radius: 9999px; overflow: hidden;">
            <div id="updateProgressBar" style="width: 5%; height: 100%; background: var(--accent-bright); border-radius: 9999px; transition: width 0.2s ease;"></div>
          </div>
          <div style="display: flex; justify-content: space-between; font-size: 11px; color: var(--ink-secondary);">
            <span id="updateProgressStatus">Preparing download...</span>
            <span id="updateProgressPercent">5%</span>
          </div>
        </div>

        <!-- Action Buttons: Skip For Now | Update -->
        <div id="updateActionButtons" style="display: flex; gap: 12px; width: 100%; margin-top: 2px;">
          <button class="btn-secondary plate-button" id="btnSkipUpdate" style="flex: 1; height: 42px; font-size: 13px; font-weight: 700; border-radius: 9999px;">
            Skip For Now
          </button>
          <button class="btn-primary plate-button" id="btnConfirmUpdate" style="flex: 1; height: 42px; font-size: 13px; font-weight: 800; border-radius: 9999px;">
            Update
          </button>
        </div>
      </div>
    `;

    document.body.appendChild(overlay);

    let unsubscribeProgress: (() => void) | null = null;
    let isUpdating = false;

    const cleanup = (choice: 'update' | 'skip') => {
      window.removeEventListener('keydown', onKeyDown, true);
      if (unsubscribeProgress) unsubscribeProgress();
      overlay.remove();
      resolve(choice);
    };

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isUpdating) {
        e.preventDefault();
        e.stopPropagation();
        sfx.play('click');
        cleanup('skip');
      }
    };
    window.addEventListener('keydown', onKeyDown, true);

    overlay.querySelector('#btnSkipUpdate')?.addEventListener('click', () => {
      if (isUpdating) return;
      sfx.play('click');
      cleanup('skip');
    });

    overlay.querySelector('#btnConfirmUpdate')?.addEventListener('click', async () => {
      if (isUpdating) return;
      isUpdating = true;
      sfx.play('confirm');

      const progSec = overlay.querySelector('#updateProgressSection') as HTMLElement;
      const barEl = overlay.querySelector('#updateProgressBar') as HTMLElement;
      const statusEl = overlay.querySelector('#updateProgressStatus') as HTMLElement;
      const pctEl = overlay.querySelector('#updateProgressPercent') as HTMLElement;
      const btnSkip = overlay.querySelector('#btnSkipUpdate') as HTMLButtonElement;
      const btnUpdate = overlay.querySelector('#btnConfirmUpdate') as HTMLButtonElement;

      if (progSec) progSec.style.display = 'flex';
      if (btnSkip) btnSkip.disabled = true;
      if (btnUpdate) {
        btnUpdate.disabled = true;
        btnUpdate.textContent = 'Updating...';
      }

      if (emuApi?.updates?.onProgress) {
        unsubscribeProgress = emuApi.updates.onProgress((data: { percent: number; status: string }) => {
          if (barEl) barEl.style.width = `${data.percent}%`;
          if (pctEl) pctEl.textContent = `${data.percent}%`;
          if (statusEl) statusEl.textContent = data.status;
        });
      }

      try {
        if (emuApi?.updates?.perform) {
          await emuApi.updates.perform({
            downloadUrl: info.downloadUrl,
            assetName: info.assetName,
            releaseUrl: info.releaseUrl,
          });
        } else if (info.releaseUrl) {
          window.open(info.releaseUrl, '_blank');
        }
        if (barEl) barEl.style.width = '100%';
        if (pctEl) pctEl.textContent = '100%';
        if (statusEl) statusEl.textContent = 'Update ready!';
        setTimeout(() => cleanup('update'), 700);
      } catch {
        cleanup('update');
      }
    });

    overlay.addEventListener('click', (e) => {
      if (e.target === overlay && !isUpdating) {
        sfx.play('click');
        cleanup('skip');
      }
    });

    setTimeout(() => {
      (overlay.querySelector('#btnConfirmUpdate') as HTMLButtonElement)?.focus();
    }, 30);
  });
}
