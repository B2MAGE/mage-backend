// Render and validate local scene fixtures through the actual MAGE engine.
// Start frontend and dedicated Chromium with --remote-debugging-port=9225.
import { mkdir, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildSceneCatalog } from './quality-scene-catalog.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(repo, '.local/quality-review');
await mkdir(path.join(output, 'thumbnails'), { recursive: true });
const targets = await (await fetch('http://127.0.0.1:9225/json')).json();
const target = targets.find(item => item.type === 'page' && item.url.includes('127.0.0.1:5173/scripts/quality-scene-capture.html'));
if (!target) throw new Error('Open dedicated local quality-scene-capture.html on debugging port 9225 first.');
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
const pending = new Map(); let id = 0; const errors = [];
socket.addEventListener('message', event => {
  const response = JSON.parse(event.data);
  if (response.id && pending.has(response.id)) {
    const { resolve, reject, timer } = pending.get(response.id); clearTimeout(timer); pending.delete(response.id);
    if (response.error) reject(new Error(JSON.stringify(response.error))); else resolve(response.result);
  } else if (response.method === 'Runtime.exceptionThrown') errors.push(response.params.exceptionDetails);
  else if (response.method === 'Log.entryAdded' && response.params.entry.level === 'error') errors.push(response.params.entry);
});
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const next = ++id;
    const timer = setTimeout(() => { pending.delete(next); reject(new Error(method + ' timed out')); }, 60000);
    pending.set(next, { resolve, reject, timer }); socket.send(JSON.stringify({ id: next, method, params }));
  });
}
async function evaluate(expression) {
  const response = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, userGesture: true });
  if (response.exceptionDetails) throw new Error(response.exceptionDetails.exception?.description ?? response.exceptionDetails.text);
  return response.result.value;
}
try {
  await send('Runtime.enable'); await send('Log.clear'); await send('Log.enable');
  if (!await evaluate('Boolean(window.qualityRenderer)')) throw new Error('Capture harness is not ready');
  const catalog = buildSceneCatalog(); const report = [];
  const catalogFingerprint = createHash('sha256').update(JSON.stringify(catalog)).digest('hex');
  const selected = process.argv.includes('--sample') ? catalog.map((_, i) => i).filter(i => i % 10 === 0) : catalog.map((_, i) => i);
  for (const index of selected) {
    const item = catalog[index];
    const result = await evaluate(`window.qualityRenderer.captureScene(${JSON.stringify(item.sceneData)}, {checkAudio:${index % 10 === 0}})`);
    const { dataUrl, ...validation } = result;
    const filename = String(index + 1).padStart(3, '0') + '.png';
    await writeFile(path.join(output, 'thumbnails', filename), Buffer.from(dataUrl.split(',')[1], 'base64'));
    report.push({ index, filename, visualFamily: item.visualFamily, paletteName: item.paletteName, ...validation });
    await writeFile(path.join(output, 'render-validation.json'), JSON.stringify({ createdAt: new Date().toISOString(), catalogFingerprint, report, errors }, null, 2));
    console.log(`${index + 1}/100 ${item.visualFamily} ${item.paletteName}: ${result.valid ? 'PASS' : 'FAIL'}${result.audio ? ' audio=' + result.audio.reactive : ''}`);
    if (!result.valid) throw new Error('Scene failed render/audio validation; see .local/quality-review/render-validation.json');
  }
  if (errors.length) throw new Error(`Browser reported ${errors.length} errors; inspect render-validation.json`);
  console.log(`Captured ${report.length} real 640x360 thumbnails; all sampled phases and audio checks passed.`);
} finally { socket.close(); }
