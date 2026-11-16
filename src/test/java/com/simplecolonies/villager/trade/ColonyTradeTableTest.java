package com.simplecolonies.villager.trade;

import com.simplecolonies.villager.job.JobType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColonyTradeTableTest {
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    @Test void everyJobHasAUsefulLevelOneTable() {
        for (JobType job : JobType.values()) {
            List<ColonyTradeTable.Offer> offers = ColonyTradeTable.offers(job, 1);
            assertNotNull(offers);
            assertTrue(offers.size() >= 2, job + " should offer at least two trades at level one");
            assertTrue(offers.stream().anyMatch(o -> o.costItem().equals(ColonyTradeTable.EMERALD)),
                    job + " should have at least one offer the player pays emeralds for");
            assertTrue(offers.stream().anyMatch(o -> o.resultItem().equals(ColonyTradeTable.EMERALD)),
                    job + " should have at least one offer that earns emeralds");
        }
    }

    @Test void offersAreWellFormed() {
        for (JobType job : JobType.values()) {
            for (ColonyTradeTable.Offer offer : ColonyTradeTable.allOffers(job)) {
                assertTrue(ITEM_ID.matcher(offer.costItem()).matches(), "bad cost id " + offer.costItem());
                assertTrue(ITEM_ID.matcher(offer.resultItem()).matches(), "bad result id " + offer.resultItem());
                assertTrue(offer.costCount() > 0, "cost must be positive");
                assertTrue(offer.resultCount() > 0, "result must be positive");
                assertTrue(offer.costCount() <= 64 && offer.resultCount() <= 64, "stacks must fit one slot");
                assertTrue(offer.maxUses() >= 1 && offer.maxUses() <= 16, "uses must be bounded");
                assertTrue(offer.xp() >= 1 && offer.xp() <= 10, "xp must be modest");
                assertTrue(offer.minLevel() >= 1 && offer.minLevel() <= ColonyTradeTable.MAX_LEVEL,
                        "min level out of range");
                assertFalse(offer.costItem().equals(offer.resultItem()) && offer.costCount() == offer.resultCount(),
                        "a trade must not exchange an item for itself");
            }
        }
    }

    @Test void levelsUnlockMoreOffersButNeverFewer() {
        for (JobType job : JobType.values()) {
            int previous = 0;
            for (int level = 1; level <= ColonyTradeTable.MAX_LEVEL; level++) {
                int count = ColonyTradeTable.offers(job, level).size();
                assertTrue(count >= previous, job + " lost offers at level " + level);
                previous = count;
            }
            assertEquals(ColonyTradeTable.allOffers(job).size(), previous,
                    job + " should unlock its whole table by level " + ColonyTradeTable.MAX_LEVEL);
        }
    }

    @Test void levelIsClampedSoBadDataCannotHideTrades() {
        assertEquals(1, ColonyTradeTable.clampLevel(0));
        assertEquals(1, ColonyTradeTable.clampLevel(-5));
        assertEquals(ColonyTradeTable.MAX_LEVEL, ColonyTradeTable.clampLevel(99));
        assertEquals(ColonyTradeTable.offers(JobType.FARMER, 1), ColonyTradeTable.offers(JobType.FARMER, -3));
    }

    @Test void emeraldsAreTheOnlyCurrency() {
        for (JobType job : JobType.values()) {
            for (ColonyTradeTable.Offer offer : ColonyTradeTable.allOffers(job)) {
                assertTrue(offer.costItem().equals(ColonyTradeTable.EMERALD)
                                || offer.resultItem().equals(ColonyTradeTable.EMERALD),
                        "trade must use emeralds on one side: " + offer);
            }
        }
    }
}
