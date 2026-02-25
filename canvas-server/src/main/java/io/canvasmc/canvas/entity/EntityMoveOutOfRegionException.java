package io.canvasmc.canvas.entity;

public final class EntityMoveOutOfRegionException extends RuntimeException {
    private final net.minecraft.world.entity.Entity entity;
    private final net.minecraft.world.entity.MoverType moverType;
    private final net.minecraft.world.phys.Vec3 movement;

    public EntityMoveOutOfRegionException(
        net.minecraft.world.entity.Entity entity,
        net.minecraft.world.entity.MoverType moverType,
        net.minecraft.world.phys.Vec3 movement
    ) {
        this.entity = entity;
        this.moverType = moverType;
        this.movement = movement;
    }

    public net.minecraft.world.entity.Entity entity() {
        return this.entity;
    }

    public net.minecraft.world.entity.MoverType moverType() {
        return this.moverType;
    }

    public net.minecraft.world.phys.Vec3 movement() {
        return this.movement;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
