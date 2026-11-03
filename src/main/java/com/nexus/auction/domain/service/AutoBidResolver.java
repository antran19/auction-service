package com.nexus.auction.domain.service;

import com.nexus.auction.domain.model.AutoBid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Works out which bids the system must place after something changed on an auction: a person set
// or raised a maximum, or someone placed a bid by hand. It only computes; storing the bids and
// updating the auction is the caller's job, inside the same row lock.
//
// Every participant has a ceiling: the maximum of their auto-bid, or, for the person leading by a
// manual bid, the amount of that bid. The rules:
//  - the highest ceiling wins; on equal ceilings the one who got there first wins (an auto-bid
//    created earlier, or a manual leader, who is always older than a standing auto-bid);
//  - the winner pays the lower of its own ceiling and the runner-up's ceiling plus one increment;
//  - the runner-up's last bid is recorded so that the winner's bid is exactly one increment above
//    it, so every two consecutive bids in the history differ by at least one increment, whatever
//    the hidden ceilings were;
//  - nothing happens unless somebody other than the leader can actually bid, that is reach the
//    visible price plus one increment. A manual bid is public, so against it the full increment
//    always applies.
public final class AutoBidResolver {

    // A bid the system has to place, in the order given.
    public record Step(String bidderId, BigDecimal amount) {
    }

    private record Participant(String bidderId, BigDecimal ceiling, Instant priority) {
    }

    private AutoBidResolver() {
    }

    // currentPrice and leaderId are null when the auction has no bid yet. The first bid of an
    // auction may be the starting price itself; every later bid needs currentPrice + increment.
    public static List<Step> resolve(BigDecimal startingPrice, BigDecimal increment, BigDecimal currentPrice,
                                     String leaderId, List<AutoBid> autoBids) {
        BigDecimal floor = currentPrice == null ? startingPrice : currentPrice.add(increment);
        List<Participant> participants = participants(currentPrice, leaderId, autoBids);
        if (participants.isEmpty()) {
            return List.of();
        }

        Participant winner = participants.get(0);
        boolean winnerLeads = winner.bidderId().equals(leaderId);

        if (participants.size() == 1) {
            // Alone on the auction: nothing to do for the leader, or the very first bid.
            if (winnerLeads || winner.ceiling().compareTo(floor) < 0) {
                return List.of();
            }
            return List.of(new Step(winner.bidderId(), floor));
        }

        Participant runnerUp = participants.get(1);
        BigDecimal winnerCeiling = winner.ceiling();
        BigDecimal runnerUpCeiling = runnerUp.ceiling();

        // Nobody can bid against the leader, or the challenger cannot reach the next valid bid.
        boolean canChange = winnerLeads
                ? runnerUpCeiling.compareTo(floor) >= 0
                : winnerCeiling.compareTo(floor) >= 0;
        if (!canChange) {
            return List.of();
        }

        BigDecimal runnerUpLastBid = runnerUpCeiling.min(winnerCeiling.subtract(increment));
        BigDecimal winnerPrice = winnerCeiling.min(runnerUpCeiling.add(increment)).max(floor);

        List<Step> steps = new ArrayList<>();
        if (runnerUpLastBid.compareTo(floor) >= 0) {
            steps.add(new Step(runnerUp.bidderId(), runnerUpLastBid));
        }
        steps.add(new Step(winner.bidderId(), winnerPrice));
        return steps;
    }

    private static List<Participant> participants(BigDecimal currentPrice, String leaderId, List<AutoBid> autoBids) {
        Map<String, Participant> byBidder = new LinkedHashMap<>();
        for (AutoBid autoBid : autoBids) {
            byBidder.put(autoBid.getBidderId(),
                    new Participant(autoBid.getBidderId(), autoBid.getMaxAmount(), autoBid.getCreatedAt()));
        }
        if (leaderId != null) {
            Participant existing = byBidder.get(leaderId);
            if (existing == null) {
                // Leading by a manual bid only: the bid itself is the ceiling, and it came first.
                byBidder.put(leaderId, new Participant(leaderId, currentPrice, Instant.MIN));
            } else {
                byBidder.put(leaderId, new Participant(leaderId, existing.ceiling().max(currentPrice), existing.priority()));
            }
        }
        List<Participant> sorted = new ArrayList<>(byBidder.values());
        sorted.sort(Comparator.comparing(Participant::ceiling, Comparator.reverseOrder())
                .thenComparing(Participant::priority));
        return sorted;
    }
}
