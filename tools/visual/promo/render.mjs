/** Capture static poster pages with Chrome CDP and Node's built-in WebSocket. No npm dependencies. */
import { spawn } from 'node:child_process';
import { mkdtemp, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const directory = resolve(process.argv[2] ?? '');
// CSS layout width; captured at twice the density so game UI crops keep their native pixels.
const WIDTH = 1200, SCALE = 2;
if (!process.argv[2]) throw new Error('Usage: node tools/visual/promo/render.mjs BUILD_DIRECTORY');
const profile = await mkdtemp(join(tmpdir(), 'ae2-poster-chrome-'));
const chrome = spawn(process.env.CHROME_BIN || 'google-chrome', [
  '--headless', '--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--remote-debugging-port=0',
  '--disable-background-networking', '--no-first-run', '--user-data-dir=' + profile, 'about:blank',
], { stdio: ['ignore', 'ignore', 'pipe'] });
let socket;
try {
  const endpoint = await new Promise((accept, reject) => {
    let buffer = '';
    const timeout = setTimeout(() => reject(new Error('Chrome startup timed out')), 15000);
    chrome.stderr.on('data', chunk => {
      buffer += chunk;
      const match = buffer.match(/DevTools listening on (ws:\/\/[^\s]+)/);
      if (match) { clearTimeout(timeout); accept(match[1]); }
    });
    chrome.on('error', reject);
    chrome.on('exit', code => reject(new Error('Chrome exited: ' + code)));
  });
  socket = new WebSocket(endpoint);
  await new Promise((accept, reject) => { socket.onopen = accept; socket.onerror = reject; });
  let id = 0;
  const pending = new Map();
  socket.onmessage = ({ data }) => {
    const message = JSON.parse(data);
    if (message.id && pending.has(message.id)) {
      const { accept, reject, timeout } = pending.get(message.id);
      pending.delete(message.id);
      clearTimeout(timeout);
      message.error ? reject(new Error(JSON.stringify(message.error))) : accept(message.result);
    }
  };
  const call = (method, params = {}, sessionId) => new Promise((accept, reject) => {
    const key = ++id;
    const timeout = setTimeout(() => { pending.delete(key); reject(new Error('CDP timeout: ' + method)); }, 30000);
    pending.set(key, { accept, reject, timeout });
    socket.send(JSON.stringify({ id: key, method, params, ...(sessionId ? { sessionId } : {}) }));
  });
  const results = [];
  for (const language of ['zh', 'en']) {
    const { targetId } = await call('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await call('Target.attachToTarget', { targetId, flatten: true });
    const page = (method, params) => call(method, params, sessionId);
    await page('Page.enable');
    await page('Emulation.setDeviceMetricsOverride', { width: WIDTH, height: 1100, deviceScaleFactor: SCALE, mobile: false });
    await page('Page.navigate', { url: pathToFileURL(join(directory, language + '.html')).href });
    const ready = await page('Runtime.evaluate', { expression: `new Promise(resolve => {
      const ready = async () => {
        await document.fonts.ready;
        await Promise.all(Array.from(document.images, i => i.decode()));
        requestAnimationFrame(() => requestAnimationFrame(() => resolve(true)));
      };
      document.readyState === 'complete' ? ready() : addEventListener('load', ready, {once:true});
    })`, awaitPromise: true, returnByValue: true });
    if (ready.exceptionDetails || ready.result.value !== true) throw new Error('Page assets failed: ' + language);
    const audit = await page('Runtime.evaluate', { expression: `(() => {
      const problems = [];
      for (const element of document.querySelectorAll('[data-check]')) {
        const r = element.getBoundingClientRect();
        const box = element.closest('[data-bounds],section,footer').getBoundingClientRect();
        if (r.left < box.left - 1 || r.right > box.right + 1 || r.top < box.top - 1 || r.bottom > box.bottom + 1
            || element.scrollWidth > element.clientWidth + 2)
          problems.push({text:element.innerText, x:r.x, y:r.y, width:r.width, height:r.height});
      }
      return {width:document.documentElement.scrollWidth,height:document.documentElement.scrollHeight,
        images:[...document.images].map(i=>({source:i.getAttribute('src'),loaded:i.complete&&i.naturalWidth>0})),problems};
    })()`, returnByValue: true });
    const report = audit.result.value;
    if (report.width !== WIDTH || report.problems.length || report.images.some(i => !i.loaded))
      throw new Error('LAYOUT_FAILED ' + language + ' ' + JSON.stringify(report));
    const png = await page('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true,
      clip: { x: 0, y: 0, width: WIDTH, height: report.height, scale: 1 } });
    await writeFile(join(directory, language + '.png'), Buffer.from(png.data, 'base64'));
    results.push({language, scale: SCALE, ...report});
    console.log('POSTER_RENDER_OK', language, WIDTH * SCALE + 'x' + report.height * SCALE);
    await call('Target.closeTarget', { targetId });
  }
  await writeFile(join(directory, 'layout-check.json'), JSON.stringify(results, null, 2) + '\n');
} finally {
  socket?.close();
  chrome.kill();
}
