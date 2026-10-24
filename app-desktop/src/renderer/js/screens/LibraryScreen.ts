import { store } from '../state.js';
import { sfx } from '../sfx.js';
import { openFactSheet } from '../components/FactSheet.js';
import { extractCoverTone } from '../coverTone.js';
import type { RomItem } from '../../../main/library/scanner.js';

let carouselIndex = 0;

function getConsoleSlug(consoleType: string): string {
  const map: Record<string, string> = {
    '3DS': 'three_ds',
    'PSP': 'psp',
    'DS': 'ds',
    'Switch': 'switch',
    'GameCube': 'gamecube',
    'Wii': 'wii',
    'PS2': 'ps2',
  };
  return map[consoleType] || consoleType.toLowerCase();
}

export function getConsoleFriendlyName(consoleType: string): string {
  const map: Record<string, string> = {
    '3DS': 'Nintendo 3DS',
    'DS': 'Nintendo DS',
    'PSP': 'PlayStation Portable',
    'PS2': 'PlayStation 2',
    'Wii': 'Nintendo Wii',
    'GameCube': 'Nintendo GameCube',
    'Switch': 'Nintendo Switch',
  };
  return map[consoleType] || consoleType;
}

export const EMUFII_PALETTE: [string, string][] = [
  ['#3FCFC0', '#0E9C8F'], // Teal.bright to Teal.deep
  ['#EE6FA3', '#C24B7E'], // Coral.bright to Coral.deep
  ['#6B72E0', '#8E93EC'], // Violet to VioletDark
  ['#1FA98B', '#0A4A44'], // GoodLight to Teal.ink
  ['#C98A12', '#5A1D3E'], // WarnLight to Coral.ink
  ['#5A8FD8', '#6B72E0'], // InfoLight to Violet
  ['#F793BC', '#5A1D3E'], // Coral.darkBright to Coral.ink
  ['#5CE0D2', '#0A4A44'], // Teal.darkBright to Teal.ink
  ['#8E93EC', '#5A1D3E'], // VioletDark to Coral.ink
  ['#C24B7E', '#0A4A44'], // Coral.deep to Teal.ink
  ['#0E9C8F', '#5A1D3E'], // Teal.deep to Coral.ink
];

export function paletteFor(seed: string): [string, string] {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) {
    hash = (hash * 31 + seed.charCodeAt(i)) | 0;
  }
  const idx = Math.abs(hash) % EMUFII_PALETTE.length;
  return EMUFII_PALETTE[idx];
}

export function shortLabel(displayName: string): string {
  const words = displayName.split(/[\s._-]+/).filter((w) => w.length > 0);
  if (words.length === 0) return '?';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0].slice(0, 1) + words[1].slice(0, 1)).toUpperCase();
}

export function getGridColumnCount(): number {
  if (typeof window === 'undefined') return 8;
  const gridWidth = Math.max(300, window.innerWidth - 64);
  return Math.max(3, Math.floor((gridWidth + 16) / 124));
}

function renderCompatBadge(rating?: string | null): string {
  const r = rating || 'perfect';
  if (r === 'perfect') {
    return `
      <div class="rom-compat-badge badge-perfect" title="Playable">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="20 6 9 17 4 12"/>
        </svg>
      </div>
    `;
  }
  if (r === 'partial') {
    return `
      <div class="rom-compat-badge badge-partial" title="Ingame / Partial">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="1"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
        </svg>
      </div>
    `;
  }
  if (r === 'broken') {
    return `
      <div class="rom-compat-badge badge-broken" title="Broken">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
          <line x1="18" y1="6" x2="6" y2="18"/>
          <line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </div>
    `;
  }
  return `
    <div class="rom-compat-badge badge-untested" title="Untested">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
        <path d="M5 12h14"/>
      </svg>
    </div>
  `;
}

