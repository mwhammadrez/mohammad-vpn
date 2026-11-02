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
import { subscription, contentType, profileTitle, pickIp, parseIps, xrayConfig, statusPage } from './lib.js';

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
  async addUser(env, opts) {
    const name = opts.name;
    const uuid = uuidv4();
    const sub = randHex(16);
    const password = randHex(10);
    const now = Date.now();
    const days = Number(opts.days) || 0;
    const startOnFirst = opts.start_on_first_connect ? 1 : 0;
    const expires = startOnFirst ? 0 : days > 0 ? now + days * 86400000 : 0;
    const protocol = ['vless', 'trojan', 'both'].includes(opts.protocol) ? opts.protocol : 'both';
    await env.DB.prepare(
      'INSERT INTO users (name, uuid, sub_token, trojan_password, trojan_hash, limit_bytes, limit_req, ' +
        'ip_limit, expires_at, expiry_days, start_on_first_connect, auto_reset_vol_days, ' +
        'auto_reset_req_days, connection_type, ip_pool_id, note, frag_len, frag_int, ip_count, enabled, created_at) ' +
        'VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,?)',
    )
      .bind(
        name, uuid, sub, password, sha224hex(password),
        Math.round((Number(opts.limit_gb) || 0) * 1024 ** 3),
        Math.max(0, Number(opts.limit_req) || 0),
        Math.max(0, Number(opts.ip_limit) || 0),
        expires, days, startOnFirst,
        Math.max(0, Number(opts.auto_reset_vol_days) || 0),
        Math.max(0, Number(opts.auto_reset_req_days) || 0),
        protocol, opts.ip_pool_id ? Number(opts.ip_pool_id) : null,
        String(opts.note || '').slice(0, 200) || null,
        String(opts.frag_len || '').trim().slice(0, 20) || null,
        String(opts.frag_int || '').trim().slice(0, 20) || null,
        Math.max(1, Math.min(8, Number(opts.ip_count) || 1)),
        now,
      )
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
 * The panel is deployed over databases that predate this build, so the schema is created lazily and
 * every new column is added only when `PRAGMA table_info` says it is missing. `CREATE TABLE IF NOT
 * EXISTS` alone can never add a column.
 */
const schemaReady = new WeakMap();

function ensureSchema(env) {
  let ready = schemaReady.get(env.DB);
  if (!ready) {
    ready = (async () => {
      await env.DB.prepare(
        'CREATE TABLE IF NOT EXISTS configs (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, uri TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, sort INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)',
      ).run();
      await env.DB.prepare(
        'CREATE TABLE IF NOT EXISTS ip_pools (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, operator TEXT NOT NULL DEFAULT \'any\', ips TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL)',
      ).run();
      await env.DB.prepare(
        'CREATE TABLE IF NOT EXISTS login_attempts (ip TEXT PRIMARY KEY, fails INTEGER NOT NULL DEFAULT 0, reset_at INTEGER NOT NULL)',
      ).run();

      const info = await env.DB.prepare('PRAGMA table_info(users)').all();
      const have = new Set((info.results || []).map((r) => r.name));
      const additions = [
        ['ip_pool_id', 'INTEGER'],
        ['pinned_ip', 'TEXT'],
        ['ip_pinned_at', 'INTEGER'],
        ['limit_req', 'INTEGER NOT NULL DEFAULT 0'],
        ['used_req', 'INTEGER NOT NULL DEFAULT 0'],
        ['ip_limit', 'INTEGER NOT NULL DEFAULT 0'],
        ['active_ips', 'TEXT'],
        ['expiry_days', 'INTEGER NOT NULL DEFAULT 0'],
        ['start_on_first_connect', 'INTEGER NOT NULL DEFAULT 0'],
        ['first_connection_time', 'INTEGER'],
        ['auto_reset_vol_days', 'INTEGER NOT NULL DEFAULT 0'],
        ['auto_reset_req_days', 'INTEGER NOT NULL DEFAULT 0'],
        ['last_reset_vol_time', 'INTEGER'],
        ['last_reset_req_time', 'INTEGER'],
        ['auto_rotate_ip', 'INTEGER NOT NULL DEFAULT 1'],
        ['connection_type', "TEXT NOT NULL DEFAULT 'both'"],
        ['note', 'TEXT'],
        ['frag_len', 'TEXT'],
        ['frag_int', 'TEXT'],
        ['ip_count', 'INTEGER NOT NULL DEFAULT 1'],
      ];
      for (const [name, type] of additions) {
        if (!have.has(name)) {
          await env.DB.prepare(`ALTER TABLE users ADD COLUMN ${name} ${type}`).run();
        }
      }
    })().catch((err) => {
      schemaReady.delete(env.DB);
      throw err;
    });
    schemaReady.set(env.DB, ready);
  }
  return ready;
}

/**
 * Removing or disabling a pool must release every pin it handed out. Users carry no pool id when
 * they were served by the default pool, so match the pinned address against the pool's own list.
 */
async function clearPoolPins(env, poolId) {
  const pool = await env.DB.prepare('SELECT ips FROM ip_pools WHERE id = ?').bind(poolId).first();
  await env.DB.prepare('UPDATE users SET ip_pool_id = NULL, pinned_ip = NULL, ip_pinned_at = NULL WHERE ip_pool_id = ?')
    .bind(poolId).run();
  if (pool) {
    for (const ip of parseIps(pool.ips)) {
      await env.DB.prepare('UPDATE users SET pinned_ip = NULL, ip_pinned_at = NULL WHERE pinned_ip = ?')
        .bind(ip).run();
    }
  }
}

/** The pool a user belongs to, falling back to the first enabled pool. */
async function poolFor(env, user) {
  let pool = null;
  if (user.ip_pool_id) {
    pool = await env.DB.prepare('SELECT * FROM ip_pools WHERE id = ? AND enabled = 1')
      .bind(user.ip_pool_id).first();
  }
  if (!pool) {
    pool = await env.DB.prepare('SELECT * FROM ip_pools WHERE enabled = 1 ORDER BY id ASC LIMIT 1').first();
  }
  return pool || null;
}

/**
 * The address a link should carry. A pinned IP is sticky: changing it under a connected client
 * drops that client until it re-reads its subscription. Null means "no pool yet" — the caller falls
 * back to the domain so the panel still works before any IP is added.
 */
async function ipFor(env, user, host) {
  if (user.pinned_ip) return user.pinned_ip;
  const pool = await poolFor(env, user);
  if (!pool) return null;
  const picked = pickIp(pool.ips, String(user.id) + host);
  if (!picked) return null;
  await env.DB.prepare('UPDATE users SET pinned_ip = ?, ip_pinned_at = ? WHERE id = ?')
    .bind(picked, Date.now(), user.id).run();
  return picked;
}

/**
 * Addresses a subscription should carry. One IP keeps the sticky behaviour; several are picked
 * deterministically from the same pool so a blocked edge does not take the whole user down.
 */
async function ipListFor(env, user, host) {
  const count = Math.max(1, Math.min(8, Number(user.ip_count) || 1));
  const pool = await poolFor(env, user);
  if (!pool) return [null];
  const ips = parseIps(pool.ips);
  if (!ips.length) return [null];
  if (count === 1) return [await ipFor(env, user, host) || null];

  const out = [];
  if (user.pinned_ip && ips.includes(user.pinned_ip)) out.push(user.pinned_ip);
  for (let i = 0; out.length < count && i < ips.length * 4; i++) {
    const ip = pickIp(pool.ips, `${user.id}:${host}:${i}`);
    if (ip && !out.includes(ip)) out.push(ip);
  }
  return out.length ? out : [null];
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

async function handleSub(request, env, token) {
  const user = await DB.bySubToken(env, token);
  // Expired, over-quota or over-request users must not keep receiving working links: the panel
  // already shows them as blocked, and the node refuses them at connect time.
  if (!user || !user.enabled || blocked(user)) return new Response('not found', { status: 404 });

  const host = (await DB.setting(env, 'node_host')) || env.PROXY_HOST || hostOf(request, env);
  const ips = await ipListFor(env, user, host);
  const format = new URL(request.url).searchParams.get('format') || 'base64';

  return new Response(subscription(format, user, host, ips), {
    headers: {
      'content-type': contentType(format),
      'cache-control': 'no-store',
      'profile-title': profileTitle(user.name),
      'profile-update-interval': '12',
      'profile-web-page-url': new URL(`/status/${token}`, request.url).toString(),
      'subscription-userinfo': `upload=0; download=${user.used_bytes}; total=${user.limit_bytes || 0}; expire=${user.expires_at ? Math.floor(user.expires_at / 1000) : 0}`,
    },
  });
}

/**
 * `GET /xray/<token>` — the same profile as a full Xray config instead of a share link. Clients
 * that prefer a file (and users who want the JSON) get one object with every outbound.
 */
async function handleXray(request, env, token) {
  const user = await DB.bySubToken(env, token);
  if (!user || !user.enabled || blocked(user)) return new Response('not found', { status: 404 });

  const host = (await DB.setting(env, 'node_host')) || env.PROXY_HOST || hostOf(request, env);
  const ips = await ipListFor(env, user, host);
  const body = JSON.stringify(xrayConfig(user, host, ips[0]), null, 2);

  return new Response(body, {
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'content-disposition': `attachment; filename="${user.name}-xray.json"`,
      'cache-control': 'no-store',
      'profile-title': profileTitle(user.name),
      'profile-web-page-url': new URL(`/status/${token}`, request.url).toString(),
      'subscription-userinfo': `upload=0; download=${user.used_bytes}; total=${user.limit_bytes || 0}; expire=${user.expires_at ? Math.floor(user.expires_at / 1000) : 0}`,
    },
  });
}

/** `GET /status/<token>` — the public usage page linked from the subscription headers. */
async function handleStatus(request, env, token) {
  const user = await DB.bySubToken(env, token);
  if (!user) return new Response('not found', { status: 404 });
  const host = (await DB.setting(env, 'node_host')) || env.PROXY_HOST || hostOf(request, env);
  const ips = await ipListFor(env, user, host);
  return page(statusPage(user, host, ips));
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
  return (u.expires_at && u.expires_at < Date.now()) ||
    (u.limit_bytes > 0 && u.used_bytes >= u.limit_bytes) ||
    (u.limit_req > 0 && u.used_req >= u.limit_req);
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
    const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
    const now = Date.now();
    const attempt = await env.DB.prepare('SELECT fails, reset_at FROM login_attempts WHERE ip = ?').bind(ip).first();
    if (attempt && attempt.reset_at > now && attempt.fails >= 5) {
      const retry = Math.max(1, Math.ceil((attempt.reset_at - now) / 1000));
      return json({ error: 'rate_limited', retry_after: retry }, 429, { 'retry-after': String(retry) });
    }

    const { password } = await readJson(request);
    const hash = await DB.setting(env, 'password_hash');
    if (!hash) return json({ error: 'not_setup' }, 400);
    if (!timingSafe(await sha256hex(password || ''), hash)) {
      if (attempt && attempt.reset_at > now) {
        await env.DB.prepare('UPDATE login_attempts SET fails = fails + 1 WHERE ip = ?').bind(ip).run();
      } else {
        await env.DB.prepare(
          'INSERT INTO login_attempts (ip, fails, reset_at) VALUES (?,?,?) ' +
            'ON CONFLICT(ip) DO UPDATE SET fails = 1, reset_at = excluded.reset_at',
        ).bind(ip, 1, now + 600000).run();
      }
      return json({ error: 'bad_password' }, 401);
    }
    await env.DB.prepare('DELETE FROM login_attempts WHERE ip = ?').bind(ip).run();
    const s = await makeSession(env);
    return json({ ok: true, token: s }, 200, { 'set-cookie': cookieHeader(s, SESSION_TTL / 1000) });
  }
  if (path === '/api/logout' && method === 'POST') {
    return json({ ok: true }, 200, { 'set-cookie': `${COOKIE}=; Path=/; Max-Age=0` });
  }

  if (!(await isAuthed(request, env))) return json({ error: 'unauthorized' }, 401);

  if (path === '/api/users' && method === 'GET') {
    const users = await DB.getUsers(env);
    // The app's CONNECT button builds its link from `pinned_ip`. Assign the sticky address while
    // listing users, so the dashboard (and the phone) always has one to hand.
    const host = (await DB.setting(env, 'node_host')) || env.PROXY_HOST || hostOf(request, env);
    for (const user of users) {
      if (!user.pinned_ip) {
        const ip = await ipFor(env, user, host);
        if (ip) user.pinned_ip = ip;
      }
    }
    return json({ users });
  }
  if (path === '/api/users' && method === 'POST') {
    const b = await readJson(request);
    const name = String(b.name || '').trim();
    if (!/^[A-Za-z0-9_.-]{2,32}$/.test(name)) return json({ error: 'bad_name' }, 400);
    const exists = await env.DB.prepare('SELECT id FROM users WHERE name = ?').bind(name).first();
    if (exists) return json({ error: 'duplicate' }, 400);
    await DB.addUser(env, {
      name,
      limit_gb: Number(b.limit_gb) || 0,
      days: Number(b.days) || 0,
      limit_req: Number(b.limit_req) || 0,
      ip_limit: Number(b.ip_limit) || 0,
      ip_pool_id: b.ip_pool_id || null,
      start_on_first_connect: b.start_on_first_connect ? 1 : 0,
      auto_reset_vol_days: Number(b.auto_reset_vol_days) || 0,
      auto_reset_req_days: Number(b.auto_reset_req_days) || 0,
      protocol: b.protocol,
      note: b.note,
      frag_len: b.frag_len,
      frag_int: b.frag_int,
      ip_count: b.ip_count,
    });
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
      const days = b.days === undefined ? user.expiry_days : Math.max(0, Number(b.days) || 0);
      const expires = b.days === undefined
        ? user.expires_at
        : user.start_on_first_connect && !user.first_connection_time
          ? 0
          : days > 0 ? Date.now() + days * 86400000 : 0;
      const limitReq = b.limit_req === undefined ? user.limit_req : Math.max(0, Number(b.limit_req) || 0);
      const ipLimit = b.ip_limit === undefined ? user.ip_limit : Math.max(0, Number(b.ip_limit) || 0);
      const poolId = b.ip_pool_id === undefined ? user.ip_pool_id : b.ip_pool_id ? Number(b.ip_pool_id) : null;
      const resetVol = b.auto_reset_vol_days === undefined ? user.auto_reset_vol_days : Math.max(0, Number(b.auto_reset_vol_days) || 0);
      const resetReq = b.auto_reset_req_days === undefined ? user.auto_reset_req_days : Math.max(0, Number(b.auto_reset_req_days) || 0);
      const startOnFirst = b.start_on_first_connect === undefined
        ? user.start_on_first_connect
        : b.start_on_first_connect ? 1 : 0;
      const protocol = b.protocol === undefined
        ? (user.connection_type || 'both')
        : (['vless', 'trojan', 'both'].includes(b.protocol) ? b.protocol : 'both');
      const autoRotate = b.auto_rotate_ip === undefined ? user.auto_rotate_ip : b.auto_rotate_ip ? 1 : 0;
      const note = b.note === undefined ? user.note : String(b.note || '').slice(0, 200) || null;
      const fragLen = b.frag_len === undefined ? user.frag_len : String(b.frag_len || '').trim().slice(0, 20) || null;
      const fragInt = b.frag_int === undefined ? user.frag_int : String(b.frag_int || '').trim().slice(0, 20) || null;
      const ipCount = b.ip_count === undefined ? user.ip_count : Math.max(1, Math.min(8, Number(b.ip_count) || 1));
      let name = user.name;
      if (b.name !== undefined) {
        name = String(b.name).trim();
        if (!/^[A-Za-z0-9_.-]{2,32}$/.test(name)) return json({ error: 'bad_name' }, 400);
        const clash = await env.DB.prepare('SELECT id FROM users WHERE name = ? AND id != ?').bind(name, id).first();
        if (clash) return json({ error: 'duplicate' }, 400);
      }
      await env.DB.prepare(
        'UPDATE users SET name = ?, enabled = ?, limit_bytes = ?, limit_req = ?, ip_limit = ?, ' +
          'expires_at = ?, expiry_days = ?, start_on_first_connect = ?, auto_reset_vol_days = ?, ' +
          'auto_reset_req_days = ?, connection_type = ?, ip_pool_id = ?, auto_rotate_ip = ?, note = ?, ' +
          'frag_len = ?, frag_int = ?, ip_count = ? WHERE id = ?',
      )
        .bind(
          name, enabled, limit, limitReq, ipLimit, expires, days, startOnFirst, resetVol, resetReq,
          protocol, poolId, autoRotate, note, fragLen, fragInt, ipCount, id,
        )
        .run();
      return json({ ok: true });
    }
  }

  /* ── clean-IP pools ── */
  if (path === '/api/ips' && method === 'GET') {
    const rows = await env.DB.prepare('SELECT * FROM ip_pools ORDER BY id ASC').all();
    return json({ pools: rows.results || [] });
  }
  if (path === '/api/ips' && method === 'POST') {
    const b = await readJson(request);
    const name = String(b.name || '').trim().slice(0, 60);
    const operator = ['any', 'mci', 'irancell', 'mci_fixed'].includes(b.operator) ? b.operator : 'any';
    const ips = String(b.ips || '').trim();
    if (name.length < 2) return json({ error: 'bad_name' }, 400);
    if (!ips) return json({ error: 'no_ips' }, 400);
    if (ips.length > 20000) return json({ error: 'too_large' }, 400);
    await env.DB.prepare('INSERT INTO ip_pools (name, operator, ips, enabled, created_at) VALUES (?,?,?,1,?)')
      .bind(name, operator, ips, Date.now()).run();
    return json({ ok: true });
  }
  const ipPool = path.match(/^\/api\/ips\/(\d+)$/);
  if (ipPool) {
    const id = Number(ipPool[1]);
    if (method === 'PATCH') {
      const b = await readJson(request);
      const row = await env.DB.prepare('SELECT * FROM ip_pools WHERE id = ?').bind(id).first();
      if (!row) return json({ error: 'not_found' }, 404);
      const name = b.name === undefined ? row.name : String(b.name).trim().slice(0, 60);
      const operator = b.operator === undefined
        ? row.operator
        : (['any', 'mci', 'irancell', 'mci_fixed'].includes(b.operator) ? b.operator : 'any');
      const ips = b.ips === undefined ? row.ips : String(b.ips).trim();
      if (!name || !ips) return json({ error: 'bad_pool' }, 400);
      if (ips.length > 20000) return json({ error: 'too_large' }, 400);
      const enabled = b.enabled === undefined ? row.enabled : b.enabled ? 1 : 0;
      await env.DB.prepare('UPDATE ip_pools SET name = ?, operator = ?, ips = ?, enabled = ? WHERE id = ?')
        .bind(name, operator, ips, enabled, id).run();
      if (!enabled) await clearPoolPins(env, id);
      return json({ ok: true });
    }
    if (method === 'DELETE') {
      await clearPoolPins(env, id);
      await env.DB.prepare('DELETE FROM ip_pools WHERE id = ?').bind(id).run();
      return json({ ok: true });
    }
  }
  const userIp = path.match(/^\/api\/users\/(\d+)\/ip$/);
  if (userIp && method === 'POST') {
    const id = Number(userIp[1]);
    const b = await readJson(request);
    const ip = String(b.ip || '').trim();
    if (ip && !/^[0-9a-fA-F:.]{3,64}$/.test(ip)) return json({ error: 'bad_ip' }, 400);
    await env.DB.prepare('UPDATE users SET pinned_ip = ?, ip_pinned_at = ? WHERE id = ?')
      .bind(ip || null, ip ? Date.now() : null, id).run();
    return json({ ok: true, ip: ip || null });
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

/* ───────────────────────── lazy maintenance ───────────────────────── */

/**
 * No cron: the panel itself is the clock. A visitor triggers at most one check a minute, and a
 * best-effort lock keeps two cold isolates from running the same job twice.
 */
async function tick(env) {
  const now = Date.now();
  const lock = Number(await DB.setting(env, 'tick_lock', '0')) || 0;
  if (lock && now - lock < 120000) return;
  await DB.setSetting(env, 'tick_lock', String(now));
  try {
    const lastReset = Number(await DB.setting(env, 'last_auto_reset_check', '0')) || 0;
    if (now - lastReset >= 60000) {
      await DB.setSetting(env, 'last_auto_reset_check', String(now));
      await autoReset(env, now);
    }
    const lastRotate = Number(await DB.setting(env, 'last_ip_rotate_check', '0')) || 0;
    if (now - lastRotate >= 300000) {
      await DB.setSetting(env, 'last_ip_rotate_check', String(now));
      await rotatePinnedIps(env, now);
    }
  } finally {
    await DB.setSetting(env, 'tick_lock', '0');
  }
}

async function autoReset(env, now) {
  await env.DB.prepare(
    'UPDATE users SET used_bytes = 0, last_reset_vol_time = ? ' +
      'WHERE auto_reset_vol_days > 0 AND (last_reset_vol_time IS NULL OR ? - last_reset_vol_time >= auto_reset_vol_days * 86400000)',
  ).bind(now, now).run();
  await env.DB.prepare(
    'UPDATE users SET used_req = 0, last_reset_req_time = ? ' +
      'WHERE auto_reset_req_days > 0 AND (last_reset_req_time IS NULL OR ? - last_reset_req_time >= auto_reset_req_days * 86400000)',
  ).bind(now, now).run();
}

/** A new IP at most once a day: rotating more often would cut connected clients for no gain. */
async function rotatePinnedIps(env, now) {
  await env.DB.prepare(
    'UPDATE users SET pinned_ip = NULL, ip_pinned_at = NULL ' +
      'WHERE auto_rotate_ip = 1 AND pinned_ip IS NOT NULL AND (ip_pinned_at IS NULL OR ? - ip_pinned_at >= 86400000)',
  ).bind(now).run();
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
    await ensureSchema(env);
    if (ctx && typeof ctx.waitUntil === 'function') ctx.waitUntil(tick(env).catch(() => {}));

    // Visiting the bare host should land on the panel, not on a 404 — that is what people type.
    if (p === '/' || p === '') {
      return new Response(null, { status: 302, headers: { location: '/panel' } });
    }
    if (p === '/favicon.ico') return new Response(null, { status: 204 });
    if (p.startsWith('/sub/')) return handleSub(request, env, p.slice(5));
    if (p.startsWith('/xray/')) return handleXray(request, env, p.slice(6));
    if (p.startsWith('/status/')) return handleStatus(request, env, p.slice(8));
    if (p === '/rc') return handleRc(request, env);
    if (p === '/panel' || p === '/panel/') return page(PANEL);
    if (p.startsWith('/api/')) return handleApi(request, env, url);
    if (p === '/health') return json({ ok: true });
    return new Response('Not Found', { status: 404 });
}
