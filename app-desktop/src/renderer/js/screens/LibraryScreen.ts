import { store, AppState } from '../state.js';
import { sfx } from '../sfx.js';
import { esc } from '../html.js';
import { resolveTheme } from '../theme.js';
import { openFactSheet } from '../components/FactSheet.js';
import { openContextMenu, openContextMenuAt, MenuEntry } from '../contextMenu.js';
import { promptText } from '../dialogs.js';
import { toast } from '../toast.js';
import type { RomItem } from '../../../main/library/scanner.js';

type Layout = 'grid' | 'carousel' | 'list';
type ConsoleGroup = { console: string; roms: RomItem[] };
type Listen = (target: Window | HTMLElement, type: string, fn: (e: any) => void, opts?: AddEventListenerOptions) => void;

let carouselIndex = 0;

const CONSOLE_ORDER = ['3DS', 'PSP', 'DS', 'Switch', 'GameCube', 'Wii', 'PS2'];
const CONSOLE_ART = new Set(['ps2', 'ds', 'three_ds', 'wii', 'psp', 'switch', 'gamecube']);

const ICONS = {
  open: '<path d="M5 12h14"/><path d="m12 5 7 7-7 7"/>',
  host: '<rect x="2" y="3" width="9.5" height="7.5" rx="2"/><rect x="12.5" y="13.5" width="9.5" height="7.5" rx="2"/>',
  join: '<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/>',
  rename: '<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
  hide: '<path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/><path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"/><path d="M6.61 6.61A13.53 13.53 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61"/><line x1="2" y1="2" x2="22" y2="22"/>',
  folder: '<path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>',
  copy: '<rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
};

export function getConsoleSlug(consoleType: string): string {
  const map: Record<string, string> = {
    '3DS': 'three_ds',
    PSP: 'psp',
    DS: 'ds',
    Switch: 'switch',
    GameCube: 'gamecube',
    Wii: 'wii',
    PS2: 'ps2',
  };
  return map[consoleType] || consoleType.toLowerCase();
}

export function getConsoleFriendlyName(consoleType: string): string {
  const map: Record<string, string> = {
    '3DS': 'Nintendo 3DS',
    DS: 'Nintendo DS',
    PSP: 'PlayStation Portable',
    PS2: 'PlayStation 2',
    Wii: 'Nintendo Wii',
    GameCube: 'Nintendo GameCube',
    Switch: 'Nintendo Switch',
  };
  return map[consoleType] || consoleType;
}

export const EMUFII_PALETTE: [string, string][] = [
  ['#3FCFC0', '#0E9C8F'],
  ['#EE6FA3', '#C24B7E'],
  ['#6B72E0', '#8E93EC'],
  ['#1FA98B', '#0A4A44'],
  ['#C98A12', '#5A1D3E'],
  ['#5A8FD8', '#6B72E0'],
  ['#F793BC', '#5A1D3E'],
  ['#5CE0D2', '#0A4A44'],
  ['#8E93EC', '#5A1D3E'],
  ['#C24B7E', '#0A4A44'],
  ['#0E9C8F', '#5A1D3E'],
];

export function paletteFor(seed: string): [string, string] {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) {
    hash = (hash * 31 + seed.charCodeAt(i)) | 0;
  }
  return EMUFII_PALETTE[Math.abs(hash) % EMUFII_PALETTE.length];
}

export function shortLabel(displayName: string): string {
  const words = displayName.split(/[\s._-]+/).filter((w) => /[a-z0-9]/i.test(w));
  if (words.length === 0) return '?';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}

export function romTitle(rom: RomItem): string {
  return store.get().settings.customTitles?.[rom.id] || rom.displayName;
}

export function consoleOf(rom: RomItem): string {
  return (rom.console as string) === 'GBA' ? 'DS' : rom.console;
}

function isNoise(rom: RomItem): boolean {
  const name = (rom.fileName || rom.displayName || '').toLowerCase();
  return name.startsWith('photo') || name.includes('photos-') || name.includes('screenshot');
}

export function parentDir(filePath: string): string {
  const idx = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
  return idx > 0 ? filePath.slice(0, idx) : filePath;
}

export function setLibraryLayout(layout: Layout) {
  const settings = store.get().settings;
  if (settings.libraryLayout === layout) return;
  sfx.play('click');
  (window as any).emufii.config.updateSettings({ libraryLayout: layout });
  store.set({ settings: { ...settings, libraryLayout: layout } });
}