export function renderLibraryScreen(container: HTMLElement) {
  if ((container as any)._libraryCleanup) {
    (container as any)._libraryCleanup();
    (container as any)._libraryCleanup = null;
  }
  const state = store.get();
  const emuApi = (window as any).emufii;
  const isLight =
    state.settings.theme === 'day' ||
    state.settings.theme === 'light' ||
    (state.settings.theme === 'system' && window.matchMedia('(prefers-color-scheme: light)').matches);
  let cleanup: (() => void) | null = null;

  // Filter ROMs
  let filtered = state.roms.filter((r) => {
    if (state.settings.hiddenRoms.includes(r.id)) return false;
    const nameLow = (r.fileName || r.displayName || '').toLowerCase();
    if (nameLow.startsWith('photo') || nameLow.includes('photos-') || nameLow.includes('screenshot')) return false;
    const romConsole = (r.console as string) === 'GBA' ? 'DS' : r.console;
    if (state.settings.hiddenConsoles && state.settings.hiddenConsoles.includes(romConsole)) return false;
    if (state.settings.hideIncompatible && r.titleId && state.compatDb[r.titleId]?.playable === false) {
      return false;
    }
    if (state.consoleFilter !== 'ALL' && romConsole !== state.consoleFilter) return false;
    return true;
  });

  // Sort ROMs
  filtered.sort((a, b) => {
    const titleA = state.settings.customTitles[a.id] || a.displayName;
    const titleB = state.settings.customTitles[b.id] || b.displayName;
    if (state.settings.librarySort === 'az') return titleA.localeCompare(titleB);
    if (state.settings.librarySort === 'date') return b.addedAt - a.addedAt;
    if (state.settings.librarySort === 'console') {
      const cA = (a.console as string) === 'GBA' ? 'DS' : a.console;
      const cB = (b.console as string) === 'GBA' ? 'DS' : b.console;
      return cA.localeCompare(cB);
    }
    return 0;
  });

  const shouldShowFolders =
    state.consoleFilter === 'ALL' &&
    !state.searchQuery.trim();

  let totalItems = filtered.length;
  if (shouldShowFolders) {
    const supportedWebps = ['ps2', 'ds', 'three_ds', 'wii', 'psp', 'switch', 'gamecube'];
    const groupCount = state.roms.reduce((acc, r) => {
      const nameLow = (r.fileName || r.displayName || '').toLowerCase();
      if (nameLow.startsWith('photo') || nameLow.includes('photos-') || nameLow.includes('screenshot')) return acc;
      if (!state.settings.hiddenRoms.includes(r.id)) {
        const c = (r.console as string) === 'GBA' ? 'DS' : r.console;
        if (state.settings.hiddenConsoles?.includes(c)) return acc;
        if (supportedWebps.includes(getConsoleSlug(c))) {
          acc.add(c);
        }
      }
      return acc;
    }, new Set<string>()).size;
    totalItems = groupCount;
  }

  if (carouselIndex >= totalItems) {
    carouselIndex = Math.max(0, totalItems - 1);
  }

  const existingViewport = container.querySelector('#libraryViewport');
  if (!existingViewport) {
    container.innerHTML = `
      <!-- Library Toolbar: View Controls on Right -->
      <div class="library-toolbar" style="justify-content: flex-end;">
        <div class="view-controls">
          <button class="icon-btn ${state.settings.libraryLayout === 'grid' ? 'active' : ''}" id="btnLayoutGrid" title="Grid View">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/></svg>
          </button>
          <button class="icon-btn ${state.settings.libraryLayout === 'carousel' ? 'active' : ''}" id="btnLayoutCarousel" title="Carousel View">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><rect x="7" y="3" width="10" height="18" rx="2"/><rect x="2" y="6" width="3" height="12" rx="1" opacity="0.4"/><rect x="19" y="6" width="3" height="12" rx="1" opacity="0.4"/></svg>
          </button>
          <button class="icon-btn ${state.settings.libraryLayout === 'list' ? 'active' : ''}" id="btnLayoutList" title="List View">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><rect x="3" y="4" width="18" height="4" rx="1.5"/><rect x="3" y="10" width="18" height="4" rx="1.5"/><rect x="3" y="16" width="18" height="4" rx="1.5"/></svg>
          </button>
        </div>
      </div>

      <!-- Main Content Area -->
      <div class="library-viewport" id="libraryViewport">
        ${renderViewport(state, filtered, isLight)}
      </div>
    `;

    // Layout switcher
    container.querySelector('#btnLayoutGrid')?.addEventListener('click', (e) => {
      sfx.play('click');
      (e.currentTarget as HTMLElement).blur();
      emuApi.config.updateSettings({ libraryLayout: 'grid' });
      const currentSettings = store.get().settings;
      store.set({ settings: { ...currentSettings, libraryLayout: 'grid' } });
    });

    container.querySelector('#btnLayoutCarousel')?.addEventListener('click', (e) => {
      sfx.play('click');
      (e.currentTarget as HTMLElement).blur();
      emuApi.config.updateSettings({ libraryLayout: 'carousel' });
      const currentSettings = store.get().settings;
      store.set({ settings: { ...currentSettings, libraryLayout: 'carousel' } });
    });

    container.querySelector('#btnLayoutList')?.addEventListener('click', (e) => {
      sfx.play('click');
      (e.currentTarget as HTMLElement).blur();
      emuApi.config.updateSettings({ libraryLayout: 'list' });
      const currentSettings = store.get().settings;
      store.set({ settings: { ...currentSettings, libraryLayout: 'list' } });
    });
  } else {
    // Toolbar already exists: update view buttons and viewport
    container.querySelector('#btnLayoutGrid')?.classList.toggle('active', state.settings.libraryLayout === 'grid');
    container.querySelector('#btnLayoutCarousel')?.classList.toggle('active', state.settings.libraryLayout === 'carousel');
    container.querySelector('#btnLayoutList')?.classList.toggle('active', state.settings.libraryLayout === 'list');

    existingViewport.innerHTML = renderViewport(state, filtered, isLight);
  }

  // Attach viewport listeners (Folder tiles, ROMs, Carousel gestures & wheel)
  const cleanups: (() => void)[] = [];

  // Console folder tile clicks
  container.querySelectorAll<HTMLElement>('.folder-tile').forEach((tile) => {
    tile.addEventListener('click', (e) => {
      sfx.play('click');
      const targetConsole = (e.currentTarget as HTMLElement).dataset.console;
      if (targetConsole) {
        carouselIndex = 0;
        store.set({ consoleFilter: targetConsole });
      }
    });
  });

  // Back to All Consoles button
  container.querySelector('#btnBackToConsoles')?.addEventListener('click', () => {
    sfx.play('click');
    carouselIndex = 0;
    store.set({ consoleFilter: 'ALL' });
  });

  // Tile and List clicks for ROMs
  container.querySelectorAll<HTMLElement>('.rom-cell, .list-row:not(.folder-tile)').forEach((tile) => {
    tile.addEventListener('click', (e) => {
      const romId = (e.currentTarget as HTMLElement).dataset.romId;
      const rom = state.roms.find((r: RomItem) => r.id === romId);
      if (rom) {
        sfx.play('click');
        openFactSheet(rom);
      }
    });
  });

  // Carousel Layout Interactions
  if (state.settings.libraryLayout === 'carousel' && totalItems > 0) {
    const track = container.querySelector<HTMLElement>('#carouselTrack');
    const cards = container.querySelectorAll<HTMLElement>('.carousel-card');
    const carouselContainer = container.querySelector<HTMLElement>('.carousel-container');

    const updateCarouselView = () => {
      if (!track || !carouselContainer) return;
      cards.forEach((card, idx) => {
        const isFolder = card.classList.contains('folder-tile');
        if (idx === carouselIndex) {
          card.className = isFolder
            ? 'carousel-card folder-tile active'
            : 'carousel-card active';
        } else {
          card.className = isFolder
            ? 'carousel-card folder-tile neighbor'
            : 'carousel-card neighbor';
        }
      });

      if (cards.length === 1) {
        track.style.margin = '0 auto';
        track.style.transform = 'none';
      } else {
        track.style.margin = '';
        const containerWidth = carouselContainer.clientWidth || window.innerWidth;
        const cardWidth = 260;
        const cardGap = 28;
        const step = cardWidth + cardGap;
        const centerOffset = containerWidth / 2 - cardWidth / 2 - carouselIndex * step;
        track.style.transform = `translateX(${centerOffset}px)`;
      }
    };

    updateCarouselView();
    window.addEventListener('resize', updateCarouselView);
    cleanups.push(() => {
      window.removeEventListener('resize', updateCarouselView);
    });

    if (window.ResizeObserver && carouselContainer) {
      const ro = new ResizeObserver(() => {
        updateCarouselView();
      });
      ro.observe(carouselContainer);
      cleanups.push(() => {
        ro.disconnect();
      });
    }

    let isDragging = false;
    let startX = 0;
    let moved = false;

    if (carouselContainer) {
      // 1. Slide / Swipe Gesture
      const onPointerDown = (e: PointerEvent) => {
        if (e.button !== 0 && e.pointerType === 'mouse') return;
        isDragging = true;
        moved = false;
        startX = e.clientX;
      };

      const onPointerMove = (e: PointerEvent) => {
        if (!isDragging) return;
        if (Math.abs(e.clientX - startX) > 10) {
          moved = true;
        }
      };

      const onPointerUp = (e: PointerEvent) => {
        if (!isDragging) return;
        isDragging = false;
        const deltaX = e.clientX - startX;
        if (moved && Math.abs(deltaX) > 36) {
          if (deltaX < -36 && carouselIndex < cards.length - 1) {
            sfx.play('hover');
            carouselIndex++;
            updateCarouselView();
          } else if (deltaX > 36 && carouselIndex > 0) {
            sfx.play('hover');
            carouselIndex--;
            updateCarouselView();
          }
        }
      };

      carouselContainer.addEventListener('pointerdown', onPointerDown);
      window.addEventListener('pointermove', onPointerMove);
      window.addEventListener('pointerup', onPointerUp);

      // 2. Mouse Wheel / Middle Scroll
      let lastWheelTime = 0;
      const onWheel = (e: WheelEvent) => {
        e.preventDefault();
        const now = Date.now();
        if (now - lastWheelTime < 180) return;

        const delta = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
        if (Math.abs(delta) < 8) return;

        if (delta > 0 && carouselIndex < cards.length - 1) {
          lastWheelTime = now;
          sfx.play('hover');
          carouselIndex++;
          updateCarouselView();
        } else if (delta < 0 && carouselIndex > 0) {
          lastWheelTime = now;
          sfx.play('hover');
          carouselIndex--;
          updateCarouselView();
        }
      };

      carouselContainer.addEventListener('wheel', onWheel, { passive: false });

      cleanups.push(() => {
        carouselContainer.removeEventListener('pointerdown', onPointerDown);
        window.removeEventListener('pointermove', onPointerMove);
        window.removeEventListener('pointerup', onPointerUp);
        carouselContainer.removeEventListener('wheel', onWheel);
      });
    }

    // 3. Card clicks (prevented if dragged)
    cards.forEach((card) => {
      card.addEventListener('click', (e) => {
        if (moved) {
          moved = false;
          return;
        }
        const clickedIdx = parseInt((e.currentTarget as HTMLElement).dataset.index || '0', 10);
        if (clickedIdx === carouselIndex) {
          const targetConsole = (e.currentTarget as HTMLElement).dataset.console;
          if (targetConsole) {
            sfx.play('click');
            store.set({ consoleFilter: targetConsole });
            return;
          }
          const romId = (e.currentTarget as HTMLElement).dataset.romId;
          const rom = state.roms.find((r: RomItem) => r.id === romId);
          if (rom) {
            sfx.play('click');
            openFactSheet(rom);
          }
        } else {
          sfx.play('hover');
          carouselIndex = clickedIdx;
          updateCarouselView();
        }
      });
    });

    // 4. Keyboard Arrow Keys
    const onCarouselKey = (e: KeyboardEvent) => {
      if (store.get().currentScreen !== 'library') return;
      if (state.settings.libraryLayout !== 'carousel') return;
      if (document.activeElement?.tagName === 'INPUT') return;

      if (e.key === 'ArrowLeft' && carouselIndex > 0) {
        e.preventDefault();
        sfx.play('hover');
        carouselIndex--;
        updateCarouselView();
      } else if (e.key === 'ArrowRight' && carouselIndex < cards.length - 1) {
        e.preventDefault();
        sfx.play('hover');
        carouselIndex++;
        updateCarouselView();
      } else if (e.key === 'Enter') {
        const activeCard = cards[carouselIndex];
        const targetConsole = activeCard?.dataset.console;
        if (targetConsole) {
          sfx.play('click');
          store.set({ consoleFilter: targetConsole });
          return;
        }
        const activeRom = filtered[carouselIndex];
        if (activeRom) {
          sfx.play('click');
          openFactSheet(activeRom);
        }
      }
    };
    window.addEventListener('keydown', onCarouselKey);
    cleanups.push(() => {
      window.removeEventListener('keydown', onCarouselKey);
    });
  }

  // Global Escape / Backspace key handler to exit console folder back to all consoles
  const onFolderNavKey = (e: KeyboardEvent) => {
    if (store.get().currentScreen !== 'library') return;
    if (document.activeElement?.tagName === 'INPUT' || document.activeElement?.tagName === 'TEXTAREA') return;
    if (e.key === 'Escape' || e.key === 'Backspace') {
      const current = store.get().consoleFilter;
      if (current !== 'ALL') {
        e.preventDefault();
        sfx.play('click');
        carouselIndex = 0;
        store.set({ consoleFilter: 'ALL' });
      }
    }
  };
  window.addEventListener('keydown', onFolderNavKey);
  cleanups.push(() => {
    window.removeEventListener('keydown', onFolderNavKey);
  });

  // Empty state buttons
  container.querySelector('#btnAddRomDir')?.addEventListener('click', async () => {
    sfx.play('click');
    const selected = await emuApi.dialog.selectDirectory();
    if (selected) {
      await emuApi.library.addDirectory(selected);
      const scanned = await emuApi.library.scan();
      const updatedDirs = [...(store.get().settings.romDirectories || []), selected];
      store.set({ roms: scanned, settings: { ...store.get().settings, romDirectories: updatedDirs } });
    }
  });

  container.querySelector('#btnAddRomFile')?.addEventListener('click', async () => {
    sfx.play('click');
    const selected = await emuApi.dialog.selectRomFiles();
    if (selected && selected.length > 0) {
      const dirs = store.get().settings.romDirectories || [];
      const targetDir = dirs[0] || (await emuApi.library.getSpecialRomDirectory())?.path;
      await emuApi.library.importRomFiles(selected, targetDir);
      const scanned = await emuApi.library.scan();
      store.set({ roms: scanned });
    }
  });

  // Responsive resize handler that adjusts Grid empty slots when window aspect ratio/resolution changes
  let resizeRaf: number | null = null;
  const onScreenResize = () => {
    if (store.get().currentScreen !== 'library') return;
    if (resizeRaf) cancelAnimationFrame(resizeRaf);
    resizeRaf = requestAnimationFrame(() => {
      const curState = store.get();
      if (curState.settings.libraryLayout === 'grid') {
        const gridEl = container.querySelector<HTMLElement>('.roms-grid');
        const oldCols = parseInt(gridEl?.dataset.cols || '0', 10);
        const newCols = getGridColumnCount();
        if (oldCols !== newCols) {
          renderLibraryScreen(container);
        }
      }
    });
  };
  window.addEventListener('resize', onScreenResize);
  cleanups.push(() => {
    window.removeEventListener('resize', onScreenResize);
    if (resizeRaf) cancelAnimationFrame(resizeRaf);
  });

  const cleanupAll = () => {
    cleanups.forEach((fn) => fn());
  };
  (container as any)._libraryCleanup = cleanupAll;
  return cleanupAll;
}

