import test from 'node:test';
import assert from 'node:assert/strict';
import {
  hash32,
  parseIps,
  pickIp,
  vlessLink,
  trojanLink,
  subscription,
  protocolsFor,
  isBlocked,
  nextActiveIps,
  firstConnectExpiry,
  xrayConfig,
  buildLinks,
  clientKey,
  fragmentMask,
  statusPage,
} from '../worker/lib.js';

const user = {
  id: 7,
  name: 'ali',
  uuid: '0722d93a-024a-489d-be2a-a2e83a1f39b7',
  trojan_password: 'secret-pass',
  connection_type: 'both',
};

const host = 'node.example.com';
const ip = '104.16.0.1';

test('hash32 is deterministic and stays unsigned', () => {
  assert.equal(hash32('7' + host), hash32('7' + host));
  assert.ok(hash32('x') >= 0);
  assert.notEqual(hash32('7' + host), hash32('8' + host));
});

test('parseIps drops comments, blanks and duplicates', () => {
  assert.deepEqual(parseIps('1.1.1.1\n\n1.1.1.1#dup\n2.2.2.2 # note\n'), ['1.1.1.1', '2.2.2.2']);
});

test('pickIp is stable per user and null for an empty pool', () => {
  const pool = '1.1.1.1\n2.2.2.2\n3.3.3.3';
  assert.equal(pickIp(pool, '7' + host), pickIp(pool, '7' + host));
  assert.equal(pickIp('', '7' + host), null);
});

test('a link carries the clean IP and keeps the domain for SNI and Host', () => {
  const uri = vlessLink(user, host, ip);
  assert.match(uri, /@104\.16\.0\.1:443\?/);
  assert.match(uri, /sni=node\.example\.com/);
  assert.match(uri, /host=node\.example\.com/);
  assert.match(uri, /security=tls/);
});

test('without a pool the link falls back to the domain', () => {
  const uri = vlessLink(user, host, null);
  assert.match(uri, /@node\.example\.com:443\?/);
  assert.match(uri, /sni=node\.example\.com/);
});

test('trojan link uses the same addressing rules', () => {
  const uri = trojanLink(user, host, ip);
  assert.match(uri, /^trojan:\/\/secret-pass@104\.16\.0\.1:443\?/);
  assert.match(uri, /sni=node\.example\.com/);
});

test('connection_type controls which links are produced', () => {
  assert.deepEqual(protocolsFor({ connection_type: 'vless' }), ['vless']);
  assert.deepEqual(protocolsFor({ connection_type: 'trojan' }), ['trojan']);
  assert.deepEqual(protocolsFor({ connection_type: 'both' }), ['vless', 'trojan']);
});

