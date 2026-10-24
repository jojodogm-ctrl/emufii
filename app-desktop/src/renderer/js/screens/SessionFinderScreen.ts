import { store } from '../state.js';
import { sfx } from '../sfx.js';
import type { OpenSession } from '../../../main/network/coordinator.js';

export function renderSessionFinderScreen(container: HTMLElement) {
  const emuApi = (window as any).emufii;
  let sessions: OpenSession[] = [];
  let loading = true;
  let fetchError: string | null = null;

  container.innerHTML = `
    <div style="flex: 1; display: flex; flex-direction: column; padding: 32px; gap: 20px; overflow-y: auto;">
      <div style="display: flex; align-items: center; justify-content: space-between;">
        <div>
          <h1 style="font-size: 26px; font-weight: 900; margin-bottom: 4px;">Public Sessions</h1>
          <p style="font-size: 14px; color: var(--ink-secondary);">Join an open multiplayer room without needing a friend code</p>
        </div>
        <div style="display: flex; gap: 12px;">
          <button class="icon-btn plate-button" id="btnRefreshFinder" title="Refresh">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
          </button>
          <button class="btn-secondary plate-button" id="btnBackFinder">
            Back
          </button>
        </div>
      </div>

      <div id="finderListContainer" style="display: flex; flex-direction: column; gap: 12px; margin-top: 12px;">
        <div style="display: flex; justify-content: center; padding: 48px;">
          <div class="spinner"></div>
        </div>
      </div>
    </div>
  `;

  const fetchSessions = async () => {
    loading = true;
    fetchError = null;
    const listEl = container.querySelector('#finderListContainer');
    if (listEl) {
      listEl.innerHTML = `
        <div style="display: flex; justify-content: center; padding: 48px;">
          <div class="spinner"></div>
        </div>
      `;
    }
    try {
      sessions = await emuApi.coordinator.listSessions();
    } catch (err: any) {
      sessions = [];
      fetchError = err?.message || 'Unable to connect to coordinator';
    }
    loading = false;
    renderList();
  };

  const renderList = () => {
    const listEl = container.querySelector('#finderListContainer');
    if (!listEl) return;

    if (fetchError) {
      listEl.innerHTML = `
        <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 240px; gap: 14px; text-align: center;">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--compat-broken)" stroke-width="1.8"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <div>
            <div style="font-weight: 800; font-size: 16px; margin-bottom: 4px;">Connection Error</div>
            <div style="font-size: 13px; color: var(--ink-secondary); max-width: 400px;">${fetchError}</div>
          </div>
          <button class="btn-primary plate-button" id="btnRetryFinder" style="height: 36px; padding: 0 20px; font-size: 12px; border-radius: var(--radius-pill); cursor: pointer;">
            Retry Connection
          </button>
        </div>
      `;
      listEl.querySelector('#btnRetryFinder')?.addEventListener('click', () => {
        sfx.play('click');
        fetchSessions();
      });
      return;
    }

    if (sessions.length === 0) {
      listEl.innerHTML = `
        <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 240px; gap: 12px; text-align: center;">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="opacity: 0.35;"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
          <div style="font-weight: 700; font-size: 16px;">No Public Sessions Active</div>
          <div style="font-size: 13px; color: var(--ink-secondary);">Create a session or ask a friend for their six-character code.</div>
        </div>
      `;
      return;
    }

    listEl.innerHTML = sessions
      .map(
        (s) => `
      <div class="plate plate-button" style="display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; cursor: pointer;" data-code="${s.code}">
        <div style="display: flex; align-items: center; gap: 16px; min-width: 0; flex: 1 1 auto; overflow: hidden; margin-right: 16px;">
          <span style="font-family: var(--font-family); letter-spacing: 1px; font-size: 18px; font-weight: 900; color: var(--accent-bright); flex-shrink: 0;">${s.code}</span>
          <div style="min-width: 0; flex: 1 1 auto; overflow: hidden;">
            <div style="font-weight: 800; font-size: 15px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${s.romTitle || 'Custom Session'}</div>
            <div style="font-size: 12px; color: var(--ink-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">Hosted by ${s.hostName || 'Player'} · ${s.players} player(s) · ${Math.floor(s.ageSeconds / 60)}m ago</div>
          </div>
        </div>
        <button class="btn-primary plate-button" style="width: 58px; min-width: 58px; max-width: 58px; height: 28px; padding: 0; font-size: 11px; font-weight: 800; border-radius: 9999px; flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center;" data-code="${s.code}">
          Join
        </button>
      </div>
    `
      )
      .join('');

    listEl.querySelectorAll('[data-code]').forEach((el) => {
      el.addEventListener('click', (e) => {
        const code = (e.currentTarget as HTMLElement).dataset.code;
        if (code) {
          sfx.play('click');
          joinPublicSession(code);
        }
      });
    });
  };

  const joinPublicSession = async (code: string) => {
    const profile = store.get().profile;
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
        profile.name,
        profile.id
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

      sfx.play('confirm');
      store.navigate('session');
    } catch (err: any) {
      alert('Failed to join public session: ' + (err.message || err));
    }
  };

  container.querySelector('#btnRefreshFinder')?.addEventListener('click', () => {
    sfx.play('click');
    fetchSessions();
  });

  container.querySelector('#btnBackFinder')?.addEventListener('click', () => {
    sfx.play('click');
    store.goBack();
  });

  fetchSessions();
}
