package io.canvasmc.canvas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.canvasmc.canvas.util.Gradient;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import io.papermc.paper.threadedregions.TickData;
import io.papermc.paper.threadedregions.commands.CommandUtil;
import java.text.DecimalFormat;
import java.util.function.Consumer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import static net.kyori.adventure.text.Component.text;

public class RegionizedTpsBar {
    private static final ThreadLocal<DecimalFormat> TWO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.00");
    });
    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.0");
    });
    private final RegionizedWorldData worldData;
    private final boolean canTick;
    private long nextTick = System.nanoTime();

    public RegionizedTpsBar(RegionizedWorldData worldData) {
        this.worldData = worldData;
        this.canTick = Config.INSTANCE.enableTpsBar;
    }

    public static @NotNull Component gradient(final String textContent, final @Nullable Consumer<Style.Builder> style, final TextColor... colors) {
        final Gradient gradient = new Gradient(colors);
        final TextComponent.Builder builder = text();
        if (style != null) {
            builder.style(style);
        }
        final char[] content = textContent.toCharArray();
        gradient.length(content.length);
        for (final char c : content) {
            builder.append(text(c, gradient.nextColor()));
        }
        return builder.build();
    }

    public RegionizedWorldData getWorldData() {
        return worldData;
    }

    public void tick() {
        if (this.canTick && this.nextTick <= System.nanoTime()) { // use system nano time, more reliable with runtime tick rate changes
            // update tps maps
            long startTime = System.nanoTime();
            TickData.TickReportData tickReportData = this.worldData.regionData.getRegionSchedulingHandle().getTickReport5s(System.nanoTime());
            TickData.SegmentedAverage tpsAverage = tickReportData.tpsData();
            TickData.SegmentedAverage msptAverage = tickReportData.timePerTickData();
            final double util = tickReportData.utilisation() * 100;
            final double tps = tpsAverage.segmentAll().average();
            final double mspt = msptAverage.segmentAll().average() / 1.0E6;
            String tpsTruncated = TWO_DECIMAL_PLACES.get().format(tps);
            String msptTruncated = TWO_DECIMAL_PLACES.get().format(mspt);
            String utilizationTruncated = ONE_DECIMAL_PLACES.get().format(util) + "%";
            // build component
            final Component textComponent =
                gradient("TPS", (builder) -> builder.decorate(TextDecoration.BOLD), NamedTextColor.BLUE, NamedTextColor.AQUA)
                    .append(Component.text(": ", NamedTextColor.WHITE))
                    .append(Component.text(tpsTruncated, this.worldData.regionData.getRegionSchedulingHandle().ticksToSprint > 0 ? CommandUtil.SPRINTING_COLOR : CommandUtil.getColourForTPS(tps)))
                    .append(Component.text("  -  ", NamedTextColor.WHITE))
                    .append(gradient("MSPT", (builder) -> builder.decorate(TextDecoration.BOLD), NamedTextColor.BLUE, NamedTextColor.AQUA))
                    .append(Component.text(": ", NamedTextColor.WHITE))
                    .append(Component.text(msptTruncated, this.worldData.regionData.getRegionSchedulingHandle().ticksToSprint > 0 ? CommandUtil.SPRINTING_COLOR : CommandUtil.getColourForMSPT(mspt)))
                    .append(Component.text("  -  ", NamedTextColor.WHITE))
                    .append(gradient("Util", (builder) -> builder.decorate(TextDecoration.BOLD), NamedTextColor.BLUE, NamedTextColor.AQUA))
                    .append(Component.text(": ", NamedTextColor.WHITE))
                    .append(Component.text(utilizationTruncated, this.worldData.regionData.getRegionSchedulingHandle().ticksToSprint > 0 ? CommandUtil.SPRINTING_COLOR : CommandUtil.getUtilisationColourRegion(util / 100)));
            // update players
            for (final ServerPlayer localPlayer : this.worldData.getLocalPlayers()) {
                localPlayer.canvas$tpsBarDisplay.setDisplay(textComponent);
                localPlayer.canvas$tpsBarDisplay.tick();
            }
            this.nextTick = startTime + 1_000_000_000;
        }
    }

    public enum Placement {
        ACTION_BAR, BOSS_BAR;
        public static final Codec<Placement> CODEC = Codec.STRING.comapFlatMap((string) -> DataResult.success(Placement.valueOf(string)), Enum::name);
    }

    public interface DisplayManager {
        @Contract(value = "_ -> new", pure = true)
        static @NotNull DisplayManager createNew(ServerPlayer entityPlayer) {
            return new DisplayManager() {
                private Component display = Component.text("Waiting for region update...");
                public final BossBar tpsBar =
                    BossBar.bossBar(
                        this.display,
                        0.0F,
                        BossBar.Color.BLUE,
                        BossBar.Overlay.PROGRESS
                    );

                private volatile boolean enabled = false;
                private Placement placement = Placement.BOSS_BAR;
                private boolean dirty = true; // force initial sync

                @Override
                public void tick() {
                    if (!enabled) {
                        return;
                    }

                    switch (placement) {
                        case BOSS_BAR -> tpsBar.name(display);
                        case ACTION_BAR ->
                            entityPlayer.connection.send(
                                new ClientboundSetActionBarTextPacket(
                                    PaperAdventure.asVanillaNullToEmpty(display)
                                )
                            );
                    }

                    // handle state changes if marked dirty
                    if (dirty) {
                        final CraftPlayer bukkitEntity = entityPlayer.getBukkitEntity();

                        if (placement == Placement.BOSS_BAR) {
                            if (enabled) {
                                tpsBar.addViewer(bukkitEntity);
                            } else {
                                tpsBar.removeViewer(bukkitEntity);
                            }
                        } else {
                            tpsBar.removeViewer(bukkitEntity);
                        }

                        dirty = false;
                    }
                }

                @Override
                public void setDisplay(final Component component) {
                    this.display = component;
                }

                @Override
                public void enable() {
                    this.enabled = true;
                    this.dirty = true;
                }

                @Override
                public void disable() {
                    this.enabled = false;
                    this.dirty = true;
                }

                @Override
                public void updateFromEntry(final Entry entry) {
                    this.enabled = entry.enabled();
                    this.placement = entry.placement();
                    this.dirty = true;
                }

                @Override
                public Entry serializeDisplay() {
                    return new Entry(
                        this.enabled, this.placement
                    );
                }
            };
        }

        void tick();

        void setDisplay(Component component);

        void enable();

        void disable();

        void updateFromEntry(Entry entry);

        Entry serializeDisplay();
    }

    public record Entry(boolean enabled, Placement placement) {
        public static final Entry FALLBACK = new Entry(false, Placement.BOSS_BAR);
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("enabled", false).forGetter(Entry::enabled),
                    Placement.CODEC.optionalFieldOf("placement", Placement.BOSS_BAR).forGetter(Entry::placement)
                )
                .apply(instance, Entry::new)
        );

        @Override
        public boolean enabled() {
            return Config.INSTANCE.enableTpsBar && enabled;
        }
    }
}
