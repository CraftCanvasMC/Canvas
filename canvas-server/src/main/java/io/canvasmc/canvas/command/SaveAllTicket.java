package io.canvasmc.canvas.command;

import java.util.function.Consumer;

public record SaveAllTicket(Runnable callback, Consumer<Throwable> exceptionPropagator, boolean flush) {
}
