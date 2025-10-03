package io.canvasmc.canvas.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public record ServerLocation(ServerLevel world, Vec3 pos, float yaw, float pitch) {
}
