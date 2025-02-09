package io.canvasmc.canvas;

import org.bukkit.World;

public interface LevelAccess {
    World getWorld();
    void scheduleOnThread(Runnable runnable);
    void scheduleForPreNextTick(Runnable runnable);
    void scheduleForPostNextTick(Runnable runnable);
    boolean isTicking();
}
