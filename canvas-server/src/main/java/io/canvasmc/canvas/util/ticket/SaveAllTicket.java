package io.canvasmc.canvas.util.ticket;

import java.util.function.Consumer;

public record SaveAllTicket(Runnable callback, Consumer<Throwable> exceptionPropagator, boolean flush) {
}
