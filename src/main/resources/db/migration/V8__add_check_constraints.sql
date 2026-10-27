-- The rules below were enforced only in Java (the AuctionStatus enum, CreateAuctionUseCase's window
-- check, the request validation). A row written any other way -- a script, a migration, a future
-- service -- could still hold a status nobody knows, an auction that ends before it starts, or a
-- price of zero. These constraints make the database refuse such rows too.
ALTER TABLE auctions
    ADD CONSTRAINT chk_auctions_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'ENDED', 'CANCELLED')),
    ADD CONSTRAINT chk_auctions_window
        CHECK (end_time > start_time),
    ADD CONSTRAINT chk_auctions_prices_positive
        CHECK (starting_price > 0 AND bid_increment > 0);

ALTER TABLE bids
    ADD CONSTRAINT chk_bids_amount_positive
        CHECK (amount > 0);
