import"cloudflare:sockets";var on=["base64","plain","clash","singbox","json"],$=new TextEncoder;function ln(t){let n=2166136261,i=$.encode(String(t));for(let e=0;e<i.length;e++)n^=i[e],n=Math.imul(n,16777619);return n>>>0}function D(t){let n=[],i=new Set;for(let e of String(t||"").split(/\r?\n/)){let a=e.split("#")[0].trim();!a||i.has(a)||(i.add(a),n.push(a))}return n}function q(t,n){let i=D(t);return i.length?i[ln(n)%i.length]:null}function U(t){let n=String(t?.connection_type||"both").toLowerCase();return n==="vless"?["vless"]:n==="trojan"?["trojan"]:["vless","trojan"]}var z={security:"tls",fp:"chrome",alpn:"http/1.1",type:"ws",path:"/ws?ed=2048"};function dn(t,n,i){let e=i||n,a=new URLSearchParams({encryption:"none",...z,sni:n,host:n});return`vless://${t.uuid}@${e}:443?${a.toString()}#${encodeURIComponent(t.name)}`}function cn(t,n,i){let e=i||n,a=new URLSearchParams({...z,sni:n,host:n});return`trojan://${t.trojan_password}@${e}:443?${a.toString()}#${encodeURIComponent(t.name)}-trojan`}function R(t,n,i){let e=[];for(let a of U(t))e.push(a==="vless"?dn(t,n,i):cn(t,n,i));return e}function F(t){let n="",i=$.encode(t);for(let e=0;e<i.length;e++)n+=String.fromCharCode(i[e]);return btoa(n)}function y(t){return JSON.stringify(String(t??""))}function pn(t,n,i,e,a){let d=[`  - name: ${y(a)}`,`    type: ${e}`,`    server: ${y(i||n)}`,"    port: 443"];return e==="vless"?d.push(`    uuid: ${y(t.uuid)}`):d.push(`    password: ${y(t.trojan_password)}`),d.push("    network: ws","    tls: true",`    servername: ${y(n)}`,"    udp: true","    skip-cert-verify: false","    ws-opts:",'      path: "/ws?ed=2048"',"      headers:",`        Host: ${y(n)}`),d.join(`
`)}function un(t,n,i){let e=U(t),a=e.map(f=>f==="vless"?t.name:`${t.name}-trojan`);return["port: 7890","socks-port: 7891","allow-lan: false","mode: rule","log-level: info","proxies:",e.map((f,b)=>pn(t,n,i,f,a[b])).join(`
`),"proxy-groups:","  - name: PROXY","    type: select",`    proxies: [${a.map(y).join(", ")}]`,"rules:","  - MATCH,PROXY",""].join(`
`)}function fn(t,n,i,e,a){let d={type:e,tag:a,server:i||n,server_port:443,tls:{enabled:!0,server_name:n},transport:{type:"ws",path:"/ws?ed=2048",headers:{Host:n}}};return e==="vless"?{...d,uuid:t.uuid}:{...d,password:t.trojan_password}}function bn(t,n,i){let e=U(t),a=e.map(d=>d==="vless"?t.name:`${t.name}-trojan`);return{outbounds:e.map((d,f)=>fn(t,n,i,d,a[f]))}}function gn(t,n,i){return{version:1,name:t.name,host:n,address:i||n,proxies:R(t,n,i).map(e=>({uri:e}))}}function J(t){return t==="clash"?"text/yaml; charset=utf-8":t==="singbox"||t==="json"?"application/json; charset=utf-8":"text/plain; charset=utf-8"}function G(t,n,i,e){let a=on.includes(t)?t:"base64";return a==="plain"?R(n,i,e).join(`
`):a==="clash"?un(n,i,e):a==="singbox"?JSON.stringify(bn(n,i,e),null,2):a==="json"?JSON.stringify(gn(n,i,e),null,2):F(R(n,i,e).join(`
`))}function W(t){return"base64:"+F(String(t||""))}var mn={"content-type":"application/json; charset=utf-8"},k=new TextEncoder,Bn=new TextDecoder,l=(t,n=200,i={})=>new Response(JSON.stringify(t),{status:n,headers:{...mn,"cache-control":"no-store",...i}}),hn=(t,n=200)=>new Response(t,{status:n,headers:{"content-type":"text/html; charset=utf-8","cache-control":"no-store"}}),j=t=>[...new Uint8Array(t)].map(n=>n.toString(16).padStart(2,"0")).join(""),N=async t=>j(await crypto.subtle.digest("SHA-256",k.encode(String(t)))),M=(t=16)=>j(crypto.getRandomValues(new Uint8Array(t)));function vn(){let t=crypto.getRandomValues(new Uint8Array(16));t[6]=t[6]&15|64,t[8]=t[8]&63|128;let n=[...t].map(i=>i.toString(16).padStart(2,"0")).join("");return`${n.slice(0,8)}-${n.slice(8,12)}-${n.slice(12,16)}-${n.slice(16,20)}-${n.slice(20)}`}async function Z(t,n){let i=await crypto.subtle.importKey("raw",k.encode(t),{name:"HMAC",hash:"SHA-256"},!1,["sign"]);return j(await crypto.subtle.sign("HMAC",i,k.encode(n)))}function P(t,n){if(t=String(t),n=String(n),t.length!==n.length)return!1;let i=0;for(let e=0;e<t.length;e++)i|=t.charCodeAt(e)^n.charCodeAt(e);return i===0}var xn=[1116352408,1899447441,3049323471,3921009573,961987163,1508970993,2453635748,2870763221,3624381080,310598401,607225278,1426881987,1925078388,2162078206,2614888103,3248222580,3835390401,4022224774,264347078,604807628,770255983,1249150122,1555081692,1996064986,2554220882,2821834349,2952996808,3210313671,3336571891,3584528711,113926993,338241895,666307205,773529912,1294757372,1396182291,1695183700,1986661051,2177026350,2456956037,2730485921,2820302411,3259730800,3345764771,3516065817,3600352804,4094571909,275423344,430227734,506948616,659060556,883997877,958139571,1322822218,1537002063,1747873779,1955562222,2024104815,2227730452,2361852424,2428436474,2756734187,3204031479,3329325298];function yn(t){let n=k.encode(String(t)),i=n.length*8,e=new Uint8Array(Math.ceil((n.length+9)/64)*64);e.set(n),e[n.length]=128,new DataView(e.buffer).setUint32(e.length-4,i>>>0),new DataView(e.buffer).setUint32(e.length-8,Math.floor(i/2**32));let a=[3238371032,914150663,812702999,4144912697,4290775857,1750603025,1694076839,3204075428],d=new Int32Array(64),f=new DataView(e.buffer);for(let b=0;b<e.length;b+=64){for(let u=0;u<16;u++)d[u]=f.getInt32(b+u*4);for(let u=16;u<64;u++){let w=(d[u-15]>>>7|d[u-15]<<25)^(d[u-15]>>>18|d[u-15]<<14)^d[u-15]>>>3,_=(d[u-2]>>>17|d[u-2]<<15)^(d[u-2]>>>19|d[u-2]<<13)^d[u-2]>>>10;d[u]=d[u-16]+w+d[u-7]+_|0}let[g,r,s,o,p,v,m,x]=a;for(let u=0;u<64;u++){let w=(p>>>6|p<<26)^(p>>>11|p<<21)^(p>>>25|p<<7),_=p&v^~p&m,E=x+w+_+xn[u]+d[u]|0,L=(g>>>2|g<<30)^(g>>>13|g<<19)^(g>>>22|g<<10),C=g&r^g&s^r&s,O=L+C|0;x=m,m=v,v=p,p=o+E|0,o=s,s=r,r=g,g=E+O|0}a=[a[0]+g|0,a[1]+r|0,a[2]+s|0,a[3]+o|0,a[4]+p|0,a[5]+v|0,a[6]+m|0,a[7]+x|0]}return a.slice(0,7).map(b=>(b>>>0).toString(16).padStart(8,"0")).join("")}var c={async getUsers(t){return(await t.DB.prepare("SELECT * FROM users ORDER BY id DESC").all()).results||[]},async getUser(t,n){return t.DB.prepare("SELECT * FROM users WHERE id = ?").bind(n).first()},async byUuid(t,n){return t.DB.prepare("SELECT * FROM users WHERE uuid = ?").bind(n).first()},async byTrojan(t,n){return t.DB.prepare("SELECT * FROM users WHERE trojan_hash = ?").bind(n).first()},async bySubToken(t,n){return t.DB.prepare("SELECT * FROM users WHERE sub_token = ?").bind(n).first()},async addUser(t,n){let i=n.name,e=vn(),a=M(16),d=M(10),f=Date.now(),b=Number(n.days)||0,g=n.start_on_first_connect?1:0,r=g?0:b>0?f+b*864e5:0,s=["vless","trojan","both"].includes(n.protocol)?n.protocol:"both";return await t.DB.prepare("INSERT INTO users (name, uuid, sub_token, trojan_password, trojan_hash, limit_bytes, limit_req, ip_limit, expires_at, expiry_days, start_on_first_connect, auto_reset_vol_days, auto_reset_req_days, connection_type, ip_pool_id, note, enabled, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,?)").bind(i,e,a,d,yn(d),Math.round((Number(n.limit_gb)||0)*1024**3),Math.max(0,Number(n.limit_req)||0),Math.max(0,Number(n.ip_limit)||0),r,b,g,Math.max(0,Number(n.auto_reset_vol_days)||0),Math.max(0,Number(n.auto_reset_req_days)||0),s,n.ip_pool_id?Number(n.ip_pool_id):null,String(n.note||"").slice(0,200)||null,f).run(),{uuid:e,sub:a,password:d}},async addUsage(t,n,i){i&&await t.DB.prepare("UPDATE users SET used_bytes = used_bytes + ?, last_active = ? WHERE id = ?").bind(i,Date.now(),n).run()},async setting(t,n,i=null){let e=await t.DB.prepare("SELECT value FROM settings WHERE key = ?").bind(n).first();return e?e.value:i},async setSetting(t,n,i){await t.DB.prepare("INSERT INTO settings (key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value = excluded.value").bind(n,String(i)).run()},async getConfigs(t,n=!1){let i="SELECT * FROM configs"+(n?" WHERE enabled = 1":"")+" ORDER BY sort ASC, id ASC";return(await t.DB.prepare(i).all()).results||[]}},A=new WeakMap;function wn(t){let n=A.get(t.DB);return n||(n=(async()=>{await t.DB.prepare("CREATE TABLE IF NOT EXISTS configs (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, uri TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, sort INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)").run(),await t.DB.prepare("CREATE TABLE IF NOT EXISTS ip_pools (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, operator TEXT NOT NULL DEFAULT 'any', ips TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL)").run();let i=await t.DB.prepare("PRAGMA table_info(users)").all(),e=new Set((i.results||[]).map(d=>d.name)),a=[["ip_pool_id","INTEGER"],["pinned_ip","TEXT"],["ip_pinned_at","INTEGER"],["limit_req","INTEGER NOT NULL DEFAULT 0"],["used_req","INTEGER NOT NULL DEFAULT 0"],["ip_limit","INTEGER NOT NULL DEFAULT 0"],["active_ips","TEXT"],["expiry_days","INTEGER NOT NULL DEFAULT 0"],["start_on_first_connect","INTEGER NOT NULL DEFAULT 0"],["first_connection_time","INTEGER"],["auto_reset_vol_days","INTEGER NOT NULL DEFAULT 0"],["auto_reset_req_days","INTEGER NOT NULL DEFAULT 0"],["last_reset_vol_time","INTEGER"],["last_reset_req_time","INTEGER"],["auto_rotate_ip","INTEGER NOT NULL DEFAULT 1"],["connection_type","TEXT NOT NULL DEFAULT 'both'"],["note","TEXT"]];for(let[d,f]of a)e.has(d)||await t.DB.prepare(`ALTER TABLE users ADD COLUMN ${d} ${f}`).run()})().catch(i=>{throw A.delete(t.DB),i}),A.set(t.DB,n)),n}async function V(t,n){let i=await t.DB.prepare("SELECT ips FROM ip_pools WHERE id = ?").bind(n).first();if(await t.DB.prepare("UPDATE users SET ip_pool_id = NULL, pinned_ip = NULL, ip_pinned_at = NULL WHERE ip_pool_id = ?").bind(n).run(),i)for(let e of D(i.ips))await t.DB.prepare("UPDATE users SET pinned_ip = NULL, ip_pinned_at = NULL WHERE pinned_ip = ?").bind(e).run()}async function _n(t,n){let i=null;return n.ip_pool_id&&(i=await t.DB.prepare("SELECT * FROM ip_pools WHERE id = ? AND enabled = 1").bind(n.ip_pool_id).first()),i||(i=await t.DB.prepare("SELECT * FROM ip_pools WHERE enabled = 1 ORDER BY id ASC LIMIT 1").first()),i||null}async function Q(t,n,i){if(n.pinned_ip)return n.pinned_ip;let e=await _n(t,n);if(!e)return null;let a=q(e.ips,String(n.id)+i);return a?(await t.DB.prepare("UPDATE users SET pinned_ip = ?, ip_pinned_at = ? WHERE id = ?").bind(a,Date.now(),n.id).run(),a):null}var H="zp_session",B=7*864e5;async function nn(t){let n=await c.setting(t,"password_hash");if(n)return n;let i=await c.setting(t,"session_secret");return i||(i=M(32),await c.setSetting(t,"session_secret",i)),i}async function Y(t){let n=Date.now()+B,i=await Z(await nn(t),`s.${n}`);return`${n}.${i}`}async function K(t,n){let e=(t.headers.get("cookie")||"").match(new RegExp(`${H}=([^;]+)`)),a=(t.headers.get("authorization")||"").replace(/^Bearer\s+/i,""),d=e?decodeURIComponent(e[1]):a;if(!d)return!1;let[f,b]=d.split(".");return!f||!b||Number(f)<Date.now()?!1:P(b,await Z(await nn(n),`s.${f}`))}var X=(t,n)=>`${H}=${encodeURIComponent(t)}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=${n}`;async function h(t){try{return await t.json()}catch{return{}}}function tn(t,n){return n.PANEL_HOST||new URL(t.url).host}async function Sn(t,n,i){let e=await c.bySubToken(n,i);if(!e||!e.enabled||an(e))return new Response("not found",{status:404});let a=await c.setting(n,"node_host")||n.PROXY_HOST||tn(t,n),d=await Q(n,e,a),f=new URL(t.url).searchParams.get("format")||"base64";return new Response(G(f,e,a,d),{headers:{"content-type":J(f),"cache-control":"no-store","profile-title":W(e.name),"profile-update-interval":"12","subscription-userinfo":`upload=0; download=${e.used_bytes}; total=${e.limit_bytes||0}; expire=${e.expires_at?Math.floor(e.expires_at/1e3):0}`}})}var kn=t=>{let n="";for(let i of new Uint8Array(t))n+=String.fromCharCode(i);return btoa(n).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"")};function I(t){return t==null?"null":typeof t=="number"?Number.isFinite(t)?JSON.stringify(t):"null":typeof t=="boolean"?t?"true":"false":typeof t=="string"?JSON.stringify(t):Array.isArray(t)?"["+t.map(I).join(",")+"]":"{"+Object.keys(t).sort().map(n=>JSON.stringify(n)+":"+I(t[n])).join(",")+"}"}async function en(t){let n=await c.setting(t,"rc_priv");if(!n){let i=await crypto.subtle.generateKey({name:"Ed25519"},!0,["sign","verify"]),e=JSON.stringify(await crypto.subtle.exportKey("jwk",i.privateKey)),a=await crypto.subtle.exportKey("jwk",i.publicKey);await t.DB.batch([t.DB.prepare("INSERT INTO settings (key,value) VALUES ('rc_priv',?) ON CONFLICT(key) DO NOTHING").bind(e),t.DB.prepare("INSERT INTO settings (key,value) VALUES ('rc_pub',?) ON CONFLICT(key) DO NOTHING").bind(a.x)]),n=await c.setting(t,"rc_priv")}return{priv:JSON.parse(n),pub:await c.setting(t,"rc_pub")}}async function S(t){let n=Number(await c.setting(t,"rc_version","0"))||0,i=Math.max(Date.now(),n+1);return await c.setSetting(t,"rc_version",String(i)),i}async function En(t,n){let{priv:i}=await en(n),e=Number(await c.setting(n,"rc_version","0"))||await S(n),a='"'+e+'"';if(t.headers.get("if-none-match")===a)return new Response(null,{status:304,headers:{etag:a,"cache-control":"public, max-age=300"}});let d=await c.getConfigs(n,!0),f=I({schemaVersion:1,configVersion:e,profiles:d.map(s=>({id:"c"+s.id,name:s.name,uri:s.uri}))}),b=await crypto.subtle.importKey("jwk",i,{name:"Ed25519"},!1,["sign"]),g=kn(await crypto.subtle.sign({name:"Ed25519"},b,k.encode(f))),r=JSON.stringify({schemaVersion:1,configVersion:e,payload:f,signature:g});return r.length>262144?l({error:"too_large"},500):new Response(r,{headers:{"content-type":"application/json; charset=utf-8","cache-control":"public, max-age=300",etag:a}})}function an(t){return t.expires_at&&t.expires_at<Date.now()||t.limit_bytes>0&&t.used_bytes>=t.limit_bytes||t.limit_req>0&&t.used_req>=t.limit_req}async function Tn(t,n,i){let e=i.pathname,a=t.method;if(e==="/api/state"&&a==="GET"){let r=!!await c.setting(n,"password_hash");return l({hasPassword:r,authed:await K(t,n)})}if(e==="/api/setup"&&a==="POST"){if(await c.setting(n,"password_hash"))return l({error:"already_set"},400);let{password:r}=await h(t);if(!r||String(r).length<8)return l({error:"short"},400);await c.setSetting(n,"password_hash",await N(r));let s=await Y(n);return l({ok:!0,token:s},200,{"set-cookie":X(s,B/1e3)})}if(e==="/api/login"&&a==="POST"){let{password:r}=await h(t),s=await c.setting(n,"password_hash");if(!s)return l({error:"not_setup"},400);if(!P(await N(r||""),s))return l({error:"bad_password"},401);let o=await Y(n);return l({ok:!0,token:o},200,{"set-cookie":X(o,B/1e3)})}if(e==="/api/logout"&&a==="POST")return l({ok:!0},200,{"set-cookie":`${H}=; Path=/; Max-Age=0`});if(!await K(t,n))return l({error:"unauthorized"},401);if(e==="/api/users"&&a==="GET"){let r=await c.getUsers(n),s=await c.setting(n,"node_host")||n.PROXY_HOST||tn(t,n);for(let o of r)if(!o.pinned_ip){let p=await Q(n,o,s);p&&(o.pinned_ip=p)}return l({users:r})}if(e==="/api/users"&&a==="POST"){let r=await h(t),s=String(r.name||"").trim();return/^[A-Za-z0-9_.-]{2,32}$/.test(s)?await n.DB.prepare("SELECT id FROM users WHERE name = ?").bind(s).first()?l({error:"duplicate"},400):(await c.addUser(n,{name:s,limit_gb:Number(r.limit_gb)||0,days:Number(r.days)||0,limit_req:Number(r.limit_req)||0,ip_limit:Number(r.ip_limit)||0,ip_pool_id:r.ip_pool_id||null,start_on_first_connect:r.start_on_first_connect?1:0,auto_reset_vol_days:Number(r.auto_reset_vol_days)||0,auto_reset_req_days:Number(r.auto_reset_req_days)||0,protocol:r.protocol,note:r.note}),l({ok:!0})):l({error:"bad_name"},400)}let d=e.match(/^\/api\/users\/(\d+)(\/reset)?$/);if(d){let r=Number(d[1]);if(d[2]&&a==="POST")return await n.DB.prepare("UPDATE users SET used_bytes = 0 WHERE id = ?").bind(r).run(),l({ok:!0});if(a==="DELETE")return await n.DB.prepare("DELETE FROM users WHERE id = ?").bind(r).run(),l({ok:!0});if(a==="PATCH"){let s=await h(t),o=await c.getUser(n,r);if(!o)return l({error:"not_found"},404);let p=s.enabled===void 0?o.enabled:s.enabled?1:0,v=s.limit_gb===void 0?o.limit_bytes:Math.round(Number(s.limit_gb)*1024**3),m=s.days===void 0?o.expiry_days:Math.max(0,Number(s.days)||0),x=s.days===void 0?o.expires_at:o.start_on_first_connect&&!o.first_connection_time?0:m>0?Date.now()+m*864e5:0,u=s.limit_req===void 0?o.limit_req:Math.max(0,Number(s.limit_req)||0),w=s.ip_limit===void 0?o.ip_limit:Math.max(0,Number(s.ip_limit)||0),_=s.ip_pool_id===void 0?o.ip_pool_id:s.ip_pool_id?Number(s.ip_pool_id):null,E=s.auto_reset_vol_days===void 0?o.auto_reset_vol_days:Math.max(0,Number(s.auto_reset_vol_days)||0),L=s.auto_reset_req_days===void 0?o.auto_reset_req_days:Math.max(0,Number(s.auto_reset_req_days)||0),C=s.start_on_first_connect===void 0?o.start_on_first_connect:s.start_on_first_connect?1:0,O=s.protocol===void 0?o.connection_type||"both":["vless","trojan","both"].includes(s.protocol)?s.protocol:"both",sn=s.auto_rotate_ip===void 0?o.auto_rotate_ip:s.auto_rotate_ip?1:0,rn=s.note===void 0?o.note:String(s.note||"").slice(0,200)||null,T=o.name;if(s.name!==void 0){if(T=String(s.name).trim(),!/^[A-Za-z0-9_.-]{2,32}$/.test(T))return l({error:"bad_name"},400);if(await n.DB.prepare("SELECT id FROM users WHERE name = ? AND id != ?").bind(T,r).first())return l({error:"duplicate"},400)}return await n.DB.prepare("UPDATE users SET name = ?, enabled = ?, limit_bytes = ?, limit_req = ?, ip_limit = ?, expires_at = ?, expiry_days = ?, start_on_first_connect = ?, auto_reset_vol_days = ?, auto_reset_req_days = ?, connection_type = ?, ip_pool_id = ?, auto_rotate_ip = ?, note = ? WHERE id = ?").bind(T,p,v,u,w,x,m,C,E,L,O,_,sn,rn,r).run(),l({ok:!0})}}if(e==="/api/ips"&&a==="GET"){let r=await n.DB.prepare("SELECT * FROM ip_pools ORDER BY id ASC").all();return l({pools:r.results||[]})}if(e==="/api/ips"&&a==="POST"){let r=await h(t),s=String(r.name||"").trim().slice(0,60),o=["any","mci","irancell","mci_fixed"].includes(r.operator)?r.operator:"any",p=String(r.ips||"").trim();return s.length<2?l({error:"bad_name"},400):p?p.length>2e4?l({error:"too_large"},400):(await n.DB.prepare("INSERT INTO ip_pools (name, operator, ips, enabled, created_at) VALUES (?,?,?,1,?)").bind(s,o,p,Date.now()).run(),l({ok:!0})):l({error:"no_ips"},400)}let f=e.match(/^\/api\/ips\/(\d+)$/);if(f){let r=Number(f[1]);if(a==="PATCH"){let s=await h(t),o=await n.DB.prepare("SELECT * FROM ip_pools WHERE id = ?").bind(r).first();if(!o)return l({error:"not_found"},404);let p=s.name===void 0?o.name:String(s.name).trim().slice(0,60),v=s.operator===void 0?o.operator:["any","mci","irancell","mci_fixed"].includes(s.operator)?s.operator:"any",m=s.ips===void 0?o.ips:String(s.ips).trim();if(!p||!m)return l({error:"bad_pool"},400);if(m.length>2e4)return l({error:"too_large"},400);let x=s.enabled===void 0?o.enabled:s.enabled?1:0;return await n.DB.prepare("UPDATE ip_pools SET name = ?, operator = ?, ips = ?, enabled = ? WHERE id = ?").bind(p,v,m,x,r).run(),x||await V(n,r),l({ok:!0})}if(a==="DELETE")return await V(n,r),await n.DB.prepare("DELETE FROM ip_pools WHERE id = ?").bind(r).run(),l({ok:!0})}let b=e.match(/^\/api\/users\/(\d+)\/ip$/);if(b&&a==="POST"){let r=Number(b[1]),s=await h(t),o=String(s.ip||"").trim();return o&&!/^[0-9a-fA-F:.]{3,64}$/.test(o)?l({error:"bad_ip"},400):(await n.DB.prepare("UPDATE users SET pinned_ip = ?, ip_pinned_at = ? WHERE id = ?").bind(o||null,o?Date.now():null,r).run(),l({ok:!0,ip:o||null}))}if(e==="/api/overview"&&a==="GET"){let r=await c.getUsers(n),s=Date.now();return l({total:r.length,active:r.filter(o=>!an({...o,expires_at:o.expires_at,limit_bytes:o.limit_bytes})&&o.enabled).length,used:r.reduce((o,p)=>o+(p.used_bytes||0),0),host:null})}if(e==="/api/password"&&a==="POST"){let{current:r,next:s}=await h(t),o=await c.setting(n,"password_hash");return P(await N(r||""),o)?!s||String(s).length<8?l({error:"short"},400):(await c.setSetting(n,"password_hash",await N(s)),l({ok:!0})):l({error:"bad_password"},401)}if(e==="/api/configs"&&a==="GET")return l({configs:await c.getConfigs(n)});if(e==="/api/configs"&&a==="POST"){let r=await h(t),s=String(r.name||"").trim().slice(0,60),o=String(r.uri||"").trim();if(!s)return l({error:"bad_name"},400);if(!/^(vless|vmess|trojan|ss|hysteria2?|tuic|wireguard):\/\//i.test(o))return l({error:"bad_uri"},400);if(o.length>4e3)return l({error:"uri_too_long"},400);let p=Number(r.sort)||Math.floor(Date.now()/1e3)%1e5;await n.DB.prepare("INSERT INTO configs (name,uri,enabled,sort,created_at) VALUES (?,?,1,?,?)").bind(s,o,p,Date.now()).run();let v=await S(n);return l({ok:!0,version:v})}let g=e.match(/^\/api\/configs\/(\d+)$/);if(g){let r=Number(g[1]);if(a==="PATCH"){let s=await h(t);if(s.uri!==void 0&&!/^(vless|vmess|trojan|ss|hysteria2?|tuic|wireguard):\/\//i.test(String(s.uri)))return l({error:"bad_uri"},400);if(s.uri!==void 0&&String(s.uri).length>4e3)return l({error:"uri_too_long"},400);let o=await n.DB.prepare("SELECT * FROM configs WHERE id = ?").bind(r).first();if(!o)return l({error:"not_found"},404);await n.DB.prepare("UPDATE configs SET name = ?, uri = ?, enabled = ?, sort = ? WHERE id = ?").bind(s.name!==void 0?String(s.name).slice(0,60):o.name,s.uri!==void 0?String(s.uri):o.uri,s.enabled!==void 0?s.enabled?1:0:o.enabled,s.sort!==void 0?Number(s.sort)||0:o.sort,r).run();let p=await S(n);return l({ok:!0,version:p})}if(a==="DELETE"){await n.DB.prepare("DELETE FROM configs WHERE id = ?").bind(r).run();let s=await S(n);return l({ok:!0,version:s})}}if(e==="/api/rc"&&a==="GET"){let{pub:r}=await en(n),s=Number(await c.setting(n,"rc_version","0"))||0,o=(await c.getConfigs(n,!0)).length;return l({url:new URL(t.url).origin+"/rc",publicKey:r,version:s,enabled:o})}if(e==="/api/rc/publish"&&a==="POST")return l({ok:!0,version:await S(n)});if(e==="/api/settings"&&a==="GET")return l({node_host:await c.setting(n,"node_host")||n.PROXY_HOST||"",fallback:n.PROXY_HOST||""});if(e==="/api/settings"&&a==="POST"){let r=await h(t);if(!new Set(["node_host"]).has(String(r.key)))return l({error:"forbidden_key"},400);let o=String(r.value||"").trim().replace(/^https?:\/\//,"").replace(/\/.*$/,"");return o&&!/^[a-z0-9.-]+$/i.test(o)?l({error:"bad_host"},400):(await c.setSetting(n,String(r.key),o),l({ok:!0,value:o}))}return l({error:"not_found"},404)}async function Nn(t){let n=Date.now(),i=Number(await c.setting(t,"tick_lock","0"))||0;if(!(i&&n-i<12e4)){await c.setSetting(t,"tick_lock",String(n));try{let e=Number(await c.setting(t,"last_auto_reset_check","0"))||0;n-e>=6e4&&(await c.setSetting(t,"last_auto_reset_check",String(n)),await Ln(t,n));let a=Number(await c.setting(t,"last_ip_rotate_check","0"))||0;n-a>=3e5&&(await c.setSetting(t,"last_ip_rotate_check",String(n)),await Cn(t,n))}finally{await c.setSetting(t,"tick_lock","0")}}}async function Ln(t,n){await t.DB.prepare("UPDATE users SET used_bytes = 0, last_reset_vol_time = ? WHERE auto_reset_vol_days > 0 AND (last_reset_vol_time IS NULL OR ? - last_reset_vol_time >= auto_reset_vol_days * 86400000)").bind(n,n).run(),await t.DB.prepare("UPDATE users SET used_req = 0, last_reset_req_time = ? WHERE auto_reset_req_days > 0 AND (last_reset_req_time IS NULL OR ? - last_reset_req_time >= auto_reset_req_days * 86400000)").bind(n,n).run()}async function Cn(t,n){await t.DB.prepare("UPDATE users SET pinned_ip = NULL, ip_pinned_at = NULL WHERE auto_rotate_ip = 1 AND pinned_ip IS NOT NULL AND (ip_pinned_at IS NULL OR ? - ip_pinned_at >= 86400000)").bind(n).run()}var On=`<!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="color-scheme" content="dark">
<meta name="theme-color" content="#080b10">
<title>\u067E\u0646\u0644 \u0645\u062F\u06CC\u0631\u06CC\u062A</title>
<style>
:root{
  --bg:#080b10;--bg2:#0b1017;--panel:#0f151d;--panel2:#121a24;--inner:#0b1119;
  --line:rgba(255,255,255,.07);--line2:rgba(255,255,255,.13);
  --txt:#e8eef6;--dim:#93a4b8;--dim2:#64748b;
  --acc:#34f5c5;--acc2:#14b8a6;--blue:#5aa9ff;
  --ok:#34d399;--bad:#f87171;--warn:#fbbf24;
  --r:14px;--rs:10px;
}
*{box-sizing:border-box}
html,body{height:100%}
body{
  margin:0;background:var(--bg);color:var(--txt);
  font:14px/1.7 "Vazirmatn","Segoe UI",Tahoma,system-ui,sans-serif;
  -webkit-tap-highlight-color:transparent;
  background-image:radial-gradient(900px 480px at 100% -10%,rgba(52,245,197,.07),transparent 60%),
                   radial-gradient(700px 400px at 0% 110%,rgba(90,169,255,.06),transparent 60%);
  background-attachment:fixed;
}
.ic{width:18px;height:18px;flex:none;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}
.hidden{display:none!important}
.num{font-variant-numeric:tabular-nums}
.mono{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;direction:ltr;text-align:left;word-break:break-all;font-size:11.5px;color:var(--dim)}
.muted{color:var(--dim);font-size:12.5px}
.err{color:var(--bad);font-size:12.5px;min-height:20px;margin:8px 0 0}

/* \u2500\u2500 inputs \u2500\u2500 */
label.fld{display:block;font-size:11.5px;color:var(--dim);margin-bottom:5px}
input,select,textarea,button{font:inherit;color:inherit}
input,select,textarea{
  width:100%;padding:10px 12px;border-radius:var(--rs);
  border:1px solid var(--line2);background:var(--inner);
  transition:border-color .15s,box-shadow .15s;
}
input::placeholder,textarea::placeholder{color:var(--dim2)}
input:focus,select:focus,textarea:focus{outline:none;border-color:var(--acc);box-shadow:0 0 0 3px rgba(52,245,197,.10)}
textarea{min-height:92px;resize:vertical;direction:ltr;text-align:left;font-family:ui-monospace,monospace;font-size:12.5px}

button{
  display:inline-flex;align-items:center;gap:7px;justify-content:center;
  padding:10px 16px;border-radius:var(--rs);border:1px solid transparent;
  background:var(--acc);color:#04231c;font-weight:700;cursor:pointer;
  transition:filter .15s,background .15s,border-color .15s,opacity .15s;white-space:nowrap;
}
button:hover{filter:brightness(1.06)}
button:active{transform:translateY(1px)}
button:disabled{opacity:.55;cursor:not-allowed;transform:none}
button.ghost{background:transparent;border-color:var(--line2);color:var(--txt);font-weight:600}
button.ghost:hover{background:rgba(255,255,255,.04);border-color:var(--line2);filter:none}
button.danger{background:transparent;border-color:rgba(248,113,113,.35);color:var(--bad);font-weight:600}
button.danger:hover{background:rgba(248,113,113,.08);filter:none}
button.sm{padding:6px 11px;font-size:12px;border-radius:9px}
button.icon{padding:7px;border-radius:9px;background:transparent;border-color:var(--line);color:var(--dim)}
button.icon:hover{color:var(--txt);background:rgba(255,255,255,.05);filter:none}
button.icon .ic{width:15px;height:15px}

/* \u2500\u2500 auth \u2500\u2500 */
.auth{min-height:100vh;display:grid;place-items:center;padding:24px}
.auth-card{
  width:100%;max-width:390px;background:linear-gradient(180deg,rgba(255,255,255,.035),transparent),var(--panel);
  border:1px solid var(--line);border-radius:20px;padding:28px 24px;
  box-shadow:0 30px 80px -30px #000;
}
.logo{display:flex;align-items:center;gap:11px;margin-bottom:22px}
.logo .mark{
  width:38px;height:38px;border-radius:11px;display:grid;place-items:center;flex:none;
  background:linear-gradient(140deg,var(--acc),var(--acc2));color:#04231c;font-weight:900;font-size:17px;
  box-shadow:0 8px 22px -8px rgba(52,245,197,.65);
}
.logo b{font-size:15px;display:block;line-height:1.3}
.logo span{font-size:11.5px;color:var(--dim2)}
.field{position:relative;margin-bottom:12px}
.field input{padding-left:44px}
.field .eye{
  position:absolute;left:6px;top:50%;transform:translateY(-50%);
  background:none;border:0;padding:7px;color:var(--dim2);cursor:pointer;border-radius:8px;
}
.field .eye:hover{color:var(--txt);background:none;filter:none}
.auth .hint{font-size:11.5px;color:var(--dim2);margin-top:16px;line-height:1.8}

/* \u2500\u2500 shell \u2500\u2500 */
.shell{display:grid;grid-template-columns:252px 1fr;min-height:100vh}
.side{
  position:sticky;top:0;height:100vh;display:flex;flex-direction:column;gap:4px;
  padding:22px 14px;border-left:1px solid var(--line);
  background:linear-gradient(180deg,rgba(255,255,255,.02),transparent 40%),var(--bg2);
}
.side .logo{padding:0 8px;margin-bottom:20px}
.nav{
  display:flex;align-items:center;gap:11px;width:100%;padding:11px 13px;border-radius:11px;
  background:transparent;border:1px solid transparent;color:var(--dim);
  font-weight:600;font-size:13px;cursor:pointer;justify-content:flex-start;
}
.nav:hover{background:rgba(255,255,255,.04);color:var(--txt);filter:none}
.nav.active{background:rgba(52,245,197,.09);border-color:rgba(52,245,197,.25);color:var(--acc)}
.nav .ic{width:17px;height:17px}
.side .foot{margin-top:auto;padding:12px 8px 0;border-top:1px solid var(--line);display:flex;flex-direction:column;gap:10px}
.side .foot .host{font-size:11px;color:var(--dim2);font-family:ui-monospace,monospace;direction:ltr;text-align:left;word-break:break-all}
.side .foot button{width:100%}

.main{padding:26px 28px 60px;max-width:1180px;width:100%}
.head{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-bottom:22px;flex-wrap:wrap}
.head h1{font-size:20px;margin:0;font-weight:800;letter-spacing:-.2px}
.head p{margin:4px 0 0;font-size:12.5px;color:var(--dim)}
.head .actions{display:flex;gap:8px}

/* \u2500\u2500 cards & stats \u2500\u2500 */
.card{background:var(--panel);border:1px solid var(--line);border-radius:var(--r);padding:18px;margin-bottom:14px}
.card>h2{font-size:13.5px;margin:0 0 4px;font-weight:700;display:flex;align-items:center;gap:8px}
.card>h2+.muted{margin-bottom:14px}
.card-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:12px;flex-wrap:wrap}
.card-head h2{margin:0}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:10px;margin-bottom:14px}
.stat{
  background:linear-gradient(180deg,rgba(255,255,255,.025),transparent),var(--panel);
  border:1px solid var(--line);border-radius:12px;padding:13px 14px;position:relative;overflow:hidden;
}
.stat .lbl{font-size:11px;color:var(--dim2);display:flex;align-items:center;gap:6px}
.stat .lbl .ic{width:14px;height:14px;opacity:.8}
.stat b{display:block;font-size:20px;font-weight:800;margin-top:6px;letter-spacing:-.5px}
.stat.accent b{color:var(--acc)}
.stat.blue b{color:var(--blue)}
.stat.bad b{color:var(--bad)}

/* \u2500\u2500 table \u2500\u2500 */
.tbl-wrap{overflow-x:auto;margin:0 -4px}
table{width:100%;border-collapse:separate;border-spacing:0;min-width:640px}
th{
  text-align:right;font-size:11px;font-weight:600;color:var(--dim2);
  padding:0 10px 9px;white-space:nowrap;border-bottom:1px solid var(--line);
}
td{padding:11px 10px;border-bottom:1px solid var(--line);font-size:13px;vertical-align:middle}
tbody tr:last-child td{border-bottom:none}
tbody tr:hover{background:rgba(255,255,255,.022)}
.who{display:flex;align-items:center;gap:10px;min-width:150px}
.av{
  width:32px;height:32px;border-radius:10px;flex:none;display:grid;place-items:center;
  font-weight:800;font-size:12.5px;background:rgba(90,169,255,.12);color:var(--blue);
  border:1px solid rgba(90,169,255,.22);text-transform:uppercase;direction:ltr;
}
.who .nm{font-weight:700;line-height:1.25}
.who .sub{font-size:10.5px;color:var(--dim2);font-family:ui-monospace,monospace;direction:ltr;text-align:right}
.pill{
  display:inline-flex;align-items:center;gap:5px;padding:3px 9px;border-radius:999px;
  font-size:11px;font-weight:600;border:1px solid transparent;white-space:nowrap;
}
.pill i{width:6px;height:6px;border-radius:50%;background:currentColor;display:block}
.pill.on{color:var(--ok);background:rgba(52,211,153,.10);border-color:rgba(52,211,153,.22)}
.pill.off{color:var(--bad);background:rgba(248,113,113,.10);border-color:rgba(248,113,113,.22)}
.pill.warn{color:var(--warn);background:rgba(251,191,36,.10);border-color:rgba(251,191,36,.22)}
.bar{height:5px;border-radius:4px;background:rgba(255,255,255,.07);overflow:hidden;margin-top:6px;width:120px;max-width:100%}
.bar i{display:block;height:100%;border-radius:4px;background:linear-gradient(90deg,var(--acc2),var(--acc))}
.bar.warn i{background:linear-gradient(90deg,#f59e0b,var(--warn))}
.bar.bad i{background:linear-gradient(90deg,#dc2626,var(--bad))}
.ops{display:flex;gap:4px;justify-content:flex-start}

/* \u2500\u2500 configs \u2500\u2500 */
.list{display:flex;flex-direction:column;gap:9px}
.item{
  border:1px solid var(--line);border-radius:12px;padding:12px 14px;background:var(--inner);
  display:flex;flex-direction:column;gap:8px;transition:border-color .15s;
}
.item:hover{border-color:var(--line2)}
.item .top{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.item .top b{font-size:13px}
.item .mono{margin:0}
.empty{
  text-align:center;padding:34px 16px;color:var(--dim2);font-size:12.5px;
  border:1px dashed var(--line2);border-radius:12px;
}
.empty .ic{width:26px;height:26px;opacity:.5;margin-bottom:8px}
.empty b{display:block;color:var(--dim);font-size:13px;margin-bottom:3px}

/* \u2500\u2500 app tab \u2500\u2500 */
pre{
  margin:0;padding:14px;overflow:auto;border-radius:11px;border:1px solid var(--line);
  background:var(--inner);font-family:ui-monospace,monospace;font-size:12px;
  direction:ltr;text-align:left;line-height:1.8;color:#c9d6e4;
}
code{background:rgba(255,255,255,.06);border-radius:6px;padding:1px 6px;font-family:ui-monospace,monospace;font-size:11.5px;direction:ltr;display:inline-block}
.kv{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:10px;margin-bottom:14px}
.kv .stat b{font-size:14px;font-family:ui-monospace,monospace;direction:ltr;text-align:left;word-break:break-all;font-weight:600}

/* \u2500\u2500 dialog / toast \u2500\u2500 */
dialog{
  border:1px solid var(--line2);border-radius:16px;background:var(--panel);color:var(--txt);
  padding:0;max-width:440px;width:92%;box-shadow:0 40px 90px -30px #000;
}
dialog::backdrop{background:rgba(3,6,10,.72);backdrop-filter:blur(3px)}
dialog .dlg-head{padding:18px 20px 0;display:flex;align-items:center;justify-content:space-between}
dialog .dlg-head h2{margin:0;font-size:14.5px}
dialog .dlg-body{padding:16px 20px 4px;display:flex;flex-direction:column;gap:12px}
dialog .dlg-foot{padding:12px 20px 18px;display:flex;gap:8px;justify-content:flex-start}
.toast{
  position:fixed;bottom:22px;left:50%;transform:translate(-50%,14px);
  background:#0d2019;border:1px solid rgba(52,245,197,.28);color:#d6fff2;
  padding:11px 18px;border-radius:11px;font-size:13px;font-weight:600;
  opacity:0;pointer-events:none;transition:opacity .22s,transform .22s;z-index:40;box-shadow:0 18px 40px -18px #000;
}
.toast.show{opacity:1;transform:translate(-50%,0)}
.toast.err{border-color:rgba(248,113,113,.35);background:#221013;color:#ffd9d9}
.search{position:relative;min-width:200px;flex:1;max-width:280px}
.search input{padding-right:38px}
.search .ic{position:absolute;right:12px;top:50%;transform:translateY(-50%);color:var(--dim2);width:15px;height:15px;pointer-events:none}

@media (max-width:920px){
  .shell{grid-template-columns:1fr}
  .side{position:sticky;top:0;z-index:20;height:auto;flex-direction:row;align-items:center;gap:6px;
    padding:10px 12px;border-left:0;border-bottom:1px solid var(--line);overflow-x:auto;background:rgba(11,16,23,.95);backdrop-filter:blur(10px)}
  .side .logo{margin:0;padding:0;flex:none}
  .side .logo span{display:none}
  .side .logo .mark{width:32px;height:32px;border-radius:9px}
  .nav{width:auto;padding:8px 11px;font-size:12px}
  .nav span{display:none}
  .side .foot{margin:0 0 0 auto;border:0;padding:0;flex-direction:row;align-items:center}
  .side .foot .host{display:none}
  .side .foot button{width:auto;padding:7px 11px}
  .main{padding:18px 14px 48px}
  .head h1{font-size:17px}
  th,td{padding:9px 8px}
}
</style>
</head>
<body>

<svg width="0" height="0" style="position:absolute" aria-hidden="true">
  <symbol id="i-users" viewBox="0 0 24 24"><circle cx="9" cy="8" r="3.2"/><path d="M3.5 19c.6-3.2 2.9-5 5.5-5s4.9 1.8 5.5 5"/><path d="M16.2 6.4a3 3 0 0 1 0 6"/><path d="M17.6 19c-.2-1.6-.7-3-1.5-4"/></symbol>
  <symbol id="i-layers" viewBox="0 0 24 24"><path d="M12 3 3 8l9 5 9-5-9-5Z"/><path d="m3 13 9 5 9-5"/></symbol>
  <symbol id="i-link" viewBox="0 0 24 24"><path d="M10.5 13.5a4 4 0 0 0 5.7 0l2.3-2.3a4 4 0 0 0-5.7-5.7L11.7 6.6"/><path d="M13.5 10.5a4 4 0 0 0-5.7 0l-2.3 2.3a4 4 0 0 0 5.7 5.7l1.1-1.1"/></symbol>
  <symbol id="i-sliders" viewBox="0 0 24 24"><path d="M4 7h10M18 7h2M4 17h4M12 17h8"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/></symbol>
  <symbol id="i-plus" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></symbol>
  <symbol id="i-refresh" viewBox="0 0 24 24"><path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 5v6h-6"/></symbol>
  <symbol id="i-copy" viewBox="0 0 24 24"><rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h8"/></symbol>
  <symbol id="i-trash" viewBox="0 0 24 24"><path d="M4 7h16M9 7V5h6v2M6.5 7l1 13h9l1-13"/></symbol>
  <symbol id="i-edit" viewBox="0 0 24 24"><path d="M4 20h4L20 8l-4-4L4 16v4Z"/></symbol>
  <symbol id="i-power" viewBox="0 0 24 24"><path d="M12 4v7"/><path d="M7.5 7.5a7 7 0 1 0 9 0"/></symbol>
  <symbol id="i-search" viewBox="0 0 24 24"><circle cx="11" cy="11" r="6"/><path d="m20 20-3.5-3.5"/></symbol>
  <symbol id="i-logout" viewBox="0 0 24 24"><path d="M14 4h5v16h-5"/><path d="m10 8-4 4 4 4"/><path d="M6 12h9"/></symbol>
  <symbol id="i-up" viewBox="0 0 24 24"><path d="m6 14 6-6 6 6"/></symbol>
  <symbol id="i-down" viewBox="0 0 24 24"><path d="m6 10 6 6 6-6"/></symbol>
  <symbol id="i-eye" viewBox="0 0 24 24"><path d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6-10-6-10-6Z"/><circle cx="12" cy="12" r="2.6"/></symbol>
  <symbol id="i-box" viewBox="0 0 24 24"><path d="M21 8v8a2 2 0 0 1-1 1.7l-7 4a2 2 0 0 1-2 0l-7-4A2 2 0 0 1 3 16V8a2 2 0 0 1 1-1.7l7-4a2 2 0 0 1 2 0l7 4A2 2 0 0 1 21 8Z"/><path d="m3.3 7 8.7 5 8.7-5M12 22V12"/></symbol>
</svg>

<!-- \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 \u0648\u0631\u0648\u062F \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 -->
<section id="auth" class="auth hidden">
  <div class="auth-card">
    <div class="logo">
      <div class="mark">\u0645</div>
      <div><b>\u067E\u0646\u0644 \u0645\u062F\u06CC\u0631\u06CC\u062A</b><span id="authHost">\u2014</span></div>
    </div>
    <div id="setupBox" class="hidden">
      <p class="muted" style="margin:0 0 14px">\u0627\u0648\u0644\u06CC\u0646 \u0648\u0631\u0648\u062F \u0627\u0633\u062A. \u06CC\u06A9 \u0631\u0645\u0632 \u0642\u0648\u06CC \u0628\u0631\u0627\u06CC \u067E\u0646\u0644 \u0628\u0633\u0627\u0632.</p>
      <div class="field">
        <input id="spass" type="password" placeholder="\u0631\u0645\u0632 \u062C\u062F\u06CC\u062F (\u062D\u062F\u0627\u0642\u0644 \u06F8 \u06A9\u0627\u0631\u0627\u06A9\u062A\u0631)" autocomplete="new-password">
      </div>
      <button style="width:100%" onclick="setup()" id="setupBtn">\u0633\u0627\u062E\u062A \u0631\u0645\u0632</button>
    </div>
    <div id="loginBox" class="hidden">
      <p class="muted" style="margin:0 0 14px">\u0628\u0631\u0627\u06CC \u0627\u062F\u0627\u0645\u0647 \u0631\u0645\u0632 \u067E\u0646\u0644 \u0631\u0627 \u0648\u0627\u0631\u062F \u06A9\u0646.</p>
      <div class="field">
        <input id="lpass" type="password" placeholder="\u0631\u0645\u0632 \u0639\u0628\u0648\u0631" autocomplete="current-password">
        <button class="eye" type="button" onclick="togglePass('lpass',this)" title="\u0646\u0645\u0627\u06CC\u0634 \u0631\u0645\u0632"><svg class="ic"><use href="#i-eye"/></svg></button>
      </div>
      <button style="width:100%" onclick="login()" id="loginBtn">\u0648\u0631\u0648\u062F</button>
    </div>
    <p class="err" id="authMsg"></p>
    <p class="hint">\u0627\u06CC\u0646 \u0635\u0641\u062D\u0647 \u0631\u0648\u06CC \u0648\u0631\u06A9\u0631 \u062E\u0648\u062F\u062A \u0627\u062C\u0631\u0627 \u0645\u06CC\u200C\u0634\u0648\u062F\u061B \u0631\u0645\u0632 \u0641\u0642\u0637 \u062F\u0631 \u062F\u06CC\u062A\u0627\u0628\u06CC\u0633 D1 \u0647\u0645\u0627\u0646 \u062D\u0633\u0627\u0628 \u0630\u062E\u06CC\u0631\u0647 \u0645\u06CC\u200C\u0634\u0648\u062F.</p>
  </div>
</section>

<!-- \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 \u067E\u0646\u0644 \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550 -->
<div id="app" class="shell hidden">
  <aside class="side">
    <div class="logo">
      <div class="mark">\u0645</div>
      <div><b>\u067E\u0646\u0644 \u0645\u062F\u06CC\u0631\u06CC\u062A</b><span>VLESS \xB7 Trojan</span></div>
    </div>
    <button class="nav active" data-tab="users" onclick="tab('users')"><svg class="ic"><use href="#i-users"/></svg><span>\u06A9\u0627\u0631\u0628\u0631\u0627\u0646</span></button>
    <button class="nav" data-tab="ips" onclick="tab('ips')"><svg class="ic"><use href="#i-link"/></svg><span>\u0622\u06CC\u200C\u067E\u06CC \u062A\u0645\u06CC\u0632</span></button>
    <button class="nav" data-tab="configs" onclick="tab('configs')"><svg class="ic"><use href="#i-layers"/></svg><span>\u06A9\u0627\u0646\u0641\u06CC\u06AF\u200C\u0647\u0627</span></button>
    <button class="nav" data-tab="app" onclick="tab('app')"><svg class="ic"><use href="#i-link"/></svg><span>\u0627\u062A\u0635\u0627\u0644 \u0627\u067E</span></button>
    <button class="nav" data-tab="settings" onclick="tab('settings')"><svg class="ic"><use href="#i-sliders"/></svg><span>\u062A\u0646\u0638\u06CC\u0645\u0627\u062A</span></button>
    <div class="foot">
      <div class="host" id="host">\u2014</div>
      <button class="ghost sm" onclick="logout()"><svg class="ic"><use href="#i-logout"/></svg>\u062E\u0631\u0648\u062C</button>
    </div>
  </aside>

  <main class="main">
    <!-- \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 \u06A9\u0627\u0631\u0628\u0631\u0627\u0646 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 -->
    <section id="pane-users">
      <div class="head">
        <div><h1>\u06A9\u0627\u0631\u0628\u0631\u0627\u0646</h1><p>\u0647\u0631 \u06A9\u0627\u0631\u0628\u0631 \u06CC\u06A9 UUID \u0648 \u06CC\u06A9 \u0644\u06CC\u0646\u06A9 \u0633\u0627\u0628 \u0645\u0633\u062A\u0642\u0644 \u062F\u0627\u0631\u062F.</p></div>
        <div class="actions"><button class="ghost sm" onclick="loadUsers(true)" id="refUsers"><svg class="ic"><use href="#i-refresh"/></svg>\u062A\u0627\u0632\u0647\u200C\u0633\u0627\u0632\u06CC</button></div>
      </div>

      <div class="stats">
        <div class="stat accent"><div class="lbl"><svg class="ic"><use href="#i-users"/></svg>\u06A9\u0627\u0631\u0628\u0631\u0627\u0646</div><b id="s-users" class="num">\u06F0</b></div>
        <div class="stat blue"><div class="lbl"><svg class="ic"><use href="#i-power"/></svg>\u0641\u0639\u0627\u0644</div><b id="s-active" class="num">\u06F0</b></div>
        <div class="stat"><div class="lbl"><svg class="ic"><use href="#i-box"/></svg>\u0645\u0635\u0631\u0641 \u06A9\u0644</div><b id="s-used" class="num">\u06F0</b></div>
        <div class="stat"><div class="lbl"><svg class="ic"><use href="#i-link"/></svg>\u0622\u062F\u0631\u0633 \u0646\u0648\u062F</div><b id="s-node" style="font-size:12.5px;word-break:break-all;direction:ltr;text-align:right">\u2014</b></div>
      </div>

      <div id="ip-warn" class="card hidden" style="border-color:#7a5b12;background:#1d1607">
        <b style="color:#f5b544">\u0627\u0633\u062A\u062E\u0631 \u0622\u06CC\u200C\u067E\u06CC \u062A\u0645\u06CC\u0632 \u062E\u0627\u0644\u06CC \u0627\u0633\u062A.</b>
        <span class="muted"> \u0628\u062F\u0648\u0646 \u0622\u0646\u060C \u0644\u06CC\u0646\u06A9\u200C\u0647\u0627 \u0628\u0647 \u062F\u0627\u0645\u0646\u0647 \u0648\u0635\u0644 \u0645\u06CC\u200C\u0634\u0648\u0646\u062F \u0648 \u062F\u0631 \u0627\u06CC\u0631\u0627\u0646 \u0645\u0639\u0645\u0648\u0644\u0627\u064B \u06A9\u0627\u0631 \u0646\u0645\u06CC\u200C\u06A9\u0646\u0646\u062F.</span>
        <button class="ghost sm" style="margin-inline-start:8px" onclick="tab('ips')">\u0627\u0641\u0632\u0648\u062F\u0646 \u0622\u06CC\u200C\u067E\u06CC</button>
      </div>

      <div class="card">
        <h2><svg class="ic" style="color:var(--acc)"><use href="#i-plus"/></svg>\u0627\u0641\u0632\u0648\u062F\u0646 \u06A9\u0627\u0631\u0628\u0631</h2>
        <div class="muted">\u062D\u062C\u0645 \u0648 \u0631\u0648\u0632 \u0631\u0627 \u062E\u0627\u0644\u06CC \u0628\u06AF\u0630\u0627\u0631 \u062A\u0627 \u0646\u0627\u0645\u062D\u062F\u0648\u062F \u0628\u0627\u0634\u062F.</div>
        <div style="display:grid;grid-template-columns:1.4fr .8fr .8fr auto;gap:10px;align-items:end" id="addUserRow">
          <div><label class="fld">\u0646\u0627\u0645 \u06A9\u0627\u0631\u0628\u0631\u06CC</label><input id="u-name" placeholder="ali" autocomplete="off"></div>
          <div><label class="fld">\u062D\u062C\u0645 (GB)</label><input id="u-gb" type="number" min="0" step="0.5" placeholder="\u06F0"></div>
          <div><label class="fld">\u0627\u0639\u062A\u0628\u0627\u0631 (\u0631\u0648\u0632)</label><input id="u-days" type="number" min="0" step="1" placeholder="\u06F0"></div>
          <div><button onclick="addUser(this)"><svg class="ic"><use href="#i-plus"/></svg>\u0627\u0641\u0632\u0648\u062F\u0646</button></div>
        </div>
        <details style="margin-top:10px">
          <summary class="muted" style="cursor:pointer;padding:6px 0">\u062A\u0646\u0638\u06CC\u0645\u0627\u062A \u067E\u06CC\u0634\u0631\u0641\u062A\u0647</summary>
          <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-top:8px">
            <div><label class="fld">\u067E\u0631\u0648\u062A\u06A9\u0644</label><select id="u-proto" style="width:100%">
              <option value="both">VLESS + Trojan</option><option value="vless">VLESS</option><option value="trojan">Trojan</option>
            </select></div>
            <div><label class="fld">\u0633\u0642\u0641 \u062F\u0631\u062E\u0648\u0627\u0633\u062A</label><input id="u-req" type="number" min="0" placeholder="\u06F0"></div>
            <div><label class="fld">\u062F\u0633\u062A\u06AF\u0627\u0647 \u0647\u0645\u0632\u0645\u0627\u0646</label><input id="u-ip-limit" type="number" min="0" placeholder="\u06F0"></div>
            <div><label class="fld">\u0627\u0633\u062A\u062E\u0631 \u0622\u06CC\u200C\u067E\u06CC</label><select id="u-pool" style="width:100%"><option value="">\u067E\u06CC\u0634\u200C\u0641\u0631\u0636</option></select></div>
          </div>
          <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-top:10px">
            <div><label class="fld">\u0631\u06CC\u0633\u062A \u062D\u062C\u0645 (\u0631\u0648\u0632)</label><input id="u-reset-vol" type="number" min="0" placeholder="\u06F0"></div>
            <div><label class="fld">\u0631\u06CC\u0633\u062A \u062F\u0631\u062E\u0648\u0627\u0633\u062A (\u0631\u0648\u0632)</label><input id="u-reset-req" type="number" min="0" placeholder="\u06F0"></div>
            <div style="grid-column:span 2"><label class="fld">\u06CC\u0627\u062F\u062F\u0627\u0634\u062A</label><input id="u-note" placeholder="\u0627\u062E\u062A\u06CC\u0627\u0631\u06CC"></div>
          </div>
          <label class="muted" style="display:flex;align-items:center;gap:6px;margin-top:10px">
            <input id="u-start" type="checkbox"> \u0634\u0631\u0648\u0639 \u0627\u0639\u062A\u0628\u0627\u0631 \u0627\u0632 \u0627\u0648\u0644\u06CC\u0646 \u0627\u062A\u0635\u0627\u0644
          </label>
        </details>
        <p class="err" id="u-msg"></p>
      </div>

      <div class="card">
        <div class="card-head">
          <h2>\u0644\u06CC\u0633\u062A \u06A9\u0627\u0631\u0628\u0631\u0627\u0646</h2>
          <div class="search"><svg class="ic"><use href="#i-search"/></svg><input id="q" placeholder="\u062C\u0633\u062A\u200C\u0648\u062C\u0648\u06CC \u0646\u0627\u0645\u2026" oninput="onSearch(this.value)"></div>
        </div>
        <div class="tbl-wrap">
          <table>
            <thead><tr><th>\u06A9\u0627\u0631\u0628\u0631</th><th>\u0648\u0636\u0639\u06CC\u062A</th><th>\u0645\u0635\u0631\u0641</th><th>\u0627\u0646\u0642\u0636\u0627</th><th>\u0622\u06CC\u200C\u067E\u06CC</th><th style="width:150px">\u0639\u0645\u0644\u06CC\u0627\u062A</th></tr></thead>
            <tbody id="rows"></tbody>
          </table>
        </div>
        <div id="u-empty"></div>
      </div>
    </section>

    <!-- \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 \u0622\u06CC\u200C\u067E\u06CC \u062A\u0645\u06CC\u0632 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 -->
    <section id="pane-ips" class="hidden">
      <div class="head"><div><h1>\u0622\u06CC\u200C\u067E\u06CC \u062A\u0645\u06CC\u0632</h1><p>\u0644\u06CC\u0646\u06A9\u200C\u0647\u0627 \u0628\u0647 \u0627\u06CC\u0646 \u0622\u06CC\u200C\u067E\u06CC\u200C\u0647\u0627 \u0648\u0635\u0644 \u0645\u06CC\u200C\u0634\u0648\u0646\u062F\u060C \u0648\u0644\u06CC SNI \u0648 Host \u0647\u0645\u0627\u0646 \u062F\u0627\u0645\u0646\u0647\u200C\u06CC \u0646\u0648\u062F \u0645\u06CC\u200C\u0645\u0627\u0646\u0646\u062F.</p></div></div>

      <div class="card">
        <h2><svg class="ic" style="color:var(--acc)"><use href="#i-plus"/></svg>\u0627\u0641\u0632\u0648\u062F\u0646 \u0627\u0633\u062A\u062E\u0631</h2>
        <div style="display:grid;grid-template-columns:220px 180px 1fr;gap:10px;align-items:end">
          <div><label class="fld">\u0646\u0627\u0645 \u0627\u0633\u062A\u062E\u0631</label><input id="ip-name" placeholder="\u0647\u0645\u0631\u0627\u0647 \u0627\u0648\u0644" autocomplete="off"></div>
          <div><label class="fld">\u0627\u067E\u0631\u0627\u062A\u0648\u0631</label><select id="ip-op" style="width:100%">
            <option value="any">\u0647\u0645\u0647</option><option value="mci">\u0647\u0645\u0631\u0627\u0647 \u0627\u0648\u0644</option>
            <option value="irancell">\u0627\u06CC\u0631\u0627\u0646\u0633\u0644</option><option value="mci_fixed">\u0647\u0645\u0631\u0627\u0647 \u0627\u0648\u0644 (\u062B\u0627\u0628\u062A)</option>
          </select></div>
          <div><label class="fld">\u0622\u06CC\u200C\u067E\u06CC\u200C\u0647\u0627 (\u0647\u0631 \u062E\u0637 \u06CC\u06A9\u06CC\u061B <code>#</code> \u0628\u0631\u0627\u06CC \u062A\u0648\u0636\u06CC\u062D)</label><textarea id="ip-list" placeholder="104.16.0.1&#10;104.17.0.1#cdg" style="min-height:80px;direction:ltr;text-align:left"></textarea></div>
        </div>
        <div style="display:flex;gap:8px;margin-top:12px">
          <button onclick="addPool(this)"><svg class="ic"><use href="#i-plus"/></svg>\u0627\u0641\u0632\u0648\u062F\u0646 \u0627\u0633\u062A\u062E\u0631</button>
          <button class="ghost" onclick="copyTest('')">\u06A9\u067E\u06CC \u062F\u0633\u062A\u0648\u0631 \u062A\u0633\u062A</button>
        </div>
        <p class="err" id="ip-msg"></p>
      </div>

      <div class="card">
        <div class="card-head"><h2>\u0627\u0633\u062A\u062E\u0631\u0647\u0627</h2><button class="ghost sm" onclick="loadIps(true)"><svg class="ic"><use href="#i-refresh"/></svg>\u062A\u0627\u0632\u0647\u200C\u0633\u0627\u0632\u06CC</button></div>
        <div class="list" id="pool-list"></div>
        <div id="ip-empty"></div>
      </div>
    </section>

    <!-- \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 \u06A9\u0627\u0646\u0641\u06CC\u06AF\u200C\u0647\u0627 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 -->
    <section id="pane-configs" class="hidden">
      <div class="head">
        <div><h1>\u06A9\u0627\u0646\u0641\u06CC\u06AF\u200C\u0647\u0627\u06CC \u0627\u067E</h1><p>\u0647\u0631 \u06A9\u0627\u0646\u0641\u06CC\u06AF \u0627\u06CC\u0646\u062C\u0627\u060C \u062F\u0631 \u0635\u0641\u062D\u0647\u200C\u06CC \u0627\u0635\u0644\u06CC \u0647\u0645\u0647\u200C\u06CC \u06A9\u0627\u0631\u0628\u0631\u0627\u0646 \u0627\u067E \u0638\u0627\u0647\u0631 \u0645\u06CC\u200C\u0634\u0648\u062F.</p></div>
        <div class="actions"><button class="ghost sm" onclick="publish(this)"><svg class="ic"><use href="#i-refresh"/></svg>\u0627\u0646\u062A\u0634\u0627\u0631 \u0645\u062C\u062F\u062F</button></div>
      </div>

      <div class="card">
        <h2><svg class="ic" style="color:var(--acc)"><use href="#i-plus"/></svg>\u0627\u0641\u0632\u0648\u062F\u0646 \u06A9\u0627\u0646\u0641\u06CC\u06AF</h2>
        <div style="display:grid;grid-template-columns:200px 1fr auto;gap:10px;align-items:end" id="addCfgRow">
          <div><label class="fld">\u0646\u0627\u0645</label><input id="c-name" placeholder="\u0622\u0644\u0645\u0627\u0646 - \u0641\u0631\u0627\u0646\u06A9\u0641\u0648\u0631\u062A" autocomplete="off"></div>
          <div><label class="fld">\u0644\u06CC\u0646\u06A9</label><input id="c-uri" placeholder="vless://\u2026" style="direction:ltr;text-align:left;font-family:ui-monospace,monospace;font-size:12.5px"></div>
          <div><button onclick="addConfig(this)"><svg class="ic"><use href="#i-plus"/></svg>\u0627\u0641\u0632\u0648\u062F\u0646</button></div>
        </div>
        <p class="err" id="c-msg"></p>
        <details style="margin-top:6px">
          <summary class="muted" style="cursor:pointer;padding:6px 0">\u0627\u0641\u0632\u0648\u062F\u0646 \u06AF\u0631\u0648\u0647\u06CC (\u0647\u0631 \u062E\u0637 \u06CC\u06A9 \u0644\u06CC\u0646\u06A9)</summary>
          <textarea id="c-bulk" placeholder="vless://...
vless://...
trojan://..." style="margin-top:10px"></textarea>
          <div style="display:flex;align-items:center;gap:10px;margin-top:10px">
            <button class="ghost" onclick="addBulk(this)">\u0627\u0641\u0632\u0648\u062F\u0646 \u0647\u0645\u0647</button>
            <span class="muted">\u0646\u0627\u0645 \u0647\u0631 \u06A9\u0627\u0646\u0641\u06CC\u06AF \u0627\u0632 <code>#name</code> \u0622\u062E\u0631 \u0644\u06CC\u0646\u06A9 \u0628\u0631\u062F\u0627\u0634\u062A\u0647 \u0645\u06CC\u200C\u0634\u0648\u062F.</span>
          </div>
        </details>
      </div>

      <div class="card">
        <div class="card-head"><h2>\u06A9\u0627\u0646\u0641\u06CC\u06AF\u200C\u0647\u0627</h2><button class="ghost sm" onclick="loadConfigs(true)"><svg class="ic"><use href="#i-refresh"/></svg>\u062A\u0627\u0632\u0647\u200C\u0633\u0627\u0632\u06CC</button></div>
        <div class="list" id="cfg-list"></div>
        <div id="c-empty"></div>
      </div>
    </section>

    <!-- \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 \u0627\u062A\u0635\u0627\u0644 \u0627\u067E \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 -->
    <section id="pane-app" class="hidden">
      <div class="head"><div><h1>\u0627\u062A\u0635\u0627\u0644 \u0627\u067E</h1><p>\u0627\u06CC\u0646 \u062F\u0648 \u062E\u0637 \u0631\u0627 \u062F\u0631 <code>local.properties</code> \u067E\u0631\u0648\u0698\u0647 \u0628\u06AF\u0630\u0627\u0631 \u0648 \u06CC\u06A9\u200C\u0628\u0627\u0631 \u0628\u06CC\u0644\u062F \u06A9\u0646.</p></div></div>

      <div class="card">
        <h2><svg class="ic" style="color:var(--acc)"><use href="#i-link"/></svg>\u062E\u0637\u0648\u0637 \u0628\u06CC\u0644\u062F</h2>
        <pre id="rc-lines">\u2026</pre>
        <div style="display:flex;gap:8px;margin-top:12px">
          <button class="sm" onclick="copyEl('rc-lines')"><svg class="ic"><use href="#i-copy"/></svg>\u06A9\u067E\u06CC \u0647\u0631 \u062F\u0648 \u062E\u0637</button>
          <button class="ghost sm" onclick="copyEl('rc-url')"><svg class="ic"><use href="#i-copy"/></svg>\u06A9\u067E\u06CC \u0622\u062F\u0631\u0633</button>
        </div>
      </div>

      <div class="kv">
        <div class="stat"><div class="lbl">\u0622\u062F\u0631\u0633 RC</div><b id="rc-url">\u2014</b></div>
        <div class="stat"><div class="lbl">\u0646\u0633\u062E\u0647\u200C\u06CC \u0641\u0639\u0627\u0644</div><b id="rc-version">\u2014</b></div>
        <div class="stat"><div class="lbl">\u06A9\u0627\u0646\u0641\u06CC\u06AF \u0641\u0639\u0627\u0644</div><b id="rc-count">\u2014</b></div>
      </div>

      <div class="card">
        <h2>\u06A9\u0644\u06CC\u062F \u0639\u0645\u0648\u0645\u06CC \u0627\u0645\u0636\u0627</h2>
        <p class="muted">\u062F\u0627\u062E\u0644 \u062E\u0648\u062F \u0627\u067E \u06A9\u0627\u0645\u067E\u0627\u06CC\u0644 \u0645\u06CC\u200C\u0634\u0648\u062F. \u0627\u06AF\u0631 \u06A9\u0633\u06CC \u0633\u0631\u0648\u0631 \u0631\u0627 \u0639\u0648\u0636 \u06A9\u0646\u062F\u060C \u0627\u0645\u0636\u0627 \u0646\u0645\u06CC\u200C\u062E\u0648\u0627\u0646\u062F \u0648 \u06A9\u0627\u0646\u0641\u06CC\u06AF \u0631\u062F \u0645\u06CC\u200C\u0634\u0648\u062F.</p>
        <pre id="rc-key">\u2026</pre>
        <div style="margin-top:12px"><button class="sm" onclick="copyEl('rc-key')"><svg class="ic"><use href="#i-copy"/></svg>\u06A9\u067E\u06CC \u06A9\u0644\u06CC\u062F</button></div>
      </div>

      <div class="card">
        <h2><svg class="ic" style="color:var(--acc)"><use href="#i-link"/></svg>\u067E\u06CC\u0634\u200C\u0646\u0645\u0627\u06CC\u0634 \u0644\u06CC\u0646\u06A9 \u0627\u0634\u062A\u0631\u0627\u06A9</h2>
        <p class="muted">\u0647\u0645\u0627\u0646 \u0644\u06CC\u0646\u06A9\u06CC \u06A9\u0647 \u06A9\u0644\u0627\u06CC\u0646\u062A \u0645\u06CC\u200C\u06AF\u06CC\u0631\u062F\u060C \u062F\u0631 \u067E\u0646\u062C \u0642\u0627\u0644\u0628. \u067E\u06CC\u0634\u200C\u0641\u0631\u0636 base64 \u0627\u0633\u062A.</p>
        <div style="display:grid;grid-template-columns:1fr 160px auto auto;gap:10px;align-items:end">
          <div><label class="fld">\u06A9\u0627\u0631\u0628\u0631</label><select id="sub-user" style="width:100%"></select></div>
          <div><label class="fld">\u0642\u0627\u0644\u0628</label><select id="sub-format" style="width:100%">
            <option value="base64">base64</option><option value="plain">plain</option>
            <option value="clash">Clash</option><option value="singbox">sing-box</option><option value="json">JSON</option>
          </select></div>
          <div><button onclick="previewSub()">\u0646\u0645\u0627\u06CC\u0634</button></div>
          <div><button class="ghost" onclick="copyEl('sub-preview')">\u06A9\u067E\u06CC</button></div>
        </div>
        <pre id="sub-preview" style="max-height:280px;overflow:auto;direction:ltr;text-align:left">\u2026</pre>
      </div>
    </section>

    <!-- \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 \u062A\u0646\u0638\u06CC\u0645\u0627\u062A \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 -->
    <section id="pane-settings" class="hidden">
      <div class="head"><div><h1>\u062A\u0646\u0638\u06CC\u0645\u0627\u062A</h1><p>\u0622\u062F\u0631\u0633 \u0646\u0648\u062F \u0648 \u0631\u0645\u0632 \u067E\u0646\u0644.</p></div></div>

      <div class="card">
        <h2><svg class="ic" style="color:var(--acc)"><use href="#i-link"/></svg>\u0622\u062F\u0631\u0633 \u0646\u0648\u062F</h2>
        <p class="muted">\u06A9\u0627\u0646\u0641\u06CC\u06AF\u200C\u0647\u0627\u06CC\u06CC \u06A9\u0647 \u0628\u0647 \u06A9\u0627\u0631\u0628\u0631\u0627\u0646 \u062F\u0627\u062F\u0647 \u0645\u06CC\u200C\u0634\u0648\u062F \u0628\u0647 \u0627\u06CC\u0646 \u062F\u0627\u0645\u0646\u0647 \u0648\u0635\u0644 \u0645\u06CC\u200C\u0634\u0648\u0646\u062F. \u0628\u062F\u0648\u0646 <code>https://</code> \u0648 \u0628\u062F\u0648\u0646 \u067E\u0648\u0631\u062A.</p>
        <div style="display:grid;grid-template-columns:1fr auto;gap:10px;align-items:end">
          <div><label class="fld">\u062F\u0627\u0645\u0646\u0647\u200C\u06CC \u0646\u0648\u062F</label><input id="node-host" placeholder="node.example.com" style="direction:ltr;text-align:left;font-family:ui-monospace,monospace"></div>
          <div><button onclick="saveNode(this)">\u0630\u062E\u06CC\u0631\u0647</button></div>
        </div>
        <p class="err" id="n-msg"></p>
      </div>

      <div class="card">
        <h2>\u062A\u063A\u06CC\u06CC\u0631 \u0631\u0645\u0632 \u067E\u0646\u0644</h2>
        <p class="muted">\u0628\u0639\u062F \u0627\u0632 \u062A\u063A\u06CC\u06CC\u0631\u060C \u0647\u0645\u0647\u200C\u06CC \u0646\u0634\u0633\u062A\u200C\u0647\u0627\u06CC \u0628\u0627\u0632 \u0628\u0627\u0637\u0644 \u0645\u06CC\u200C\u0634\u0648\u0646\u062F.</p>
        <div style="display:grid;grid-template-columns:1fr 1fr auto;gap:10px;align-items:end">
          <div><label class="fld">\u0631\u0645\u0632 \u0641\u0639\u0644\u06CC</label><input id="pw-cur" type="password" autocomplete="current-password"></div>
          <div><label class="fld">\u0631\u0645\u0632 \u062C\u062F\u06CC\u062F</label><input id="pw-new" type="password" autocomplete="new-password"></div>
          <div><button onclick="changePassword(this)">\u062A\u063A\u06CC\u06CC\u0631</button></div>
        </div>
        <p class="err" id="pw-msg"></p>
      </div>
    </section>
  </main>
</div>

<dialog id="userDlg">
  <div class="dlg-head"><h2>\u0648\u06CC\u0631\u0627\u06CC\u0634 \u06A9\u0627\u0631\u0628\u0631</h2><button class="icon" onclick="closeDlg('userDlg')"><svg class="ic" style="transform:rotate(45deg)"><use href="#i-plus"/></svg></button></div>
  <div class="dlg-body">
    <div><label class="fld">\u0646\u0627\u0645</label><input id="e-name" autocomplete="off"></div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
      <div><label class="fld">\u062D\u062C\u0645 (GB\u060C \u06F0 = \u0646\u0627\u0645\u062D\u062F\u0648\u062F)</label><input id="e-gb" type="number" min="0" step="0.5"></div>
      <div><label class="fld">\u0631\u0648\u0632 \u0627\u0639\u062A\u0628\u0627\u0631 (\u06F0 = \u0628\u06CC\u200C\u067E\u0627\u06CC\u0627\u0646)</label><input id="e-days" type="number" min="0" step="1"></div>
      <div><label class="fld">\u0633\u0642\u0641 \u062F\u0631\u062E\u0648\u0627\u0633\u062A</label><input id="e-req" type="number" min="0"></div>
      <div><label class="fld">\u062F\u0633\u062A\u06AF\u0627\u0647 \u0647\u0645\u0632\u0645\u0627\u0646</label><input id="e-ip-limit" type="number" min="0"></div>
      <div><label class="fld">\u0631\u06CC\u0633\u062A \u062D\u062C\u0645 (\u0631\u0648\u0632)</label><input id="e-reset-vol" type="number" min="0"></div>
      <div><label class="fld">\u0631\u06CC\u0633\u062A \u062F\u0631\u062E\u0648\u0627\u0633\u062A (\u0631\u0648\u0632)</label><input id="e-reset-req" type="number" min="0"></div>
      <div><label class="fld">\u067E\u0631\u0648\u062A\u06A9\u0644</label><select id="e-proto" style="width:100%">
        <option value="both">VLESS + Trojan</option><option value="vless">VLESS</option><option value="trojan">Trojan</option>
      </select></div>
      <div><label class="fld">\u0627\u0633\u062A\u062E\u0631 \u0622\u06CC\u200C\u067E\u06CC</label><select id="e-pool" style="width:100%"><option value="">\u067E\u06CC\u0634\u200C\u0641\u0631\u0636</option></select></div>
    </div>
    <div><label class="fld">\u06CC\u0627\u062F\u062F\u0627\u0634\u062A</label><input id="e-note" placeholder="\u0627\u062E\u062A\u06CC\u0627\u0631\u06CC"></div>
    <p class="err" id="e-msg"></p>
  </div>
  <div class="dlg-foot"><button onclick="saveEdit(this)">\u0630\u062E\u06CC\u0631\u0647</button><button class="ghost" onclick="closeDlg('userDlg')">\u0627\u0646\u0635\u0631\u0627\u0641</button></div>
</dialog>

<dialog id="cfgDlg">
  <div class="dlg-head"><h2>\u0648\u06CC\u0631\u0627\u06CC\u0634 \u06A9\u0627\u0646\u0641\u06CC\u06AF</h2><button class="icon" onclick="closeDlg('cfgDlg')"><svg class="ic" style="transform:rotate(45deg)"><use href="#i-plus"/></svg></button></div>
  <div class="dlg-body">
    <div><label class="fld">\u0646\u0627\u0645</label><input id="k-name" autocomplete="off"></div>
    <div><label class="fld">\u0644\u06CC\u0646\u06A9</label><textarea id="k-uri" style="min-height:88px"></textarea></div>
    <p class="err" id="k-msg"></p>
  </div>
  <div class="dlg-foot"><button onclick="saveCfg(this)">\u0630\u062E\u06CC\u0631\u0647</button><button class="ghost" onclick="closeDlg('cfgDlg')">\u0627\u0646\u0635\u0631\u0627\u0641</button></div>
</dialog>

<div id="toast" class="toast"></div>

<script>
var S = { users: [], configs: [], pools: [], rc: null, q: '', nodeHost: '', editUser: null, editCfg: null, editingUserSnapshot: null };

function el(id){ return document.getElementById(id); }
function esc(s){ return String(s == null ? '' : s).replace(/[&<>"']/g, function(c){ return ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]; }); }
function onSearch(v){ S.q = v; renderUsers(); }

function fa(n){ return Number(n || 0).toLocaleString('fa-IR'); }
function size(bytes){
  var n = Number(bytes || 0);
  if (n <= 0) return '\u06F0';
  var u = ['B','KB','MB','GB','TB'], i = 0;
  while (n >= 1024 && i < u.length - 1) { n = n / 1024; i++; }
  return (i === 0 ? n : n.toFixed(n >= 100 ? 0 : n >= 10 ? 1 : 2)) + ' ' + u[i];
}
function limit(bytes){ return Number(bytes) > 0 ? size(bytes) : '\u0646\u0627\u0645\u062D\u062F\u0648\u062F'; }
function faDate(ts){
  if (!ts) return '\u0628\u06CC\u200C\u067E\u0627\u06CC\u0627\u0646';
  try { return new Date(ts).toLocaleDateString('fa-IR'); } catch (e) { return '\u2014'; }
}
function toast(msg, isErr){
  var t = el('toast');
  t.textContent = msg;
  t.className = 'toast show' + (isErr ? ' err' : '');
  clearTimeout(t._h);
  t._h = setTimeout(function(){ t.className = 'toast'; }, 2400);
}
function busy(btn, on){
  if (!btn) return;
  btn.disabled = !!on;
}
function togglePass(id, btn){
  var f = el(id);
  f.type = f.type === 'password' ? 'text' : 'password';
}
function closeDlg(id){ el(id).close(); }

async function api(path, opt){
  var res;
  try {
    res = await fetch(path, Object.assign({ credentials: 'include', headers: { 'content-type': 'application/json' } }, opt || {}));
  } catch (e) {
    return { ok: false, status: 0, data: { error: 'network' } };
  }
  var data = {};
  try { data = await res.json(); } catch (e) {}
  if (res.status === 401 && path !== '/api/state' && path !== '/api/login' && path !== '/api/setup') showAuth('login');
  return { ok: res.ok, status: res.status, data: data };
}

/* \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 auth \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 */
async function boot(){
  el('authHost').textContent = location.host;
  el('host').textContent = location.host;
  var st = await api('/api/state');
  if (!st.data.hasPassword) { showAuth('setup'); return; }
  if (st.data.authed) { showApp(); return; }
  showAuth('login');
}
function showAuth(mode){
  el('app').classList.add('hidden');
  el('auth').classList.remove('hidden');
  el('setupBox').classList.toggle('hidden', mode !== 'setup');
  el('loginBox').classList.toggle('hidden', mode !== 'login');
  el('authMsg').textContent = '';
  setTimeout(function(){ var f = mode === 'setup' ? el('spass') : el('lpass'); if (f) f.focus(); }, 60);
}
async function setup(){
  var p = el('spass').value;
  if (p.length < 8) { el('authMsg').textContent = '\u0631\u0645\u0632 \u062D\u062F\u0627\u0642\u0644 \u06F8 \u06A9\u0627\u0631\u0627\u06A9\u062A\u0631 \u0628\u0627\u0634\u062F.'; return; }
  busy(el('setupBtn'), true);
  var r = await api('/api/setup', { method: 'POST', body: JSON.stringify({ password: p }) });
  busy(el('setupBtn'), false);
  if (r.ok) { showApp(); } else { el('authMsg').textContent = r.data.error === 'already_set' ? '\u0631\u0645\u0632 \u0642\u0628\u0644\u0627\u064B \u0633\u0627\u062E\u062A\u0647 \u0634\u062F\u0647.' : '\u062E\u0637\u0627: ' + (r.data.error || r.status); }
}
async function login(){
  var p = el('lpass').value;
  busy(el('loginBtn'), true);
  var r = await api('/api/login', { method: 'POST', body: JSON.stringify({ password: p }) });
  busy(el('loginBtn'), false);
  if (r.ok) { showApp(); } else { el('authMsg').textContent = '\u0631\u0645\u0632 \u0627\u0634\u062A\u0628\u0627\u0647 \u0627\u0633\u062A.'; el('lpass').select(); }
}
async function logout(){
  await api('/api/logout', { method: 'POST' });
  location.reload();
}
function showApp(){
  el('auth').classList.add('hidden');
  el('app').classList.remove('hidden');
  tab('users');
}
function tab(name){
  ['users','ips','configs','app','settings'].forEach(function(p){
    var pane = el('pane-' + p);
    if (pane) pane.classList.toggle('hidden', p !== name);
  });
  var navs = document.querySelectorAll('.nav');
  for (var i = 0; i < navs.length; i++) navs[i].classList.toggle('active', navs[i].getAttribute('data-tab') === name);
  if (name === 'app') loadRc();
  if (name === 'settings') loadSettings();
  if (name === 'users') { loadUsers(); loadIps(); }
  if (name === 'ips') loadIps();
  if (name === 'configs') loadConfigs();
}

/* \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 users \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 */
async function loadUsers(manual){
  var btn = manual ? el('refUsers') : null;
  busy(btn, true);
  var r = await api('/api/users');
  busy(btn, false);
  if (!r.ok) { toast('\u062F\u0631\u06CC\u0627\u0641\u062A \u06A9\u0627\u0631\u0628\u0631\u0627\u0646 \u0646\u0634\u062F', true); return; }
  S.users = r.data.users || [];
  renderUsers();
}
function blocked(u){
  return !u.enabled || (u.expires_at && u.expires_at < Date.now()) ||
    (u.limit_bytes > 0 && u.used_bytes >= u.limit_bytes) ||
    (u.limit_req > 0 && u.used_req >= u.limit_req);
}
function renderUsers(){
  var q = (S.q || '').trim().toLowerCase();
  var list = S.users.filter(function(u){ return !q || String(u.name).toLowerCase().indexOf(q) >= 0; });
  var used = 0, active = 0;
  S.users.forEach(function(u){ used += u.used_bytes || 0; if (!blocked(u)) active++; });

  var html = '';
  list.forEach(function(u){
    var b = blocked(u);
    var pct = u.limit_bytes > 0 ? Math.min(100, (u.used_bytes / u.limit_bytes) * 100) : 0;
    var barCls = pct >= 100 ? 'bad' : pct >= 85 ? 'warn' : '';
    var expired = u.expires_at && u.expires_at < Date.now();
    html += '<tr>'
      + '<td><div class="who"><div class="av">' + esc(String(u.name).slice(0, 2)) + '</div><div>'
        + '<div class="nm">' + esc(u.name) + '</div>'
        + '<div class="sub">' + esc(String(u.uuid).slice(0, 8)) + '\u2026</div></div></div></td>'
      + '<td><span class="pill ' + (b ? 'off' : 'on') + '"><i></i>' + (b ? (expired ? '\u0645\u0646\u0642\u0636\u06CC' : '\u063A\u06CC\u0631\u0641\u0639\u0627\u0644') : '\u0641\u0639\u0627\u0644') + '</span></td>'
      + '<td><div class="num">' + size(u.used_bytes) + ' <span class="muted">/ ' + limit(u.limit_bytes) + '</span></div>'
        + (u.limit_bytes > 0 ? '<div class="bar ' + barCls + '"><i style="width:' + pct.toFixed(1) + '%"></i></div>' : '') + '</td>'
      + '<td class="muted num">' + faDate(u.expires_at) + '</td>'
      + '<td class="mono">' + (u.pinned_ip
          ? esc(u.pinned_ip) + '<br><button class="ghost sm" onclick="releaseIp(' + u.id + ')">\u0622\u0632\u0627\u062F\u0633\u0627\u0632\u06CC</button>'
          : '\u2014') + '</td>'
      + '<td><div class="ops">'
        + '<button class="icon" title="\u0648\u06CC\u0631\u0627\u06CC\u0634" onclick="openUser(' + u.id + ')"><svg class="ic"><use href="#i-edit"/></svg></button>'
        + '<button class="icon" title="' + (u.enabled ? '\u062E\u0627\u0645\u0648\u0634 \u06A9\u0631\u062F\u0646' : '\u0631\u0648\u0634\u0646 \u06A9\u0631\u062F\u0646') + '" onclick="toggleUser(' + u.id + ')"><svg class="ic"><use href="#i-power"/></svg></button>'
        + '<button class="icon" title="\u06A9\u067E\u06CC \u0644\u06CC\u0646\u06A9 \u0633\u0627\u0628" onclick="copySub(' + u.id + ')"><svg class="ic"><use href="#i-copy"/></svg></button>'
        + '<button class="icon" title="\u0635\u0641\u0631 \u06A9\u0631\u062F\u0646 \u0645\u0635\u0631\u0641" onclick="resetUser(' + u.id + ')"><svg class="ic"><use href="#i-refresh"/></svg></button>'
        + '<button class="icon" title="\u062D\u0630\u0641" style="color:var(--bad)" onclick="delUser(' + u.id + ')"><svg class="ic"><use href="#i-trash"/></svg></button>'
        + '</div></td></tr>';
  });
  el('rows').innerHTML = html;
  el('u-empty').innerHTML = list.length ? '' :
    '<div class="empty"><svg class="ic"><use href="#i-users"/></svg><b>' + (q ? '\u0686\u06CC\u0632\u06CC \u067E\u06CC\u062F\u0627 \u0646\u0634\u062F' : '\u0647\u0646\u0648\u0632 \u06A9\u0627\u0631\u0628\u0631\u06CC \u0646\u0633\u0627\u062E\u062A\u0647\u200C\u0627\u06CC') + '</b>'
    + (q ? '\u0639\u0628\u0627\u0631\u062A \u062F\u06CC\u06AF\u0631\u06CC \u0631\u0627 \u062C\u0633\u062A\u200C\u0648\u062C\u0648 \u06A9\u0646.' : '\u0627\u0632 \u0641\u0631\u0645 \u0628\u0627\u0644\u0627 \u0627\u0648\u0644\u06CC\u0646 \u06A9\u0627\u0631\u0628\u0631 \u0631\u0627 \u0628\u0633\u0627\u0632.') + '</div>';
  var sel = el('sub-user');
  if (sel) {
    var prev = sel.value;
    sel.innerHTML = S.users.map(function(u){ return '<option value="' + u.id + '">' + esc(u.name) + '</option>'; }).join('');
    if (prev) sel.value = prev;
  }
  el('s-users').textContent = fa(S.users.length);
  el('s-active').textContent = fa(active);
  el('s-used').textContent = size(used);
}

async function addUser(btn){
  var name = el('u-name').value.trim();
  var gbv = el('u-gb').value, daysv = el('u-days').value;
  if (!/^[A-Za-z0-9_.-]{2,32}$/.test(name)) { el('u-msg').textContent = '\u0646\u0627\u0645: \u06F2 \u062A\u0627 \u06F3\u06F2 \u06A9\u0627\u0631\u0627\u06A9\u062A\u0631\u060C \u0641\u0642\u0637 \u062D\u0631\u0648\u0641 \u0627\u0646\u06AF\u0644\u06CC\u0633\u06CC\u060C \u0639\u062F\u062F\u060C \u0646\u0642\u0637\u0647\u060C \u062E\u0637 \u062A\u06CC\u0631\u0647.'; return; }
  el('u-msg').textContent = '';
  busy(btn, true);
  var body = {
    name: name, limit_gb: Number(gbv || 0), days: Number(daysv || 0),
    protocol: el('u-proto').value,
    limit_req: Number(el('u-req').value || 0),
    ip_limit: Number(el('u-ip-limit').value || 0),
    ip_pool_id: el('u-pool').value ? Number(el('u-pool').value) : null,
    start_on_first_connect: el('u-start').checked,
    auto_reset_vol_days: Number(el('u-reset-vol').value || 0),
    auto_reset_req_days: Number(el('u-reset-req').value || 0),
    note: el('u-note').value
  };
  var r = await api('/api/users', { method: 'POST', body: JSON.stringify(body) });
  busy(btn, false);
  if (r.ok) {
    ['u-name','u-gb','u-days','u-req','u-ip-limit','u-reset-vol','u-reset-req','u-note'].forEach(function(id){ el(id).value = ''; });
    el('u-start').checked = false;
    toast('\u06A9\u0627\u0631\u0628\u0631 \u0633\u0627\u062E\u062A\u0647 \u0634\u062F');
    loadUsers();
  } else {
    el('u-msg').textContent = r.data.error === 'duplicate' ? '\u0627\u06CC\u0646 \u0646\u0627\u0645 \u0642\u0628\u0644\u0627\u064B \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u0634\u062F\u0647.' : r.data.error === 'bad_name' ? '\u0646\u0627\u0645 \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A.' : '\u062E\u0637\u0627: ' + (r.data.error || r.status);
  }
}

function openUser(id){
  var u = S.users.filter(function(x){ return x.id === id; })[0];
  if (!u) return;
  S.editUser = id;
  S.editingUserSnapshot = { gb: u.limit_bytes, days: u.expires_at ? Math.max(0, Math.round((u.expires_at - Date.now()) / 86400000)) : 0 };
  el('e-name').value = u.name;
  el('e-gb').value = u.limit_bytes ? +(u.limit_bytes / 1073741824).toFixed(2) : 0;
  el('e-days').value = S.editingUserSnapshot.days;
  el('e-req').value = u.limit_req || 0;
  el('e-ip-limit').value = u.ip_limit || 0;
  el('e-reset-vol').value = u.auto_reset_vol_days || 0;
  el('e-reset-req').value = u.auto_reset_req_days || 0;
  el('e-proto').value = u.connection_type || 'both';
  el('e-pool').value = u.ip_pool_id ? String(u.ip_pool_id) : '';
  el('e-note').value = u.note || '';
  el('e-msg').textContent = '';
  el('userDlg').showModal();
}

async function saveEdit(btn){
  var name = el('e-name').value.trim();
  var gb = Number(el('e-gb').value || 0);
  var days = Number(el('e-days').value || 0);
  var body = {};
  // \u0641\u0642\u0637 \u0641\u06CC\u0644\u062F\u0647\u0627\u06CC\u06CC \u06A9\u0647 \u0639\u0648\u0636 \u0634\u062F\u0647\u200C\u0627\u0646\u062F \u0641\u0631\u0633\u062A\u0627\u062F\u0647 \u0645\u06CC\u200C\u0634\u0648\u0646\u062F\u061B \u0641\u0631\u0633\u062A\u0627\u062F\u0646 \u0631\u0648\u0632\u0650 \u06AF\u0631\u062F\u0634\u062F\u0647 \u062A\u0627\u0631\u06CC\u062E \u0627\u0646\u0642\u0636\u0627 \u0631\u0627 \u062C\u0627\u0628\u0647\u200C\u062C\u0627 \u0645\u06CC\u200C\u06A9\u0631\u062F.
  if (name) body.name = name;
  if (gb !== Number((S.editingUserSnapshot.gb / 1073741824).toFixed(2))) body.limit_gb = gb;
  if (days !== S.editingUserSnapshot.days) body.days = days;
  body.limit_req = Number(el('e-req').value || 0);
  body.ip_limit = Number(el('e-ip-limit').value || 0);
  body.auto_reset_vol_days = Number(el('e-reset-vol').value || 0);
  body.auto_reset_req_days = Number(el('e-reset-req').value || 0);
  body.protocol = el('e-proto').value;
  body.ip_pool_id = el('e-pool').value ? Number(el('e-pool').value) : null;
  body.note = el('e-note').value;
  if (!Object.keys(body).length) { el('userDlg').close(); return; }
  busy(btn, true);
  var r = await api('/api/users/' + S.editUser, { method: 'PATCH', body: JSON.stringify(body) });
  busy(btn, false);
  if (r.ok) { el('userDlg').close(); toast('\u0630\u062E\u06CC\u0631\u0647 \u0634\u062F'); loadUsers(); }
  else el('e-msg').textContent = r.data.error === 'duplicate' ? '\u0627\u06CC\u0646 \u0646\u0627\u0645 \u062A\u06A9\u0631\u0627\u0631\u06CC \u0627\u0633\u062A.' : '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}

async function toggleUser(id){
  var u = S.users.filter(function(x){ return x.id === id; })[0];
  if (!u) return;
  var r = await api('/api/users/' + id, { method: 'PATCH', body: JSON.stringify({ enabled: !u.enabled }) });
  if (r.ok) loadUsers(); else toast('\u062A\u063A\u06CC\u06CC\u0631 \u0648\u0636\u0639\u06CC\u062A \u0646\u0634\u062F', true);
}
async function resetUser(id){
  if (!confirm('\u0645\u0635\u0631\u0641 \u0627\u06CC\u0646 \u06A9\u0627\u0631\u0628\u0631 \u0635\u0641\u0631 \u0634\u0648\u062F\u061F')) return;
  var r = await api('/api/users/' + id + '/reset', { method: 'POST' });
  if (r.ok) { toast('\u0645\u0635\u0631\u0641 \u0635\u0641\u0631 \u0634\u062F'); loadUsers(); } else toast('\u0627\u0646\u062C\u0627\u0645 \u0646\u0634\u062F', true);
}
async function delUser(id){
  var u = S.users.filter(function(x){ return x.id === id; })[0];
  if (!confirm('\u06A9\u0627\u0631\u0628\u0631 \xAB' + (u ? u.name : id) + '\xBB \u062D\u0630\u0641 \u0634\u0648\u062F\u061F \u0627\u06CC\u0646 \u06A9\u0627\u0631 \u0628\u0631\u06AF\u0634\u062A\u200C\u067E\u0630\u06CC\u0631 \u0646\u06CC\u0633\u062A.')) return;
  var r = await api('/api/users/' + id, { method: 'DELETE' });
  if (r.ok) { toast('\u062D\u0630\u0641 \u0634\u062F'); loadUsers(); } else toast('\u062D\u0630\u0641 \u0646\u0634\u062F', true);
}
function copySub(id){
  var u = S.users.filter(function(x){ return x.id === id; })[0];
  if (!u) return;
  copyText(location.origin + '/sub/' + u.sub_token, '\u0644\u06CC\u0646\u06A9 \u0633\u0627\u0628 \u06A9\u067E\u06CC \u0634\u062F');
}

/* \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 configs \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 */
async function loadConfigs(manual){
  var r = await api('/api/configs');
  if (!r.ok) { toast('\u062F\u0631\u06CC\u0627\u0641\u062A \u06A9\u0627\u0646\u0641\u06CC\u06AF\u200C\u0647\u0627 \u0646\u0634\u062F', true); return; }
  S.configs = r.data.configs || [];
  renderConfigs();
  if (manual) toast('\u062A\u0627\u0632\u0647 \u0634\u062F');
}
function renderConfigs(){
  var html = '';
  S.configs.forEach(function(c, i){
    html += '<div class="item">'
      + '<div class="top">'
        + '<b>' + esc(c.name) + '</b>'
        + '<span class="pill ' + (c.enabled ? 'on' : 'off') + '"><i></i>' + (c.enabled ? '\u0641\u0639\u0627\u0644' : '\u062E\u0627\u0645\u0648\u0634') + '</span>'
        + '<span style="margin-right:auto"></span>'
        + '<button class="icon" title="\u0628\u0627\u0644\u0627" ' + (i === 0 ? 'disabled' : '') + ' onclick="moveCfg(' + c.id + ',-1)"><svg class="ic"><use href="#i-up"/></svg></button>'
        + '<button class="icon" title="\u067E\u0627\u06CC\u06CC\u0646" ' + (i === S.configs.length - 1 ? 'disabled' : '') + ' onclick="moveCfg(' + c.id + ',1)"><svg class="ic"><use href="#i-down"/></svg></button>'
        + '<button class="icon" title="\u0648\u06CC\u0631\u0627\u06CC\u0634" onclick="openCfg(' + c.id + ')"><svg class="ic"><use href="#i-edit"/></svg></button>'
        + '<button class="icon" title="' + (c.enabled ? '\u062E\u0627\u0645\u0648\u0634' : '\u0631\u0648\u0634\u0646') + '" onclick="toggleCfg(' + c.id + ')"><svg class="ic"><use href="#i-power"/></svg></button>'
        + '<button class="icon" title="\u062D\u0630\u0641" style="color:var(--bad)" onclick="delCfg(' + c.id + ')"><svg class="ic"><use href="#i-trash"/></svg></button>'
      + '</div>'
      + '<div class="mono">' + esc(c.uri) + '</div>'
      + '</div>';
  });
  el('cfg-list').innerHTML = html;
  el('c-empty').innerHTML = S.configs.length ? '' :
    '<div class="empty"><svg class="ic"><use href="#i-layers"/></svg><b>\u0647\u0646\u0648\u0632 \u06A9\u0627\u0646\u0641\u06CC\u06AF\u06CC \u0627\u0636\u0627\u0641\u0647 \u0646\u06A9\u0631\u062F\u0647\u200C\u0627\u06CC</b>\u0627\u0648\u0644\u06CC\u0646 \u0633\u0631\u0648\u0631 \u0631\u0627 \u0627\u0632 \u0641\u0631\u0645 \u0628\u0627\u0644\u0627 \u0627\u0636\u0627\u0641\u0647 \u06A9\u0646.</div>';
  var n = S.configs.filter(function(c){ return c.enabled; }).length;
  if (el('rc-count')) el('rc-count').textContent = fa(n);
}
async function addConfig(btn){
  var name = el('c-name').value.trim();
  var uri = el('c-uri').value.trim();
  if (!name) { el('c-msg').textContent = '\u0646\u0627\u0645 \u0631\u0627 \u0628\u0646\u0648\u06CC\u0633.'; return; }
  if (!/^(vless|vmess|trojan|ss|hysteria2?|tuic|wireguard):\\/\\//i.test(uri)) { el('c-msg').textContent = '\u0644\u06CC\u0646\u06A9 \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A.'; return; }
  el('c-msg').textContent = '';
  busy(btn, true);
  var r = await api('/api/configs', { method: 'POST', body: JSON.stringify({ name: name, uri: uri }) });
  busy(btn, false);
  if (r.ok) { el('c-name').value = ''; el('c-uri').value = ''; toast('\u0627\u0636\u0627\u0641\u0647 \u0634\u062F'); loadConfigs(); }
  else el('c-msg').textContent = r.data.error === 'uri_too_long' ? '\u0644\u06CC\u0646\u06A9 \u062E\u06CC\u0644\u06CC \u0628\u0644\u0646\u062F \u0627\u0633\u062A.' : '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}
async function addBulk(btn){
  var lines = (el('c-bulk').value || '').split(/\\s+/).filter(function(x){ return /:\\/\\//.test(x); });
  if (!lines.length) { toast('\u0644\u06CC\u0646\u06A9\u06CC \u067E\u06CC\u062F\u0627 \u0646\u0634\u062F', true); return; }
  busy(btn, true);
  var ok = 0, fail = 0;
  for (var i = 0; i < lines.length; i++) {
    var uri = lines[i];
    var name = '\u06A9\u0627\u0646\u0641\u06CC\u06AF ' + (S.configs.length + ok + 1);
    var hash = uri.split('#')[1];
    if (hash) { try { name = decodeURIComponent(hash); } catch (e) { name = hash; } }
    var r = await api('/api/configs', { method: 'POST', body: JSON.stringify({ name: name.slice(0, 60), uri: uri }) });
    if (r.ok) ok++; else fail++;
  }
  busy(btn, false);
  el('c-bulk').value = '';
  toast(ok + ' \u0627\u0636\u0627\u0641\u0647 \u0634\u062F' + (fail ? ' \xB7 ' + fail + ' \u0631\u062F \u0634\u062F' : ''), fail > 0 && ok === 0);
  loadConfigs();
}
function openCfg(id){
  var c = S.configs.filter(function(x){ return x.id === id; })[0];
  if (!c) return;
  S.editCfg = id;
  el('k-name').value = c.name;
  el('k-uri').value = c.uri;
  el('k-msg').textContent = '';
  el('cfgDlg').showModal();
}
async function saveCfg(btn){
  var name = el('k-name').value.trim();
  var uri = el('k-uri').value.trim();
  if (!name) { el('k-msg').textContent = '\u0646\u0627\u0645 \u0631\u0627 \u0628\u0646\u0648\u06CC\u0633.'; return; }
  if (!/^(vless|vmess|trojan|ss|hysteria2?|tuic|wireguard):\\/\\//i.test(uri)) { el('k-msg').textContent = '\u0644\u06CC\u0646\u06A9 \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A.'; return; }
  busy(btn, true);
  var r = await api('/api/configs/' + S.editCfg, { method: 'PATCH', body: JSON.stringify({ name: name, uri: uri }) });
  busy(btn, false);
  if (r.ok) { el('cfgDlg').close(); toast('\u0630\u062E\u06CC\u0631\u0647 \u0634\u062F'); loadConfigs(); }
  else el('k-msg').textContent = '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}
async function toggleCfg(id){
  var c = S.configs.filter(function(x){ return x.id === id; })[0];
  if (!c) return;
  var r = await api('/api/configs/' + id, { method: 'PATCH', body: JSON.stringify({ enabled: !c.enabled }) });
  if (r.ok) loadConfigs(); else toast('\u062A\u063A\u06CC\u06CC\u0631 \u0648\u0636\u0639\u06CC\u062A \u0646\u0634\u062F', true);
}
async function delCfg(id){
  var c = S.configs.filter(function(x){ return x.id === id; })[0];
  if (!confirm('\u06A9\u0627\u0646\u0641\u06CC\u06AF \xAB' + (c ? c.name : id) + '\xBB \u062D\u0630\u0641 \u0634\u0648\u062F\u061F')) return;
  var r = await api('/api/configs/' + id, { method: 'DELETE' });
  if (r.ok) { toast('\u062D\u0630\u0641 \u0634\u062F'); loadConfigs(); } else toast('\u062D\u0630\u0641 \u0646\u0634\u062F', true);
}
async function moveCfg(id, dir){
  var i = S.configs.findIndex(function(x){ return x.id === id; });
  var j = i + dir;
  if (i < 0 || j < 0 || j >= S.configs.length) return;
  var a = S.configs[i], b = S.configs[j];
  var sa = a.sort, sb = b.sort;
  // \u0627\u06AF\u0631 sort \u062F\u0648 \u06A9\u0627\u0646\u0641\u06CC\u06AF \u06CC\u06A9\u06CC \u0628\u0627\u0634\u062F\u060C \u062C\u0627\u0628\u0647\u200C\u062C\u0627\u06CC\u06CC \u0647\u06CC\u0686 \u0627\u062B\u0631\u06CC \u0646\u062F\u0627\u0631\u062F\u061B \u06CC\u06A9\u06CC \u0631\u0627 \u06CC\u06A9 \u0648\u0627\u062D\u062F \u062C\u062F\u0627 \u0645\u06CC\u200C\u06A9\u0646\u06CC\u0645.
  if (sa === sb) { sa = sb - 1; }
  await api('/api/configs/' + a.id, { method: 'PATCH', body: JSON.stringify({ sort: sb }) });
  await api('/api/configs/' + b.id, { method: 'PATCH', body: JSON.stringify({ sort: sa }) });
  loadConfigs();
}
async function publish(btn){
  busy(btn, true);
  var r = await api('/api/rc/publish', { method: 'POST' });
  busy(btn, false);
  if (r.ok) { toast('\u0646\u0633\u062E\u0647 \u0628\u0627\u0644\u0627 \u0631\u0641\u062A'); loadRc(); } else toast('\u0627\u0646\u062A\u0634\u0627\u0631 \u0646\u0634\u062F', true);
}

/* \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 app + settings \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 */
async function loadRc(){
  var r = await api('/api/rc');
  if (!r.ok) { toast('\u0627\u0637\u0644\u0627\u0639\u0627\u062A RC \u0646\u06CC\u0627\u0645\u062F', true); return; }
  S.rc = r.data || {};
  var url = S.rc.url || '';
  el('rc-url').textContent = url.replace(/^https?:\\/\\//, '');
  el('rc-key').textContent = S.rc.publicKey || '';
  el('rc-version').textContent = fa(S.rc.version || 0);
  el('rc-count').textContent = fa(S.rc.enabled || 0);
  el('rc-lines').textContent = 'mohammad.remoteConfigUrl=' + url + '\\n'
    + 'mohammad.remoteConfigKey=' + (S.rc.publicKey || '');
}
async function loadSettings(){
  var r = await api('/api/settings');
  if (!r.ok) return;
  S.nodeHost = (r.data && r.data.node_host) || '';
  el('node-host').value = S.nodeHost;
  el('s-node').textContent = S.nodeHost || '\u2014';
}
async function saveNode(btn){
  var v = el('node-host').value.trim().replace(/^https?:\\/\\//, '').replace(/\\/.*$/, '');
  busy(btn, true);
  var r = await api('/api/settings', { method: 'POST', body: JSON.stringify({ key: 'node_host', value: v }) });
  busy(btn, false);
  if (r.ok) { toast('\u0630\u062E\u06CC\u0631\u0647 \u0634\u062F'); el('n-msg').textContent = ''; loadSettings(); }
  else el('n-msg').textContent = r.data.error === 'bad_host' ? '\u0641\u0631\u0645\u062A \u062F\u0627\u0645\u0646\u0647 \u062F\u0631\u0633\u062A \u0646\u06CC\u0633\u062A (\u0628\u062F\u0648\u0646 https \u0648 \u0628\u062F\u0648\u0646 \u067E\u0648\u0631\u062A).' : '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}
async function changePassword(btn){
  var cur = el('pw-cur').value, next = el('pw-new').value;
  if (next.length < 8) { el('pw-msg').textContent = '\u0631\u0645\u0632 \u062C\u062F\u06CC\u062F \u062D\u062F\u0627\u0642\u0644 \u06F8 \u06A9\u0627\u0631\u0627\u06A9\u062A\u0631 \u0628\u0627\u0634\u062F.'; return; }
  busy(btn, true);
  var r = await api('/api/password', { method: 'POST', body: JSON.stringify({ current: cur, next: next }) });
  busy(btn, false);
  if (r.ok) { el('pw-cur').value = ''; el('pw-new').value = ''; el('pw-msg').textContent = ''; toast('\u0631\u0645\u0632 \u0639\u0648\u0636 \u0634\u062F'); showAuth('login'); }
  else el('pw-msg').textContent = r.status === 401 ? '\u0631\u0645\u0632 \u0641\u0639\u0644\u06CC \u0627\u0634\u062A\u0628\u0627\u0647 \u0627\u0633\u062A.' : '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}

/* \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 clean IP pools \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 */
async function loadIps(manual){
  var r = await api('/api/ips');
  if (!r.ok) { toast('\u0627\u0633\u062A\u062E\u0631\u0647\u0627 \u0646\u06CC\u0627\u0645\u062F\u0646\u062F', true); return; }
  S.pools = r.data.pools || [];
  renderPools();
  fillPoolSelect('u-pool');
  fillPoolSelect('e-pool');
  var warn = el('ip-warn');
  if (warn) warn.classList.toggle('hidden', S.pools.some(function(p){ return p.enabled; }));
  if (manual) toast('\u062A\u0627\u0632\u0647 \u0634\u062F');
}

function renderPools(){
  var html = '';
  S.pools.forEach(function(p){
    var list = String(p.ips || '').split(/\\r?\\n/).filter(function(x){ return x.split('#')[0].trim(); });
    html += '<div class="item">'
      + '<div class="top"><b>' + esc(p.name) + '</b>'
        + '<span class="pill ' + (p.enabled ? 'on' : 'off') + '"><i></i>' + (p.enabled ? '\u0641\u0639\u0627\u0644' : '\u062E\u0627\u0645\u0648\u0634') + '</span>'
        + '<span class="muted">' + fa(list.length) + ' \u0622\u06CC\u200C\u067E\u06CC \xB7 ' + esc(p.operator) + '</span>'
        + '<span style="margin-right:auto"></span>'
        + '<button class="icon" title="\u062A\u0633\u062A" onclick="copyTest(\\'' + esc(list[0] || '') + '\\')"><svg class="ic"><use href="#i-link"/></svg></button>'
        + '<button class="icon" title="\u0648\u06CC\u0631\u0627\u06CC\u0634" onclick="editPool(' + p.id + ')"><svg class="ic"><use href="#i-edit"/></svg></button>'
        + '<button class="icon" title="' + (p.enabled ? '\u062E\u0627\u0645\u0648\u0634' : '\u0631\u0648\u0634\u0646') + '" onclick="togglePool(' + p.id + ')"><svg class="ic"><use href="#i-power"/></svg></button>'
        + '<button class="icon" title="\u062D\u0630\u0641" style="color:var(--bad)" onclick="delPool(' + p.id + ')"><svg class="ic"><use href="#i-trash"/></svg></button>'
      + '</div>'
      + '<div class="mono">' + esc(list.slice(0, 6).join(' \xB7 ')) + (list.length > 6 ? ' \u2026' : '') + '</div>'
      + '</div>';
  });
  el('pool-list').innerHTML = html;
  el('ip-empty').innerHTML = S.pools.length ? '' :
    '<div class="empty"><svg class="ic"><use href="#i-link"/></svg><b>\u0647\u0646\u0648\u0632 \u0627\u0633\u062A\u062E\u0631\u06CC \u0646\u0633\u0627\u062E\u062A\u0647\u200C\u0627\u06CC</b>\u0628\u062F\u0648\u0646 \u0622\u06CC\u200C\u067E\u06CC \u062A\u0645\u06CC\u0632\u060C \u06A9\u0627\u0646\u0641\u06CC\u06AF\u200C\u0647\u0627 \u062F\u0631 \u0627\u06CC\u0631\u0627\u0646 \u0645\u0639\u0645\u0648\u0644\u0627\u064B \u0648\u0635\u0644 \u0646\u0645\u06CC\u200C\u0634\u0648\u0646\u062F.</div>';
}

function fillPoolSelect(id){
  var sel = el(id);
  if (!sel) return;
  var value = sel.value;
  sel.innerHTML = '<option value="">\u067E\u06CC\u0634\u200C\u0641\u0631\u0636</option>' + S.pools.map(function(p){
    return '<option value="' + p.id + '">' + esc(p.name) + '</option>';
  }).join('');
  if (value) sel.value = value;
}

async function addPool(btn){
  var name = el('ip-name').value.trim();
  var ips = el('ip-list').value.trim();
  if (name.length < 2) { el('ip-msg').textContent = '\u0646\u0627\u0645 \u0627\u0633\u062A\u062E\u0631 \u0631\u0627 \u0628\u0646\u0648\u06CC\u0633.'; return; }
  if (!ips) { el('ip-msg').textContent = '\u062D\u062F\u0627\u0642\u0644 \u06CC\u06A9 \u0622\u06CC\u200C\u067E\u06CC \u0644\u0627\u0632\u0645 \u0627\u0633\u062A.'; return; }
  busy(btn, true);
  var r = await api('/api/ips', { method: 'POST', body: JSON.stringify({ name: name, operator: el('ip-op').value, ips: ips }) });
  busy(btn, false);
  if (r.ok) {
    el('ip-name').value = ''; el('ip-list').value = ''; el('ip-msg').textContent = '';
    toast('\u0627\u0633\u062A\u062E\u0631 \u0627\u0636\u0627\u0641\u0647 \u0634\u062F'); loadIps();
  } else el('ip-msg').textContent = '\u062E\u0637\u0627: ' + (r.data.error || r.status);
}

async function editPool(id){
  var p = S.pools.filter(function(x){ return x.id === id; })[0];
  if (!p) return;
  var name = prompt('\u0646\u0627\u0645 \u0627\u0633\u062A\u062E\u0631', p.name);
  if (name === null) return;
  var ips = prompt('\u0622\u06CC\u200C\u067E\u06CC\u200C\u0647\u0627 (\u0647\u0631 \u062E\u0637 \u06CC\u06A9\u06CC)', p.ips);
  if (ips === null) return;
  var r = await api('/api/ips/' + id, { method: 'PATCH', body: JSON.stringify({ name: name, ips: ips }) });
  if (!r.ok) toast('\u062E\u0637\u0627: ' + (r.data.error || r.status), true);
  loadIps();
}

async function togglePool(id){
  var p = S.pools.filter(function(x){ return x.id === id; })[0];
  if (!p) return;
  var r = await api('/api/ips/' + id, { method: 'PATCH', body: JSON.stringify({ enabled: !p.enabled }) });
  if (r.ok) loadIps(); else toast('\u062A\u063A\u06CC\u06CC\u0631 \u0648\u0636\u0639\u06CC\u062A \u0646\u0634\u062F', true);
}

async function delPool(id){
  var p = S.pools.filter(function(x){ return x.id === id; })[0];
  if (!confirm('\u0627\u0633\u062A\u062E\u0631 \xAB' + (p ? p.name : id) + '\xBB \u062D\u0630\u0641 \u0634\u0648\u062F\u061F \u06A9\u0627\u0631\u0628\u0631\u0627\u0646\u0634 \u0628\u0647 \u0627\u0633\u062A\u062E\u0631 \u067E\u06CC\u0634\u200C\u0641\u0631\u0636 \u0628\u0631\u0645\u06CC\u200C\u06AF\u0631\u062F\u0646\u062F.')) return;
  var r = await api('/api/ips/' + id, { method: 'DELETE' });
  if (r.ok) { toast('\u062D\u0630\u0641 \u0634\u062F'); loadIps(); loadUsers(); } else toast('\u062D\u0630\u0641 \u0646\u0634\u062F', true);
}

function copyTest(ip){
  var host = S.nodeHost || 'NODE_HOST';
  return copyText('curl -s --resolve ' + host + ':443:' + (ip || 'IP') + ' https://' + host + '/health', '\u062F\u0633\u062A\u0648\u0631 \u062A\u0633\u062A \u06A9\u067E\u06CC \u0634\u062F');
}

async function releaseIp(id){
  var r = await api('/api/users/' + id + '/ip', { method: 'POST', body: JSON.stringify({ ip: '' }) });
  if (r.ok) { toast('\u0622\u06CC\u200C\u067E\u06CC \u0622\u0632\u0627\u062F \u0634\u062F'); loadUsers(); } else toast('\u0622\u0632\u0627\u062F \u0646\u0634\u062F', true);
}

async function previewSub(){
  var id = Number(el('sub-user').value);
  var u = S.users.filter(function(x){ return x.id === id; })[0];
  if (!u) { toast('\u0627\u0648\u0644 \u06CC\u06A9 \u06A9\u0627\u0631\u0628\u0631 \u0628\u0633\u0627\u0632', true); return; }
  var format = el('sub-format').value;
  var res = await fetch('/sub/' + encodeURIComponent(u.sub_token) + '?format=' + format, { credentials: 'include' });
  var text = await res.text();
  if (format === 'base64') { try { text = atob(text); } catch (e) {} }
  el('sub-preview').textContent = text;
}

/* \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 helpers \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 */
function copyText(text, msg){
  var done = function(){ toast(msg || '\u06A9\u067E\u06CC \u0634\u062F'); };
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(done, function(){ fallbackCopy(text); done(); });
  } else { fallbackCopy(text); done(); }
}
function fallbackCopy(text){
  var ta = document.createElement('textarea');
  ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
  document.body.appendChild(ta); ta.select();
  try { document.execCommand('copy'); } catch (e) {}
  document.body.removeChild(ta);
}
function copyEl(id){ copyText(el(id).textContent, '\u06A9\u067E\u06CC \u0634\u062F'); }

document.addEventListener('keydown', function(e){
  if (e.key === 'Enter' && !el('auth').classList.contains('hidden')) {
    if (!el('setupBox').classList.contains('hidden')) setup(); else login();
  }
});

boot();
<\/script>
</body>
</html>
`,In={async fetch(t,n,i){try{return await Rn(t,n,i)}catch(e){return console.error(e),l({error:"internal"},500)}}};async function Rn(t,n,i){let e=new URL(t.url),a=e.pathname;return await wn(n),i&&typeof i.waitUntil=="function"&&i.waitUntil(Nn(n).catch(()=>{})),a==="/"||a===""?new Response(null,{status:302,headers:{location:"/panel"}}):a==="/favicon.ico"?new Response(null,{status:204}):a.startsWith("/sub/")?Sn(t,n,a.slice(5)):a==="/rc"?En(t,n):a==="/panel"||a==="/panel/"?hn(On):a.startsWith("/api/")?Tn(t,n,e):a==="/health"?l({ok:!0}):new Response("Not Found",{status:404})}export{In as default};
