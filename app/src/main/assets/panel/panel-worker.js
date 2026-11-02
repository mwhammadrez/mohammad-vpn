/**
 * Zentor Panel — پنل سبک VLESS/Trojan روی Cloudflare Workers + D1
 * ------------------------------------------------------------------
 * مسیرها:
 *   /ws            → ترافیک پروکسی (VLESS و Trojan روی WebSocket)
 *   /sub/<token>   → لینک اشتراک (base64)
 *   /panel         → پنل مدیریت
 *   /api/*         → API پنل (کوکی امن)
 */

import { connect } from 'cloudflare:sockets';

const JSON_H = { 'content-type': 'application/json; charset=utf-8' };
const enc = new TextEncoder();
const dec = new TextDecoder();

const json = (o, s = 200, h = {}) =>
  new Response(JSON.stringify(o), { status: s, headers: { ...JSON_H, 'cache-control': 'no-store', ...h } });
const page = (s, status = 200) =>
  new Response(s, { status, headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } });

/* ───────────────────────── helpers ───────────────────────── */

const toHex = (buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('');
const sha256hex = async (s) => toHex(await crypto.subtle.digest('SHA-256', enc.encode(String(s))));
const randHex = (n = 16) => toHex(crypto.getRandomValues(new Uint8Array(n)));

function uuidv4() {
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

async function hmacHex(key, msg) {
  const k = await crypto.subtle.importKey('raw', enc.encode(key), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return toHex(await crypto.subtle.sign('HMAC', k, enc.encode(msg)));
}

function timingSafe(a, b) {
  a = String(a); b = String(b);
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

/* SHA-224 در WebCrypto نیست؛ پیاده‌سازی کوچک برای احراز هویت Trojan */
const K224 = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
];
function sha224hex(str) {
  const msg = enc.encode(String(str));
  const bitLen = msg.length * 8;
  const withPad = new Uint8Array(Math.ceil((msg.length + 9) / 64) * 64);
  withPad.set(msg);
  withPad[msg.length] = 0x80;
  new DataView(withPad.buffer).setUint32(withPad.length - 4, bitLen >>> 0);
  new DataView(withPad.buffer).setUint32(withPad.length - 8, Math.floor(bitLen / 2 ** 32));
  let H = [0xc1059ed8, 0x367cd507, 0x3070dd17, 0xf70e5939, 0xffc00b31, 0x68581511, 0x64f98fa7, 0xbefa4fa4];
  const w = new Int32Array(64);
  const dv = new DataView(withPad.buffer);
  for (let off = 0; off < withPad.length; off += 64) {
    for (let i = 0; i < 16; i++) w[i] = dv.getInt32(off + i * 4);
    for (let i = 16; i < 64; i++) {
      const s0 = ((w[i - 15] >>> 7) | (w[i - 15] << 25)) ^ ((w[i - 15] >>> 18) | (w[i - 15] << 14)) ^ (w[i - 15] >>> 3);
      const s1 = ((w[i - 2] >>> 17) | (w[i - 2] << 15)) ^ ((w[i - 2] >>> 19) | (w[i - 2] << 13)) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0;
    }
    let [a, b, c, d, e, f, g, h] = H;
    for (let i = 0; i < 64; i++) {
      const S1 = ((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7));
      const ch = (e & f) ^ (~e & g);
      const t1 = (h + S1 + ch + K224[i] + w[i]) | 0;
      const S0 = ((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10));
      const mj = (a & b) ^ (a & c) ^ (b & c);
      const t2 = (S0 + mj) | 0;
      h = g; g = f; f = e; e = (d + t1) | 0; d = c; c = b; b = a; a = (t1 + t2) | 0;
    }
    H = [(H[0] + a) | 0, (H[1] + b) | 0, (H[2] + c) | 0, (H[3] + d) | 0, (H[4] + e) | 0, (H[5] + f) | 0, (H[6] + g) | 0, (H[7] + h) | 0];
  }
  return H.slice(0, 7).map((x) => (x >>> 0).toString(16).padStart(8, '0')).join('');
}

/* ───────────────────────── DB ───────────────────────── */

const DB = {
  async getUsers(env) {
    return (await env.DB.prepare('SELECT * FROM users ORDER BY id DESC').all()).results || [];
  },
  async getUser(env, id) {
    return env.DB.prepare('SELECT * FROM users WHERE id = ?').bind(id).first();
  },
  async byUuid(env, uuid) {
    return env.DB.prepare('SELECT * FROM users WHERE uuid = ?').bind(uuid).first();
  },
  async byTrojan(env, hash) {
    return env.DB.prepare('SELECT * FROM users WHERE trojan_hash = ?').bind(hash).first();
  },
  async bySubToken(env, token) {
    return env.DB.prepare('SELECT * FROM users WHERE sub_token = ?').bind(token).first();
  },
  async addUser(env, { name, limit_gb, days }) {
    const uuid = uuidv4();
    const sub = randHex(16);
    const password = randHex(10);
    const now = Date.now();
    const expires = days > 0 ? now + days * 86400000 : 0;
    await env.DB.prepare(
      'INSERT INTO users (name, uuid, sub_token, trojan_password, trojan_hash, limit_bytes, used_bytes, expires_at, enabled, created_at) VALUES (?,?,?,?,?,?,0,?,1,?)'
    )
      .bind(name, uuid, sub, password, sha224hex(password), Math.round((limit_gb || 0) * 1024 ** 3), expires, now)
      .run();
    return { uuid, sub, password };
  },
  async addUsage(env, id, bytes) {
    if (!bytes) return;
    await env.DB.prepare('UPDATE users SET used_bytes = used_bytes + ?, last_active = ? WHERE id = ?')
      .bind(bytes, Date.now(), id)
      .run();
  },
  async setting(env, key, fallback = null) {
    const row = await env.DB.prepare('SELECT value FROM settings WHERE key = ?').bind(key).first();
    return row ? row.value : fallback;
  },
  async setSetting(env, key, value) {
    await env.DB.prepare('INSERT INTO settings (key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value = excluded.value')
      .bind(key, String(value))
      .run();
  },
  async getConfigs(env, onlyEnabled = false) {
    const sql = 'SELECT * FROM configs' + (onlyEnabled ? ' WHERE enabled = 1' : '') + ' ORDER BY sort ASC, id ASC';
    return (await env.DB.prepare(sql).all()).results || [];
  },
};

/**
 * Databases created before this build have no `configs` table. Creating it lazily keeps the worker
 * deployable over an existing panel without a migration step.
 */
let configsTable = null;
function ensureConfigs(env) {
  if (!configsTable) {
    configsTable = env.DB.prepare(
      'CREATE TABLE IF NOT EXISTS configs (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, uri TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, sort INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)',
    ).run().catch(() => {});
  }
  return configsTable;
}

/* ───────────────────────── auth ───────────────────────── */

const COOKIE = 'zp_session';
const SESSION_TTL = 7 * 86400000;

async function secret(env) {
  // Keyed on the password hash so changing the password invalidates every open session; before a
  // password exists there is nothing to bind to, so the random secret keeps the placeholder opaque.
  const hash = await DB.setting(env, 'password_hash');
  if (hash) return hash;
  let s = await DB.setting(env, 'session_secret');
  if (!s) {
    s = randHex(32);
    await DB.setSetting(env, 'session_secret', s);
  }
  return s;
}

async function makeSession(env) {
  const exp = Date.now() + SESSION_TTL;
  const sig = await hmacHex(await secret(env), `s.${exp}`);
  return `${exp}.${sig}`;
}

async function isAuthed(request, env) {
  const raw = request.headers.get('cookie') || '';
  const m = raw.match(new RegExp(`${COOKIE}=([^;]+)`));
  const bearer = (request.headers.get('authorization') || '').replace(/^Bearer\s+/i, '');
  const value = m ? decodeURIComponent(m[1]) : bearer;
  if (!value) return false;
  const [exp, sig] = value.split('.');
  if (!exp || !sig || Number(exp) < Date.now()) return false;
  return timingSafe(sig, await hmacHex(await secret(env), `s.${exp}`));
}

const cookieHeader = (v, maxAge) =>
  `${COOKIE}=${encodeURIComponent(v)}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=${maxAge}`;

async function readJson(request) {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

/* ───────────────────────── subscription ───────────────────────── */

function hostOf(request, env) {
  return env.PANEL_HOST || new URL(request.url).host;
}

function vlessLink(user, host) {
  const q = new URLSearchParams({
    encryption: 'none',
    security: 'tls',
    sni: host,
    fp: 'chrome',
    alpn: 'http/1.1',
    type: 'ws',
    host: host,
    path: '/ws?ed=2048',
  });
  return `vless://${user.uuid}@${host}:443?${q.toString()}#${encodeURIComponent(user.name)}`;
}

function trojanLink(user, host) {
  const q = new URLSearchParams({
    security: 'tls',
    sni: host,
    fp: 'chrome',
    alpn: 'http/1.1',
    type: 'ws',
    host: host,
    path: '/ws?ed=2048',
  });
  return `trojan://${user.trojan_password}@${host}:443?${q.toString()}#${encodeURIComponent(user.name)}-trojan`;
}

function b64(str) {
  let s = '';
  const bytes = enc.encode(str);
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s);
}

async function handleSub(request, env, token) {
  const user = await DB.bySubToken(env, token);
  if (!user || !user.enabled || blocked(user)) return new Response('not found', { status: 404 });
  const host = (await DB.setting(env, 'node_host')) || env.PROXY_HOST || hostOf(request, env);
  const links = [vlessLink(user, host), trojanLink(user, host)].join('\n');
  const body = b64(links);
  return new Response(body, {
    headers: {
      'content-type': 'text/plain; charset=utf-8',
      'cache-control': 'no-store',
      'profile-update-interval': '12',
      'subscription-userinfo': `upload=0; download=${user.used_bytes}; total=${user.limit_bytes || 0}; expire=${user.expires_at ? Math.floor(user.expires_at / 1000) : 0}`,
    },
  });
}

/* ───────────────────────── remote config ───────────────────────── */

const b64url = (bytes) => {
  let s = '';
  for (const b of new Uint8Array(bytes)) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};

/**
 * RFC 8785-shaped canonical JSON for the shapes this panel emits: sorted keys, no whitespace.
 * The client verifies the exact bytes, so determinism is what matters, not prettiness.
 */
function canon(value) {
  if (value === null || value === undefined) return 'null';
  if (typeof value === 'number') return Number.isFinite(value) ? JSON.stringify(value) : 'null';
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (typeof value === 'string') return JSON.stringify(value);
  if (Array.isArray(value)) return '[' + value.map(canon).join(',') + ']';
  return '{' + Object.keys(value).sort().map((k) => JSON.stringify(k) + ':' + canon(value[k])).join(',') + '}';
}

/**
 * Two settings rows hold the signing key. INSERT ... DO NOTHING makes the first writer win, so two
 * cold isolates starting at once cannot end up with a key that does not match its published half.
 */
async function rcKeys(env) {
  let priv = await DB.setting(env, 'rc_priv');
  if (!priv) {
    const pair = await crypto.subtle.generateKey({ name: 'Ed25519' }, true, ['sign', 'verify']);
    const privateJwk = JSON.stringify(await crypto.subtle.exportKey('jwk', pair.privateKey));
    const publicJwk = await crypto.subtle.exportKey('jwk', pair.publicKey);
    // One batch = one transaction: two isolates racing on a fresh database must not end up with
    // the private half of one pair and the public half of another, which no client could verify.
    await env.DB.batch([
      env.DB.prepare("INSERT INTO settings (key,value) VALUES ('rc_priv',?) ON CONFLICT(key) DO NOTHING").bind(privateJwk),
      env.DB.prepare("INSERT INTO settings (key,value) VALUES ('rc_pub',?) ON CONFLICT(key) DO NOTHING").bind(publicJwk.x),
    ]);
    priv = await DB.setting(env, 'rc_priv');
  }
  return { priv: JSON.parse(priv), pub: await DB.setting(env, 'rc_pub') };
}

/** Strictly increasing: milliseconds, but never equal to the previous value. */
async function bumpVersion(env) {
  const prev = Number(await DB.setting(env, 'rc_version', '0')) || 0;
  const next = Math.max(Date.now(), prev + 1);
  await DB.setSetting(env, 'rc_version', String(next));
  return next;
}

async function handleRc(request, env) {
  const { priv } = await rcKeys(env);
  const version = Number(await DB.setting(env, 'rc_version', '0')) || (await bumpVersion(env));
  const tag = '"' + version + '"';
  if (request.headers.get('if-none-match') === tag) {
    return new Response(null, { status: 304, headers: { etag: tag, 'cache-control': 'public, max-age=300' } });
  }

  const configs = await DB.getConfigs(env, true);
  const payload = canon({
    schemaVersion: 1,
    configVersion: version,
    profiles: configs.map((c) => ({ id: 'c' + c.id, name: c.name, uri: c.uri })),
  });

  const key = await crypto.subtle.importKey('jwk', priv, { name: 'Ed25519' }, false, ['sign']);
  const signature = b64url(await crypto.subtle.sign({ name: 'Ed25519' }, key, enc.encode(payload)));

  const body = JSON.stringify({ schemaVersion: 1, configVersion: version, payload, signature });
  if (body.length > 262144) return json({ error: 'too_large' }, 500);

  return new Response(body, {
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'public, max-age=300',
      etag: tag,
    },
  });
}

/**
 * Same rule the node applies in `lookup`: expired or over quota counts as blocked.
 * `/api/overview` called this without ever defining it, so the stats call answered 500.
 */
function blocked(u) {
  return (u.expires_at && u.expires_at < Date.now()) || (u.limit_bytes > 0 && u.used_bytes >= u.limit_bytes);
}

/* ───────────────────────── API ───────────────────────── */

async function handleApi(request, env, url) {
  const path = url.pathname;
  const method = request.method;

  if (path === '/api/state' && method === 'GET') {
    const hasPassword = !!(await DB.setting(env, 'password_hash'));
    return json({ hasPassword, authed: await isAuthed(request, env) });
  }
  if (path === '/api/setup' && method === 'POST') {
    if (await DB.setting(env, 'password_hash')) return json({ error: 'already_set' }, 400);
    const { password } = await readJson(request);
    if (!password || String(password).length < 8) return json({ error: 'short' }, 400);
    await DB.setSetting(env, 'password_hash', await sha256hex(password));
    const s = await makeSession(env);
    return json({ ok: true, token: s }, 200, { 'set-cookie': cookieHeader(s, SESSION_TTL / 1000) });
  }
  if (path === '/api/login' && method === 'POST') {
    const { password } = await readJson(request);
    const hash = await DB.setting(env, 'password_hash');
    if (!hash) return json({ error: 'not_setup' }, 400);
    if (!timingSafe(await sha256hex(password || ''), hash)) return json({ error: 'bad_password' }, 401);
    const s = await makeSession(env);
    return json({ ok: true, token: s }, 200, { 'set-cookie': cookieHeader(s, SESSION_TTL / 1000) });
  }
  if (path === '/api/logout' && method === 'POST') {
    return json({ ok: true }, 200, { 'set-cookie': `${COOKIE}=; Path=/; Max-Age=0` });
  }

  if (!(await isAuthed(request, env))) return json({ error: 'unauthorized' }, 401);

  if (path === '/api/users' && method === 'GET') return json({ users: await DB.getUsers(env) });
  if (path === '/api/users' && method === 'POST') {
    const b = await readJson(request);
    const name = String(b.name || '').trim();
    if (!/^[A-Za-z0-9_.-]{2,32}$/.test(name)) return json({ error: 'bad_name' }, 400);
    const exists = await env.DB.prepare('SELECT id FROM users WHERE name = ?').bind(name).first();
    if (exists) return json({ error: 'duplicate' }, 400);
    await DB.addUser(env, { name, limit_gb: Number(b.limit_gb) || 0, days: Number(b.days) || 0 });
    return json({ ok: true });
  }

  const m = path.match(/^\/api\/users\/(\d+)(\/reset)?$/);
  if (m) {
    const id = Number(m[1]);
    if (m[2] && method === 'POST') {
      await env.DB.prepare('UPDATE users SET used_bytes = 0 WHERE id = ?').bind(id).run();
      return json({ ok: true });
    }
    if (method === 'DELETE') {
      await env.DB.prepare('DELETE FROM users WHERE id = ?').bind(id).run();
      return json({ ok: true });
    }
    if (method === 'PATCH') {
      const b = await readJson(request);
      const user = await DB.getUser(env, id);
      if (!user) return json({ error: 'not_found' }, 404);
      const enabled = b.enabled === undefined ? user.enabled : b.enabled ? 1 : 0;
      const limit = b.limit_gb === undefined ? user.limit_bytes : Math.round(Number(b.limit_gb) * 1024 ** 3);
      const expires = b.days === undefined ? user.expires_at : Number(b.days) > 0 ? Date.now() + Number(b.days) * 86400000 : 0;
      let name = user.name;
      if (b.name !== undefined) {
        name = String(b.name).trim();
        if (!/^[A-Za-z0-9_.-]{2,32}$/.test(name)) return json({ error: 'bad_name' }, 400);
        const clash = await env.DB.prepare('SELECT id FROM users WHERE name = ? AND id != ?').bind(name, id).first();
        if (clash) return json({ error: 'duplicate' }, 400);
      }
      await env.DB.prepare('UPDATE users SET name = ?, enabled = ?, limit_bytes = ?, expires_at = ? WHERE id = ?')
        .bind(name, enabled, limit, expires, id)
        .run();
      return json({ ok: true });
    }
  }

  if (path === '/api/overview' && method === 'GET') {
    const users = await DB.getUsers(env);
    const now = Date.now();
    return json({
      total: users.length,
      active: users.filter((u) => !blocked({ ...u, expires_at: u.expires_at, limit_bytes: u.limit_bytes }) && u.enabled).length,
      used: users.reduce((a, u) => a + (u.used_bytes || 0), 0),
      host: null,
    });
  }

  if (path === '/api/password' && method === 'POST') {
    const { current, next } = await readJson(request);
    const hash = await DB.setting(env, 'password_hash');
    if (!timingSafe(await sha256hex(current || ''), hash)) return json({ error: 'bad_password' }, 401);
    if (!next || String(next).length < 8) return json({ error: 'short' }, 400);
    await DB.setSetting(env, 'password_hash', await sha256hex(next));
    return json({ ok: true });
  }

  if (path === '/api/configs' && method === 'GET') {
    return json({ configs: await DB.getConfigs(env) });
  }
  if (path === '/api/configs' && method === 'POST') {
    const b = await readJson(request);
    const name = String(b.name || '').trim().slice(0, 60);
    const uri = String(b.uri || '').trim();
    if (!name) return json({ error: 'bad_name' }, 400);
    if (!/^(vless|vmess|trojan|ss|hysteria2?|tuic|wireguard):\/\//i.test(uri)) return json({ error: 'bad_uri' }, 400);
    if (uri.length > 4000) return json({ error: 'uri_too_long' }, 400);
    const sort = Number(b.sort) || Math.floor(Date.now() / 1000) % 100000;
    await env.DB.prepare('INSERT INTO configs (name,uri,enabled,sort,created_at) VALUES (?,?,1,?,?)')
      .bind(name, uri, sort, Date.now()).run();
    const version = await bumpVersion(env);
    return json({ ok: true, version });
  }
  const cfg = path.match(/^\/api\/configs\/(\d+)$/);
  if (cfg) {
    const id = Number(cfg[1]);
    if (method === 'PATCH') {
      const b = await readJson(request);
      if (b.uri !== undefined && !/^(vless|vmess|trojan|ss|hysteria2?|tuic|wireguard):\/\//i.test(String(b.uri))) {
        return json({ error: 'bad_uri' }, 400);
      }
      if (b.uri !== undefined && String(b.uri).length > 4000) return json({ error: 'uri_too_long' }, 400);
      const row = await env.DB.prepare('SELECT * FROM configs WHERE id = ?').bind(id).first();
      if (!row) return json({ error: 'not_found' }, 404);
      await env.DB.prepare('UPDATE configs SET name = ?, uri = ?, enabled = ?, sort = ? WHERE id = ?')
        .bind(
          b.name !== undefined ? String(b.name).slice(0, 60) : row.name,
          b.uri !== undefined ? String(b.uri) : row.uri,
          b.enabled !== undefined ? (b.enabled ? 1 : 0) : row.enabled,
          b.sort !== undefined ? Number(b.sort) || 0 : row.sort,
          id,
        ).run();
      const version = await bumpVersion(env);
      return json({ ok: true, version });
    }
    if (method === 'DELETE') {
      await env.DB.prepare('DELETE FROM configs WHERE id = ?').bind(id).run();
      const version = await bumpVersion(env);
      return json({ ok: true, version });
    }
  }
  if (path === '/api/rc' && method === 'GET') {
    const { pub } = await rcKeys(env);
    const version = Number(await DB.setting(env, 'rc_version', '0')) || 0;
    const enabled = (await DB.getConfigs(env, true)).length;
    return json({ url: new URL(request.url).origin + '/rc', publicKey: pub, version, enabled });
  }
  if (path === '/api/rc/publish' && method === 'POST') {
    return json({ ok: true, version: await bumpVersion(env) });
  }
  if (path === '/api/settings' && method === 'GET') {
    return json({
      node_host: (await DB.setting(env, 'node_host')) || env.PROXY_HOST || '',
      fallback: env.PROXY_HOST || '',
    });
  }
  if (path === '/api/settings' && method === 'POST') {
    const b = await readJson(request);
    const allowed = new Set(['node_host']);
    if (!allowed.has(String(b.key))) return json({ error: 'forbidden_key' }, 400);
    const value = String(b.value || '').trim().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
    if (value && !/^[a-z0-9.-]+$/i.test(value)) return json({ error: 'bad_host' }, 400);
    await DB.setSetting(env, String(b.key), value);
    return json({ ok: true, value });
  }

  return json({ error: 'not_found' }, 404);
}

/* ───────────────────────── panel HTML ───────────────────────── */

const PANEL = "\u003c!doctype html\u003e\n\u003chtml lang=\"fa\" dir=\"rtl\"\u003e\n\u003chead\u003e\n\u003cmeta charset=\"utf-8\"\u003e\n\u003cmeta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\"\u003e\n\u003cmeta name=\"color-scheme\" content=\"dark\"\u003e\n\u003cmeta name=\"theme-color\" content=\"#080b10\"\u003e\n\u003ctitle\u003eپنل مدیریت\u003c/title\u003e\n\u003cstyle\u003e\n:root{\n  --bg:#080b10;--bg2:#0b1017;--panel:#0f151d;--panel2:#121a24;--inner:#0b1119;\n  --line:rgba(255,255,255,.07);--line2:rgba(255,255,255,.13);\n  --txt:#e8eef6;--dim:#93a4b8;--dim2:#64748b;\n  --acc:#34f5c5;--acc2:#14b8a6;--blue:#5aa9ff;\n  --ok:#34d399;--bad:#f87171;--warn:#fbbf24;\n  --r:14px;--rs:10px;\n}\n*{box-sizing:border-box}\nhtml,body{height:100%}\nbody{\n  margin:0;background:var(--bg);color:var(--txt);\n  font:14px/1.7 \"Vazirmatn\",\"Segoe UI\",Tahoma,system-ui,sans-serif;\n  -webkit-tap-highlight-color:transparent;\n  background-image:radial-gradient(900px 480px at 100% -10%,rgba(52,245,197,.07),transparent 60%),\n                   radial-gradient(700px 400px at 0% 110%,rgba(90,169,255,.06),transparent 60%);\n  background-attachment:fixed;\n}\n.ic{width:18px;height:18px;flex:none;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}\n.hidden{display:none!important}\n.num{font-variant-numeric:tabular-nums}\n.mono{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;direction:ltr;text-align:left;word-break:break-all;font-size:11.5px;color:var(--dim)}\n.muted{color:var(--dim);font-size:12.5px}\n.err{color:var(--bad);font-size:12.5px;min-height:20px;margin:8px 0 0}\n\n/* ── inputs ── */\nlabel.fld{display:block;font-size:11.5px;color:var(--dim);margin-bottom:5px}\ninput,select,textarea,button{font:inherit;color:inherit}\ninput,select,textarea{\n  width:100%;padding:10px 12px;border-radius:var(--rs);\n  border:1px solid var(--line2);background:var(--inner);\n  transition:border-color .15s,box-shadow .15s;\n}\ninput::placeholder,textarea::placeholder{color:var(--dim2)}\ninput:focus,select:focus,textarea:focus{outline:none;border-color:var(--acc);box-shadow:0 0 0 3px rgba(52,245,197,.10)}\ntextarea{min-height:92px;resize:vertical;direction:ltr;text-align:left;font-family:ui-monospace,monospace;font-size:12.5px}\n\nbutton{\n  display:inline-flex;align-items:center;gap:7px;justify-content:center;\n  padding:10px 16px;border-radius:var(--rs);border:1px solid transparent;\n  background:var(--acc);color:#04231c;font-weight:700;cursor:pointer;\n  transition:filter .15s,background .15s,border-color .15s,opacity .15s;white-space:nowrap;\n}\nbutton:hover{filter:brightness(1.06)}\nbutton:active{transform:translateY(1px)}\nbutton:disabled{opacity:.55;cursor:not-allowed;transform:none}\nbutton.ghost{background:transparent;border-color:var(--line2);color:var(--txt);font-weight:600}\nbutton.ghost:hover{background:rgba(255,255,255,.04);border-color:var(--line2);filter:none}\nbutton.danger{background:transparent;border-color:rgba(248,113,113,.35);color:var(--bad);font-weight:600}\nbutton.danger:hover{background:rgba(248,113,113,.08);filter:none}\nbutton.sm{padding:6px 11px;font-size:12px;border-radius:9px}\nbutton.icon{padding:7px;border-radius:9px;background:transparent;border-color:var(--line);color:var(--dim)}\nbutton.icon:hover{color:var(--txt);background:rgba(255,255,255,.05);filter:none}\nbutton.icon .ic{width:15px;height:15px}\n\n/* ── auth ── */\n.auth{min-height:100vh;display:grid;place-items:center;padding:24px}\n.auth-card{\n  width:100%;max-width:390px;background:linear-gradient(180deg,rgba(255,255,255,.035),transparent),var(--panel);\n  border:1px solid var(--line);border-radius:20px;padding:28px 24px;\n  box-shadow:0 30px 80px -30px #000;\n}\n.logo{display:flex;align-items:center;gap:11px;margin-bottom:22px}\n.logo .mark{\n  width:38px;height:38px;border-radius:11px;display:grid;place-items:center;flex:none;\n  background:linear-gradient(140deg,var(--acc),var(--acc2));color:#04231c;font-weight:900;font-size:17px;\n  box-shadow:0 8px 22px -8px rgba(52,245,197,.65);\n}\n.logo b{font-size:15px;display:block;line-height:1.3}\n.logo span{font-size:11.5px;color:var(--dim2)}\n.field{position:relative;margin-bottom:12px}\n.field input{padding-left:44px}\n.field .eye{\n  position:absolute;left:6px;top:50%;transform:translateY(-50%);\n  background:none;border:0;padding:7px;color:var(--dim2);cursor:pointer;border-radius:8px;\n}\n.field .eye:hover{color:var(--txt);background:none;filter:none}\n.auth .hint{font-size:11.5px;color:var(--dim2);margin-top:16px;line-height:1.8}\n\n/* ── shell ── */\n.shell{display:grid;grid-template-columns:252px 1fr;min-height:100vh}\n.side{\n  position:sticky;top:0;height:100vh;display:flex;flex-direction:column;gap:4px;\n  padding:22px 14px;border-left:1px solid var(--line);\n  background:linear-gradient(180deg,rgba(255,255,255,.02),transparent 40%),var(--bg2);\n}\n.side .logo{padding:0 8px;margin-bottom:20px}\n.nav{\n  display:flex;align-items:center;gap:11px;width:100%;padding:11px 13px;border-radius:11px;\n  background:transparent;border:1px solid transparent;color:var(--dim);\n  font-weight:600;font-size:13px;cursor:pointer;justify-content:flex-start;\n}\n.nav:hover{background:rgba(255,255,255,.04);color:var(--txt);filter:none}\n.nav.active{background:rgba(52,245,197,.09);border-color:rgba(52,245,197,.25);color:var(--acc)}\n.nav .ic{width:17px;height:17px}\n.side .foot{margin-top:auto;padding:12px 8px 0;border-top:1px solid var(--line);display:flex;flex-direction:column;gap:10px}\n.side .foot .host{font-size:11px;color:var(--dim2);font-family:ui-monospace,monospace;direction:ltr;text-align:left;word-break:break-all}\n.side .foot button{width:100%}\n\n.main{padding:26px 28px 60px;max-width:1180px;width:100%}\n.head{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-bottom:22px;flex-wrap:wrap}\n.head h1{font-size:20px;margin:0;font-weight:800;letter-spacing:-.2px}\n.head p{margin:4px 0 0;font-size:12.5px;color:var(--dim)}\n.head .actions{display:flex;gap:8px}\n\n/* ── cards \u0026 stats ── */\n.card{background:var(--panel);border:1px solid var(--line);border-radius:var(--r);padding:18px;margin-bottom:14px}\n.card\u003eh2{font-size:13.5px;margin:0 0 4px;font-weight:700;display:flex;align-items:center;gap:8px}\n.card\u003eh2+.muted{margin-bottom:14px}\n.card-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:12px;flex-wrap:wrap}\n.card-head h2{margin:0}\n.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:10px;margin-bottom:14px}\n.stat{\n  background:linear-gradient(180deg,rgba(255,255,255,.025),transparent),var(--panel);\n  border:1px solid var(--line);border-radius:12px;padding:13px 14px;position:relative;overflow:hidden;\n}\n.stat .lbl{font-size:11px;color:var(--dim2);display:flex;align-items:center;gap:6px}\n.stat .lbl .ic{width:14px;height:14px;opacity:.8}\n.stat b{display:block;font-size:20px;font-weight:800;margin-top:6px;letter-spacing:-.5px}\n.stat.accent b{color:var(--acc)}\n.stat.blue b{color:var(--blue)}\n.stat.bad b{color:var(--bad)}\n\n/* ── table ── */\n.tbl-wrap{overflow-x:auto;margin:0 -4px}\ntable{width:100%;border-collapse:separate;border-spacing:0;min-width:640px}\nth{\n  text-align:right;font-size:11px;font-weight:600;color:var(--dim2);\n  padding:0 10px 9px;white-space:nowrap;border-bottom:1px solid var(--line);\n}\ntd{padding:11px 10px;border-bottom:1px solid var(--line);font-size:13px;vertical-align:middle}\ntbody tr:last-child td{border-bottom:none}\ntbody tr:hover{background:rgba(255,255,255,.022)}\n.who{display:flex;align-items:center;gap:10px;min-width:150px}\n.av{\n  width:32px;height:32px;border-radius:10px;flex:none;display:grid;place-items:center;\n  font-weight:800;font-size:12.5px;background:rgba(90,169,255,.12);color:var(--blue);\n  border:1px solid rgba(90,169,255,.22);text-transform:uppercase;direction:ltr;\n}\n.who .nm{font-weight:700;line-height:1.25}\n.who .sub{font-size:10.5px;color:var(--dim2);font-family:ui-monospace,monospace;direction:ltr;text-align:right}\n.pill{\n  display:inline-flex;align-items:center;gap:5px;padding:3px 9px;border-radius:999px;\n  font-size:11px;font-weight:600;border:1px solid transparent;white-space:nowrap;\n}\n.pill i{width:6px;height:6px;border-radius:50%;background:currentColor;display:block}\n.pill.on{color:var(--ok);background:rgba(52,211,153,.10);border-color:rgba(52,211,153,.22)}\n.pill.off{color:var(--bad);background:rgba(248,113,113,.10);border-color:rgba(248,113,113,.22)}\n.pill.warn{color:var(--warn);background:rgba(251,191,36,.10);border-color:rgba(251,191,36,.22)}\n.bar{height:5px;border-radius:4px;background:rgba(255,255,255,.07);overflow:hidden;margin-top:6px;width:120px;max-width:100%}\n.bar i{display:block;height:100%;border-radius:4px;background:linear-gradient(90deg,var(--acc2),var(--acc))}\n.bar.warn i{background:linear-gradient(90deg,#f59e0b,var(--warn))}\n.bar.bad i{background:linear-gradient(90deg,#dc2626,var(--bad))}\n.ops{display:flex;gap:4px;justify-content:flex-start}\n\n/* ── configs ── */\n.list{display:flex;flex-direction:column;gap:9px}\n.item{\n  border:1px solid var(--line);border-radius:12px;padding:12px 14px;background:var(--inner);\n  display:flex;flex-direction:column;gap:8px;transition:border-color .15s;\n}\n.item:hover{border-color:var(--line2)}\n.item .top{display:flex;align-items:center;gap:10px;flex-wrap:wrap}\n.item .top b{font-size:13px}\n.item .mono{margin:0}\n.empty{\n  text-align:center;padding:34px 16px;color:var(--dim2);font-size:12.5px;\n  border:1px dashed var(--line2);border-radius:12px;\n}\n.empty .ic{width:26px;height:26px;opacity:.5;margin-bottom:8px}\n.empty b{display:block;color:var(--dim);font-size:13px;margin-bottom:3px}\n\n/* ── app tab ── */\npre{\n  margin:0;padding:14px;overflow:auto;border-radius:11px;border:1px solid var(--line);\n  background:var(--inner);font-family:ui-monospace,monospace;font-size:12px;\n  direction:ltr;text-align:left;line-height:1.8;color:#c9d6e4;\n}\ncode{background:rgba(255,255,255,.06);border-radius:6px;padding:1px 6px;font-family:ui-monospace,monospace;font-size:11.5px;direction:ltr;display:inline-block}\n.kv{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:10px;margin-bottom:14px}\n.kv .stat b{font-size:14px;font-family:ui-monospace,monospace;direction:ltr;text-align:left;word-break:break-all;font-weight:600}\n\n/* ── dialog / toast ── */\ndialog{\n  border:1px solid var(--line2);border-radius:16px;background:var(--panel);color:var(--txt);\n  padding:0;max-width:440px;width:92%;box-shadow:0 40px 90px -30px #000;\n}\ndialog::backdrop{background:rgba(3,6,10,.72);backdrop-filter:blur(3px)}\ndialog .dlg-head{padding:18px 20px 0;display:flex;align-items:center;justify-content:space-between}\ndialog .dlg-head h2{margin:0;font-size:14.5px}\ndialog .dlg-body{padding:16px 20px 4px;display:flex;flex-direction:column;gap:12px}\ndialog .dlg-foot{padding:12px 20px 18px;display:flex;gap:8px;justify-content:flex-start}\n.toast{\n  position:fixed;bottom:22px;left:50%;transform:translate(-50%,14px);\n  background:#0d2019;border:1px solid rgba(52,245,197,.28);color:#d6fff2;\n  padding:11px 18px;border-radius:11px;font-size:13px;font-weight:600;\n  opacity:0;pointer-events:none;transition:opacity .22s,transform .22s;z-index:40;box-shadow:0 18px 40px -18px #000;\n}\n.toast.show{opacity:1;transform:translate(-50%,0)}\n.toast.err{border-color:rgba(248,113,113,.35);background:#221013;color:#ffd9d9}\n.search{position:relative;min-width:200px;flex:1;max-width:280px}\n.search input{padding-right:38px}\n.search .ic{position:absolute;right:12px;top:50%;transform:translateY(-50%);color:var(--dim2);width:15px;height:15px;pointer-events:none}\n\n@media (max-width:920px){\n  .shell{grid-template-columns:1fr}\n  .side{position:sticky;top:0;z-index:20;height:auto;flex-direction:row;align-items:center;gap:6px;\n    padding:10px 12px;border-left:0;border-bottom:1px solid var(--line);overflow-x:auto;background:rgba(11,16,23,.95);backdrop-filter:blur(10px)}\n  .side .logo{margin:0;padding:0;flex:none}\n  .side .logo span{display:none}\n  .side .logo .mark{width:32px;height:32px;border-radius:9px}\n  .nav{width:auto;padding:8px 11px;font-size:12px}\n  .nav span{display:none}\n  .side .foot{margin:0 0 0 auto;border:0;padding:0;flex-direction:row;align-items:center}\n  .side .foot .host{display:none}\n  .side .foot button{width:auto;padding:7px 11px}\n  .main{padding:18px 14px 48px}\n  .head h1{font-size:17px}\n  th,td{padding:9px 8px}\n}\n\u003c/style\u003e\n\u003c/head\u003e\n\u003cbody\u003e\n\n\u003csvg width=\"0\" height=\"0\" style=\"position:absolute\" aria-hidden=\"true\"\u003e\n  \u003csymbol id=\"i-users\" viewBox=\"0 0 24 24\"\u003e\u003ccircle cx=\"9\" cy=\"8\" r=\"3.2\"/\u003e\u003cpath d=\"M3.5 19c.6-3.2 2.9-5 5.5-5s4.9 1.8 5.5 5\"/\u003e\u003cpath d=\"M16.2 6.4a3 3 0 0 1 0 6\"/\u003e\u003cpath d=\"M17.6 19c-.2-1.6-.7-3-1.5-4\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-layers\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"M12 3 3 8l9 5 9-5-9-5Z\"/\u003e\u003cpath d=\"m3 13 9 5 9-5\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-link\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"M10.5 13.5a4 4 0 0 0 5.7 0l2.3-2.3a4 4 0 0 0-5.7-5.7L11.7 6.6\"/\u003e\u003cpath d=\"M13.5 10.5a4 4 0 0 0-5.7 0l-2.3 2.3a4 4 0 0 0 5.7 5.7l1.1-1.1\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-sliders\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"M4 7h10M18 7h2M4 17h4M12 17h8\"/\u003e\u003ccircle cx=\"16\" cy=\"7\" r=\"2\"/\u003e\u003ccircle cx=\"10\" cy=\"17\" r=\"2\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-plus\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"M12 5v14M5 12h14\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-refresh\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"M20 11a8 8 0 1 0-2.3 5.7\"/\u003e\u003cpath d=\"M20 5v6h-6\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-copy\" viewBox=\"0 0 24 24\"\u003e\u003crect x=\"9\" y=\"9\" width=\"11\" height=\"11\" rx=\"2\"/\u003e\u003cpath d=\"M5 15V5a2 2 0 0 1 2-2h8\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-trash\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"M4 7h16M9 7V5h6v2M6.5 7l1 13h9l1-13\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-edit\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"M4 20h4L20 8l-4-4L4 16v4Z\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-power\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"M12 4v7\"/\u003e\u003cpath d=\"M7.5 7.5a7 7 0 1 0 9 0\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-search\" viewBox=\"0 0 24 24\"\u003e\u003ccircle cx=\"11\" cy=\"11\" r=\"6\"/\u003e\u003cpath d=\"m20 20-3.5-3.5\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-logout\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"M14 4h5v16h-5\"/\u003e\u003cpath d=\"m10 8-4 4 4 4\"/\u003e\u003cpath d=\"M6 12h9\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-up\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"m6 14 6-6 6 6\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-down\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"m6 10 6 6 6-6\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-eye\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6-10-6-10-6Z\"/\u003e\u003ccircle cx=\"12\" cy=\"12\" r=\"2.6\"/\u003e\u003c/symbol\u003e\n  \u003csymbol id=\"i-box\" viewBox=\"0 0 24 24\"\u003e\u003cpath d=\"M21 8v8a2 2 0 0 1-1 1.7l-7 4a2 2 0 0 1-2 0l-7-4A2 2 0 0 1 3 16V8a2 2 0 0 1 1-1.7l7-4a2 2 0 0 1 2 0l7 4A2 2 0 0 1 21 8Z\"/\u003e\u003cpath d=\"m3.3 7 8.7 5 8.7-5M12 22V12\"/\u003e\u003c/symbol\u003e\n\u003c/svg\u003e\n\n\u003c!-- ══════════ ورود ══════════ --\u003e\n\u003csection id=\"auth\" class=\"auth hidden\"\u003e\n  \u003cdiv class=\"auth-card\"\u003e\n    \u003cdiv class=\"logo\"\u003e\n      \u003cdiv class=\"mark\"\u003eم\u003c/div\u003e\n      \u003cdiv\u003e\u003cb\u003eپنل مدیریت\u003c/b\u003e\u003cspan id=\"authHost\"\u003e—\u003c/span\u003e\u003c/div\u003e\n    \u003c/div\u003e\n    \u003cdiv id=\"setupBox\" class=\"hidden\"\u003e\n      \u003cp class=\"muted\" style=\"margin:0 0 14px\"\u003eاولین ورود است. یک رمز قوی برای پنل بساز.\u003c/p\u003e\n      \u003cdiv class=\"field\"\u003e\n        \u003cinput id=\"spass\" type=\"password\" placeholder=\"رمز جدید (حداقل ۸ کاراکتر)\" autocomplete=\"new-password\"\u003e\n      \u003c/div\u003e\n      \u003cbutton style=\"width:100%\" onclick=\"setup()\" id=\"setupBtn\"\u003eساخت رمز\u003c/button\u003e\n    \u003c/div\u003e\n    \u003cdiv id=\"loginBox\" class=\"hidden\"\u003e\n      \u003cp class=\"muted\" style=\"margin:0 0 14px\"\u003eبرای ادامه رمز پنل را وارد کن.\u003c/p\u003e\n      \u003cdiv class=\"field\"\u003e\n        \u003cinput id=\"lpass\" type=\"password\" placeholder=\"رمز عبور\" autocomplete=\"current-password\"\u003e\n        \u003cbutton class=\"eye\" type=\"button\" onclick=\"togglePass(\u0027lpass\u0027,this)\" title=\"نمایش رمز\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-eye\"/\u003e\u003c/svg\u003e\u003c/button\u003e\n      \u003c/div\u003e\n      \u003cbutton style=\"width:100%\" onclick=\"login()\" id=\"loginBtn\"\u003eورود\u003c/button\u003e\n    \u003c/div\u003e\n    \u003cp class=\"err\" id=\"authMsg\"\u003e\u003c/p\u003e\n    \u003cp class=\"hint\"\u003eاین صفحه روی ورکر خودت اجرا می‌شود؛ رمز فقط در دیتابیس D1 همان حساب ذخیره می‌شود.\u003c/p\u003e\n  \u003c/div\u003e\n\u003c/section\u003e\n\n\u003c!-- ══════════ پنل ══════════ --\u003e\n\u003cdiv id=\"app\" class=\"shell hidden\"\u003e\n  \u003caside class=\"side\"\u003e\n    \u003cdiv class=\"logo\"\u003e\n      \u003cdiv class=\"mark\"\u003eم\u003c/div\u003e\n      \u003cdiv\u003e\u003cb\u003eپنل مدیریت\u003c/b\u003e\u003cspan\u003eVLESS · Trojan\u003c/span\u003e\u003c/div\u003e\n    \u003c/div\u003e\n    \u003cbutton class=\"nav active\" data-tab=\"users\" onclick=\"tab(\u0027users\u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-users\"/\u003e\u003c/svg\u003e\u003cspan\u003eکاربران\u003c/span\u003e\u003c/button\u003e\n    \u003cbutton class=\"nav\" data-tab=\"configs\" onclick=\"tab(\u0027configs\u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-layers\"/\u003e\u003c/svg\u003e\u003cspan\u003eکانفیگ‌ها\u003c/span\u003e\u003c/button\u003e\n    \u003cbutton class=\"nav\" data-tab=\"app\" onclick=\"tab(\u0027app\u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-link\"/\u003e\u003c/svg\u003e\u003cspan\u003eاتصال اپ\u003c/span\u003e\u003c/button\u003e\n    \u003cbutton class=\"nav\" data-tab=\"settings\" onclick=\"tab(\u0027settings\u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-sliders\"/\u003e\u003c/svg\u003e\u003cspan\u003eتنظیمات\u003c/span\u003e\u003c/button\u003e\n    \u003cdiv class=\"foot\"\u003e\n      \u003cdiv class=\"host\" id=\"host\"\u003e—\u003c/div\u003e\n      \u003cbutton class=\"ghost sm\" onclick=\"logout()\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-logout\"/\u003e\u003c/svg\u003eخروج\u003c/button\u003e\n    \u003c/div\u003e\n  \u003c/aside\u003e\n\n  \u003cmain class=\"main\"\u003e\n    \u003c!-- ───────── کاربران ───────── --\u003e\n    \u003csection id=\"pane-users\"\u003e\n      \u003cdiv class=\"head\"\u003e\n        \u003cdiv\u003e\u003ch1\u003eکاربران\u003c/h1\u003e\u003cp\u003eهر کاربر یک UUID و یک لینک ساب مستقل دارد.\u003c/p\u003e\u003c/div\u003e\n        \u003cdiv class=\"actions\"\u003e\u003cbutton class=\"ghost sm\" onclick=\"loadUsers(true)\" id=\"refUsers\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-refresh\"/\u003e\u003c/svg\u003eتازه‌سازی\u003c/button\u003e\u003c/div\u003e\n      \u003c/div\u003e\n\n      \u003cdiv class=\"stats\"\u003e\n        \u003cdiv class=\"stat accent\"\u003e\u003cdiv class=\"lbl\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-users\"/\u003e\u003c/svg\u003eکاربران\u003c/div\u003e\u003cb id=\"s-users\" class=\"num\"\u003e۰\u003c/b\u003e\u003c/div\u003e\n        \u003cdiv class=\"stat blue\"\u003e\u003cdiv class=\"lbl\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-power\"/\u003e\u003c/svg\u003eفعال\u003c/div\u003e\u003cb id=\"s-active\" class=\"num\"\u003e۰\u003c/b\u003e\u003c/div\u003e\n        \u003cdiv class=\"stat\"\u003e\u003cdiv class=\"lbl\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-box\"/\u003e\u003c/svg\u003eمصرف کل\u003c/div\u003e\u003cb id=\"s-used\" class=\"num\"\u003e۰\u003c/b\u003e\u003c/div\u003e\n        \u003cdiv class=\"stat\"\u003e\u003cdiv class=\"lbl\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-link\"/\u003e\u003c/svg\u003eآدرس نود\u003c/div\u003e\u003cb id=\"s-node\" style=\"font-size:12.5px;word-break:break-all;direction:ltr;text-align:right\"\u003e—\u003c/b\u003e\u003c/div\u003e\n      \u003c/div\u003e\n\n      \u003cdiv class=\"card\"\u003e\n        \u003ch2\u003e\u003csvg class=\"ic\" style=\"color:var(--acc)\"\u003e\u003cuse href=\"#i-plus\"/\u003e\u003c/svg\u003eافزودن کاربر\u003c/h2\u003e\n        \u003cdiv class=\"muted\"\u003eحجم و روز را خالی بگذار تا نامحدود باشد.\u003c/div\u003e\n        \u003cdiv style=\"display:grid;grid-template-columns:1.4fr .8fr .8fr auto;gap:10px;align-items:end\" id=\"addUserRow\"\u003e\n          \u003cdiv\u003e\u003clabel class=\"fld\"\u003eنام کاربری\u003c/label\u003e\u003cinput id=\"u-name\" placeholder=\"ali\" autocomplete=\"off\"\u003e\u003c/div\u003e\n          \u003cdiv\u003e\u003clabel class=\"fld\"\u003eحجم (GB)\u003c/label\u003e\u003cinput id=\"u-gb\" type=\"number\" min=\"0\" step=\"0.5\" placeholder=\"۰\"\u003e\u003c/div\u003e\n          \u003cdiv\u003e\u003clabel class=\"fld\"\u003eاعتبار (روز)\u003c/label\u003e\u003cinput id=\"u-days\" type=\"number\" min=\"0\" step=\"1\" placeholder=\"۰\"\u003e\u003c/div\u003e\n          \u003cdiv\u003e\u003cbutton onclick=\"addUser(this)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-plus\"/\u003e\u003c/svg\u003eافزودن\u003c/button\u003e\u003c/div\u003e\n        \u003c/div\u003e\n        \u003cp class=\"err\" id=\"u-msg\"\u003e\u003c/p\u003e\n      \u003c/div\u003e\n\n      \u003cdiv class=\"card\"\u003e\n        \u003cdiv class=\"card-head\"\u003e\n          \u003ch2\u003eلیست کاربران\u003c/h2\u003e\n          \u003cdiv class=\"search\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-search\"/\u003e\u003c/svg\u003e\u003cinput id=\"q\" placeholder=\"جست‌وجوی نام…\" oninput=\"onSearch(this.value)\"\u003e\u003c/div\u003e\n        \u003c/div\u003e\n        \u003cdiv class=\"tbl-wrap\"\u003e\n          \u003ctable\u003e\n            \u003cthead\u003e\u003ctr\u003e\u003cth\u003eکاربر\u003c/th\u003e\u003cth\u003eوضعیت\u003c/th\u003e\u003cth\u003eمصرف\u003c/th\u003e\u003cth\u003eانقضا\u003c/th\u003e\u003cth style=\"width:150px\"\u003eعملیات\u003c/th\u003e\u003c/tr\u003e\u003c/thead\u003e\n            \u003ctbody id=\"rows\"\u003e\u003c/tbody\u003e\n          \u003c/table\u003e\n        \u003c/div\u003e\n        \u003cdiv id=\"u-empty\"\u003e\u003c/div\u003e\n      \u003c/div\u003e\n    \u003c/section\u003e\n\n    \u003c!-- ───────── کانفیگ‌ها ───────── --\u003e\n    \u003csection id=\"pane-configs\" class=\"hidden\"\u003e\n      \u003cdiv class=\"head\"\u003e\n        \u003cdiv\u003e\u003ch1\u003eکانفیگ‌های اپ\u003c/h1\u003e\u003cp\u003eهر کانفیگ اینجا، در صفحه‌ی اصلی همه‌ی کاربران اپ ظاهر می‌شود.\u003c/p\u003e\u003c/div\u003e\n        \u003cdiv class=\"actions\"\u003e\u003cbutton class=\"ghost sm\" onclick=\"publish(this)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-refresh\"/\u003e\u003c/svg\u003eانتشار مجدد\u003c/button\u003e\u003c/div\u003e\n      \u003c/div\u003e\n\n      \u003cdiv class=\"card\"\u003e\n        \u003ch2\u003e\u003csvg class=\"ic\" style=\"color:var(--acc)\"\u003e\u003cuse href=\"#i-plus\"/\u003e\u003c/svg\u003eافزودن کانفیگ\u003c/h2\u003e\n        \u003cdiv style=\"display:grid;grid-template-columns:200px 1fr auto;gap:10px;align-items:end\" id=\"addCfgRow\"\u003e\n          \u003cdiv\u003e\u003clabel class=\"fld\"\u003eنام\u003c/label\u003e\u003cinput id=\"c-name\" placeholder=\"آلمان - فرانکفورت\" autocomplete=\"off\"\u003e\u003c/div\u003e\n          \u003cdiv\u003e\u003clabel class=\"fld\"\u003eلینک\u003c/label\u003e\u003cinput id=\"c-uri\" placeholder=\"vless://…\" style=\"direction:ltr;text-align:left;font-family:ui-monospace,monospace;font-size:12.5px\"\u003e\u003c/div\u003e\n          \u003cdiv\u003e\u003cbutton onclick=\"addConfig(this)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-plus\"/\u003e\u003c/svg\u003eافزودن\u003c/button\u003e\u003c/div\u003e\n        \u003c/div\u003e\n        \u003cp class=\"err\" id=\"c-msg\"\u003e\u003c/p\u003e\n        \u003cdetails style=\"margin-top:6px\"\u003e\n          \u003csummary class=\"muted\" style=\"cursor:pointer;padding:6px 0\"\u003eافزودن گروهی (هر خط یک لینک)\u003c/summary\u003e\n          \u003ctextarea id=\"c-bulk\" placeholder=\"vless://...\nvless://...\ntrojan://...\" style=\"margin-top:10px\"\u003e\u003c/textarea\u003e\n          \u003cdiv style=\"display:flex;align-items:center;gap:10px;margin-top:10px\"\u003e\n            \u003cbutton class=\"ghost\" onclick=\"addBulk(this)\"\u003eافزودن همه\u003c/button\u003e\n            \u003cspan class=\"muted\"\u003eنام هر کانفیگ از \u003ccode\u003e#name\u003c/code\u003e آخر لینک برداشته می‌شود.\u003c/span\u003e\n          \u003c/div\u003e\n        \u003c/details\u003e\n      \u003c/div\u003e\n\n      \u003cdiv class=\"card\"\u003e\n        \u003cdiv class=\"card-head\"\u003e\u003ch2\u003eکانفیگ‌ها\u003c/h2\u003e\u003cbutton class=\"ghost sm\" onclick=\"loadConfigs(true)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-refresh\"/\u003e\u003c/svg\u003eتازه‌سازی\u003c/button\u003e\u003c/div\u003e\n        \u003cdiv class=\"list\" id=\"cfg-list\"\u003e\u003c/div\u003e\n        \u003cdiv id=\"c-empty\"\u003e\u003c/div\u003e\n      \u003c/div\u003e\n    \u003c/section\u003e\n\n    \u003c!-- ───────── اتصال اپ ───────── --\u003e\n    \u003csection id=\"pane-app\" class=\"hidden\"\u003e\n      \u003cdiv class=\"head\"\u003e\u003cdiv\u003e\u003ch1\u003eاتصال اپ\u003c/h1\u003e\u003cp\u003eاین دو خط را در \u003ccode\u003elocal.properties\u003c/code\u003e پروژه بگذار و یک‌بار بیلد کن.\u003c/p\u003e\u003c/div\u003e\u003c/div\u003e\n\n      \u003cdiv class=\"card\"\u003e\n        \u003ch2\u003e\u003csvg class=\"ic\" style=\"color:var(--acc)\"\u003e\u003cuse href=\"#i-link\"/\u003e\u003c/svg\u003eخطوط بیلد\u003c/h2\u003e\n        \u003cpre id=\"rc-lines\"\u003e…\u003c/pre\u003e\n        \u003cdiv style=\"display:flex;gap:8px;margin-top:12px\"\u003e\n          \u003cbutton class=\"sm\" onclick=\"copyEl(\u0027rc-lines\u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-copy\"/\u003e\u003c/svg\u003eکپی هر دو خط\u003c/button\u003e\n          \u003cbutton class=\"ghost sm\" onclick=\"copyEl(\u0027rc-url\u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-copy\"/\u003e\u003c/svg\u003eکپی آدرس\u003c/button\u003e\n        \u003c/div\u003e\n      \u003c/div\u003e\n\n      \u003cdiv class=\"kv\"\u003e\n        \u003cdiv class=\"stat\"\u003e\u003cdiv class=\"lbl\"\u003eآدرس RC\u003c/div\u003e\u003cb id=\"rc-url\"\u003e—\u003c/b\u003e\u003c/div\u003e\n        \u003cdiv class=\"stat\"\u003e\u003cdiv class=\"lbl\"\u003eنسخه‌ی فعال\u003c/div\u003e\u003cb id=\"rc-version\"\u003e—\u003c/b\u003e\u003c/div\u003e\n        \u003cdiv class=\"stat\"\u003e\u003cdiv class=\"lbl\"\u003eکانفیگ فعال\u003c/div\u003e\u003cb id=\"rc-count\"\u003e—\u003c/b\u003e\u003c/div\u003e\n      \u003c/div\u003e\n\n      \u003cdiv class=\"card\"\u003e\n        \u003ch2\u003eکلید عمومی امضا\u003c/h2\u003e\n        \u003cp class=\"muted\"\u003eداخل خود اپ کامپایل می‌شود. اگر کسی سرور را عوض کند، امضا نمی‌خواند و کانفیگ رد می‌شود.\u003c/p\u003e\n        \u003cpre id=\"rc-key\"\u003e…\u003c/pre\u003e\n        \u003cdiv style=\"margin-top:12px\"\u003e\u003cbutton class=\"sm\" onclick=\"copyEl(\u0027rc-key\u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-copy\"/\u003e\u003c/svg\u003eکپی کلید\u003c/button\u003e\u003c/div\u003e\n      \u003c/div\u003e\n    \u003c/section\u003e\n\n    \u003c!-- ───────── تنظیمات ───────── --\u003e\n    \u003csection id=\"pane-settings\" class=\"hidden\"\u003e\n      \u003cdiv class=\"head\"\u003e\u003cdiv\u003e\u003ch1\u003eتنظیمات\u003c/h1\u003e\u003cp\u003eآدرس نود و رمز پنل.\u003c/p\u003e\u003c/div\u003e\u003c/div\u003e\n\n      \u003cdiv class=\"card\"\u003e\n        \u003ch2\u003e\u003csvg class=\"ic\" style=\"color:var(--acc)\"\u003e\u003cuse href=\"#i-link\"/\u003e\u003c/svg\u003eآدرس نود\u003c/h2\u003e\n        \u003cp class=\"muted\"\u003eکانفیگ‌هایی که به کاربران داده می‌شود به این دامنه وصل می‌شوند. بدون \u003ccode\u003ehttps://\u003c/code\u003e و بدون پورت.\u003c/p\u003e\n        \u003cdiv style=\"display:grid;grid-template-columns:1fr auto;gap:10px;align-items:end\"\u003e\n          \u003cdiv\u003e\u003clabel class=\"fld\"\u003eدامنه‌ی نود\u003c/label\u003e\u003cinput id=\"node-host\" placeholder=\"node.example.com\" style=\"direction:ltr;text-align:left;font-family:ui-monospace,monospace\"\u003e\u003c/div\u003e\n          \u003cdiv\u003e\u003cbutton onclick=\"saveNode(this)\"\u003eذخیره\u003c/button\u003e\u003c/div\u003e\n        \u003c/div\u003e\n        \u003cp class=\"err\" id=\"n-msg\"\u003e\u003c/p\u003e\n      \u003c/div\u003e\n\n      \u003cdiv class=\"card\"\u003e\n        \u003ch2\u003eتغییر رمز پنل\u003c/h2\u003e\n        \u003cp class=\"muted\"\u003eبعد از تغییر، همه‌ی نشست‌های باز باطل می‌شوند.\u003c/p\u003e\n        \u003cdiv style=\"display:grid;grid-template-columns:1fr 1fr auto;gap:10px;align-items:end\"\u003e\n          \u003cdiv\u003e\u003clabel class=\"fld\"\u003eرمز فعلی\u003c/label\u003e\u003cinput id=\"pw-cur\" type=\"password\" autocomplete=\"current-password\"\u003e\u003c/div\u003e\n          \u003cdiv\u003e\u003clabel class=\"fld\"\u003eرمز جدید\u003c/label\u003e\u003cinput id=\"pw-new\" type=\"password\" autocomplete=\"new-password\"\u003e\u003c/div\u003e\n          \u003cdiv\u003e\u003cbutton onclick=\"changePassword(this)\"\u003eتغییر\u003c/button\u003e\u003c/div\u003e\n        \u003c/div\u003e\n        \u003cp class=\"err\" id=\"pw-msg\"\u003e\u003c/p\u003e\n      \u003c/div\u003e\n    \u003c/section\u003e\n  \u003c/main\u003e\n\u003c/div\u003e\n\n\u003cdialog id=\"userDlg\"\u003e\n  \u003cdiv class=\"dlg-head\"\u003e\u003ch2\u003eویرایش کاربر\u003c/h2\u003e\u003cbutton class=\"icon\" onclick=\"closeDlg(\u0027userDlg\u0027)\"\u003e\u003csvg class=\"ic\" style=\"transform:rotate(45deg)\"\u003e\u003cuse href=\"#i-plus\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u003c/div\u003e\n  \u003cdiv class=\"dlg-body\"\u003e\n    \u003cdiv\u003e\u003clabel class=\"fld\"\u003eنام\u003c/label\u003e\u003cinput id=\"e-name\" autocomplete=\"off\"\u003e\u003c/div\u003e\n    \u003cdiv style=\"display:grid;grid-template-columns:1fr 1fr;gap:10px\"\u003e\n      \u003cdiv\u003e\u003clabel class=\"fld\"\u003eحجم (GB، ۰ = نامحدود)\u003c/label\u003e\u003cinput id=\"e-gb\" type=\"number\" min=\"0\" step=\"0.5\"\u003e\u003c/div\u003e\n      \u003cdiv\u003e\u003clabel class=\"fld\"\u003eروز اعتبار (۰ = بی‌پایان)\u003c/label\u003e\u003cinput id=\"e-days\" type=\"number\" min=\"0\" step=\"1\"\u003e\u003c/div\u003e\n    \u003c/div\u003e\n    \u003cp class=\"err\" id=\"e-msg\"\u003e\u003c/p\u003e\n  \u003c/div\u003e\n  \u003cdiv class=\"dlg-foot\"\u003e\u003cbutton onclick=\"saveEdit(this)\"\u003eذخیره\u003c/button\u003e\u003cbutton class=\"ghost\" onclick=\"closeDlg(\u0027userDlg\u0027)\"\u003eانصراف\u003c/button\u003e\u003c/div\u003e\n\u003c/dialog\u003e\n\n\u003cdialog id=\"cfgDlg\"\u003e\n  \u003cdiv class=\"dlg-head\"\u003e\u003ch2\u003eویرایش کانفیگ\u003c/h2\u003e\u003cbutton class=\"icon\" onclick=\"closeDlg(\u0027cfgDlg\u0027)\"\u003e\u003csvg class=\"ic\" style=\"transform:rotate(45deg)\"\u003e\u003cuse href=\"#i-plus\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u003c/div\u003e\n  \u003cdiv class=\"dlg-body\"\u003e\n    \u003cdiv\u003e\u003clabel class=\"fld\"\u003eنام\u003c/label\u003e\u003cinput id=\"k-name\" autocomplete=\"off\"\u003e\u003c/div\u003e\n    \u003cdiv\u003e\u003clabel class=\"fld\"\u003eلینک\u003c/label\u003e\u003ctextarea id=\"k-uri\" style=\"min-height:88px\"\u003e\u003c/textarea\u003e\u003c/div\u003e\n    \u003cp class=\"err\" id=\"k-msg\"\u003e\u003c/p\u003e\n  \u003c/div\u003e\n  \u003cdiv class=\"dlg-foot\"\u003e\u003cbutton onclick=\"saveCfg(this)\"\u003eذخیره\u003c/button\u003e\u003cbutton class=\"ghost\" onclick=\"closeDlg(\u0027cfgDlg\u0027)\"\u003eانصراف\u003c/button\u003e\u003c/div\u003e\n\u003c/dialog\u003e\n\n\u003cdiv id=\"toast\" class=\"toast\"\u003e\u003c/div\u003e\n\n\u003cscript\u003e\nvar S = { users: [], configs: [], rc: null, q: \u0027\u0027, editUser: null, editCfg: null, editingUserSnapshot: null };\n\nfunction el(id){ return document.getElementById(id); }\nfunction esc(s){ return String(s == null ? \u0027\u0027 : s).replace(/[\u0026\u003c\u003e\"\u0027]/g, function(c){ return ({\u0027\u0026\u0027:\u0027\u0026amp;\u0027,\u0027\u003c\u0027:\u0027\u0026lt;\u0027,\u0027\u003e\u0027:\u0027\u0026gt;\u0027,\u0027\"\u0027:\u0027\u0026quot;\u0027,\"\u0027\":\u0027\u0026#39;\u0027})[c]; }); }\nfunction onSearch(v){ S.q = v; renderUsers(); }\n\nfunction fa(n){ return Number(n || 0).toLocaleString(\u0027fa-IR\u0027); }\nfunction size(bytes){\n  var n = Number(bytes || 0);\n  if (n \u003c= 0) return \u0027۰\u0027;\n  var u = [\u0027B\u0027,\u0027KB\u0027,\u0027MB\u0027,\u0027GB\u0027,\u0027TB\u0027], i = 0;\n  while (n \u003e= 1024 \u0026\u0026 i \u003c u.length - 1) { n = n / 1024; i++; }\n  return (i === 0 ? n : n.toFixed(n \u003e= 100 ? 0 : n \u003e= 10 ? 1 : 2)) + \u0027 \u0027 + u[i];\n}\nfunction limit(bytes){ return Number(bytes) \u003e 0 ? size(bytes) : \u0027نامحدود\u0027; }\nfunction faDate(ts){\n  if (!ts) return \u0027بی‌پایان\u0027;\n  try { return new Date(ts).toLocaleDateString(\u0027fa-IR\u0027); } catch (e) { return \u0027—\u0027; }\n}\nfunction toast(msg, isErr){\n  var t = el(\u0027toast\u0027);\n  t.textContent = msg;\n  t.className = \u0027toast show\u0027 + (isErr ? \u0027 err\u0027 : \u0027\u0027);\n  clearTimeout(t._h);\n  t._h = setTimeout(function(){ t.className = \u0027toast\u0027; }, 2400);\n}\nfunction busy(btn, on){\n  if (!btn) return;\n  btn.disabled = !!on;\n}\nfunction togglePass(id, btn){\n  var f = el(id);\n  f.type = f.type === \u0027password\u0027 ? \u0027text\u0027 : \u0027password\u0027;\n}\nfunction closeDlg(id){ el(id).close(); }\n\nasync function api(path, opt){\n  var res;\n  try {\n    res = await fetch(path, Object.assign({ credentials: \u0027include\u0027, headers: { \u0027content-type\u0027: \u0027application/json\u0027 } }, opt || {}));\n  } catch (e) {\n    return { ok: false, status: 0, data: { error: \u0027network\u0027 } };\n  }\n  var data = {};\n  try { data = await res.json(); } catch (e) {}\n  if (res.status === 401 \u0026\u0026 path !== \u0027/api/state\u0027 \u0026\u0026 path !== \u0027/api/login\u0027 \u0026\u0026 path !== \u0027/api/setup\u0027) showAuth(\u0027login\u0027);\n  return { ok: res.ok, status: res.status, data: data };\n}\n\n/* ───────── auth ───────── */\nasync function boot(){\n  el(\u0027authHost\u0027).textContent = location.host;\n  el(\u0027host\u0027).textContent = location.host;\n  var st = await api(\u0027/api/state\u0027);\n  if (!st.data.hasPassword) { showAuth(\u0027setup\u0027); return; }\n  if (st.data.authed) { showApp(); return; }\n  showAuth(\u0027login\u0027);\n}\nfunction showAuth(mode){\n  el(\u0027app\u0027).classList.add(\u0027hidden\u0027);\n  el(\u0027auth\u0027).classList.remove(\u0027hidden\u0027);\n  el(\u0027setupBox\u0027).classList.toggle(\u0027hidden\u0027, mode !== \u0027setup\u0027);\n  el(\u0027loginBox\u0027).classList.toggle(\u0027hidden\u0027, mode !== \u0027login\u0027);\n  el(\u0027authMsg\u0027).textContent = \u0027\u0027;\n  setTimeout(function(){ var f = mode === \u0027setup\u0027 ? el(\u0027spass\u0027) : el(\u0027lpass\u0027); if (f) f.focus(); }, 60);\n}\nasync function setup(){\n  var p = el(\u0027spass\u0027).value;\n  if (p.length \u003c 8) { el(\u0027authMsg\u0027).textContent = \u0027رمز حداقل ۸ کاراکتر باشد.\u0027; return; }\n  busy(el(\u0027setupBtn\u0027), true);\n  var r = await api(\u0027/api/setup\u0027, { method: \u0027POST\u0027, body: JSON.stringify({ password: p }) });\n  busy(el(\u0027setupBtn\u0027), false);\n  if (r.ok) { showApp(); } else { el(\u0027authMsg\u0027).textContent = r.data.error === \u0027already_set\u0027 ? \u0027رمز قبلاً ساخته شده.\u0027 : \u0027خطا: \u0027 + (r.data.error || r.status); }\n}\nasync function login(){\n  var p = el(\u0027lpass\u0027).value;\n  busy(el(\u0027loginBtn\u0027), true);\n  var r = await api(\u0027/api/login\u0027, { method: \u0027POST\u0027, body: JSON.stringify({ password: p }) });\n  busy(el(\u0027loginBtn\u0027), false);\n  if (r.ok) { showApp(); } else { el(\u0027authMsg\u0027).textContent = \u0027رمز اشتباه است.\u0027; el(\u0027lpass\u0027).select(); }\n}\nasync function logout(){\n  await api(\u0027/api/logout\u0027, { method: \u0027POST\u0027 });\n  location.reload();\n}\nfunction showApp(){\n  el(\u0027auth\u0027).classList.add(\u0027hidden\u0027);\n  el(\u0027app\u0027).classList.remove(\u0027hidden\u0027);\n  tab(\u0027users\u0027);\n}\nfunction tab(name){\n  [\u0027users\u0027,\u0027configs\u0027,\u0027app\u0027,\u0027settings\u0027].forEach(function(p){\n    var pane = el(\u0027pane-\u0027 + p);\n    if (pane) pane.classList.toggle(\u0027hidden\u0027, p !== name);\n  });\n  var navs = document.querySelectorAll(\u0027.nav\u0027);\n  for (var i = 0; i \u003c navs.length; i++) navs[i].classList.toggle(\u0027active\u0027, navs[i].getAttribute(\u0027data-tab\u0027) === name);\n  if (name === \u0027app\u0027) loadRc();\n  if (name === \u0027settings\u0027) loadSettings();\n  if (name === \u0027users\u0027) loadUsers();\n  if (name === \u0027configs\u0027) loadConfigs();\n}\n\n/* ───────── users ───────── */\nasync function loadUsers(manual){\n  var btn = manual ? el(\u0027refUsers\u0027) : null;\n  busy(btn, true);\n  var r = await api(\u0027/api/users\u0027);\n  busy(btn, false);\n  if (!r.ok) { toast(\u0027دریافت کاربران نشد\u0027, true); return; }\n  S.users = r.data.users || [];\n  renderUsers();\n}\nfunction blocked(u){\n  return !u.enabled || (u.expires_at \u0026\u0026 u.expires_at \u003c Date.now()) || (u.limit_bytes \u003e 0 \u0026\u0026 u.used_bytes \u003e= u.limit_bytes);\n}\nfunction renderUsers(){\n  var q = (S.q || \u0027\u0027).trim().toLowerCase();\n  var list = S.users.filter(function(u){ return !q || String(u.name).toLowerCase().indexOf(q) \u003e= 0; });\n  var used = 0, active = 0;\n  S.users.forEach(function(u){ used += u.used_bytes || 0; if (!blocked(u)) active++; });\n\n  var html = \u0027\u0027;\n  list.forEach(function(u){\n    var b = blocked(u);\n    var pct = u.limit_bytes \u003e 0 ? Math.min(100, (u.used_bytes / u.limit_bytes) * 100) : 0;\n    var barCls = pct \u003e= 100 ? \u0027bad\u0027 : pct \u003e= 85 ? \u0027warn\u0027 : \u0027\u0027;\n    var expired = u.expires_at \u0026\u0026 u.expires_at \u003c Date.now();\n    html += \u0027\u003ctr\u003e\u0027\n      + \u0027\u003ctd\u003e\u003cdiv class=\"who\"\u003e\u003cdiv class=\"av\"\u003e\u0027 + esc(String(u.name).slice(0, 2)) + \u0027\u003c/div\u003e\u003cdiv\u003e\u0027\n        + \u0027\u003cdiv class=\"nm\"\u003e\u0027 + esc(u.name) + \u0027\u003c/div\u003e\u0027\n        + \u0027\u003cdiv class=\"sub\"\u003e\u0027 + esc(String(u.uuid).slice(0, 8)) + \u0027…\u003c/div\u003e\u003c/div\u003e\u003c/div\u003e\u003c/td\u003e\u0027\n      + \u0027\u003ctd\u003e\u003cspan class=\"pill \u0027 + (b ? \u0027off\u0027 : \u0027on\u0027) + \u0027\"\u003e\u003ci\u003e\u003c/i\u003e\u0027 + (b ? (expired ? \u0027منقضی\u0027 : \u0027غیرفعال\u0027) : \u0027فعال\u0027) + \u0027\u003c/span\u003e\u003c/td\u003e\u0027\n      + \u0027\u003ctd\u003e\u003cdiv class=\"num\"\u003e\u0027 + size(u.used_bytes) + \u0027 \u003cspan class=\"muted\"\u003e/ \u0027 + limit(u.limit_bytes) + \u0027\u003c/span\u003e\u003c/div\u003e\u0027\n        + (u.limit_bytes \u003e 0 ? \u0027\u003cdiv class=\"bar \u0027 + barCls + \u0027\"\u003e\u003ci style=\"width:\u0027 + pct.toFixed(1) + \u0027%\"\u003e\u003c/i\u003e\u003c/div\u003e\u0027 : \u0027\u0027) + \u0027\u003c/td\u003e\u0027\n      + \u0027\u003ctd class=\"muted num\"\u003e\u0027 + faDate(u.expires_at) + \u0027\u003c/td\u003e\u0027\n      + \u0027\u003ctd\u003e\u003cdiv class=\"ops\"\u003e\u0027\n        + \u0027\u003cbutton class=\"icon\" title=\"ویرایش\" onclick=\"openUser(\u0027 + u.id + \u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-edit\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u0027\n        + \u0027\u003cbutton class=\"icon\" title=\"\u0027 + (u.enabled ? \u0027خاموش کردن\u0027 : \u0027روشن کردن\u0027) + \u0027\" onclick=\"toggleUser(\u0027 + u.id + \u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-power\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u0027\n        + \u0027\u003cbutton class=\"icon\" title=\"کپی لینک ساب\" onclick=\"copySub(\u0027 + u.id + \u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-copy\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u0027\n        + \u0027\u003cbutton class=\"icon\" title=\"صفر کردن مصرف\" onclick=\"resetUser(\u0027 + u.id + \u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-refresh\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u0027\n        + \u0027\u003cbutton class=\"icon\" title=\"حذف\" style=\"color:var(--bad)\" onclick=\"delUser(\u0027 + u.id + \u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-trash\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u0027\n        + \u0027\u003c/div\u003e\u003c/td\u003e\u003c/tr\u003e\u0027;\n  });\n  el(\u0027rows\u0027).innerHTML = html;\n  el(\u0027u-empty\u0027).innerHTML = list.length ? \u0027\u0027 :\n    \u0027\u003cdiv class=\"empty\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-users\"/\u003e\u003c/svg\u003e\u003cb\u003e\u0027 + (q ? \u0027چیزی پیدا نشد\u0027 : \u0027هنوز کاربری نساخته‌ای\u0027) + \u0027\u003c/b\u003e\u0027\n    + (q ? \u0027عبارت دیگری را جست‌وجو کن.\u0027 : \u0027از فرم بالا اولین کاربر را بساز.\u0027) + \u0027\u003c/div\u003e\u0027;\n  el(\u0027s-users\u0027).textContent = fa(S.users.length);\n  el(\u0027s-active\u0027).textContent = fa(active);\n  el(\u0027s-used\u0027).textContent = size(used);\n}\n\nasync function addUser(btn){\n  var name = el(\u0027u-name\u0027).value.trim();\n  var gbv = el(\u0027u-gb\u0027).value, daysv = el(\u0027u-days\u0027).value;\n  if (!/^[A-Za-z0-9_.-]{2,32}$/.test(name)) { el(\u0027u-msg\u0027).textContent = \u0027نام: ۲ تا ۳۲ کاراکتر، فقط حروف انگلیسی، عدد، نقطه، خط تیره.\u0027; return; }\n  el(\u0027u-msg\u0027).textContent = \u0027\u0027;\n  busy(btn, true);\n  var r = await api(\u0027/api/users\u0027, { method: \u0027POST\u0027, body: JSON.stringify({ name: name, limit_gb: Number(gbv || 0), days: Number(daysv || 0) }) });\n  busy(btn, false);\n  if (r.ok) {\n    el(\u0027u-name\u0027).value = \u0027\u0027; el(\u0027u-gb\u0027).value = \u0027\u0027; el(\u0027u-days\u0027).value = \u0027\u0027;\n    toast(\u0027کاربر ساخته شد\u0027);\n    loadUsers();\n  } else {\n    el(\u0027u-msg\u0027).textContent = r.data.error === \u0027duplicate\u0027 ? \u0027این نام قبلاً استفاده شده.\u0027 : r.data.error === \u0027bad_name\u0027 ? \u0027نام معتبر نیست.\u0027 : \u0027خطا: \u0027 + (r.data.error || r.status);\n  }\n}\n\nfunction openUser(id){\n  var u = S.users.filter(function(x){ return x.id === id; })[0];\n  if (!u) return;\n  S.editUser = id;\n  S.editingUserSnapshot = { gb: u.limit_bytes, days: u.expires_at ? Math.max(0, Math.round((u.expires_at - Date.now()) / 86400000)) : 0 };\n  el(\u0027e-name\u0027).value = u.name;\n  el(\u0027e-gb\u0027).value = u.limit_bytes ? +(u.limit_bytes / 1073741824).toFixed(2) : 0;\n  el(\u0027e-days\u0027).value = S.editingUserSnapshot.days;\n  el(\u0027e-msg\u0027).textContent = \u0027\u0027;\n  el(\u0027userDlg\u0027).showModal();\n}\n\nasync function saveEdit(btn){\n  var name = el(\u0027e-name\u0027).value.trim();\n  var gb = Number(el(\u0027e-gb\u0027).value || 0);\n  var days = Number(el(\u0027e-days\u0027).value || 0);\n  var body = {};\n  // فقط فیلدهایی که عوض شده‌اند فرستاده می‌شوند؛ فرستادن روزِ گردشده تاریخ انقضا را جابه‌جا می‌کرد.\n  if (name) body.name = name;\n  if (gb !== Number((S.editingUserSnapshot.gb / 1073741824).toFixed(2))) body.limit_gb = gb;\n  if (days !== S.editingUserSnapshot.days) body.days = days;\n  if (!Object.keys(body).length) { el(\u0027userDlg\u0027).close(); return; }\n  busy(btn, true);\n  var r = await api(\u0027/api/users/\u0027 + S.editUser, { method: \u0027PATCH\u0027, body: JSON.stringify(body) });\n  busy(btn, false);\n  if (r.ok) { el(\u0027userDlg\u0027).close(); toast(\u0027ذخیره شد\u0027); loadUsers(); }\n  else el(\u0027e-msg\u0027).textContent = r.data.error === \u0027duplicate\u0027 ? \u0027این نام تکراری است.\u0027 : \u0027خطا: \u0027 + (r.data.error || r.status);\n}\n\nasync function toggleUser(id){\n  var u = S.users.filter(function(x){ return x.id === id; })[0];\n  if (!u) return;\n  var r = await api(\u0027/api/users/\u0027 + id, { method: \u0027PATCH\u0027, body: JSON.stringify({ enabled: !u.enabled }) });\n  if (r.ok) loadUsers(); else toast(\u0027تغییر وضعیت نشد\u0027, true);\n}\nasync function resetUser(id){\n  if (!confirm(\u0027مصرف این کاربر صفر شود؟\u0027)) return;\n  var r = await api(\u0027/api/users/\u0027 + id + \u0027/reset\u0027, { method: \u0027POST\u0027 });\n  if (r.ok) { toast(\u0027مصرف صفر شد\u0027); loadUsers(); } else toast(\u0027انجام نشد\u0027, true);\n}\nasync function delUser(id){\n  var u = S.users.filter(function(x){ return x.id === id; })[0];\n  if (!confirm(\u0027کاربر «\u0027 + (u ? u.name : id) + \u0027» حذف شود؟ این کار برگشت‌پذیر نیست.\u0027)) return;\n  var r = await api(\u0027/api/users/\u0027 + id, { method: \u0027DELETE\u0027 });\n  if (r.ok) { toast(\u0027حذف شد\u0027); loadUsers(); } else toast(\u0027حذف نشد\u0027, true);\n}\nfunction copySub(id){\n  var u = S.users.filter(function(x){ return x.id === id; })[0];\n  if (!u) return;\n  copyText(location.origin + \u0027/sub/\u0027 + u.sub_token, \u0027لینک ساب کپی شد\u0027);\n}\n\n/* ───────── configs ───────── */\nasync function loadConfigs(manual){\n  var r = await api(\u0027/api/configs\u0027);\n  if (!r.ok) { toast(\u0027دریافت کانفیگ‌ها نشد\u0027, true); return; }\n  S.configs = r.data.configs || [];\n  renderConfigs();\n  if (manual) toast(\u0027تازه شد\u0027);\n}\nfunction renderConfigs(){\n  var html = \u0027\u0027;\n  S.configs.forEach(function(c, i){\n    html += \u0027\u003cdiv class=\"item\"\u003e\u0027\n      + \u0027\u003cdiv class=\"top\"\u003e\u0027\n        + \u0027\u003cb\u003e\u0027 + esc(c.name) + \u0027\u003c/b\u003e\u0027\n        + \u0027\u003cspan class=\"pill \u0027 + (c.enabled ? \u0027on\u0027 : \u0027off\u0027) + \u0027\"\u003e\u003ci\u003e\u003c/i\u003e\u0027 + (c.enabled ? \u0027فعال\u0027 : \u0027خاموش\u0027) + \u0027\u003c/span\u003e\u0027\n        + \u0027\u003cspan style=\"margin-right:auto\"\u003e\u003c/span\u003e\u0027\n        + \u0027\u003cbutton class=\"icon\" title=\"بالا\" \u0027 + (i === 0 ? \u0027disabled\u0027 : \u0027\u0027) + \u0027 onclick=\"moveCfg(\u0027 + c.id + \u0027,-1)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-up\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u0027\n        + \u0027\u003cbutton class=\"icon\" title=\"پایین\" \u0027 + (i === S.configs.length - 1 ? \u0027disabled\u0027 : \u0027\u0027) + \u0027 onclick=\"moveCfg(\u0027 + c.id + \u0027,1)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-down\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u0027\n        + \u0027\u003cbutton class=\"icon\" title=\"ویرایش\" onclick=\"openCfg(\u0027 + c.id + \u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-edit\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u0027\n        + \u0027\u003cbutton class=\"icon\" title=\"\u0027 + (c.enabled ? \u0027خاموش\u0027 : \u0027روشن\u0027) + \u0027\" onclick=\"toggleCfg(\u0027 + c.id + \u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-power\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u0027\n        + \u0027\u003cbutton class=\"icon\" title=\"حذف\" style=\"color:var(--bad)\" onclick=\"delCfg(\u0027 + c.id + \u0027)\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-trash\"/\u003e\u003c/svg\u003e\u003c/button\u003e\u0027\n      + \u0027\u003c/div\u003e\u0027\n      + \u0027\u003cdiv class=\"mono\"\u003e\u0027 + esc(c.uri) + \u0027\u003c/div\u003e\u0027\n      + \u0027\u003c/div\u003e\u0027;\n  });\n  el(\u0027cfg-list\u0027).innerHTML = html;\n  el(\u0027c-empty\u0027).innerHTML = S.configs.length ? \u0027\u0027 :\n    \u0027\u003cdiv class=\"empty\"\u003e\u003csvg class=\"ic\"\u003e\u003cuse href=\"#i-layers\"/\u003e\u003c/svg\u003e\u003cb\u003eهنوز کانفیگی اضافه نکرده‌ای\u003c/b\u003eاولین سرور را از فرم بالا اضافه کن.\u003c/div\u003e\u0027;\n  var n = S.configs.filter(function(c){ return c.enabled; }).length;\n  if (el(\u0027rc-count\u0027)) el(\u0027rc-count\u0027).textContent = fa(n);\n}\nasync function addConfig(btn){\n  var name = el(\u0027c-name\u0027).value.trim();\n  var uri = el(\u0027c-uri\u0027).value.trim();\n  if (!name) { el(\u0027c-msg\u0027).textContent = \u0027نام را بنویس.\u0027; return; }\n  if (!/^(vless|vmess|trojan|ss|hysteria2?|tuic|wireguard):\\/\\//i.test(uri)) { el(\u0027c-msg\u0027).textContent = \u0027لینک معتبر نیست.\u0027; return; }\n  el(\u0027c-msg\u0027).textContent = \u0027\u0027;\n  busy(btn, true);\n  var r = await api(\u0027/api/configs\u0027, { method: \u0027POST\u0027, body: JSON.stringify({ name: name, uri: uri }) });\n  busy(btn, false);\n  if (r.ok) { el(\u0027c-name\u0027).value = \u0027\u0027; el(\u0027c-uri\u0027).value = \u0027\u0027; toast(\u0027اضافه شد\u0027); loadConfigs(); }\n  else el(\u0027c-msg\u0027).textContent = r.data.error === \u0027uri_too_long\u0027 ? \u0027لینک خیلی بلند است.\u0027 : \u0027خطا: \u0027 + (r.data.error || r.status);\n}\nasync function addBulk(btn){\n  var lines = (el(\u0027c-bulk\u0027).value || \u0027\u0027).split(/\\s+/).filter(function(x){ return /:\\/\\//.test(x); });\n  if (!lines.length) { toast(\u0027لینکی پیدا نشد\u0027, true); return; }\n  busy(btn, true);\n  var ok = 0, fail = 0;\n  for (var i = 0; i \u003c lines.length; i++) {\n    var uri = lines[i];\n    var name = \u0027کانفیگ \u0027 + (S.configs.length + ok + 1);\n    var hash = uri.split(\u0027#\u0027)[1];\n    if (hash) { try { name = decodeURIComponent(hash); } catch (e) { name = hash; } }\n    var r = await api(\u0027/api/configs\u0027, { method: \u0027POST\u0027, body: JSON.stringify({ name: name.slice(0, 60), uri: uri }) });\n    if (r.ok) ok++; else fail++;\n  }\n  busy(btn, false);\n  el(\u0027c-bulk\u0027).value = \u0027\u0027;\n  toast(ok + \u0027 اضافه شد\u0027 + (fail ? \u0027 · \u0027 + fail + \u0027 رد شد\u0027 : \u0027\u0027), fail \u003e 0 \u0026\u0026 ok === 0);\n  loadConfigs();\n}\nfunction openCfg(id){\n  var c = S.configs.filter(function(x){ return x.id === id; })[0];\n  if (!c) return;\n  S.editCfg = id;\n  el(\u0027k-name\u0027).value = c.name;\n  el(\u0027k-uri\u0027).value = c.uri;\n  el(\u0027k-msg\u0027).textContent = \u0027\u0027;\n  el(\u0027cfgDlg\u0027).showModal();\n}\nasync function saveCfg(btn){\n  var name = el(\u0027k-name\u0027).value.trim();\n  var uri = el(\u0027k-uri\u0027).value.trim();\n  if (!name) { el(\u0027k-msg\u0027).textContent = \u0027نام را بنویس.\u0027; return; }\n  if (!/^(vless|vmess|trojan|ss|hysteria2?|tuic|wireguard):\\/\\//i.test(uri)) { el(\u0027k-msg\u0027).textContent = \u0027لینک معتبر نیست.\u0027; return; }\n  busy(btn, true);\n  var r = await api(\u0027/api/configs/\u0027 + S.editCfg, { method: \u0027PATCH\u0027, body: JSON.stringify({ name: name, uri: uri }) });\n  busy(btn, false);\n  if (r.ok) { el(\u0027cfgDlg\u0027).close(); toast(\u0027ذخیره شد\u0027); loadConfigs(); }\n  else el(\u0027k-msg\u0027).textContent = \u0027خطا: \u0027 + (r.data.error || r.status);\n}\nasync function toggleCfg(id){\n  var c = S.configs.filter(function(x){ return x.id === id; })[0];\n  if (!c) return;\n  var r = await api(\u0027/api/configs/\u0027 + id, { method: \u0027PATCH\u0027, body: JSON.stringify({ enabled: !c.enabled }) });\n  if (r.ok) loadConfigs(); else toast(\u0027تغییر وضعیت نشد\u0027, true);\n}\nasync function delCfg(id){\n  var c = S.configs.filter(function(x){ return x.id === id; })[0];\n  if (!confirm(\u0027کانفیگ «\u0027 + (c ? c.name : id) + \u0027» حذف شود؟\u0027)) return;\n  var r = await api(\u0027/api/configs/\u0027 + id, { method: \u0027DELETE\u0027 });\n  if (r.ok) { toast(\u0027حذف شد\u0027); loadConfigs(); } else toast(\u0027حذف نشد\u0027, true);\n}\nasync function moveCfg(id, dir){\n  var i = S.configs.findIndex(function(x){ return x.id === id; });\n  var j = i + dir;\n  if (i \u003c 0 || j \u003c 0 || j \u003e= S.configs.length) return;\n  var a = S.configs[i], b = S.configs[j];\n  var sa = a.sort, sb = b.sort;\n  // اگر sort دو کانفیگ یکی باشد، جابه‌جایی هیچ اثری ندارد؛ یکی را یک واحد جدا می‌کنیم.\n  if (sa === sb) { sa = sb - 1; }\n  await api(\u0027/api/configs/\u0027 + a.id, { method: \u0027PATCH\u0027, body: JSON.stringify({ sort: sb }) });\n  await api(\u0027/api/configs/\u0027 + b.id, { method: \u0027PATCH\u0027, body: JSON.stringify({ sort: sa }) });\n  loadConfigs();\n}\nasync function publish(btn){\n  busy(btn, true);\n  var r = await api(\u0027/api/rc/publish\u0027, { method: \u0027POST\u0027 });\n  busy(btn, false);\n  if (r.ok) { toast(\u0027نسخه بالا رفت\u0027); loadRc(); } else toast(\u0027انتشار نشد\u0027, true);\n}\n\n/* ───────── app + settings ───────── */\nasync function loadRc(){\n  var r = await api(\u0027/api/rc\u0027);\n  if (!r.ok) { toast(\u0027اطلاعات RC نیامد\u0027, true); return; }\n  S.rc = r.data || {};\n  var url = S.rc.url || \u0027\u0027;\n  el(\u0027rc-url\u0027).textContent = url.replace(/^https?:\\/\\//, \u0027\u0027);\n  el(\u0027rc-key\u0027).textContent = S.rc.publicKey || \u0027\u0027;\n  el(\u0027rc-version\u0027).textContent = fa(S.rc.version || 0);\n  el(\u0027rc-count\u0027).textContent = fa(S.rc.enabled || 0);\n  el(\u0027rc-lines\u0027).textContent = \u0027mohammad.remoteConfigUrl=\u0027 + url + \u0027\\n\u0027\n    + \u0027mohammad.remoteConfigKey=\u0027 + (S.rc.publicKey || \u0027\u0027);\n}\nasync function loadSettings(){\n  var r = await api(\u0027/api/settings\u0027);\n  if (!r.ok) return;\n  el(\u0027node-host\u0027).value = (r.data \u0026\u0026 r.data.node_host) || \u0027\u0027;\n  el(\u0027s-node\u0027).textContent = (r.data \u0026\u0026 r.data.node_host) || \u0027—\u0027;\n}\nasync function saveNode(btn){\n  var v = el(\u0027node-host\u0027).value.trim().replace(/^https?:\\/\\//, \u0027\u0027).replace(/\\/.*$/, \u0027\u0027);\n  busy(btn, true);\n  var r = await api(\u0027/api/settings\u0027, { method: \u0027POST\u0027, body: JSON.stringify({ key: \u0027node_host\u0027, value: v }) });\n  busy(btn, false);\n  if (r.ok) { toast(\u0027ذخیره شد\u0027); el(\u0027n-msg\u0027).textContent = \u0027\u0027; loadSettings(); }\n  else el(\u0027n-msg\u0027).textContent = r.data.error === \u0027bad_host\u0027 ? \u0027فرمت دامنه درست نیست (بدون https و بدون پورت).\u0027 : \u0027خطا: \u0027 + (r.data.error || r.status);\n}\nasync function changePassword(btn){\n  var cur = el(\u0027pw-cur\u0027).value, next = el(\u0027pw-new\u0027).value;\n  if (next.length \u003c 8) { el(\u0027pw-msg\u0027).textContent = \u0027رمز جدید حداقل ۸ کاراکتر باشد.\u0027; return; }\n  busy(btn, true);\n  var r = await api(\u0027/api/password\u0027, { method: \u0027POST\u0027, body: JSON.stringify({ current: cur, next: next }) });\n  busy(btn, false);\n  if (r.ok) { el(\u0027pw-cur\u0027).value = \u0027\u0027; el(\u0027pw-new\u0027).value = \u0027\u0027; el(\u0027pw-msg\u0027).textContent = \u0027\u0027; toast(\u0027رمز عوض شد\u0027); showAuth(\u0027login\u0027); }\n  else el(\u0027pw-msg\u0027).textContent = r.status === 401 ? \u0027رمز فعلی اشتباه است.\u0027 : \u0027خطا: \u0027 + (r.data.error || r.status);\n}\n\n/* ───────── helpers ───────── */\nfunction copyText(text, msg){\n  var done = function(){ toast(msg || \u0027کپی شد\u0027); };\n  if (navigator.clipboard \u0026\u0026 navigator.clipboard.writeText) {\n    navigator.clipboard.writeText(text).then(done, function(){ fallbackCopy(text); done(); });\n  } else { fallbackCopy(text); done(); }\n}\nfunction fallbackCopy(text){\n  var ta = document.createElement(\u0027textarea\u0027);\n  ta.value = text; ta.style.position = \u0027fixed\u0027; ta.style.opacity = \u00270\u0027;\n  document.body.appendChild(ta); ta.select();\n  try { document.execCommand(\u0027copy\u0027); } catch (e) {}\n  document.body.removeChild(ta);\n}\nfunction copyEl(id){ copyText(el(id).textContent, \u0027کپی شد\u0027); }\n\ndocument.addEventListener(\u0027keydown\u0027, function(e){\n  if (e.key === \u0027Enter\u0027 \u0026\u0026 !el(\u0027auth\u0027).classList.contains(\u0027hidden\u0027)) {\n    if (!el(\u0027setupBox\u0027).classList.contains(\u0027hidden\u0027)) setup(); else login();\n  }\n});\n\nboot();\n\u003c/script\u003e\n\u003c/body\u003e\n\u003c/html\u003e\n"; // inlined from templates/panel-ui.html at build time

/* ───────────────────────── router ───────────────────────── */

export default {
  async fetch(request, env, ctx) {
    try {
      return await handleRequest(request, env, ctx);
    } catch (err) {
      console.error(err);
      return json({ error: 'internal' }, 500);
    }
  },
};

async function handleRequest(request, env, ctx) {
    const url = new URL(request.url);
    const p = url.pathname;
    await ensureConfigs(env);

    // Visiting the bare host should land on the panel, not on a 404 — that is what people type.
    if (p === '/' || p === '') {
      return new Response(null, { status: 302, headers: { location: '/panel' } });
    }
    if (p === '/favicon.ico') return new Response(null, { status: 204 });
    if (p.startsWith('/sub/')) return handleSub(request, env, p.slice(5));
    if (p === '/rc') return handleRc(request, env);
    if (p === '/panel' || p === '/panel/') return page(PANEL);
    if (p.startsWith('/api/')) return handleApi(request, env, url);
    if (p === '/health') return json({ ok: true });
    return new Response('Not Found', { status: 404 });
}
