package io.canvasmc.canvas.util;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
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
        for (final E _ : this) count++;
        return count;
    }

    @Override
    public boolean isEmpty() {
        if (liveCount.get() <= 0) return true;
        for (final WeakReference<E> weak : backed) {
            if (weak.get() != null) return false;
        }
        return true;
    }

    @Override
    public boolean contains(final @Nullable Object obj) {
        if (obj == null) return false;
        for (final E element : this) {
            if (obj.equals(element)) return true;
        }
        return false;
    }

    @Override
    public Iterator<E> iterator() {
        final List<WeakReference<E>> snapshot = List.copyOf(backed);
        return new Iterator<>() {
            private final Iterator<WeakReference<E>> it = snapshot.iterator();
            private @Nullable E next = null;
            private @Nullable E lastResolved = null;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                while (it.hasNext()) {
                    final E value = it.next().get();
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
    public Object[] toArray() {
        return new ObjectArrayList<>(this).toArray();
    }

    @Override
    public <T> T[] toArray(final T[] arr) {
        return new ObjectArrayList<>(this).toArray(arr);
    }

    @Override
    public boolean add(final E element) {
        Objects.requireNonNull(element, "cannot add null element");
        backed.add(new WeakReference<>(element));
        liveCount.incrementAndGet();
        compactIfNeeded();
        return true;
    }

    @Override
    public boolean remove(final @Nullable Object obj) {
        if (obj == null) return false;
        for (final WeakReference<E> ref : backed) {
            final E value = ref.get();
            if (obj.equals(value)) {
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
    public boolean containsAll(final Collection<?> collection) {
        for (final Object obj : collection) {
            if (!contains(obj)) return false;
        }
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends E> collection) {
        boolean changed = false;
        for (final E value : collection) changed |= add(value);
        return changed;
    }

    @Override
    public boolean removeAll(final Collection<?> collection) {
        boolean changed = false;
        for (final WeakReference<E> ref : backed) {
            final E value = ref.get();
            if (value != null && collection.contains(value)) {
                ref.clear();
                if (backed.remove(ref)) {
                    liveCount.decrementAndGet();
                }
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean retainAll(final Collection<?> collection) {
        boolean changed = false;
        for (final WeakReference<E> ref : backed) {
            final E value = ref.get();
            if (value != null && !collection.contains(value)) {
                ref.clear();
                if (backed.remove(ref)) {
                    liveCount.decrementAndGet();
                }
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clear() {
        for (final WeakReference<E> weak : backed) weak.clear();
        backed.clear();
        liveCount.set(0);
    }

    public void compact() {
        int removed = 0;
        for (final WeakReference<E> weak : backed) {
            if (weak.get() == null && backed.remove(weak)) removed++;
        }
        if (removed > 0) liveCount.addAndGet(-removed);
    }

    private void compactIfNeeded() {
        final int total = backed.size();
        final int live = liveCount.get();
        if (total > 0 && (total - live) * 4 >= total) compact();
    }
}
