package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

public final class FastUtilHackUtil {

    private FastUtilHackUtil() {
        throw new AssertionError("No instances");
    }

    private static <T> Int2ObjectMap.@NotNull Entry<T> intEntryForwards(Map.Entry<Integer, T> entry) {
        return new Int2ObjectMap.Entry<>() {
            @Override
            public T getValue() {
                return entry.getValue();
            }

            @Override
            public T setValue(T value) {
                return entry.setValue(value);
            }

            @Override
            public int getIntKey() {
                return entry.getKey();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == entry) {
                    return true;
                }
                return super.equals(obj);
            }

            @Override
            public int hashCode() {
                return entry.hashCode();
            }
        };
    }

    private static <K, T> Reference2ObjectMap.@NotNull Entry<K, T> refEntryForwards(Map.Entry<K, T> entry) {
        return new Reference2ObjectMap.Entry<>() {
            @Override
            public T getValue() {
                return entry.getValue();
            }

            @Override
            public T setValue(T value) {
                return entry.setValue(value);
            }

            @Override
            public K getKey() {
                return entry.getKey();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == entry) {
                    return true;
                }
                return super.equals(obj);
            }

            @Override
            public int hashCode() {
                return entry.hashCode();
            }
        };
    }

    private static <T> Map.Entry<Integer, T> intEntryBackwards(Int2ObjectMap.Entry<T> entry) {
        return entry;
    }

    private static <K, T> Map.Entry<K, T> refEntryBackwards(Reference2ObjectMap.Entry<K, T> entry) {
        return entry;
    }

    private static <T> Long2ObjectMap.Entry<T> longEntryForwards(Map.Entry<Long, T> entry) {
        return new Long2ObjectMap.Entry<>() {
            @Override
            public T getValue() {
                return entry.getValue();
            }

            @Override
            public T setValue(T value) {
                return entry.setValue(value);
            }

            @Override
            public long getLongKey() {
                return entry.getKey();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == entry) {
                    return true;
                }
                return super.equals(obj);
            }

            @Override
            public int hashCode() {
                return entry.hashCode();
            }
        };
    }

    private static <T> Map.Entry<Long, T> longEntryBackwards(Long2ObjectMap.Entry<T> entry) {
        return entry;
    }

    private static Long2ByteMap.Entry longByteEntryForwards(Map.Entry<Long, Byte> entry) {
        return new Long2ByteMap.Entry() {
            @Override
            public Byte getValue() {
                return entry.getValue();
            }

            @Override
            public byte setValue(byte value) {
                return entry.setValue(value);
            }

            @Override
            public byte getByteValue() {
                return entry.getValue();
            }

            @Override
            public long getLongKey() {
                return entry.getKey();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == entry) {
                    return true;
                }
                return super.equals(obj);
            }

            @Override
            public int hashCode() {
                return entry.hashCode();
            }
        };
    }

    private static Map.Entry<Long, Byte> longByteEntryBackwards(Long2ByteMap.Entry entry) {
        return entry;
    }

    private static Long2LongMap.Entry longLongEntryForwards(Map.Entry<Long, Long> entry) {
        return new Long2LongMap.Entry() {
            @Override
            public Long getValue() {
                return entry.getValue();
            }

            @Override
            public long setValue(long value) {
                return entry.setValue(value);
            }

            @Override
            public long getLongValue() {
                return entry.getValue();
            }

            @Override
            public long getLongKey() {
                return entry.getKey();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == entry) {
                    return true;
                }
                return super.equals(obj);
            }

            @Override
            public int hashCode() {
                return entry.hashCode();
            }
        };
    }

    private static Map.Entry<Long, Long> longLongEntryBackwards(Long2LongMap.Entry entry) {
        return entry;
    }

    // Utility methods
    public static <T> ObjectSet<Int2ObjectMap.Entry<T>> entrySetIntWrap(Map<Integer, T> map) {
        return new ConvertingObjectSet<>(
            map.entrySet(),
            FastUtilHackUtil::intEntryForwards,
            FastUtilHackUtil::intEntryBackwards
        );
    }

    public static <K, V> ObjectSet<Reference2ObjectMap.Entry<K, V>> entrySetRefWrap(Map<K, V> map) {
        return new ConvertingObjectSet<>(
            map.entrySet(),
            FastUtilHackUtil::refEntryForwards,
            FastUtilHackUtil::refEntryBackwards
        );
    }

    public static <T> ObjectSet<Long2ObjectMap.Entry<T>> entrySetLongWrap(Map<Long, T> map) {
        return new ConvertingObjectSet<>(
            map.entrySet(),
            FastUtilHackUtil::longEntryForwards,
            FastUtilHackUtil::longEntryBackwards
        );
    }

    public static <T> Long2ObjectMap.FastEntrySet<T> entrySetLongWrapFast(Map<Long, T> map) {
        return new ConvertingObjectSetFast<>(
            map.entrySet(),
            FastUtilHackUtil::longEntryForwards,
            FastUtilHackUtil::longEntryBackwards
        );
    }

    public static ObjectSet<Long2ByteMap.Entry> entrySetLongByteWrap(Map<Long, Byte> map) {
        return new ConvertingObjectSet<>(
            map.entrySet(),
            FastUtilHackUtil::longByteEntryForwards,
            FastUtilHackUtil::longByteEntryBackwards
        );
    }

    public static ObjectSet<Long2LongMap.Entry> entrySetLongLongWrap(Map<Long, Long> map) {
        return new ConvertingObjectSet<>(
            map.entrySet(),
            FastUtilHackUtil::longLongEntryForwards,
            FastUtilHackUtil::longLongEntryBackwards
        );
    }

    public static LongSet wrapLongSet(Set<Long> longset) {
        return new WrappingLongSet(longset);
    }

    public static LongSortedSet wrapLongSortedSet(Set<Long> longset) {
        return new WrappingLongSortedSet(longset);
    }

    public static IntSet wrapIntSet(Set<Integer> intset) {
        return new WrappingIntSet(intset);
    }

    public static ByteCollection wrapBytes(Collection<Byte> c) {
        return new WrappingByteCollection(c);
    }

    public static ByteIterator itrByteWrap(Iterator<Byte> backing) {
        return new WrappingByteIterator(backing);
    }

    public static ByteIterator itrByteWrap(Iterable<Byte> backing) {
        return itrByteWrap(backing.iterator());
    }

    public static IntIterator itrIntWrap(Iterator<Integer> backing) {
        return new WrappingIntIterator(backing);
    }

    public static IntIterator itrIntWrap(Iterable<Integer> backing) {
        return itrIntWrap(backing.iterator());
    }

    public static LongIterator itrLongWrap(Iterator<Long> backing) {
        return new WrappingLongIterator(backing);
    }

    public static LongIterator itrLongWrap(Iterable<Long> backing) {
        return itrLongWrap(backing.iterator());
    }

    public static ShortIterator itrShortWrap(Iterator<Short> backing) {
        return new WrappingShortIterator(backing);
    }

    public static ShortIterator itrShortWrap(Iterable<Short> backing) {
        return itrShortWrap(backing.iterator());
    }

    public static LongListIterator wrap(ListIterator<Long> c) {
        return new WrappingLongListIterator(c);
    }

    public static LongListIterator wrap(Iterator<Long> c) {
        return new SlimWrappingLongListIterator(c);
    }

    // Utility methods
    public static <K> ObjectCollection<K> wrap(Collection<K> c) {
        return new WrappingObjectCollection<>(c);
    }

    public static <K> ReferenceSet<K> wrap(Set<K> s) {
        return new WrappingRefSet<>(s);
    }

    public static IntCollection wrapInts(Collection<Integer> c) {
        return new WrappingIntCollection(c);
    }

    public static LongCollection wrapLongs(Collection<Long> c) {
        return new WrappingLongCollection(c);
    }

    public static <T> ObjectIterator<T> itrWrap(Iterator<T> in) {
        return new WrapperObjectIterator<>(in);
    }

    public static <T> ObjectIterator<T> itrWrap(Iterable<T> in) {
        return new WrapperObjectIterator<>(in.iterator());
    }

    public static class ConvertingObjectSet<E, T> implements ObjectSet<T> {
        private final Set<E> backing;
        private final Function<E, T> forward;
        private final Function<T, E> back;

        public ConvertingObjectSet(Set<E> backing, Function<E, T> forward, Function<T, E> back) {
            this.backing = Objects.requireNonNull(backing, "Backing set cannot be null");
            this.forward = Objects.requireNonNull(forward, "Forward function cannot be null");
            this.back = Objects.requireNonNull(back, "Backward function cannot be null");
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean contains(Object o) {
            try {
                return backing.contains(back.apply((T) o));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @Override
        public Object[] toArray() {
            return backing.stream().map(forward).toArray();
        }

        @Override
        public <R> R[] toArray(R[] a) {
            return backing.stream().map(forward).collect(Collectors.toSet()).toArray(a);
        }

        @Override
        public boolean add(T e) {
            return backing.add(back.apply(e));
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean remove(Object o) {
            try {
                return backing.remove(back.apply((T) o));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean containsAll(Collection<?> c) {
            try {
                return backing.containsAll(c.stream()
                    .map(i -> back.apply((T) i))
                    .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            return backing.addAll(c.stream().map(back).collect(Collectors.toSet()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean removeAll(Collection<?> c) {
            try {
                return backing.removeAll(c.stream()
                    .map(i -> back.apply((T) i))
                    .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean retainAll(Collection<?> c) {
            try {
                return backing.retainAll(c.stream()
                    .map(i -> back.apply((T) i))
                    .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public ObjectIterator<T> iterator() {
            return new ObjectIterator<>() {
                private final Iterator<E> backg = backing.iterator();

                @Override
                public boolean hasNext() {
                    return backg.hasNext();
                }

                @Override
                public T next() {
                    return forward.apply(backg.next());
                }

                @Override
                public void remove() {
                    backg.remove();
                }
            };
        }
    }

    public static class ConvertingObjectSetFast<E, T>
        implements Long2ObjectMap.FastEntrySet<T> {
        private final Set<E> backing;
        private final Function<E, Long2ObjectMap.Entry<T>> forward;
        private final Function<Long2ObjectMap.Entry<T>, E> back;

        public ConvertingObjectSetFast(
            Set<E> backing,
            Function<E, Long2ObjectMap.Entry<T>> forward,
            Function<Long2ObjectMap.Entry<T>, E> back) {
            this.backing = Objects.requireNonNull(backing);
            this.forward = Objects.requireNonNull(forward);
            this.back = Objects.requireNonNull(back);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean contains(Object o) {
            try {
                return backing.contains(back.apply((Long2ObjectMap.Entry<T>) o));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @Override
        public Object[] toArray() {
            return backing.stream().map(forward).toArray();
        }

        @Override
        public <R> R[] toArray(R[] a) {
            return backing.stream().map(forward).collect(Collectors.toSet()).toArray(a);
        }


        @SuppressWarnings("unchecked")
        @Override
        public boolean remove(Object o) {
            try {
                return backing.remove(back.apply((Long2ObjectMap.Entry<T>) o));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public ObjectIterator<Long2ObjectMap.Entry<T>> iterator() {
            return fastIterator();
        }

        @Override
        public ObjectIterator<Long2ObjectMap.Entry<T>> fastIterator() {
            return new ObjectIterator<>() {
                private final Iterator<E> it = backing.iterator();

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Long2ObjectMap.Entry<T> next() {
                    return forward.apply(it.next());
                }

                @Override
                public void remove() {
                    it.remove();
                }
            };
        }

        @Override
        public boolean add(Long2ObjectMap.Entry<T> e) {
            return backing.add(back.apply(e));
        }

        @Override
        public boolean addAll(Collection<? extends Long2ObjectMap.Entry<T>> c) {
            return backing.addAll(c.stream().map(back).toList());
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean containsAll(Collection<?> c) {
            try {
                return backing.containsAll(c.stream()
                    .map(i -> back.apply((Long2ObjectMap.Entry<T>) i))
                    .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean removeAll(Collection<?> c) {
            try {
                return backing.removeAll(c.stream()
                    .map(i -> back.apply((Long2ObjectMap.Entry<T>) i))
                    .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean retainAll(Collection<?> c) {
            try {
                return backing.retainAll(c.stream()
                    .map(i -> back.apply((Long2ObjectMap.Entry<T>) i))
                    .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }
        }
    }

    static class WrappingIntIterator implements IntIterator {
        private final Iterator<Integer> backing;

        WrappingIntIterator(Iterator<Integer> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public int nextInt() {
            return backing.next();
        }

        @Override
        public Integer next() {
            return backing.next();
        }

        @Override
        public void remove() {
            backing.remove();
        }
    }

    static class WrappingLongIterator implements LongIterator {
        private final Iterator<Long> backing;

        WrappingLongIterator(Iterator<Long> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public long nextLong() {
            return backing.next();
        }

        @Override
        public Long next() {
            return backing.next();
        }

        @Override
        public void remove() {
            backing.remove();
        }
    }

    static class WrappingShortIterator implements ShortIterator {
        private final Iterator<Short> backing;

        WrappingShortIterator(Iterator<Short> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public short nextShort() {
            return backing.next();
        }

        @Override
        public Short next() {
            return backing.next();
        }

        @Override
        public void remove() {
            backing.remove();
        }
    }

    static class WrappingByteIterator implements ByteIterator {
        private final Iterator<Byte> backing;

        WrappingByteIterator(Iterator<Byte> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public byte nextByte() {
            return next();
        }

        @Override
        public Byte next() {
            return backing.next();
        }

        @Override
        public void remove() {
            backing.remove();
        }
    }

    public static class WrappingIntSet implements IntSet {
        private final Set<Integer> backing;

        public WrappingIntSet(Set<Integer> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean add(int key) {
            return backing.add(key);
        }

        @Override
        public boolean contains(int key) {
            return backing.contains(key);
        }

        @Override
        public int[] toIntArray() {
            return backing.stream().mapToInt(Integer::intValue).toArray();
        }

        @Override
        public int[] toArray(int[] a) {
            return ArrayUtils.toPrimitive(backing.toArray(new Integer[0]));
        }

        @Override
        public boolean addAll(IntCollection c) {
            return backing.addAll(c);
        }

        @Override
        public boolean containsAll(IntCollection c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean removeAll(IntCollection c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(IntCollection c) {
            return backing.retainAll(c);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public Object[] toArray() {
            return backing.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return backing.toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends Integer> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public IntIterator iterator() {
            return new WrappingIntIterator(backing.iterator());
        }

        @Override
        public boolean remove(int k) {
            return backing.remove(k);
        }
    }

    public static class WrappingLongSet implements LongSet {
        private final Set<Long> backing;

        public WrappingLongSet(Set<Long> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean add(long key) {
            return backing.add(key);
        }

        @Override
        public boolean contains(long key) {
            return backing.contains(key);
        }

        @Override
        public long[] toLongArray() {
            return backing.stream().mapToLong(Long::longValue).toArray();
        }

        @Override
        public long[] toLongArray(long[] a) {
            if (a.length >= size()) {
                return null;
            } else {
                return toLongArray();
            }
        }

        @Override
        public long[] toArray(long[] a) {
            return toLongArray(a);
        }

        @Override
        public boolean addAll(LongCollection c) {
            return backing.addAll(c);
        }

        @Override
        public boolean containsAll(LongCollection c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean removeAll(LongCollection c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(LongCollection c) {
            return backing.retainAll(c);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public Object[] toArray() {
            return backing.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return backing.toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends Long> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public LongIterator iterator() {
            return new WrappingLongIterator(backing.iterator());
        }

        @Override
        public boolean remove(long k) {
            return backing.remove(k);
        }
    }

    public static class WrappingRefSet<V> implements ReferenceSet<V> {
        private final Set<V> backing;

        public WrappingRefSet(Set<V> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean add(V key) {
            return backing.add(key);
        }

        @Override
        public boolean remove(final Object o) {
            return backing.remove(o);
        }

        @Override
        public boolean containsAll(@NotNull final Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(@NotNull final Collection<? extends V> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(@NotNull final Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(@NotNull final Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            this.backing.clear();
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public boolean contains(final Object o) {
            return backing.contains(o);
        }

        @Override
        public ObjectIterator<V> iterator() {
            return null; // TODO
        }

        @Override
        public @NotNull Object[] toArray() {
            return this.backing.toArray();
        }

        @Override
        public @NotNull <T> T[] toArray(@NotNull final T[] a) {
            return this.backing.toArray(a);
        }
    }

    public static class WrappingLongSortedSet implements LongSortedSet {
        private final Set<Long> backing;

        public WrappingLongSortedSet(Set<Long> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean add(long key) {
            return backing.add(key);
        }

        @Override
        public boolean contains(long key) {
            return backing.contains(key);
        }

        @Override
        public long[] toLongArray() {
            return backing.stream().mapToLong(Long::longValue).toArray();
        }

        @Override
        public long[] toLongArray(long[] a) {
            if (a.length >= size()) {
                return null;
            }
            return toLongArray();
        }

        @Override
        public long[] toArray(long[] a) {
            return toLongArray(a);
        }

        @Override
        public boolean addAll(LongCollection c) {
            return backing.addAll(c);
        }

        @Override
        public boolean containsAll(LongCollection c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean removeAll(LongCollection c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(LongCollection c) {
            return backing.retainAll(c);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public Object[] toArray() {
            return backing.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return backing.toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends Long> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public boolean remove(long k) {
            return backing.remove(k);
        }

        @Override
        public LongBidirectionalIterator iterator(long fromElement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LongBidirectionalIterator iterator() {
            return wrap(new LinkedList<>(backing).iterator());
        }

        @Override
        public LongSortedSet subSet(long fromElement, long toElement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LongSortedSet headSet(long toElement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LongSortedSet tailSet(long fromElement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LongComparator comparator() {
            return null;
        }

        @Override
        public long firstLong() {
            return backing.stream().findFirst().orElseThrow();
        }

        @Override
        public long lastLong() {
            return backing.stream().reduce((first, second) -> second).orElseThrow();
        }
    }

    public static class WrappingByteCollection implements ByteCollection {
        private final Collection<Byte> backing;

        public WrappingByteCollection(Collection<Byte> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public boolean contains(byte o) {
            return backing.contains(o);
        }

        @Override
        public Object[] toArray() {
            return backing.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return backing.toArray(a);
        }

        @Override
        public boolean add(byte e) {
            return backing.add(e);
        }

        @Override
        public boolean remove(Object o) {
            return backing.remove(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends Byte> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public ByteIterator iterator() {
            return itrByteWrap(backing);
        }

        @Override
        public boolean rem(byte key) {
            return remove(key);
        }

        @Override
        public byte[] toByteArray() {
            return ArrayUtils.toPrimitive(backing.toArray(new Byte[0]));
        }

        @Override
        public byte[] toByteArray(byte[] a) {
            return toArray(a);
        }

        @Override
        public byte[] toArray(byte[] a) {
            return ArrayUtils.toPrimitive(backing.toArray(new Byte[0]));
        }

        @Override
        public boolean addAll(ByteCollection c) {
            return addAll((Collection<Byte>) c);
        }

        @Override
        public boolean containsAll(ByteCollection c) {
            return containsAll((Collection<?>) c);
        }

        @Override
        public boolean removeAll(ByteCollection c) {
            return removeAll((Collection<?>) c);
        }

        @Override
        public boolean retainAll(ByteCollection c) {
            return retainAll((Collection<?>) c);
        }
    }

    public static class WrappingLongListIterator implements LongListIterator {
        private final ListIterator<Long> backing;

        WrappingLongListIterator(ListIterator<Long> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public long previousLong() {
            return backing.previous();
        }

        @Override
        public long nextLong() {
            return backing.next();
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public boolean hasPrevious() {
            return backing.hasPrevious();
        }

        @Override
        public int nextIndex() {
            return backing.nextIndex();
        }

        @Override
        public int previousIndex() {
            return backing.previousIndex();
        }

        @Override
        public void add(long k) {
            backing.add(k);
        }

        @Override
        public void remove() {
            backing.remove();
        }

        @Override
        public void set(long k) {
            backing.set(k);
        }
    }

    public static class SlimWrappingLongListIterator implements LongListIterator {
        private final Iterator<Long> backing;

        SlimWrappingLongListIterator(Iterator<Long> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public long previousLong() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long nextLong() {
            return backing.next();
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public boolean hasPrevious() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int previousIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(long k) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove() {
            backing.remove();
        }

        @Override
        public void set(long k) {
            throw new UnsupportedOperationException();
        }
    }

    public static class WrappingObjectCollection<V> implements ObjectCollection<V> {
        private final Collection<V> backing;

        public WrappingObjectCollection(Collection<V> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return backing.contains(o);
        }

        @Override
        public Object[] toArray() {
            return backing.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return backing.toArray(a);
        }

        @Override
        public boolean add(V e) {
            return backing.add(e);
        }

        @Override
        public boolean remove(Object o) {
            return backing.remove(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends V> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public ObjectIterator<V> iterator() {
            return itrWrap(backing);
        }
    }

    public static class WrappingIntCollection implements IntCollection {
        private final Collection<Integer> backing;

        public WrappingIntCollection(Collection<Integer> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public boolean contains(int o) {
            return backing.contains(o);
        }

        @Override
        public Object[] toArray() {
            return backing.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return backing.toArray(a);
        }

        @Override
        public boolean add(int e) {
            return backing.add(e);
        }

        @Override
        public boolean remove(Object o) {
            return backing.remove(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends Integer> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public IntIterator iterator() {
            return itrIntWrap(backing);
        }

        @Override
        public boolean rem(int key) {
            return remove(key);
        }

        @Override
        public int[] toIntArray() {
            return ArrayUtils.toPrimitive(backing.toArray(new Integer[0]));
        }

        @Override
        public int[] toIntArray(int[] a) {
            return toArray(a);
        }

        @Override
        public int[] toArray(int[] a) {
            return ArrayUtils.toPrimitive(backing.toArray(new Integer[0]));
        }

        @Override
        public boolean addAll(IntCollection c) {
            return addAll((Collection<Integer>) c);
        }

        @Override
        public boolean containsAll(IntCollection c) {
            return containsAll((Collection<?>) c);
        }

        @Override
        public boolean removeAll(IntCollection c) {
            return removeAll((Collection<?>) c);
        }

        @Override
        public boolean retainAll(IntCollection c) {
            return retainAll((Collection<?>) c);
        }
    }

    public static class WrappingLongCollection implements LongCollection {
        private final Collection<Long> backing;

        public WrappingLongCollection(Collection<Long> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public boolean contains(long o) {
            return backing.contains(o);
        }

        @Override
        public Object[] toArray() {
            return backing.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return backing.toArray(a);
        }

        @Override
        public boolean add(long e) {
            return backing.add(e);
        }

        @Override
        public boolean remove(Object o) {
            return backing.remove(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends Long> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public LongIterator iterator() {
            return itrLongWrap(backing);
        }

        @Override
        public boolean rem(long key) {
            return remove(key);
        }

        @Override
        public long[] toLongArray() {
            return ArrayUtils.toPrimitive(backing.toArray(new Long[0]));
        }

        @Override
        public long[] toLongArray(long[] a) {
            return toArray(a);
        }

        @Override
        public long[] toArray(long[] a) {
            return ArrayUtils.toPrimitive(backing.toArray(new Long[0]));
        }

        @Override
        public boolean addAll(LongCollection c) {
            return addAll((Collection<Long>) c);
        }

        @Override
        public boolean containsAll(LongCollection c) {
            return containsAll((Collection<?>) c);
        }

        @Override
        public boolean removeAll(LongCollection c) {
            return removeAll((Collection<?>) c);
        }

        @Override
        public boolean retainAll(LongCollection c) {
            return retainAll((Collection<?>) c);
        }
    }

    private static class WrapperObjectIterator<T> implements ObjectIterator<T> {
        private final Iterator<T> parent;

        public WrapperObjectIterator(Iterator<T> parent) {
            this.parent = Objects.requireNonNull(parent);
        }

        @Override
        public boolean hasNext() {
            return parent.hasNext();
        }

        @Override
        public T next() {
            return parent.next();
        }

        @Override
        public void remove() {
            parent.remove();
        }
    }
}
