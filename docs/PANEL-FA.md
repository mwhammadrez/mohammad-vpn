# پنل روی Cloudflare — راهنمای کامل

این سند دقیقاً همان چیزی است که در پروژه ساخته شده: یک پنل مدیریت + یک نود پروکسی روی
حساب Cloudflare خودت، و کانفیگ‌هایی که خودکار در صفحه‌ی اصلی اپ همه‌ی کاربران می‌آیند.

---

## ۱) چطور کار می‌کند

```
┌──────────────┐   BuiltConfig (RC)   ┌───────────────────────┐
│  اپ اندروید  │ ───── GET /rc ─────▶ │  Worker «panel»       │
│              │ ◀── JSON + امضا ──── │  + D1                 │
└──────┬───────┘                      └──────────┬────────────┘
       │                                          │ /sub/<token>
       │ ربط مستقیم                               │
       ▼                                          ▼
┌──────────────────────────────────────────────────────────────┐
│  Worker «<name>-node»  (VLESS / Trojan روی WebSocket)        │
└──────────────────────────────────────────────────────────────┘
```

سه چیز در حساب تو ساخته می‌شود:

| چیز | کار |
|---|---|
| Worker `panel` (یا هر نامی) | پنل مدیریت (فارسی) + `/api/*` + `/sub/<token>` + `/rc` |
| Worker `panel-node` | فقط تونل: VLESS و Trojan روی WebSocket با احراز هویت D1 |
| D1 `panel-db` | جدول‌های `users`، `settings` و `configs` |

چرا دو ورکر: یک ورکر که هم پنل و هم WebSocket را داشته باشد در استارت سرد ناپایدار است؛
جدا کردنشان (پنل سنگین، نود سبک و minified) مشکل را حل کرد.

---

## ۲) راه اول: از داخل اپ (Mode 3 · Panel)

1. اپ → **Mode 3 · Panel**
2. کارت اول → **Open Cloudflare** (لینک با سه مجوز از قبل تیک‌خورده باز می‌شود):

   | مجوز | برای چه |
   |---|---|
   | Workers Scripts · Edit | آپلود دو ورکر |
   | D1 · Edit | ساخت دیتابیس و جدول‌ها |
   | Account Settings · Read | پیدا کردن حساب و زیردامنه‌ی workers.dev |

3. توکن را بچسبان (**PASTE** کنار فیلد)، نام پنل و رمز پنل را ببین → **CONNECT**
4. لیست پیشرفت واقعی است: تأیید حساب → ساخت دیتابیس → جدول‌ها → رمز → نصب نود → نصب پنل → دامنه
5. تمام شد؛ همان‌جا پنل باز می‌شود: کاربر بساز، ساب بگیر، کانفیگ اضافه کن
6. هر وقت خواستی پنل را پاک کنی: **DELETE** بالای پنل (توکن را دوباره می‌پرسد).

> توکن فقط در همان یک درخواست استفاده می‌شود و هیچ‌جا ذخیره نمی‌شود — نه روی گوشی، نه در سرور.

---

## ۳) راه دوم: روی دامنه‌ی خودت (برای اینترنت ملی / فیلتر دامنه)

وقتی پنل روی `*.workers.dev` است، در ایران معمولاً فیلتر می‌شود. راه‌حل: دامنه‌ی خودت.

### ۳.۱ دامنه را به ورکر وصل کن

**روش داشبورد (ساده‌ترین):**
1. Cloudflare → **Workers & Pages** → `panel` → **Settings** → **Domains & Routes**
2. **Add** → **Custom domain** → `panel.example.com` → **Add domain**
3. همین کار را برای ورکر `panel-node` با `node.example.com` تکرار کن

**روش API (اگر توکن با مجوز `DNS:Edit` و `Workers Routes:Edit` داری):**

```bash
# ۱) رکورد پلاس‌هولدر (پروکسی روشن)
curl -X POST "https://api.cloudflare.com/client/v4/zones/$ZONE/dns_records" \
  -H "Authorization: Bearer $TOKEN" -H "content-type: application/json" \
  -d '{"type":"AAAA","name":"panel.example.com","content":"100::","proxied":true,"ttl":1}'

# ۲) روت ورکر
curl -X POST "https://api.cloudflare.com/client/v4/zones/$ZONE/workers/routes" \
  -H "Authorization: Bearer $TOKEN" -H "content-type: application/json" \
  -d '{"pattern":"panel.example.com/*","script":"panel"}'
```

> `POST /accounts/{id}/workers/domains` با توکن API کار نمی‌کند (`405`). همان روش بالا درست است.

### ۳.۲ آدرس نود را عوض کن

پنل → **تنظیمات → آدرس نود** → `node.example.com` → ذخیره.
از این به بعد همه‌ی لینک‌های ساب به دامنه‌ی خودت اشاره می‌کنند؛ **بدون بیلد مجدد اپ**.

### ۳.۳ ⚠️ WebSocket را چک کن

بعضی زون‌ها WebSocket را می‌بندند (حتی وقتی `websockets=on` است). علامتش:

```
wss://node.example.com  →  403  "WebSocket Connection Blocked"
```

