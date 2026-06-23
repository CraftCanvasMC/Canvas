package io.canvasmc.canvas.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// note: if pos, yaw, and pitch are null, we are portaling
public record ServerLocation(ServerLevel level, @Nullable Vec3 pos, @Nullable Float yaw, @Nullable Float pitch) {
}
