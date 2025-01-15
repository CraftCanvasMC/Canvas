package io.canvasmc.canvas.command;

import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import io.netty.util.Version;
import io.papermc.paper.command.MSPTCommand;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.SpigotConfig;

import static java.lang.String.valueOf;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.TextColor.color;

public class ThreadedTickDiagnosis {
    private static final List<String> TPS_OPTIONS = Arrays.asList("tps5", "tps10", "tps60");
    static int HEADER = 0x5FC3DD;
    static int VALUE = 0x96D6F0;

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
            TextComponent base = text("").color(color(0x4EA2ED));
            TextComponent breaker = text("==================================")
                .color(color(0x2F8FE9));

            l.add(breaker);
            l.add(base.append(text("Server Status Report").color(color(HEADER))));

            l.add(reportLine(" Online Players: ", valueOf(server.getOnlinePlayers().size()), 0x4EA2ED, 0x2F8FE9));
            l.add(reportLine(" Lowest Thread TPS: ", createColoredComponent(simplifyNumber(min).toString(), min, 20F), 0x4EA2ED));
            l.add(reportLine(" Median Thread TPS: ", createColoredComponent(simplifyNumber(median).toString(), median, 20F), 0x4EA2ED));
            l.add(reportLine(" Highest Thread TPS: ", createColoredComponent(simplifyNumber(max).toString(), max, 20F), 0x4EA2ED));
            l.add(reportLine(" MinecraftServer MSPT: ", MSPTCommand.getColor(MinecraftServer.getServer().tickTimes5s.getAverage()), 0x4EA2ED));

            l.add(breaker);

            l.add(base.append(text(" Thread Analysis").color(color(HEADER))));
            l.add(reportLine(" Netty Version: ", valueOf(Version.identify().get("netty-common")), 0x4EA2ED, 0x2F8FE9));
            l.add(reportLine(" MAX Available threads: ", valueOf(Runtime.getRuntime().availableProcessors()), 0x4EA2ED, 0x2F8FE9));
            l.add(reportLine(" Current Available threads: ", valueOf(Runtime.getRuntime().availableProcessors() - usingThreads()), 0x4EA2ED, 0x2F8FE9));

            l.add(base.append(text(" Util ThreadCount:").color(color(0x4EA2ED))));
            l.add(subReportLine("NettyIO: ", valueOf(SpigotConfig.getInt("settings.netty-threads", 4)), 0x2F8FE9, VALUE));
            l.add(subReportLine("Moonrise Workers: ", valueOf(MoonriseCommon.WORKER_POOL.getCoreThreads().length), 0x2F8FE9, VALUE));

            l.add(breaker);

            for (ServerLevel level : MinecraftServer.getThreadedServer().getThreadedWorlds()) {
                int chunkCount = level.getChunkSource().getLoadedChunksCount();
                int playerCount = level.players().size();
                int entityCount = level.moonrise$getEntityLookup().getEntityCount();

                l.add(base.append(text(" - ThreadedLevel [")
                    .append(text(level.dimension().location().toString()).color(color(0x96D6F0)))
                    .append(text("]").color(color(0x4EA2ED)))));

                l.add(base.append(Component.text("   ")).append(MSPTCommand.getColor(level.getNanoSecondsFromLastTick() / 1_000_000)
                    .append(text(" MSPT at "))
                    .append(createColoredComponent(simplifyNumber((float) level.recentTps[0]).toString(), (float) level.recentTps[0], 20F))
                    .append(text(" TPS"))));

                float threadUtil = (float) (((level.getNanoSecondsFromLastTick() / 1_000_000.0) / 50) * 100);
                if (threadUtil > 100) threadUtil = 100;

                l.add(base.append(text("   Chunks: ")
                    .append(text(valueOf(chunkCount)).color(color(VALUE)))
                    .append(text(" Players: ").append(text(valueOf(playerCount)).color(color(VALUE))))
                    .append(text(" Entities: ").append(text(valueOf(entityCount)).color(color(VALUE))))
                    .append(text(" Thread Utilization: ").append(createColoredUtilComponent(simplifyNumber(threadUtil) + "%", threadUtil)))));
            }

            l.add(breaker);
            return l;
        }, sender);
        return true;
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