function setLibrarySort(sort: AppState['settings']['librarySort']) {
  const settings = store.get().settings;
  (window as any).emufii.config.updateSettings({ librarySort: sort });
  store.set({ settings: { ...settings, librarySort: sort } });
}

function visibleRoms(state: AppState): RomItem[] {
  const { settings } = state;
  return state.roms.filter((r) => {
    if (settings.hiddenRoms.includes(r.id) || isNoise(r)) return false;
    if (settings.hiddenConsoles?.includes(consoleOf(r))) return false;
    if (settings.hideIncompatible && r.titleId && state.compatDb[r.titleId]?.playable === false) return false;
    return true;
  });
}

function filteredRoms(state: AppState): RomItem[] {
  const query = state.searchQuery.trim().toLowerCase();
  const titles = state.settings.customTitles || {};
  const list = visibleRoms(state).filter((r) => {
    if (state.consoleFilter !== 'ALL' && consoleOf(r) !== state.consoleFilter) return false;
    if (!query) return true;
    const haystack = [titles[r.id], r.displayName, r.fileName, r.console, r.titleId, r.productCode]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return query.split(/\s+/).every((q) => haystack.includes(q));
  });

  const titleOf = (r: RomItem) => titles[r.id] || r.displayName;
  list.sort((a, b) => {
    switch (state.settings.librarySort) {
      case 'date':
        return b.addedAt - a.addedAt;
      case 'console':
        return consoleOf(a).localeCompare(consoleOf(b)) || titleOf(a).localeCompare(titleOf(b));
      default:
        return titleOf(a).localeCompare(titleOf(b));
    }
  });
  return list;
}

export function consoleGroups(state: AppState): ConsoleGroup[] {
  const map = new Map<string, RomItem[]>();
  for (const r of visibleRoms(state)) {
    const c = consoleOf(r);
    if (!CONSOLE_ART.has(getConsoleSlug(c))) continue;
    if (!map.has(c)) map.set(c, []);
    map.get(c)!.push(r);
  }
  const rank = (c: string) => (CONSOLE_ORDER.includes(c) ? CONSOLE_ORDER.indexOf(c) : 99);
  return [...map.entries()].map(([console, roms]) => ({ console, roms })).sort((a, b) => rank(a.console) - rank(b.console));
}

function renderCompatBadge(rating?: string | null): string {
  const r = rating || 'perfect';
  const variants: Record<string, { cls: string; title: string; path: string }> = {
    perfect: { cls: 'badge-perfect', title: 'Playable online', path: '<polyline points="20 6 9 17 4 12"/>' },
    partial: { cls: 'badge-partial', title: 'Partially working', path: '<line x1="12" y1="7" x2="12" y2="13"/><circle cx="12" cy="17" r="0.8"/>' },
    broken: { cls: 'badge-broken', title: 'Not working online', path: '<line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>' },
  };
  const v = variants[r] || { cls: 'badge-untested', title: 'Untested', path: '<path d="M5 12h14"/>' };
  return `
    <span class="rom-compat-badge ${v.cls}" title="${v.title}">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">${v.path}</svg>
    </span>`;
}

export function renderArtwork(r: RomItem, imgClass: string, placeholderClass = 'rom-placeholder-art'): string {
  const title = romTitle(r);
  const [c1, c2] = paletteFor(r.displayName);
  return `
    ${r.artworkUrl ? `<img src="${esc(r.artworkUrl)}" data-fallback="${esc(r.fallbackArtworkUrl || '')}" class="${imgClass}" alt="" loading="lazy" draggable="false">` : ''}
    <div class="${placeholderClass}" data-img-fallback style="background: linear-gradient(135deg, ${c1}, ${c2});" ${r.artworkUrl ? 'hidden' : ''}>
      <span class="rom-placeholder-initials">${esc(shortLabel(title))}</span>
    </div>`;
}

function countLabel(n: number): string {
  return `${n} ${n === 1 ? 'game' : 'games'}`;
}

