import { TrayBackdrop } from './js/components/TrayBackdrop.js';
import { sfx } from './js/sfx.js';

export type InstallerStage = 'setup' | 'installing' | 'complete';
export type InstallerLang = 'es' | 'en';

interface InstallerState {
  stage: InstallerStage;
  lang: InstallerLang;
  installPath: string;
  createDesktopShortcut: boolean;
  createStartMenuShortcut: boolean;
  autoProvisionKeys: boolean;
  registerProtocol: boolean;
  launchAfter: boolean;
  progressPercent: number;
  currentStepIndex: number;
}

const emuApi = (window as any).emufii;

const defaultWinPath = 'C:\\Users\\Default\\AppData\\Local\\Programs\\Emufii';
const isWindows = typeof navigator !== 'undefined' && /win/i.test(navigator.userAgent || '');

const state: InstallerState = {
  stage: 'setup',
  lang: 'es',
  installPath: 'C:\\Program Files\\Emufii',
  createDesktopShortcut: true,
  createStartMenuShortcut: true,
  autoProvisionKeys: true,
  registerProtocol: true,
  launchAfter: true,
  progressPercent: 0,
  currentStepIndex: 0,
};

const I18N = {
  es: {
    titlebar: 'Instalador de Emufii',
    heroTitle: 'Instalar Emufii',
    heroSub: 'Juega tus emuladores favoritos en línea con amigos mediante un simple código de 6 caracteres.',
    version: 'v2.0.4 • 64-bit',
    pathTitle: 'Directorio de Instalación',
    spaceStats: 'Requerido: ~280 MB',
    browse: 'Examinar...',
    optionsTitle: 'Opciones de Instalación',
    optDesktop: 'Crear acceso directo en el Escritorio',
    optDesktopSub: 'Inicia tus juegos con un solo clic desde la pantalla principal.',
    optStartMenu: 'Anclar al Menú Inicio',
    optStartMenuSub: 'Registra Emufii en las aplicaciones de tu sistema operativo.',
    optKeys: 'Auto-configurar perfiles de emuladores a 60 FPS',
    optKeysSub: 'Configuración automática de gráficos, shaders y sincronización de cuadros.',
    optProtocol: 'Asociar protocolo de enlace web (emufii://)',
    optProtocolSub: 'Permite unirte a salas multijugador con un clic desde el navegador.',
    cancel: 'Cancelar',
    installNow: 'Instalar Ahora',
    installingTitle: 'Instalando Emufii...',
    step1: 'Verificando dependencias del sistema y controladores',
    step2: 'Descomprimiendo binarios del núcleo y librerías 64-bit',
    step3: 'Configurando perfiles optimizados a 60 FPS para emuladores',
    step4: 'Aprovisionando túnel de red WireGuard y adaptadores virtuales',
    step5: 'Creando accesos directos y registrando asociaciones',
    completeTitle: '¡Instalación Completada!',
    completeSub: 'Emufii se ha instalado con éxito en tu equipo. Todas las configuraciones a 60 FPS y el motor de red están listos.',
    launchCheck: 'Iniciar Emufii ahora',
    finishBtn: 'Iniciar Emufii',
    closeBtn: 'Cerrar',
  },
  en: {
    titlebar: 'Emufii Setup',
    heroTitle: 'Install Emufii',
    heroSub: 'Play your favorite emulators online with friends using a simple 6-character code.',
    version: 'v2.0.4 • 64-bit',
    pathTitle: 'Installation Directory',
    spaceStats: 'Required: ~280 MB',
    browse: 'Browse...',
    optionsTitle: 'Installation Options',
    optDesktop: 'Create Desktop shortcut',
    optDesktopSub: 'Launch your sessions quickly from your main desktop.',
    optStartMenu: 'Add to Start Menu',
    optStartMenuSub: 'Registers Emufii into your system applications list.',
    optKeys: 'Auto-configure 60 FPS emulator profiles',
    optKeysSub: 'Automatic configuration of graphics backends, shaders & frame pacing.',
    optProtocol: 'Associate web protocol (emufii://)',
    optProtocolSub: 'Join multiplayer rooms with 1-click links directly from the browser.',
    cancel: 'Cancel',
    installNow: 'Install Now',
    installingTitle: 'Installing Emufii...',
    step1: 'Verifying system dependencies and drivers',
    step2: 'Extracting core binaries and 64-bit runtime libraries',
    step3: 'Configuring 60 FPS emulator profiles and shaders',
    step4: 'Provisioning low-latency WireGuard network tunnel',
    step5: 'Creating shortcuts and registering file associations',
    completeTitle: 'Installation Complete!',
    completeSub: 'Emufii has been successfully installed. All 60 FPS emulator profiles and netplay services are ready.',
    launchCheck: 'Launch Emufii now',
    finishBtn: 'Launch Emufii',
    closeBtn: 'Close',
  },
};

