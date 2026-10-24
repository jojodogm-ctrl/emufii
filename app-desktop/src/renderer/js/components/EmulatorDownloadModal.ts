declare const window: any;

export async function ensureEmulator(consoleType: string): Promise<boolean> {
  const emuApi = window.emufii;
  if (!emuApi) return true;

  try {
    const all = await emuApi.emulators.detectAll();
    let emu = all[consoleType];

    // For DS games that might be GBA or vice-versa
    if (!emu && (consoleType === 'DS' || consoleType === 'GBA')) {
      emu = all['DS'] || all['GBA'];
    }

    if (emu && emu.isInstalled) {
      return true;
    }

    // Need to download
    return new Promise<boolean>((resolve) => {
      const emuName = emu?.name || consoleType;

      const overlay = document.createElement('div');
      overlay.className = 'modal-backdrop visible';
      overlay.style.zIndex = '9999';
      overlay.style.display = 'flex';
      overlay.style.alignItems = 'center';
      overlay.style.justifyContent = 'center';
      overlay.style.background = 'rgba(0, 0, 0, 0.65)';
      overlay.style.backdropFilter = 'blur(8px)';

      overlay.innerHTML = `
        <div class="plate modal-window" style="width: 440px; max-width: 90vw; padding: 28px; border-radius: 28px; display: flex; flex-direction: column; align-items: center; text-align: center; gap: 16px; border: 1px solid var(--plate-border); box-shadow: 0 20px 40px rgba(0,0,0,0.4);">
          <div style="width: 56px; height: 56px; border-radius: 18px; background: rgba(63, 207, 192, 0.15); display: flex; align-items: center; justify-content: center; color: var(--accent-bright);">
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
          </div>

          <div>
            <h3 style="margin: 0 0 6px 0; font-size: 18px; font-weight: 700; color: var(--ink-primary);">
              Auto-Configuring Emulator
            </h3>
            <p style="margin: 0; font-size: 13px; color: var(--ink-secondary); line-height: 1.4;">
              Downloading and setting up <strong>${emuName}</strong> for <strong>${consoleType}</strong> automatically...
            </p>
          </div>

          <!-- Progress Bar -->
          <div style="width: 100%; display: flex; flex-direction: column; gap: 8px;">
            <div style="width: 100%; height: 8px; background: var(--plate-bg-low); border-radius: 9999px; overflow: hidden; position: relative;">
              <div id="emuDlBar" style="width: 5%; height: 100%; background: var(--accent-bright); border-radius: 9999px; transition: width 0.2s cubic-bezier(0.4, 0, 0.2, 1);"></div>
            </div>
            <div style="display: flex; justify-content: space-between; font-size: 11px; color: var(--ink-secondary);">
              <span id="emuDlStatusText">Connecting...</span>
              <span id="emuDlPercentText">5%</span>
            </div>
          </div>
        </div>
      `;

      document.body.appendChild(overlay);

      const onKeyDown = (e: KeyboardEvent) => {
        if (e.key === 'Escape') {
          e.preventDefault();
          window.removeEventListener('keydown', onKeyDown);
          unsubscribe();
          overlay.remove();
          resolve(false);
        }
      };
      window.addEventListener('keydown', onKeyDown);

      const barEl = overlay.querySelector('#emuDlBar') as HTMLElement;
      const statusEl = overlay.querySelector('#emuDlStatusText') as HTMLElement;
      const percentEl = overlay.querySelector('#emuDlPercentText') as HTMLElement;

      const unsubscribe = emuApi.emulators.onDownloadProgress((data: any) => {
        if (data.consoleType === consoleType || (consoleType === 'Wii' && data.consoleType === 'GameCube')) {
          if (barEl) barEl.style.width = `${data.percent}%`;
          if (percentEl) percentEl.textContent = `${data.percent}%`;
          if (statusEl) statusEl.textContent = data.status || 'Downloading...';
        }
      });

      emuApi.emulators
        .download(consoleType)
        .then((res: any) => {
          window.removeEventListener('keydown', onKeyDown);
          unsubscribe();
          if (res && res.success) {
            if (barEl) barEl.style.width = '100%';
            if (percentEl) percentEl.textContent = '100%';
            if (statusEl) statusEl.textContent = 'Ready!';
            setTimeout(() => {
              overlay.remove();
              resolve(true);
            }, 600);
          } else {
            overlay.remove();
            alert(res?.message || `Failed to auto-download ${emuName}. Please install manually.`);
            resolve(false);
          }
        })
        .catch((err: any) => {
          window.removeEventListener('keydown', onKeyDown);
          unsubscribe();
          overlay.remove();
          alert(`Error downloading ${emuName}: ` + (err.message || err));
          resolve(false);
        });
    });
  } catch {
    return true;
  }
}
