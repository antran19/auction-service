-- product_snapshots.status is copied from catalog-service's ProductStatus (DRAFT, ACTIVE, INACTIVE,
-- SOLD), which catalog-service now also enforces with a CHECK. Without this one, a misspelt status
-- would be stored here and CreateAuction would treat the product as "not on sale" with no error.
-- When catalog-service adds a ProductStatus value, a new migration must widen this constraint too.
ALTER TABLE product_snapshots
    ADD CONSTRAINT chk_product_snapshots_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'SOLD'));
