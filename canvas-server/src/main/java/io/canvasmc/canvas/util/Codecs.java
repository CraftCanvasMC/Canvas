package io.canvasmc.canvas.util;

import com.google.common.util.concurrent.AtomicDouble;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;

/**
 * Custom {@link com.mojang.serialization.Codec} implementations for Canvas
 */
@SuppressWarnings("unused")
public interface Codecs {
    /**
     * Codec instance for {@link java.util.UUID UUIDs}, based off the
     * {@link com.mojang.serialization.Codec#STRING STRING} primitive codec
     */
    Codec<UUID> UUID_CODEC = Codec.STRING
        .comapFlatMap(s -> {
            // try-catch so we don't swallow malformed uuids
            try {
                return DataResult.success(UUID.fromString(s));
            } catch (final Throwable thrown) {
                return DataResult.error(thrown::getMessage);
            }
        }, UUID::toString);

    /**
     * Codec instance for an {@link java.util.concurrent.atomic.AtomicInteger}, based off the {@link Codec#INT INT}
     * primitive codec, essentially just wrapping the raw {@code int} with the atomic variant
     */
    Codec<AtomicInteger> ATOMIC_INTEGER = Codec.INT
        .comapFlatMap(i -> DataResult.success(new AtomicInteger(i)), AtomicInteger::get);

    /**
     * Codec instance for an {@link java.util.concurrent.atomic.AtomicLong}, based off the {@link Codec#LONG LONG}
     * primitive codec, essentially just wrapping the raw {@code long} with the atomic variant
     */
    Codec<AtomicLong> ATOMIC_LONG = Codec.LONG
        .comapFlatMap(l -> DataResult.success(new AtomicLong(l)), AtomicLong::get);

    /**
     * Codec instance for an {@link java.util.concurrent.atomic.AtomicBoolean}, based off the {@link Codec#BOOL BOOL}
     * primitive codec, essentially just wrapping the raw {@code boolean} with the atomic variant
     */
    Codec<AtomicBoolean> ATOMIC_BOOLEAN = Codec.BOOL
        .comapFlatMap(b -> DataResult.success(new AtomicBoolean(b)), AtomicBoolean::get);

    /**
     * Codec instance for an {@link com.google.common.util.concurrent.AtomicDouble}, based off the
     * {@link Codec#DOUBLE DOUBLE} primitive codec, essentially just wrapping the raw {@code double} with the atomic
     * variant
     */
    Codec<AtomicDouble> ATOMIC_DOUBLE = Codec.DOUBLE
        .comapFlatMap(d -> DataResult.success(new AtomicDouble(d)), AtomicDouble::get);

    /**
     * Codec instance for an {@link java.util.concurrent.atomic.AtomicIntegerArray}, based off an {@link Codec#INT INT}
     * {@link Codec#listOf() list} codec, converting between the backing {@code int[]} (via boxed {@link List}) and the
     * atomic array variant
     */
    Codec<AtomicIntegerArray> ATOMIC_INTEGER_ARRAY = Codec.INT.listOf()
        .comapFlatMap(
            list -> DataResult.success(new AtomicIntegerArray(list.stream().mapToInt(Integer::intValue).toArray())),
            array -> {
                int[] result = new int[array.length()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = array.get(i);
                }
                return Arrays.stream(result).boxed().toList();
            }
        );

    /**
     * Codec instance for an {@link java.util.concurrent.atomic.AtomicLongArray}, based off a {@link Codec#LONG LONG}
     * {@link Codec#listOf() list} codec, converting between the backing {@code long[]} (via boxed {@link List}) and the
     * atomic array variant
     */
    Codec<AtomicLongArray> ATOMIC_LONG_ARRAY = Codec.LONG.listOf()
        .comapFlatMap(
            list -> DataResult.success(new AtomicLongArray(list.stream().mapToLong(Long::longValue).toArray())),
            array -> {
                long[] result = new long[array.length()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = array.get(i);
                }
                return Arrays.stream(result).boxed().toList();
            }
        );

    /**
     * Codec instance for an {@link net.minecraft.world.phys.AABB}, based off a {@link Codec#DOUBLE DOUBLE}
     * {@link Codec#listOf() list} codec of exactly {@code 6} elements ({@code minX}, {@code minY}, {@code minZ},
     * {@code maxX}, {@code maxY}, {@code maxZ}), verifying via {@link Util#fixedSize(List, int)} to validate the
     * element count before constructing the {@link AABB} instance
     * <p>
     * Decoding results in 6 {@code double} primitive objects, in linked order of ({@code minX}, {@code minY},
     * {@code minZ}, {@code maxX}, {@code maxY}, {@code maxZ})
     */
    Codec<AABB> AABB_CODEC = Codec.DOUBLE
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize(list, 6).map(listx -> new AABB(listx.getFirst(), listx.get(1), listx.get(2), listx.get(3), listx.get(4), listx.get(5))),
            aabb -> new LinkedList<>(List.of(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ))
        );

    /**
     * Wraps an existing {@link Codec} for a {@link List} so that it decodes into, and encodes from, a
     * {@link java.util.concurrent.CopyOnWriteArrayList}
     *
     * @param innerCodec
     *     the underlying list codec to delegate encoding/decoding to
     * @param <T>
     *     the element type of the list
     *
     * @return a codec producing/consuming a {@link CopyOnWriteArrayList} backed by {@code innerCodec}
     */
    static <T> Codec<List<T>> copyOnWriteArrayListCodec(final Codec<List<T>> innerCodec) {
        return innerCodec.comapFlatMap(
            list -> DataResult.success(new CopyOnWriteArrayList<>(list)),
            (list) -> list
        );
    }

    /**
     * Wraps an existing {@link Codec} for a value type so that it decodes into, and encodes from, an
     * {@link java.util.concurrent.atomic.AtomicReference} holding that value
     *
     * @param innerCodec
     *     the underlying codec to delegate encoding/decoding to
     * @param <T>
     *     the type of value held by the reference
     *
     * @return a codec producing/consuming an {@link AtomicReference} wrapping {@code innerCodec}'s value
     */
    static <T> Codec<AtomicReference<T>> atomicReferenceCodec(final Codec<T> innerCodec) {
        return innerCodec.comapFlatMap(
            t -> DataResult.success(new AtomicReference<>(t)),
            AtomicReference::get
        );
    }
}