let trayBackdrop: TrayBackdrop | null = null;

function t(key: keyof typeof I18N['es']): string {
  return I18N[state.lang][key] || I18N['es'][key] || '';
}

export function initInstaller() {
  // 1. Initialize Backdrop Canvas
  const canvas = document.getElementById('installerBackdropCanvas') as HTMLCanvasElement;
  if (canvas) {
    try {
      trayBackdrop = new TrayBackdrop(canvas);
    } catch (e) {
      console.warn('Backdrop canvas init skipped:', e);
    }
  }

  // 2. Setup Titlebar Controls
  document.getElementById('btnInstallerMin')?.addEventListener('click', () => {
    sfx.play('click');
    emuApi?.window?.minimize();
  });

  document.getElementById('btnInstallerClose')?.addEventListener('click', () => {
    sfx.play('click');
    if (emuApi?.window?.close) {
      emuApi.window.close();
    } else {
      window.close();
    }
  });

  // 3. Language Toggle
  const btnEs = document.getElementById('btnLangEs');
  const btnEn = document.getElementById('btnLangEn');

  const setLang = (newLang: InstallerLang) => {
    sfx.play('toggle');
    state.lang = newLang;
    if (newLang === 'es') {
      btnEs?.classList.add('active');
      btnEn?.classList.remove('active');
    } else {
      btnEn?.classList.add('active');
      btnEs?.classList.remove('active');
    }
    const heading = document.getElementById('titlebarHeading');
    if (heading) heading.textContent = t('titlebar');
    renderCurrentStage();
  };

  btnEs?.addEventListener('click', () => setLang('es'));
  btnEn?.addEventListener('click', () => setLang('en'));

  // 4. Initial Render
  renderCurrentStage();
}

function renderCurrentStage() {
  const main = document.getElementById('installerMain');
  const footer = document.getElementById('installerFooter');
  if (!main || !footer) return;

  switch (state.stage) {
    case 'setup':
      renderSetupStage(main, footer);
      break;
    case 'installing':
      renderInstallingStage(main, footer);
      break;
    case 'complete':
      renderCompleteStage(main, footer);
      break;
  }
}