function renderHero(rom: RomItem | null): string {
  if (!rom) return '';
  const [c1, c2] = paletteFor(rom.displayName);
  const rating = rom.compatRating || 'perfect';
  const compat = rating === 'broken' ? 'Not working online' : rating === 'partial' ? 'Partially working online' : 'Works online';
  return `
    <div class="hero-backdrop" style="--hero-a: ${c1}; --hero-b: ${c2};">
      ${rom.artworkUrl ? `<img src="${esc(rom.artworkUrl)}" data-fallback="${esc(rom.fallbackArtworkUrl || '')}" alt="" draggable="false">` : ''}
    </div>
    <div class="hero-art">${renderArtwork(rom, 'rom-artwork')}</div>
    <div class="hero-info">
      <div class="hero-eyebrow">${esc(getConsoleFriendlyName(consoleOf(rom)))}</div>
      <h2 class="hero-title">${esc(romTitle(rom))}</h2>
      <div class="hero-meta">
        <span class="hero-compat ${esc(rating)}">${compat}</span>
        ${rom.titleId ? `<span>${esc(rom.titleId)}</span>` : ''}
        ${rom.fileSizeBytes ? `<span>${(rom.fileSizeBytes / 1024 / 1024 / 1024).toFixed(rom.fileSizeBytes > 1024 ** 3 ? 1 : 2)} GB</span>` : ''}
      </div>
    </div>
    <div class="hero-actions">
      <button class="btn-primary plate-button" id="btnHeroPlay" data-rom-id="${esc(rom.id)}">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M7 4.5v15l12-7.5z"/></svg>
        Play
      </button>
      <button class="hero-more plate-button" id="btnHeroMore" title="More actions" aria-label="More actions">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg>
      </button>
    </div>`;
}

function renderGridItems(roms: RomItem[]): string {
  return roms
    .map(
      (r) => `
    <div class="rom-cell" data-rom-id="${esc(r.id)}" role="button" tabindex="0" title="${esc(romTitle(r))}">
      <div class="rom-artwork-wrap">
        ${renderArtwork(r, 'rom-artwork')}
        <span class="rom-tile-badge">${esc(r.console)}</span>
        ${renderCompatBadge(r.compatRating)}
      </div>
      <div class="rom-title">${esc(romTitle(r))}</div>
    </div>`
    )
    .join('');
}

function renderListItems(roms: RomItem[]): string {
  return roms
    .map(
      (r) => `
    <div class="list-row" data-rom-id="${esc(r.id)}" role="button" tabindex="0">
      <div class="list-row-art">${renderArtwork(r, 'rom-artwork', 'rom-placeholder-art small')}</div>
      <div class="list-row-text">
        <div class="list-row-title">${esc(romTitle(r))}</div>
        <div class="list-row-sub">${esc(getConsoleFriendlyName(consoleOf(r)))}${r.titleId ? ` · ${esc(r.titleId)}` : ''}</div>
      </div>
      ${renderCompatBadge(r.compatRating)}
      <span class="rom-tile-badge static">${esc(r.console)}</span>
      <svg class="list-row-chevron" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
    </div>`
    )
    .join('');
}

function renderRoms(state: AppState, roms: RomItem[], layout: Layout): string {
  if (layout === 'carousel') {
    return `
      <div class="carousel-container" data-own-arrows="x">
        <div class="roms-carousel-track" id="carouselTrack">
          ${roms
            .map(
              (r, idx) => `
            <div class="carousel-card ${idx === carouselIndex ? 'active' : 'neighbor'}" data-rom-id="${esc(r.id)}" data-index="${idx}" role="button" tabindex="${idx === carouselIndex ? 0 : -1}" aria-label="${esc(romTitle(r))}">
              <span class="cursor-ring static" aria-hidden="true"></span>
              <div class="carousel-artwork-wrap">
                ${renderArtwork(r, 'rom-artwork')}
                <span class="rom-tile-badge">${esc(r.console)}</span>
                ${renderCompatBadge(r.compatRating)}
              </div>
              <div class="carousel-title">${esc(romTitle(r))}</div>
            </div>`
            )
            .join('')}
        </div>
      </div>`;
  }

  const wrap = (items: string) => (layout === 'list' ? `<div class="roms-list">${items}</div>` : `<div class="roms-grid">${items}</div>`);
  const items = layout === 'list' ? renderListItems : renderGridItems;

  // "All games" sorted by console reads best as one titled section per console.
  const sectioned = state.consoleFilter === 'ALL' && !state.searchQuery.trim() && state.settings.librarySort === 'console';
  if (!sectioned) return wrap(items(roms));

  const sections = new Map<string, RomItem[]>();
  for (const r of roms) {
    const c = consoleOf(r);
    if (!sections.has(c)) sections.set(c, []);
    sections.get(c)!.push(r);
  }
  return [...sections.entries()]
    .map(
      ([c, list]) => `
      <section class="library-section">
        <h3 class="library-section-title">${esc(getConsoleFriendlyName(c))}<span>${list.length}</span></h3>
        ${wrap(items(list))}
      </section>`
    )
    .join('');
}

