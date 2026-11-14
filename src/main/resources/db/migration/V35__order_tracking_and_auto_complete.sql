ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_carrier VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tracking_code VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_orders_delivered_unreceived
    ON orders (delivered_at)
    WHERE status = 'DELIVERED' AND received_at IS NULL;

CREATE TABLE IF NOT EXISTS order_tracking_event (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    status_code VARCHAR(40) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    location    TEXT,
    description VARCHAR(500),
    event_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_order_tracking_event_order_time
    ON order_tracking_event (order_id, event_time DESC, created_at DESC);

