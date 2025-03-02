package io.canvasmc.canvas.command;

import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import io.netty.util.Version;
import io.papermc.paper.command.MSPTCommand;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.SpigotConfig;

import static java.lang.String.valueOf;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.TextColor.color;

public class ThreadedTickDiagnosis {
    public static final int BASE_COLOR = 0x4EA2ED;
    public static final int LABEL_COLOR = 0x4EA2ED;
    public static final int VALUE_COLOR = 0x2F8FE9;
    static final TextComponent BASE = text("").color(color(BASE_COLOR));
    public static int HEADER_COLOR = 0x5FC3DD;
    public static int VALUE = 0x96D6F0;

    public static @NotNull Double simplifyNumber(@NotNull Float num) {
        DecimalFormat df = new DecimalFormat("###.##");
        return Double.parseDouble(df.format(num.doubleValue()).replace(",", "."));
    }

    public static boolean execute(@NotNull final CommandSender sender) {
        CraftServer server = (CraftServer) sender.getServer();
        float min;
        float max;
        float median;

        List<Double> tpsValues = MinecraftServer.getThreadedServer().getThreadedWorlds().stream()
                                                .map(level -> level.recentTps[1])
                                                .sorted().toList();

        if (!tpsValues.isEmpty()) {
            min = tpsValues.getFirst().floatValue();
            max = tpsValues.getLast().floatValue();
            median = tpsValues.size() % 2 == 0
                ? (float) ((tpsValues.get(tpsValues.size() / 2 - 1) + tpsValues.get(tpsValues.size() / 2)) / 2.0)
                : tpsValues.get(tpsValues.size() / 2).floatValue();
        } else {
            min = max = median = 0.0f;
        }

        sendCollective((l) -> {
            breaking(l, (list) -> {
                list.add(BASE.append(text("Server Status Report").color(color(HEADER_COLOR))));

                list.add(reportLine(" Online Players: ", valueOf(server.getOnlinePlayers().size()), LABEL_COLOR, VALUE_COLOR));
                list.add(reportLine(" Lowest Thread TPS: ", createColoredComponent(simplifyNumber(min).toString(), min, 20F), LABEL_COLOR));
                list.add(reportLine(" Median Thread TPS: ", createColoredComponent(simplifyNumber(median).toString(), median, 20F), LABEL_COLOR));
                list.add(reportLine(" Highest Thread TPS: ", createColoredComponent(simplifyNumber(max).toString(), max, 20F), LABEL_COLOR));
                list.add(reportLine(" MinecraftServer MSPT: ", MSPTCommand.getColor(MinecraftServer.getServer().tickTimes5s.getAverage()), LABEL_COLOR));
            }, (list) -> {
                list.add(BASE.append(text("Thread Analysis").color(color(HEADER_COLOR))));
                list.add(reportLine(" Netty Version: ", valueOf(Version.identify().get("netty-common")), LABEL_COLOR, VALUE_COLOR));
                list.add(reportLine(" MAX Available threads: ", valueOf(Runtime.getRuntime().availableProcessors()), LABEL_COLOR, VALUE_COLOR));
                list.add(reportLine(" Current Available threads: ", valueOf(Runtime.getRuntime()
                                                                                   .availableProcessors() - usingThreads()), LABEL_COLOR, VALUE_COLOR));

                list.add(BASE.append(text("Util ThreadCount:").color(color(HEADER_COLOR))));
                list.add(subReportLine("NettyIO: ", valueOf(SpigotConfig.getInt("settings.netty-threads", 4)), VALUE_COLOR, VALUE));
                list.add(subReportLine("Moonrise Workers: ", MoonriseCommon.WORKER_POOL.getAliveThreads() + "/" + MoonriseCommon.WORKER_POOL.getCoreThreads().length, VALUE_COLOR, VALUE));
            }, (list) -> {
                MinecraftServer.getThreadedServer().getThreadedWorlds().forEach(level -> doLevel(list, level, BASE));
            }, (list) -> {
                list.add(BASE.append(text("Chunk Analysis").color(color(HEADER_COLOR))));
                chunkInfo(list);
            });

            return l;
        }, sender);
        return true;
    }