function renderEmptyLibrary(): string {
  return `
    <div class="library-empty">
      <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="4"/><path d="M9 3v18M15 3v18M3 9h18M3 15h18"/></svg>
      <h2>Your library is empty</h2>
      <p>Add game files (.iso, .rvz, .3ds, .nds, .nsp, .cso…) or a whole folder. You can also drag and drop them anywhere in this window.</p>
      <div class="library-empty-actions">
        <button class="btn-primary plate-button" id="btnAddRomFile">Add game files</button>
        <button class="btn-secondary plate-button" id="btnAddRomDir">Add a folder</button>
      </div>
    </div>`;
}

function renderNoResults(state: AppState): string {
  const where = state.consoleFilter === 'ALL' ? '' : ` in ${esc(getConsoleFriendlyName(state.consoleFilter))}`;
  return `
    <div class="library-empty compact">
      <h3>${state.searchQuery.trim() ? `No match for “${esc(state.searchQuery.trim())}”${where}` : `No games${where}`}</h3>
      <div class="library-empty-actions">
        ${state.searchQuery.trim() ? '<button class="btn-secondary plate-button" id="btnClearSearch">Clear search</button>' : ''}
        ${state.consoleFilter !== 'ALL' ? '<button class="btn-secondary plate-button" id="btnBackToConsoles">Show all games</button>' : ''}
      </div>
    </div>`;
}

function renderViewport(state: AppState, roms: RomItem[]): string {
  if (visibleRoms(state).length === 0) return renderEmptyLibrary();
  return roms.length ? renderRoms(state, roms, state.settings.libraryLayout as Layout) : renderNoResults(state);
}

function renderToolbar(state: AppState): string {
  const { libraryLayout: layout, librarySort: sort } = state.settings;
  const layoutBtn = (id: Layout, title: string, key: string, icon: string) => `
    <button class="icon-btn ${layout === id ? 'active' : ''}" data-layout-btn="${id}" title="${title} (Ctrl+${key})" aria-pressed="${layout === id}">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">${icon}</svg>
    </button>`;

  return `
    <div class="library-toolbar">
      <label class="search-slab">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        <input type="search" id="librarySearch" placeholder="Search games" value="${esc(state.searchQuery)}" autocomplete="off" spellcheck="false">
        <kbd class="search-kbd">Ctrl F</kbd>
      </label>
      <div class="library-toolbar-right">
        <label class="sort-select" title="Sort games">
          <span>Sort</span>
          <select id="librarySort">
            <option value="console" ${sort === 'console' ? 'selected' : ''}>Console</option>
            <option value="az" ${sort === 'az' ? 'selected' : ''}>A – Z</option>
            <option value="date" ${sort === 'date' ? 'selected' : ''}>Recently added</option>
          </select>
        </label>
        <div class="view-controls" role="group" aria-label="Layout">
          ${layoutBtn('grid', 'Grid', '1', '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/>')}
          ${layoutBtn('carousel', 'Carousel', '2', '<rect x="7" y="3" width="10" height="18" rx="2"/><rect x="2" y="6" width="3" height="12" rx="1" opacity="0.4"/><rect x="19" y="6" width="3" height="12" rx="1" opacity="0.4"/>')}
          ${layoutBtn('list', 'List', '3', '<rect x="3" y="4" width="18" height="4" rx="1.5"/><rect x="3" y="10" width="18" height="4" rx="1.5"/><rect x="3" y="16" width="18" height="4" rx="1.5"/>')}
        </div>
      </div>
    </div>`;
}

