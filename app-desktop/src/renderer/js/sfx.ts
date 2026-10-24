export type SfxName = 'hover' | 'click' | 'confirm' | 'pop' | 'notify' | 'toggle' | 'tick';

class SfxEngine {
  private ctx: AudioContext | null = null;
  private buffers: Map<SfxName, AudioBuffer> = new Map();
  private enabled = true;

  private soundUrls: Record<SfxName, string> = {
    hover: './assets/sounds/sfx_hover.mp3',
    click: './assets/sounds/sfx_click.mp3',
    confirm: './assets/sounds/sfx_confirm.mp3',
    pop: './assets/sounds/sfx_pop.mp3',
    notify: './assets/sounds/sfx_notify.mp3',
    toggle: './assets/sounds/sfx_toggle.mp3',
    tick: './assets/sounds/sfx_tick.mp3',
  };

  private volumes: Record<SfxName, number> = {
    hover: 0.55,
    click: 1.0,
    confirm: 0.9,
    pop: 0.85,
    notify: 0.8,
    toggle: 0.85,
    tick: 0.7,
  };

  setEnabled(enabled: boolean) {
    this.enabled = enabled;
  }

  async init() {
    if (this.ctx) return;
    try {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      this.ctx = new AudioCtx();

      // Preload all sounds
      for (const [name, url] of Object.entries(this.soundUrls) as [SfxName, string][]) {
        fetch(url)
          .then((res) => res.arrayBuffer())
          .then((buf) => this.ctx!.decodeAudioData(buf))
          .then((decoded) => this.buffers.set(name, decoded))
          .catch(() => {});
      }
    } catch {
      // AudioContext unavailable
    }
  }

  play(name: SfxName) {
    if (!this.enabled || !this.ctx) return;

    if (this.ctx.state === 'suspended') {
      this.ctx.resume();
    }

    const buffer = this.buffers.get(name);
    if (!buffer) return;

    try {
      const source = this.ctx.createBufferSource();
      source.buffer = buffer;

      const gain = this.ctx.createGain();
      gain.gain.value = this.volumes[name] ?? 1.0;

      source.connect(gain);
      gain.connect(this.ctx.destination);
      source.start(0);
    } catch {
      // Playback failed
    }
  }
}

export const sfx = new SfxEngine();
