package io.canvasmc.canvas.util;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A list that returns itself for methods that modify the backing set
 *
 * @param <T>
 *     the type
 *
 * @author dueris
 */
public class CollectingList<T> {
    private final ObjectArrayList<T> backing = new ObjectArrayList<>();

    public int size() {
        return this.backing.size();
    }

    public boolean isEmpty() {
        return this.backing.isEmpty();
    }

    public @NonNull Iterator<T> iterator() {
        return this.backing.iterator();
    }

    public Collection<T> copyOf() {
        return new ObjectArrayList<>(this.backing);
    }

    public <N> CollectingList<N> castAllTo(@NonNull Class<N> nClass) {
        return new CollectingList<N>().addAll(this.backing.stream().map(nClass::cast).toList());
    }

    public CollectingList<T> addAllNotPresent(final @NonNull Collection<T> tCollection) {
        for (final T t : tCollection) {
            if (this.backing.contains(t)) {
                continue;
            }
            this.backing.add(t);
        }
        return this;
    }

    public CollectingList<T> addAll(final Collection<T> tCollection) {
        this.backing.addAll(tCollection);
        return this;
    }

    public CollectingList<T> add(final T t) {
        this.backing.add(t);
        return this;
    }

    public CollectingList<T> remove(final T t) {
        this.backing.remove(t);
        return this;
    }

    public CollectingList<T> sort(@Nullable final Comparator<? super T> c) {
        this.backing.sort(c);
        return this;
    }

    public CollectingList<T> clear() {
        this.backing.clear();
        return this;
    }

    public T get(final int index) {
        return this.backing.get(index);
    }

    public T getFirst() {
        return this.backing.getFirst();
    }

    public T getLast() {
        return this.backing.getLast();
    }
}