function renderEmptyState(): string {
  return `
    <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 340px; gap: 18px; text-align: center;">
      <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="opacity: 0.35;"><rect x="3" y="3" width="18" height="18" rx="4"/><path d="M9 3v18M15 3v18M3 9h18M3 15h18"/></svg>
      <div>
        <h2 style="font-size: 20px; font-weight: 800; margin-bottom: 6px;">No ROMs Found</h2>
        <p style="font-size: 14px; color: var(--ink-secondary); max-width: 440px; line-height: 1.5;">
          Add individual game files (.gba, .3ds, .iso, .rvz, .nds, .nsp) or select a games folder to populate your library.
        </p>
      </div>
      <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap; justify-content: center;">
        <button class="btn-primary plate-button" id="btnAddRomFile" style="padding: 0 24px; width: auto; height: 42px;">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="15" y2="15"/></svg>
          Add ROM File
        </button>
        <button class="btn-secondary plate-button" id="btnAddRomDir" style="padding: 0 24px; width: auto; height: 42px;">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
          Add ROM Folder
        </button>
      </div>
    </div>
  `;
}

function renderViewport(state: any, filtered: RomItem[], isLight: boolean): string {
  if (state.roms.length === 0) {
    return renderEmptyState();
  }

  const shouldShowFolders =
    state.consoleFilter === 'ALL' &&
    !state.searchQuery.trim();

  if (shouldShowFolders) {
    const supportedWebps = ['ps2', 'ds', 'three_ds', 'wii', 'psp', 'switch', 'gamecube'];
    const consoleGroups: { console: string; roms: RomItem[] }[] = [];
    const groupMap: Record<string, RomItem[]> = {};
    for (const r of state.roms) {
      if (state.settings.hiddenRoms?.includes(r.id)) continue;
      const nameLow = (r.fileName || r.displayName || '').toLowerCase();
      if (nameLow.startsWith('photo') || nameLow.includes('photos-') || nameLow.includes('screenshot')) continue;
      const c = (r.console as string) === 'GBA' ? 'DS' : r.console;
      if (!groupMap[c]) {
        groupMap[c] = [];
        consoleGroups.push({ console: c, roms: groupMap[c] });
      }
      groupMap[c].push(r);
    }

    const validGroups = consoleGroups.filter(
      (g) => supportedWebps.includes(getConsoleSlug(g.console)) && !state.settings.hiddenConsoles?.includes(g.console)
    );

    if (validGroups.length === 0) {
      return renderEmptyState();
    }

    const consoleOrder = ['3DS', 'PSP', 'DS', 'Switch', 'GameCube', 'Wii', 'PS2'];
    validGroups.sort((a, b) => {
      const idxA = consoleOrder.indexOf(a.console);
      const idxB = consoleOrder.indexOf(b.console);
      return (idxA >= 0 ? idxA : 99) - (idxB >= 0 ? idxB : 99);
    });

    if (state.settings.libraryLayout === 'carousel') {
      return renderFolderCarousel(validGroups, isLight);
    }
    if (state.settings.libraryLayout === 'list') {
      return renderFolderList(validGroups, isLight);
    }
    return renderFolderGrid(validGroups, isLight);
  }

  if (filtered.length === 0) {
    return `
      <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 260px; gap: 12px; text-align: center;">
        <h3 style="font-size: 16px; font-weight: 800; color: var(--ink-secondary);">No games found in ${state.consoleFilter === 'ALL' ? 'this search' : state.consoleFilter}</h3>
        ${
          state.consoleFilter !== 'ALL'
            ? `<button class="plate-button btn-secondary" id="btnBackToConsoles" style="height: 32px; padding: 0 14px; font-size: 12px; border-radius: var(--radius-pill); cursor: pointer;">
                 &larr; Back to Library
               </button>`
            : ''
        }
      </div>
    `;
  }

  return renderContent(filtered, state.settings.libraryLayout, isLight);
}

