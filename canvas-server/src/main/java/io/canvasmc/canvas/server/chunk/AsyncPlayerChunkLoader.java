package io.canvasmc.canvas.server.chunk;

import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkFullTask;
import io.canvasmc.canvas.server.AbstractTickLoop;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;

import static io.canvasmc.canvas.command.ThreadedServerHealthDump.HEADER;
import static io.canvasmc.canvas.command.ThreadedServerHealthDump.INFORMATION;
import static io.canvasmc.canvas.command.ThreadedServerHealthDump.LIST;
import static io.canvasmc.canvas.command.ThreadedServerHealthDump.NEW_LINE;
import static io.canvasmc.canvas.command.ThreadedServerHealthDump.PRIMARY;
import static io.canvasmc.canvas.command.ThreadedServerHealthDump.PRIME_ALT;
import static io.canvasmc.canvas.command.ThreadedServerHealthDump.SECONDARY;
import static io.canvasmc.canvas.command.ThreadedServerHealthDump.TWO_DECIMAL_PLACES;
import static net.kyori.adventure.text.Component.text;

public class AsyncPlayerChunkLoader extends AbstractTickLoop {
    public static AsyncPlayerChunkLoader INSTANCE;

    public AsyncPlayerChunkLoader(final String name, final String debugName) {
        super(name, debugName);
        INSTANCE = this;
    }

    @Override
    protected void blockTick(final BooleanSupplier hasTimeLeft, final int tickCount) {
        int processedPolledCount = 0;
        while (this.pollInternal() && !shutdown) processedPolledCount++;
        this.tick(hasTimeLeft);
    }

    @Override
    public @NotNull Component debugInfo() {
        int moonriseWorkers = MoonriseCommon.WORKER_POOL.getCoreThreads().length;
        int activeWorkers = MoonriseCommon.WORKER_POOL.getAliveThreads();
        int moonriseIOWorkers = MoonriseCommon.IO_POOL.getCoreThreads().length;
        int activeIOWorkers = MoonriseCommon.IO_POOL.getAliveThreads().length;

        final long currTime = System.nanoTime();
        final double genRate = ChunkFullTask.genRate(currTime);
        final double loadRate = ChunkFullTask.loadRate(currTime);

        return Component.text()
            // Worker debug
            .append(Component.text(" - ", LIST, TextDecoration.BOLD))
            .append(Component.text("TheChunkSystem/ChunkLoader", PRIMARY))
            .append(Component.text(": Active[", SECONDARY))
            .append(Component.text(activeWorkers, INFORMATION))
            .append(Component.text("] Total[", SECONDARY))
            .append(Component.text(moonriseWorkers, INFORMATION))
            .append(Component.text("]", SECONDARY))
            .append(NEW_LINE)

            // I/O debug
            .append(Component.text(" - ", LIST, TextDecoration.BOLD))
            .append(Component.text("TheChunkSystem/Chunk IO", PRIMARY))
            .append(Component.text(": Active[", SECONDARY))
            .append(Component.text(activeIOWorkers, INFORMATION))
            .append(Component.text("] Total[", SECONDARY))
            .append(Component.text(moonriseIOWorkers, INFORMATION))
            .append(Component.text("]", SECONDARY))
            .append(NEW_LINE)

            .append(Component.text(" - ", LIST, TextDecoration.BOLD))
            .append(Component.text("Load rate: ", PRIMARY))
            .append(Component.text(TWO_DECIMAL_PLACES.get().format(loadRate) + ", ", INFORMATION))
            .append(Component.text("Gen rate: ", PRIMARY))
            .append(Component.text(TWO_DECIMAL_PLACES.get().format(genRate), INFORMATION))
            .append(NEW_LINE)

            // chunk info debug
            .append(Component.text("Chunk Info", HEADER, TextDecoration.BOLD))
            .append(NEW_LINE)
            .append(doChunkInfo())

            // holder info debug
            .append(Component.text("Holder Info", HEADER, TextDecoration.BOLD))
            .append(NEW_LINE)
            .append(doHolderInfo())
            .append(NEW_LINE)

            // queue debug
            .append(Component.text("Loader Queues", HEADER, TextDecoration.BOLD))
            .append(NEW_LINE)
            .append(doLoaderQueues())
            .build();
    }

