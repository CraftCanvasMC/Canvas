package io.canvasmc.canvas.util.ticket;

import io.canvasmc.canvas.WorldUnloadResult;
import java.util.function.Consumer;

/**
 * Represents a ticket propagated to a {@link net.minecraft.server.level.ServerLevel} instance when the world should be
 * marked for unloading. Regions read this ticket and mark themselves as non-schedulable via
 * {@link io.papermc.paper.threadedregions.TickRegionScheduler.RegionScheduleHandle#markNonSchedulable} while the
 * {@link io.canvasmc.canvas.threadedregions.WorldShutdownThread} instance that is handling the world unload waits for
 * all regions to deschedule or be killed by the regionizer
 *
 * @param callback
 *     the callback provided by the API to run on completion of the unload
 * @param save
 *     whether to save chunks before unload, however this is always handled as true in modern implementations of the
 *     unload system and will be removed in the future
 *
 * @author dueris
 */
public record UnloadTicket(Consumer<WorldUnloadResult> callback, boolean save) {
}