function renderFolderGrid(groups: { console: string; roms: RomItem[] }[], isLight: boolean): string {
  const cols = getGridColumnCount();
  const minRows = 2;
  const targetSlots = Math.max(minRows * cols, Math.ceil(groups.length / cols) * cols);
  const emptyCount = Math.max(0, targetSlots - groups.length);
  const emptySlotsHtml = Array.from({ length: emptyCount })
    .map(() => '<div class="empty-slot" aria-hidden="true"></div>')
    .join('');

  return `
    <div class="roms-grid" data-cols="${cols}">
      ${groups
        .map((g) => {
          const [c1, c2] = paletteFor(g.console);
          const slug = getConsoleSlug(g.console);
          const theme = isLight ? 'light' : 'dark';
          const supportedWebps = ['ps2', 'ds', 'three_ds', 'wii', 'psp', 'switch', 'gamecube'];
          const hasArtwork = supportedWebps.includes(slug);
          const count = g.roms.length;
          const countLabel = `${count} ${count === 1 ? 'game' : 'games'}`;

          return `
            <div class="folder-tile" data-console="${g.console}" tabindex="0" title="${getConsoleFriendlyName(g.console)}">
              <div class="folder-artwork-wrap" style="background: linear-gradient(135deg, ${c1}, ${c2});">
                ${
                  hasArtwork
                    ? `<img src="assets/consoles/console_${slug}_${theme}.webp" class="folder-art-img" alt="${g.console}" onerror="this.style.display='none'; const lbl = this.nextElementSibling; if (lbl) lbl.style.display='block';">
                       <div class="folder-console-label" style="display: none;">${g.console}</div>`
                    : `<div class="folder-console-label">${g.console}</div>`
                }
                <div class="folder-count-pill">${countLabel}</div>
              </div>
            </div>
          `;
        })
        .join('')}
      ${emptySlotsHtml}
    </div>
  `;
}

