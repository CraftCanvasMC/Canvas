package io.canvasmc.canvas.server.level;

import com.google.common.collect.Maps;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.LevelAccess;
import io.canvasmc.canvas.command.ThreadedTickDiagnosis;
import io.canvasmc.canvas.entity.SleepingBlockEntity;
import io.canvasmc.canvas.server.AbstractTick;
import io.canvasmc.canvas.server.AbstractTickLoop;
import io.canvasmc.canvas.server.AverageTickTimeAccessor;
import io.canvasmc.canvas.server.ThreadedServer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.World;
import org.bukkit.craftbukkit.scheduler.CraftScheduler;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

public abstract class MinecraftServerWorld extends AbstractTickLoop<LevelThread, ServerLevel> implements TickRateManagerInstance, LevelAccess, AverageTickTimeAccessor {
    protected final ConcurrentLinkedQueue<Runnable> queuedForNextTickPost = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<Runnable> queuedForNextTickPre = new ConcurrentLinkedQueue<>();
    protected final ServerTickRateManager tickRateManager;
    protected final CraftScheduler bukkitScheduler;
    private long emptyTicks = 0L;

    public MinecraftServerWorld(final String name, final String debugName) {
        super(name, debugName, (r, n, self) -> new LevelThread(ThreadedServer.SERVER_THREAD_GROUP, r, n, self));
        this.tickRateManager = new ServerTickRateManager(this);
        this.bukkitScheduler = new CraftScheduler();
        this.setThreadModifier((levelThread) -> {
            levelThread.setName(this.name());
            levelThread.setPriority(Config.INSTANCE.tickLoopThreadPriority);
            levelThread.setUncaughtExceptionHandler((_, throwable) -> LOGGER.error("Uncaught exception in level thread, {}", this.name(), throwable));
        });
        this.setPreBlockStart(() -> {
            if (Config.INSTANCE.useLevelThreadsAsChunkSourceMain) level().chunkSource.mainThread = this.owner;
        });
    }

    @Override
    public boolean pollInternal() {
        if (super.pollInternal()) {
            return true;
        } else {
            boolean ret = false;
            if (tickRateManager().isSprinting() || this.haveTime()) {
                ServerLevel worldserver = level();

                if (worldserver.getChunkSource().pollTask()) {
                    ret = true;
                }
            }

            return ret;
        }
    }

    @Override
    public long blockTick(final long tickSection, final @NotNull AbstractTick tick) {
        // pretick, check if empty and should sleep
        MinecraftServer server = MinecraftServer.getServer();
        int i = server.pauseWhileEmptySeconds() * 20;
        if (Config.INSTANCE.emptySleepPerWorlds && i > 0) {
            if (this.level().players().isEmpty() && !this.tickRateManager.isSprinting() && server.pluginsBlockingSleep.isEmpty()) {
                this.emptyTicks++;
            } else {
                this.emptyTicks = 0;
            }

            if (this.emptyTicks >= i) {
                if (this.emptyTicks == i) {
                    LOGGER.info("Level empty for {} seconds, pausing", server.pauseWhileEmptySeconds());
                    this.sleep();
                    this.emptyTicks = 0;
                }
            }
        }
        return super.blockTick(tickSection, tick);
    }

