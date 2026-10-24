import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import esbuild from 'esbuild';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const root = path.resolve(__dirname, '..');

const srcRenderer = path.join(root, 'src', 'renderer');
const distRenderer = path.join(root, 'dist', 'renderer');

// Recursively copy directory
function copyDir(src, dest) {
  if (!fs.existsSync(src)) return;
  fs.mkdirSync(dest, { recursive: true });
  const entries = fs.readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDir(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

async function build() {
  fs.mkdirSync(distRenderer, { recursive: true });

  // 1. Copy index.html and installer.html
  fs.copyFileSync(
    path.join(srcRenderer, 'index.html'),
    path.join(distRenderer, 'index.html')
  );
  if (fs.existsSync(path.join(srcRenderer, 'installer.html'))) {
    fs.copyFileSync(
      path.join(srcRenderer, 'installer.html'),
      path.join(distRenderer, 'installer.html')
    );
  }

  // 2. Copy css
  copyDir(path.join(srcRenderer, 'css'), path.join(distRenderer, 'css'));

  // 3. Copy assets
  copyDir(path.join(srcRenderer, 'assets'), path.join(distRenderer, 'assets'));

  // 4. Bundle TS/JS for renderer
  const entryPoints = [path.join(srcRenderer, 'main.ts')];
  if (fs.existsSync(path.join(srcRenderer, 'installer.ts'))) {
    entryPoints.push(path.join(srcRenderer, 'installer.ts'));
  }

  await esbuild.build({
    entryPoints,
    bundle: true,
    platform: 'browser',
    target: ['chrome120', 'es2022'],
    outdir: distRenderer,
    sourcemap: true,
  });

  console.log('Renderer build completed successfully.');
}

build().catch((err) => {
  console.error('Renderer build failed:', err);
  process.exit(1);
});