    private @NotNull Component doLoaderQueues() {
        TextComponent.@NotNull Builder root = text();
        int send = 0;
        int ticking = 0;
        int generating = 0;
        int gen = 0;
        int loading = 0;
        int load = 0;

        for (final ServerPlayer player : MinecraftServer.getServer().getPlayerList().players) {
            RegionizedPlayerChunkLoader.PlayerChunkLoaderData data = player.moonrise$getChunkLoader();
            send += data.sendQueue.size();
            ticking += data.tickingQueue.size();
            generating += data.generatingQueue.size();
            gen += data.genQueue.size();
            loading += data.loadingQueue.size();
            load += data.loadQueue.size();
        }
        root.append(Component.text(" - ", LIST, TextDecoration.BOLD))
            .append(Component.text("Send Queue: ", PRIME_ALT))
            .append(Component.text(send, INFORMATION))
            .append(NEW_LINE)

            .append(Component.text(" - ", LIST, TextDecoration.BOLD))
            .append(Component.text("Ticking Queue: ", PRIME_ALT))
            .append(Component.text(ticking, INFORMATION))
            .append(NEW_LINE)

            .append(Component.text(" - ", LIST, TextDecoration.BOLD))
            .append(Component.text("Generating Queue: ", PRIME_ALT))
            .append(Component.text(generating, INFORMATION))
            .append(NEW_LINE)

            .append(Component.text(" - ", LIST, TextDecoration.BOLD))
            .append(Component.text("Gen Queue: ", PRIME_ALT))
            .append(Component.text(gen, INFORMATION))
            .append(NEW_LINE)

            .append(Component.text(" - ", LIST, TextDecoration.BOLD))
            .append(Component.text("Loading Queue: ", PRIME_ALT))
            .append(Component.text(loading, INFORMATION))
            .append(NEW_LINE)

            .append(Component.text(" - ", LIST, TextDecoration.BOLD))
            .append(Component.text("Load Queue: ", PRIME_ALT))
            .append(Component.text(load, INFORMATION));
        return root.build();
    }

    public void tick(BooleanSupplier hasTimeLeft) {
        ProfilerFiller profilerFiller = Profiler.get();
        for (ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
            ServerChunkCache chunkSource = level.getChunkSource();
            level.moonrise$getPlayerChunkLoader().tick();
            level.moonrise$getChunkTaskScheduler().executeMainThreadTask();
            chunkSource.broadcastChangedChunks(profilerFiller);
            chunkSource.chunkMap.tick(hasTimeLeft, true);
        }
        if (MoonriseCommon.WORKER_POOL.hasPendingTasks()) {
            MoonriseCommon.WORKER_POOL.wakeup();
        }
    }