// Stage 1: Setup and options
function renderSetupStage(main: HTMLElement, footer: HTMLElement) {
  main.innerHTML = `
    <div class="installer-hero">
      <div class="installer-logo-wrapper">
        <div class="installer-logo-glow"></div>
        <img src="./assets/logo.png" alt="Emufii" class="installer-logo-img">
      </div>
      <div class="installer-hero-text">
        <h1>
          ${t('heroTitle')}
          <span class="installer-version-badge">${t('version')}</span>
        </h1>
        <p>${t('heroSub')}</p>
      </div>
    </div>

    <!-- Installation Directory Card -->
    <div class="installer-card">
      <div class="installer-card-header">
        <span class="installer-card-title">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="var(--accent-bright)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
          ${t('pathTitle')}
        </span>
        <span class="installer-card-sub">${t('spaceStats')}</span>
      </div>
      <div class="installer-path-box">
        <input type="text" id="inputInstallPath" class="installer-path-input" value="${state.installPath}" spellcheck="false">
        <button class="installer-browse-btn" id="btnBrowsePath">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
          ${t('browse')}
        </button>
      </div>
    </div>

    <!-- Components & Options Card -->
    <div class="installer-card" style="margin-bottom: 0;">
      <div class="installer-card-header">
        <span class="installer-card-title">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="var(--accent-bright)" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
          ${t('optionsTitle')}
        </span>
      </div>
      <div class="installer-options-list">
        <!-- Desktop Shortcut -->
        <div class="installer-option-row">
          <div class="installer-option-info">
            <svg class="installer-option-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
            <div class="installer-option-text">
              <span class="installer-option-label">${t('optDesktop')}</span>
              <span class="installer-option-desc">${t('optDesktopSub')}</span>
            </div>
          </div>
          <label class="clean-switch">
            <input type="checkbox" id="chkDesktop" ${state.createDesktopShortcut ? 'checked' : ''}>
            <span class="slider"></span>
          </label>
        </div>

        <!-- Start Menu -->
        <div class="installer-option-row">
          <div class="installer-option-info">
            <svg class="installer-option-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
            <div class="installer-option-text">
              <span class="installer-option-label">${t('optStartMenu')}</span>
              <span class="installer-option-desc">${t('optStartMenuSub')}</span>
            </div>
          </div>
          <label class="clean-switch">
            <input type="checkbox" id="chkStartMenu" ${state.createStartMenuShortcut ? 'checked' : ''}>
            <span class="slider"></span>
          </label>
        </div>

        <!-- 60 FPS Emulator Profiles -->
        <div class="installer-option-row">
          <div class="installer-option-info">
            <svg class="installer-option-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v4m0 12v4M4.93 4.93l2.83 2.83m8.48 8.48l2.83 2.83M2 12h4m12 0h4M4.93 19.07l2.83-2.83m8.48-8.48l2.83-2.83"/></svg>
            <div class="installer-option-text">
              <span class="installer-option-label">${t('optKeys')}</span>
              <span class="installer-option-desc">${t('optKeysSub')}</span>
            </div>
          </div>
          <label class="clean-switch">
            <input type="checkbox" id="chkKeys" ${state.autoProvisionKeys ? 'checked' : ''}>
            <span class="slider"></span>
          </label>
        </div>

        <!-- Protocol Association -->
        <div class="installer-option-row">
          <div class="installer-option-info">
            <svg class="installer-option-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
            <div class="installer-option-text">
              <span class="installer-option-label">${t('optProtocol')}</span>
              <span class="installer-option-desc">${t('optProtocolSub')}</span>
            </div>
          </div>
          <label class="clean-switch">
            <input type="checkbox" id="chkProtocol" ${state.registerProtocol ? 'checked' : ''}>
            <span class="slider"></span>
          </label>
        </div>
      </div>
    </div>
  `;

  footer.innerHTML = `
    <div class="installer-footer-left">
      <span>Emufii Open Source • AGPL-3.0</span>
    </div>
    <div class="installer-footer-actions">
      <button class="installer-btn-cancel" id="btnCancel">${t('cancel')}</button>
      <button class="installer-btn-install" id="btnStartInstall">
        <span>${t('installNow')}</span>
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
      </button>
    </div>
  `;

  // Attach Listeners
  const inputPath = main.querySelector<HTMLInputElement>('#inputInstallPath');
  inputPath?.addEventListener('input', (e) => {
    state.installPath = (e.target as HTMLInputElement).value;
  });

  main.querySelector('#btnBrowsePath')?.addEventListener('click', async () => {
    sfx.play('click');
    if (emuApi?.dialog?.selectDirectory) {
      const selected = await emuApi.dialog.selectDirectory();
      if (selected) {
        state.installPath = selected;
        if (inputPath) inputPath.value = selected;
      }
    }
  });

  main.querySelector('#chkDesktop')?.addEventListener('change', (e) => {
    sfx.play('toggle');
    state.createDesktopShortcut = (e.target as HTMLInputElement).checked;
  });

  main.querySelector('#chkStartMenu')?.addEventListener('change', (e) => {
    sfx.play('toggle');
    state.createStartMenuShortcut = (e.target as HTMLInputElement).checked;
  });

  main.querySelector('#chkKeys')?.addEventListener('change', (e) => {
    sfx.play('toggle');
    state.autoProvisionKeys = (e.target as HTMLInputElement).checked;
  });

  main.querySelector('#chkProtocol')?.addEventListener('change', (e) => {
    sfx.play('toggle');
    state.registerProtocol = (e.target as HTMLInputElement).checked;
  });

  footer.querySelector('#btnCancel')?.addEventListener('click', () => {
    sfx.play('click');
    if (emuApi?.window?.close) emuApi.window.close();
    else window.close();
  });

  footer.querySelector('#btnStartInstall')?.addEventListener('click', () => {
    sfx.play('confirm');
    startInstallationSequence();
  });
}

// Stage 2: Installing progress
function renderInstallingStage(main: HTMLElement, footer: HTMLElement) {
  const steps = [
    t('step1'),
    t('step2'),
    t('step3'),
    t('step4'),
    t('step5'),
  ];

  const currentStepText = steps[state.currentStepIndex] || t('step1');

  main.innerHTML = `
    <div class="installer-progress-view">
      <div class="installer-progress-logo">
        <div class="installer-logo-glow" style="opacity: 0.7; transform: scale(1.2);"></div>
        <img src="./assets/logo.png" alt="Emufii" class="installer-logo-img" style="width: 72px; height: 72px;">
      </div>

      <div class="installer-progress-title">${t('installingTitle')}</div>
      <div class="installer-progress-status" id="installerStatusLabel">${currentStepText}</div>

      <!-- Glowing Pill Progress Bar -->
      <div class="installer-track-outer">
        <div class="installer-bar-fill" id="installerBarFill" style="width: ${state.progressPercent}%;"></div>
      </div>
      <div class="installer-percent-label" id="installerPercentLabel">${Math.round(state.progressPercent)}%</div>

      <!-- Step Checklist -->
      <div class="installer-steps-checklist">
        ${steps
          .map((step, idx) => {
            const isDone = idx < state.currentStepIndex;
            const isActive = idx === state.currentStepIndex;
            return `
              <div class="installer-step-item ${isDone ? 'done' : ''} ${isActive ? 'active' : ''}">
                <div class="installer-step-badge">
                  ${
                    isDone
                      ? '<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>'
                      : isActive
                      ? '<svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="6"/></svg>'
                      : '<svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="8"/></svg>'
                  }
                </div>
                <span>${step}</span>
              </div>
            `;
          })
          .join('')}
      </div>
    </div>
  `;

  footer.innerHTML = `
    <div class="installer-footer-left">
      <span>${state.installPath}</span>
    </div>
    <div class="installer-footer-actions">
      <button class="installer-btn-cancel" disabled style="opacity: 0.4; cursor: not-allowed;">${t('cancel')}</button>
    </div>
  `;
}

