package io.canvasmc.canvas.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A location in the server with a nullable pos, pitch, and yaw. It is worth noting that if {@code pos}, {@code pitch},
 * and {@code yaw} are all null, this is being used as a <b>world destination</b> target, not as a <b>specific</b>
 * location target
 *
 * @param level
 *     the world target
 * @param pos
 *     the nullable position in the world
 * @param yaw
 *     the nullable yaw at the location
 * @param pitch
 *     the nullable pitch at the location
 *
 * @author dueris
 */
public record ServerLocation(ServerLevel level, @Nullable Vec3 pos, @Nullable Float yaw, @Nullable Float pitch) {
}