export async function addRomFiles() {
  const emuApi = (window as any).emufii;
  const selected = await emuApi.dialog.selectRomFiles();
  if (!selected?.length) return;
  const dirs = store.get().settings.romDirectories || [];
  const targetDir = dirs[0] || (await emuApi.library.getSpecialRomDirectory())?.path;
  const res = await emuApi.library.importRomFiles(selected, targetDir);
  const scanned = await emuApi.library.scan();
  const config = await emuApi.config.get();
  store.set({ roms: scanned, settings: { ...store.get().settings, romDirectories: config.settings.romDirectories } });
  if (res?.count) toast(`Imported ${res.count} ${res.count === 1 ? 'game' : 'games'}`, 'success', 2500);
}

export async function addRomFolder() {
  const emuApi = (window as any).emufii;
  const selected = await emuApi.dialog.selectDirectory();
  if (!selected) return;
  await emuApi.library.addDirectory(selected);
  const scanned = await emuApi.library.scan();
  const config = await emuApi.config.get();
  store.set({ roms: scanned, settings: { ...store.get().settings, romDirectories: config.settings.romDirectories } });
}

export function romMenu(rom: RomItem): MenuEntry[] {
  const emuApi = (window as any).emufii;
  const hasCustomTitle = !!store.get().settings.customTitles?.[rom.id];
  return [
    { label: 'Open', icon: ICONS.open, hint: 'Enter', action: () => openFactSheet(rom) },
    'separator',
    {
      label: 'Rename…',
      icon: ICONS.rename,
      hint: 'F2',
      action: () => renameRom(rom),
    },
    ...(hasCustomTitle
      ? [
          {
            label: 'Restore original title',
            icon: ICONS.rename,
            action: async () => {
              await emuApi.config.renameRom(rom.id, '');
              const config = await emuApi.config.get();
              store.set({ settings: { ...store.get().settings, customTitles: config.settings.customTitles } });
            },
          } as MenuEntry,
        ]
      : []),
    { label: 'Copy title', icon: ICONS.copy, action: () => navigator.clipboard.writeText(romTitle(rom)) },
    { label: 'Show in folder', icon: ICONS.folder, action: () => emuApi.library.openPath(parentDir(rom.filePath)) },
    'separator',
    {
      label: 'Hide from library',
      icon: ICONS.hide,
      hint: 'Del',
      danger: true,
      action: () => hideRom(rom),
    },
  ];
}

export async function renameRom(rom: RomItem) {
  const emuApi = (window as any).emufii;
  const value = await promptText({
    title: 'Rename game',
    description: `Original title: ${rom.displayName}. Leave empty to restore it.`,
    value: romTitle(rom),
    placeholder: rom.displayName,
    maxLength: 120,
  });
  if (value === null) return;
  await emuApi.config.renameRom(rom.id, value === rom.displayName ? '' : value);
  const config = await emuApi.config.get();
  store.set({ settings: { ...store.get().settings, customTitles: config.settings.customTitles } });
}

export async function hideRom(rom: RomItem) {
  const emuApi = (window as any).emufii;
  await emuApi.config.toggleHideRom(rom.id);
  const config = await emuApi.config.get();
  store.set({ settings: { ...store.get().settings, hiddenRoms: config.settings.hiddenRoms } });
  toast(`“${romTitle(rom)}” is hidden. Restore it from Settings › Library.`, 'info');
}

