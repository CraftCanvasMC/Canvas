package io.canvasmc.amethyst;

import io.canvasmc.amethyst.source.SourceResolver;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What the server running Amethyst provides. The platform sets its implementation once at startup, and everything else
 * reads it with {@link #get()}
 *
 * @author dueris
 */
public interface PlatformImplementation {

    /**
     * @return the platform implementation
     *
     * @throws IllegalStateException
     *     if not set yet
     */
    static PlatformImplementation get() {
        final PlatformImplementation instance = Holder.INSTANCE;
        if (instance == null) {
            throw new IllegalStateException("The platform implementation hasnt been set yet");
        }
        return instance;
    }

    /**
     * @param implementation
     *     the platform implementation
     *
     * @throws IllegalStateException
     *     if one was already set
     */
    static void set(final PlatformImplementation implementation) {
        synchronized (Holder.class) {
            if (Holder.INSTANCE != null) {
                throw new IllegalStateException("The platform implementation was already set");
            }
            Holder.INSTANCE = implementation;
        }
    }

    String getPlatformVer();

    String getPlatformName();

    List<String> getPlatformAuthors();

    SourceResolver getResolver();

    final class Holder {
        @Nullable
        private static volatile PlatformImplementation INSTANCE;

        private Holder() {
        }
    }
}
