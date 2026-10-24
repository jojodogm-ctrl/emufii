import { store } from '../state.js';
import { sfx } from '../sfx.js';
import { ensureEmulator } from '../components/EmulatorDownloadModal.js';
import {
  resolveFriendDisplayName,
  resolveFriendAvatar,
  resolveFriendActivity,
} from '../components/FriendActionModal.js';
import { toggleFloatingPanel } from '../components/FloatingPanel.js';
import { formatFriendCode } from '../../../main/profile/friendCode.js';
import type { RemoteSession } from '../../../main/network/coordinator.js';

const invitedInSession = new Set<string>();

function openInviteFriendsModal(sessionCode: string) {
  const emuApi = (window as any).emufii;
  const state = store.get();
  const friends = state.settings.friends || [];

  const overlay = document.createElement('div');
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
    animation: fadeIn 160ms ease-out;
  `;

  overlay.innerHTML = `
    <div class="plate modal-window" role="dialog" aria-modal="true" aria-labelledby="inviteFriendsModalTitle" style="width: 440px; max-width: calc(100vw - 32px); padding: 24px; border-radius: 24px; display: flex; flex-direction: column; gap: 14px; border: 1px solid var(--plate-border); box-shadow: 0 24px 48px rgba(0, 0, 0, 0.55);">
      <div style="display: flex; align-items: center; justify-content: space-between;">
        <div>
          <h3 id="inviteFriendsModalTitle" style="margin: 0 0 2px 0; font-size: 18px; font-weight: 800; color: var(--ink-primary);">
            Invite Friends to Room ${sessionCode}
          </h3>
          <p style="margin: 0; font-size: 12px; color: var(--ink-secondary);">
            Send a direct room invite to your friends
          </p>
        </div>
        <button class="cloud-close-btn plate-button" id="btnCloseInviteModal" title="Close">&times;</button>
      </div>

      <div style="display: flex; flex-direction: column; gap: 8px; max-height: 280px; overflow-y: auto;">
        ${
          friends.length === 0
            ? `
          <div style="padding: 24px 12px; text-align: center; color: var(--ink-secondary); font-size: 13px;">
            You have not added any friends yet.
          </div>
        `
            : friends
                .map((code) => {
                  const name = resolveFriendDisplayName(code);
                  const avatar = resolveFriendAvatar(code);
                  const activity = resolveFriendActivity(code);
                  const inviteKey = `${sessionCode}:${code}`;
                  const alreadySent = invitedInSession.has(inviteKey);

                  return `
            <div class="plate" style="display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; border-radius: var(--radius-sm);">
              <div style="display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1;">
                <div style="position: relative; flex-shrink: 0;">
                  <div style="width: 34px; height: 34px; border-radius: 50%; background: var(--bg-shelf); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 12px; color: var(--accent-bright); border: 1.5px solid var(--plate-border); overflow: hidden;">
                    ${
                      avatar
                        ? `<img src="${avatar}" alt="${name}" style="width: 100%; height: 100%; object-fit: cover;">`
                        : name.slice(0, 2).toUpperCase()
                    }
                  </div>
                  <div style="position: absolute; bottom: -1px; right: -1px; width: 9px; height: 9px; border-radius: 50%; background: ${
                    activity.isOnline ? 'var(--compat-perfect)' : 'var(--ink-tertiary)'
                  }; border: 1.5px solid var(--plate-bg);"></div>
                </div>
                <div style="min-width: 0; flex: 1;">
                  <div style="font-weight: 700; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${name}</div>
                  <div style="font-size: 11px; color: var(--ink-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                    ${activity.statusText || formatFriendCode(code)}
                  </div>
                </div>
              </div>
              <button class="${alreadySent ? 'btn-secondary' : 'btn-primary'} plate-button btn-send-session-invite" data-code="${code}" ${alreadySent ? 'disabled' : ''} style="height: 28px; padding: 0 12px; font-size: 11px; width: auto; border-radius: 9999px;">
                ${alreadySent ? '✓ Sent' : 'Invite'}
              </button>
            </div>
          `;
                })
                .join('')
        }
      </div>

      <div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 4px;">
        ${
          friends.length === 0
            ? `<button class="btn-primary plate-button" id="btnOpenFriendsFromInvite" style="height: 36px; padding: 0 16px; font-size: 12px; border-radius: 9999px; width: auto;">Add Friends</button>`
            : ''
        }
        <button class="btn-secondary plate-button" id="btnDoneInviteModal" style="height: 36px; padding: 0 18px; font-size: 12px; border-radius: 9999px;">
          Done
        </button>
      </div>
    </div>
  `;

  document.body.appendChild(overlay);

  const cleanup = () => {
    window.removeEventListener('keydown', onKeyDown, true);
    overlay.remove();
  };

  const onKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      sfx.play('click');
      cleanup();
    }
  };
  window.addEventListener('keydown', onKeyDown, true);

  overlay.querySelector('#btnCloseInviteModal')?.addEventListener('click', () => {
    sfx.play('click');
    cleanup();
  });
  overlay.querySelector('#btnDoneInviteModal')?.addEventListener('click', () => {
    sfx.play('click');
    cleanup();
  });
  overlay.querySelector('#btnOpenFriendsFromInvite')?.addEventListener('click', () => {
    sfx.play('click');
    cleanup();
    toggleFloatingPanel('friends');
  });
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) {
      sfx.play('click');
      cleanup();
    }
  });

  overlay.querySelectorAll('.btn-send-session-invite').forEach((btn) => {
    btn.addEventListener('click', async (e) => {
      const button = e.currentTarget as HTMLButtonElement;
      const toCode = button.dataset.code;
      if (!toCode) return;

      button.disabled = true;
      button.textContent = 'Sending...';
      sfx.play('confirm');

      const ok = await emuApi.coordinator?.sendSessionInvite?.(
        state.profile.id,
        toCode,
        sessionCode,
        state.profile.name || 'Joueur'
      );

      if (ok !== false) {
        invitedInSession.add(`${sessionCode}:${toCode}`);
        button.textContent = '✓ Sent';
        button.className = 'btn-secondary plate-button btn-send-session-invite';
      } else {
        button.disabled = false;
        button.textContent = 'Retry';
      }
    });
  });
}

export function renderSessionScreen(container: HTMLElement) {
  const emuApi = (window as any).emufii;
  let pollTimer: any = null;
  let currentPingMs: number | null = null;

  const update = () => {
    const session = store.get().activeSession;
    if (!session) {
      container.innerHTML = `<div style="padding: 32px;">No active session.</div>`;
      return;
    }

    const isHost = session.isHost;
    const isOnline = session.code === 'ONLINE';
    const remote = session.remote;
    const wg = session.wireguard;
    const isPsp = session.rom?.console === 'PSP';
    const isDolphin = session.rom?.console === 'GameCube' || session.rom?.console === 'Wii';
    const isPs2 = session.rom?.console === 'PS2';

    // PPSSPP ad hoc server uses fixed sentinel address 10.66.1.1 (matches Session.kt shownAddress)
    const displayIp = isPsp
      ? '10.66.1.1'
      : isHost
      ? (wg?.address || '10.67.0.2')
      : (remote?.hostIp || '10.67.0.2');

    // Default netplay port matching Console.kt backend.defaultNetplayPort:
    // Dolphin: 2626, PS2: 19072, MelonDS: 7000, 3DS/Switch: 24872
    const defaultPort = isDolphin ? 2626 : isPs2 ? 19072 : session.rom?.console === 'DS' ? 7000 : 24872;
    const displayPort = remote?.port || defaultPort;

    container.innerHTML = `
      <div class="session-screen">
        <!-- Left Panel: State -->
        <div class="session-left">
          <div class="code-display-card" style="border-radius: 24px;">
            <div style="font-size: 13px; font-weight: 700; color: var(--ink-secondary); text-transform: uppercase; letter-spacing: 1px;">
              ${isOnline ? 'Online Play' : 'Session Code'}
            </div>
            <div class="code-value" style="${isOnline ? 'font-size: 32px; letter-spacing: 2px;' : ''}">${session.code}</div>
            ${!isOnline ? `
            <div style="display: flex; gap: 8px; justify-content: center; flex-wrap: wrap;">
              <button class="btn-secondary plate-button" id="btnCopyCode" style="height: 36px; padding: 0 16px; font-size: 12px; border-radius: 9999px;">
                Copy Code
              </button>
              <button class="btn-primary plate-button" id="btnInviteFriendsSession" style="height: 36px; padding: 0 16px; font-size: 12px; border-radius: 9999px; width: auto;">
                Invite Friends
              </button>
            </div>
            ` : `
            <div style="font-size: 12px; color: var(--ink-secondary);">Direct connection to multiplayer servers</div>
            `}
          </div>

          <!-- Connection Details Card -->
          <div class="plate" style="padding: 20px; display: flex; flex-direction: column; gap: 12px; border-radius: 24px;">
            <div style="font-size: 13px; font-weight: 700; color: var(--ink-secondary);">${isOnline ? 'Network Mode' : 'Tunnel & Address'}</div>
            <div style="display: flex; justify-content: space-between; align-items: center; background: var(--plate-bg-low); padding: 10px 14px; border-radius: 18px; font-family: var(--font-family); font-size: 14px;">
              <span>${isOnline ? 'Service: <strong>Public Online Ready</strong>' : `Address: <strong>${displayIp}</strong>`}</span>
              ${isOnline ? '<span class="lamp active"></span>' : '<button class="btn-secondary plate-button" style="height: 28px; padding: 0 12px; font-size: 11px; border-radius: 9999px;" id="btnCopyIp">Copy</button>'}
            </div>
            ${!isOnline && !isPsp ? `
            <div style="display: flex; justify-content: space-between; align-items: center; background: var(--plate-bg-low); padding: 10px 14px; border-radius: 18px; font-family: var(--font-family); font-size: 14px;">
              <span>Port: <strong>${displayPort}</strong></span>
              <button class="btn-secondary plate-button" style="height: 28px; padding: 0 12px; font-size: 11px; border-radius: 9999px;" id="btnCopyPort">Copy</button>
            </div>
            ` : ''}
            ${!isOnline ? `
            <div style="display: flex; justify-content: space-between; align-items: center; background: var(--plate-bg-low); padding: 10px 14px; border-radius: 18px; font-family: var(--font-family); font-size: 14px;">
              <span>Ping: <strong id="sessionLivePingValue">${currentPingMs !== null ? `${currentPingMs} ms` : 'Measuring...'}</strong></span>
              <span id="sessionLivePingDot" style="width: 10px; height: 10px; border-radius: 50%; background: ${
                currentPingMs === null
                  ? 'var(--ink-tertiary)'
                  : currentPingMs < 60
                  ? '#10b981'
                  : currentPingMs < 130
                  ? '#f59e0b'
                  : '#ef4444'
              };"></span>
            </div>
            ` : ''}
          </div>

          <!-- Players Card -->
          <div class="plate" style="flex: 1; padding: 20px; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; border-radius: 24px;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span style="font-size: 13px; font-weight: 700; color: var(--ink-secondary);">${isOnline ? 'Current Player' : 'Players'}</span>
              <span class="lamp active"></span>
            </div>
            <div class="member-list" id="sessionMemberList">
              ${(remote?.members || [{ id: store.get().profile.id, name: store.get().profile.name, forSeconds: 0 }])
                .map(
                  (m: any) => `
                <div class="member-row" style="border-radius: 18px;">
                  <div class="chip-avatar">${m.name.slice(0, 2).toUpperCase()}</div>
                  <div style="flex: 1;">
                    <div style="font-size: 14px;">${m.name}</div>
                    <div style="font-size: 11px; color: var(--ink-secondary);">${m.forSeconds ? Math.floor(m.forSeconds / 60) + 'm in room' : 'Connected'}${typeof m.rttMs === 'number' ? ` · ${m.rttMs} ms` : ''}</div>
                  </div>
                </div>
              `
                )
                .join('')}
            </div>
          </div>
        </div>

        <!-- Right Panel: Actions and Steps -->
        <div class="session-right">
          <div style="font-size: 20px; font-weight: 900; margin-bottom: 4px;">
            ${session.rom ? session.rom.displayName : 'Multiplayer Session'}
          </div>
          <div style="font-size: 14px; color: var(--ink-secondary); margin-bottom: 12px;">
            ${isOnline ? 'Online play via public community server' : (isHost ? 'You are hosting this session' : 'You are joined as a guest')}
          </div>

          <!-- Step 1 -->
          <div class="step-card" style="border-radius: 20px;">
            <div class="step-num ${session.step1Done ? 'done' : ''}">
              ${session.step1Done ? `&#10003;` : '1'}
            </div>
            <div class="step-info">
              <div class="step-title">Configure Network & Emulator</div>
              <div class="step-desc">
                ${isOnline ? 'Online multiplayer connection ready' : (session.step1Done ? (session.wireguardNative === false ? 'Tunnel config prepared (manual sudo elevation required)' : 'WireGuard tunnel active and emulator prepared') : 'Connecting to session relay...')}
              </div>
            </div>
            ${!session.step1Done && session.netplayBusy ? `<div class="spinner"></div>` : ''}
          </div>

          ${
            !isOnline && session.wireguardNative === false
              ? `
            <div class="plate" style="padding: 16px; border-left: 4px solid var(--compat-partial); display: flex; flex-direction: column; gap: 8px; border-radius: 20px;">
              <div style="display: flex; align-items: center; gap: 8px;">
                <span class="lamp" style="background-color: var(--compat-partial);"></span>
                <strong style="font-size: 13px;">Tunnel Elevation Required</strong>
              </div>
              <div style="font-size: 12px; color: var(--ink-secondary); line-height: 1.4;">
                WireGuard requires root/sudo privileges to activate the interface on your OS. Run in terminal:
              </div>
              <div style="display: flex; gap: 8px; align-items: center; background: var(--plate-bg-low); padding: 8px 12px; border-radius: 16px; font-family: monospace; font-size: 12px;">
                <span style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" id="wgCmdText">sudo wg-quick up "${session.wireguardConfigPath || 'emufii0.conf'}"</span>
                <button class="btn-secondary plate-button" id="btnCopyWgCmd" style="height: 28px; padding: 0 12px; font-size: 11px; border-radius: 9999px;">Copy</button>
              </div>
            </div>
          `
              : ''
          }

          <!-- Step 2 -->
          <div class="step-card" style="border-radius: 20px;">
            <div class="step-num ${session.step2Done ? 'done' : ''}">
              ${session.step2Done ? `&#10003;` : '2'}
            </div>
            <div class="step-info">
              <div class="step-title">Multiplayer In-Game</div>
              <div class="step-desc">
                ${isOnline ? (session.step2Done ? 'Emulator launched' : 'Ready to start game in emulator') : (isHost ? 'Host your room in the emulator' : (remote?.hostReady ? 'Host is ready! Launch emulator to connect' : 'Waiting for host to open room...'))}
              </div>
            </div>
            <button class="plate-button ${session.step2Done ? '' : 'btn-primary'}" id="btnLaunchEmu" style="width: auto; height: 38px; padding: 0 20px; font-size: 13px; border-radius: 9999px; display: inline-flex; align-items: center; justify-content: center; gap: 8px; font-weight: 800; cursor: pointer; transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1); ${
              session.step2Done
                ? 'background: #22c55e !important; color: #ffffff !important; border: 1px solid #16a34a !important; box-shadow: 0 4px 18px rgba(34, 197, 94, 0.4);'
                : ''
            }">
              ${
                session.launching
                  ? `<div class="spinner" style="width: 14px; height: 14px; border: 2px solid rgba(255, 255, 255, 0.25); border-top-color: currentColor; border-radius: 50%; animation: spin 800ms linear infinite;"></div><span>Launching...</span>`
                  : session.step2Done
                  ? `✓ Launched`
                  : `Launch`
              }
            </button>
          </div>

          <div style="flex: 1;"></div>

          <!-- Leave Button -->
          <div style="display: flex; justify-content: flex-end;">
            <button class="btn-secondary plate-button" id="btnLeaveSession" style="color: var(--compat-broken); border-color: rgba(224, 69, 123, 0.3); border-radius: 9999px; height: 42px; padding: 0 24px;">
              ${isOnline ? 'Exit Game' : 'Leave Session'}
            </button>
          </div>
        </div>
      </div>
    `;

    // Event listeners
    container.querySelector('#btnCopyCode')?.addEventListener('click', () => {
      navigator.clipboard.writeText(session.code);
      sfx.play('click');
    });

    container.querySelector('#btnInviteFriendsSession')?.addEventListener('click', () => {
      sfx.play('click');
      openInviteFriendsModal(session.code);
    });

    container.querySelector('#btnCopyIp')?.addEventListener('click', () => {
      navigator.clipboard.writeText(displayIp);
      sfx.play('click');
    });

    container.querySelector('#btnCopyPort')?.addEventListener('click', () => {
      navigator.clipboard.writeText(displayPort.toString());
      sfx.play('click');
    });

    container.querySelector('#btnCopyWgCmd')?.addEventListener('click', () => {
      const cmd = `sudo wg-quick up "${session.wireguardConfigPath || 'emufii0.conf'}"`;
      navigator.clipboard.writeText(cmd);
      sfx.play('click');
    });

    container.querySelector('#btnLaunchEmu')?.addEventListener('click', async () => {
      sfx.play('click');
      const current = store.get().activeSession;
      if (!current || !current.rom || current.launching) return;

      store.set({ activeSession: { ...current, launching: true } });

      const ok = await ensureEmulator(current.rom.console);
      if (!ok) {
        const reset = store.get().activeSession;
        if (reset) store.set({ activeSession: { ...reset, launching: false } });
        return;
      }

      try {
        if (store.get().settings.shareLastGame !== false && current.rom) {
          emuApi.coordinator
            .announceLastGame(
              store.get().profile.id,
              current.rom.displayName,
              current.rom.titleId || current.rom.productCode
            )
            .catch(() => {});
        }

        const res = await emuApi.emulators.launchGame(current.rom.console, {
          romPath: current.rom.filePath,
          isHost,
          code: current.code,
          hostIp: displayIp,
          port: displayPort,
          onlineMode: current.code === 'ONLINE',
          discId: current.rom.discId || current.rom.titleId,
          ps2ElfCrc: current.rom.ps2ElfCrc,
        });

        const updated = store.get().activeSession;
        if (updated) {
          store.set({
            activeSession: {
              ...updated,
              step2Done: res?.success === true,
              launching: false,
            },
          });
        }

        if (res && !res.success) {
          alert(res.message || `Could not launch ${current.rom.console} emulator. Check Settings to set its executable path.`);
        }
      } catch (err: any) {
        const updated = store.get().activeSession;
        if (updated) {
          store.set({
            activeSession: {
              ...updated,
              launching: false,
            },
          });
        }
        alert('Failed to launch emulator: ' + (err.message || err));
      }
    });

    container.querySelector('#btnLeaveSession')?.addEventListener('click', async () => {
      sfx.play('click');
      clearInterval(pollTimer);
      try {
        await emuApi.emulators.killActiveGame?.();
      } catch {}
      if (session.code !== 'ONLINE') {
        try {
          if (isHost && session.token) {
            await emuApi.coordinator.deleteSession(session.code, session.token);
          } else {
            await emuApi.coordinator.leaveSession(session.code, store.get().profile.id);
          }
        } catch {}
        await emuApi.wireguard.down();
      }
      store.set({ activeSession: null });
      store.navigate('library');
    });
  };

  update();

  const unsubscribe = store.subscribe((state) => {
    if (state.currentScreen === 'session') {
      update();
    }
  });

  const unsubscribeGameClosed = emuApi?.emulators?.onGameClosed?.(() => {
    const current = store.get().activeSession;
    if (current && current.step2Done) {
      store.set({ activeSession: { ...current, step2Done: false } });
    }
  });

  // Polling loop for session heartbeats, live RTT ping, and members
  const poll = async () => {
    const session = store.get().activeSession;
    if (!session || session.code === 'ONLINE') return;
    try {
      const endpointHost = (session.wireguard?.relayEndpoint || '').split(':')[0] || 'coord.emufii.xyz';
      if (emuApi.coordinator.pingHost) {
        const measured = await emuApi.coordinator.pingHost(endpointHost);
        if (typeof measured === 'number') {
          currentPingMs = measured;
          const pingEl = container.querySelector('#sessionLivePingValue');
          const dotEl = container.querySelector('#sessionLivePingDot') as HTMLElement | null;
          if (pingEl) pingEl.textContent = `${measured} ms`;
          if (dotEl) {
            dotEl.style.background =
              measured < 60 ? '#10b981' : measured < 130 ? '#f59e0b' : '#ef4444';
          }
        }
      }

      await emuApi.coordinator.heartbeat(
        session.code,
        store.get().profile.id,
        store.get().profile.name,
        currentPingMs ?? undefined
      );
      const remote = await emuApi.coordinator.getSession(session.code);
      const current = store.get().activeSession;
      if (current && current.code === session.code) {
        store.set({ activeSession: { ...current, remote } });
      }
    } catch {}
  };

  poll();
  pollTimer = setInterval(poll, 4000);
  return () => {
    clearInterval(pollTimer);
    unsubscribe();
    unsubscribeGameClosed?.();
  };
}
