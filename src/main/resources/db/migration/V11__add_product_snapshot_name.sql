-- The product name, copied from ProductCreated / ProductUpdated, so the "auctions I bid in" list can show it
-- without calling catalog-service. Null for rows made before this column existed, until the product is renamed.
ALTER TABLE product_snapshots ADD COLUMN name VARCHAR(255);
