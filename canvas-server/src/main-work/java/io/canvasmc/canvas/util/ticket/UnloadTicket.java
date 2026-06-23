package io.canvasmc.canvas.util.ticket;

import io.canvasmc.canvas.WorldUnloadResult;
import java.util.function.Consumer;

public record UnloadTicket(Consumer<WorldUnloadResult> callback, boolean save) {
}
