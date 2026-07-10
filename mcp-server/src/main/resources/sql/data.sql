-- =============================================================================
-- Support Agent — 示範 / 測試資料（H2 相容）
--
-- 情境說明：
--   1. Sarah  — 攪拌機壺身破裂，第三次 -> 善意退款。
--   2. （任意）— 售前詢問「X200 支援歐洲電壓？」-> 由商品資料回答。
--   3. Priya  — 「訂單 #4471 重複收費」-> 重複付款，退還一筆。
--   4. Rohan  — 語帶諷刺、中英混雜，兩個問題 -> 多語言 + 多意圖。
--
-- 所有日期相對 CURRENT_DATE 計算，確保每次執行情境均有效。
--
-- H2 與 MySQL 原始版本的差異：
--   • 移除 USE mydatabase
--   • TIMESTAMP(CURDATE() - INTERVAL n DAY, 'HH:MM:SS')
--       → DATEADD('SECOND', <seconds>, CAST(DATEADD('DAY', -n, CURRENT_DATE) AS TIMESTAMP))
--   • CURDATE() - INTERVAL n DAY  → DATEADD('DAY', -n, CURRENT_DATE)
--   • JSON_OBJECT('k',v,…)        → 純 JSON 字串字面值
--   • INSERT INTO … → MERGE INTO … KEY(id)，確保重啟冪等性
--   • ALTER TABLE … RESTART WITH 100 確保 AUTO_INCREMENT 不與種子資料衝突
--   • 所有資料表名稱大寫以符合 JPA 實體中的 @Table(name=…)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 顧客
-- ---------------------------------------------------------------------------
MERGE INTO CUSTOMERS (id, full_name, email, phone, preferred_language, loyalty_tier, created_at)
KEY (id)
VALUES
  (1, 'Sarah Mitchell', 'sarah.mitchell@example.com', '+1-415-555-0142', 'en',    'GOLD',
      DATEADD('SECOND', 33600, CAST(DATEADD('DAY', -1216, CURRENT_DATE) AS TIMESTAMP))),
  (2, 'James Cooper',   'james.cooper@example.com',   '+44-7700-900145', 'zh',    'STANDARD',
      DATEADD('SECOND', 57900, CAST(DATEADD('DAY', -12,   CURRENT_DATE) AS TIMESTAMP))),
  (3, 'Priya Sharma',   'priya.sharma@example.com',   '+1-312-555-0188', 'zh',    'SILVER',
      DATEADD('SECOND', 42120, CAST(DATEADD('DAY', -661,  CURRENT_DATE) AS TIMESTAMP))),
  (4, 'Rohan Verma',    'rohan.verma@example.com',    '+91-98200-12345', 'en+zh', 'STANDARD',
      DATEADD('SECOND', 70200, CAST(DATEADD('DAY', -220,  CURRENT_DATE) AS TIMESTAMP)));

ALTER TABLE CUSTOMERS ALTER COLUMN id RESTART WITH 100;

-- ---------------------------------------------------------------------------
-- 商品
-- ---------------------------------------------------------------------------
MERGE INTO PRODUCTS (id, sku, name, description, category, price, currency, specifications, warranty_months, stock_quantity)
KEY (id)
VALUES
  (1, 'BLND-300', 'AeroBlend 300 High-Speed Blender',
      '1200W high-speed blender with a 2L Tritan jug. Known issue: jug can crack under thermal shock.',
      'Kitchen', 129.99, 'USD',
      '{"power":"1200W","capacity":"2L Tritan jug","voltage":"120V / 60Hz — North America only (NOT dual voltage)"}',
      24, 37),
  (2, 'X200', 'VoltMaster X200 Travel Steam Iron',
      'Compact travel steam iron with auto dual-voltage switching, ideal for international trips.',
      'Appliances', 79.99, 'USD',
      '{"type":"Travel steam iron","voltage":"100-240V, 50/60Hz auto dual-voltage — works on European 230V mains","note":"EU plug adapter required"}',
      12, 120),
  (3, 'HMX-50', 'HushMix 50 Hand Mixer',
      '5-speed quiet hand mixer with stainless beaters.',
      'Kitchen', 49.99, 'USD',
      '{"speeds":5,"voltage":"120V / 60Hz — North America only"}',
      12, 64),
  (4, 'KSET-12', 'ChefPro 12-Piece Knife Set',
      'German stainless steel 12-piece knife block set.',
      'Kitchen', 199.99, 'USD',
      '{"pieces":12,"material":"German stainless steel"}',
      60, 18),
  (5, 'EKET-7', 'BrewWell 7-Cup Electric Kettle',
      'Stainless steel cordless electric kettle with auto shut-off.',
      'Kitchen', 39.99, 'USD',
      '{"capacity":"7 cups","material":"Stainless steel","voltage":"120V / 60Hz — North America only"}',
      24, 50),
  (6, 'BOOK-021', 'The Quiet Garden (Paperback)',
      'Bestselling literary novel, paperback edition.',
      'Books', 14.99, 'USD',
      '{"format":"Paperback","pages":328,"language":"English","author":"M. Iyer"}',
      0, 210),
  (7, 'YOGA-08', 'ZenFlow Yoga Mat',
      'Non-slip TPE yoga mat with carry strap.',
      'Fitness', 34.99, 'USD',
      '{"material":"TPE","thickness":"6mm","dimensions":"183x61cm","color":"Teal"}',
      12, 95),
  (8, 'TSHIRT-M', 'CottonComfort Crew T-Shirt',
      '100% combed-cotton crew-neck t-shirt.',
      'Apparel', 19.99, 'USD',
      '{"size":"M","color":"Navy","material":"100% cotton","fit":"Regular"}',
      0, 300);