function renderFolderCarousel(groups: { console: string; roms: RomItem[] }[], isLight: boolean): string {
  return `
    <div class="carousel-container">
      <div class="roms-carousel-track" id="carouselTrack">
        ${groups
          .map((g, idx) => {
            const [c1, c2] = paletteFor(g.console);
            const slug = getConsoleSlug(g.console);
            const theme = isLight ? 'light' : 'dark';
            const supportedWebps = ['ps2', 'ds', 'three_ds', 'wii', 'psp', 'switch', 'gamecube'];
            const hasArtwork = supportedWebps.includes(slug);
            const count = g.roms.length;
            const countLabel = `${count} ${count === 1 ? 'game' : 'games'}`;

            return `
              <div class="carousel-card folder-tile ${idx === carouselIndex ? 'active' : 'neighbor'}" data-console="${g.console}" data-index="${idx}" tabindex="0" title="${getConsoleFriendlyName(g.console)}">
                <div class="carousel-artwork-wrap folder-artwork-wrap" style="background: linear-gradient(135deg, ${c1}, ${c2});">
                  ${
                    hasArtwork
                      ? `<img src="assets/consoles/console_${slug}_${theme}.webp" class="folder-art-img" alt="${g.console}" onerror="this.style.display='none'; const lbl = this.nextElementSibling; if (lbl) lbl.style.display='block';">
                         <div class="folder-console-label" style="display: none;">${g.console}</div>`
                      : `<div class="folder-console-label">${g.console}</div>`
                  }
                  <div class="folder-count-pill">${countLabel}</div>
                </div>
              </div>
            `;
          })
          .join('')}
      </div>
    </div>
  `;
}