// The neon cursor glides between games, following the mouse or the keyboard focus (CursorRing.kt).
function setupCursor(viewport: HTMLElement, onTarget: (romId: string) => void, on: Listen): () => void {
  const cursor = document.createElement('div');
  cursor.className = 'cursor-ring';
  cursor.setAttribute('aria-hidden', 'true');
  viewport.appendChild(cursor);

  let current: HTMLElement | null = null;
  let frame = 0;

  const surfaceOf = (cell: HTMLElement) => cell.querySelector<HTMLElement>('.rom-artwork-wrap') || cell;

  const place = (cell: HTMLElement | null) => {
    if (!cell || !viewport.contains(cell)) {
      current = null;
      cursor.classList.remove('visible');
      return;
    }
    const wasHidden = !cursor.classList.contains('visible');
    const surface = surfaceOf(cell);
    const vp = viewport.getBoundingClientRect();
    const r = surface.getBoundingClientRect();
    const x = r.left - vp.left + viewport.scrollLeft;
    const y = r.top - vp.top + viewport.scrollTop;
    const band = Math.round(Math.min(16, Math.max(6, Math.min(r.width, r.height) * 0.1)));

    cursor.classList.toggle('snap', wasHidden);
    cursor.style.setProperty('--x', `${x}px`);
    cursor.style.setProperty('--y', `${y}px`);
    cursor.style.setProperty('--w', `${r.width}px`);
    cursor.style.setProperty('--h', `${r.height}px`);
    cursor.style.setProperty('--r', getComputedStyle(surface).borderTopLeftRadius);
    cursor.style.setProperty('--band-size', `${band}px`);
    if (wasHidden) void cursor.offsetWidth;
    cursor.classList.add('visible');
    cursor.classList.remove('snap');

    if (cell !== current) {
      current = cell;
      if (cell.dataset.romId) onTarget(cell.dataset.romId);
    }
  };

  const cellOf = (el: EventTarget | null) => (el as HTMLElement | null)?.closest?.<HTMLElement>('.rom-cell, .list-row') || null;
  const focusedCell = () => cellOf(document.activeElement);

  on(viewport, 'pointerover', (e: PointerEvent) => {
    const cell = cellOf(e.target);
    if (cell && cell !== current) {
      if (cell !== focusedCell()) sfx.play('hover');
      place(cell);
    }
  });
  on(viewport, 'pointerleave', () => place(focusedCell()));
  on(viewport, 'focusin', (e: FocusEvent) => place(cellOf(e.target)));
  on(viewport, 'focusout', () => requestAnimationFrame(() => !viewport.matches(':hover') && place(focusedCell())));

  const replace = () => {
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => current && place(current));
  };
  const ro = new ResizeObserver(replace);
  ro.observe(viewport);

  const initial = focusedCell();
  if (initial) place(initial);

  return () => {
    ro.disconnect();
    cancelAnimationFrame(frame);
  };
}

let heroRomId: string | null = null;

