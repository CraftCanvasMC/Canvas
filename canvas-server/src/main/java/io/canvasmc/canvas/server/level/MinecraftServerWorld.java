package io.canvasmc.canvas.server.level;

import com.google.common.collect.Maps;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.LevelAccess;
import io.canvasmc.canvas.command.ThreadedServerHealthDump;
import io.canvasmc.canvas.entity.SleepingBlockEntity;
import io.canvasmc.canvas.region.ServerRegions;
import io.canvasmc.canvas.scheduler.TickScheduler;
import io.canvasmc.canvas.scheduler.WrappedTickLoop;
import io.canvasmc.canvas.server.MultiWatchdogThread;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.World;
import org.bukkit.craftbukkit.scheduler.CraftScheduler;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

public abstract class MinecraftServerWorld extends TickScheduler.FullTick<MinecraftServerWorld.TickHandle> implements LevelAccess {
    protected final ConcurrentLinkedQueue<Runnable> queuedForNextTickPost = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<Runnable> queuedForNextTickPre = new ConcurrentLinkedQueue<>();
    protected final CraftScheduler bukkitScheduler;
    public long emptyTicks = 0L;
    protected boolean hasTasks = true;

    public MinecraftServerWorld(final ResourceLocation worldIdentifier) {
        super((DedicatedServer) MinecraftServer.getServer(), worldIdentifier, new TickHandle());
        this.bukkitScheduler = new CraftScheduler();
    }

    public static class TickHandle implements WrappedTick {
        @Override
        public boolean blockTick(final WrappedTickLoop loop, final BooleanSupplier hasTimeLeft, final int tickCount) {
            ServerLevel thisAsTickable = (ServerLevel) loop; // we are extended by ServerLevel
            if (thisAsTickable.levelTickData == null) {
                thisAsTickable.levelTickData = new ServerRegions.WorldTickData(thisAsTickable, null);
            }
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
            if (Config.INSTANCE.ticking.enableThreadedRegionizing && (!thisAsTickable.levelTickData.getBlockLevelTicks().allContainers.isEmpty() || !thisAsTickable.levelTickData.getFluidLevelTicks().allContainers.isEmpty()))
                throw new IllegalStateException("Cannot register block/fluid level ticks to the world.");
            thisAsTickable.bench(() -> thisAsTickable.worldtick(hasTimeLeft, tickCount));
            TickScheduler.setTickingData(null);
            return !thisAsTickable.cancelled.get();
        }
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

