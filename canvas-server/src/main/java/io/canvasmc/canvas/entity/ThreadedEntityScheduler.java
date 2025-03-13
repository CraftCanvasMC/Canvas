package io.canvasmc.canvas.entity;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.google.common.collect.Maps;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.command.ThreadedTickDiagnosis;
import io.canvasmc.canvas.server.AbstractTickLoop;
import io.canvasmc.canvas.server.AverageTickTimeAccessor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

public class ThreadedEntityScheduler extends AbstractTickLoop<TickThread, ThreadedEntityScheduler> implements AverageTickTimeAccessor {

    public ThreadedEntityScheduler(final String name, final String debugName) {
        super(name, debugName);
        this.setThreadModifier((levelThread) -> {
            levelThread.setName(this.name());
            levelThread.setPriority(Config.INSTANCE.tickLoopThreadPriority);
            levelThread.setUncaughtExceptionHandler((_, throwable) -> LOGGER.error("Uncaught exception in entity thread", throwable));
        });
    }

    public void tickEntities() {
        for (final ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
            if (level.isSleeping()) continue;
            level.tickEntities(true);
        }
    }

    @Override
    public double getAverageTickTime() {
        return getNanoSecondsFromLastTick() / 1_000_000;
    }

    @Override
    public String getName() {
        return name();
    }

    public ServerTickRateManager tickRateManager() {
        return MinecraftServer.getServer().tickRateManager();
    }

    @Override
    public @NotNull Component debugInfo() {
        int allEntities = 0;
        for (final ServerLevel world : MinecraftServer.getServer().getAllLevels()) {
            allEntities += world.entityTickList.entities.size();
        }
        return Component.text()
            // total entities
            .append(Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
            .append(Component.text("Total Ticking Entities: ", ThreadedTickDiagnosis.PRIMARY))
            .append(Component.text(allEntities, ThreadedTickDiagnosis.INFORMATION))
            .append(ThreadedTickDiagnosis.NEW_LINE)
            // per-world
            .append(buildPerWorldDebug())
            .build();
    }

    private @NotNull Component buildPerWorldDebug() {
        TextComponent.@NotNull Builder root = text()
            .append(Component.text("Per World Details", ThreadedTickDiagnosis.HEADER, TextDecoration.BOLD))
            .append(ThreadedTickDiagnosis.NEW_LINE);

        for (final ServerLevel world : MinecraftServer.getServer().getAllLevels()) {
            root.append(Component.text(" - ", ThreadedTickDiagnosis.LIST, TextDecoration.BOLD))
                .append(Component.text("World ", ThreadedTickDiagnosis.PRIMARY))
                .append(Component.text("[" + world.dimension().location().toDebugFileName() + "]", ThreadedTickDiagnosis.INFORMATION))
                .append(Component.text(":", ThreadedTickDiagnosis.PRIMARY))
                .append(ThreadedTickDiagnosis.NEW_LINE);

            String filter = "*";
            final String cleanfilter = filter.replace("?", ".?").replace("*", ".*?");
            Set<ResourceLocation> names = BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .filter(n -> n.toString().matches(cleanfilter))
                .collect(Collectors.toSet());

            Map<ResourceLocation, MutablePair<Integer, Map<ChunkPos, Integer>>> list = Maps.newHashMap();
            Map<ResourceLocation, Integer> nonEntityTicking = Maps.newHashMap();

            final AtomicReference<ChunkPos> highest = new AtomicReference<>();
            final AtomicInteger highestCount = new AtomicInteger();

            world.getAllEntities().forEach(e -> {
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
                    .append(ThreadedTickDiagnosis.NEW_LINE);
                continue;
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
                root.append(Component.text("    Chunk with highest ticking entities: ", ThreadedTickDiagnosis.PRIME_ALT))
                    .append(Component.text(highest + " (" + highestCount + " entities)", ThreadedTickDiagnosis.INFORMATION))
                    .append(ThreadedTickDiagnosis.NEW_LINE);
            }
        }
        return root.append(Component.text("* First number is ticking entities, second number is non-ticking entities", ThreadedTickDiagnosis.PRIMARY))
            .build();
    }
}
