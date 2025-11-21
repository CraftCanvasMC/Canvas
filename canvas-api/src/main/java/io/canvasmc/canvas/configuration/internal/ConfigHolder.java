package io.canvasmc.canvas.configuration.internal;

import java.util.function.Supplier;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface ConfigHolder<T> extends Supplier<T> {
    Class<T> getConfigClass();

    void save();

    boolean load();

    T getConfig();

    void setConfig(T var1);

    default T get() {
        return this.getConfig();
    }

    void resetToDefault();
}
