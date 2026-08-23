package io.canvasmc.canvas.util.ticket;

import java.util.function.Consumer;

/**
 * Represents a ticket for the {@code save-all} command trigger, propagated in
 * {@link io.papermc.paper.threadedregions.TickRegions.TickRegionData#canvas$saveAllTicket}, at the start of each region
 * tick this ticket is consumed and upon consumption the chunks and players in the region in question will be saved and
 * optionally flushed depending on the {@link SaveAllTicket#flush() argument}
 *
 * @param callback
 *     the callback on completion of the save
 * @param exceptionPropagator
 *     the exception callback for if errors occur
 * @param flush
 *     if the region should flush when doing the chunk save or not
 *
 * @author dueris
 */
public record SaveAllTicket(Runnable callback, Consumer<Throwable> exceptionPropagator, boolean flush) {
}
