package io.canvasmc.canvas.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Offloads packet serialization to a background thread pool to reduce region tick time.
 */
@NullMarked
public final class AsyncPacketSerializer {

    private static final Executor SERIALIZER_POOL = Executors.newWorkStealingPool(
        Math.max(1, Runtime.getRuntime().availableProcessors() / 4)
    );

    /**
     * Serializes a packet asynchronously.
     *
     * @param packet the packet to serialize
     * @return a future containing the serialized buffer
     */
    public static CompletableFuture<ByteBuf> serializeAsync(Packet<?> packet) {
        return CompletableFuture.supplyAsync(() -> {
            ByteBuf byteBuf = Unpooled.buffer();
            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(byteBuf);
            // In a real implementation, we would need the codec/protocol context here
            // This is a simplified architectural representation
            return byteBuf;
        }, SERIALIZER_POOL);
    }

    private AsyncPacketSerializer() {}
}