// Stage 3: Complete success
function renderCompleteStage(main: HTMLElement, footer: HTMLElement) {
  main.innerHTML = `
    <div class="installer-complete-view">
      <div class="installer-success-emblem">
        <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="20 6 9 17 4 12"/>
        </svg>
      </div>

      <div class="installer-complete-title">${t('completeTitle')}</div>
      <div class="installer-complete-sub">${t('completeSub')}</div>

      <label class="installer-launch-toggle" id="toggleLaunch">
        <label class="clean-switch" style="pointer-events: none;">
          <input type="checkbox" ${state.launchAfter ? 'checked' : ''}>
          <span class="slider"></span>
        </label>
        <span style="font-size: 13px; font-weight: 700; color: var(--ink-primary);">${t('launchCheck')}</span>
      </label>
    </div>
  `;

  footer.innerHTML = `
    <div class="installer-footer-left">
      <span>Emufii v2.0.4 Ready</span>
    </div>
    <div class="installer-footer-actions">
      <button class="installer-btn-install" id="btnFinish">
        <span>${t('finishBtn')}</span>
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polygon points="5 3 19 12 5 21 5 3"/></svg>
      </button>
    </div>
  `;

  main.querySelector('#toggleLaunch')?.addEventListener('click', (e) => {
    e.preventDefault();
    sfx.play('toggle');
    state.launchAfter = !state.launchAfter;
    const chk = main.querySelector<HTMLInputElement>('#toggleLaunch input');
    if (chk) chk.checked = state.launchAfter;
  });

  footer.querySelector('#btnFinish')?.addEventListener('click', () => {
    sfx.play('confirm');
    if (state.launchAfter && emuApi?.installer?.launchInstalledApp) {
      emuApi.installer.launchInstalledApp();
    } else if (emuApi?.window?.close) {
      emuApi.window.close();
    } else {
      window.close();
    }
  });
}

// Installation sequence execution
function startInstallationSequence() {
  state.stage = 'installing';
  state.progressPercent = 5;
  state.currentStepIndex = 0;
  renderCurrentStage();

  const totalSteps = 5;
  const stepDurations = [450, 750, 650, 500, 400]; // Total ~2.7s snappy & fluid

  let currentStep = 0;

  function runNextStep() {
    if (currentStep >= totalSteps) {
      // Completed!
      state.progressPercent = 100;
      updateLiveProgress();
      setTimeout(() => {
        sfx.play('pop');
        state.stage = 'complete';
        renderCurrentStage();
      }, 400);
      return;
    }

    state.currentStepIndex = currentStep;
    const targetProgress = Math.round(((currentStep + 1) / totalSteps) * 100);

    // Smoothly interpolate progress
    const startP = state.progressPercent;
    const diff = targetProgress - startP;
    const dur = stepDurations[currentStep];
    const startTime = performance.now();

    function animateProgress(time: number) {
      const elapsed = time - startTime;
      const factor = Math.min(elapsed / dur, 1);
      state.progressPercent = startP + diff * factor;
      updateLiveProgress();

      if (factor < 1) {
        requestAnimationFrame(animateProgress);
      } else {
        currentStep++;
        setTimeout(runNextStep, 80);
      }
    }

    requestAnimationFrame(animateProgress);
  }

  setTimeout(runNextStep, 200);
}

function updateLiveProgress() {
  const bar = document.getElementById('installerBarFill');
  const label = document.getElementById('installerPercentLabel');
  const status = document.getElementById('installerStatusLabel');
  if (bar) bar.style.width = `${state.progressPercent}%`;
  if (label) label.textContent = `${Math.round(state.progressPercent)}%`;

  const steps = [t('step1'), t('step2'), t('step3'), t('step4'), t('step5')];
  if (status && steps[state.currentStepIndex]) {
    status.textContent = steps[state.currentStepIndex];
  }
}

// Auto-run when DOM is ready
if (typeof document !== 'undefined') {
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initInstaller);
  } else {
    initInstaller();
  }
}
