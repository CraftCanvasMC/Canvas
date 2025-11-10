package io.canvasmc.canvas.util.ticket;

import java.util.function.Consumer;

public record UnloadTicket(Consumer<Boolean> callback, boolean save) {
}