ALTER TABLE PRODUCTS ALTER COLUMN id RESTART WITH 100;

-- ---------------------------------------------------------------------------
-- 訂單
-- ---------------------------------------------------------------------------
MERGE INTO ORDERS (id, order_number, customer_id, order_date, status, shipping_address, total_amount, currency)
KEY (id)
VALUES
  -- Sarah 最新訂單的攪拌機 — 已送達，在 24 個月保固期內。
  (1, '4198', 1, DATEADD('DAY', -22,  CURRENT_DATE), 'DELIVERED',
      '88 Maple Ave, San Francisco, CA 94110, USA',    129.99, 'USD'),
  -- Priya 的刀具組 — 她反映被重複收費的訂單。
  (2, '4471', 3, DATEADD('DAY', -9,   CURRENT_DATE), 'PAID',
      '512 Lakeshore Dr, Chicago, IL 60611, USA',      199.99, 'USD'),
  -- Rohan 的訂單 — 電熱水壺 + 手持攪拌機，兩個投訴的依據。
  (3, '4502', 4, DATEADD('DAY', -6,   CURRENT_DATE), 'DELIVERED',
      'A-14 Green Park, New Delhi 110016, India',        49.99, 'USD'),
  -- Sarah 較早期的攪拌機訂單（前兩次壺身破裂事件）。
  (4, '3801', 1, DATEADD('DAY', -183, CURRENT_DATE), 'DELIVERED',
      '88 Maple Ave, San Francisco, CA 94110, USA',    129.99, 'USD'),
  (5, '4007', 1, DATEADD('DAY', -101, CURRENT_DATE), 'DELIVERED',
      '88 Maple Ave, San Francisco, CA 94110, USA',    129.99, 'USD');

ALTER TABLE ORDERS ALTER COLUMN id RESTART WITH 100;

-- ---------------------------------------------------------------------------
-- 訂單明細
-- ---------------------------------------------------------------------------
MERGE INTO ORDER_ITEMS (id, order_id, product_id, quantity, unit_price)
KEY (id)
VALUES
  (1, 1, 1, 1, 129.99),   -- 訂單 4198：AeroBlend 300
  (2, 2, 4, 1, 199.99),   -- 訂單 4471：ChefPro 刀具組
  (3, 3, 3, 1, 49.99),    -- 訂單 4502：手持攪拌機
  (4, 4, 1, 1, 129.99),   -- 訂單 3801：AeroBlend 300（第一次損壞）
  (5, 5, 1, 1, 129.99);   -- 訂單 4007：AeroBlend 300（第二次損壞）

ALTER TABLE ORDER_ITEMS ALTER COLUMN id RESTART WITH 100;

