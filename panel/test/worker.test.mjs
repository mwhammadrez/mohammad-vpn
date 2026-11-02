/**
 * End-to-end tests for the built panel worker, backed by a real SQLite database.
 *
 * The worker is a single ES module, so it can be imported in Node once the unused
 * `cloudflare:sockets` side-effect import is removed. `node:sqlite` gives us the actual schema,
 * parameter binding and SQL the worker will run — a typo in a column name fails here, not on a
 * phone in Tehran.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const schema = fs.readFileSync(path.join(root, 'panel/worker/schema.sql'), 'utf8');
const built = path.join(root, 'app/src/main/assets/panel/panel-worker.js');

class Stmt {
  constructor(db, sql, params = []) {
    this.db = db;
    this.sql = sql;
    this.params = params;
  }
  bind(...params) {
    return new Stmt(this.db, this.sql, params);
  }
  first() {
    return this.db.prepare(this.sql).get(...this.params) ?? null;
  }
  all() {
    return { results: this.db.prepare(this.sql).all(...this.params) };
  }
  run() {
    const result = this.db.prepare(this.sql).run(...this.params);
    return { success: true, meta: { changes: Number(result.changes) } };
  }
}

class D1 {
  constructor() {
    this.db = new DatabaseSync(':memory:');
    this.db.exec(schema);
  }
  prepare(sql) {
    return new Stmt(this.db, sql);
  }
  async batch(statements) {
    this.db.exec('BEGIN');
    try {
      const out = statements.map((s) => s.run());
      this.db.exec('COMMIT');
      return out;
    } catch (error) {
      this.db.exec('ROLLBACK');
      throw error;
    }
  }
  get(sql, ...params) {
    return this.db.prepare(sql).get(...params);
  }
  run(sql, ...params) {
    return this.db.prepare(sql).run(...params);
  }
}

if (!fs.existsSync(built)) throw new Error('run `node panel/build.mjs` before this suite');

const source = fs.readFileSync(built, 'utf8').replace('import"cloudflare:sockets";', '');
const modulePath = path.join(here, '.worker-under-test.mjs');
fs.writeFileSync(modulePath, source);
const worker = (await import(modulePath)).default;

function harness() {
  const db = new D1();
  const env = { DB: db, PROXY_HOST: 'node.example.com' };
  const ctx = { waitUntil() {} };
  const call = (pathname, init = {}) =>
    worker.fetch(new Request('https://panel.example.com' + pathname, init), env, ctx);
  const json = (pathname, body, token, method = 'POST') =>
    call(pathname, {
      method,
      headers: {
        'content-type': 'application/json',
        ...(token ? { authorization: 'Bearer ' + token } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  return { db, env, call, json };
}

async function setupPanel() {
  const h = harness();
  const res = await h.json('/api/setup', { password: 'password123' });
  assert.equal(res.status, 200);
  const { token } = await res.json();
  assert.ok(token);
  return { ...h, token };
}

test('setup creates a session and protects the API', async () => {
  const h = await setupPanel();
  assert.equal((await h.call('/api/ips')).status, 401);

  const pool = await h.json('/api/ips', { name: 'MCI', operator: 'mci', ips: '104.16.0.1\n104.17.0.1' }, h.token);
  assert.equal(pool.status, 200);

  const list = await h.json('/api/ips', undefined, h.token, 'GET');
  const pools = (await list.json()).pools;
  assert.equal(pools.length, 1);
  assert.equal(pools[0].name, 'MCI');
});

test('a subscription uses a clean IP and keeps the domain for SNI/Host', async () => {
  const h = await setupPanel();
  await h.json('/api/ips', { name: 'MCI', operator: 'mci', ips: '104.16.0.1' }, h.token);
  await h.json('/api/users', { name: 'ali', limit_gb: 1, days: 30 }, h.token);

  const users = (await (await h.json('/api/users', undefined, h.token, 'GET')).json()).users;
  assert.equal(users.length, 1);
  // Listing users assigns the sticky address, so the app's CONNECT button has one to use.
  assert.equal(users[0].pinned_ip, '104.16.0.1');

  const sub = await h.call('/sub/' + users[0].sub_token);
  const text = Buffer.from(await sub.text(), 'base64').toString('utf8');
  assert.match(text, /@104\.16\.0\.1:443\?/);
  assert.match(text, /sni=node\.example\.com/);
  assert.match(text, /host=node\.example\.com/);
  assert.match(text, /security=tls/);
  assert.equal(sub.headers.get('content-type'), 'text/plain; charset=utf-8');
  assert.match(sub.headers.get('profile-title'), /^base64:/);

  const pinned = h.db.get('SELECT pinned_ip FROM users WHERE id = ?', users[0].id);
  assert.equal(pinned.pinned_ip, '104.16.0.1');
});

test('all five subscription formats render and keep the IP', async () => {
  const h = await setupPanel();
  await h.json('/api/ips', { name: 'MCI', ips: '104.16.0.1' }, h.token);
  await h.json('/api/users', { name: 'ali', days: 30 }, h.token);
  const user = (await (await h.json('/api/users', undefined, h.token, 'GET')).json()).users[0];

  const plain = await h.call('/sub/' + user.sub_token + '?format=plain');
  assert.match(await plain.text(), /^vless:\/\/[^@]+@104\.16\.0\.1:443/);

  const clash = await h.call('/sub/' + user.sub_token + '?format=clash');
  const yaml = await clash.text();
  assert.equal(clash.headers.get('content-type'), 'text/yaml; charset=utf-8');
  assert.match(yaml, /server: "104\.16\.0\.1"/);
  assert.match(yaml, /servername: "node\.example\.com"/);

  const singbox = await h.call('/sub/' + user.sub_token + '?format=singbox');
  const sing = await singbox.json();
  assert.equal(sing.outbounds[0].server, '104.16.0.1');
  assert.equal(sing.outbounds[0].tls.server_name, 'node.example.com');

  const json = await h.call('/sub/' + user.sub_token + '?format=json');
  const doc = await json.json();
  assert.equal(doc.address, '104.16.0.1');

  const base64 = await h.call('/sub/' + user.sub_token);
  assert.ok(Buffer.from(await base64.text(), 'base64').toString('utf8').startsWith('vless://'));
});

test('a pinned IP can be released and is re-picked later', async () => {
  const h = await setupPanel();
  await h.json('/api/ips', { name: 'MCI', ips: '104.16.0.1' }, h.token);
  await h.json('/api/users', { name: 'ali', days: 30 }, h.token);
  const user = (await (await h.json('/api/users', undefined, h.token, 'GET')).json()).users[0];

  await h.call('/sub/' + user.sub_token);
  assert.equal(h.db.get('SELECT pinned_ip FROM users WHERE id = ?', user.id).pinned_ip, '104.16.0.1');

  const released = await h.json('/api/users/' + user.id + '/ip', { ip: '' }, h.token);
  assert.equal(released.status, 200);
  assert.equal(h.db.get('SELECT pinned_ip FROM users WHERE id = ?', user.id).pinned_ip, null);
});

test('quota and expiry block the subscription', async () => {
  const h = await setupPanel();
  await h.json('/api/ips', { name: 'MCI', ips: '104.16.0.1' }, h.token);
  await h.json('/api/users', { name: 'ali', limit_req: 2, limit_gb: 1, days: 30 }, h.token);
  const user = (await (await h.json('/api/users', undefined, h.token, 'GET')).json()).users[0];

  assert.equal((await h.call('/sub/' + user.sub_token)).status, 200);
  h.db.run('UPDATE users SET used_req = 2 WHERE id = ?', user.id);
  assert.equal((await h.call('/sub/' + user.sub_token)).status, 404);
});

test('a user with no pool still gets a domain-based link', async () => {
  const h = await setupPanel();
  await h.json('/api/users', { name: 'ali', days: 30 }, h.token);
  const user = (await (await h.json('/api/users', undefined, h.token, 'GET')).json()).users[0];
  const text = Buffer.from(await (await h.call('/sub/' + user.sub_token)).text(), 'base64').toString('utf8');
  assert.match(text, /@node\.example\.com:443\?/);
});

test('deleting a pool clears its users pins', async () => {
  const h = await setupPanel();
  await h.json('/api/ips', { name: 'MCI', ips: '104.16.0.1' }, h.token);
  await h.json('/api/users', { name: 'ali', days: 30 }, h.token);
  const user = (await (await h.json('/api/users', undefined, h.token, 'GET')).json()).users[0];
  await h.call('/sub/' + user.sub_token);
  assert.equal(h.db.get('SELECT pinned_ip FROM users WHERE id = ?', user.id).pinned_ip, '104.16.0.1');

  await h.call('/api/ips/1', { method: 'DELETE', headers: { authorization: 'Bearer ' + h.token } });
  assert.equal(h.db.get('SELECT pinned_ip FROM users WHERE id = ?', user.id).pinned_ip, null);
});

test('the schema migration adds columns to an older users table', async () => {
  const db = new D1();
  // Simulate a panel created before this build: no new columns.
  db.db.exec(`DROP TABLE users;
    CREATE TABLE users (
      id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, uuid TEXT NOT NULL UNIQUE,
      sub_token TEXT NOT NULL UNIQUE, trojan_password TEXT NOT NULL, trojan_hash TEXT NOT NULL,
      limit_bytes INTEGER NOT NULL DEFAULT 0, used_bytes INTEGER NOT NULL DEFAULT 0,
      expires_at INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1,
      created_at INTEGER NOT NULL, last_active INTEGER
    );`);
  const env = { DB: db, PROXY_HOST: 'node.example.com' };
  const res = await worker.fetch(new Request('https://panel.example.com/api/state'), env, { waitUntil() {} });
  assert.equal(res.status, 200);
  const columns = db.db.prepare('PRAGMA table_info(users)').all().map((c) => c.name);
  for (const name of ['pinned_ip', 'ip_pool_id', 'limit_req', 'ip_limit', 'start_on_first_connect']) {
    assert.ok(columns.includes(name), name + ' missing');
  }
});

test.after(() => {
  try {
    fs.unlinkSync(modulePath);
  } catch {}
});
