/**
 * TrayBackdrop - Signature Emufii Duotone Shelves & Sonar Wave Ripple Backdrop
 * Direct 1:1 implementation of TrayBackdrop.kt from app-android:
 * - ShellDark (#120F1D) to ShellDarkLow (#090711) base duotone gradient
 * - Off-screen Coral rounded plate with radial glow (top-left)
 * - Off-screen Teal rounded plate with radial glow (bottom-right)
 * - Dual expanding wave ripples with halo stroke and smooth sine-weighted alpha
 */

export class TrayBackdrop {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private stillCanvas: HTMLCanvasElement;
  private stillCtx: CanvasRenderingContext2D;
  private animId: number | null = null;
  private bakedKey: string = '';
  private width: number = 0;
  private height: number = 0;
  private dpr: number = 1;

  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Canvas 2D context unavailable');
    this.ctx = ctx;

    this.stillCanvas = document.createElement('canvas');
    this.stillCtx = this.stillCanvas.getContext('2d')!;

    this.onResize = this.onResize.bind(this);
    this.loop = this.loop.bind(this);

    this.init();
  }

  private init() {
    window.addEventListener('resize', this.onResize);
    this.updateDimensions();
    this.start();
  }

  public updateDimensions() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = window.innerWidth;
    const h = window.innerHeight;

    if (w <= 0 || h <= 0) return;

    this.width = Math.round(w * dpr);
    this.height = Math.round(h * dpr);
    this.dpr = dpr;

    this.canvas.width = this.width;
    this.canvas.height = this.height;
    this.canvas.style.width = `${w}px`;
    this.canvas.style.height = `${h}px`;

    this.stillCanvas.width = this.width;
    this.stillCanvas.height = this.height;

    this.bakedKey = '';
    this.bakeStillTray();
    this.drawFrame();
  }

  private onResize() {
    this.updateDimensions();
  }

  public update() {
    this.bakedKey = '';
    this.bakeStillTray();
    this.drawFrame();
  }

  private bakeStillTray() {
    const theme = document.body.dataset.theme || 'dark';
    const key = `${this.width}x${this.height}_${theme}`;
    if (this.bakedKey === key) return;

    const ctx = this.stillCtx;
    const w = this.width;
    const h = this.height;
    if (w <= 0 || h <= 0) return;

    this.bakedKey = key;
    ctx.clearRect(0, 0, w, h);

    const isOled = theme === 'oled';
    const isDark = theme === 'night' || theme === 'dark' || !theme;

    // Base colors matching Color.kt
    const top = isOled ? '#000000' : isDark ? '#120F1D' : '#F1EFEA';
    const bottom = isOled ? '#000000' : isDark ? '#090711' : '#E2DFD7';

    const fillAlpha = isOled ? 0.07 : isDark ? 0.08 : 0.12;
    const glowAlpha = isOled ? 0.04 : isDark ? 0.05 : 0.07;

    // Duotone accent colors
    const coral = isDark ? { r: 247, g: 147, b: 188 } : { r: 238, g: 111, b: 163 };
    const coralDeep = { r: 194, g: 75, b: 126 };
    const teal = isDark ? { r: 92, g: 224, b: 210 } : { r: 63, g: 207, b: 192 };
    const tealDeep = { r: 14, g: 156, b: 143 };

    // 1. Base vertical gradient
    const baseGrad = ctx.createLinearGradient(0, 0, 0, h);
    baseGrad.addColorStop(0, top);
    baseGrad.addColorStop(1, bottom);
    ctx.fillStyle = baseGrad;
    ctx.fillRect(0, 0, w, h);

    // 2. Geometry matching TrayGeometry in TrayBackdrop.kt
    const side = 0.58 * Math.max(w, h);
    const radius = side * 0.3;

    const coralX = w * 0.02;
    const coralY = h * -0.06;
    const coralLeft = coralX - side / 2;
    const coralTop = coralY - side / 2;
    const coralCornerX = coralLeft + side;
    const coralCornerY = coralTop + side;

    const tealX = w * 0.98;
    const tealY = h * 1.06;
    const tealLeft = tealX - side / 2;
    const tealTop = tealY - side / 2;
    const tealCornerX = tealLeft;
    const tealCornerY = tealTop;

    // Coral Shelf
    const coralGrad = ctx.createLinearGradient(0, coralTop, 0, coralTop + side);
    coralGrad.addColorStop(0, `rgba(${coral.r}, ${coral.g}, ${coral.b}, ${fillAlpha})`);
    coralGrad.addColorStop(1, `rgba(${coralDeep.r}, ${coralDeep.g}, ${coralDeep.b}, ${fillAlpha * 0.72})`);
    ctx.fillStyle = coralGrad;
    ctx.beginPath();
    ctx.roundRect(coralLeft, coralTop, side, side, radius);
    ctx.fill();

    // Dark radial gradient mask over Coral Shelf (matching TrayBackdrop.kt lines 174-187)
    if (isDark || isOled) {
      const coralMask = ctx.createRadialGradient(coralCornerX, coralCornerY, 0, coralCornerX, coralCornerY, side * 1.10);
      coralMask.addColorStop(0, 'rgba(0, 0, 0, 0)');
      coralMask.addColorStop(0.52, 'rgba(0, 0, 0, 0)');
      coralMask.addColorStop(1, top);
      ctx.fillStyle = coralMask;
      ctx.beginPath();
      ctx.roundRect(coralLeft, coralTop, side, side, radius);
      ctx.fill();
    }

    // Coral Shelf Corner Glow
    const coralGlow = ctx.createRadialGradient(coralCornerX, coralCornerY, 0, coralCornerX, coralCornerY, side * 0.82);
    coralGlow.addColorStop(0, `rgba(${coral.r}, ${coral.g}, ${coral.b}, ${glowAlpha})`);
    coralGlow.addColorStop(0.45, `rgba(${coral.r}, ${coral.g}, ${coral.b}, ${glowAlpha * 0.45})`);
    coralGlow.addColorStop(1, 'rgba(0, 0, 0, 0)');
    ctx.fillStyle = coralGlow;
    ctx.beginPath();
    ctx.arc(coralCornerX, coralCornerY, side * 0.82, 0, Math.PI * 2);
    ctx.fill();

    // Teal Shelf
    const tealGrad = ctx.createLinearGradient(0, tealTop, 0, tealTop + side);
    tealGrad.addColorStop(0, `rgba(${teal.r}, ${teal.g}, ${teal.b}, ${fillAlpha})`);
    tealGrad.addColorStop(1, `rgba(${tealDeep.r}, ${tealDeep.g}, ${tealDeep.b}, ${fillAlpha * 0.72})`);
    ctx.fillStyle = tealGrad;
    ctx.beginPath();
    ctx.roundRect(tealLeft, tealTop, side, side, radius);
    ctx.fill();

    // Dark radial gradient mask over Teal Shelf (matching TrayBackdrop.kt lines 174-187)
    if (isDark || isOled) {
      const tealMask = ctx.createRadialGradient(tealCornerX, tealCornerY, 0, tealCornerX, tealCornerY, side * 1.10);
      tealMask.addColorStop(0, 'rgba(0, 0, 0, 0)');
      tealMask.addColorStop(0.52, 'rgba(0, 0, 0, 0)');
      tealMask.addColorStop(1, bottom);
      ctx.fillStyle = tealMask;
      ctx.beginPath();
      ctx.roundRect(tealLeft, tealTop, side, side, radius);
      ctx.fill();
    }

    // Teal Shelf Corner Glow
    const tealGlow = ctx.createRadialGradient(tealCornerX, tealCornerY, 0, tealCornerX, tealCornerY, side * 0.82);
    tealGlow.addColorStop(0, `rgba(${teal.r}, ${teal.g}, ${teal.b}, ${glowAlpha})`);
    tealGlow.addColorStop(0.45, `rgba(${teal.r}, ${teal.g}, ${teal.b}, ${glowAlpha * 0.45})`);
    tealGlow.addColorStop(1, 'rgba(0, 0, 0, 0)');
    ctx.fillStyle = tealGlow;
    ctx.beginPath();
    ctx.arc(tealCornerX, tealCornerY, side * 0.82, 0, Math.PI * 2);
    ctx.fill();

    // 3. Ambient Vignette (matching TrayBackdrop.kt)
    if (!isOled) {
      const vigRadius = Math.max(w, h) * 0.78;
      const vig = ctx.createRadialGradient(w * 0.5, h * 0.42, 0, w * 0.5, h * 0.42, vigRadius);
      vig.addColorStop(0, 'rgba(0, 0, 0, 0)');
      vig.addColorStop(0.55, 'rgba(0, 0, 0, 0)');
      vig.addColorStop(1, isDark ? 'rgba(0, 0, 0, 0.32)' : 'rgba(0, 0, 0, 0.14)');
      ctx.fillStyle = vig;
      ctx.fillRect(0, 0, w, h);
    }
  }

  private start() {
    if (this.animId !== null) return;
    this.loop();
  }

  public stop() {
    if (this.animId !== null) {
      cancelAnimationFrame(this.animId);
      this.animId = null;
    }
  }

  private loop() {
    this.drawFrame();
    this.animId = requestAnimationFrame(this.loop);
  }

  private drawFrame() {
    const w = this.width;
    const h = this.height;
    if (w <= 0 || h <= 0) return;

    const ctx = this.ctx;

    // 1. Clear frame and paint cached still backdrop
    ctx.clearRect(0, 0, w, h);
    ctx.drawImage(this.stillCanvas, 0, 0, w, h);

    // 2. Animated wave ripples matching drawWaves in TrayBackdrop.kt
    const theme = document.body.dataset.theme || 'dark';
    const isOled = theme === 'oled';
    const isDark = theme === 'night' || theme === 'dark' || !theme;

    const waveAlpha = isOled ? 0.025 : isDark ? 0.038 : 0.05;
    const coral = isDark ? { r: 247, g: 147, b: 188 } : { r: 238, g: 111, b: 163 };
    const teal = isDark ? { r: 92, g: 224, b: 210 } : { r: 63, g: 207, b: 192 };

    const side = 0.58 * Math.max(w, h);
    const radius = side * 0.3;

    const coralX = w * 0.02;
    const coralY = h * -0.06;
    const tealX = w * 0.98;
    const tealY = h * 1.06;

    const CYCLE_MS = 19000;
    const WAVES = 2;
    const WAVE_SPEED = 0.55;

    const time = (performance.now() % CYCLE_MS) / CYCLE_MS;
    const dpr = this.dpr;

    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';

    for (let i = 0; i < WAVES; i++) {
      const p = (time * WAVE_SPEED + i / WAVES) % 1.0;
      const alpha = waveAlpha * Math.sin(p * Math.PI);
      if (alpha <= 0.001) continue;

      const reach = p * side * 0.75;

      // Coral Ripple (Top-Left)
      const cx = coralX - side / 2 - reach;
      const cy = coralY - side / 2 - reach;
      const cw = side + reach * 2;
      const ch = side + reach * 2;
      const cr = Math.min(Math.max(0, radius + reach), cw / 2);

      ctx.beginPath();
      ctx.roundRect(cx, cy, cw, ch, cr);

      // Halo Stroke (12dp @ 0.25)
      ctx.lineWidth = 12 * dpr;
      ctx.strokeStyle = `rgba(${coral.r}, ${coral.g}, ${coral.b}, ${alpha * 0.25})`;
      ctx.stroke();

      // Soft Core Stroke (2dp @ 0.65)
      ctx.lineWidth = 2 * dpr;
      ctx.strokeStyle = `rgba(${coral.r}, ${coral.g}, ${coral.b}, ${alpha * 0.65})`;
      ctx.stroke();

      // Teal Ripple (Bottom-Right)
      const tx = tealX - side / 2 - reach;
      const ty = tealY - side / 2 - reach;
      const tw = side + reach * 2;
      const th = side + reach * 2;
      const tr = Math.min(Math.max(0, radius + reach), tw / 2);

      ctx.beginPath();
      ctx.roundRect(tx, ty, tw, th, tr);

      // Halo Stroke (12dp @ 0.25)
      ctx.lineWidth = 12 * dpr;
      ctx.strokeStyle = `rgba(${teal.r}, ${teal.g}, ${teal.b}, ${alpha * 0.25})`;
      ctx.stroke();

      // Soft Core Stroke (2dp @ 0.65)
      ctx.lineWidth = 2 * dpr;
      ctx.strokeStyle = `rgba(${teal.r}, ${teal.g}, ${teal.b}, ${alpha * 0.65})`;
      ctx.stroke();
    }
  }

  public destroy() {
    this.stop();
    window.removeEventListener('resize', this.onResize);
  }
}
