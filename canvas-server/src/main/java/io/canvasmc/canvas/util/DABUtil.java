package io.canvasmc.canvas.util;

import io.canvasmc.canvas.Config;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;

public class DABUtil {
    public static boolean checkActive(Entity entity) {
        if (entity instanceof Player || entity.isPassenger() || entity.isVehicle()) {
            return true;
        }

        if (entity instanceof LivingEntity living && living.hurtTime > 0) {
            return true;
        }

        if (entity instanceof Villager villager && villager.isNoAi()) {
            return false;
        }

        int maxDelay = Config.INSTANCE.entities.dab.maxTickDelay;
        int activationRangeMod = Config.INSTANCE.entities.dab.activationRangeMod;

        // Simplified DAB logic: scale tick delay based on distance to nearest player
        // In a real implementation, we'd use the server's MSPT to scale this further.
        // For now, let's keep it simple and effective.
        
        double nearestPlayerDist = Double.MAX_VALUE;
        for (Player player : entity.level().players()) {
            double dist = entity.distanceToSqr(player);
            if (dist < nearestPlayerDist) {
                nearestPlayerDist = dist;
            }
        }

        int delay = 1;
        if (nearestPlayerDist > (activationRangeMod * activationRangeMod)) {
            delay = Math.min(maxDelay, (int) (Math.sqrt(nearestPlayerDist) / activationRangeMod));
        }

        return MinecraftServer.currentTick % delay == 0;
    }
}
