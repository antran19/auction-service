package com.nexus.auction.domain.service;

import com.nexus.auction.domain.model.AutoBid;
import com.nexus.auction.domain.service.AutoBidResolver.Step;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// Starting price 100, increment 10 in every test. "An" always has the older auto-bid.
class AutoBidResolverTest {

    private static final BigDecimal START = new BigDecimal("100");
    private static final BigDecimal INCREMENT = new BigDecimal("10");
    private static final Instant T0 = Instant.parse("2026-11-03T00:00:00Z");

    private static AutoBid autoBid(String bidder, int max, int createdSecond) {
        Instant created = T0.plusSeconds(createdSecond);
        return AutoBid.reconstitute("id-" + bidder, "auction-1", bidder, new BigDecimal(max), created, created);
    }

    private static List<Step> resolve(Integer price, String leader, AutoBid... autoBids) {
        return AutoBidResolver.resolve(START, INCREMENT, price == null ? null : new BigDecimal(price), leader,
                List.of(autoBids));
    }

    private static List<Object> asPairs(List<Step> steps) {
        return steps.stream().map(s -> (Object) tuple(s.bidderId(), s.amount().intValue())).toList();
    }

    // ---- a single person

    @Test
    void firstAutoBidOnAnAuctionWithNoBid_bidsTheStartingPrice() {
        List<Step> steps = resolve(null, null, autoBid("An", 500, 0));

        assertThat(asPairs(steps)).containsExactly(tuple("An", 100));
    }

    @Test
    void aMaximumBelowTheStartingPrice_placesNothing() {
        assertThat(resolve(null, null, autoBid("An", 99, 0))).isEmpty();
    }

    @Test
    void theLeaderRaisingTheirOwnMaximum_changesNothing() {
        assertThat(resolve(100, "An", autoBid("An", 800, 0))).isEmpty();
    }

    // ---- a manual bid meets an auto-bid

    @Test
    void aManualBid_isAnsweredByTheAutoBidWithOneIncrementMore() {
        List<Step> steps = resolve(150, "Binh", autoBid("An", 500, 0));

        assertThat(asPairs(steps)).containsExactly(tuple("An", 160));
    }

    @Test
    void aManualBidAboveTheAutoBidMaximum_winsAndNothingIsPlaced() {
        assertThat(resolve(520, "Binh", autoBid("An", 500, 0))).isEmpty();
    }

    @Test
    void aManualBidLessThanOneIncrementBelowTheAutoBidMaximum_isNotAnswered() {
        // 495 + 10 = 505 is more than the maximum of 500, so the auto-bid cannot answer.
        assertThat(resolve(495, "Binh", autoBid("An", 500, 0))).isEmpty();
    }

    @Test
    void aManualBidExactlyOneIncrementBelowTheMaximum_isAnsweredAtTheMaximum() {
        List<Step> steps = resolve(490, "Binh", autoBid("An", 500, 0));

        assertThat(asPairs(steps)).containsExactly(tuple("An", 500));
    }

    @Test
    void aManualLeaderBeatenByAnAutoBidExactlyOneIncrementAbove_isOvertaken() {
        // Public price 500, an auto-bid of 510 reaches exactly the next valid bid.
        List<Step> steps = resolve(500, "Binh", autoBid("An", 510, 0));

        assertThat(asPairs(steps)).containsExactly(tuple("An", 510));
    }

    @Test
    void anAutoBidLessThanOneIncrementAboveAPublicBid_cannotOvertake() {
        assertThat(resolve(500, "Binh", autoBid("An", 505, 0))).isEmpty();
    }

    // ---- two auto-bids

    @Test
    void theHigherMaximumWins_andPaysTheLowerOnePlusOneIncrement() {
        // An leads at 100 with a maximum of 500; Binh joins with 300.
        List<Step> steps = resolve(100, "An", autoBid("An", 500, 0), autoBid("Binh", 300, 1));

        assertThat(asPairs(steps)).containsExactly(tuple("Binh", 300), tuple("An", 310));
    }

    @Test
    void aMaximumLessThanOneIncrementBelow_stillLoses_andTheWinnerPaysItsOwnMaximum() {
        List<Step> steps = resolve(100, "An", autoBid("An", 500, 0), autoBid("Binh", 495, 1));

        assertThat(asPairs(steps)).containsExactly(tuple("Binh", 490), tuple("An", 500));
    }

