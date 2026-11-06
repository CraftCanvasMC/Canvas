package io.canvasmc.amethyst.table;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;

/**
 * Distinct values in insertion order, each with a stable index. Every table in the report should use this
 *
 * @param <T> the generic type
 * @author dueris
 */
public final class IndexTable<T> {

    private final List<T> values = new ObjectArrayList<>();
    private final Object2IntOpenHashMap<T> index = new Object2IntOpenHashMap<>();

    private int written;

    public IndexTable() {
        index.defaultReturnValue(-1);
    }

    public int add(final T value) {
        final int existing = index.getInt(value);
        if (existing != -1) {
            return existing;
        }

        values.add(value);
        index.put(value, values.size() - 1);
        return values.size() - 1;
    }

    // the values added since the last call
    public List<T> unwritten() {
        final List<T> fresh = values.subList(written, values.size());
        written = values.size();
        return fresh;
    }
}