export function renderLibraryScreen(container: HTMLElement) {
  const prevCleanup = (container as any)._libraryCleanup;
  if (prevCleanup) prevCleanup();

  const state = store.get();
  const isLight = resolveTheme(state.settings.theme) === 'light';
  const layout = state.settings.libraryLayout as Layout;
  const roms = filteredRoms(state);
  carouselIndex = Math.max(0, Math.min(carouselIndex, roms.length - 1));

  const cleanups: (() => void)[] = [];
  const on: Listen = (target, type, fn, opts) => {
    target.addEventListener(type, fn as EventListener, opts);
    cleanups.push(() => target.removeEventListener(type, fn as EventListener, opts));
  };

  let viewport = container.querySelector<HTMLElement>('#libraryViewport');
  if (!viewport) {
    container.innerHTML = `
      <div class="page library-page">
        <header class="page-header">
          <div class="page-title-block">
            <h1 class="page-title" id="libraryTitle"></h1>
            <span class="page-count" id="libraryCount"></span>
          </div>
          ${renderToolbar(state)}
        </header>
        <div class="library-hero" id="libraryHero"></div>
        <div class="library-viewport" id="libraryViewport" data-layout="${layout}"></div>
      </div>
    `;
    viewport = container.querySelector<HTMLElement>('#libraryViewport')!;

    const search = container.querySelector<HTMLInputElement>('#librarySearch')!;
    search.addEventListener('input', () => {
      carouselIndex = 0;
      store.set({ searchQuery: search.value });
    });
    search.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && search.value) {
        e.preventDefault();
        search.value = '';
        store.set({ searchQuery: '' });
      } else if (e.key === 'ArrowDown' || e.key === 'Enter') {
        const first = container.querySelector<HTMLElement>('.rom-cell, .list-row, .carousel-card.active');
        if (first) {
          e.preventDefault();
          first.focus();
          if (e.key === 'Enter' && store.get().searchQuery.trim()) first.click();
        }
      }
    });

    container.querySelector<HTMLSelectElement>('#librarySort')?.addEventListener('change', (e) => {
      setLibrarySort((e.target as HTMLSelectElement).value as AppState['settings']['librarySort']);
    });

    container.querySelectorAll<HTMLElement>('[data-layout-btn]').forEach((btn) => {
      btn.addEventListener('click', () => setLibraryLayout(btn.dataset.layoutBtn as Layout));
    });

  } else {
    container.querySelectorAll<HTMLElement>('[data-layout-btn]').forEach((btn) => {
      const active = btn.dataset.layoutBtn === layout;
      btn.classList.toggle('active', active);
      btn.setAttribute('aria-pressed', String(active));
    });
    const sortSelect = container.querySelector<HTMLSelectElement>('#librarySort');
    if (sortSelect) sortSelect.value = state.settings.librarySort;
    const search = container.querySelector<HTMLInputElement>('#librarySearch');
    if (search && search.value !== state.searchQuery) search.value = state.searchQuery;
  }

  const titleEl = container.querySelector<HTMLElement>('#libraryTitle');
  const countEl = container.querySelector<HTMLElement>('#libraryCount');
  if (titleEl) titleEl.textContent = state.consoleFilter === 'ALL' ? 'Library' : getConsoleFriendlyName(state.consoleFilter);
  if (countEl) countEl.textContent = countLabel(roms.length);

  const hadFocusInViewport = viewport.contains(document.activeElement);
  const focusedKey = (document.activeElement as HTMLElement | null)?.dataset?.romId;
  const viewKey = `${state.consoleFilter}|${state.searchQuery}|${layout}`;
  const keepScroll = viewport.dataset.viewKey === viewKey ? viewport.scrollTop : 0;
  viewport.dataset.layout = layout;
  viewport.dataset.viewKey = viewKey;
  viewport.innerHTML = renderViewport(state, roms);
  viewport.scrollTop = keepScroll;
  if (hadFocusInViewport && focusedKey) {
    viewport.querySelector<HTMLElement>(`[data-rom-id="${CSS.escape(focusedKey)}"]`)?.focus({ preventScroll: true });
  }

  const romById = (id?: string | null) => state.roms.find((r) => r.id === id);

  const hero = container.querySelector<HTMLElement>('#libraryHero')!;
  const showHero = (id: string | null) => {
    const rom = romById(id) || roms[0] || null;
    hero.hidden = !rom;
    if (!rom || hero.dataset.romId === rom.id) return;
    heroRomId = rom.id;
    hero.dataset.romId = rom.id;
    hero.classList.remove('swap');
    void hero.offsetWidth;
    hero.classList.add('swap');
    hero.innerHTML = renderHero(rom);
  };
  hero.dataset.romId = '';
  showHero(roms.some((r) => r.id === heroRomId) ? heroRomId : null);

  on(hero, 'click', (e: MouseEvent) => {
    const rom = romById(hero.dataset.romId);
    if (!rom) return;
    const target = e.target as HTMLElement;
    if (target.closest('#btnHeroPlay')) {
      sfx.play('click');
      openFactSheet(rom);
    } else if (target.closest('#btnHeroMore')) {
      sfx.play('click');
      openContextMenuAt(target.closest('#btnHeroMore') as HTMLElement, romMenu(rom));
    }
  });

  const activate = (el: HTMLElement) => {
    const rom = romById(el.dataset.romId);
    if (rom) {
      sfx.play('click');
      openFactSheet(rom);
    }
  };

  const isCarousel = layout === 'carousel' && roms.length > 0;

  on(viewport, 'click', (e: MouseEvent) => {
    const el = (e.target as HTMLElement).closest<HTMLElement>('[data-rom-id]');
    if (!el || isCarousel) return;
    activate(el);
  });

  on(viewport, 'contextmenu', (e: MouseEvent) => {
    const el = (e.target as HTMLElement).closest<HTMLElement>('[data-rom-id]');
    const rom = romById(el?.dataset.romId);
    if (!rom) return;
    e.preventDefault();
    openContextMenu(e.clientX, e.clientY, romMenu(rom));
  });

  on(viewport, 'keydown', (e: KeyboardEvent) => {
    const el = (e.target as HTMLElement).closest<HTMLElement>('[data-rom-id]');
    if (!el) return;
    const rom = romById(el.dataset.romId);
    if (!rom) return;
    if (e.key === 'ContextMenu' || (e.shiftKey && e.key === 'F10')) {
      e.preventDefault();
      openContextMenuAt(el, romMenu(rom));
    } else if (e.key === 'F2') {
      e.preventDefault();
      renameRom(rom);
    } else if (e.key === 'Delete') {
      e.preventDefault();
      hideRom(rom);
    }
  });

  viewport.querySelector('#btnAddRomFile')?.addEventListener('click', () => {
    sfx.play('click');
    addRomFiles();
  });
  viewport.querySelector('#btnAddRomDir')?.addEventListener('click', () => {
    sfx.play('click');
    addRomFolder();
  });
  viewport.querySelector('#btnBackToConsoles')?.addEventListener('click', () => {
    sfx.play('click');
    carouselIndex = 0;
    store.set({ consoleFilter: 'ALL' });
  });
  viewport.querySelector('#btnClearSearch')?.addEventListener('click', () => {
    store.set({ searchQuery: '' });
    container.querySelector<HTMLInputElement>('#librarySearch')?.focus();
  });

  if (isCarousel) {
    cleanups.push(setupCarousel(viewport, roms.length, activate, on, (card) => showHero(card.dataset.romId || null)));
  } else if (roms.length) {
    cleanups.push(setupCursor(viewport, (id) => showHero(id), on));
  }

  const cleanupAll = () => cleanups.forEach((fn) => fn());
  (container as any)._libraryCleanup = cleanupAll;
  return cleanupAll;
}

