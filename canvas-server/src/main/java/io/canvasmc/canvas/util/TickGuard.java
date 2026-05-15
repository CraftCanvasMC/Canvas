package io.canvasmc.canvas.util;

import ca.spottedleaf.moonrise.common.util.EntityUtil;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import io.canvasmc.canvas.GlobalConfiguration;
import io.papermc.paper.threadedregions.TickRegions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.function.BooleanSupplier;

import static ca.spottedleaf.moonrise.common.util.TickThread.getThreadContext;
import static io.canvasmc.canvas.GlobalConfiguration.LOGGER;

public class TickGuard {

    public static void guard(final @NonNull BlockPos pos, final Level world, String reason) {
        guard(pos.getX() >> 4, pos.getZ() >> 4, world, reason);
    }

    public static void guard(final int chunkX, final int chunkZ, final Level world, String reason) {
        switch (GlobalConfiguration.getInstance().regionScheduler.guardSeverity) {
            case SILENT -> ensureIsTickThread(reason);
            case LOG -> {
                // ensure tick thread first, since that is required, then we check and log
                ensureIsTickThread(reason);
                if (!TickThread.isTickThreadFor(world, chunkX, chunkZ)) {
                    LOGGER.warn("Thread failed main thread check: {}, context={}, world={}, chunk_pos={}", reason, getThreadContext(), WorldUtil.getWorldName(world), new ChunkPos(chunkX, chunkZ), new Throwable());
                }
            }
            case THROW -> TickThread.ensureTickThread(world, chunkX, chunkZ, reason);
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

    public static void hardThrowIfStarted(final BooleanSupplier isTickThreadFor, final String reason) {
        if (TickRegions.started && !isTickThreadFor.getAsBoolean()) {
            LOGGER.error("Thread failed main thread check: {}, context={}", reason, getThreadContext(), new Throwable());
            throw new IllegalStateException(reason);
        }
    }

    private static void ensureIsTickThread(String reason) {
        if (!TickThread.isTickThread()) {
            LOGGER.error("Thread failed main thread check: {}, context={}", reason, getThreadContext(), new Throwable());
            throw new IllegalStateException(reason);
        }
    }
}