            worldserver.getChunkSource().mainThreadProcessor.pollTask(!Config.INSTANCE.ticking.enableThreadedRegionizing);
            hasTasks = false;
            return super.runTasks(canContinue);
        } finally {
            TickScheduler.setTickingData(null);
            MultiWatchdogThread.WATCHDOG.undock(watchdogEntry);
        }
    }

    public ServerLevel level() {
        return (ServerLevel) this;
    }

    @Override
    public World getWorld() {
        return this.level().getWorld();
    }

    @Override
    public boolean hasTasks() {
        return hasTasks || (!Config.INSTANCE.ticking.enableThreadedRegionizing && Config.INSTANCE.ticking.dontParkBetweenTicks);
    }

    @Override
    public void scheduleOnThread(final Runnable runnable) {
        this.pushTask(runnable);
    }

    @Override
    public void scheduleForPostNextTick(Runnable run) {
        queuedForNextTickPost.add(run);
    }

    @Override
    public void scheduleForPreNextTick(Runnable run) {
        queuedForNextTickPre.add(run);
    }

    @Override
    public void wake() {
        // only wake the world if it has players
        // if it doesn't then why wake it...?
        this.emptyTicks = 0L;
        super.wake();
    }

    @Override
    public BukkitScheduler getBukkitScheduler() {
        return bukkitScheduler;
    }

    public String getDebugLocation() {
        return "w:" + this.level().dimension().location();
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
        if (!Config.INSTANCE.ticking.enableThreadedRegionizing) {
            pendingBlock += this.level().getBlockTicks().count();
            pendingFluid += this.level().getFluidTicks().count();
            localPlayers += this.level().getLocalPlayers().size();
            localEntities += ServerRegions.getTickData(this.level()).getLocalEntitiesCopy().length;
        } else {
            for (final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region : regions) {
                pendingBlock += region.getData().tickData.getBlockLevelTicks().count();
                pendingFluid += region.getData().tickData.getFluidLevelTicks().count();
                localPlayers += region.getData().tickData.getLocalPlayers().size();
                localEntities += region.getData().tickData.getLocalEntitiesCopy().length;
            }
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
            .append(Component.text(this.level().getChunkSource().lastTickingChunksCount, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Regions: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(regions.size(), ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE);
        basic.append(Component.text("Tile Entities", ThreadedServerHealthDump.HEADER, TextDecoration.BOLD))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(doTileEntityInfo())
            .append(ThreadedServerHealthDump.NEW_LINE);
        basic.append(Component.text("Entities", ThreadedServerHealthDump.HEADER, TextDecoration.BOLD))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(doEntityInfo())
            .append(ThreadedServerHealthDump.NEW_LINE);
        // basic.append(Component.text("Neighbor Updates", ThreadedServerHealthDump.HEADER, TextDecoration.BOLD))
        //     .append(ThreadedServerHealthDump.NEW_LINE)
        //     .append(doNeighborInfo());
        return basic.build();
    }

    /* private @NotNull Component doNeighborInfo() {
        TextComponent.@NotNull Builder root = text();
        ServerLevel world = this.level();
        List<CollectingNeighborUpdater.NeighborUpdates> addedThisLayer = new ArrayList<>();
        Deque<CollectingNeighborUpdater.NeighborUpdates> stack = new ArrayDeque<>();
        Map<ResourceLocation, Integer> blockUpdateCounts = new HashMap<>();
        for (final CollectingNeighborUpdater updater : CollectingNeighborUpdater.COLLECTED_COLLECTING_NEIGHBOR_UPDATERS.getOrDefault(world, List.of())) {
            addedThisLayer.addAll(updater.addedThisLayer);
            stack.addAll(updater.stack);
        }
        for (final CollectingNeighborUpdater.NeighborUpdates neighborUpdates : stack) {
            if (neighborUpdates instanceof CollectingNeighborUpdater.FullNeighborUpdate full) {
                ResourceLocation location = CraftNamespacedKey.toMinecraft(full.state().getBukkitMaterial().getKey());
                if (blockUpdateCounts.containsKey(location)) {
                    blockUpdateCounts.put(location, blockUpdateCounts.get(location) + 1);
                } else {
                    blockUpdateCounts.putIfAbsent(location, 1);
                }
            } else if (neighborUpdates instanceof CollectingNeighborUpdater.MultiNeighborUpdate multi) {
                ResourceLocation location = CraftNamespacedKey.toMinecraft(multi.sourceBlock.defaultBlockState().getBukkitMaterial().getKey());
                if (blockUpdateCounts.containsKey(location)) {
                    blockUpdateCounts.put(location, blockUpdateCounts.get(location) + 1);
                } else {
                    blockUpdateCounts.putIfAbsent(location, 1);
                }
            } else if (neighborUpdates instanceof CollectingNeighborUpdater.ShapeUpdate shape) {
                ResourceLocation location = CraftNamespacedKey.toMinecraft(shape.neighborState().getBukkitMaterial().getKey());
                if (blockUpdateCounts.containsKey(location)) {
                    blockUpdateCounts.put(location, blockUpdateCounts.get(location) + 1);
                } else {
                    blockUpdateCounts.putIfAbsent(location, 1);
                }
            } else if (neighborUpdates instanceof CollectingNeighborUpdater.SimpleNeighborUpdate simple) {
                ResourceLocation location = CraftNamespacedKey.toMinecraft(simple.block().defaultBlockState().getBukkitMaterial().getKey());
                if (blockUpdateCounts.containsKey(location)) {
                    blockUpdateCounts.put(location, blockUpdateCounts.get(location) + 1);
                } else {
                    blockUpdateCounts.putIfAbsent(location, 1);
                }
            }
        }

        Map<Class<? extends CollectingNeighborUpdater.NeighborUpdates>, Integer> stackMap = buildNeighborCounts(stack);
        root.append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Stack Count: ", ThreadedServerHealthDump.PRIME_ALT))
            .append(Component.text(stackMap.values().stream().mapToInt(Integer::intValue).sum(), ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE);
        stackMap.forEach((clazz, count) -> root.append(Component.text("    "))
            .append(Component.text("| " + clazz.getSimpleName() + ": ", ThreadedServerHealthDump.PRIME_ALT))
            .append(Component.text(count, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE));
        blockUpdateCounts.forEach((location, count) -> root.append(Component.text("    "))
            .append(Component.text("\\ " + location.toDebugFileName() + ": ", ThreadedServerHealthDump.PRIME_ALT))
            .append(Component.text(count, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE));

        blockUpdateCounts.clear();
        for (final CollectingNeighborUpdater.NeighborUpdates neighborUpdates : addedThisLayer) {
            if (neighborUpdates instanceof CollectingNeighborUpdater.FullNeighborUpdate full) {
                ResourceLocation location = CraftNamespacedKey.toMinecraft(full.state().getBukkitMaterial().getKey());
                if (blockUpdateCounts.containsKey(location)) {
                    blockUpdateCounts.put(location, blockUpdateCounts.get(location) + 1);
                } else {
                    blockUpdateCounts.putIfAbsent(location, 1);
                }
            } else if (neighborUpdates instanceof CollectingNeighborUpdater.MultiNeighborUpdate multi) {
                ResourceLocation location = CraftNamespacedKey.toMinecraft(multi.sourceBlock.defaultBlockState().getBukkitMaterial().getKey());
                if (blockUpdateCounts.containsKey(location)) {
                    blockUpdateCounts.put(location, blockUpdateCounts.get(location) + 1);
                } else {
                    blockUpdateCounts.putIfAbsent(location, 1);
                }
            } else if (neighborUpdates instanceof CollectingNeighborUpdater.ShapeUpdate shape) {
                ResourceLocation location = CraftNamespacedKey.toMinecraft(shape.neighborState().getBukkitMaterial().getKey());
                if (blockUpdateCounts.containsKey(location)) {
                    blockUpdateCounts.put(location, blockUpdateCounts.get(location) + 1);
                } else {
                    blockUpdateCounts.putIfAbsent(location, 1);
                }
            } else if (neighborUpdates instanceof CollectingNeighborUpdater.SimpleNeighborUpdate simple) {
                ResourceLocation location = CraftNamespacedKey.toMinecraft(simple.block().defaultBlockState().getBukkitMaterial().getKey());
                if (blockUpdateCounts.containsKey(location)) {
                    blockUpdateCounts.put(location, blockUpdateCounts.get(location) + 1);
                } else {
                    blockUpdateCounts.putIfAbsent(location, 1);
                }
            }
        }

        stackMap = buildNeighborCounts(addedThisLayer);
        root.append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("AddedThisLayer Count: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(stackMap.values().stream().mapToInt(Integer::intValue).sum(), ThreadedServerHealthDump.PRIMARY))
            .append(ThreadedServerHealthDump.NEW_LINE);
        stackMap.forEach((clazz, count) -> root.append(Component.text("    "))
            .append(Component.text("| " + clazz.getSimpleName() + ": ", ThreadedServerHealthDump.PRIME_ALT))
            .append(Component.text(count, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE));
        blockUpdateCounts.forEach((location, count) -> root.append(Component.text("    "))
            .append(Component.text("\\ " + location.toDebugFileName() + ": ", ThreadedServerHealthDump.PRIME_ALT))
            .append(Component.text(count, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE));
        return root.build();
    }

    private @NotNull Map<Class<? extends CollectingNeighborUpdater.NeighborUpdates>, Integer> buildNeighborCounts(@NotNull Iterable<CollectingNeighborUpdater.NeighborUpdates> updates) {
        Map<Class<? extends CollectingNeighborUpdater.NeighborUpdates>, Integer> retVal = new HashMap<>();
        for (final CollectingNeighborUpdater.NeighborUpdates neighborUpdate : updates) {
            if (!retVal.containsKey(neighborUpdate.getClass())) {
                retVal.put(neighborUpdate.getClass(), 0);
            }
            retVal.put(neighborUpdate.getClass(), retVal.get(neighborUpdate.getClass()) + 1);
        }
        return retVal;
    } */

    private @NotNull Component doTileEntityInfo() {
        TextComponent.@NotNull Builder root = text();
        ServerLevel world = this.level();
        root.append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
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

        world.getAllBlockEntities().forEach(e -> {
            ResourceLocation key = BlockEntityType.getKey(e.getType());

            MutablePair<Integer, Map<ChunkPos, Integer>> info = list.computeIfAbsent(key, k -> MutablePair.of(0, Maps.newHashMap()));
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
        });

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

    private @NotNull Component doEntityInfo() {
        TextComponent.@NotNull Builder root = text();
        ServerLevel world = level();
        root.append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
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

        world.getAllRegionizedEntities().forEach(e -> {
            ResourceLocation key = EntityType.getKey(e.getType());
            MutablePair<Integer, Map<ChunkPos, Integer>> info = list.computeIfAbsent(key, k -> MutablePair.of(0, Maps.newHashMap()));
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
        });

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
}
