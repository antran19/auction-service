-- The reserve price: the least the seller accepts. Hidden from bidders (they only see whether it is met).
-- Null means no reserve. It can never be below the starting price: a reserve under it would never matter.
ALTER TABLE auctions ADD COLUMN reserve_price NUMERIC(12,2);
ALTER TABLE auctions ADD CONSTRAINT chk_auctions_reserve_price
    CHECK (reserve_price IS NULL OR reserve_price >= starting_price);
