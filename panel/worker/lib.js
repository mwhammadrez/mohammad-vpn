/**
 * Pure helpers shared by the panel worker and its tests.
 *
 * The one idea that makes a config work on an Iranian line: the link's `address` is a clean
 * Cloudflare edge IP, while TLS `sni` and the WebSocket `Host` stay the node's real domain. DNS is
 * no longer on the path, and the edge still routes the request to the worker because SNI/Host say
 * which zone it belongs to.
 */

export const FORMATS = ['base64', 'plain', 'clash', 'singbox', 'json'];

const enc = new TextEncoder();

/** FNV-1a over UTF-8. Deterministic, so the same user always lands on the same IP. */
export function hash32(str) {
  let h = 0x811c9dc5;
  const bytes = enc.encode(String(str));
  for (let i = 0; i < bytes.length; i++) {
    h ^= bytes[i];
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

/** Lines, comments after `#`, blank lines and duplicates removed. */
export function parseIps(text) {
  const out = [];
  const seen = new Set();
  for (const line of String(text || '').split(/\r?\n/)) {
    const ip = line.split('#')[0].trim();
    if (!ip || seen.has(ip)) continue;
    seen.add(ip);
    out.push(ip);
  }
  return out;
}

/** The user's sticky IP, or a deterministic pick from the pool. Null means "use the domain". */
export function pickIp(ipsText, seed) {
  const list = parseIps(ipsText);
  if (!list.length) return null;
  return list[hash32(seed) % list.length];
}

/** Protocols this user is allowed to receive. */
export function protocolsFor(user) {
  const type = String(user?.connection_type || 'both').toLowerCase();
  if (type === 'vless') return ['vless'];
  if (type === 'trojan') return ['trojan'];
  return ['vless', 'trojan'];
}

const WS_QUERY = {
  security: 'tls',
  fp: 'chrome',
  alpn: 'http/1.1',
  type: 'ws',
  path: '/ws?ed=2048',
};

/** One or many addresses; a null entry means "use the node domain". */
export function addressList(ip) {
  if (Array.isArray(ip)) return ip.length ? ip : [null];
  return [ip || null];
}

/**
 * The anti-DPI fragment mask carried as the `fm=` link parameter. Lengths/delays are the operator
 * presets a middlebox cannot match; `packets: tlshello` splits only the TLS ClientHello.
 */
export function fragmentMask(user) {
  const length = String(user?.frag_len || '').trim();
  const delay = String(user?.frag_int || '').trim();
  if (!length || !delay) return null;
  return {
    tcp: [{
      type: 'fragment',
      settings: { packets: 'tlshello', lengths: [length], delays: [delay], maxSplit: '3-6' },
    }],
  };
}

function addFragment(q, user) {
  const mask = fragmentMask(user);
  if (mask) q.set('fm', JSON.stringify(mask));
  return q;
}

export function vlessLink(user, host, ip, label = user.name) {
  const address = ip || host;
  const q = addFragment(new URLSearchParams({
    encryption: 'none',
    ...WS_QUERY,
    sni: host,
    host,
  }), user);
  if (Number(user.mtu) > 0) q.set('mtu', String(user.mtu));
  return `vless://${user.uuid}@${address}:443?${q.toString()}#${encodeURIComponent(label)}`;
}

export function trojanLink(user, host, ip, label = `${user.name}-trojan`) {
  const address = ip || host;
  const q = addFragment(new URLSearchParams({ ...WS_QUERY, sni: host, host }), user);
  if (Number(user.mtu) > 0) q.set('mtu', String(user.mtu));
  return `trojan://${user.trojan_password}@${address}:443?${q.toString()}#${encodeURIComponent(label)}`;
}

export function buildLinks(user, host, ip) {
  const addresses = addressList(ip);
  const links = [];
  addresses.forEach((address, index) => {
    const label = addresses.length > 1 ? `${user.name}-${index + 1}` : user.name;
    for (const protocol of protocolsFor(user)) {
      links.push(
        protocol === 'vless'
          ? vlessLink(user, host, address, label)
          : trojanLink(user, host, address, `${label}-trojan`),
      );
    }
  });
  return links;
}

export function b64(str) {
  let s = '';
  const bytes = enc.encode(str);
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s);
}

/** JSON-string escaping is valid YAML for a quoted scalar. */
export function yamlQuote(value) {
  return JSON.stringify(String(value ?? ''));
}

function clashProxy(user, host, ip, protocol, tag) {
  const lines = [
    `  - name: ${yamlQuote(tag)}`,
    `    type: ${protocol}`,
    `    server: ${yamlQuote(ip || host)}`,
    '    port: 443',
  ];
  if (protocol === 'vless') lines.push(`    uuid: ${yamlQuote(user.uuid)}`);
  else lines.push(`    password: ${yamlQuote(user.trojan_password)}`);
  lines.push(
    '    network: ws',
    '    tls: true',
    `    servername: ${yamlQuote(host)}`,
    '    udp: true',
    '    skip-cert-verify: false',
    '    ws-opts:',
    '      path: "/ws?ed=2048"',
    '      headers:',
    `        Host: ${yamlQuote(host)}`,
  );
  return lines.join('\n');
}

export function clashYaml(user, host, ip) {
  const addresses = addressList(ip);
  const entries = [];
  addresses.forEach((address, index) => {
    for (const protocol of protocolsFor(user)) {
      const base = addresses.length > 1 ? `${user.name}-${index + 1}` : user.name;
      entries.push({ address, protocol, tag: protocol === 'vless' ? base : `${base}-trojan` });
    }
  });
  const proxies = entries
    .map((e) => clashProxy(user, host, e.address, e.protocol, e.tag))
    .join('\n');
  const tags = entries.map((e) => e.tag);
  return [
    'port: 7890',
    'socks-port: 7891',
    'allow-lan: false',
    'mode: rule',
    'log-level: info',
    'proxies:',
    proxies,
    'proxy-groups:',
    '  - name: PROXY',
    '    type: select',
    `    proxies: [${tags.map(yamlQuote).join(', ')}]`,
    'rules:',
    '  - MATCH,PROXY',
    '',
  ].join('\n');
}

function singboxOutbound(user, host, ip, protocol, tag) {
  const common = {
    type: protocol,
    tag,
    server: ip || host,
    server_port: 443,
    tls: { enabled: true, server_name: host },
    transport: { type: 'ws', path: '/ws?ed=2048', headers: { Host: host } },
  };
  if (protocol === 'vless') return { ...common, uuid: user.uuid };
  return { ...common, password: user.trojan_password };
}

export function singboxConfig(user, host, ip) {
  const addresses = addressList(ip);
  const outbounds = [];
  addresses.forEach((address, index) => {
    for (const protocol of protocolsFor(user)) {
      const base = addresses.length > 1 ? `${user.name}-${index + 1}` : user.name;
      const tag = protocol === 'vless' ? base : `${base}-trojan`;
      outbounds.push(singboxOutbound(user, host, address, protocol, tag));
    }
  });
  return { outbounds };
}

export function jsonConfig(user, host, ip) {
  return {
    version: 1,
    name: user.name,
    host,
    address: addressList(ip)[0] || host,
    proxies: buildLinks(user, host, ip).map((uri) => ({ uri })),
  };
}

export function contentType(format) {
  if (format === 'clash') return 'text/yaml; charset=utf-8';
  if (format === 'singbox' || format === 'json') return 'application/json; charset=utf-8';
  return 'text/plain; charset=utf-8';
}

/**
 * The body for `GET /sub/<token>?format=…`. `base64` remains the default so existing clients keep
 * working unchanged.
 */
export function subscription(format, user, host, ip) {
  const name = FORMATS.includes(format) ? format : 'base64';
  if (name === 'plain') return buildLinks(user, host, ip).join('\n');
  if (name === 'clash') return clashYaml(user, host, ip);
  if (name === 'singbox') return JSON.stringify(singboxConfig(user, host, ip), null, 2);
  if (name === 'json') return JSON.stringify(jsonConfig(user, host, ip), null, 2);
  return b64(buildLinks(user, host, ip).join('\n'));
}

/**
 * The public status page behind `/status/<token>`. It carries no secrets beyond the token in the
 * URL and answers the questions a channel user actually asks: how much is left, when it ends, and
 * where the subscription is.
 */
export function statusPage(user, host, ips, now = Date.now()) {
  const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const total = Number(user.limit_bytes) || 0;
  const used = Number(user.used_bytes) || 0;
  const percent = total ? Math.min(100, Math.round((used / total) * 100)) : 0;
  const remaining = total ? Math.max(0, total - used) : 0;
  const gb = (bytes) => (bytes / 1024 ** 3).toFixed(2);
  const expiry = user.expires_at ? new Date(user.expires_at).toISOString().slice(0, 10) : 'بدون انقضا';
  const addresses = (Array.isArray(ips) ? ips : [ips]).filter(Boolean);
  const blocked = !user.enabled ||
    (user.expires_at && user.expires_at < now) ||
    (total > 0 && used >= total);
  const state = blocked ? 'غیرفعال' : 'فعال';
  const color = blocked ? '#b42318' : '#067647';
  return `<!doctype html>
<html lang="fa" dir="rtl"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>وضعیت اشتراک · ${esc(user.name)}</title>
<style>
body{margin:0;background:#0b0f14;color:#e6edf3;font:15px/1.7 system-ui,-apple-system,"Segoe UI",sans-serif}
.card{max-width:560px;margin:40px auto;background:#111820;border:1px solid #22303d;border-radius:18px;padding:26px}
h1{font-size:20px;margin:0 0 4px}.muted{color:#8b98a5;font-size:13px}
.badge{display:inline-block;padding:3px 10px;border-radius:999px;font-size:12px;color:#fff;background:${color}}
.row{display:flex;justify-content:space-between;border-top:1px solid #1d2833;padding:11px 0}
.bar{height:9px;background:#1d2833;border-radius:99px;overflow:hidden;margin:8px 0 14px}
.bar>i{display:block;height:100%;width:${percent}%;background:${color}}
code{background:#0b1117;border:1px solid #22303d;border-radius:8px;padding:3px 7px;direction:ltr;display:inline-block;max-width:100%;overflow-wrap:anywhere}
a{color:#7cc4ff}
</style></head><body><div class="card">
<h1>${esc(user.name)} <span class="badge">${state}</span></h1>
<div class="muted">${esc(host)}</div>
<div class="row"><span>مصرف‌شده</span><b>${gb(used)} GB</b></div>
<div class="row"><span>سهمیه</span><b>${total ? gb(total) + ' GB' : 'نامحدود'}</b></div>
${total ? `<div class="bar"><i></i></div><div class="muted">${percent}% مصرف‌شده · ${gb(remaining)} GB باقی‌مانده</div>` : ''}
<div class="row"><span>انقضا</span><b>${esc(expiry)}</b></div>
<div class="row"><span>آدرس‌ها</span><b>${addresses.length ? addresses.map((ip) => esc(ip)).join('<br>') : esc(host)}</b></div>
<div class="row"><span>لینک اشتراک</span><b><a href="/sub/${esc(user.sub_token)}">باز کردن</a></b></div>
<div class="muted" style="margin-top:18px">این صفحه فقط وضعیت را نشان می‌دهد؛ برای اتصال، لینک اشتراک را در اپ وارد کنید.</div>
</div></body></html>`;
}

export function profileTitle(name) {
  return 'base64:' + b64(String(name || ''));
}

/**
 * VLESS response header: version (0) followed by a zero-length addons block.
 *
 * Xray's client calls DecodeResponseHeader() before reading any payload and aborts with
 * "unexpected response version" if the first byte is not 0. A proxy that pipes the destination's
 * bytes straight through therefore never carries a single VLESS connection.
 */
export const VLESS_RESPONSE_HEADER = new Uint8Array([0, 0]);

/* ───────────── quota rules (shared with the node worker) ───────────── */

/** Every reason a user must not be allowed to connect. Pure so it is testable off-Workers. */
export function isBlocked(row, now) {
  if (!row || !row.enabled) return true;
  if (row.expires_at && row.expires_at < now) return true;
  if (row.limit_bytes > 0 && row.used_bytes >= row.limit_bytes) return true;
  if (row.limit_req > 0 && row.used_req >= row.limit_req) return true;
  return false;
}

/**
 * Device limit. Returns the next `active_ips` JSON and whether this client may connect.
 * Entries older than 90 s are dropped: a client that vanished must not hold a slot forever.
 */
/**
 * Device identity: an IPv4 /24 or IPv6 /64 counts as one device. Carriers rotate addresses inside a
 * prefix, so counting the exact IP would burn a slot on every reconnect.
 */
export function clientKey(ip) {
  const value = String(ip || '').trim();
  if (!value) return '';
  if (value.includes(':')) {
    const head = value.split('::')[0].split(':').filter(Boolean).slice(0, 4);
    return head.length ? head.join(':') + '::/64' : value;
  }
  const parts = value.split('.');
  if (parts.length === 4) return `${parts[0]}.${parts[1]}.${parts[2]}.0/24`;
  return value;
}

export function nextActiveIps(raw, clientIp, now, limit) {
  let seen;
  try {
    seen = JSON.parse(raw || '{}');
  } catch {
    seen = {};
  }
  if (!seen || typeof seen !== 'object' || Array.isArray(seen)) seen = {};
  const fresh = {};
  for (const [ip, ts] of Object.entries(seen)) {
    if (now - Number(ts) < 90000) fresh[ip] = Number(ts);
  }
  const key = clientKey(clientIp);
  if (!key || limit <= 0) return { allowed: true, active: JSON.stringify(fresh), changed: false };
  const known = key in fresh;
  if (!known && Object.keys(fresh).length >= limit) {
    return { allowed: false, active: JSON.stringify(fresh), changed: false };
  }
  fresh[key] = now;
  return { allowed: true, active: JSON.stringify(fresh), changed: true };
}

/**
 * `start_on_first_connect`: the clock starts on the first successful auth, not when the user was
 * created. Returns the new expires_at, or null when nothing needs writing.
 */
export function firstConnectExpiry(row, now) {
  if (!row.start_on_first_connect || row.first_connection_time) return null;
  const days = Number(row.expiry_days) || 0;
  return days > 0 ? now + days * 86400000 : 0;
}

/**
 * A complete Xray client config, the shape a desktop/mobile client can import directly.
 *
 * Same addressing rule as the subscription links: `address` is the clean edge IP (or the node
 * domain when no pool exists), while `serverName` and the WebSocket `Host` header stay the real
 * domain. Routing lists private ranges literally so the file works without geoip.dat.
 */
export function xrayConfig(user, host, ip) {
  const address = addressList(ip)[0] || host;
  const outbounds = [];
  const mask = fragmentMask(user);
  const stream = (extra) => {
    const value = {
      network: 'ws',
      wsSettings: { path: '/ws?ed=2048', headers: { Host: host } },
      security: 'tls',
      tlsSettings: { serverName: host, allowInsecure: false },
      ...extra,
    };
    if (mask) value.finalmask = mask;
    return value;
  };

  if (protocolsFor(user).includes('vless')) {
    outbounds.push({
      tag: `vless-${host}-443`,
      protocol: 'vless',
      settings: {
        vnext: [{
          address,
          port: 443,
          users: [{ id: user.uuid, encryption: 'none' }],
        }],
      },
      streamSettings: stream(),
    });
  }

  if (protocolsFor(user).includes('trojan')) {
    outbounds.push({
      tag: `trojan-${host}-443`,
      protocol: 'trojan',
      settings: {
        servers: [{ address, port: 443, password: user.trojan_password }],
      },
      streamSettings: stream(),
    });
  }

  outbounds.push({ tag: 'direct', protocol: 'freedom' }, { tag: 'block', protocol: 'blackhole' });

  return {
    log: { loglevel: 'warning' },
    // UDP DNS, like the commercial panels: the node bridges a port-53 association to TCP DNS, so
    // the client gets the normal resolver path instead of a TCP-only workaround.
    dns: { servers: ['1.1.1.1', '8.8.8.8'] },
    inbounds: [
      {
        tag: 'socks-in', listen: '127.0.0.1', port: 10808, protocol: 'socks',
        settings: { auth: 'noauth', udp: true },
        sniffing: { enabled: true, destOverride: ['http', 'tls'] },
      },
      {
        tag: 'http-in', listen: '127.0.0.1', port: 10809, protocol: 'http',
        settings: {},
        sniffing: { enabled: true, destOverride: ['http', 'tls'] },
      },
    ],
    outbounds,
    routing: {
      domainStrategy: 'IPIfNonMatch',
      rules: [
        {
          type: 'field',
          ip: ['10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16', '127.0.0.0/8', '169.254.0.0/16'],
          outboundTag: 'direct',
        },
      ],
    },
  };
}
