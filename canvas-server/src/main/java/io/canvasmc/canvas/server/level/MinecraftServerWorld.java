package io.canvasmc.canvas.server.level;

import com.google.common.collect.Maps;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.LevelAccess;
import io.canvasmc.canvas.command.ThreadedServerHealthDump;
import io.canvasmc.canvas.entity.SleepingBlockEntity;
import io.canvasmc.canvas.region.Region;
import io.canvasmc.canvas.region.RegionizedTaskQueue;
import io.canvasmc.canvas.region.ServerRegions;
import io.canvasmc.canvas.scheduler.TickScheduler;
import io.canvasmc.canvas.scheduler.WrappedTickLoop;
import io.canvasmc.canvas.server.MultiWatchdogThread;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

public abstract class MinecraftServerWorld extends TickScheduler.FullTick<MinecraftServerWorld.TickHandle> implements LevelAccess {
    public long emptyTicks = 0L;
    protected boolean hasTasks = true;

    public MinecraftServerWorld(final ResourceLocation worldIdentifier) {
        super((DedicatedServer) MinecraftServer.getServer(), worldIdentifier, new TickHandle(), true);
    }

    @Override
    public boolean runTasks(final BooleanSupplier canContinue) {
        MultiWatchdogThread.RunningTick watchdogEntry = new MultiWatchdogThread.RunningTick(Util.getNanos(), this, Thread.currentThread());
        try {
            if (this.isSleeping) {
                // don't poll tasks if we are asleep! this is dumb!
                return super.runTasks(canContinue);
            }
            MultiWatchdogThread.WATCHDOG.dock(watchdogEntry);
            TickScheduler.setTickingData(level().levelTickData);
            ServerLevel worldserver = level();

            worldserver.getChunkSource().mainThreadProcessor.pollTask();
            runRegionTasks(canContinue);
            hasTasks = false;
            return super.runTasks(canContinue);
        } finally {
            TickScheduler.setTickingData(null);
            MultiWatchdogThread.WATCHDOG.undock(watchdogEntry);
        }
    }

    public boolean runRegionTasks(final BooleanSupplier canContinue) {
        final RegionizedTaskQueue.RegionTaskQueueData queue = this.level().regionTaskQueueData;

        boolean processedChunkTask = false;

        boolean executeChunkTask;
        boolean executeTickTask;
        do {
            executeTickTask = queue.executeTickTask();
            executeChunkTask = queue.executeChunkTask();

            processedChunkTask |= executeChunkTask;
        } while ((executeChunkTask | executeTickTask) && canContinue.getAsBoolean());

        if (processedChunkTask) {
            // if we processed any chunk tasks, try to process ticket level updates for full status changes
            this.level().moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates();
        }
        return true;
    }

    public ServerLevel level() {
        return (ServerLevel) this;
    }

    @Override
    public @NotNull World getWorld() {
        return this.level().getWorld();
    }

    @Override
    public boolean hasTasks() {
        return (this.hasTasks || super.hasTasks()) && (!this.isSleeping());
    }

    @Override
    public @Unmodifiable @NotNull List<Region> getAllRegions() {
        ArrayList<Region> regions = new ArrayList<>();
        if (!this.server.isRegionized()) return regions;
        this.forEachRegion(regions::add);
        return Collections.unmodifiableList(regions);
    }

    @Override
    public void forEachRegion(final @NotNull Consumer<Region> forEach) {
        if (!this.server.isRegionized()) return;
        this.level().regioniser.computeForAllRegionsUnsynchronised((region) -> forEach.accept(region.getData()));
    }

    @Override
    public void wake() {
        // only wake the world if it has players
        // if it doesn't then why wake it...?
        this.emptyTicks = 0L;
        super.wake();
    }

    public String getDebugLocation() {
        return "w:" + this.level().dimension().location().getPath();
    }

    @Override
    public String toString() {
        return getDebugLocation();
    }

