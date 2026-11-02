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

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
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
const inlined = path.join(root, 'panel/.panel-worker.inline.js');
fs.writeFileSync(inlined, source.replace('__PANEL_HTML__', JSON.stringify(html)));

fs.mkdirSync(out, { recursive: true });
try {
  execFileSync('npx', ['--yes', 'esbuild', inlined, '--minify', '--format=esm',
    '--outfile=' + path.join(out, 'panel-worker.js')], { stdio: 'inherit' });
  execFileSync('npx', ['--yes', 'esbuild', proxySrc, '--minify', '--format=esm',
    '--outfile=' + path.join(out, 'proxy-worker.js')], { stdio: 'inherit' });
} finally {
  fs.unlinkSync(inlined);
}
fs.copyFileSync(path.join(root, 'panel/worker/schema.sql'), path.join(out, 'schema.sql'));
console.log('built', out);
