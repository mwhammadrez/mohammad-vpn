-- ساختار دیتابیس پنل (Cloudflare D1 / SQLite)
CREATE TABLE IF NOT EXISTS users (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  name            TEXT NOT NULL UNIQUE,
  uuid            TEXT NOT NULL UNIQUE,          -- شناسه VLESS
  sub_token       TEXT NOT NULL UNIQUE,          -- توکن لینک اشتراک
  trojan_password TEXT NOT NULL,                 -- رمز Trojan
  trojan_hash     TEXT NOT NULL,                 -- sha224(password) برای احراز هویت
  limit_bytes     INTEGER NOT NULL DEFAULT 0,    -- 0 = بدون محدودیت
  used_bytes      INTEGER NOT NULL DEFAULT 0,
  expires_at      INTEGER NOT NULL DEFAULT 0,    -- 0 = بدون انقضا (ms)
  enabled         INTEGER NOT NULL DEFAULT 1,
  created_at      INTEGER NOT NULL,
  last_active     INTEGER
);

CREATE INDEX IF NOT EXISTS idx_users_uuid  ON users(uuid);
CREATE INDEX IF NOT EXISTS idx_users_sub   ON users(sub_token);
CREATE INDEX IF NOT EXISTS idx_users_trojan ON users(trojan_hash);

CREATE TABLE IF NOT EXISTS settings (
  key   TEXT PRIMARY KEY,
  value TEXT
);

-- کانفیگ‌هایی که اپ از طریق /rc دریافت می‌کند (امضاشده با Ed25519)
CREATE TABLE IF NOT EXISTS configs (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT NOT NULL,
  uri         TEXT NOT NULL,
  enabled     INTEGER NOT NULL DEFAULT 1,
  sort        INTEGER NOT NULL DEFAULT 0,
  created_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_configs_sort ON configs (sort, id);
