package io.canvasmc.canvas.server;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface AbstractTick {
    void process(BooleanSupplier shouldKeepTicking, int tickCount);

    default void processAndThen(BooleanSupplier shouldKeepTicking, int tickCount, @NotNull Runnable postTick) {
        process(shouldKeepTicking, tickCount);
        postTick.run();
    }

    default BiConsumer<BooleanSupplier, Integer> andThen(BiConsumer<? super BooleanSupplier, ? super Integer> after) {
        Objects.requireNonNull(after);

        return (l, r) -> {
            process(l, r);
            after.accept(l, r);
        };
    }
}
