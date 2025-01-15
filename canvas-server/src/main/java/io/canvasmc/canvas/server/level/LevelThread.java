package io.canvasmc.canvas.server.level;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.level.ServerLevel;

public class LevelThread extends TickThread {
    private final ServerLevel level;

    public LevelThread(final ThreadGroup group, final Runnable run, final String name, final ServerLevel level) {
        super(group, run, name);
        this.level = level;
    }

    public ServerLevel getLevel() {
        return level;
    }
}