این رول **مال Cloudflare** است و در منوی Security دیده نمی‌شود. برای دیدنش:

```graphql
query {
  viewer {
    zones(filter: {zoneTag: "$ZONE"}) {
      firewallEventsAdaptive(limit: 5, filter: {datetime_gt: "2026-01-01T00:00:00Z"},
                             orderBy: [datetime_DESC]) {
        datetime action ruleId description clientRequestHTTPHost
      }
    }
  }
}
```

اگر در `description` نوشته بود `WebSocket network block rule for account: …` یعنی کلودفلر
خودش بسته و هیچ تنظیمی بازش نمی‌کند؛ باید دامنه‌ی دیگری بگیری یا از پشتیبانی بخواهی.

---

## ۴) وصل کردن اپ به پنل (Remote Config)

اپ کانفیگ‌ها را از `/rc` می‌گیرد. آدرس و کلید **داخل اپ کامپایل می‌شوند** (نه از شبکه)،
پس یک بار باید در `local.properties` باشند:

```properties
mohammad.remoteConfigUrl=https://panel.example.com/rc,https://panel.mohammadreza-xxxx.workers.dev/rc
mohammad.remoteConfigKey=<کلید عمومی، از تب «اتصال اپ» در پنل>
```

- می‌توانی چند آدرس با `,` بدهی؛ اپ به ترتیب امتحان می‌کند (اول دامنه‌ی خودت).
- `mohammad.remoteConfigKey` را از پنل کپی کن → تب **اتصال اپ** → «کلید عمومی امضا».

### امنیت

پنل هر بار یک JSON می‌سازد و **امضای Ed25519** روی همان بایت‌ها می‌گذارد:

```json
{ "schemaVersion": 1, "configVersion": 1793610222331,
  "payload": "{\"profiles\":[...]}", "signature": "base64url" }
```

- کلید خصوصی فقط در D1 تو است؛ کلید عمومی داخل اپ.
- اپ اگر امضا نخواند، کانفیگ را **رد** می‌کند و لیست قبلی سر جایش می‌ماند.
- یعنی حتی اگر DNS یا سرور را کسی عوض کند، نمی‌تواند کانفیگ جعلی تزریق کند.

### زمان‌بندی

- در هر استارت، اگر از آخرین دریافت بیش از **۱ ساعت** گذشته باشد، دوباره می‌گیرد.
- دکمه‌ی **⟳** کنار `PROFILES` = همین حالا بگیر.
- لیست ذخیره‌شده (رمزنگاری‌شده با Keystore) در استارت فوراً نمایش داده می‌شود.

---

## ۵) پنل چه کاری می‌کند

| تب | امکانات |
|---|---|
| **کاربران** | افزودن، ویرایش (نام/حجم/روز)، روشن-خاموش، ریست مصرف، کپی ساب، حذف، نوار مصرف |
| **کانفیگ‌های اپ** | افزودن، ویرایش، ترتیب، خاموش/روشن، حذف، **افزودن گروهی** (هر خط یک لینک) |
| **اتصال اپ** | آدرس RC، کلید عمومی، نسخه، دو خط آماده‌ی `local.properties` |
| **تنظیمات** | آدرس نود، تغییر رمز پنل |

هر تغییر در کانفیگ‌ها، `configVersion` را بالا می‌برد؛ اپ فقط نسخه‌ی جدیدتر را می‌پذیرد.

---

## ۶) ساخت و آپلود دستی

```bash
# ورکرها را از سورس بساز (به app/src/main/assets/panel می‌ریزد)
node panel/build.mjs

# یا با wrangler
npx wrangler deploy panel/worker/panel-worker.js --name panel --compatibility-date 2025-01-01
```

بایندینگ‌هایی که ورکرها لازم دارند:

| ورکر | بایندینگ |
|---|---|
| `panel` | `DB` → D1، `PROXY_HOST` → `node.example.com` (plain text) |
| `panel-node` | `DB` → همان D1 |

اسکیمای دیتابیس: `panel/worker/schema.sql` (پنل خودش هم جدول `configs` را در اولین درخواست می‌سازد).

---

## ۷) عیب‌یابی

| علامت | علت |
|---|---|
| `404` روی `/` | مسیر ریشه ریدایرکت می‌شود به `/panel`؛ اگر 404 دیدی ورکر قدیمی است |
| صفحه‌ی پنل باز می‌شود ولی هیچ دکمه‌ای کار نمی‌کند | جاوااسکریپت صفحه خطا دارد — `node panel/build.mjs` را اجرا کن (قبل از بیلد، سینتکس چک می‌شود) |
| `401` در `/rc` | امضا نخواند یا کلید اشتباه است؛ کلید را از تب «اتصال اپ» دوباره کپی کن |
| ساب کار می‌کند ولی وصل نمی‌شود | ۱) `?ed=2048` نیاز به early data دارد (پیاده‌سازی شده) ۲) `security` را با سرورت هم‌خوان کن ۳) WebSocket زون بسته نباشد |
| `403 WebSocket Connection Blocked` | رول خود Cloudflare روی زون (بخش ۳.۳) |
| `10016` هنگام ساخت | ورکری با این نام از قبل هست؛ نام دیگری بده |