function renderFolderList(groups: { console: string; roms: RomItem[] }[], isLight: boolean): string {
  return `
    <div class="roms-list">
      ${groups
        .map((g) => {
          const [c1, c2] = paletteFor(g.console);
          const slug = getConsoleSlug(g.console);
          const theme = isLight ? 'light' : 'dark';
          const supportedWebps = ['ps2', 'ds', 'three_ds', 'wii', 'psp', 'switch', 'gamecube'];
          const hasArtwork = supportedWebps.includes(slug);
          const count = g.roms.length;
          const countLabel = `${count} ${count === 1 ? 'game' : 'games'}`;
          return `
            <div class="list-row folder-tile" data-console="${g.console}" tabindex="0">
              <div style="display: flex; align-items: center; gap: 16px;">
                <div class="list-row-art" style="width: 54px; height: 54px; border-radius: 16px; overflow: hidden; position: relative; flex-shrink: 0; background: linear-gradient(135deg, ${c1}, ${c2}); border: 2px solid rgba(255, 255, 255, 0.08); display: flex; align-items: center; justify-content: center;">
                  ${
                    hasArtwork
                      ? `<img src="assets/consoles/console_${slug}_${theme}.webp" style="width: 100%; height: 100%; object-fit: cover;" alt="${g.console}" onerror="this.style.display='none'; const p = this.nextElementSibling; if (p) p.style.display='block';">
                         <span style="display: none; font-weight: 800; font-size: 13px; color: #fff;">${g.console}</span>`
                      : `<span style="font-weight: 800; font-size: 13px; color: #fff;">${g.console}</span>`
                  }
                </div>
                <div>
                  <div style="font-weight: 700; font-size: 16px; color: var(--ink-primary);">${getConsoleFriendlyName(g.console)}</div>
                  <div style="font-size: 12px; color: var(--ink-secondary); margin-top: 2px;">${countLabel}</div>
                </div>
              </div>
              <div style="display: flex; align-items: center; gap: 14px; color: var(--ink-secondary);">
                <span class="rom-tile-badge" style="position: static;">${g.console}</span>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
              </div>
            </div>
          `;
        })
        .join('')}
    </div>
  `;
}

