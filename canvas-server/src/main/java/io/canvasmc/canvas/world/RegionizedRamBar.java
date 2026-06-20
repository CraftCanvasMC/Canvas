package io.canvasmc.canvas.world;

import com.mojang.datafixers.util.Pair;
import io.canvasmc.canvas.WorldConfig;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import io.papermc.paper.threadedregions.commands.CommandUtil;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public class RegionizedRamBar extends RegionResourceBar {
    private static final ThreadLocal<DecimalFormat> VALUE_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));
    private static final ThreadLocal<DecimalFormat> PERCENT_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));

    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;
    private static final String[] KEYS = {"used", "xmx", "percent"};

    // we have this as an instance field because formats can be per-world
    private final AtomicReference<FormatEntry> cachedFormat = new AtomicReference<>(null);

    public RegionizedRamBar() {
        super((worldData) -> worldData.world.canvasConfig().regionBars.enableRamBar);
    }

    @Override
    void updatePlayerDisplaysAndTick(final Pair<Component, Float> componentAndProgress) {
        for (final ServerPlayer localPlayer : getWorldData().getLocalPlayers()) {
            localPlayer.canvas$ramBarDisplay.setDisplay(componentAndProgress.getFirst());
            localPlayer.canvas$ramBarDisplay.setProgress(componentAndProgress.getSecond());
            localPlayer.canvas$ramBarDisplay.tick();
        }
    }

    @Override
    Pair<Component, Float> buildComponent() {
        final Runtime runtime = Runtime.getRuntime();
        final long maxBytes = runtime.maxMemory();
        final long usedBytes = runtime.totalMemory() - runtime.freeMemory();

        final double usedMiB = usedBytes / BYTES_PER_MIB;
        final double maxMiB = maxBytes / BYTES_PER_MIB;
        final double percent = maxMiB <= 0.0 ? 0.0 : (usedMiB / maxMiB) * 100.0;

        final String raw = getWorldData().world.canvasConfig().regionBars.ramBarFormat;
        final String effectiveRaw = (raw == null || raw.isBlank()) ? "" : raw;
        FormatEntry entry = cachedFormat.get();
        if (entry == null || !effectiveRaw.equals(entry.raw())) {
            entry = FormatEntry.compile(effectiveRaw, this);
            cachedFormat.set(entry);
        }

        final TextColor ramColor = CommandUtil.getUtilisationColourRegion(percent / 100.0);
        final Component usedComponent = valueWithUnit(usedMiB, ramColor);
        final Component xmxComponent = valueWithUnit(maxMiB, ramColor);
        final Component percentComponent = Component.text(PERCENT_FORMAT.get().format(percent), ramColor);

        final TextComponent.Builder builder = Component.text();
        for (final FormatEntry.Segment segment : entry.segments()) {
            if (segment instanceof FormatEntry.Segment.Static(Component component)) {
                builder.append(component);
            }
            else if (segment instanceof FormatEntry.Segment.Dynamic(String key)) {
                builder.append(switch (key) {
                    case "used" -> usedComponent;
                    case "xmx" -> xmxComponent;
                    case "percent" -> percentComponent;
                    default -> Component.empty();
                });
            }
        }

        return new Pair<>(builder.build(), (float) Math.clamp(percent / 100.0, 0.0, 1.0));
    }

    @Contract("_, _ -> new")
    private @NonNull Component valueWithUnit(final double value, final TextColor color) {
        return Component.text(VALUE_FORMAT.get().format(value) + " MiB", color);
    }

    @Override
    String normalize(final @NonNull String input) {
        return input
            .replace("%used%", "<used>")
            .replace("%xmx%", "<xmx>")
            .replace("%percent%", "<percent>");
    }

    @Override
    String getDefaultFormat() {
        return WorldConfig.DEFAULT_RAMBAR_FORMAT;
    }

    @Override
    String[] getKeys() {
        return KEYS;
    }
}
