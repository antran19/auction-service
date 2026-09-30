package com.nexus.auction.domain.service;

import com.nexus.auction.domain.model.Auction;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public final class AntiSnipingPolicy {

    public static final long EXTENSION_MINUTES = 5;
    public static final int MAX_EXTENSIONS = 12;

    private AntiSnipingPolicy() {
    }

    public static Optional<Instant> tryExtend(Auction auction, Instant now) {
        if (auction.getExtensionCount() >= MAX_EXTENSIONS) {
            return Optional.empty();
        }
        Instant window = auction.getEndTime().minus(EXTENSION_MINUTES, ChronoUnit.MINUTES);
        if (now.isBefore(window)) {
            return Optional.empty();
        }
        return Optional.of(auction.getEndTime().plus(EXTENSION_MINUTES, ChronoUnit.MINUTES));
    }
}
