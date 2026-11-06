package io.canvasmc.amethyst.source;

import io.canvasmc.amethyst.proto.Samples;
import io.canvasmc.amethyst.table.IndexTable;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.regex.Pattern;

/**
 * The source of every class, cached. Index 0 is always the server itself
 *
 * @author dueris
 */
public final class SourceTable {

    // lambdas and hidden classes can't be looked up by name, their host class can
    private static final Pattern GENERATED_SUFFIX = Pattern.compile("\\$\\$Lambda.*|\\.0x.*");

    private final SourceResolver resolver;
    private final IndexTable<SourceInfo> sources = new IndexTable<>();
    private final Object2IntOpenHashMap<String> byClass = new Object2IntOpenHashMap<>();

    public SourceTable(final SourceResolver resolver) {
        this.resolver = resolver;
        this.byClass.defaultReturnValue(-1);

        // add the internal source ourselves, since we cant guarantee the platform will remember
        sources.add(SourceInfo.INTERNALS);

        try {
            // add all platform sources
            resolver.known().forEach(sources::add);
        } catch (final Throwable ignored) {
            // works without them, they just aren't listed until sampled :rgb_shrug:
        }
    }

    public int of(final String className) {
        final String host = GENERATED_SUFFIX.matcher(className).replaceFirst("");
        final int cached = byClass.getInt(host);
        if (cached != -1) {
            return cached;
        }

        // try and identify the source, if we cant do it, just assume internals
        SourceInfo source;
        try {
            source = resolver.identify(host);
        } catch (final RuntimeException | LinkageError ignored) {
            source = SourceInfo.INTERNALS;
        }

        final int index = sources.add(source);
        byClass.put(host, index);
        return index;
    }

    public void write(final Samples.Builder out) {
        for (final SourceInfo source : sources.unwritten()) {
            out.addSources(source.toProto());
        }
    }
}
