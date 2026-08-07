package io.canvasmc.canvas.util;

import com.google.common.base.Preconditions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.util.CraftLocation;

/**
 * A location in the server with a world, specific position, yaw, and float. All are considered nonnull
 *
 * @param level
 *     the world
 * @param pos
 *     the position in the world
 * @param yaw
 *     the yaw at the position
 * @param pitch
 *     the pitch at the position
 *
 * @author dueris
 */
public record ServerLocation(ServerLevel level, Vec3 pos, float yaw, float pitch) {

    public static ServerLocation fromBukkit(final Location location) {
        Preconditions.checkNotNull(location.getWorld(), "Cannot pass location with null world to server location construction");
        final ServerLevel world = ((CraftWorld) location.getWorld()).getHandle();

        return new ServerLocation(
            world,
            CraftLocation.toVec3(location),
            location.getYaw(),
            location.getPitch()
        );
    }

    @Override
    public String toString() {
        return "ServerLocation=[world=" + Util.getLevelName(level) + ",pos=" + pos + ",yaw=" + yaw + ",pitch=" + pitch + "]";
    }
}