function renderContent(roms: RomItem[], layout: string, isLight: boolean): string {
  if (layout === 'carousel') {
    return `
      <div class="carousel-container">
        <div class="roms-carousel-track" id="carouselTrack">
          ${roms
            .map(
              (r, idx) => {
                const [c1, c2] = paletteFor(r.displayName);
                const initials = shortLabel(r.displayName);
                return `
            <div class="carousel-card ${idx === carouselIndex ? 'active' : 'neighbor'}" data-rom-id="${r.id}" data-index="${idx}" tabindex="0">
              <div class="carousel-artwork-wrap">
                ${
                  r.artworkUrl
                    ? `<img src="${r.artworkUrl}" data-fallback="${r.fallbackArtworkUrl || ''}" class="rom-artwork" alt="${r.displayName}" loading="lazy" onerror="if (this.dataset.fallback && this.src !== this.dataset.fallback) { this.src = this.dataset.fallback; } else { this.style.display='none'; const p = this.nextElementSibling; if (p) p.style.display='flex'; }">`
                    : ''
                }
                <div class="rom-placeholder-art" style="background: linear-gradient(135deg, ${c1}, ${c2}); ${r.artworkUrl ? 'display: none;' : 'display: flex;'}">
                  <span class="rom-placeholder-initials" style="font-size: 42px;">${initials}</span>
                </div>
                <span class="rom-tile-badge">${r.console}</span>
                ${renderCompatBadge(r.compatRating)}
              </div>
              <div class="carousel-title">${r.displayName}</div>
            </div>
          `;
              }
            )
            .join('')}
        </div>
      </div>
    `;
  }

  if (layout === 'list') {
    return `
      <div class="roms-list">
        ${roms
          .map(
            (r) => {
              const [c1, c2] = paletteFor(r.displayName);
              const initials = shortLabel(r.displayName);
              return `
          <div class="list-row" data-rom-id="${r.id}" tabindex="0">
            <div style="display: flex; align-items: center; gap: 16px;">
              <div class="list-row-art" style="width: 54px; height: 54px; border-radius: 16px; overflow: hidden; position: relative; flex-shrink: 0; background: rgba(255, 255, 255, 0.04); border: 2px solid rgba(255, 255, 255, 0.08); display: flex; align-items: center; justify-content: center; padding: 4px; box-sizing: border-box;">
                ${
                  r.artworkUrl
                    ? `<img src="${r.artworkUrl}" data-fallback="${r.fallbackArtworkUrl || ''}" style="width: 100%; height: 100%; object-fit: contain;" alt="${r.displayName}" loading="lazy" onerror="if (this.dataset.fallback && this.src !== this.dataset.fallback) { this.src = this.dataset.fallback; } else { this.style.display='none'; const p = this.nextElementSibling; if (p) p.style.display='flex'; }">`
                    : ''
                }
                <div class="rom-placeholder-art small" style="background: linear-gradient(135deg, ${c1}, ${c2}); ${r.artworkUrl ? 'display: none;' : 'display: flex;'} width: 100%; height: 100%; border-radius: 12px;">
                  <span class="rom-placeholder-initials small">${initials}</span>
                </div>
              </div>
              <div>
                <div style="font-weight: 700; font-size: 16px; color: var(--ink-primary);">${r.displayName}</div>
              </div>
            </div>
            <div style="display: flex; align-items: center; gap: 12px;">
              ${renderCompatBadge(r.compatRating)}
              <span class="rom-tile-badge" style="position: static;">${r.console}</span>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="color: var(--ink-secondary);"><polyline points="9 18 15 12 9 6"/></svg>
            </div>
          </div>
        `;
            }
          )
          .join('')}
      </div>
    `;
  }

  // Default Grid Layout with Empty Sockets
  const cols = getGridColumnCount();
  const minRows = 2;
  const targetSlots = Math.max(minRows * cols, Math.ceil(roms.length / cols) * cols);
  // Keep games centered when viewing a specific console folder or when few titles exist
  const currentFilter = store.get().consoleFilter;
  const isFilteredConsole = currentFilter && currentFilter !== 'ALL';
  const emptyCount = (isFilteredConsole || roms.length < cols) ? 0 : Math.max(0, targetSlots - roms.length);
  const emptySlotsHtml = Array.from({ length: emptyCount })
    .map(() => '<div class="empty-slot" aria-hidden="true"></div>')
    .join('');

  return `
    <div class="roms-grid" data-cols="${cols}">
      ${roms
        .map(
          (r) => {
            const [c1, c2] = paletteFor(r.displayName);
            const initials = shortLabel(r.displayName);
            return `
        <div class="rom-cell" data-rom-id="${r.id}" tabindex="0">
          <div class="rom-tile">
            <div class="rom-artwork-wrap">
              ${
                r.artworkUrl
                  ? `<img src="${r.artworkUrl}" data-fallback="${r.fallbackArtworkUrl || ''}" class="rom-artwork" alt="${r.displayName}" loading="lazy" onerror="if (this.dataset.fallback && this.src !== this.dataset.fallback) { this.src = this.dataset.fallback; } else { this.style.display='none'; const p = this.nextElementSibling; if (p) p.style.display='flex'; }">`
                  : ''
              }
              <div class="rom-placeholder-art" style="background: linear-gradient(135deg, ${c1}, ${c2}); ${r.artworkUrl ? 'display: none;' : 'display: flex;'}">
                <span class="rom-placeholder-initials">${initials}</span>
              </div>
              <span class="rom-tile-badge">${r.console}</span>
              ${renderCompatBadge(r.compatRating)}
            </div>
          </div>
          <div class="rom-title" title="${r.displayName}">${r.displayName}</div>
        </div>
      `;
          }
        )
        .join('')}
      ${emptySlotsHtml}
    </div>
  `;
}
