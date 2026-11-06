package io.canvasmc.amethyst.source;

import java.util.List;

/**
 * The source resolver for Amethyst. Used for identifying plugins and mods and their sources from their class name
 *
 * @author dueris
 */
public interface SourceResolver {
    SourceResolver INTERNALS_ONLY = new SourceResolver() {
        @Override
        public List<SourceInfo> known() {
            return List.of();
        }

        @Override
        public SourceInfo identify(final String className) {
            return SourceInfo.INTERNALS;
        }
    };

    /**
     * @return EVERY source the platform knows about, even if the code isnt sampled
     */
    List<SourceInfo> known();

    /**
     * Identifies the source from the binary class name
     *
     * @param className
     *     the binary class name, like {@code com.example.Plugin$Inner}
     *
     * @return {@link io.canvasmc.amethyst.source.SourceInfo#INTERNALS} when isnt a plugin or library
     */
    SourceInfo identify(final String className);
}
