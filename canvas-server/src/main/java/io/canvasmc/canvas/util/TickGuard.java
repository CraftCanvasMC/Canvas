package io.canvasmc.canvas.util;

import ca.spottedleaf.moonrise.common.util.EntityUtil;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import io.canvasmc.canvas.GlobalConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import static ca.spottedleaf.moonrise.common.util.TickThread.getThreadContext;
import static io.canvasmc.canvas.GlobalConfiguration.LOGGER;

public class TickGuard {

    public static void guard(final BlockPos pos, final Level world, String reason) {
        switch (GlobalConfiguration.getInstance().regionScheduler.guardSeverity) {
            case SILENT -> ensureIsTickThread(reason);
            case LOG -> {
                // ensure tick thread first, since that is required, then we check and log
                ensureIsTickThread(reason);
                if (!TickThread.isTickThreadFor(world, pos)) {
                    LOGGER.warn("Thread failed main thread check: {}, context={}, world={}, block_pos={}", reason, getThreadContext(), WorldUtil.getWorldName(world), pos, new Throwable());
                }
            }
            case THROW -> TickThread.ensureTickThread(world, pos, reason);
        }
    }

    public static void guard(final Entity entity, String reason) {
        switch (GlobalConfiguration.getInstance().regionScheduler.guardSeverity) {
            case SILENT -> ensureIsTickThread(reason);
            case LOG -> {
                // ensure tick thread first, since that is required, then we check and log
                ensureIsTickThread(reason);
                if (!TickThread.isTickThreadFor(entity)) {
                    LOGGER.warn("Thread failed main thread check: {}, context={}, entity={}", reason, getThreadContext(), EntityUtil.dumpEntity(entity), new Throwable());
                }
            }
            case THROW -> TickThread.ensureTickThread(entity, reason);
        }
    }

    private static void ensureIsTickThread(String reason) {
        if (!TickThread.isTickThread()) {
            LOGGER.error("Thread failed main thread check: {}, context={}", reason, getThreadContext(), new Throwable());
            throw new IllegalStateException(reason);
        }
    }
}
