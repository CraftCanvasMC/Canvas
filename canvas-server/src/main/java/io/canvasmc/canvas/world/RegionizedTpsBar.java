package io.canvasmc.canvas.world;

import ca.spottedleaf.common.time.TickData;
import com.mojang.datafixers.util.Pair;
import io.canvasmc.canvas.WorldConfig;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.commands.CommandUtil;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import static io.papermc.paper.threadedregions.commands.CommandUtil.SPRINTING_COLOR;

public class RegionizedTpsBar extends RegionResourceBar {
    private static final ThreadLocal<DecimalFormat> TPS_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));
    private static final ThreadLocal<DecimalFormat> MSPT_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));
    private static final ThreadLocal<DecimalFormat> UTIL_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));
    private static final ThreadLocal<DecimalFormat> INT_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0"));

    private static final String[] KEYS = {"tps", "mspt", "util", "players"};

    // we have this as an instance field because formats can be per-world
    private final AtomicReference<FormatEntry> cachedFormat = new AtomicReference<>(null);

    public RegionizedTpsBar() {
        super((worldData) -> worldData.world.canvasConfig().regionBars.enableTpsBar);
    }

    @Override
    String getDefaultFormat() {
        return WorldConfig.DEFAULT_TPSBAR_FORMAT;
    }

    @Override
    String normalize(final @NonNull String input) {
        return input
            .replace("%tps%", "<tps>")
            .replace("%mspt%", "<mspt>")
            .replace("%util%", "<util>")
            .replace("%players%", "<players>");
    }

    @Override
    void updatePlayerDisplaysAndTick(final Pair<Component, Float> componentAndProgress) {
        for (final ServerPlayer localPlayer : getWorldData().getLocalPlayers()) {
            localPlayer.canvas$tpsBarDisplay.setDisplay(componentAndProgress.getFirst());
            localPlayer.canvas$tpsBarDisplay.setProgress(componentAndProgress.getSecond());
            localPlayer.canvas$tpsBarDisplay.tick();
        }
    }

    @Override
    String[] getKeys() {
        return KEYS;
    }

    @NonNull Pair<Component, Float> buildComponent() {
        final TickData.TickReportData tickReportData = getWorldData().regionData.getRegionSchedulingHandle().getTickReport5s(System.nanoTime());
        final TickData.SegmentedAverage tpsAverage = tickReportData.tpsData();
        final TickData.SegmentedAverage msptAverage = tickReportData.timePerTickData();

        final double utilPercent = tickReportData.utilisation() * 100;
        final double tps = tpsAverage.segmentAll().average();
        final double mspt = msptAverage.segmentAll().average() / 1.0E6;
        final int players = getWorldData().getPlayerCount();
        final boolean sprinting = getWorldData().regionData.getRegionSchedulingHandle().getTickManager().isSprinting();
        final String raw = getWorldData().world.canvasConfig().regionBars.tpsBarFormat;
        final String effectiveRaw = (raw == null || raw.isBlank()) ? "" : raw;

        FormatEntry entry = cachedFormat.get();
        if (entry == null || !effectiveRaw.equals(entry.raw())) {
            entry = FormatEntry.compile(effectiveRaw, this);
            cachedFormat.set(entry);
        }

        final TextColor tpsColor = sprinting ? SPRINTING_COLOR : CommandUtil.getColourForTPS(tps);
        final TextColor msptColor = sprinting ? SPRINTING_COLOR : CommandUtil.getColourForMSPT(mspt);
        final TextColor utilColor = sprinting ? SPRINTING_COLOR : CommandUtil.getUtilisationColourRegion(utilPercent / 100);
        final TextColor playerColor = sprinting ? SPRINTING_COLOR : CommandUtil.getColourForTPS(TickRegionScheduler.getTickRate());

        final Component tpsComponent = number(tps, TPS_FORMAT, tpsColor);
        final Component msptComponent = number(mspt, MSPT_FORMAT, msptColor);
        final Component utilComponent = number(utilPercent, UTIL_FORMAT, utilColor).append(Component.text("%").color(utilColor));
        final Component playersComponent = number(players, INT_FORMAT, playerColor);

        final TextComponent.Builder builder = Component.text();
        for (final FormatEntry.Segment segment : entry.segments()) {
            if (segment instanceof FormatEntry.Segment.Static(Component component)) {
                builder.append(component);
            }
            else if (segment instanceof FormatEntry.Segment.Dynamic(String key)) {
                builder.append(switch (key) {
                    case "tps" -> tpsComponent;
                    case "mspt" -> msptComponent;
                    case "util" -> utilComponent;
                    case "players" -> playersComponent;
                    default -> Component.empty();
                });
            }
        }
        return new Pair<>(builder.build(), (float) utilPercent / 100);
    }

}
