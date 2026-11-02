/**
 * Zentor Proxy Node — VLESS/Trojan روی WebSocket با احراز هویت D1
 *
 * این ورکر عمداً کوچک است تا در استارت سرد پایدار بماند. سه نکته‌ای که در نسخه‌ی اول جا افتاده
 * بود و کلاینت‌های واقعی (v2rayNG، NekoBox، sing-box) را زمین می‌زد:
 *   ۱. early data: کلاینت وقتی در لینک `?ed=2048` می‌بیند، اولین بایت‌ها را در هدر
 *      `sec-websocket-protocol` می‌فرستد؛ اگر ورکر آن را نخواند، هندشیک نیمه‌کاره می‌ماند.
 *   ۲. هدری که در دو فریم می‌آید: نباید فرض کرد اولین پیام کامل است.
 *   ۳. پاسخ 101 باید بدون هدر اضافه باشد تا کلاینت آن را قبول کند.
 */
import { connect } from 'cloudflare:sockets';

const dec = new TextDecoder();
const enc = new TextEncoder();

const HEX56 = /^[0-9a-f]{56}$/i;

async function lookup(env, kind, value) {
  const row = kind === 'uuid'
    ? await env.DB.prepare('SELECT * FROM users WHERE uuid = ?').bind(value).first()
    : await env.DB.prepare('SELECT * FROM users WHERE trojan_hash = ?').bind(value).first();
  if (!row || !row.enabled) return null;
  if (row.expires_at && row.expires_at < Date.now()) return null;
  if (row.limit_bytes > 0 && row.used_bytes >= row.limit_bytes) return null;
  return row;
}

/** base64url → bytes. Some clients send "binary" instead of data; that decodes to junk and is dropped. */
function decodeEarly(header) {
  if (!header || header.length > 4096) return null;
  const normalized = header.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4);
  let binary;
  try {
    binary = atob(padded);
  } catch {
    return null;
  }
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

/**
 * Is this really the start of a VLESS/Trojan request, or just a subprotocol name like "binary"?
 * Only the first bytes are visible here, so this checks shape, not credentials.
 */
function looksLikeHandshake(bytes) {
  if (!bytes || bytes.length < 18) return false;
  if (bytes[0] === 0) return true; // VLESS version 0
  if (bytes.length >= 58) {
    const head = dec.decode(bytes.slice(0, 56));
    return HEX56.test(head) && (bytes[56] === 13 || bytes[56] === 10);
  }
  return false;
}

/**
 * How many bytes the handshake header occupies.
 * @returns {number|null|-1} bytes needed, null when more data must arrive, -1 when malformed
 */
function headerLength(buf) {
  if (!buf.length) return null;

  if (buf[0] === 0) {
    if (buf.length < 18) return null;
    const base = 18 + buf[17]; // version + uuid + addon length + addons
    if (buf.length < base + 4) return null; // command + port + address type
    const atyp = buf[base + 3];
    let addrLen;
    if (atyp === 1) addrLen = 4;
    else if (atyp === 3) addrLen = 16;
    else if (atyp === 2) {
      if (buf.length < base + 5) return null;
      addrLen = 1 + buf[base + 4];
    } else return -1;
    return base + 4 + addrLen;
  }

  if (buf.length < 58) return null;
  if (!HEX56.test(dec.decode(buf.slice(0, 56)))) return -1;
  let p = 56;
  while (p < buf.length && (buf[p] === 13 || buf[p] === 10)) p++;
  if (buf.length < p + 4) return null;
  const atyp = buf[p + 3];
  let addrLen;
  if (atyp === 1) addrLen = 4;
  else if (atyp === 3) {
    if (buf.length < p + 5) return null;
    addrLen = 1 + buf[p + 4];
  } else if (atyp === 4) addrLen = 16;
  else return -1;
  return p + 4 + addrLen;
}

