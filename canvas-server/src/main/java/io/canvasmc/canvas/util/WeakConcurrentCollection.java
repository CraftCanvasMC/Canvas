package io.canvasmc.canvas.util;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A concurrent collection version of {@link org.bukkit.craftbukkit.util.WeakCollection} from CraftBukkit
 *
 * @param <E>
 *     the element type
 *
 * @author dueris
 */
public class WeakConcurrentCollection<E> implements Collection<E> {
    private final CopyOnWriteArrayList<WeakReference<E>> backed = new CopyOnWriteArrayList<>();
    private final AtomicInteger liveCount = new AtomicInteger(0);

    @Override
    public int size() {
        int count = 0;
        for (E _ : this) count++;
        return count;
    }

    @Override
    public boolean isEmpty() {
        if (liveCount.get() <= 0) return true;
        for (WeakReference<E> ref : backed) {
            if (ref.get() != null) return false;
        }
        return true;
    }

    @Override
    public boolean contains(final Object o) {
        if (o == null) return false;
        for (E value : this) {
            if (o.equals(value)) return true;
        }
        return false;
    }

    @Override
    public @NonNull Iterator<E> iterator() {
        final List<WeakReference<E>> snapshot = List.copyOf(backed);
        return new Iterator<>() {
            private final Iterator<WeakReference<E>> it = snapshot.iterator();
            private @Nullable E next = null;
            private @Nullable E lastResolved = null;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                while (it.hasNext()) {
                    E value = it.next().get();
                    if (value != null) {
                        next = value;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public @Nullable E next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                lastResolved = next;
                next = null;
                return lastResolved;
            }

            @Override
            public void remove() {
                Preconditions.checkState(lastResolved != null, "No element to remove, call next() first");
                WeakConcurrentCollection.this.remove(lastResolved);
                lastResolved = null;
            }
        };
    }

    @Override
    public @NonNull Object @NonNull [] toArray() {
        return new ObjectArrayList<>(this).toArray();
    }

    @Override
    public @NonNull <T> T @NonNull [] toArray(@NonNull final T @NonNull [] a) {
        return new ObjectArrayList<>(this).toArray(a);
    }

    @Override
    public boolean add(final E value) {
        Preconditions.checkArgument(value != null, "Cannot add null value");
        backed.add(new WeakReference<>(value));
        liveCount.incrementAndGet();
        compactIfNeeded();
        return true;
    }

    @Override
    public boolean remove(final Object o) {
        if (o == null) return false;
        for (WeakReference<E> ref : backed) {
            E value = ref.get();
            if (o.equals(value)) {
                ref.clear();
                if (backed.remove(ref)) {
                    liveCount.decrementAndGet();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsAll(@NonNull final Collection<?> c) {
        for (final Object o : c) {
            if (!contains(o)) return false;
        }
        return true;
    }

    @Override
    public boolean addAll(@NonNull final Collection<? extends E> c) {
        boolean changed = false;
        for (E value : c) changed |= add(value);
        return changed;
    }

    @Override
    public boolean removeAll(@NonNull final Collection<?> c) {
        boolean changed = false;
        for (WeakReference<E> ref : backed) {
            E value = ref.get();
            if (value != null && c.contains(value)) {
                ref.clear();
                backed.remove(ref);
                liveCount.decrementAndGet();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean retainAll(@NonNull final Collection<?> c) {
        boolean changed = false;
        for (WeakReference<E> ref : backed) {
            E value = ref.get();
            if (value != null && !c.contains(value)) {
                ref.clear();
                backed.remove(ref);
                liveCount.decrementAndGet();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clear() {
        for (WeakReference<E> ref : backed) ref.clear();
        backed.clear();
        liveCount.set(0);
    }

    public void compact() {
        int removed = 0;
        for (WeakReference<E> ref : backed) {
            if (ref.get() == null && backed.remove(ref)) removed++;
        }
        if (removed > 0) liveCount.addAndGet(-removed);
    }

    private void compactIfNeeded() {
        int total = backed.size();
        int live = liveCount.get();
        if (total > 0 && (total - live) * 4 >= total) compact();
    }
}
