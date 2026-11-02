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

export function vlessLink(user, host, ip) {
  const address = ip || host;
  const q = new URLSearchParams({
    encryption: 'none',
    ...WS_QUERY,
    sni: host,
    host,
  });
  return `vless://${user.uuid}@${address}:443?${q.toString()}#${encodeURIComponent(user.name)}`;
}

export function trojanLink(user, host, ip) {
  const address = ip || host;
  const q = new URLSearchParams({ ...WS_QUERY, sni: host, host });
  return `trojan://${user.trojan_password}@${address}:443?${q.toString()}#${encodeURIComponent(user.name)}-trojan`;
}

export function buildLinks(user, host, ip) {
  const links = [];
  for (const protocol of protocolsFor(user)) {
    links.push(protocol === 'vless' ? vlessLink(user, host, ip) : trojanLink(user, host, ip));
  }
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
  const protocols = protocolsFor(user);
  const tags = protocols.map((p) => (p === 'vless' ? user.name : `${user.name}-trojan`));
  const proxies = protocols
    .map((p, i) => clashProxy(user, host, ip, p, tags[i]))
    .join('\n');
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
  const protocols = protocolsFor(user);
  const tags = protocols.map((p) => (p === 'vless' ? user.name : `${user.name}-trojan`));
  return {
    outbounds: protocols.map((p, i) => singboxOutbound(user, host, ip, p, tags[i])),
  };
}

export function jsonConfig(user, host, ip) {
  return {
    version: 1,
    name: user.name,
    host,
    address: ip || host,
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
  if (!clientIp || limit <= 0) return { allowed: true, active: JSON.stringify(fresh), changed: false };
  const known = clientIp in fresh;
  if (!known && Object.keys(fresh).length >= limit) {
    return { allowed: false, active: JSON.stringify(fresh), changed: false };
  }
  fresh[clientIp] = now;
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
