import { store } from '../state.js';
import { sfx } from '../sfx.js';
import { esc } from '../html.js';
import { openContextMenuAt } from '../contextMenu.js';
import { openFloatingPanel } from '../components/FloatingPanel.js';
import { launchOnline, startHostSession } from '../components/FactSheet.js';
import {
  paletteFor,
  romTitle,
  getConsoleFriendlyName,
  consoleOf,
  renderArtwork,
  romMenu,
  renameRom,
  parentDir,
} from './LibraryScreen.js';

function formatSize(bytes: number): string {
  if (!bytes) return '—';
  const gb = bytes / 1024 ** 3;
  return gb >= 1 ? `${gb.toFixed(1)} GB` : `${Math.max(1, Math.round(bytes / 1024 ** 2))} MB`;
}

function formatDate(ms: number): string {
  if (!ms) return '—';
  return new Date(ms).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

// Steam-style game page: wide banner, one big call to action, details underneath.
export function renderGameScreen(container: HTMLElement) {
  const rom = store.get().selectedRom;
  if (!rom) {
    store.navigate('library');
    return;
  }

  const hasModes = rom.console === 'PSP' || rom.console === 'DS' || rom.console === 'PS2';
  let mode: 'wireless' | 'online' = 'wireless';
  let isPrivate = false;

  const compat = store.get().compatDb[rom.titleId || ''] || store.get().compatDb[rom.productCode || ''];
  const rating = compat?.rating || rom.compatRating || 'perfect';
  const compatLabel = rating === 'broken' ? 'Not working online' : rating === 'partial' ? 'Partially working online' : 'Works online';
  const note = compat?.note || rom.compatNote || '';
  const [c1, c2] = paletteFor(rom.displayName);

  container.innerHTML = `
    <div class="page game-page">
      <section class="game-banner" style="--hero-a: ${c1}; --hero-b: ${c2};">
        <div class="game-banner-backdrop">
          ${rom.artworkUrl ? `<img src="${esc(rom.artworkUrl)}" data-fallback="${esc(rom.fallbackArtworkUrl || '')}" alt="" draggable="false">` : ''}
        </div>
        <button class="page-back" id="btnGameBack" title="Back (Esc)">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><polyline points="15 18 9 12 15 6"/></svg>
          Library
        </button>
        <div class="game-banner-body">
          <div class="game-cover">${renderArtwork(rom, 'rom-artwork')}</div>
          <div class="game-heading">
            <div class="hero-eyebrow">${esc(getConsoleFriendlyName(consoleOf(rom)))}</div>
            <h1 class="game-title">${esc(romTitle(rom))}</h1>
            <div class="hero-meta">
              <span class="hero-compat ${esc(rating)}">${compatLabel}</span>
              ${rom.titleId ? `<span>${esc(rom.titleId)}</span>` : ''}
              <span>${formatSize(rom.fileSizeBytes)}</span>
            </div>
          </div>
        </div>
      </section>

      <div class="game-content">
        <section class="game-play-bar plate">
          <div class="game-play-main">
            <button class="btn-primary game-play-btn plate-button" id="btnGamePlay" data-autofocus>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M7 4.5v15l12-7.5z"/></svg>
              <span id="gamePlayLabel">Create a session</span>
            </button>
            <div class="game-play-hint" id="gamePlayHint">Host a room, then share the code with your friends.</div>
          </div>
          <div class="game-play-options">
            ${
              hasModes
                ? `<div class="segmented-mini" role="tablist" aria-label="Play mode">
                     <button role="tab" class="active" data-mode="wireless" aria-selected="true">Wireless play</button>
                     <button role="tab" data-mode="online" aria-selected="false">Online</button>
                   </div>`
                : ''
            }
            <label class="game-option" id="gamePrivacyRow">
              <span>
                <strong>Private session</strong>
                <small>Hidden from the session list; only your code lets people in.</small>
              </span>
              <span class="clean-switch"><input type="checkbox" id="chkGamePrivate"><span class="slider"></span></span>
            </label>
            <button class="btn-secondary plate-button" id="btnGameJoin">Join with a code</button>
          </div>
        </section>

        <div class="game-columns">
          <section class="page-card">
            <div class="page-card-head"><h2>About this copy</h2></div>
            <dl class="game-facts">
              <div><dt>Title</dt><dd>${esc(romTitle(rom))}</dd></div>
              ${romTitle(rom) !== rom.displayName ? `<div><dt>Original title</dt><dd>${esc(rom.displayName)}</dd></div>` : ''}
              <div><dt>Console</dt><dd>${esc(getConsoleFriendlyName(consoleOf(rom)))}</dd></div>
              <div><dt>Game ID</dt><dd>${esc(rom.titleId || rom.productCode || '—')}</dd></div>
              <div><dt>Size</dt><dd>${formatSize(rom.fileSizeBytes)}</dd></div>
              ${rom.addedAt > Date.UTC(2005, 0) ? `<div><dt>Added</dt><dd>${formatDate(rom.addedAt)}</dd></div>` : ''}
              <div class="wide"><dt>File</dt><dd class="selectable" title="${esc(rom.filePath)}">${esc(rom.filePath)}</dd></div>
            </dl>
          </section>

          <section class="page-card">
            <div class="page-card-head"><h2>Online play</h2></div>
            <div class="game-compat ${esc(rating)}">
              <strong>${compatLabel}</strong>
              <p>${esc(note || (rating === 'broken' ? 'Players reported that this game does not work over Emufii yet.' : rating === 'partial' ? 'It runs, but some modes may not connect.' : 'Netplay settings are applied automatically when you start a session.'))}</p>
            </div>
            <div class="game-actions-list">
              <button class="game-action" id="btnGameRename">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>
                Rename <kbd>F2</kbd>
              </button>
              <button class="game-action" id="btnGameFolder">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
                Show in folder
              </button>
              <button class="game-action" id="btnGameMore">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg>
                More
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>
  `;

  const q = <T extends HTMLElement>(sel: string) => container.querySelector<T>(sel);
  const label = q('#gamePlayLabel')!;
  const hint = q('#gamePlayHint')!;
  const privacy = q('#gamePrivacyRow')!;
  const join = q('#btnGameJoin')!;

  const update = () => {
    container.querySelectorAll<HTMLElement>('[data-mode]').forEach((b) => {
      const active = b.dataset.mode === mode;
      b.classList.toggle('active', active);
      b.setAttribute('aria-selected', String(active));
    });
    const online = mode === 'online';
    label.textContent = online ? 'Play online' : 'Create a session';
    hint.textContent = online
      ? 'Launches the game connected to its community online servers.'
      : isPrivate
      ? 'Private room: only people with your code can join.'
      : 'Host a room, then share the code with your friends.';
    privacy.hidden = online;
    join.hidden = online;
  };
  update();

  container.querySelectorAll<HTMLElement>('[data-mode]').forEach((b) =>
    b.addEventListener('click', () => {
      if (mode === b.dataset.mode) return;
      sfx.play('toggle');
      mode = b.dataset.mode as typeof mode;
      update();
    })
  );
  q<HTMLInputElement>('#chkGamePrivate')?.addEventListener('change', (e) => {
    sfx.play('toggle');
    isPrivate = (e.target as HTMLInputElement).checked;
    update();
  });

  q('#btnGamePlay')?.addEventListener('click', () => {
    sfx.play('confirm');
    if (mode === 'online') launchOnline(rom);
    else startHostSession(rom, isPrivate);
  });
  join.addEventListener('click', () => {
    sfx.play('click');
    openFloatingPanel('join');
  });
  q('#btnGameBack')?.addEventListener('click', () => {
    sfx.play('click');
    if (!store.goBack()) store.navigate('library');
  });
  q('#btnGameRename')?.addEventListener('click', () => renameRom(rom));
  q('#btnGameFolder')?.addEventListener('click', () => (window as any).emufii.library.openPath(parentDir(rom.filePath)));
  q('#btnGameMore')?.addEventListener('click', (e) => openContextMenuAt(e.currentTarget as HTMLElement, romMenu(rom).slice(2)));

  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'F2' && !(e.target as HTMLElement).closest('input')) {
      e.preventDefault();
      renameRom(rom);
    }
  };
  window.addEventListener('keydown', onKey);

  const unsubscribe = store.subscribe((state) => {
    const title = container.querySelector('.game-title');
    if (state.currentScreen === 'game' && title && title.textContent !== romTitle(rom)) {
      title.textContent = romTitle(rom);
    }
  });

  return () => {
    window.removeEventListener('keydown', onKey);
    unsubscribe();
  };
}
