package io.canvasmc.canvas.config.internal;

import java.util.function.Supplier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public interface ConfigHolder<T> extends Supplier<T> {
    @NotNull Class<T> getConfigClass();

    void save();

    boolean load();

    T getConfig();
    void setConfig(T var1);
    default T get() {
        return this.getConfig();
    }
    void resetToDefault();
}