    @Override
    public @NotNull Component debugInfo() {
        final List<ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData>> regions =
            new ArrayList<>();
        this.level().regioniser.computeForAllRegionsUnsynchronised((Consumer<ThreadedRegionizer.ThreadedRegion>) regions::add);
        TextComponent.Builder basic = Component.text()
            .append(Component.text("Basic Information", ThreadedServerHealthDump.HEADER, TextDecoration.BOLD))
            .append(ThreadedServerHealthDump.NEW_LINE);
        int pendingBlock = 0;
        int pendingFluid = 0;
        int localPlayers = 0;
        int localEntities = 0;
        if (this.server.isRegionized()) {
            basic
                .append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
                .append(Component.text("Regions: ", ThreadedServerHealthDump.PRIMARY))
                .append(Component.text(regions.size(), ThreadedServerHealthDump.INFORMATION))
                .append(ThreadedServerHealthDump.NEW_LINE);
            return basic.build();
        } else {
            pendingBlock += this.level().getBlockTicks().count();
            pendingFluid += this.level().getFluidTicks().count();
            localPlayers += this.level().getLocalPlayers().size();
            localEntities += ServerRegions.getTickData(this.level()).getLocalEntitiesCopy().length;
        }
        basic
            .append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Pending Block Ticks: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(pendingBlock, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Pending Fluid Ticks: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(pendingFluid, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Local Players: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(localPlayers, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Local Entities: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(localEntities, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE);
        basic.append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Ticking Chunks: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(ServerRegions.getTickData(this.level()).lastTickingChunksCount, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Regions: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(regions.size(), ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE);
        basic.append(Component.text("Tile Entities", ThreadedServerHealthDump.HEADER, TextDecoration.BOLD))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(doTileEntityInfo(null))
            .append(ThreadedServerHealthDump.NEW_LINE);
        basic.append(Component.text("Entities", ThreadedServerHealthDump.HEADER, TextDecoration.BOLD))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(doEntityInfo(null))
            .append(ThreadedServerHealthDump.NEW_LINE);
        return basic.build();
    }

    public @NotNull Component doTileEntityInfo(final @Nullable ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region) {
        TextComponent.@NotNull Builder root = text();
        ServerLevel world = this.level();
        if (region != null) root.append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("World ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text("[" + world.dimension().location().toDebugFileName() + "]", ThreadedServerHealthDump.INFORMATION))
            .append(Component.text(":", ThreadedServerHealthDump.PRIMARY))
            .append(ThreadedServerHealthDump.NEW_LINE);
        String filter = "*";
        final String cleanfilter = filter.replace("?", ".?").replace("*", ".*?");
        Set<ResourceLocation> names = BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet().stream()
            .filter(n -> n.toString().matches(cleanfilter))
            .collect(Collectors.toSet());

        Map<ResourceLocation, MutablePair<Integer, Map<ChunkPos, Integer>>> list = Maps.newHashMap();
        Map<ResourceLocation, Integer> nonEntityTicking = Maps.newHashMap();

        final AtomicReference<ChunkPos> highest = new AtomicReference<>();
        final AtomicInteger highestCount = new AtomicInteger();

        Consumer<BlockEntity> blockEntityConsumer = (e) -> {
            ResourceLocation key = BlockEntityType.getKey(e.getType());

            MutablePair<Integer, Map<ChunkPos, Integer>> info = list.computeIfAbsent(key, _ -> MutablePair.of(0, Maps.newHashMap()));
            BlockPos worldPosition = e.worldPosition;
            ChunkPos chunk = new ChunkPos(worldPosition);
            info.left++;
            int chunkCount = info.right.merge(chunk, 1, Integer::sum);

            if (!world.isPositionTickingWithEntitiesLoaded(chunk.longKey) || (e instanceof SleepingBlockEntity sleeping && sleeping.isSleeping())) {
                nonEntityTicking.merge(key, 1, Integer::sum);
            }

            if (chunkCount > highestCount.get()) {
                highestCount.set(chunkCount);
                highest.set(chunk);
            }
        };
        if (region != null) {
            for (final LevelChunk chunk : region.getData().tickData.getTickingChunks().getRawDataUnchecked()) {
                if (chunk == null) continue;
                chunk.blockEntities.values().forEach(blockEntityConsumer);
            }
        } else {
            world.getAllBlockEntities().forEach(blockEntityConsumer);
        }

        List<Pair<ResourceLocation, Integer>> info = list.entrySet().stream()
            .filter(e -> names.contains(e.getKey()))
            .map(e -> Pair.of(e.getKey(), e.getValue().left))
            .sorted((a, b) -> !a.getRight().equals(b.getRight()) ? b.getRight() - a.getRight() : a.getKey().toString().compareTo(b.getKey().toString()))
            .toList();

        if (info.isEmpty()) {
            root.append(text("    No entities found.", RED))
                .append(ThreadedServerHealthDump.NEW_LINE);
            return root.build();
        }

        int count = info.stream().mapToInt(Pair::getRight).sum();
        int nonTickingCount = nonEntityTicking.values().stream().mapToInt(Integer::intValue).sum();
        root.append(Component.text("    Total Ticking: ", ThreadedServerHealthDump.PRIME_ALT))
            .append(Component.text((count - nonTickingCount), ThreadedServerHealthDump.INFORMATION))
            .append(Component.text(", Total Non-Ticking: ", ThreadedServerHealthDump.PRIME_ALT))
            .append(Component.text(nonTickingCount, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE);

        if (highest.get() != null) {
            BlockPos blockPos = highest.get().getMiddleBlockPosition(90);
            root.append(Component.text("    "))
                .append(Component.text("[Teleport to most populated ChunkPos]", ThreadedServerHealthDump.SECONDARY)
                    .clickEvent(ClickEvent.runCommand("/execute in " + world.dimension().location() + " run teleport @s " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ())))
                .append(ThreadedServerHealthDump.NEW_LINE);
        }

        info.forEach(e -> {
            int nonTicking = nonEntityTicking.getOrDefault(e.getKey(), 0);
            root.append(Component.text("       "))
                .append(Component.text((e.getValue() - nonTicking), ThreadedServerHealthDump.INFORMATION))
                .append(Component.text(" (", ThreadedServerHealthDump.PRIME_ALT))
                .append(Component.text(nonTicking, ThreadedServerHealthDump.INFORMATION))
                .append(Component.text(") : ", ThreadedServerHealthDump.PRIME_ALT))
                .append(Component.text(e.getKey().toDebugFileName(), ThreadedServerHealthDump.INFORMATION))
                .append(ThreadedServerHealthDump.NEW_LINE);
        });

        if (highest.get() != null) {
            root.append(Component.text("    Chunk with highest ticking block entities: ", ThreadedServerHealthDump.PRIME_ALT))
                .append(Component.text(highest + " (" + highestCount + " entities)", ThreadedServerHealthDump.INFORMATION))
                .append(ThreadedServerHealthDump.NEW_LINE);
        }

        return root.append(Component.text("* First number is ticking entities, second number is non-ticking entities", ThreadedServerHealthDump.PRIMARY))
            .build();
    }

    public @NotNull Component doEntityInfo(final @Nullable ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region) {
        TextComponent.@NotNull Builder root = text();
        ServerLevel world = level();
        if (region != null) root.append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("World ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text("[" + world.dimension().location().toDebugFileName() + "]", ThreadedServerHealthDump.INFORMATION))
            .append(Component.text(":", ThreadedServerHealthDump.PRIMARY))
            .append(ThreadedServerHealthDump.NEW_LINE);

        String filter = "*";
        final String cleanfilter = filter.replace("?", ".?").replace("*", ".*?");
        Set<ResourceLocation> names = BuiltInRegistries.ENTITY_TYPE.keySet().stream()
            .filter(n -> n.toString().matches(cleanfilter))
            .collect(Collectors.toSet());

        Map<ResourceLocation, MutablePair<Integer, Map<ChunkPos, Integer>>> list = Maps.newHashMap();
        Map<ResourceLocation, Integer> nonEntityTicking = Maps.newHashMap();

        final AtomicReference<ChunkPos> highest = new AtomicReference<>();
        final AtomicInteger highestCount = new AtomicInteger();

        Consumer<Entity> entityConsumer = (e) -> {
            ResourceLocation key = EntityType.getKey(e.getType());
            MutablePair<Integer, Map<ChunkPos, Integer>> info = list.computeIfAbsent(key, _ -> MutablePair.of(0, Maps.newHashMap()));
            ChunkPos chunk = e.chunkPosition();
            info.left++;
            int chunkCount = info.right.merge(chunk, 1, Integer::sum);

            if (!world.isPositionEntityTicking(e.blockPosition()) ||
                (e instanceof net.minecraft.world.entity.Marker && !world.paperConfig().entities.markers.tick)) {
                nonEntityTicking.merge(key, 1, Integer::sum);
            }

            if (chunkCount > highestCount.get()) {
                highestCount.set(chunkCount);
                highest.set(chunk);
            }
        };
        if (region != null) {
            for (final Entity loadedEntity : region.getData().tickData.getLoadedEntities()) {
                if (loadedEntity == null) continue;
                entityConsumer.accept(loadedEntity);
            }
        } else {
            world.getAllRegionizedEntities().forEach(entityConsumer);
        }

        List<Pair<ResourceLocation, Integer>> info = list.entrySet().stream()
            .filter(e -> names.contains(e.getKey()))
            .map(e -> Pair.of(e.getKey(), e.getValue().left))
            .sorted((a, b) -> !a.getRight().equals(b.getRight()) ? b.getRight() - a.getRight() : a.getKey().toString().compareTo(b.getKey().toString()))
            .toList();

        if (info.isEmpty()) {
            root.append(text("    No entities found.", RED))
                .append(ThreadedServerHealthDump.NEW_LINE);
            return root.build();
        }

        int count = info.stream().mapToInt(Pair::getRight).sum();
        int nonTickingCount = nonEntityTicking.values().stream().mapToInt(Integer::intValue).sum();
        root.append(Component.text("    Total Ticking: ", ThreadedServerHealthDump.PRIME_ALT))
            .append(Component.text((count - nonTickingCount), ThreadedServerHealthDump.INFORMATION))
            .append(Component.text(", Total Non-Ticking: ", ThreadedServerHealthDump.PRIME_ALT))
            .append(Component.text(nonTickingCount, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE);

        if (highest.get() != null) {
            BlockPos blockPos = highest.get().getMiddleBlockPosition(90);
            root.append(Component.text("    "))
                .append(Component.text("[Teleport to most populated ChunkPos]", ThreadedServerHealthDump.SECONDARY)
                    .clickEvent(ClickEvent.runCommand("/execute in " + world.dimension().location() + " run teleport @s " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ())))
                .append(ThreadedServerHealthDump.NEW_LINE);
        }

        info.forEach(e -> {
            int nonTicking = nonEntityTicking.getOrDefault(e.getKey(), 0);
            root.append(Component.text("       "))
                .append(Component.text((e.getValue() - nonTicking), ThreadedServerHealthDump.INFORMATION))
                .append(Component.text(" (", ThreadedServerHealthDump.PRIME_ALT))
                .append(Component.text(nonTicking, ThreadedServerHealthDump.INFORMATION))
                .append(Component.text(") : ", ThreadedServerHealthDump.PRIME_ALT))
                .append(Component.text(e.getKey().toDebugFileName(), ThreadedServerHealthDump.INFORMATION))
                .append(ThreadedServerHealthDump.NEW_LINE);
        });

        if (highest.get() != null) {
            root.append(Component.text("    Chunk with highest ticking entities: ", ThreadedServerHealthDump.PRIME_ALT))
                .append(Component.text(highest + " (" + highestCount + " entities)", ThreadedServerHealthDump.INFORMATION))
                .append(ThreadedServerHealthDump.NEW_LINE);
        }
        return root.append(Component.text("* First number is ticking entities, second number is non-ticking entities", ThreadedServerHealthDump.PRIMARY))
            .build();
    }

    public static class TickHandle implements WrappedTick {
        @Override
        public boolean blockTick(final @NotNull WrappedTickLoop loop, final @NotNull BooleanSupplier hasTimeLeft, final int tickCount) {
            ServerLevel thisAsTickable = (ServerLevel) loop; // we are extended by ServerLevel
            TickScheduler.setTickingData(thisAsTickable.levelTickData);
            MinecraftServer server = MinecraftServer.getServer();
            if (!server.isTicking()) {
                while (hasTimeLeft.getAsBoolean()) {
                    thisAsTickable.runTasks(hasTimeLeft);
                }
                TickScheduler.setTickingData(null);
                return !thisAsTickable.cancelled.get();
            }
            thisAsTickable.hasTasks = true;
            int i = server.pauseWhileEmptySeconds() * 20;
            if (Config.INSTANCE.ticking.emptySleepPerWorlds && i > 0) {
                if (thisAsTickable.players().isEmpty() && !thisAsTickable.tickRateManager().isSprinting() && server.pluginsBlockingSleep.isEmpty()) {
                    thisAsTickable.emptyTicks++;
                } else {
                    thisAsTickable.emptyTicks = 0;
                }

                if (thisAsTickable.emptyTicks >= i) {
                    if (thisAsTickable.emptyTicks == i) {
                        TickScheduler.LOGGER.info("Level empty for {} seconds, pausing", server.pauseWhileEmptySeconds());
                        thisAsTickable.sleep();
                        thisAsTickable.emptyTicks = 0;
                        return true; // on next tick it will realize we need to sleep and kill the task
                    }
                }
            }
            thisAsTickable.runTasks(hasTimeLeft);
            // set again because 'runTasks' sets back to null
            TickScheduler.setTickingData(thisAsTickable.levelTickData);
            thisAsTickable.bench(() -> thisAsTickable.worldtick(hasTimeLeft, tickCount));
            TickScheduler.setTickingData(null);
            return !thisAsTickable.cancelled.get();
        }
    }
}
