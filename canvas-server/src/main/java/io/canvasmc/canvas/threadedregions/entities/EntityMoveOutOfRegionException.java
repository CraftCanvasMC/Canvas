package io.canvasmc.canvas.threadedregions.entities;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class EntityMoveOutOfRegionException extends RuntimeException {
    private final Entity entity;
    private final MoverType moverType;
    private final Vec3 movement;

    public EntityMoveOutOfRegionException(
        Entity entity,
        MoverType moverType,
        Vec3 movement
    ) {
        this.entity = entity;
        this.moverType = moverType;
        this.movement = movement;
    }

    public Entity entity() {
        return this.entity;
    }

    public MoverType moverType() {
        return this.moverType;
    }

    public Vec3 movement() {
        return this.movement;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
