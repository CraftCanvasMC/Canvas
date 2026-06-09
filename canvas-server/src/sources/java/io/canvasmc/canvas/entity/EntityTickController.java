package io.canvasmc.canvas.entity;

import io.canvasmc.canvas.WorldConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;

public final class EntityTickController {

    private EntityTickController() {
    }

    public static boolean shouldSkipTick(final Entity entity) {
        if (entity instanceof Player) {
            return false;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        final WorldConfig.Entities.TickIntervals intervals = level.canvasConfig().entities.tickIntervals;
        final int interval = resolveInterval(intervals, entity.getType().getCategory());
        return interval > 1 && (entity.tickCount % interval != 0);
    }

    private static int resolveInterval(final WorldConfig.Entities.TickIntervals config, final MobCategory category) {
        return switch (category) {
            case MONSTER -> config.monster;
            case CREATURE -> config.creature;
            case AMBIENT -> config.ambient;
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE -> config.waterCreature;
            case MISC -> config.misc;
        };
    }
}
