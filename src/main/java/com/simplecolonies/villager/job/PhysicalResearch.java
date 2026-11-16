package com.simplecolonies.villager.job;

import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;

/** One builder carries the inputs of a persisted research order to a real civic site. */
final class PhysicalResearch {
    enum Result { NONE, MOVING, WORKING, MISSING, BLOCKED }
    private final ColonyVillager worker;
    private final Colony colony;
    private final PhysicalBuilderMaterials materials;

    PhysicalResearch(ColonyVillager worker, Colony colony) {
        this.worker = worker;
        this.colony = colony;
        this.materials = new PhysicalBuilderMaterials(worker, colony, worker.getResearchMaterials());
    }

    Result tick(ServerLevel level) {
        var research = colony.getResearch();
        var topic = research.getPending();
        var owner = research.getAssignedWorker();
        boolean mine = topic != null && worker.getUUID().equals(owner);
        // Cancellation or reassignment leaves actual inputs on the original worker.
        // Return them before taking a new order; never convert them to virtual stock.
        if (!mine && !worker.getResearchMaterials().isEmpty()) return map(materials.returnSurplus(level));
        if (topic == null || !research.claimWorker(worker.getUUID())) return Result.NONE;
        if (!research.canComplete(worker.getUUID(), topic)) return Result.BLOCKED;

        var station = colony.getBuildings().stream()
                .filter(b -> b.isOperational() && (b.getType() == BuildingType.TOWN_HALL || b.getType() == BuildingType.LIBRARY))
                .map(b -> b.getPos())
                .filter(p -> colony.isInside(p) && level.isLoaded(p))
                .min(java.util.Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).orElse(null);
        if (station == null) return Result.BLOCKED;
        // Fetch exact plain items, never virtual stock and never a partial payment.
        for (var entry : topic.cost.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            var result = materials.prepareCount(level, entry.getKey(), entry.getValue());
            if (result != PhysicalBuilderMaterials.Result.READY) return map(result);
        }
        var approach = materials.approachResearchSite(level, station);
        if (approach != PhysicalBuilderMaterials.Result.READY) return map(approach);
        // The reserve can change while the builder is walking; do not spend it.
        if (!research.hasAutomaticReserve(level, topic) || !research.canComplete(worker.getUUID(), topic)) return Result.MISSING;
        for (var entry : topic.cost.entrySet()) if (materials.countPrepared(entry.getKey()) < entry.getValue()) return Result.MISSING;
        var consumed = new java.util.LinkedHashMap<String, Integer>();
        for (var entry : topic.cost.entrySet()) {
            if (!materials.takePrepared(entry.getKey(), entry.getValue())) {
                consumed.forEach(materials::refund);
                return Result.BLOCKED;
            }
            consumed.put(entry.getKey(), entry.getValue());
        }
        if (!research.complete(worker.getUUID(), topic, level)) {
            consumed.forEach(materials::refund);
            return Result.BLOCKED;
        }
        worker.swing(InteractionHand.MAIN_HAND);
        return Result.WORKING;
    }

    Result returnCargo(ServerLevel level) { return map(materials.returnSurplus(level)); }

    private static Result map(PhysicalBuilderMaterials.Result result) {
        return switch (result) {
            case READY -> Result.NONE;
            case MOVING -> Result.MOVING;
            case WORKING -> Result.WORKING;
            case MISSING -> Result.MISSING;
            case BLOCKED -> Result.BLOCKED;
        };
    }
}
