import"cloudflare:sockets";const z={"content-type":"application/json; charset=utf-8"},x=new TextEncoder,rt=new TextDecoder,c=(n,t=200,r={})=>new Response(JSON.stringify(n),{status:t,headers:{...z,"cache-control":"no-store",...r}}),J=(n,t=200)=>new Response(n,{status:t,headers:{"content-type":"text/html; charset=utf-8","cache-control":"no-store"}}),T=n=>[...new Uint8Array(n)].map(t=>t.toString(16).padStart(2,"0")).join(""),v=async n=>T(await crypto.subtle.digest("SHA-256",x.encode(String(n)))),C=(n=16)=>T(crypto.getRandomValues(new Uint8Array(n)));function F(){const n=crypto.getRandomValues(new Uint8Array(16));n[6]=n[6]&15|64,n[8]=n[8]&63|128;const t=[...n].map(r=>r.toString(16).padStart(2,"0")).join("");return`${t.slice(0,8)}-${t.slice(8,12)}-${t.slice(12,16)}-${t.slice(16,20)}-${t.slice(20)}`}async function L(n,t){const r=await crypto.subtle.importKey("raw",x.encode(n),{name:"HMAC",hash:"SHA-256"},!1,["sign"]);return T(await crypto.subtle.sign("HMAC",r,x.encode(t)))}function _(n,t){if(n=String(n),t=String(t),n.length!==t.length)return!1;let r=0;for(let e=0;e<n.length;e++)r|=n.charCodeAt(e)^t.charCodeAt(e);return r===0}const W=[1116352408,1899447441,3049323471,3921009573,961987163,1508970993,2453635748,2870763221,3624381080,310598401,607225278,1426881987,1925078388,2162078206,2614888103,3248222580,3835390401,4022224774,264347078,604807628,770255983,1249150122,1555081692,1996064986,2554220882,2821834349,2952996808,3210313671,3336571891,3584528711,113926993,338241895,666307205,773529912,1294757372,1396182291,1695183700,1986661051,2177026350,2456956037,2730485921,2820302411,3259730800,3345764771,3516065817,3600352804,4094571909,275423344,430227734,506948616,659060556,883997877,958139571,1322822218,1537002063,1747873779,1955562222,2024104815,2227730452,2361852424,2428436474,2756734187,3204031479,3329325298];function G(n){const t=x.encode(String(n)),r=t.length*8,e=new Uint8Array((t.length+9>>6)+1<<6);e.set(t),e[t.length]=128,new DataView(e.buffer).setUint32(e.length-4,r>>>0),new DataView(e.buffer).setUint32(e.length-8,Math.floor(r/2**32));let i=[3238371032,914150663,812702999,4144912697,4290775857,1750603025,1694076839,3204075428];const d=new Int32Array(64),p=new DataView(e.buffer);for(let s=0;s<e.length;s+=64){for(let u=0;u<16;u++)d[u]=p.getInt32(s+u*4);for(let u=16;u<64;u++){const S=(d[u-15]>>>7|d[u-15]<<25)^(d[u-15]>>>18|d[u-15]<<14)^d[u-15]>>>3,E=(d[u-2]>>>17|d[u-2]<<15)^(d[u-2]>>>19|d[u-2]<<13)^d[u-2]>>>10;d[u]=d[u-16]+S+d[u-7]+E|0}let[a,o,f,h,b,g,y,k]=i;for(let u=0;u<64;u++){const S=(b>>>6|b<<26)^(b>>>11|b<<21)^(b>>>25|b<<7),E=b&g^~b&y,U=k+S+E+W[u]+d[u]|0,B=(a>>>2|a<<30)^(a>>>13|a<<19)^(a>>>22|a<<10),M=a&o^a&f^o&f,$=B+M|0;k=y,y=g,g=b,b=h+U|0,h=f,f=o,o=a,a=U+$|0}i=[i[0]+a|0,i[1]+o|0,i[2]+f|0,i[3]+h|0,i[4]+b|0,i[5]+g|0,i[6]+y|0,i[7]+k|0]}return i.slice(0,7).map(s=>(s>>>0).toString(16).padStart(8,"0")).join("")}const l={async getUsers(n){return(await n.DB.prepare("SELECT * FROM users ORDER BY id DESC").all()).results||[]},async getUser(n,t){return n.DB.prepare("SELECT * FROM users WHERE id = ?").bind(t).first()},async byUuid(n,t){return n.DB.prepare("SELECT * FROM users WHERE uuid = ?").bind(t).first()},async byTrojan(n,t){return n.DB.prepare("SELECT * FROM users WHERE trojan_hash = ?").bind(t).first()},async bySubToken(n,t){return n.DB.prepare("SELECT * FROM users WHERE sub_token = ?").bind(t).first()},async addUser(n,{name:t,limit_gb:r,days:e}){const i=F(),d=C(16),p=C(10),s=Date.now(),a=e>0?s+e*864e5:0;return await n.DB.prepare("INSERT INTO users (name, uuid, sub_token, trojan_password, trojan_hash, limit_bytes, used_bytes, expires_at, enabled, created_at) VALUES (?,?,?,?,?,?,0,?,1,?)").bind(t,i,d,p,G(p),Math.round((r||0)*1024**3),a,s).run(),{uuid:i,sub:d,password:p}},async addUsage(n,t,r){r&&await n.DB.prepare("UPDATE users SET used_bytes = used_bytes + ?, last_active = ? WHERE id = ?").bind(r,Date.now(),t).run()},async setting(n,t,r=null){const e=await n.DB.prepare("SELECT value FROM settings WHERE key = ?").bind(t).first();return e?e.value:r},async setSetting(n,t,r){await n.DB.prepare("INSERT INTO settings (key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value = excluded.value").bind(t,String(r)).run()},async getConfigs(n,t=!1){const r="SELECT * FROM configs"+(t?" WHERE enabled = 1":"")+" ORDER BY sort ASC, id ASC";return(await n.DB.prepare(r).all()).results||[]}};let O=null;function K(n){return O||(O=n.DB.prepare("CREATE TABLE IF NOT EXISTS configs (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, uri TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, sort INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)").run().catch(()=>{})),O}const N="zp_session",R=7*864e5;async function A(n){let t=await l.setting(n,"session_secret");return t||(t=C(32),await l.setSetting(n,"session_secret",t)),t}async function P(n){const t=Date.now()+R,r=await L(await A(n),`s.${t}`);return`${t}.${r}`}async function I(n,t){const e=(n.headers.get("cookie")||"").match(new RegExp(`${N}=([^;]+)`)),i=(n.headers.get("authorization")||"").replace(/^Bearer\s+/i,""),d=e?decodeURIComponent(e[1]):i;if(!d)return!1;const[p,s]=d.split(".");return!p||!s||Number(p)<Date.now()?!1:_(s,await L(await A(t),`s.${p}`))}const H=(n,t)=>`${N}=${encodeURIComponent(n)}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=${t}`;async function m(n){try{return await n.json()}catch{return{}}}function V(n,t){return t.PANEL_HOST||new URL(n.url).host}function Y(n,t){const r=new URLSearchParams({encryption:"none",security:"tls",sni:t,fp:"chrome",alpn:"http/1.1",type:"ws",host:t,path:"/ws?ed=2048"});return`vless://${n.uuid}@${t}:443?${r.toString()}#${encodeURIComponent(n.name)}`}function X(n,t){const r=new URLSearchParams({security:"tls",sni:t,fp:"chrome",alpn:"http/1.1",type:"ws",host:t,path:"/ws?ed=2048"});return`trojan://${n.trojan_password}@${t}:443?${r.toString()}#${encodeURIComponent(n.name)}-trojan`}function Z(n){let t="";const r=x.encode(n);for(let e=0;e<r.length;e++)t+=String.fromCharCode(r[e]);return btoa(t)}async function Q(n,t,r){const e=await l.bySubToken(t,r);if(!e||!e.enabled)return new Response("not found",{status:404});const i=await l.setting(t,"node_host")||t.PROXY_HOST||V(n,t),d=[Y(e,i),X(e,i)].join(`
`),p=Z(d);return new Response(p,{headers:{"content-type":"text/plain; charset=utf-8","cache-control":"no-store","profile-update-interval":"12","subscription-userinfo":`upload=0; download=${e.used_bytes}; total=${e.limit_bytes||0}; expire=${e.expires_at?Math.floor(e.expires_at/1e3):0}`}})}const q=n=>{let t="";for(const r of new Uint8Array(n))t+=String.fromCharCode(r);return btoa(t).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"")};function D(n){return n==null?"null":typeof n=="number"?Number.isFinite(n)?JSON.stringify(n):"null":typeof n=="boolean"?n?"true":"false":typeof n=="string"?JSON.stringify(n):Array.isArray(n)?"["+n.map(D).join(",")+"]":"{"+Object.keys(n).sort().map(t=>JSON.stringify(t)+":"+D(n[t])).join(",")+"}"}async function j(n){let t=await l.setting(n,"rc_priv");if(!t){const r=await crypto.subtle.generateKey({name:"Ed25519"},!0,["sign","verify"]),e=JSON.stringify(await crypto.subtle.exportKey("jwk",r.privateKey)),i=await crypto.subtle.exportKey("jwk",r.publicKey);await n.DB.prepare("INSERT INTO settings (key,value) VALUES ('rc_priv',?) ON CONFLICT(key) DO NOTHING").bind(e).run(),await n.DB.prepare("INSERT INTO settings (key,value) VALUES ('rc_pub',?) ON CONFLICT(key) DO NOTHING").bind(i.x).run(),t=await l.setting(n,"rc_priv")}return{priv:JSON.parse(t),pub:await l.setting(n,"rc_pub")}}async function w(n){const t=Number(await l.setting(n,"rc_version","0"))||0,r=Math.max(Date.now(),t+1);return await l.setSetting(n,"rc_version",String(r)),r}async function tt(n,t){const{priv:r}=await j(t),e=Number(await l.setting(t,"rc_version","0"))||await w(t),i='"'+e+'"';if(n.headers.get("if-none-match")===i)return new Response(null,{status:304,headers:{etag:i,"cache-control":"public, max-age=300"}});const d=await l.getConfigs(t,!0),p=D({schemaVersion:1,configVersion:e,profiles:d.map(f=>({id:"c"+f.id,name:f.name,uri:f.uri}))}),s=await crypto.subtle.importKey("jwk",r,{name:"Ed25519"},!1,["sign"]),a=q(await crypto.subtle.sign({name:"Ed25519"},s,x.encode(p))),o=JSON.stringify({schemaVersion:1,configVersion:e,payload:p,signature:a});return o.length>262144?c({error:"too_large"},500):new Response(o,{headers:{"content-type":"application/json; charset=utf-8","cache-control":"public, max-age=300",etag:i}})}async function nt(n,t,r){const e=r.pathname,i=n.method;if(e==="/api/state"&&i==="GET"){const s=!!await l.setting(t,"password_hash");return c({hasPassword:s,authed:await I(n,t)})}if(e==="/api/setup"&&i==="POST"){if(await l.setting(t,"password_hash"))return c({error:"already_set"},400);const{password:s}=await m(n);if(!s||String(s).length<8)return c({error:"short"},400);await l.setSetting(t,"password_hash",await v(s));const a=await P(t);return c({ok:!0,token:a},200,{"set-cookie":H(a,R/1e3)})}if(e==="/api/login"&&i==="POST"){const{password:s}=await m(n),a=await l.setting(t,"password_hash");if(!a)return c({error:"not_setup"},400);if(!_(await v(s||""),a))return c({error:"bad_password"},401);const o=await P(t);return c({ok:!0,token:o},200,{"set-cookie":H(o,R/1e3)})}if(e==="/api/logout"&&i==="POST")return c({ok:!0},200,{"set-cookie":`${N}=; Path=/; Max-Age=0`});if(!await I(n,t))return c({error:"unauthorized"},401);if(e==="/api/users"&&i==="GET")return c({users:await l.getUsers(t)});if(e==="/api/users"&&i==="POST"){const s=await m(n),a=String(s.name||"").trim();return/^[A-Za-z0-9_.-]{2,32}$/.test(a)?await t.DB.prepare("SELECT id FROM users WHERE name = ?").bind(a).first()?c({error:"duplicate"},400):(await l.addUser(t,{name:a,limit_gb:Number(s.limit_gb)||0,days:Number(s.days)||0}),c({ok:!0})):c({error:"bad_name"},400)}const d=e.match(/^\/api\/users\/(\d+)(\/reset)?$/);if(d){const s=Number(d[1]);if(d[2]&&i==="POST")return await t.DB.prepare("UPDATE users SET used_bytes = 0 WHERE id = ?").bind(s).run(),c({ok:!0});if(i==="DELETE")return await t.DB.prepare("DELETE FROM users WHERE id = ?").bind(s).run(),c({ok:!0});if(i==="PATCH"){const a=await m(n),o=await l.getUser(t,s);if(!o)return c({error:"not_found"},404);const f=a.enabled===void 0?o.enabled:a.enabled?1:0,h=a.limit_gb===void 0?o.limit_bytes:Math.round(Number(a.limit_gb)*1024**3),b=a.days===void 0?o.expires_at:Number(a.days)>0?Date.now()+Number(a.days)*864e5:0;let g=o.name;if(a.name!==void 0){if(g=String(a.name).trim(),!/^[A-Za-z0-9_.-]{2,32}$/.test(g))return c({error:"bad_name"},400);if(await t.DB.prepare("SELECT id FROM users WHERE name = ? AND id != ?").bind(g,s).first())return c({error:"duplicate"},400)}return await t.DB.prepare("UPDATE users SET name = ?, enabled = ?, limit_bytes = ?, expires_at = ? WHERE id = ?").bind(g,f,h,b,s).run(),c({ok:!0})}}if(e==="/api/overview"&&i==="GET"){const s=await l.getUsers(t),a=Date.now();return c({total:s.length,active:s.filter(o=>!blocked({...o,expires_at:o.expires_at,limit_bytes:o.limit_bytes})&&o.enabled).length,used:s.reduce((o,f)=>o+(f.used_bytes||0),0),host:null})}if(e==="/api/password"&&i==="POST"){const{current:s,next:a}=await m(n),o=await l.setting(t,"password_hash");return _(await v(s||""),o)?!a||String(a).length<8?c({error:"short"},400):(await l.setSetting(t,"password_hash",await v(a)),c({ok:!0})):c({error:"bad_password"},401)}if(e==="/api/configs"&&i==="GET")return c({configs:await l.getConfigs(t)});if(e==="/api/configs"&&i==="POST"){const s=await m(n),a=String(s.name||"").trim().slice(0,60),o=String(s.uri||"").trim();if(!a)return c({error:"bad_name"},400);if(!/^(vless|vmess|trojan|ss|hysteria2?|tuic|wireguard):\/\//i.test(o))return c({error:"bad_uri"},400);if(o.length>4e3)return c({error:"uri_too_long"},400);const f=Number(s.sort)||Math.floor(Date.now()/1e3)%1e5;await t.DB.prepare("INSERT INTO configs (name,uri,enabled,sort,created_at) VALUES (?,?,1,?,?)").bind(a,o,f,Date.now()).run();const h=await w(t);return c({ok:!0,version:h})}const p=e.match(/^\/api\/configs\/(\d+)$/);if(p){const s=Number(p[1]);if(i==="PATCH"){const a=await m(n);if(a.uri!==void 0&&!/^(vless|vmess|trojan|ss|hysteria2?|tuic|wireguard):\/\//i.test(String(a.uri)))return c({error:"bad_uri"},400);const o=await t.DB.prepare("SELECT * FROM configs WHERE id = ?").bind(s).first();if(!o)return c({error:"not_found"},404);await t.DB.prepare("UPDATE configs SET name = ?, uri = ?, enabled = ?, sort = ? WHERE id = ?").bind(a.name!==void 0?String(a.name).slice(0,60):o.name,a.uri!==void 0?String(a.uri):o.uri,a.enabled!==void 0?a.enabled?1:0:o.enabled,a.sort!==void 0?Number(a.sort)||0:o.sort,s).run();const f=await w(t);return c({ok:!0,version:f})}if(i==="DELETE"){await t.DB.prepare("DELETE FROM configs WHERE id = ?").bind(s).run();const a=await w(t);return c({ok:!0,version:a})}}if(e==="/api/rc"&&i==="GET"){const{pub:s}=await j(t),a=Number(await l.setting(t,"rc_version","0"))||0,o=(await l.getConfigs(t,!0)).length;return c({url:new URL(n.url).origin+"/rc",publicKey:s,version:a,enabled:o})}if(e==="/api/rc/publish"&&i==="POST")return c({ok:!0,version:await w(t)});if(e==="/api/settings"&&i==="GET")return c({node_host:await l.setting(t,"node_host")||t.PROXY_HOST||"",fallback:t.PROXY_HOST||""});if(e==="/api/settings"&&i==="POST"){const s=await m(n);if(!new Set(["node_host"]).has(String(s.key)))return c({error:"forbidden_key"},400);const o=String(s.value||"").trim().replace(/^https?:\/\//,"").replace(/\/.*$/,"");return o&&!/^[a-z0-9.-]+(:\d+)?$/i.test(o)?c({error:"bad_host"},400):(await l.setSetting(t,String(s.key),o),c({ok:!0,value:o}))}return c({error:"not_found"},404)}const et=`<!doctype html>
<html lang="fa" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>\u067E\u0646\u0644 \u0645\u062F\u06CC\u0631\u06CC\u062A</title>
<style>
:root{--bg:#0b0f14;--card:#131a23;--inner:#0e141b;--line:#22303f;--txt:#e6edf3;--dim:#8b98a5;
--acc:#3b82f6;--ok:#22c55e;--bad:#ef4444;--warn:#f59e0b;--mint:#34f5c5}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--txt);font:14px/1.65 Tahoma,system-ui,sans-serif}
.wrap{max-width:1040px;margin:0 auto;padding:20px 16px 60px}
.top{display:flex;align-items:center;gap:12px;margin-bottom:18px}
.brand{font-weight:700;font-size:17px;letter-spacing:.3px}
.top .sp{flex:1}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:16px;margin-bottom:14px}
h2{font-size:14px;margin:0 0 12px;font-weight:600;color:var(--txt)}
input,select,textarea,button{font:inherit;border-radius:10px;border:1px solid var(--line);background:var(--inner);color:var(--txt);padding:9px 12px}
input:focus,textarea:focus{outline:none;border-color:var(--acc)}
textarea{width:100%;min-height:96px;resize:vertical;direction:ltr;text-align:left;font-family:monospace;font-size:12px}
button{background:var(--acc);border-color:var(--acc);cursor:pointer;font-weight:600;white-space:nowrap}
button:active{transform:translateY(1px)}
button.ghost{background:transparent;color:var(--txt)}
button.bad{background:var(--bad);border-color:var(--bad)}
button.ok{background:var(--ok);border-color:var(--ok);color:#04170a}
button.sm{padding:5px 9px;font-size:12px;border-radius:8px}
select.sm{padding:5px 8px;font-size:12px}
table{width:100%;border-collapse:collapse}
th,td{padding:9px 8px;border-bottom:1px solid var(--line);text-align:right;font-size:13px;vertical-align:middle}
th{color:var(--dim);font-weight:500;font-size:12px}
tr:last-child td{border-bottom:none}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
.grid{display:grid;gap:10px;grid-template-columns:repeat(auto-fit,minmax(150px,1fr))}
.pill{padding:2px 9px;border-radius:999px;font-size:11.5px;border:1px solid var(--line);white-space:nowrap}
.on{color:var(--ok);border-color:#1d4d2b;background:#0c1f13}
.off{color:var(--bad);border-color:#4d1d1d;background:#1f0c0c}
.muted{color:var(--dim);font-size:12px}
.err{color:var(--bad);font-size:12.5px;min-height:18px;margin:6px 0 0}
.okmsg{color:var(--ok);font-size:12.5px}
code,pre{background:var(--inner);border-radius:8px;font-family:monospace;font-size:12px}
code{padding:2px 6px}
pre{margin:0;padding:12px;overflow:auto;direction:ltr;text-align:left;border:1px solid var(--line)}
.tabs{display:flex;gap:6px;margin-bottom:14px;flex-wrap:wrap}
.tab{background:transparent;border:1px solid var(--line);color:var(--dim);padding:8px 14px;border-radius:10px;font-weight:500}
.tab.active{background:var(--inner);color:var(--mint);border-color:#1f6f5c}
.hidden{display:none!important}
.stat{background:var(--inner);border:1px solid var(--line);border-radius:12px;padding:10px 12px}
.stat b{display:block;font-size:18px;margin-top:2px}
.stat span{color:var(--dim);font-size:11px}
.bar{height:6px;border-radius:4px;background:#1b2530;overflow:hidden;margin-top:6px;min-width:90px}
.bar i{display:block;height:100%;background:var(--mint)}
.list{display:flex;flex-direction:column;gap:8px}
.item{border:1px solid var(--line);border-radius:12px;padding:10px 12px;background:var(--inner)}
.mono{font-family:monospace;direction:ltr;text-align:left;word-break:break-all;font-size:11.5px;color:var(--dim)}
dialog{border:1px solid var(--line);border-radius:16px;background:var(--card);color:var(--txt);padding:18px;max-width:420px;width:92%}
dialog::backdrop{background:#0009}
.toast{position:fixed;bottom:18px;left:50%;transform:translateX(-50%);background:#0f2a20;border:1px solid #1f6f5c;
color:#d7ffee;padding:10px 16px;border-radius:10px;font-size:13px;opacity:0;transition:opacity .2s;pointer-events:none;z-index:9}
.toast.show{opacity:1}
@media(max-width:640px){.hide-sm{display:none}}
</style>

<div class="wrap">
  <div class="top">
    <div class="brand">\u067E\u0646\u0644 \u0645\u062F\u06CC\u0631\u06CC\u062A</div>
    <div class="sp"></div>
    <span class="muted" id="host"></span>
    <button class="ghost sm hidden" id="logoutBtn" onclick="logout()">\u062E\u0631\u0648\u062C</button>
  </div>

  <section id="auth" class="card hidden">
    <div id="setupBox" class="hidden">
      <h2>\u0627\u0648\u0644\u06CC\u0646 \u0648\u0631\u0648\u062F</h2>
      <p class="muted">\u06CC\u06A9 \u0631\u0645\u0632 \u0642\u0648\u06CC \u0628\u0631\u0627\u06CC \u067E\u0646\u0644 \u0628\u0633\u0627\u0632 (\u062D\u062F\u0627\u0642\u0644 \u06F8 \u06A9\u0627\u0631\u0627\u06A9\u062A\u0631).</p>
      <div class="row"><input id="spass" type="password" placeholder="\u0631\u0645\u0632 \u062C\u062F\u06CC\u062F" style="flex:1;min-width:180px">
      <button onclick="setup()">\u0633\u0627\u062E\u062A \u0631\u0645\u0632</button></div>
    </div>
    <div id="loginBox" class="hidden">
      <h2>\u0648\u0631\u0648\u062F</h2>
      <div class="row"><input id="lpass" type="password" placeholder="\u0631\u0645\u0632 \u0639\u0628\u0648\u0631" style="flex:1;min-width:180px">
      <button onclick="login()">\u0648\u0631\u0648\u062F</button></div>
    </div>
    <p class="err" id="authMsg"></p>
  </section>

  <nav id="tabs" class="tabs hidden">
    <button class="tab active" data-tab="users" onclick="tab('users')">\u06A9\u0627\u0631\u0628\u0631\u0627\u0646</button>
    <button class="tab" data-tab="configs" onclick="tab('configs')">\u06A9\u0627\u0646\u0641\u06CC\u06AF\u200C\u0647\u0627\u06CC \u0627\u067E</button>
    <button class="tab" data-tab="app" onclick="tab('app')">\u0627\u062A\u0635\u0627\u0644 \u0627\u067E</button>
    <button class="tab" data-tab="settings" onclick="tab('settings')">\u062A\u0646\u0638\u06CC\u0645\u0627\u062A</button>
  </nav>

  <main id="main" class="hidden">
    <!-- \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 users \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 -->
    <section id="pane-users">
      <div class="grid" style="margin-bottom:14px">
        <div class="stat"><span>\u06A9\u0627\u0631\u0628\u0631\u0627\u0646</span><b id="s-users">0</b></div>
        <div class="stat"><span>\u0641\u0639\u0627\u0644</span><b id="s-active">0</b></div>
        <div class="stat"><span>\u0645\u0635\u0631\u0641 \u06A9\u0644</span><b id="s-used">0</b></div>
        <div class="stat"><span>\u0622\u062F\u0631\u0633 \u0646\u0648\u062F</span><b id="s-node" style="font-size:12px;word-break:break-all">\u2014</b></div>
      </div>

      <div class="card">
        <h2>\u0627\u0641\u0632\u0648\u062F\u0646 \u06A9\u0627\u0631\u0628\u0631</h2>
        <div class="row">
          <input id="u-name" placeholder="\u0646\u0627\u0645 \u06A9\u0627\u0631\u0628\u0631\u06CC" style="flex:1;min-width:140px">
          <input id="u-gb" type="number" placeholder="\u062D\u062C\u0645 (GB\u060C \u06F0 = \u0646\u0627\u0645\u062D\u062F\u0648\u062F)" style="width:190px">
          <input id="u-days" type="number" placeholder="\u0631\u0648\u0632 \u0627\u0639\u062A\u0628\u0627\u0631 (\u06F0 = \u0628\u06CC\u200C\u067E\u0627\u06CC\u0627\u0646)" style="width:200px">
          <button onclick="addUser()">\u0627\u0641\u0632\u0648\u062F\u0646</button>
        </div>
        <p class="err" id="u-msg"></p>
      </div>

      <div class="card">
        <div class="row" style="justify-content:space-between;margin-bottom:8px">
          <h2 style="margin:0">\u06A9\u0627\u0631\u0628\u0631\u0627\u0646</h2>
          <button class="ghost sm" onclick="loadUsers()">\u062A\u0627\u0632\u0647\u200C\u0633\u0627\u0632\u06CC</button>
        </div>
        <div style="overflow-x:auto">
          <table>
            <thead><tr>
              <th>\u0646\u0627\u0645</th><th>\u0648\u0636\u0639\u06CC\u062A</th><th>\u0645\u0635\u0631\u0641</th><th class="hide-sm">\u0627\u0646\u0642\u0636\u0627</th><th>\u0639\u0645\u0644\u06CC\u0627\u062A</th>
            </tr></thead>
            <tbody id="rows"></tbody>
          </table>
        </div>
        <p class="muted" id="u-empty" style="margin:10px 0 0"></p>
      </div>
    </section>

    <!-- \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 configs \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 -->
    <section id="pane-configs" class="hidden">
      <div class="card">
        <h2>\u0627\u0641\u0632\u0648\u062F\u0646 \u06A9\u0627\u0646\u0641\u06CC\u06AF</h2>
        <p class="muted" style="margin-top:-6px">\u0647\u0631 \u06A9\u0627\u0646\u0641\u06CC\u06AF\u06CC \u06A9\u0647 \u0627\u06CC\u0646\u062C\u0627 \u0627\u0636\u0627\u0641\u0647 \u06A9\u0646\u06CC\u060C \u062F\u0631 \u0635\u0641\u062D\u0647\u200C\u06CC \u0627\u0635\u0644\u06CC \u0647\u0645\u0647\u200C\u06CC \u06A9\u0627\u0631\u0628\u0631\u0627\u0646 \u0627\u067E \u0638\u0627\u0647\u0631 \u0645\u06CC\u200C\u0634\u0648\u062F.</p>
        <div class="row">
          <input id="c-name" placeholder="\u0646\u0627\u0645 (\u0645\u062B\u0644\u0627\u064B \u0641\u0631\u0627\u0646\u06A9\u0641\u0648\u0631\u062A)" style="flex:0 0 190px">
          <input id="c-uri" placeholder="vless://\u2026 \u06CC\u0627 trojan://\u2026" style="flex:1;min-width:240px;direction:ltr;text-align:left">
          <button onclick="addConfig()">\u0627\u0641\u0632\u0648\u062F\u0646</button>
        </div>
        <p class="err" id="c-msg"></p>
        <details style="margin-top:8px">
          <summary class="muted" style="cursor:pointer">\u0627\u0641\u0632\u0648\u062F\u0646 \u06AF\u0631\u0648\u0647\u06CC (\u0647\u0631 \u062E\u0637 \u06CC\u06A9 \u0644\u06CC\u0646\u06A9)</summary>
          <textarea id="c-bulk" placeholder="vless://...
vless://...
trojan://..."></textarea>
          <div class="row" style="margin-top:8px"><button class="ghost" onclick="addBulk()">\u0627\u0641\u0632\u0648\u062F\u0646 \u0647\u0645\u0647</button>
          <span class="muted">\u0646\u0627\u0645 \u0647\u0631 \u06A9\u0627\u0646\u0641\u06CC\u06AF \u0627\u0632 \u062A\u06A9\u0647\u200C\u06CC \u0622\u062E\u0631 \u0644\u06CC\u0646\u06A9 (#name) \u0628\u0631\u062F\u0627\u0634\u062A\u0647 \u0645\u06CC\u200C\u0634\u0648\u062F.</span></div>
        </details>
      </div>

      <div class="card">
        <div class="row" style="justify-content:space-between;margin-bottom:8px">
          <h2 style="margin:0">\u06A9\u0627\u0646\u0641\u06CC\u06AF\u200C\u0647\u0627</h2>
          <div class="row"><button class="ghost sm" onclick="loadConfigs()">\u062A\u0627\u0632\u0647\u200C\u0633\u0627\u0632\u06CC</button>
          <button class="ghost sm" onclick="publish()">\u0627\u0646\u062A\u0634\u0627\u0631 \u0645\u062C\u062F\u062F</button></div>
        </div>
        <div class="list" id="cfg-list"></div>
        <p class="muted" id="c-empty" style="margin:10px 0 0"></p>
      </div>
    </section>

    <!-- \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 app \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 -->
    <section id="pane-app" class="hidden">
      <div class="card">
        <h2>\u0631\u0628\u0637 \u062F\u0627\u062F\u0646 \u0627\u067E</h2>
        <p class="muted">\u0627\u06CC\u0646 \u062F\u0648 \u062E\u0637 \u0631\u0627 \u062F\u0631 <code>local.properties</code> \u067E\u0631\u0648\u0698\u0647 \u0628\u06AF\u0630\u0627\u0631 \u0648 \u0627\u067E \u0631\u0627 \u06CC\u06A9\u200C\u0628\u0627\u0631 \u0628\u06CC\u0644\u062F \u06A9\u0646.</p>
        <pre id="rc-lines"></pre>
        <div class="row" style="margin-top:10px">
          <button class="sm" onclick="copyEl('rc-lines')">\u06A9\u067E\u06CC</button>
          <button class="ghost sm" onclick="copyEl('rc-url')">\u06A9\u067E\u06CC \u0622\u062F\u0631\u0633</button>
        </div>
        <div class="grid" style="margin-top:14px">
          <div class="stat"><span>\u0622\u062F\u0631\u0633 RC</span><b id="rc-url" style="font-size:11.5px;word-break:break-all">\u2014</b></div>
          <div class="stat"><span>\u0646\u0633\u062E\u0647\u200C\u06CC \u0641\u0639\u0627\u0644</span><b id="rc-version">0</b></div>
          <div class="stat"><span>\u06A9\u0627\u0646\u0641\u06CC\u06AF \u0641\u0639\u0627\u0644</span><b id="rc-count">0</b></div>
        </div>
        <p class="muted" style="margin-top:12px">\u06A9\u0644\u06CC\u062F \u0639\u0645\u0648\u0645\u06CC \u062F\u0627\u062E\u0644 \u062E\u0648\u062F \u0627\u067E \u06A9\u0627\u0645\u067E\u0627\u06CC\u0644 \u0645\u06CC\u200C\u0634\u0648\u062F. \u0627\u06AF\u0631 \u06A9\u0633\u06CC \u0633\u0631\u0648\u0631 \u0631\u0627 \u0639\u0648\u0636 \u06A9\u0646\u062F\u060C \u0627\u0645\u0636\u0627 \u0646\u0645\u06CC\u200C\u062E\u0648\u0627\u0646\u062F \u0648 \u06A9\u0627\u0646\u0641\u06CC\u06AF \u0631\u062F \u0645\u06CC\u200C\u0634\u0648\u062F.</p>
      </div>

      <div class="card">
        <h2>\u06A9\u0644\u06CC\u062F \u0639\u0645\u0648\u0645\u06CC \u0627\u0645\u0636\u0627</h2>
        <pre id="rc-key"></pre>
        <div class="row" style="margin-top:10px"><button class="sm" onclick="copyEl('rc-key')">\u06A9\u067E\u06CC</button></div>
      </div>
    </section>

    <!-- \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 settings \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 -->
    <section id="pane-settings" class="hidden">
      <div class="card">
        <h2>\u0622\u062F\u0631\u0633 \u0646\u0648\u062F</h2>
        <p class="muted">\u06A9\u0627\u0646\u0641\u06CC\u06AF\u200C\u0647\u0627\u06CC\u06CC \u06A9\u0647 \u0628\u0647 \u06A9\u0627\u0631\u0628\u0631\u0627\u0646 \u062F\u0627\u062F\u0647 \u0645\u06CC\u200C\u0634\u0648\u062F \u0628\u0647 \u0627\u06CC\u0646 \u062F\u0627\u0645\u0646\u0647 \u0648\u0635\u0644 \u0645\u06CC\u200C\u0634\u0648\u0646\u062F.</p>
        <div class="row"><input id="node-host" placeholder="node.example.com" style="flex:1;min-width:220px;direction:ltr;text-align:left">
        <button onclick="saveNode()">\u0630\u062E\u06CC\u0631\u0647</button></div>
        <p class="err" id="n-msg"></p>
      </div>

      <div class="card">
        <h2>\u062A\u063A\u06CC\u06CC\u0631 \u0631\u0645\u0632 \u067E\u0646\u0644</h2>
        <div class="row">
          <input id="pw-cur" type="password" placeholder="\u0631\u0645\u0632 \u0641\u0639\u0644\u06CC" style="flex:1;min-width:150px">
          <input id="pw-new" type="password" placeholder="\u0631\u0645\u0632 \u062C\u062F\u06CC\u062F (\u062D\u062F\u0627\u0642\u0644 \u06F8)" style="flex:1;min-width:150px">
          <button onclick="changePassword()">\u062A\u063A\u06CC\u06CC\u0631</button>
        </div>
        <p class="err" id="pw-msg"></p>
      </div>
    </section>
  </main>
</div>

<dialog id="editDlg">
  <h2>\u0648\u06CC\u0631\u0627\u06CC\u0634 \u06A9\u0627\u0631\u0628\u0631</h2>
  <div class="grid">
    <label class="muted">\u0646\u0627\u0645<input id="e-name" style="width:100%;margin-top:4px"></label>
    <label class="muted">\u062D\u062C\u0645 (GB)<input id="e-gb" type="number" style="width:100%;margin-top:4px"></label>
    <label class="muted">\u0631\u0648\u0632 \u0627\u0639\u062A\u0628\u0627\u0631<input id="e-days" type="number" style="width:100%;margin-top:4px"></label>
  </div>
  <p class="err" id="e-msg"></p>
  <div class="row" style="justify-content:flex-end;margin-top:10px">
    <button class="ghost" onclick="document.getElementById('editDlg').close()">\u0627\u0646\u0635\u0631\u0627\u0641</button>
    <button onclick="saveEdit()">\u0630\u062E\u06CC\u0631\u0647</button>
  </div>
</dialog>

<div class="toast" id="toast"></div>

<script>
var users = [], configs = [], editing = null, rcInfo = null;

function el(id) { return document.getElementById(id); }
function esc(s) { return String(s).replace(/[&<>"']/g, function (c) { return ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]; }); }
function gb(n) { return n ? (n / 1073741824).toFixed(2) + ' GB' : '\u0646\u0627\u0645\u062D\u062F\u0648\u062F'; }
function faDate(ts) { return ts ? new Date(ts).toLocaleDateString('fa-IR') : '\u0628\u06CC\u200C\u067E\u0627\u06CC\u0627\u0646'; }
function toast(m) { var t = el('toast'); t.textContent = m; t.className = 'toast show'; setTimeout(function () { t.className = 'toast'; }, 2200); }

async function api(path, opt) {
  var res = await fetch(path, Object.assign({ credentials: 'include', headers: { 'content-type': 'application/json' } }, opt || {}));
  var data = {};
  try { data = await res.json(); } catch (e) {}
  return { ok: res.ok, status: res.status, data: data };
}

async function boot() {
  el('host').textContent = location.host;
  var st = await api('/api/state');
  if (!st.data.hasPassword) { el('auth').classList.remove('hidden'); el('setupBox').classList.remove('hidden'); return; }
  if (st.data.authed) { showApp(); return; }
  el('auth').classList.remove('hidden'); el('loginBox').classList.remove('hidden');
}

async function setup() {
  var p = el('spass').value;
  if (p.length < 8) { el('authMsg').textContent = '\u0631\u0645\u0632 \u062D\u062F\u0627\u0642\u0644 \u06F8 \u06A9\u0627\u0631\u0627\u06A9\u062A\u0631'; return; }
  var r = await api('/api/setup', { method: 'POST', body: JSON.stringify({ password: p }) });
  if (r.ok) showApp(); else el('authMsg').textContent = '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}

async function login() {
  var p = el('lpass').value;
  var r = await api('/api/login', { method: 'POST', body: JSON.stringify({ password: p }) });
  if (r.ok) showApp(); else el('authMsg').textContent = '\u0631\u0645\u0632 \u0627\u0634\u062A\u0628\u0627\u0647';
}

async function logout() {
  await api('/api/logout', { method: 'POST' });
  location.reload();
}

function showApp() {
  el('auth').classList.add('hidden');
  el('tabs').classList.remove('hidden');
  el('main').classList.remove('hidden');
  el('logoutBtn').classList.remove('hidden');
  loadAll();
}

function tab(name) {
  var panes = ['users', 'configs', 'app', 'settings'];
  panes.forEach(function (p) {
    el('pane-' + p).classList.toggle('hidden', p !== name);
  });
  var btns = document.querySelectorAll('.tab');
  for (var i = 0; i < btns.length; i++) btns[i].classList.toggle('active', btns[i].getAttribute('data-tab') === name);
  if (name === 'app') loadRc();
  if (name === 'settings') loadSettings();
}

async function loadAll() { await Promise.all([loadUsers(), loadConfigs(), loadSettings()]); }

/* \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 users \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 */
async function loadUsers() {
  var r = await api('/api/users');
  users = r.data.users || [];
  var now = Date.now(), used = 0, active = 0;
  var html = '';
  users.forEach(function (u) {
    used += u.used_bytes || 0;
    var blocked = !u.enabled || (u.expires_at && u.expires_at < now) || (u.limit_bytes > 0 && u.used_bytes >= u.limit_bytes);
    if (!blocked) active++;
    var pct = u.limit_bytes > 0 ? Math.min(100, (u.used_bytes / u.limit_bytes) * 100) : 0;
    html += '<tr>'
      + '<td>' + esc(u.name) + '<div class="mono">' + esc(String(u.uuid).slice(0, 13)) + '\u2026</div></td>'
      + '<td><span class="pill ' + (blocked ? 'off' : 'on') + '">' + (blocked ? '\u063A\u06CC\u0631\u0641\u0639\u0627\u0644' : '\u0641\u0639\u0627\u0644') + '</span></td>'
      + '<td>' + gb(u.used_bytes) + ' / ' + gb(u.limit_bytes)
        + (u.limit_bytes > 0 ? '<div class="bar"><i style="width:' + pct.toFixed(0) + '%"></i></div>' : '') + '</td>'
      + '<td class="hide-sm">' + faDate(u.expires_at) + '</td>'
      + '<td><div class="row" style="gap:4px">'
        + '<button class="ghost sm" onclick="editUser(' + u.id + ')">\u0648\u06CC\u0631\u0627\u06CC\u0634</button>'
        + '<button class="ghost sm" onclick="toggleUser(' + u.id + ',' + (!u.enabled) + ')">' + (u.enabled ? '\u062E\u0627\u0645\u0648\u0634' : '\u0631\u0648\u0634\u0646') + '</button>'
        + '<button class="ghost sm" onclick="resetUser(' + u.id + ')">\u0631\u06CC\u0633\u062A</button>'
        + '<button class="ghost sm" onclick="copySub(' + u.id + ')">\u0633\u0627\u0628</button>'
        + '<button class="bad sm" onclick="delUser(' + u.id + ')">\u062D\u0630\u0641</button>'
      + '</div></td></tr>';
  });
  el('rows').innerHTML = html;
  el('u-empty').textContent = users.length ? '' : '\u0647\u0646\u0648\u0632 \u06A9\u0627\u0631\u0628\u0631\u06CC \u0646\u0633\u0627\u062E\u062A\u0647\u200C\u0627\u06CC.';
  el('s-users').textContent = users.length;
  el('s-active').textContent = active;
  el('s-used').textContent = gb(used);
}

async function addUser() {
  var name = el('u-name').value.trim();
  var limit = Number(el('u-gb').value || 0);
  var days = Number(el('u-days').value || 0);
  if (!/^[A-Za-z0-9_.-]{2,32}$/.test(name)) { el('u-msg').textContent = '\u0646\u0627\u0645: \u06F2 \u062A\u0627 \u06F3\u06F2 \u06A9\u0627\u0631\u0627\u06A9\u062A\u0631\u060C \u0641\u0642\u0637 \u062D\u0631\u0648\u0641 \u0627\u0646\u06AF\u0644\u06CC\u0633\u06CC/\u0639\u062F\u062F/._-'; return; }
  var r = await api('/api/users', { method: 'POST', body: JSON.stringify({ name: name, limit_gb: limit, days: days }) });
  if (r.ok) { el('u-name').value = ''; el('u-gb').value = ''; el('u-days').value = ''; el('u-msg').textContent = ''; toast('\u06A9\u0627\u0631\u0628\u0631 \u0633\u0627\u062E\u062A\u0647 \u0634\u062F'); loadUsers(); }
  else el('u-msg').textContent = r.data.error === 'duplicate' ? '\u0627\u06CC\u0646 \u0646\u0627\u0645 \u0642\u0628\u0644\u0627\u064B \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u0634\u062F\u0647' : '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}

function editUser(id) {
  var u = users.filter(function (x) { return x.id === id; })[0];
  if (!u) return;
  editing = id;
  el('e-name').value = u.name;
  el('e-gb').value = u.limit_bytes ? (u.limit_bytes / 1073741824).toFixed(2) : 0;
  el('e-days').value = u.expires_at ? Math.max(0, Math.round((u.expires_at - Date.now()) / 86400000)) : 0;
  el('e-msg').textContent = '';
  el('editDlg').showModal();
}

async function saveEdit() {
  var body = {
    name: el('e-name').value.trim(),
    limit_gb: Number(el('e-gb').value || 0),
    days: Number(el('e-days').value || 0)
  };
  var r = await api('/api/users/' + editing, { method: 'PATCH', body: JSON.stringify(body) });
  if (r.ok) { el('editDlg').close(); toast('\u0630\u062E\u06CC\u0631\u0647 \u0634\u062F'); loadUsers(); }
  else el('e-msg').textContent = '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}

async function toggleUser(id, on) { await api('/api/users/' + id, { method: 'PATCH', body: JSON.stringify({ enabled: on }) }); loadUsers(); }
async function resetUser(id) { await api('/api/users/' + id + '/reset', { method: 'POST' }); toast('\u0645\u0635\u0631\u0641 \u0635\u0641\u0631 \u0634\u062F'); loadUsers(); }
async function delUser(id) {
  if (!confirm('\u0627\u06CC\u0646 \u06A9\u0627\u0631\u0628\u0631 \u062D\u0630\u0641 \u0634\u0648\u062F\u061F')) return;
  await api('/api/users/' + id, { method: 'DELETE' });
  toast('\u062D\u0630\u0641 \u0634\u062F'); loadUsers();
}
function copySub(id) {
  var u = users.filter(function (x) { return x.id === id; })[0];
  if (u) copyText(location.origin + '/sub/' + u.sub_token, '\u0644\u06CC\u0646\u06A9 \u0633\u0627\u0628 \u06A9\u067E\u06CC \u0634\u062F');
}

/* \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 configs \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 */
async function loadConfigs() {
  var r = await api('/api/configs');
  configs = r.data.configs || [];
  var html = '';
  configs.forEach(function (c) {
    html += '<div class="item">'
      + '<div class="row" style="justify-content:space-between">'
        + '<div><b>' + esc(c.name) + '</b> <span class="pill ' + (c.enabled ? 'on' : 'off') + '">' + (c.enabled ? '\u0641\u0639\u0627\u0644' : '\u062E\u0627\u0645\u0648\u0634') + '</span></div>'
        + '<div class="row" style="gap:4px">'
          + '<button class="ghost sm" onclick="moveCfg(' + c.id + ',-1)">\u2191</button>'
          + '<button class="ghost sm" onclick="moveCfg(' + c.id + ',1)">\u2193</button>'
          + '<button class="ghost sm" onclick="editCfg(' + c.id + ')">\u0648\u06CC\u0631\u0627\u06CC\u0634</button>'
          + '<button class="ghost sm" onclick="toggleCfg(' + c.id + ',' + (!c.enabled) + ')">' + (c.enabled ? '\u062E\u0627\u0645\u0648\u0634' : '\u0631\u0648\u0634\u0646') + '</button>'
          + '<button class="bad sm" onclick="delCfg(' + c.id + ')">\u062D\u0630\u0641</button>'
        + '</div>'
      + '</div>'
      + '<div class="mono">' + esc(c.uri) + '</div>'
    + '</div>';
  });
  el('cfg-list').innerHTML = html;
  el('c-empty').textContent = configs.length ? '' : '\u0647\u0646\u0648\u0632 \u06A9\u0627\u0646\u0641\u06CC\u06AF\u06CC \u0627\u0636\u0627\u0641\u0647 \u0646\u06A9\u0631\u062F\u0647\u200C\u0627\u06CC.';
  el('rc-count').textContent = configs.filter(function (c) { return c.enabled; }).length;
}

async function addConfig() {
  var name = el('c-name').value.trim();
  var uri = el('c-uri').value.trim();
  if (!name) { el('c-msg').textContent = '\u0646\u0627\u0645 \u0631\u0627 \u0628\u0646\u0648\u06CC\u0633'; return; }
  if (!/^(vless|vmess|trojan|ss|hysteria2?|tuic|wireguard):\\/\\//i.test(uri)) { el('c-msg').textContent = '\u0644\u06CC\u0646\u06A9 \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A'; return; }
  var r = await api('/api/configs', { method: 'POST', body: JSON.stringify({ name: name, uri: uri }) });
  if (r.ok) { el('c-name').value = ''; el('c-uri').value = ''; el('c-msg').textContent = ''; toast('\u0627\u0636\u0627\u0641\u0647 \u0634\u062F'); loadConfigs(); }
  else el('c-msg').textContent = '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}

async function addBulk() {
  var lines = (el('c-bulk').value || '').split(/\\s+/).filter(function (x) { return /:\\/\\//.test(x); });
  var ok = 0;
  for (var i = 0; i < lines.length; i++) {
    var uri = lines[i], name = '\u06A9\u0627\u0646\u0641\u06CC\u06AF ' + (configs.length + ok + 1);
    var hash = uri.split('#')[1];
    if (hash) { try { name = decodeURIComponent(hash); } catch (e) { name = hash; } }
    var r = await api('/api/configs', { method: 'POST', body: JSON.stringify({ name: name, uri: uri }) });
    if (r.ok) ok++;
  }
  el('c-bulk').value = '';
  toast(ok + ' \u06A9\u0627\u0646\u0641\u06CC\u06AF \u0627\u0636\u0627\u0641\u0647 \u0634\u062F');
  loadConfigs();
}

async function toggleCfg(id, on) { await api('/api/configs/' + id, { method: 'PATCH', body: JSON.stringify({ enabled: on }) }); loadConfigs(); }
async function delCfg(id) {
  if (!confirm('\u062D\u0630\u0641 \u0634\u0648\u062F\u061F')) return;
  await api('/api/configs/' + id, { method: 'DELETE' });
  toast('\u062D\u0630\u0641 \u0634\u062F'); loadConfigs();
}
async function editCfg(id) {
  var c = configs.filter(function (x) { return x.id === id; })[0];
  if (!c) return;
  var name = prompt('\u0646\u0627\u0645 \u06A9\u0627\u0646\u0641\u06CC\u06AF', c.name);
  if (name === null) return;
  var uri = prompt('\u0644\u06CC\u0646\u06A9 \u06A9\u0627\u0646\u0641\u06CC\u06AF', c.uri);
  if (uri === null) return;
  var r = await api('/api/configs/' + id, { method: 'PATCH', body: JSON.stringify({ name: name, uri: uri }) });
  if (!r.ok) toast('\u062E\u0637\u0627: ' + (r.data.error || r.status));
  loadConfigs();
}
async function moveCfg(id, dir) {
  var i = configs.findIndex(function (x) { return x.id === id; });
  var j = i + dir;
  if (i < 0 || j < 0 || j >= configs.length) return;
  await api('/api/configs/' + configs[i].id, { method: 'PATCH', body: JSON.stringify({ sort: configs[j].sort }) });
  await api('/api/configs/' + configs[j].id, { method: 'PATCH', body: JSON.stringify({ sort: configs[i].sort }) });
  loadConfigs();
}

async function publish() { await api('/api/rc/publish', { method: 'POST' }); toast('\u0646\u0633\u062E\u0647 \u0628\u0627\u0644\u0627 \u0631\u0641\u062A'); loadRc(); }

/* \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 app + settings \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 */
async function loadRc() {
  var r = await api('/api/rc');
  rcInfo = r.data || {};
  var url = rcInfo.url || '';
  el('rc-url').textContent = url;
  el('rc-key').textContent = rcInfo.publicKey || '';
  el('rc-version').textContent = rcInfo.version || 0;
  el('rc-count').textContent = rcInfo.enabled || 0;
  el('rc-lines').textContent = 'mohammad.remoteConfigUrl=' + url + String.fromCharCode(10)
    + 'mohammad.remoteConfigKey=' + (rcInfo.publicKey || '');
}

async function loadSettings() {
  var r = await api('/api/settings');
  el('node-host').value = (r.data && r.data.node_host) || '';
  el('s-node').textContent = (r.data && r.data.node_host) || '\u2014';
}

async function saveNode() {
  var v = el('node-host').value.trim();
  var r = await api('/api/settings', { method: 'POST', body: JSON.stringify({ key: 'node_host', value: v }) });
  if (r.ok) { toast('\u0630\u062E\u06CC\u0631\u0647 \u0634\u062F'); el('n-msg').textContent = ''; loadSettings(); }
  else el('n-msg').textContent = '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}

async function changePassword() {
  var cur = el('pw-cur').value, next = el('pw-new').value;
  if (next.length < 8) { el('pw-msg').textContent = '\u0631\u0645\u0632 \u062C\u062F\u06CC\u062F \u062D\u062F\u0627\u0642\u0644 \u06F8 \u06A9\u0627\u0631\u0627\u06A9\u062A\u0631'; return; }
  var r = await api('/api/password', { method: 'POST', body: JSON.stringify({ current: cur, next: next }) });
  if (r.ok) { el('pw-cur').value = ''; el('pw-new').value = ''; el('pw-msg').textContent = ''; toast('\u0631\u0645\u0632 \u0639\u0648\u0636 \u0634\u062F'); }
  else el('pw-msg').textContent = r.status === 401 ? '\u0631\u0645\u0632 \u0641\u0639\u0644\u06CC \u0627\u0634\u062A\u0628\u0627\u0647 \u0627\u0633\u062A' : '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}

/* \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 helpers \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 */
function copyText(text, msg) {
  var done = function () { toast(msg || '\u06A9\u067E\u06CC \u0634\u062F'); };
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(done, function () { fallbackCopy(text); done(); });
  } else { fallbackCopy(text); done(); }
}
function fallbackCopy(text) {
  var ta = document.createElement('textarea');
  ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
  document.body.appendChild(ta); ta.select();
  try { document.execCommand('copy'); } catch (e) {}
  document.body.removeChild(ta);
}
function copyEl(id) { copyText(el(id).textContent, '\u06A9\u067E\u06CC \u0634\u062F'); }

boot();
<\/script>
</html>
`;var ot={async fetch(n,t,r){try{return await at(n,t,r)}catch(e){return c({error:String(e&&e.stack||e).slice(0,800)},500)}}};async function at(n,t,r){const e=new URL(n.url),i=e.pathname;return await K(t),i==="/"||i===""?new Response(null,{status:302,headers:{location:"/panel"}}):i==="/favicon.ico"?new Response(null,{status:204}):i.startsWith("/sub/")?Q(n,t,i.slice(5)):i==="/rc"?tt(n,t):i==="/panel"||i==="/panel/"?J(et):i.startsWith("/api/")?nt(n,t,e):i==="/health"?c({ok:!0}):new Response("Not Found",{status:404})}export{ot as default};
