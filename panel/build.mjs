#!/usr/bin/env node
/**
 * Builds the two Workers the app installs into a user's Cloudflare account.
 *
 * The page lives in its own file so an inline-<script> syntax error can be caught by a test instead
 * of in a browser (that is how the panel once shipped completely dead), and the HTML is inlined as a
 * JSON string so no backslash survives a template literal.
 *
 *   node panel/build.mjs
 *   → app/src/main/assets/panel/{panel-worker,proxy-worker}.js
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// fileURLToPath, not URL.pathname: on Windows the latter yields "/C:/..." and ENOENT.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const out = path.join(root, 'app/src/main/assets/panel');

const page = path.join(root, 'panel/worker/panel-ui.html');
const panelSrc = path.join(root, 'panel/worker/panel-worker.js');
const proxySrc = path.join(root, 'panel/worker/proxy-worker.js');

const html = fs.readFileSync(page, 'utf8');
for (const code of [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1])) {
  new Function(code); // throws before a build ever produces a dead panel
}

const source = fs.readFileSync(panelSrc, 'utf8');
if (!source.includes('__PANEL_HTML__')) throw new Error('panel-worker.js lost its placeholder');
// Next to its source, so the relative './lib.js' import resolves while bundling.
  const inlined = path.join(root, 'panel/worker/.panel-worker.inline.js');
fs.writeFileSync(inlined, source.replace('__PANEL_HTML__', JSON.stringify(html)));

fs.mkdirSync(out, { recursive: true });
try {
  // --bundle is required: lib.js is shared by both workers, and the panel uploads a single module
  // per worker. Without it the output would import './lib.js', which Cloudflare never receives.
  const flags = ['--bundle', '--minify', '--format=esm', '--external:cloudflare:sockets'];
  execFileSync('npx', ['--yes', 'esbuild', inlined, ...flags,
    '--outfile=' + path.join(out, 'panel-worker.js')], { stdio: 'inherit' });
  execFileSync('npx', ['--yes', 'esbuild', proxySrc, ...flags,
    '--outfile=' + path.join(out, 'proxy-worker.js')], { stdio: 'inherit' });
} finally {
  fs.unlinkSync(inlined);
}
fs.copyFileSync(path.join(root, 'panel/worker/schema.sql'), path.join(out, 'schema.sql'));

// The node worker is the one Cloudflare starts on every connection: an import that pushes it past a
// few tens of KB turns into a visible cold-start penalty (and, at the upload limit, a hard failure).
const proxyOut = path.join(out, 'proxy-worker.js');
const proxyBytes = fs.statSync(proxyOut).size;
const PROXY_LIMIT = 32 * 1024;
if (proxyBytes > PROXY_LIMIT) {
  throw new Error(`proxy-worker.js is ${proxyBytes} bytes (limit ${PROXY_LIMIT}); remove the import or inline less`);
}
console.log(`proxy-worker.js ${(proxyBytes / 1024).toFixed(1)} KB (limit ${(PROXY_LIMIT / 1024)} KB)`);
console.log('built', out);