    @Test
    void aMaximumLessThanOneIncrementAbove_winsAtItsOwnMaximum() {
        List<Step> steps = resolve(100, "An", autoBid("An", 500, 0), autoBid("Binh", 505, 1));

        assertThat(asPairs(steps)).containsExactly(tuple("An", 495), tuple("Binh", 505));
    }

    @Test
    void aMaximumExactlyOneIncrementAbove_winsAtThatMaximum() {
        List<Step> steps = resolve(100, "An", autoBid("An", 500, 0), autoBid("Binh", 510, 1));

        assertThat(asPairs(steps)).containsExactly(tuple("An", 500), tuple("Binh", 510));
    }

    @Test
    void equalMaximums_goToWhoGotThereFirst() {
        List<Step> steps = resolve(100, "An", autoBid("An", 500, 0), autoBid("Binh", 500, 1));

        assertThat(asPairs(steps)).containsExactly(tuple("Binh", 490), tuple("An", 500));
    }

    @Test
    void equalMaximums_amongChallengersOfAManualLeader_goToTheOlderAutoBid() {
        List<Step> steps = resolve(150, "Zed", autoBid("An", 500, 0), autoBid("Binh", 500, 1));

        assertThat(asPairs(steps)).containsExactly(tuple("Binh", 490), tuple("An", 500));
    }

    @Test
    void aMaximumBelowTheNextValidBid_isIgnored() {
        // An leads at 100; Binh's old maximum of 105 cannot reach 110.
        assertThat(resolve(100, "An", autoBid("An", 500, 0), autoBid("Binh", 105, 1))).isEmpty();
    }

    // ---- more than two people

    @Test
    void theWinnerPaysTheSecondHighestMaximum_notTheThirdOne() {
        List<Step> steps = resolve(100, "An", autoBid("An", 500, 0), autoBid("Binh", 300, 1), autoBid("Chi", 450, 2));

        assertThat(asPairs(steps)).containsExactly(tuple("Chi", 450), tuple("An", 460));
    }

    @Test
    void aNewHighestMaximum_pricesAgainstTheOldLeadersMaximum_evenWithAStandingLoserBelow() {
        // An (500) already beat Binh (480) and stands at 490; Chi comes in with 520.
        List<Step> steps = resolve(490, "An", autoBid("An", 500, 0), autoBid("Binh", 480, 1), autoBid("Chi", 520, 2));

        assertThat(asPairs(steps)).containsExactly(tuple("An", 500), tuple("Chi", 510));
    }

    // ---- the history is always one full increment apart

    @Test
    void consecutiveBidsAreAlwaysAtLeastOneIncrementApart() {
        int[][] scenarios = {{500, 300}, {500, 495}, {500, 505}, {500, 510}, {500, 500}, {500, 130}, {250, 900}};
        for (int[] s : scenarios) {
            List<Step> steps = resolve(100, "An", autoBid("An", s[0], 0), autoBid("Binh", s[1], 1));

            BigDecimal previous = new BigDecimal(100);
            for (Step step : steps) {
                assertThat(step.amount().subtract(previous)).as("scenario %d/%d step %s", s[0], s[1], step)
                        .isGreaterThanOrEqualTo(INCREMENT);
                previous = step.amount();
            }
        }
    }

    @Test
    void theWinnerNeverPaysMoreThanItsOwnMaximum() {
        List<Step> steps = resolve(100, "An", autoBid("An", 500, 0), autoBid("Binh", 505, 1));

        assertThat(steps.get(steps.size() - 1).amount()).isLessThanOrEqualTo(new BigDecimal(505));
    }

    @Test
    void theSameAuction_givesTheSameWinnerWhoeverArrivesFirst() {
        // The highest maximum wins in both orders of arrival (Binh 505 against An 500, from a clean auction).
        List<Step> anFirst = resolve(100, "An", autoBid("An", 500, 0), autoBid("Binh", 505, 1));
        List<Step> binhFirst = resolve(100, "Binh", autoBid("Binh", 505, 0), autoBid("An", 500, 1));

        assertThat(anFirst.get(anFirst.size() - 1).bidderId()).isEqualTo("Binh");
        assertThat(binhFirst.get(binhFirst.size() - 1).bidderId()).isEqualTo("Binh");
        assertThat(anFirst.get(anFirst.size() - 1).amount()).isEqualByComparingTo(binhFirst.get(binhFirst.size() - 1).amount());
    }
}
