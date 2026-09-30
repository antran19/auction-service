-- CreateAuctionUseCase already checks existsActiveOrPendingForProduct before inserting,
-- but that is a check-then-insert race under concurrent requests (e.g. a double-clicked
-- "create auction" button). This partial unique index is the actual guarantee: only one
-- PENDING or ACTIVE auction can exist per product at the database level, regardless of
-- how many requests race to create one.
CREATE UNIQUE INDEX idx_auctions_one_active_per_product ON auctions (product_id)
    WHERE status IN ('PENDING', 'ACTIVE');
