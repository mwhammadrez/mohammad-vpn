-- ساختار دیتابیس پنل (Cloudflare D1 / SQLite)
CREATE TABLE IF NOT EXISTS users (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  name                   TEXT NOT NULL UNIQUE,
  uuid                   TEXT NOT NULL UNIQUE,          -- شناسه VLESS
  sub_token              TEXT NOT NULL UNIQUE,          -- توکن لینک اشتراک
  trojan_password        TEXT NOT NULL,                 -- رمز Trojan
  trojan_hash            TEXT NOT NULL,                 -- sha224(password) برای احراز هویت
  limit_bytes            INTEGER NOT NULL DEFAULT 0,    -- 0 = بدون محدودیت
  used_bytes             INTEGER NOT NULL DEFAULT 0,
  limit_req              INTEGER NOT NULL DEFAULT 0,    -- 0 = بدون محدودیت
  used_req               INTEGER NOT NULL DEFAULT 0,
  ip_limit               INTEGER NOT NULL DEFAULT 0,    -- دستگاه همزمان، 0 = نامحدود
  active_ips             TEXT,                          -- JSON: {ip: lastSeenMs}
  expires_at             INTEGER NOT NULL DEFAULT 0,    -- 0 = بدون انقضا (ms)
  expiry_days            INTEGER NOT NULL DEFAULT 0,    -- برای start_on_first_connect
  start_on_first_connect INTEGER NOT NULL DEFAULT 0,
  first_connection_time  INTEGER,
  auto_reset_vol_days    INTEGER NOT NULL DEFAULT 0,
  auto_reset_req_days    INTEGER NOT NULL DEFAULT 0,
  last_reset_vol_time    INTEGER,
  last_reset_req_time    INTEGER,
  auto_rotate_ip         INTEGER NOT NULL DEFAULT 1,
  enabled                INTEGER NOT NULL DEFAULT 1,
  connection_type        TEXT NOT NULL DEFAULT 'both',   -- vless | trojan | both
  ip_pool_id             INTEGER,
  pinned_ip              TEXT,
  ip_pinned_at           INTEGER,
  note                   TEXT,
  frag_len               TEXT,                 -- طول فرگمنت ضدDPI (مثلاً 100-200)
  frag_int               TEXT,                 -- فاصله فرگمنت (مثلاً 1-1)
  ip_count               INTEGER NOT NULL DEFAULT 1,  -- چند IP در لینک‌ها
  mtu                    INTEGER NOT NULL DEFAULT 0,  -- 0 = پیش‌فرض اپ
  created_at             INTEGER NOT NULL,
  last_active            INTEGER
);

CREATE INDEX IF NOT EXISTS idx_users_uuid  ON users(uuid);
CREATE INDEX IF NOT EXISTS idx_users_sub   ON users(sub_token);
CREATE INDEX IF NOT EXISTS idx_users_trojan ON users(trojan_hash);

CREATE TABLE IF NOT EXISTS settings (
  key   TEXT PRIMARY KEY,
  value TEXT
);

-- استخر آی‌پی تمیز: لینک‌ها به این آی‌پی‌ها وصل می‌شوند، با SNI/Host دامنه‌ی نود
CREATE TABLE IF NOT EXISTS ip_pools (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  name       TEXT NOT NULL,
  operator   TEXT NOT NULL DEFAULT 'any',   -- mci | irancell | mci_fixed | any
  ips        TEXT NOT NULL,                 -- هر خط یک IP؛ «IP#توضیح» هم مجاز است
  enabled    INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL
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

CREATE TABLE IF NOT EXISTS login_attempts (
  ip       TEXT PRIMARY KEY,
  fails    INTEGER NOT NULL DEFAULT 0,
  reset_at INTEGER NOT NULL
);
