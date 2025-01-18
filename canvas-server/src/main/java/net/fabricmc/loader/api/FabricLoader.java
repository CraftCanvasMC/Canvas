package net.fabricmc.loader.api;

import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implemented for AutoConfig
 */
public interface FabricLoader {
    static @NotNull FabricLoader getInstance() {
        return () -> Paths.get("./");
    }
    Path getConfigDir();
}