-- ---------------------------------------------------------------------------
-- 付款
-- 訂單 4471（Priya）有兩筆已授權費用 — 典型重複收費案例。
-- 明確指定 id，因為退款記錄會引用 payment_id 5 與 6。
-- ---------------------------------------------------------------------------
MERGE INTO PAYMENTS (id, order_id, amount, currency, payment_method, transaction_ref, status, charged_at)
KEY (id)
VALUES
  (1, 1, 129.99, 'USD', 'CARD', 'TXN-20260520-0001', 'CAPTURED',
      DATEADD('SECOND', 50591, CAST(DATEADD('DAY', -22,  CURRENT_DATE) AS TIMESTAMP))),
  (2, 2, 199.99, 'USD', 'CARD', 'TXN-20260602-0188', 'CAPTURED',
      DATEADD('SECOND', 36942, CAST(DATEADD('DAY', -9,   CURRENT_DATE) AS TIMESTAMP))),
  (3, 2, 199.99, 'USD', 'CARD', 'TXN-20260602-0189', 'CAPTURED',
      DATEADD('SECOND', 36948, CAST(DATEADD('DAY', -9,   CURRENT_DATE) AS TIMESTAMP))),
  (4, 3,  49.99, 'USD', 'UPI',  'TXN-20260605-7741', 'CAPTURED',
      DATEADD('SECOND', 30125, CAST(DATEADD('DAY', -6,   CURRENT_DATE) AS TIMESTAMP))),
  (5, 4, 129.99, 'USD', 'CARD', 'TXN-20251210-0044', 'REFUNDED',
      DATEADD('SECOND', 43200, CAST(DATEADD('DAY', -183, CURRENT_DATE) AS TIMESTAMP))),
  (6, 5, 129.99, 'USD', 'CARD', 'TXN-20260302-0091', 'REFUNDED',
      DATEADD('SECOND', 63930, CAST(DATEADD('DAY', -101, CURRENT_DATE) AS TIMESTAMP)));

ALTER TABLE PAYMENTS ALTER COLUMN id RESTART WITH 100;

-- ---------------------------------------------------------------------------
-- 退款 — 前兩次攪拌機更換／退款的歷史記錄。
-- （Sarah 第三次退款及 4471 重複收費退款由代理在執行時建立，
--  此處刻意不預填。）
-- ---------------------------------------------------------------------------
MERGE INTO REFUNDS (id, order_id, payment_id, amount, currency, reason, refund_type, status, created_at)
KEY (id)
VALUES
  (1, 4, 5, 129.99, 'USD', 'Cracked jug on arrival — 1st incident, replacement issued', 'WARRANTY', 'PROCESSED',
      DATEADD('SECOND', 36000, CAST(DATEADD('DAY', -175, CURRENT_DATE) AS TIMESTAMP))),
  (2, 5, 6, 129.99, 'USD', 'Cracked jug again — 2nd incident, replacement issued',       'WARRANTY', 'PROCESSED',
      DATEADD('SECOND', 52200, CAST(DATEADD('DAY', -94,  CURRENT_DATE) AS TIMESTAMP)));

ALTER TABLE REFUNDS ALTER COLUMN id RESTART WITH 100;

-- ---------------------------------------------------------------------------
-- 客服工單 — 歷史記錄，讓代理識別此為重複事件。
-- ---------------------------------------------------------------------------
MERGE INTO SUPPORT_TICKETS
  (id, customer_id, order_id, product_id, channel, subject, raw_message,
   detected_language, intent, sentiment, status, resolution, created_at, resolved_at)
KEY (id)
VALUES
  -- Sarah，第一次事件
  (1, 1, 4, 1, 'EMAIL', 'Blender jug arrived cracked',
   'Hi, my new AeroBlend blender arrived with a crack along the jug. Can you help?',
   'en', 'WARRANTY_CLAIM', 'NEGATIVE', 'RESOLVED',
   'Replacement unit shipped + full refund processed (goodwill).',
   DATEADD('SECOND', 33120, CAST(DATEADD('DAY', -178, CURRENT_DATE) AS TIMESTAMP)),
   DATEADD('SECOND', 36300, CAST(DATEADD('DAY', -175, CURRENT_DATE) AS TIMESTAMP))),
  -- Sarah，第二次事件
  (2, 1, 5, 1, 'EMAIL', 'Cracked jug AGAIN',
   'This is the second time the jug has cracked. Getting frustrated.',
   'en', 'WARRANTY_CLAIM', 'ANGRY', 'RESOLVED',
   'Second replacement shipped + refund processed. Flagged product quality issue.',
   DATEADD('SECOND', 67200, CAST(DATEADD('DAY', -98, CURRENT_DATE) AS TIMESTAMP)),
   DATEADD('SECOND', 52500, CAST(DATEADD('DAY', -94, CURRENT_DATE) AS TIMESTAMP))),
  -- Rohan，較早期的一次小問題（確認為已知顧客）
  (3, 4, 3, NULL, 'EMAIL', 'Order tracking',
   'Where is my order? / 我的訂單在哪裡?',
   'en+zh', 'GENERAL', 'NEUTRAL', 'RESOLVED',
   'Shared tracking link; delivered next day.',
   DATEADD('SECOND', 39600, CAST(DATEADD('DAY', -5, CURRENT_DATE) AS TIMESTAMP)),
   DATEADD('SECOND', 40800, CAST(DATEADD('DAY', -5, CURRENT_DATE) AS TIMESTAMP)));

ALTER TABLE SUPPORT_TICKETS ALTER COLUMN id RESTART WITH 100;