test('base64 subscription decodes to the two links', () => {
  const body = subscription('base64', user, host, ip);
  const decoded = Buffer.from(body, 'base64').toString('utf8');
  assert.match(decoded, /^vless:\/\//);
  assert.match(decoded, /\ntrojan:\/\//);
});

test('plain subscription is the links themselves', () => {
  const body = subscription('plain', user, host, ip);
  assert.ok(body.startsWith('vless://'));
  assert.ok(body.includes('trojan://'));
});

test('clash subscription points at the IP and verifies the real hostname', () => {
  const yaml = subscription('clash', user, host, ip);
  assert.match(yaml, /server: "104\.16\.0\.1"/);
  assert.match(yaml, /servername: "node\.example\.com"/);
  assert.match(yaml, /network: ws/);
  assert.match(yaml, /type: vless/);
  assert.match(yaml, /MATCH,PROXY/);
});

test('singbox subscription is valid JSON with the right server and tls name', () => {
  const parsed = JSON.parse(subscription('singbox', user, host, ip));
  const out = parsed.outbounds[0];
  assert.equal(out.server, ip);
  assert.equal(out.tls.server_name, host);
  assert.equal(out.transport.headers.Host, host);
  assert.equal(out.uuid, user.uuid);
});

test('json subscription exposes the address it used', () => {
  const parsed = JSON.parse(subscription('json', user, host, ip));
  assert.equal(parsed.address, ip);
  assert.equal(parsed.host, host);
});

test('quota rules cover bytes, requests, expiry and disabled users', () => {
  const now = 1_000_000;
  assert.equal(isBlocked({ enabled: 1, limit_bytes: 10, used_bytes: 10 }, now), true);
  assert.equal(isBlocked({ enabled: 1, limit_req: 5, used_req: 5 }, now), true);
  assert.equal(isBlocked({ enabled: 1, expires_at: now - 1 }, now), true);
  assert.equal(isBlocked({ enabled: 0 }, now), true);
  assert.equal(isBlocked({ enabled: 1, limit_bytes: 10, used_bytes: 9 }, now), false);
});

test('device limit allows a known client and refuses a new one at the cap', () => {
  const now = 1_000_000;
  let state = nextActiveIps('{}', '1.1.1.1', now, 1);
  assert.equal(state.allowed, true);
  state = nextActiveIps(state.active, '1.1.1.1', now + 1, 1);
  assert.equal(state.allowed, true);
  state = nextActiveIps(state.active, '2.2.2.2', now + 2, 1);
  assert.equal(state.allowed, false);
});

test('stale device entries are pruned so a vanished client frees its slot', () => {
  const old = JSON.stringify({ '1.1.1.1': 0 });
  const state = nextActiveIps(old, '2.2.2.2', 200000, 1);
  assert.equal(state.allowed, true);
});

test('start_on_first_connect sets expiry only once', () => {
  const now = 1_000_000;
  assert.equal(firstConnectExpiry({ start_on_first_connect: 1, expiry_days: 30 }, now), now + 30 * 86400000);
  assert.equal(firstConnectExpiry({ start_on_first_connect: 1, first_connection_time: 5 }, now), null);
  assert.equal(firstConnectExpiry({ start_on_first_connect: 0 }, now), null);
});

test('xray config keeps the clean IP and the domain for TLS', () => {
  const cfg = xrayConfig(user, host, ip);
  const vless = cfg.outbounds.find((o) => o.protocol === 'vless');
  assert.equal(vless.settings.vnext[0].address, ip);
  assert.equal(vless.streamSettings.tlsSettings.serverName, host);
  assert.equal(vless.streamSettings.wsSettings.headers.Host, host);
  assert.equal(vless.streamSettings.wsSettings.path, '/ws?ed=2048');
  assert.deepEqual(cfg.dns.servers, ['1.1.1.1', '8.8.8.8']);

  const trojan = cfg.outbounds.find((o) => o.protocol === 'trojan');
  assert.equal(trojan.settings.servers[0].address, ip);
  assert.equal(trojan.settings.servers[0].password, user.trojan_password);
  assert.deepEqual(cfg.outbounds.map((o) => o.protocol), ['vless', 'trojan', 'freedom', 'blackhole']);
});

test('xray config follows the connection_type filter', () => {
  const cfg = xrayConfig({ ...user, connection_type: 'vless' }, host, ip);
  assert.deepEqual(cfg.outbounds.map((o) => o.protocol), ['vless', 'freedom', 'blackhole']);
});

test('device identity groups an IPv4 /24 and an IPv6 /64', () => {
  assert.equal(clientKey('1.2.3.4'), '1.2.3.0/24');
  assert.equal(clientKey('1.2.3.99'), '1.2.3.0/24');
  assert.equal(clientKey('2001:db8:1:2:3:4:5:6'), '2001:db8:1:2::/64');
  assert.equal(clientKey('2001:db8::1'), '2001:db8::/64');
});

test('two addresses in one /24 do not consume two device slots', () => {
  const now = 1_000_000;
  let state = nextActiveIps('{}', '1.2.3.4', now, 1);
  state = nextActiveIps(state.active, '1.2.3.99', now + 1, 1);
  assert.equal(state.allowed, true);
});

test('fragment settings travel in the fm parameter', () => {
  const tuned = { ...user, frag_len: '100-200', frag_int: '1-1' };
  const link = vlessLink(tuned, host, ip);
  const query = new URLSearchParams(link.split('?')[1].split('#')[0]);
  const mask = JSON.parse(query.get('fm'));
  assert.equal(mask.tcp[0].type, 'fragment');
  assert.equal(mask.tcp[0].settings.packets, 'tlshello');
  assert.deepEqual(mask.tcp[0].settings.lengths, ['100-200']);
  assert.deepEqual(mask.tcp[0].settings.delays, ['1-1']);
  assert.equal(new URLSearchParams(vlessLink(user, host, ip).split('?')[1].split('#')[0]).get('fm'), null);
});

test('one user can carry several addresses in one subscription', () => {
  const links = buildLinks(user, host, ['1.1.1.1', '2.2.2.2']);
  assert.equal(links.length, 4);
  assert.match(links[0], /@1\.1\.1\.1:443/);
  assert.match(links[2], /@2\.2\.2\.2:443/);
  assert.match(links[0], /#ali-1/);
});

test('the Xray config carries the fragment finalmask', () => {
  const cfg = xrayConfig({ ...user, frag_len: '1-3', frag_int: '1-2' }, host, ip);
  const mask = cfg.outbounds[0].streamSettings.finalmask;
  assert.equal(mask.tcp[0].settings.lengths[0], '1-3');
  assert.equal(mask.tcp[0].settings.delays[0], '1-2');
});

test('the status page renders usage without leaking anything else', () => {
  const html = statusPage(
    { ...user, sub_token: 'tok', used_bytes: 1.5 * 1024 ** 3, limit_bytes: 3 * 1024 ** 3, expires_at: Date.now() + 86400000 },
    host,
    [ip],
  );
  assert.match(html, /ali/);
  assert.match(html, /1\.50/);
  assert.match(html, /50%/);
  assert.match(html, new RegExp(ip.replace(/\./g, '\\.')));
});

test('a per-user MTU travels in the link', () => {
  const link = vlessLink({ ...user, mtu: 1280 }, host, ip);
  const query = new URLSearchParams(link.split('?')[1].split('#')[0]);
  assert.equal(query.get('mtu'), '1280');
  assert.equal(new URLSearchParams(vlessLink(user, host, ip).split('?')[1].split('#')[0]).get('mtu'), null);
});
