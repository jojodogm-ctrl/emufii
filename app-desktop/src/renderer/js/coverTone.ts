/**
 * Extracts dominant vibrant color from an image element or URL
 * to produce the cover's glowing ambient shadow, matching CoverTone.kt.
 */
const toneCache = new Map<string, string>();

export function extractCoverTone(imgElement: HTMLImageElement): string {
  const src = imgElement.src;
  if (toneCache.has(src)) {
    return toneCache.get(src)!;
  }

  try {
    const canvas = document.createElement('canvas');
    canvas.width = 32;
    canvas.height = 32;
    const ctx = canvas.getContext('2d');
    if (!ctx) return 'rgba(56, 225, 255, 0.4)';

    ctx.drawImage(imgElement, 0, 0, 32, 32);
    const data = ctx.getImageData(0, 0, 32, 32).data;

    let bestR = 56, bestG = 225, bestB = 255;
    let maxScore = -1;

    for (let i = 0; i < data.length; i += 4) {
      const r = data[i];
      const g = data[i + 1];
      const b = data[i + 2];
      const a = data[i + 3];
      if (a < 128) continue;

      // Calculate saturation and brightness
      const max = Math.max(r, g, b);
      const min = Math.min(r, g, b);
      const l = (max + min) / 510;
      const s = max === min ? 0 : (max - min) / (l > 0.5 ? 510 - max - min : max + min);

      // We weigh saturation squared (matching CoverTone decision in docs)
      const score = s * s * l;
      if (score > maxScore) {
        maxScore = score;
        bestR = r;
        bestG = g;
        bestB = b;
      }
    }

    const rgba = `rgba(${bestR}, ${bestG}, ${bestB}, 0.5)`;
    toneCache.set(src, rgba);
    return rgba;
  } catch {
    return 'rgba(56, 225, 255, 0.4)';
  }
}
