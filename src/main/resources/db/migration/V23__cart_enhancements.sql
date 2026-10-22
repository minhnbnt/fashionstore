ALTER TABLE cart_item ADD COLUMN price_at_added BIGINT NOT NULL DEFAULT 0;

UPDATE cart_item ci
SET price_at_added = pv.price
FROM product_variant pv
WHERE ci.variant_id = pv.id;
