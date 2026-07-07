-- =============================================================================
-- Support Agent — schema (H2-compatible)
--
-- Changes from the MySQL original:
--   • Removed CREATE DATABASE / USE — H2 uses the JDBC URL database
--   • Removed ENGINE=InnoDB / CHARSET / COLLATE — not supported in H2
--   • ENUM(…) → VARCHAR(n) CHECK (col IN (…)) — portable constraint
--   • Inline KEY declarations moved to standalone CREATE INDEX statements
--   • UNIQUE KEY name (col) → CONSTRAINT name UNIQUE (col)
--   • CREATE TABLE IF NOT EXISTS for idempotent startup init
--   • All table names are UPPERCASE to match @Table(name=…) in JPA entities
-- =============================================================================

DROP ALL OBJECTS;

-- ---------------------------------------------------------------------------
-- Customers
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS CUSTOMERS (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    full_name          VARCHAR(150) NOT NULL,
    email              VARCHAR(255) NOT NULL,
    phone              VARCHAR(40),
    preferred_language VARCHAR(8)   NOT NULL DEFAULT 'en',
    loyalty_tier       VARCHAR(20)  NOT NULL DEFAULT 'STANDARD'
                           CHECK (loyalty_tier IN ('STANDARD','SILVER','GOLD','PLATINUM')),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_customers_email UNIQUE (email)
);

-- ---------------------------------------------------------------------------
-- Products
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS PRODUCTS (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    sku             VARCHAR(40)    NOT NULL,
    name            VARCHAR(200)   NOT NULL,
    description     TEXT,
    category        VARCHAR(80),
    price           DECIMAL(10,2)  NOT NULL,
    currency        CHAR(3)        NOT NULL DEFAULT 'USD',
    specifications  JSON,
    warranty_months INT            NOT NULL DEFAULT 12,
    stock_quantity  INT            NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_products_sku UNIQUE (sku)
);

-- ---------------------------------------------------------------------------
-- Orders
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ORDERS (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    order_number     VARCHAR(40)   NOT NULL,
    customer_id      BIGINT        NOT NULL,
    order_date       DATE          NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING','PAID','SHIPPED','DELIVERED','CANCELLED','RETURNED')),
    shipping_address VARCHAR(400),
    total_amount     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    currency         CHAR(3)       NOT NULL DEFAULT 'USD',
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_orders_number   UNIQUE (order_number),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMERS (id)
);

CREATE INDEX IF NOT EXISTS idx_orders_customer ON ORDERS (customer_id);

-- ---------------------------------------------------------------------------
-- Order line items (an order can contain several products)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ORDER_ITEMS (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    order_id   BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INT           NOT NULL DEFAULT 1,
    unit_price DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_items_order   FOREIGN KEY (order_id)   REFERENCES ORDERS (id)   ON DELETE CASCADE,
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES PRODUCTS (id)
);

CREATE INDEX IF NOT EXISTS idx_items_order   ON ORDER_ITEMS (order_id);
CREATE INDEX IF NOT EXISTS idx_items_product ON ORDER_ITEMS (product_id);

-- ---------------------------------------------------------------------------
-- Payments / charges
-- One order can have MORE THAN ONE captured payment row — that is precisely
-- how the agent spots a duplicate charge ("charged twice for order #4471").
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS PAYMENTS (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_id        BIGINT        NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    currency        CHAR(3)       NOT NULL DEFAULT 'USD',
    payment_method  VARCHAR(40)   NOT NULL DEFAULT 'CARD',
    transaction_ref VARCHAR(80)   NOT NULL,
    status          VARCHAR(30)   NOT NULL DEFAULT 'CAPTURED'
                        CHECK (status IN ('AUTHORIZED','CAPTURED','FAILED','REFUNDED','PARTIALLY_REFUNDED')),
    charged_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_payments_txn   UNIQUE (transaction_ref),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES ORDERS (id)
);

CREATE INDEX IF NOT EXISTS idx_payments_order ON PAYMENTS (order_id);

-- ---------------------------------------------------------------------------
-- Refunds — the resolution the agent writes back when it takes action
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS REFUNDS (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    payment_id  BIGINT,
    amount      DECIMAL(10,2) NOT NULL,
    currency    CHAR(3)       NOT NULL DEFAULT 'USD',
    reason      VARCHAR(400),
    refund_type VARCHAR(30)   NOT NULL DEFAULT 'OTHER'
                    CHECK (refund_type IN ('GOODWILL','DUPLICATE_CHARGE','WARRANTY','RETURN','OTHER')),
    status      VARCHAR(20)   NOT NULL DEFAULT 'PROCESSED'
                    CHECK (status IN ('REQUESTED','APPROVED','PROCESSED','REJECTED')),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_refunds_order   FOREIGN KEY (order_id)   REFERENCES ORDERS (id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES PAYMENTS (id)
);

CREATE INDEX IF NOT EXISTS idx_refunds_order ON REFUNDS (order_id);

-- ---------------------------------------------------------------------------
-- Support tickets — log of every inbound email + what the agent did.
-- Doubles as the HISTORY the agent reads to recognise a repeat failure.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SUPPORT_TICKETS (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id       BIGINT      NOT NULL,
    order_id          BIGINT,
    product_id        BIGINT,
    channel           VARCHAR(10) NOT NULL DEFAULT 'EMAIL'
                          CHECK (channel IN ('EMAIL','CHAT','PHONE')),
    subject           VARCHAR(255),
    raw_message       TEXT,
    detected_language VARCHAR(20),
    intent            VARCHAR(30) NOT NULL DEFAULT 'OTHER'
                          CHECK (intent IN ('REFUND_REQUEST','PRESALES_QUESTION','BILLING_ISSUE',
                                            'WARRANTY_CLAIM','COMPLAINT','GENERAL','OTHER')),
    sentiment         VARCHAR(10) NOT NULL DEFAULT 'NEUTRAL'
                          CHECK (sentiment IN ('POSITIVE','NEUTRAL','NEGATIVE','ANGRY')),
    status            VARCHAR(10) NOT NULL DEFAULT 'OPEN'
                          CHECK (status IN ('OPEN','RESOLVED','ESCALATED')),
    resolution        TEXT,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at       TIMESTAMP   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tickets_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMERS (id),
    CONSTRAINT fk_tickets_order    FOREIGN KEY (order_id)    REFERENCES ORDERS (id),
    CONSTRAINT fk_tickets_product  FOREIGN KEY (product_id)  REFERENCES PRODUCTS (id)
);

CREATE INDEX IF NOT EXISTS idx_tickets_customer ON SUPPORT_TICKETS (customer_id);
CREATE INDEX IF NOT EXISTS idx_tickets_product  ON SUPPORT_TICKETS (product_id);
