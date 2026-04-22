package io.canvasmc.canvas.world;

import ca.spottedleaf.moonrise.common.time.TickData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.canvasmc.canvas.Config;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.commands.CommandUtil;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static io.papermc.paper.threadedregions.commands.CommandUtil.SPRINTING_COLOR;
import static net.kyori.adventure.text.Component.text;

public class RegionizedTpsBar {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final ThreadLocal<DecimalFormat> TPS_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));
    private static final ThreadLocal<DecimalFormat> MSPT_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));
    private static final ThreadLocal<DecimalFormat> UTIL_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));
    private static final ThreadLocal<DecimalFormat> INT_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0"));
    public static final String DEFAULT_FORMAT =
        "<gradient:blue:aqua><b>TPS:</b></gradient> <tps>  <dark_gray>-</dark_gray>  " +
        "<gradient:blue:aqua><b>MSPT:</b></gradient> <mspt>  <dark_gray>-</dark_gray>  " +
        "<gradient:blue:aqua><b>Util:</b></gradient> <util>  <dark_gray>-</dark_gray>  " +
        "<gradient:blue:aqua><b>Players:</b></gradient> <players>";
    private static final AtomicReference<FormatEntry> cachedFormat = new AtomicReference<>(null);
    private final RegionizedWorldData worldData;
    private final boolean canTick;
    private long nextTick = System.nanoTime();

    public RegionizedTpsBar(RegionizedWorldData worldData) {
        this.worldData = worldData;
        this.canTick = Config.INSTANCE.enableTpsBar;
    }

    public static @NonNull Component gradient(final String textContent, final @Nullable Consumer<Style.Builder> style, final TextColor... colors) {
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
            final int players = this.worldData.getPlayerCount();
            final boolean sprinting = this.worldData.regionData.getRegionSchedulingHandle().getTickManager().isSprinting();
            final Component textComponent = buildComponent(tps, mspt, util, players, sprinting);
            // update players
            for (final ServerPlayer localPlayer : this.worldData.getLocalPlayers()) {
                localPlayer.canvas$tpsBarDisplay.setDisplay(textComponent);
                localPlayer.canvas$tpsBarDisplay.tick();
            }
            this.nextTick = startTime + 1_000_000_000;
        }
    }

    private @NonNull Component buildComponent(final double tps, final double mspt, final double utilPercent, final int players, final boolean sprinting) {
        final String raw = Config.INSTANCE.tpsBarFormat;
        final String effectiveRaw = (raw == null || raw.isBlank()) ? "" : raw;
        FormatEntry entry = cachedFormat.get();
        if (entry == null || !effectiveRaw.equals(entry.raw())) {
            entry = FormatEntry.compile(effectiveRaw);
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
            } else if (segment instanceof FormatEntry.Segment.Dynamic(String key)) {
                builder.append(switch (key) {
                    case "tps" -> tpsComponent;
                    case "mspt" -> msptComponent;
                    case "util" -> utilComponent;
                    case "players" -> playersComponent;
                    default -> Component.empty();
                });
            }
        }
        return builder.build();
    }

    @Contract("_, _, _ -> new")
    private @NonNull Component number(final double value, final @NonNull ThreadLocal<DecimalFormat> fmt, final TextColor color) {
        return Component.text(fmt.get().format(value), color);
    }

    public enum Placement {
        ACTION_BAR, BOSS_BAR;
        public static final Codec<Placement> CODEC = Codec.STRING.comapFlatMap((string) -> DataResult.success(Placement.valueOf(string)), Enum::name);
    }

    public interface DisplayManager {
        @Contract(value = "_ -> new", pure = true)
        static @NonNull DisplayManager createNew(ServerPlayer entityPlayer) {
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
                    // handle state changes if marked dirty
                    if (dirty) {
                        final CraftPlayer bukkitEntity = entityPlayer.getBukkitEntity();

                        if (placement == Placement.BOSS_BAR) {
                            if (enabled) {
                                tpsBar.addViewer(bukkitEntity);
                            }
                            else {
                                tpsBar.removeViewer(bukkitEntity);
                            }
                        }
                        else {
                            tpsBar.removeViewer(bukkitEntity);
                        }

                        dirty = false;
                    }

                    if (!enabled) {
                        return;
                    }

                    switch (placement) {
                        case BOSS_BAR -> tpsBar.name(display);
                        case ACTION_BAR -> entityPlayer.connection.send(
                            new ClientboundSetActionBarTextPacket(
                                PaperAdventure.asVanillaNullToEmpty(display)
                            )
                        );
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

    record FormatEntry(String raw, List<Segment> segments) {
        sealed interface Segment permits Segment.Static, Segment.Dynamic {
            record Static(Component component) implements Segment {}
            record Dynamic(String key) implements Segment {}
        }

        static @NonNull FormatEntry compile(final @NonNull String effectiveRaw) {
            final String normalized = effectiveRaw.isEmpty() ? DEFAULT_FORMAT : normalize(effectiveRaw);
            return new FormatEntry(effectiveRaw, buildSegments(normalized));
        }

        private static @NonNull String normalize(final @NonNull String input) {
            return input
                .replace("%tps%", "<tps>")
                .replace("%mspt%", "<mspt>")
                .replace("%util%", "<util>")
                .replace("%players%", "<players>");
        }

        private static @NonNull List<Segment> buildSegments(final String normalized) {
            final List<Segment> result = new ArrayList<>();
            final String[] keys = {"tps", "mspt", "util", "players"};
            String remaining = normalized;

            while (!remaining.isEmpty()) {
                int earliestIdx = Integer.MAX_VALUE;
                String earliestKey = null;
                for (final String key : keys) {
                    final int idx = remaining.indexOf("<" + key + ">");
                    if (idx >= 0 && idx < earliestIdx) {
                        earliestIdx = idx;
                        earliestKey = key;
                    }
                }

                if (earliestKey == null) {
                    result.add(parseStatic(remaining));
                    break;
                }

                if (earliestIdx > 0) {
                    result.add(parseStatic(remaining.substring(0, earliestIdx)));
                }
                result.add(new Segment.Dynamic(earliestKey));
                remaining = remaining.substring(earliestIdx + earliestKey.length() + 2); // +2 for '<' and '>'
            }

            return result;
        }

        @Contract("_ -> new")
        private static Segment.@NonNull Static parseStatic(final String text) {
            try {
                return new Segment.Static(MINI_MESSAGE.deserialize(text));
            } catch (final Exception e) {
                return new Segment.Static(Component.text(text));
            }
        }
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

    public static final class Gradient {
        private final boolean negativePhase;
        private final TextColor[] colors;
        private int index = 0;
        private int colorIndex = 0;
        private float factorStep = 0;
        private float phase;

        public Gradient(final @NonNull TextColor... colors) {
            this(0, colors);
        }

        public Gradient(final float phase, final @NonNull TextColor @NonNull ... colors) {
            if (colors.length < 2) {
                throw new IllegalArgumentException("Gradients must have at least two colors! colors=" + Arrays.toString(colors));
            }
            if (phase > 1.0 || phase < -1.0) {
                throw new IllegalArgumentException(String.format("Phase must be in range [-1, 1]. '%s' is not valid.", phase));
            }
            this.colors = colors;
            if (phase < 0) {
                this.negativePhase = true;
                this.phase = 1 + phase;
                Collections.reverse(Arrays.asList(this.colors));
            }
            else {
                this.negativePhase = false;
                this.phase = phase;
            }
        }

        public void length(final int size) {
            this.colorIndex = 0;
            this.index = 0;
            final int sectorLength = size / (this.colors.length - 1);
            this.factorStep = 1.0f / sectorLength;
            this.phase = this.phase * sectorLength;
        }

        public @NonNull TextColor nextColor() {
            if (this.factorStep * this.index > 1) {
                this.colorIndex++;
                this.index = 0;
            }

            float factor = this.factorStep * (this.index++ + this.phase);
            // loop around if needed
            if (factor > 1) {
                factor = 1 - (factor - 1);
            }
            if (this.negativePhase && this.colors.length % 2 != 0) {
                // flip the gradient segment for to allow for looping phase -1 through 1
                return this.interpolate(this.colors[this.colorIndex + 1], this.colors[this.colorIndex], factor);
            }
            else {
                return this.interpolate(this.colors[this.colorIndex], this.colors[this.colorIndex + 1], factor);
            }
        }

        private @NonNull TextColor interpolate(final @NonNull TextColor color1, final @NonNull TextColor color2, final float factor) {
            return TextColor.color(
                Math.round(color1.red() + factor * (color2.red() - color1.red())),
                Math.round(color1.green() + factor * (color2.green() - color1.green())),
                Math.round(color1.blue() + factor * (color2.blue() - color1.blue()))
            );
        }
    }
}