function concat(a, b) {
  if (!a.length) return b;
  const out = new Uint8Array(a.length + b.length);
  out.set(a, 0);
  out.set(b, a.length);
  return out;
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === '/health') {
      return new Response(JSON.stringify({ ok: true, node: true, db: !!env.DB }), {
        headers: { 'content-type': 'application/json' },
      });
    }
    if ((request.headers.get('Upgrade') || '').toLowerCase() !== 'websocket') {
      return new Response('expected websocket', { status: 426 });
    }

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    server.accept();

    let remote = null;
    let writer = null;
    let user = null;
    let up = 0;
    let down = 0;
    let closed = false;
    let ready = false;
    let pending = new Uint8Array(0);
    const queue = [];

    const flush = () => {
      if (!user || !(up + down)) return;
      const n = up + down;
      up = 0;
      down = 0;
      ctx.waitUntil(
        env.DB.prepare('UPDATE users SET used_bytes = used_bytes + ?, last_active = ? WHERE id = ?')
          .bind(n, Date.now(), user.id).run().catch(() => {}),
      );
    };

    const closeAll = () => {
      if (closed) return;
      closed = true;
      try { server.close(); } catch {}
      try { writer && writer.releaseLock(); } catch {}
      try { remote && remote.close(); } catch {}
      flush();
    };

    const dial = async (host, port, first) => {
      try {
        remote = connect({ hostname: host, port });
        writer = remote.writable.getWriter();
        await remote.opened;
        remote.readable.pipeTo(new WritableStream({
          write(chunk) {
            down += chunk.byteLength;
            try { server.send(chunk); } catch { closeAll(); }
            if (down > 65536) flush();
          },
          close() { closeAll(); },
        })).catch(() => closeAll());

        if (first && first.byteLength) {
          up += first.byteLength;
          await writer.write(first);
        }
        for (const part of queue.splice(0)) {
          up += part.byteLength;
          await writer.write(part);
        }
        ready = true;
      } catch {
        closeAll();
      }
    };

    const handshake = async (buf, need) => {
      const body = buf.slice(0, need);

      if (body[0] === 0) {
        const hex = [...body.slice(1, 17)].map((b) => b.toString(16).padStart(2, '0')).join('');
        const uuid = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
        user = await lookup(env, 'uuid', uuid);
        if (!user) return closeAll();

        let p = 18 + body[17];
        if (body[p] !== 1) return closeAll(); // only CONNECT; UDP is not proxied
        p += 1;
        const port = (body[p] << 8) | body[p + 1];
        p += 2;
        const atyp = body[p];
        p += 1;
        let host;
        if (atyp === 1) {
          host = `${body[p]}.${body[p + 1]}.${body[p + 2]}.${body[p + 3]}`;
          p += 4;
        } else if (atyp === 2) {
          const len = body[p];
          p += 1;
          host = dec.decode(body.slice(p, p + len));
          p += len;
        } else {
          host = [...body.slice(p, p + 16)].map((b) => b.toString(16).padStart(2, '0')).join(':');
          p += 16;
        }
        if (!host || !port) return closeAll();
        return dial(host, port, buf.slice(need));
      }

      // Trojan
      user = await lookup(env, 'trojan', dec.decode(body.slice(0, 56)).toLowerCase());
      if (!user) return closeAll();
      let p = 56;
      while (body[p] === 13 || body[p] === 10) p++;
      if (body[p] !== 1) return closeAll();
      p += 1;
      const port = (body[p] << 8) | body[p + 1];
      p += 2;
      const atyp = body[p];
      p += 1;
      let host;
      if (atyp === 1) {
        host = `${body[p]}.${body[p + 1]}.${body[p + 2]}.${body[p + 3]}`;
        p += 4;
      } else if (atyp === 3) {
        const len = body[p];
        p += 1;
        host = dec.decode(body.slice(p, p + len));
        p += len;
      } else {
        const parts = [];
        for (let i = 0; i < 8; i++) {
          parts.push(((body[p] << 8) | body[p + 1]).toString(16));
          p += 2;
        }
        host = parts.join(':');
      }
      if (!host || !port) return closeAll();
      return dial(host, port, buf.slice(need));
    };

    /**
     * Serialises handshake input. A frame that arrives while the first one is still awaiting D1 must
     * not be parsed as a second header — it is either payload (handshake done) or more header.
     */
    let chain = Promise.resolve();
    const feedSerial = (chunk) => {
      chain = chain.then(async () => {
        if (ready) {
          up += chunk.byteLength;
          await writer.write(chunk);
        } else if (user) {
          queue.push(chunk);
        } else {
          await feed(chunk);
        }
      }).catch(closeAll);
      return chain;
    };

    /** Feeds bytes to the handshake parser until the header is complete. */
    const feed = async (chunk) => {
      pending = concat(pending, chunk);
      if (pending.length > 8192) return closeAll(); // a header this large is not a real client
      const need = headerLength(pending);
      if (need === null) return;
      if (need < 0) return closeAll();
      if (pending.length < need) return;
      return handshake(pending, need);
    };

    // Early data: the client's first bytes arrive in the subprotocol header, not in a frame.
    const protocol = request.headers.get('sec-websocket-protocol');
    const early = decodeEarly(protocol);
    if (looksLikeHandshake(early)) feedSerial(early);

    server.addEventListener('message', async (event) => {
      try {
        const data = event.data;
        const bytes = typeof data === 'string' ? enc.encode(data) : new Uint8Array(data);
        if (!ready) return feedSerial(bytes);
        up += bytes.byteLength;
        await writer.write(bytes);
        if (up > 65536) flush();
      } catch {
        closeAll();
      }
    });
    server.addEventListener('close', closeAll);
    server.addEventListener('error', closeAll);

    const headers = protocol ? { 'sec-websocket-protocol': protocol } : undefined;
    return new Response(null, { status: 101, webSocket: client, headers });
  },
};
