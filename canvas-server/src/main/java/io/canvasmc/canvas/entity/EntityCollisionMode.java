package io.canvasmc.canvas.entity;

public enum EntityCollisionMode {
    VANILLA,
    ONLY_PUSHABLE_PLAYERS_LARGE,
    ONLY_PUSHABLE_PLAYERS_SMALL,
    NO_COLLISIONS;

    private static final EntityCollisionMode[] VALUES = values();

    public static EntityCollisionMode fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) {
            return VANILLA;
        }
        return VALUES[ordinal];
    }

    private final int id;

    EntityCollisionMode() {
        this.id = ordinal();
    }

    public int getId() {
        return this.id;
    }

    public boolean onlyPlayersPushable() {
        return id == ONLY_PUSHABLE_PLAYERS_LARGE.id || id == ONLY_PUSHABLE_PLAYERS_SMALL.id;
    }

    public boolean allEntitiesCanBePushed() {
        return id == VANILLA.id;
    }

    public boolean noCollisions() {
        return id == NO_COLLISIONS.id;
    }

    public boolean isLargePushRange() {
        return id == ONLY_PUSHABLE_PLAYERS_LARGE.id;
    }
}
