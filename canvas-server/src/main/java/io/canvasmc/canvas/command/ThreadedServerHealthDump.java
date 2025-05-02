package io.canvasmc.canvas.command;

import io.canvasmc.canvas.CanvasBootstrap;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.ThreadedBukkitServer;
import io.canvasmc.canvas.TickTimes;
import io.canvasmc.canvas.region.ChunkRegion;
import io.canvasmc.canvas.region.ServerRegions;
import io.canvasmc.canvas.scheduler.TickScheduler;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.ServerBuildInfoImpl;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.util.HSVLike;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;

public class ThreadedServerHealthDump {
    public static final TextColor HEADER = TextColor.color(79, 164, 240);
    public static final TextColor PRIMARY = TextColor.color(48, 145, 237);
    public static final TextColor PRIME_ALT = TextColor.color(48, 157, 240);
    public static final TextColor SECONDARY = TextColor.color(104, 177, 240);
    public static final TextColor INFORMATION = TextColor.color(145, 198, 243);
    public static final TextColor LIST = TextColor.color(33, 97, 188);
    public static final Component NEW_LINE = Component.text("\n");
    public static final ThreadLocal<DecimalFormat> TWO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));
    public static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));
    public static final TextColor ORANGE = TextColor.color(255, 165, 0);

    public static boolean dump(@NotNull final CommandSender sender, boolean regionDump) {
        int build = ServerBuildInfo.buildInfo().buildNumber().orElse(-1);
        final int maxThreadCount = TickScheduler.getScheduler().scheduler.getCoreThreads().length;
        final double minTps;
        final double medianTps;
        final double maxTps;
        final Object2DoubleArrayMap<TickScheduler.FullTick<?>> utilization = new Object2DoubleArrayMap<>();
        final DoubleArrayList allTps = new DoubleArrayList();
        for (final TickScheduler.FullTick<?> fullTick : TickScheduler.FullTick.ALL_REGISTERED) {
            if (fullTick.isSleeping()) continue;
            TickTimes timings15 = fullTick.getTickTimes15s();
            allTps.add(fullTick.getTps15s().getAverage());
            utilization.put(fullTick, timings15.getUtilization());
        }

        allTps.sort(null);
        if (!allTps.isEmpty()) {
            minTps = allTps.getDouble(0);
            maxTps = allTps.getDouble(allTps.size() - 1);

            final int middle = allTps.size() >> 1;
            if ((allTps.size() & 1) == 0) {
                medianTps = (allTps.getDouble(middle - 1) + allTps.getDouble(middle)) / 2.0;
            } else {
                medianTps = allTps.getDouble(middle);
            }
        } else {
            minTps = medianTps = maxTps = 20.0;
        }

        TextComponent.@NotNull Builder root = Component.text();
        final boolean experimental = ServerBuildInfoImpl.IS_EXPERIMENTAL;
        root.append(
            Component.text()
                .append(Component.text("Server Tick Report", HEADER, TextDecoration.BOLD))
                .append(NEW_LINE)
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("Build Info: ", PRIMARY))
                .append(Component.text(ServerBuildInfo.buildInfo().brandName() + "-", INFORMATION))
                .append(
                    CanvasBootstrap.RUNNING_IN_IDE ? Component.text("IDE", TextColor.color(230, 65, 170), TextDecoration.BOLD) : (build == -1 ? Component.text("local", TextColor.color(250, 40, 80))
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text("Running local/dev build, possibly unstable", TextColor.color(250, 40, 80))))
                        : Component.text(build, experimental ? TextColor.color(255, 119, 6) : INFORMATION)
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, experimental ?
                            Component.text("Experimental build, use with caution", TextColor.color(255, 119, 6)) : Component.text("Stable build", INFORMATION))))
                )
                .append(NEW_LINE)
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("Git Info: ", PRIMARY))
                .append(Component.text(ServerBuildInfo.buildInfo().gitBranch().orElse("unknown-branch") + ":" + ServerBuildInfo.buildInfo().gitCommit().orElse("unknown-commit"), INFORMATION))
                .append(NEW_LINE)
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("Utilization: ", PRIMARY))
                .append(Component.text(ONE_DECIMAL_PLACES.get().format(utilization.values().doubleStream().sum()), getColorForMSPT((utilization.values().doubleStream().sum() / ((double) (maxThreadCount * 100))) * 50.0)))
                .append(Component.text("% / ", PRIMARY))
                .append(Component.text(ONE_DECIMAL_PLACES.get().format(maxThreadCount * 100.0), INFORMATION))
                .append(Component.text("%", PRIMARY))
                .append(NEW_LINE)
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("Online Players: ", PRIMARY))
                .append(Component.text(Bukkit.getOnlinePlayers().size(), INFORMATION))
                .append(NEW_LINE)
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("Total FullTicks: ", PRIMARY))
                .append(Component.text(allTps.size(), INFORMATION))
                .append(NEW_LINE)
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("Lowest Loop TPS: ", PRIMARY))
                .append(Component.text(TWO_DECIMAL_PLACES.get().format(minTps), getColorForTPS(minTps)))
                .append(NEW_LINE)
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("Median Loop TPS: ", PRIMARY))
                .append(Component.text(TWO_DECIMAL_PLACES.get().format(medianTps), getColorForTPS(medianTps)))
                .append(NEW_LINE)
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("Highest Loop TPS: ", PRIMARY))
                .append(Component.text(TWO_DECIMAL_PLACES.get().format(maxTps), getColorForTPS(maxTps)))
                .append(NEW_LINE)
        );
        root.append(Component.text()
            .append(Component.text("All " + (regionDump ? "Regions" : "TickLoops"), HEADER, TextDecoration.BOLD))
            .append(NEW_LINE)
        );
        for (final TickScheduler.FullTick<?> tickLoop : TickScheduler.FullTick.ALL_REGISTERED.stream().sorted(TickScheduler.FullTick::compareTo).toList()) {
            if (regionDump || (tickLoop instanceof ChunkRegion)) {
                continue;
            }
            String location = "[" + tickLoop.toString() + "]";
            double mspt5s = Math.min(tickLoop.tickTimes5s.getAverage(), ThreadedBukkitServer.getInstance().getScheduler().getTimeBetweenTicks());
            double tps5s = Math.min(tickLoop.tps5s.getAverage(), ThreadedBukkitServer.getInstance().getScheduler().getTickRate());
            double util = utilization.getDouble(tickLoop);
            TextComponent.@NotNull Builder head = Component.text()
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("TickLoop of ", PRIMARY))
                .append(Component.text(location, INFORMATION)
                    .clickEvent(ClickEvent.callback((audience) -> {
                        audience.sendMessage(
                            Component.text()
                                .append(Component.text(tickLoop.toString(), HEADER, TextDecoration.BOLD))
                                .build()
                        );
                        audience.sendMessage(tickLoop.debugInfo());
                    }))
                    .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text()
                        .append(Component.text(tickLoop.toString(), HEADER, TextDecoration.BOLD))
                        .append(Component.text(" debug info", PRIME_ALT))
                        .build()))
                )
                .append(NEW_LINE)

                .append(Component.text()
                    .append(Component.text("    5s: ", PRIMARY, TextDecoration.BOLD))
                    .append(Component.text(ONE_DECIMAL_PLACES.get().format(util), getColorForMSPT((util / 100) * 50.0)))
                    .append(Component.text("% util at ", PRIMARY))
                    .append(Component.text(TWO_DECIMAL_PLACES.get().format(mspt5s), getColorForMSPT(mspt5s)))
                    .append(Component.text(" MSPT at ", PRIMARY))
                    .append(Component.text(TWO_DECIMAL_PLACES.get().format(tps5s), getColorForTPS(tps5s)))
                    .append(Component.text(" TPS", PRIMARY)))
                .append(NEW_LINE);

            if ((Util.getNanos() - tickLoop.lastRespondedNanos) > TimeUnit.SECONDS.toNanos(5)) {
                // hasn't responded in 5 seconds, warn.
                head.append(Component.text("    Hasn't responded in over 5 seconds! Next scheduled start in " + (tickLoop.getNextScheduledStart() - Util.getNanos()) + " nanos", TextColor.color(200, 52, 34)))
                    .append(NEW_LINE);
            }
            root.append(head.build());
        }
        if (Config.INSTANCE.ticking.enableThreadedRegionizing && regionDump) {
            final List<ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData>> regions =
                new ArrayList<>();

            for (final World bukkitWorld : Bukkit.getWorlds()) {
                final ServerLevel world = ((CraftWorld) bukkitWorld).getHandle();
                world.regioniser.computeForAllRegionsUnsynchronised(regions::add);
            }
            for (final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region : regions) {
                ChunkRegion tickHandle = region.getData().tickHandle;
                String location = "Region at " + tickHandle.world.getDebugLocation() + "|" + region.getCenterChunk();
                double mspt5s = tickHandle.tickTimes5s.getAverage();
                double tps5s = tickHandle.tps5s.getAverage();
                double util = utilization.getDouble(region);
                TextComponent.@NotNull Builder head = Component.text()
                    .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                    .append(Component.text("TickLoop of ", PRIMARY))
                    .append(Component.text(location, INFORMATION))
                    .append(NEW_LINE)

                    .append(Component.text()
                        .append(Component.text("    5s: ", PRIMARY, TextDecoration.BOLD))
                        .append(Component.text(ONE_DECIMAL_PLACES.get().format(util), getColorForMSPT((util / 100) * 50.0)))
                        .append(Component.text("% util at ", PRIMARY))
                        .append(getMSPTColor(Math.min(mspt5s, ThreadedBukkitServer.getInstance().getScheduler().getTimeBetweenTicks())))
                        .append(Component.text(" MSPT at ", PRIMARY))
                        .append(getTPSColor(Math.min(tps5s, ThreadedBukkitServer.getInstance().getScheduler().getTickRate())))
                        .append(Component.text(" TPS", PRIMARY)))
                    .append(NEW_LINE);

                if ((Util.getNanos() - tickHandle.lastRespondedNanos) > TimeUnit.SECONDS.toNanos(5)) {
                    // hasn't responded in 5 seconds, warn.
                    head.append(Component.text("    Hasn't responded in over 5 seconds! Next scheduled start in " + TimeUnit.MILLISECONDS.convert((tickHandle.getNextScheduledStart() - Util.getNanos()), TimeUnit.NANOSECONDS) + "ms", TextColor.color(200, 52, 34)))
                        .append(NEW_LINE);
                }
                root.append(head.build());
            }
        }
        sender.sendMessage(root.build());
        return true;
    }

    public static @NotNull TextColor getColorForTPS(final double tps) {
        final double maxTps = ThreadedBukkitServer.getInstance().getScheduler().getTickRate();
        final double clamped = Math.min(Math.abs(maxTps - tps), maxTps);
        final double percent = clamped / maxTps;

        final double hue = interpolateHue(percent);
        return TextColor.color(HSVLike.hsvLike((float)(hue / 360.0), 0.85f, 0.80f));
    }

    public static @NotNull TextColor getColorForMSPT(final double mspt) {
        final double idealMspt = ThreadedBukkitServer.getInstance().getScheduler().getTimeBetweenTicks();
        final double clamped = Math.min(mspt, idealMspt * 2);
        final double percent = Math.max(0.0, (clamped - idealMspt) / idealMspt);

        final double hue = interpolateHue(percent);
        return TextColor.color(HSVLike.hsvLike((float)(hue / 360.0), 0.85f, 0.80f));
    }

    private static double interpolateHue(double percent) {
        percent = Math.min(Math.max(percent, 0.0), 1.0);
        return 130.0 * (1.0 - percent);
    }

    public static @NotNull Component getTPSColor(double tps) {
        return Component.text(String.format("%.2f", tps)).color(getColorForTPS(tps));
    }

    public static @NotNull Component getMSPTColor(double mspt) {
        return Component.text(String.format("%.2f", mspt)).color(getColorForMSPT(mspt));
    }
}