    @SafeVarargs
    public static void breaking(@NotNull List<TextComponent> list, Consumer<List<TextComponent>> @NotNull ... builders) {
        TextComponent breaker = text("==================================")
            .color(color(0x2F8FE9));
        list.add(breaker);
        for (final Consumer<List<TextComponent>> builder : builders) {
            builder.accept(list);
            list.add(breaker);
        }
    }

    private static void doLevel(@NotNull List<TextComponent> l, @NotNull ServerLevel level, @NotNull TextComponent base) {
        int playerCount = level.players().size();
        int entityCount = level.moonrise$getEntityLookup().getEntityCount();
        int blockEntityCount = level.blockEntityTickers.size();
        long[] tickDurationsLong = level.tickTimes10s.getTimes();
        if (tickDurationsLong.length == 0) {
            throw new IllegalArgumentException("Array is empty");
        }

        Arrays.sort(tickDurationsLong);
        int index = (int) Math.ceil(0.95 * tickDurationsLong.length) - 1;
        index = Math.max(0, Math.min(index, tickDurationsLong.length - 1));

        double _95$ile = tickDurationsLong[index] / 1.0E6D;

        l.add(base.append(text(" - ThreadedLevel [")
            .append(text(level.dimension().location().toString()).color(color(0x96D6F0)))
            .append(text("]").color(color(0x4EA2ED)))));
        l.add(base.append(text("   ")).append(createTpsComponent(level)));
        l.add(base
            .append(text("   Players: ").append(text(valueOf(playerCount)).color(color(VALUE))))
            .append(text(" Entities: ").append(text(valueOf(entityCount)).color(color(VALUE))))
            .append(text(" Ticking Block Entities: ").append(text(valueOf(blockEntityCount)).color(color(VALUE))))
            .append(text(" MSPT 95%ile: ").append(MSPTCommand.getColor(_95$ile)).append(text("ms")))
        );
    }

    private static @NotNull Component createTpsComponent(@NotNull ServerLevel level) {
        double mspt = level.getNanoSecondsFromLastTick() / 1_000_000;
        float tps = (float) level.recentTps[0];

        return text(" MSPT: ")
            .append(MSPTCommand.getColor(mspt))
            .append(text(" | TPS: "))
            .append(createColoredComponent(simplifyNumber(tps).toString(), tps, 20F));
    }