    private @NotNull Component doChunkInfo() {
        List<World> worlds = Bukkit.getWorlds();

        int accumulatedTotal = 0;
        int accumulatedInactive = 0;
        int accumulatedBorder = 0;
        int accumulatedTicking = 0;
        int accumulatedEntityTicking = 0;

        TextComponent.@NotNull Builder root = text();

        for (final org.bukkit.World bukkitWorld : worlds) {
            final ServerLevel world = ((CraftWorld) bukkitWorld).getHandle();

            int total = 0;
            int inactive = 0;
            int full = 0;
            int blockTicking = 0;
            int entityTicking = 0;

            for (final NewChunkHolder holder : ((ChunkSystemServerLevel) world).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolders()) {
                final NewChunkHolder.ChunkCompletion completion = holder.getLastChunkCompletion();
                final ChunkAccess chunk = completion == null ? null : completion.chunk();

                if (!(chunk instanceof LevelChunk)) {
                    continue;
                }

                ++total;

                switch (holder.getChunkStatus()) {
                    case INACCESSIBLE: {
                        ++inactive;
                        break;
                    }
                    case FULL: {
                        ++full;
                        break;
                    }
                    case BLOCK_TICKING: {
                        ++blockTicking;
                        break;
                    }
                    case ENTITY_TICKING: {
                        ++entityTicking;
                        break;
                    }
                }
            }

            accumulatedTotal += total;
            accumulatedInactive += inactive;
            accumulatedBorder += full;
            accumulatedTicking += blockTicking;
            accumulatedEntityTicking += entityTicking;
        }
        root.append(text().append(text("Chunks in ", PRIMARY, TextDecoration.BOLD), text("[all worlds]", INFORMATION), text(":", PRIMARY, TextDecoration.BOLD)));
        root.append(NEW_LINE);
        root.append(text().append(
            text("  Total: ", SECONDARY), text(accumulatedTotal, INFORMATION),
            text(" Inactive: ", SECONDARY), text(accumulatedInactive, INFORMATION),
            text(" Full: ", SECONDARY), text(accumulatedBorder, INFORMATION),
            text(" Block Ticking: ", SECONDARY), text(accumulatedTicking, INFORMATION),
            text(" Entity Ticking: ", SECONDARY), text(accumulatedEntityTicking, INFORMATION)
        ));
        root.append(NEW_LINE);
        return root.build();
    }

    private @NotNull Component doHolderInfo() {
        List<org.bukkit.World> worlds = Bukkit.getWorlds();

        int accumulatedTotal = 0;
        int accumulatedCanUnload = 0;
        int accumulatedNull = 0;
        int accumulatedReadOnly = 0;
        int accumulatedProtoChunk = 0;
        int accumulatedFullChunk = 0;

        TextComponent.@NotNull Builder root = text();

        for (final org.bukkit.World bukkitWorld : worlds) {
            final ServerLevel world = ((CraftWorld) bukkitWorld).getHandle();

            int total = 0;
            int canUnload = 0;
            int nullChunks = 0;
            int readOnly = 0;
            int protoChunk = 0;
            int fullChunk = 0;

            for (final NewChunkHolder holder : ((ChunkSystemServerLevel) world).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolders()) {
                final NewChunkHolder.ChunkCompletion completion = holder.getLastChunkCompletion();
                final ChunkAccess chunk = completion == null ? null : completion.chunk();

                ++total;

                if (chunk == null) {
                    ++nullChunks;
                } else if (chunk instanceof ImposterProtoChunk) {
                    ++readOnly;
                } else if (chunk instanceof ProtoChunk) {
                    ++protoChunk;
                } else if (chunk instanceof LevelChunk) {
                    ++fullChunk;
                }

                if (holder.isSafeToUnload() == null) {
                    ++canUnload;
                }
            }

            accumulatedTotal += total;
            accumulatedCanUnload += canUnload;
            accumulatedNull += nullChunks;
            accumulatedReadOnly += readOnly;
            accumulatedProtoChunk += protoChunk;
            accumulatedFullChunk += fullChunk;
        }
        root.append(text().append(text("Holders in ", PRIMARY, TextDecoration.BOLD), text("[all worlds]", INFORMATION), text(":", PRIMARY, TextDecoration.BOLD)));
        root.append(NEW_LINE);
        root.append(text().append(
            text("  Total: ", SECONDARY), text(accumulatedTotal, INFORMATION),
            text(" Unloadable: ", SECONDARY), text(accumulatedCanUnload, INFORMATION),
            text(" Null: ", SECONDARY), text(accumulatedNull, INFORMATION),
            text(" ReadOnly: ", SECONDARY), text(accumulatedReadOnly, INFORMATION),
            text(" Proto: ", SECONDARY), text(accumulatedProtoChunk, INFORMATION),
            text(" Full: ", SECONDARY), text(accumulatedFullChunk, INFORMATION)
        ));
        return root.build();
    }
}