function setupCarousel(
  viewport: HTMLElement,
  count: number,
  activate: (el: HTMLElement) => void,
  on: Listen,
  onActive: (card: HTMLElement) => void
): () => void {
  const track = viewport.querySelector<HTMLElement>('#carouselTrack');
  const box = viewport.querySelector<HTMLElement>('.carousel-container');
  const cards = Array.from(viewport.querySelectorAll<HTMLElement>('.carousel-card'));
  if (!track || !box || cards.length === 0) return () => {};

  const update = (focus = false) => {
    cards.forEach((card, idx) => {
      const active = idx === carouselIndex;
      card.classList.toggle('active', active);
      card.classList.toggle('neighbor', !active);
      card.tabIndex = active ? 0 : -1;
    });
    const card = cards[0];
    const step = card.offsetWidth + parseFloat(getComputedStyle(track).columnGap || '28');
    const offset = box.clientWidth / 2 - card.offsetWidth / 2 - carouselIndex * step;
    track.style.transform = `translateX(${offset}px)`;
    if (focus) cards[carouselIndex].focus({ preventScroll: true });
    onActive(cards[carouselIndex]);
  };

  const move = (delta: number) => {
    const next = Math.max(0, Math.min(count - 1, carouselIndex + delta));
    if (next === carouselIndex) return;
    carouselIndex = next;
    sfx.play('hover');
    update(box.contains(document.activeElement));
  };

  update();
  const ro = new ResizeObserver(() => update());
  ro.observe(box);

  let dragStart: number | null = null;
  let dragged = false;
  on(box, 'pointerdown', (e: PointerEvent) => {
    if (e.button !== 0) return;
    dragStart = e.clientX;
    dragged = false;
  });
  on(window, 'pointermove', (e: PointerEvent) => {
    if (dragStart !== null && Math.abs(e.clientX - dragStart) > 10) dragged = true;
  });
  on(window, 'pointerup', (e: PointerEvent) => {
    if (dragStart === null) return;
    const delta = e.clientX - dragStart;
    dragStart = null;
    if (dragged && Math.abs(delta) > 40) move(delta < 0 ? 1 : -1);
  });

  let lastWheel = 0;
  on(
    box,
    'wheel',
    (e: WheelEvent) => {
      e.preventDefault();
      const now = Date.now();
      const delta = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
      if (now - lastWheel < 160 || Math.abs(delta) < 8) return;
      lastWheel = now;
      move(delta > 0 ? 1 : -1);
    },
    { passive: false }
  );

  cards.forEach((card) => {
    card.addEventListener('click', () => {
      if (dragged) {
        dragged = false;
        return;
      }
      const idx = Number(card.dataset.index || 0);
      if (idx === carouselIndex) activate(card);
      else {
        carouselIndex = idx;
        sfx.play('hover');
        update();
      }
    });
  });

  on(box, 'keydown', (e: KeyboardEvent) => {
    if (e.key === 'ArrowLeft') {
      e.preventDefault();
      move(-1);
    } else if (e.key === 'ArrowRight') {
      e.preventDefault();
      move(1);
    } else if (e.key === 'Home') {
      e.preventDefault();
      move(-count);
    } else if (e.key === 'End') {
      e.preventDefault();
      move(count);
    }
  });

  return () => ro.disconnect();
}