    private static void chunkInfo(final List<TextComponent> list) {
        List<World> worlds = Bukkit.getWorlds();

        int accumulatedTotal = 0;
        int accumulatedInactive = 0;
        int accumulatedBorder = 0;
        int accumulatedTicking = 0;
        int accumulatedEntityTicking = 0;
        int accumulatedChunkTicking = 0;
        int accumulatedTickingRegions = 0;
        int accumulatedBlockTickingChunkHolders = 0;
        int accumulatedEntityTickingChunkHolders = 0;
        int fullChunksSize = 0;
        int newChunkHolderCount = 0;

        for (final World bukkitWorld : worlds) {
            final ServerLevel world = ((CraftWorld) bukkitWorld).getHandle();

            int total = 0;
            int inactive = 0;
            int full = 0;
            int blockTicking = 0;
            int entityTicking = 0;
            fullChunksSize += world.getChunkSource().getFullChunksCount();
            newChunkHolderCount += world.moonrise$getChunkTaskScheduler().chunkHolderManager.size();

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
            accumulatedChunkTicking += world.chunkSource.lastTickingChunksCount;
            accumulatedTickingRegions += world.chunkSource.tickingRegionsCount;
            accumulatedBlockTickingChunkHolders += world.moonrise$getChunkTaskScheduler().chunkHolderManager.blockTickingChunkHolders.size();
            accumulatedEntityTickingChunkHolders += world.moonrise$getChunkTaskScheduler().chunkHolderManager.entityTickingChunkHolders.size();

            list.add(text("  ").toBuilder().append(text("Chunks in ", color(0x4EA2ED)), text(bukkitWorld.getName(), GREEN), text(":")).build());
            list.add(text("  ").toBuilder().color(NamedTextColor.AQUA).append(
                text("Total: ", color(0x4EA2ED)), text(total),
                text(" Inactive: ", color(0x4EA2ED)), text(inactive),
                text(" Entity Ticking: ", color(0x4EA2ED)), text(entityTicking),
                text(" Block Ticking: ", color(0x4EA2ED)), text(blockTicking),
                text(" Full: ", color(0x4EA2ED)), text(full),
                text(" Chunk Ticking: ", color(0x4EA2ED)), text(world.chunkSource.lastTickingChunksCount),
                text(" Tick Regions: ", color(0x4EA2ED)), text(world.chunkSource.tickingRegionsCount),
                text(" Entity Ticking Holders: ", color(0x4EA2ED)), text(world.moonrise$getChunkTaskScheduler().chunkHolderManager.entityTickingChunkHolders.size()),
                text(" Block Ticking Holders: ", color(0x4EA2ED)), text(world.moonrise$getChunkTaskScheduler().chunkHolderManager.blockTickingChunkHolders.size())
            ).build());
        }
        if (worlds.size() > 1) {
            list.add(text("  ").toBuilder().append(text("Chunks in ", color(0x4EA2ED)), text("all listed worlds", GREEN), text(":", NamedTextColor.AQUA))
                               .build());
            list.add(text("  ").toBuilder().color(NamedTextColor.AQUA).append(
                text("Total: ", color(0x4EA2ED)), text(accumulatedTotal),
                text(" Inactive: ", color(0x4EA2ED)), text(accumulatedInactive),
                text(" Entity Ticking: ", color(0x4EA2ED)), text(accumulatedEntityTicking),
                text(" Block Ticking: ", color(0x4EA2ED)), text(accumulatedTicking),
                text(" Full: ", color(0x4EA2ED)), text(accumulatedBorder),
                text(" Chunk Ticking: ", color(0x4EA2ED)), text(accumulatedChunkTicking),
                text(" Tick Regions: ", color(0x4EA2ED)), text(accumulatedTickingRegions),
                text(" Entity Ticking Holders: ", color(0x4EA2ED)), text(accumulatedEntityTickingChunkHolders),
                text(" Block Ticking Holders: ", color(0x4EA2ED)), text(accumulatedBlockTickingChunkHolders)
            ).build());
        }
        list.add(text("  ").toBuilder().color(NamedTextColor.AQUA).append(text(" LevelChunk full count", color(0x4EA2ED)), text(fullChunksSize)).build());
        list.add(text("  ").toBuilder().color(NamedTextColor.AQUA).append(text(" NewChunkHolder count", color(0x4EA2ED)), text(newChunkHolderCount)).build());
    }

    public static void sendCollective(@NotNull Function<List<TextComponent>, List<TextComponent>> builder, CommandSender sender) {
        Component base = Component.text("").appendNewline();
        for (final TextComponent textComponent : builder.apply(new ArrayList<>())) {
            base = base.append(textComponent).appendNewline();
        }
        sender.sendMessage(base);
    }

    private static @NotNull TextComponent reportLine(String label, String value, int labelColor, int valueColor) {
        return (text(label).color(color(labelColor))
                           .append(text(value).color(color(valueColor))));
    }

    private static @NotNull TextComponent reportLine(String label, Component value, int labelColor) {
        return (text(label).color(color(labelColor)).append(value));
    }

    private static @NotNull TextComponent subReportLine(String label, String value, int labelColor, int valueColor) {
        return (text("   " + label).color(color(labelColor))
                                   .append(text(value).color(color(valueColor))));
    }

    public static int usingThreads() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        return (int) java.util.Arrays.stream(threadMXBean.getAllThreadIds())
                                     .mapToObj(threadMXBean::getThreadInfo)
                                     .filter(threadInfo -> threadInfo != null && threadInfo.getThreadState() == Thread.State.RUNNABLE)
                                     .count();
    }

    public static @NotNull Component createColoredUtilComponent(String text, float value) {
        float ratio = Math.max(0, Math.min(100, value)) / 100.0f;
        int red = (ratio <= 0.5) ? (int) (ratio * 510) : 255;
        int green = (ratio <= 0.5) ? 255 : (int) ((1.0f - ratio) * 510);

        return text(text).color(color(red, green, 0));
    }

    public static @NotNull Component createColoredComponent(String text, float value, float outOf) {
        float ratio = Math.max(0, Math.min(outOf, value)) / outOf;
        int red = (int) ((1 - ratio) * 255);
        int green = (int) (ratio * 255);

        return text(text).color(color(red, green, 0));
    }
}
