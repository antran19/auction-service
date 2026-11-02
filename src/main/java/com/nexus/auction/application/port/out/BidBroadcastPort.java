package com.nexus.auction.application.port.out;

import com.nexus.auction.application.usecase.BidResult;

// Tells whoever is watching an auction that a bid was accepted. Called from inside the bid
// transaction; an implementation must not announce the bid before that transaction commits.
public interface BidBroadcastPort {
    void bidPlaced(BidResult bid);
}
