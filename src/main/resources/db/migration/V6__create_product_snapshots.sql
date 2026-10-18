-- auction-service's own copy of who owns each catalog product and whether it is on sale, kept up
-- to date from catalog-events. Lets CreateAuction refuse someone else's product without calling
-- catalog-service.
CREATE TABLE product_snapshots (
    product_id VARCHAR(255) PRIMARY KEY,
    seller_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
