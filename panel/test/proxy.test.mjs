/**
 * Integration test for the built node worker.
 *
 * Two globals a Worker has are replaced here: `cloudflare:sockets` (Node net) and `WebSocketPair`
 * (a recording pair). Everything else — routing, auth against SQLite, WS framing, early data — is
 * the deployed code.
 *
 * This is the test that catches the bug that made every VLESS config fail on a phone: the client
 * decodes a two-byte response header before any payload, and a worker that only pipes the
 * destination's bytes sends the first payload byte where version 0 is expected.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import crypto from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const schema = fs.readFileSync(path.join(root, 'panel/worker/schema.sql'), 'utf8');
const built = path.join(root, 'app/src/main/assets/panel/proxy-worker.js');

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
    const r = this.db.prepare(this.sql).run(...this.params);
    return { success: true, meta: { changes: Number(r.changes) } };
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
    return statements.map((s) => s.run());
  }
  run(sql, ...params) {
    return this.db.prepare(sql).run(...params);
  }
  get(sql, ...params) {
    return this.db.prepare(sql).get(...params);
  }
}

class Server {
  constructor() {
    this.listeners = {};
    this.sent = [];
    this.closed = false;
  }
  accept() {}
  addEventListener(type, fn) {
    (this.listeners[type] ||= []).push(fn);
  }
  send(data) {
    this.sent.push(new Uint8Array(data));
  }
  close() {
    this.closed = true;
    for (const fn of this.listeners.close || []) fn({});
  }
  emit(type, event) {
    for (const fn of this.listeners[type] || []) fn(event);
  }
}

// Node's Response rejects status 101; Workers accepts it with a webSocket. Keep the fields the test
// needs and hand Node a status it allows.
const NativeResponse = globalThis.Response;
globalThis.Response = class extends NativeResponse {
  constructor(body, init = {}) {
    if (init.status === 101) {
      super(null, { status: 200, headers: init.headers });
      this.upgradeStatus = 101;
      this.webSocket = init.webSocket;
    } else {
      super(body, init);
    }
  }
};

let lastServer = null;
globalThis.WebSocketPair = class {
  constructor() {
    this[0] = {};
    this[1] = new Server();
    lastServer = this[1];
  }
};

if (!fs.existsSync(built)) throw new Error('run `node panel/build.mjs` before this suite');

const source = fs
  .readFileSync(built, 'utf8')
  .replace('from"cloudflare:sockets"', 'from"./sockets-stub.mjs"');
const modulePath = path.join(here, '.proxy-under-test.mjs');
fs.writeFileSync(modulePath, source);
const worker = (await import(modulePath)).default;

const uuid = '5ddd358d-b923-4a29-ac93-e8247b29404e';
const password = '6f83b4aa4b66a37715de';
const uuidBytes = new Uint8Array(uuid.replace(/-/g, '').match(/../g).map((h) => parseInt(h, 16)));
// Trojan sends the sha224 digest as 56 ASCII hex characters, not as raw bytes.
const trojanHash = crypto.createHash('sha224').update(password).digest('hex');

function vlessHeader(port, ip = [127, 0, 0, 1]) {
  return new Uint8Array([
    0, ...uuidBytes, 0, 1,
    (port >> 8) & 255, port & 255,
    1, ...ip,
  ]);
}

function trojanHeader(port, ip = [127, 0, 0, 1]) {
  return new Uint8Array([
    ...new TextEncoder().encode(trojanHash), 13, 10, 1,
    (port >> 8) & 255, port & 255,
    1, ...ip,
  ]);
}

function makeEnv() {
  const db = new D1();
  const now = Date.now();
  db.run(
    `INSERT INTO users (name,uuid,sub_token,trojan_password,trojan_hash,limit_bytes,used_bytes,
      expires_at,enabled,created_at,ip_limit,start_on_first_connect,connection_type)
     VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)`,
    'ali', uuid, 'sub', password, trojanHash,
    0, 0, now + 86400000, 1, now, 0, 0, 'both',
  );
  return { DB: db, PROXY_HOST: 'node.test' };
}

function request(protocol) {
  const headers = new Headers({ Upgrade: 'websocket', 'CF-Connecting-IP': '203.0.113.9' });
  if (protocol) headers.set('sec-websocket-protocol', protocol);
  return { url: 'https://node.test/ws?ed=2048', headers };
}

function startTarget() {
  return new Promise((resolve) => {
    const received = [];
    const server = net.createServer((socket) => {
      socket.on('data', (chunk) => received.push(Buffer.from(chunk)));
      socket.write('HELLO');
    });
    server.listen(0, '127.0.0.1', () => {
      resolve({ server, port: server.address().port, received });
    });
  });
}

async function waitFor(predicate, timeoutMs = 3000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (predicate()) return true;
    await new Promise((r) => setTimeout(r, 20));
  }
  return false;
}

function joinSent(server) {
  return Buffer.concat(server.sent.map((b) => Buffer.from(b)));
}

test('VLESS sends the 0,0 response header before any payload', async () => {
  const { server: target, port, received } = await startTarget();
  try {
    const res = await worker.fetch(request(), makeEnv(), { waitUntil() {} });
    assert.equal(res.upgradeStatus, 101);
    const ws = lastServer;

    ws.emit('message', { data: vlessHeader(port) });
    await waitFor(() => joinSent(ws).length >= 7);

    const bytes = joinSent(ws);
    assert.equal(bytes[0], 0, 'response version');
    assert.equal(bytes[1], 0, 'addon length');
    assert.equal(bytes.slice(2, 7).toString(), 'HELLO');
  } finally {
    target.close();
  }
});

test('VLESS works when the header arrives as early data', async () => {
  const { server: target, port } = await startTarget();
  try {
    const early = Buffer.from(vlessHeader(port)).toString('base64')
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const res = await worker.fetch(request(early), makeEnv(), { waitUntil() {} });
    assert.equal(res.upgradeStatus, 101);
    const ws = lastServer;

    await waitFor(() => joinSent(ws).length >= 7);
    const bytes = joinSent(ws);
    assert.deepEqual([bytes[0], bytes[1]], [0, 0]);
    assert.equal(bytes.slice(2, 7).toString(), 'HELLO');
  } finally {
    target.close();
  }
});

test('Trojan has no response header: the first bytes are the target data', async () => {
  const { server: target, port } = await startTarget();
  try {
    const res = await worker.fetch(request(), makeEnv(), { waitUntil() {} });
    assert.equal(res.upgradeStatus, 101);
    const ws = lastServer;

    ws.emit('message', { data: trojanHeader(port) });
    await waitFor(() => joinSent(ws).length >= 5);

    const bytes = joinSent(ws);
    assert.equal(bytes.slice(0, 5).toString(), 'HELLO');
  } finally {
    target.close();
  }
});

test('a Blob frame is decoded (wrangler local delivers Blobs)', async () => {
  const { server: target, port } = await startTarget();
  try {
    const res = await worker.fetch(request(), makeEnv(), { waitUntil() {} });
    assert.equal(res.upgradeStatus, 101);
    const ws = lastServer;

    ws.emit('message', { data: new Blob([vlessHeader(port)]) });
    await waitFor(() => joinSent(ws).length >= 7);

    const bytes = joinSent(ws);
    assert.deepEqual([bytes[0], bytes[1]], [0, 0]);
    assert.equal(bytes.slice(2, 7).toString(), 'HELLO');
  } finally {
    target.close();
  }
});

test('an unknown uuid closes the socket without connecting', async () => {
  const { server: target, port } = await startTarget();
  try {
    const res = await worker.fetch(request(), makeEnv(), { waitUntil() {} });
    assert.equal(res.upgradeStatus, 101);
    const ws = lastServer;

    const bad = vlessHeader(port);
    bad[1] ^= 0xff;
    ws.emit('message', { data: bad });
    await waitFor(() => ws.closed);
    assert.equal(ws.closed, true);
    assert.equal(joinSent(ws).length, 0);
  } finally {
    target.close();
  }
});

test.after(() => {
  try {
    fs.unlinkSync(modulePath);
  } catch {}
});
