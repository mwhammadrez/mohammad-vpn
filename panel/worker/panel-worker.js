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

const PANEL = __PANEL_HTML__; // inlined from templates/panel-ui.html at build time

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
