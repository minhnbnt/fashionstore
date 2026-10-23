ALTER TABLE product_variant
    DROP CONSTRAINT IF EXISTS product_variant_check;

ALTER TABLE product_variant
    DROP CONSTRAINT IF EXISTS product_variant_compare_at_price_check;

ALTER TABLE product_variant
    ADD CONSTRAINT product_variant_compare_at_price_check
    CHECK (compare_at_price IS NULL OR compare_at_price >= 0);