    public ServerLevel level() {
        return (ServerLevel) this;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public World getWorld() {
        return this.level().getWorld();
    }

    @Override
    public void scheduleOnThread(final Runnable runnable) {
        this.scheduleOnMain(runnable);
    }

    @Override
    public CommandSourceStack createCommandSourceStack() {
        return MinecraftServer.getServer().createCommandSourceStack();
    }

    @Override
    public void onTickRateChanged() {
        MinecraftServer.getServer().onTickRateChanged();
    }

    @Override
    public void broadcastPacketsToPlayers(final Packet<?> packet) {
        for (final ServerPlayer player : this.level().players()) {
            player.connection.send(packet);
        }
    }

    @Override
    public void skipTickWait() {
        this.delayedTasksMaxNextTickTimeNanos = Util.getNanos();
        this.nextTickTimeNanos = Util.getNanos();
    }

    public boolean isLevelThread() {
        return Thread.currentThread() instanceof LevelThread;
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
    public <V> V scheduleOnThread(final Callable<V> callable) throws Exception {
        Thread current = Thread.currentThread();
        if (current.equals(getRunningThread())) {
            return callable.call();
        }
        final AtomicReference<V> retVal = new AtomicReference<V>();
        final AtomicBoolean finished = new AtomicBoolean(false);
        this.scheduleOnMain(() -> {
            try {
                retVal.set(callable.call());
                finished.set(true);
            } catch (Exception e) {
                throw new RuntimeException("Unexpected exception occurred when running Callable<V> to level thread", e);
            }
        });
        this.managedBlock(finished::get);
        return retVal.get();
    }

    @Override
    public boolean isTicking() {
        return super.isTicking() && this.tickCount >= 1;
    }

    @Override
    public double getAverageTickTime() {
        return getNanoSecondsFromLastTick() / 1_000_000;
    }

    @Override
    public BukkitScheduler getBukkitScheduler() {
        return bukkitScheduler;
    }

    @Override
    public String location() {
        return "w:" + this.level().dimension().location();
    }

    @Override
    public @NotNull Component debugInfo() {
        TextComponent.Builder basic = Component.text()
            .append(Component.text("Basic Information", ThreadedTickDiagnosis.HEADER, TextDecoration.BOLD))
            .append(ThreadedTickDiagnosis.NEW_LINE)
            .append(Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
            .append(Component.text("Pending Block Ticks: ", ThreadedTickDiagnosis.PRIMARY))
            .append(Component.text(this.level().getBlockTicks().count(), ThreadedTickDiagnosis.PRIMARY))
            .append(ThreadedTickDiagnosis.NEW_LINE)
            .append(Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
            .append(Component.text("Pending Fluid Ticks: ", ThreadedTickDiagnosis.PRIMARY))
            .append(Component.text(this.level().getFluidTicks().count(), ThreadedTickDiagnosis.PRIMARY))
            .append(ThreadedTickDiagnosis.NEW_LINE)
            .append(Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
            .append(Component.text("Local Players: ", ThreadedTickDiagnosis.PRIMARY))
            .append(Component.text(this.level().players().size(), ThreadedTickDiagnosis.PRIMARY))
            .append(ThreadedTickDiagnosis.NEW_LINE)
            .append(Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
            .append(Component.text("Local Entities: ", ThreadedTickDiagnosis.PRIMARY))
            .append(Component.text(this.level().entityTickList.entities.size(), ThreadedTickDiagnosis.PRIMARY))
            .append(ThreadedTickDiagnosis.NEW_LINE)
            .append(Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
            .append(Component.text("Ticking Chunks: ", ThreadedTickDiagnosis.PRIMARY))
            .append(Component.text(this.level().getChunkSource().lastTickingChunksCount, ThreadedTickDiagnosis.PRIMARY))
            .append(ThreadedTickDiagnosis.NEW_LINE);
        if (Config.INSTANCE.chunks.regionized.enableRegionizedChunkTicking) {
            basic.append(Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
                .append(Component.text("Active ChunkRegions: ", ThreadedTickDiagnosis.PRIMARY))
                .append(Component.text(this.level().getChunkSource().tickingRegionsCount, ThreadedTickDiagnosis.PRIMARY))
                .append(ThreadedTickDiagnosis.NEW_LINE);
        }
        basic.append(Component.text("Tile Entities", ThreadedTickDiagnosis.HEADER, TextDecoration.BOLD))
            .append(ThreadedTickDiagnosis.NEW_LINE)
            .append(doTileEntityInfo())
            .append(ThreadedTickDiagnosis.NEW_LINE);
        basic.append(Component.text("Neighbor Updates", ThreadedTickDiagnosis.HEADER, TextDecoration.BOLD))
            .append(ThreadedTickDiagnosis.NEW_LINE)
            .append(doNeighborInfo());
        return basic.build();
    }

    private @NotNull Component doNeighborInfo() {
        TextComponent.@NotNull Builder root = text();
        ServerLevel world = this.level();
        // TODO - debug info for threadlocals
        /*
        NeighborUpdater updater = world.neighborUpdater.get();
        if (updater instanceof CollectingNeighborUpdater collectingNeighborUpdater) {
            List<CollectingNeighborUpdater.NeighborUpdates> addedThisLayer = new ArrayList<>(collectingNeighborUpdater.addedThisLayer);
            Deque<CollectingNeighborUpdater.NeighborUpdates> stack = new ArrayDeque<>(collectingNeighborUpdater.stack);
            Map<ResourceLocation, Integer> blockUpdateCounts = new HashMap<>();
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
            root.append(Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
                .append(Component.text("Stack Count: ", ThreadedTickDiagnosis.PRIME_ALT))
                .append(Component.text(stackMap.values().stream().mapToInt(Integer::intValue).sum(), ThreadedTickDiagnosis.INFORMATION))
                .append(ThreadedTickDiagnosis.NEW_LINE);
            stackMap.forEach((clazz, count) -> root.append(Component.text("    "))
                .append(Component.text("| " + clazz.getSimpleName() + ": ", ThreadedTickDiagnosis.PRIME_ALT))
                .append(Component.text(count, ThreadedTickDiagnosis.INFORMATION))
                .append(ThreadedTickDiagnosis.NEW_LINE));
            blockUpdateCounts.forEach((location, count) -> root.append(Component.text("    "))
                .append(Component.text("\\ " + location.toDebugFileName() + ": ", ThreadedTickDiagnosis.PRIME_ALT))
                .append(Component.text(count, ThreadedTickDiagnosis.INFORMATION))
                .append(ThreadedTickDiagnosis.NEW_LINE));

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
            root.append(Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
                .append(Component.text("AddedThisLayer Count: ", ThreadedTickDiagnosis.PRIMARY))
                .append(Component.text(stackMap.values().stream().mapToInt(Integer::intValue).sum(), ThreadedTickDiagnosis.PRIMARY))
                .append(ThreadedTickDiagnosis.NEW_LINE);
            stackMap.forEach((clazz, count) -> root.append(Component.text("    "))
                .append(Component.text("| " + clazz.getSimpleName() + ": ", ThreadedTickDiagnosis.PRIME_ALT))
                .append(Component.text(count, ThreadedTickDiagnosis.INFORMATION))
                .append(ThreadedTickDiagnosis.NEW_LINE));
            blockUpdateCounts.forEach((location, count) -> root.append(Component.text("    "))
                .append(Component.text("\\ " + location.toDebugFileName() + ": ", ThreadedTickDiagnosis.PRIME_ALT))
                .append(Component.text(count, ThreadedTickDiagnosis.INFORMATION))
                .append(ThreadedTickDiagnosis.NEW_LINE));
        } else {
            root.append(Component.text("World NeighborUpdater not instanceof CollectingNeighborUpdater", TextColor.color(239, 35, 58)));
        }
         */
        return root.build();
    }

    /* private @NotNull Map<Class<? extends CollectingNeighborUpdater.NeighborUpdates>, Integer> buildNeighborCounts(@NotNull Iterable<CollectingNeighborUpdater.NeighborUpdates> updates) {
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
        root.append(Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
            .append(Component.text("World ", ThreadedTickDiagnosis.PRIMARY))
            .append(Component.text("[" + world.dimension().location().toDebugFileName() + "]", ThreadedTickDiagnosis.INFORMATION))
            .append(Component.text(":", ThreadedTickDiagnosis.PRIMARY))
            .append(ThreadedTickDiagnosis.NEW_LINE);
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
                .append(ThreadedTickDiagnosis.NEW_LINE);
            return root.build();
        }

        int count = info.stream().mapToInt(Pair::getRight).sum();
        int nonTickingCount = nonEntityTicking.values().stream().mapToInt(Integer::intValue).sum();
        root.append(Component.text("    Total Ticking: ", ThreadedTickDiagnosis.PRIME_ALT))
            .append(Component.text((count - nonTickingCount), ThreadedTickDiagnosis.INFORMATION))
            .append(Component.text(", Total Non-Ticking: ", ThreadedTickDiagnosis.PRIME_ALT))
            .append(Component.text(nonTickingCount, ThreadedTickDiagnosis.INFORMATION))
            .append(ThreadedTickDiagnosis.NEW_LINE);

        if (highest.get() != null) {
            BlockPos blockPos = highest.get().getMiddleBlockPosition(90);
            root.append(Component.text("    "))
                .append(Component.text("[Teleport to most populated ChunkPos]", ThreadedTickDiagnosis.SECONDARY)
                    .clickEvent(ClickEvent.runCommand("/execute in " + world.dimension().location() + " run teleport @s " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ())))
                .append(ThreadedTickDiagnosis.NEW_LINE);
        }

        info.forEach(e -> {
            int nonTicking = nonEntityTicking.getOrDefault(e.getKey(), 0);
            root.append(Component.text("       "))
                .append(Component.text((e.getValue() - nonTicking), ThreadedTickDiagnosis.INFORMATION))
                .append(Component.text(" (", ThreadedTickDiagnosis.PRIME_ALT))
                .append(Component.text(nonTicking, ThreadedTickDiagnosis.INFORMATION))
                .append(Component.text(") : ", ThreadedTickDiagnosis.PRIME_ALT))
                .append(Component.text(e.getKey().toDebugFileName(), ThreadedTickDiagnosis.INFORMATION))
                .append(ThreadedTickDiagnosis.NEW_LINE);
        });

        if (highest.get() != null) {
            root.append(Component.text("    Chunk with highest ticking block entities: ", ThreadedTickDiagnosis.PRIME_ALT))
                .append(Component.text(highest + " (" + highestCount + " entities)", ThreadedTickDiagnosis.INFORMATION))
                .append(ThreadedTickDiagnosis.NEW_LINE);
        }

        return root.append(Component.text("* First number is ticking entities, second number is non-ticking entities", ThreadedTickDiagnosis.PRIMARY))
            .build();
    }
}
