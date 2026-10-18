-- auction-service's copy of each product's sellable stock, from fulfillment's InventoryLevelChanged.
-- as_of is the event's occurredAt; an older event never overwrites a newer level.
CREATE TABLE product_stock_levels (
    product_id VARCHAR(255) PRIMARY KEY,
    available_quantity INTEGER NOT NULL,
    as_of TIMESTAMPTZ NOT NULL
);
