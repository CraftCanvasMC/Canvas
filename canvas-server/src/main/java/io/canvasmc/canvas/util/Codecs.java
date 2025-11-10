package io.canvasmc.canvas.util;

import com.google.common.util.concurrent.AtomicDouble;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.Util;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public interface Codecs {
    Codec<AtomicInteger> ATOMIC_INTEGER = Codec.INT
        .comapFlatMap(i -> DataResult.success(new AtomicInteger(i)), AtomicInteger::get);

    Codec<AtomicLong> ATOMIC_LONG = Codec.LONG
        .comapFlatMap(l -> DataResult.success(new AtomicLong(l)), AtomicLong::get);

    Codec<AtomicBoolean> ATOMIC_BOOLEAN = Codec.BOOL
        .comapFlatMap(b -> DataResult.success(new AtomicBoolean(b)), AtomicBoolean::get);

    Codec<AtomicDouble> ATOMIC_DOUBLE = Codec.DOUBLE
        .comapFlatMap(d -> DataResult.success(new AtomicDouble(d)), AtomicDouble::get);

    Codec<AtomicIntegerArray> ATOMIC_INTEGER_ARRAY = Codec.INT.listOf()
        .comapFlatMap(
            list -> DataResult.success(new AtomicIntegerArray(list.stream().mapToInt(Integer::intValue).toArray())),
            array -> {
                int[] result = new int[array.length()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = array.get(i);
                }
                return java.util.Arrays.stream(result).boxed().toList();
            }
        );

    Codec<AtomicLongArray> ATOMIC_LONG_ARRAY = Codec.LONG.listOf()
        .comapFlatMap(
            list -> DataResult.success(new AtomicLongArray(list.stream().mapToLong(Long::longValue).toArray())),
            array -> {
                long[] result = new long[array.length()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = array.get(i);
                }
                return java.util.Arrays.stream(result).boxed().toList();
            }
        );

    static <T> Codec<AtomicReference<T>> atomicReferenceCodec(@NotNull Codec<T> innerCodec) {
        return innerCodec.comapFlatMap(
            t -> DataResult.success(new AtomicReference<>(t)),
            AtomicReference::get
        );
    }

    Codec<AABB> AABB_CODEC = Codec.DOUBLE
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize(list, 6).map(listx -> new AABB(listx.getFirst(), listx.get(1), listx.get(2), listx.get(3), listx.get(4), listx.get(5))),
            aabb -> List.of(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ)
        );
}
