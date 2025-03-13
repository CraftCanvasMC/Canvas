package io.canvasmc.canvas.command;

import io.canvasmc.canvas.server.AbstractTickLoop;
import io.canvasmc.canvas.server.ThreadedServer;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.ServerBuildInfoImpl;
import java.text.DecimalFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.util.HSVLike;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ThreadedTickDiagnosis {
    public static final TextColor HEADER = TextColor.color(79, 164, 240);
    public static final TextColor PRIMARY = TextColor.color(48, 145, 237);
    public static final TextColor PRIME_ALT = TextColor.color(48, 157, 240);
    public static final TextColor SECONDARY = TextColor.color(104, 177, 240);
    public static final TextColor INFORMATION = TextColor.color(145, 198, 243);
    public static final TextColor LIST = TextColor.color(33, 97, 188);
    public static final Component NEW_LINE = Component.text("\n");
    public static final ThreadLocal<DecimalFormat> THREE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.000");
    });
    public static final ThreadLocal<DecimalFormat> TWO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.00");
    });
    public static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.0");
    });
    public static final ThreadLocal<DecimalFormat> NO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0");
    });
    public static final TextColor ORANGE = TextColor.color(255, 165, 0);

    public static boolean dump(@NotNull final CommandSender sender) {
        ThreadedServer server = MinecraftServer.getThreadedServer();
        int build = ServerBuildInfo.buildInfo().buildNumber().orElse(-1);
        TextComponent.@NotNull Builder root = Component.text();
        root.append(
            Component.text()
                .append(Component.text("Server Tick Report", HEADER, TextDecoration.BOLD))
                .append(NEW_LINE)
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("Build Info: ", PRIMARY))
                .append(Component.text(ServerBuildInfo.buildInfo().brandName() + "-", INFORMATION))
                .append(
                    build == -1 ? Component.text("LOCAL", TextColor.color(250, 40, 80))
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text("Running local/dev build, possibly unstable", TextColor.color(250, 40, 80))))
                        : Component.text(build, ServerBuildInfoImpl.IS_EXPERIMENTAL ? TextColor.color(255, 119, 6) : INFORMATION)
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, ServerBuildInfoImpl.IS_EXPERIMENTAL ?
                            Component.text("Experimental build, use with caution", TextColor.color(255, 119, 6)) : Component.text("Stable build", INFORMATION)))
                )
                .append(NEW_LINE)
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("Online Players: ", PRIMARY))
                .append(Component.text(Bukkit.getOnlinePlayers().size(), INFORMATION))
                .append(NEW_LINE)
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("Total TickLoops: ", PRIMARY))
                .append(Component.text(server.getTickLoops().size(), INFORMATION))
                .append(NEW_LINE)
        );
        root.append(Component.text()
            .append(Component.text("All TickLoops", HEADER, TextDecoration.BOLD))
            .append(NEW_LINE)
        );
        for (final AbstractTickLoop<?, ?> tickLoop : server.getTickLoops()) {
            String location = "[" + tickLoop.location() + "]";
            double mspt5s = tickLoop.tickTimes5s.getAverage();
            double tps5s = tickLoop.tps5s.getAverage();
            double util = tickLoop.tickTimes5s.getUtilization();
            // TODO - `isHandlingTick` and `lastRespondTime`
            Component head = Component.text()
                .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                .append(Component.text("TickLoop of ", PRIMARY))
                .append(Component.text(location, INFORMATION)
                    .clickEvent(ClickEvent.callback((audience) -> {
                        audience.sendMessage(
                            Component.text()
                                .append(Component.text(tickLoop.name(), HEADER, TextDecoration.BOLD))
                                .build()
                        );
                        audience.sendMessage(tickLoop.debugInfo());
                    }))
                    .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text()
                        .append(Component.text(tickLoop.name(), HEADER, TextDecoration.BOLD))
                        .append(Component.text(" debug info", PRIME_ALT))
                        .build()))
                )
                .append(NEW_LINE)

                .append(Component.text()
                    .append(Component.text("    5s: ", PRIMARY, TextDecoration.BOLD))
                    .append(Component.text(ONE_DECIMAL_PLACES.get().format(util), getColourForMSPT((util / 100) * 50.0)))
                    .append(Component.text("% util at ", PRIMARY))
                    .append(Component.text(TWO_DECIMAL_PLACES.get().format(mspt5s), getColourForMSPT(mspt5s)))
                    .append(Component.text(" MSPT at ", PRIMARY))
                    .append(Component.text(TWO_DECIMAL_PLACES.get().format(tps5s), getColourForTPS(tps5s)))
                    .append(Component.text(" TPS", PRIMARY)))
                .append(NEW_LINE)

                .build();
            root.append(head);
        }
        sender.sendMessage(root.build());
        return true;
    }

    public static @NotNull TextColor getColourForTPS(final double tps) {
        final double difference = Math.min(Math.abs(20.0 - tps), 20.0);
        final double coordinate;
        if (difference <= 2.0) {
            coordinate = 70.0 + ((140.0 - 70.0) / (0.0 - 2.0)) * (difference - 2.0);
        } else if (difference <= 5.0) {
            coordinate = 30.0 + ((70.0 - 30.0) / (2.0 - 5.0)) * (difference - 5.0);
        } else if (difference <= 10.0) {
            coordinate = 10.0 + ((30.0 - 10.0) / (5.0 - 10.0)) * (difference - 10.0);
        } else {
            coordinate = 0.0 + ((10.0 - 0.0) / (10.0 - 20.0)) * (difference - 20.0);
        }

        return TextColor.color(HSVLike.hsvLike((float) (coordinate / 360.0), 85.0f / 100.0f, 80.0f / 100.0f));
    }

    public static @NotNull TextColor getColourForMSPT(final double mspt) {
        final double clamped = Math.min(Math.abs(mspt), 50.0);
        final double coordinate;
        if (clamped <= 15.0) {
            coordinate = 130.0 + ((140.0 - 130.0) / (0.0 - 15.0)) * (clamped - 15.0);
        } else if (clamped <= 25.0) {
            coordinate = 90.0 + ((130.0 - 90.0) / (15.0 - 25.0)) * (clamped - 25.0);
        } else if (clamped <= 35.0) {
            coordinate = 30.0 + ((90.0 - 30.0) / (25.0 - 35.0)) * (clamped - 35.0);
        } else if (clamped <= 40.0) {
            coordinate = 15.0 + ((30.0 - 15.0) / (35.0 - 40.0)) * (clamped - 40.0);
        } else {
            coordinate = 0.0 + ((15.0 - 0.0) / (40.0 - 50.0)) * (clamped - 50.0);
        }

        return TextColor.color(HSVLike.hsvLike((float) (coordinate / 360.0), 85.0f / 100.0f, 80.0f / 100.0f));
    }

}
