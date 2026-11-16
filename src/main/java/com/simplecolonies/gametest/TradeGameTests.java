package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.JobType;
import com.simplecolonies.villager.trade.ColonyTradeTable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class TradeGameTests {
    @GameTest(template = "empty")
    public static void colonyTradesFollowJobAndLevel(GameTestHelper helper) {
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 8));
        Colony colony = new Colony(-2000, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        worker.setColony(colony);

        worker.setJob(JobType.FARMER);
        var farmerOffers = worker.getOffers();
        int expectedFarmer = ColonyTradeTable.offers(JobType.FARMER, 1).size();
        if (farmerOffers.size() != expectedFarmer) {
            helper.fail("Farmer had " + farmerOffers.size() + " offers, expected " + expectedFarmer);
        }
        boolean sellsBread = false;
        for (MerchantOffer offer : farmerOffers) {
            if (offer.getResult().isEmpty()) helper.fail("Offer with an empty result was registered");
            if (offer.getResult().is(Items.BREAD)) sellsBread = true;
        }
        if (!sellsBread) helper.fail("Farmer does not sell bread from the colony table");

        // A job change replaces the whole list rather than appending another job's goods.
        worker.setJob(JobType.MINER);
        var minerOffers = worker.getOffers();
        int expectedMiner = ColonyTradeTable.offers(JobType.MINER, 1).size();
        if (minerOffers.size() != expectedMiner) {
            helper.fail("Miner had " + minerOffers.size() + " offers, expected " + expectedMiner);
        }
        boolean sellsMinerGoods = false;
        for (MerchantOffer offer : minerOffers) {
            if (offer.getResult().is(Items.BREAD)) helper.fail("Miner kept the farmer's bread trade");
            if (offer.getResult().is(Items.TORCH) || offer.getResult().is(Items.EMERALD)) sellsMinerGoods = true;
        }
        if (!sellsMinerGoods) helper.fail("Miner has no colony-relevant trade");

        // Higher job levels unlock the extra rows of the table.
        worker.setVillagerData(worker.getVillagerData().setLevel(3));
        worker.refreshColonyOffers();
        int expectedLevelThree = ColonyTradeTable.offers(JobType.MINER, 3).size();
        if (worker.getOffers().size() != expectedLevelThree) {
            helper.fail("Level-three miner had " + worker.getOffers().size() + " offers, expected " + expectedLevelThree);
        }
        helper.succeed();
    }
}
