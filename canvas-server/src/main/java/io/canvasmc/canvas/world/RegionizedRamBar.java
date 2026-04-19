package io.canvasmc.canvas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.canvasmc.canvas.Config;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.threadedregions.commands.CommandUtil;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public class RegionizedRamBar {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final ThreadLocal<DecimalFormat> VALUE_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));
    private static final ThreadLocal<DecimalFormat> PERCENT_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));
    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;
    public static final String DEFAULT_FORMAT =
        "<gradient:green:dark_green><b>RAM:</b></gradient> <used>/<xmx> <dark_gray>(</dark_gray><percent><dark_gray>%)</dark_gray>";
    private static final AtomicReference<FormatEntry> cachedFormat = new AtomicReference<>(null);
    private final boolean canTick;
    private long nextTick = System.nanoTime();

    public RegionizedRamBar() {
        this.canTick = Config.INSTANCE.enableRamBar;
    }

    public void tick(final Iterable<ServerPlayer> players) {
        if (!this.canTick || this.nextTick > System.nanoTime()) {
            return;
        }

        final long startTime = System.nanoTime();
        final Runtime runtime = Runtime.getRuntime();
        final long maxBytes = runtime.maxMemory();
        final long usedBytes = runtime.totalMemory() - runtime.freeMemory();

        final double usedMiB = usedBytes / BYTES_PER_MIB;
        final double maxMiB = maxBytes / BYTES_PER_MIB;
        final double percent = maxMiB <= 0.0 ? 0.0 : (usedMiB / maxMiB) * 100.0;
        final float progress = (float)Math.max(0.0, Math.min(1.0, percent / 100.0));
        final Component textComponent = this.buildComponent(usedMiB, maxMiB, percent);

        for (final ServerPlayer localPlayer : players) {
            localPlayer.canvas$ramBarDisplay.setDisplay(textComponent);
            localPlayer.canvas$ramBarDisplay.setProgress(progress);
            localPlayer.canvas$ramBarDisplay.tick();
        }

        this.nextTick = startTime + 1_000_000_000L;
    }

    private @NonNull Component buildComponent(final double usedMiB, final double maxMiB, final double percent) {
        final String raw = Config.INSTANCE.ramBarFormat;
        final String effectiveRaw = (raw == null || raw.isBlank()) ? "" : raw;
        FormatEntry entry = cachedFormat.get();
        if (entry == null || !effectiveRaw.equals(entry.raw())) {
            entry = FormatEntry.compile(effectiveRaw);
            cachedFormat.set(entry);
        }

        final TextColor ramColor = CommandUtil.getUtilisationColourRegion(percent / 100.0);
        final Component usedComponent = this.valueWithUnit(usedMiB, ramColor);
        final Component xmxComponent = this.valueWithUnit(maxMiB, ramColor);
        final Component percentComponent = Component.text(PERCENT_FORMAT.get().format(percent), ramColor);

        final TextComponent.Builder builder = Component.text();
        for (final FormatEntry.Segment segment : entry.segments()) {
            if (segment instanceof FormatEntry.Segment.Static(Component component)) {
                builder.append(component);
            } else if (segment instanceof FormatEntry.Segment.Dynamic(String key)) {
                builder.append(switch (key) {
                    case "used" -> usedComponent;
                    case "xmx" -> xmxComponent;
                    case "percent" -> percentComponent;
                    default -> Component.empty();
                });
            }
        }

        return builder.build();
    }

    @Contract("_, _ -> new")
    private @NonNull Component valueWithUnit(final double value, final TextColor color) {
        return Component.text(VALUE_FORMAT.get().format(value) + " MiB", color);
    }

    public enum Placement {
        ACTION_BAR,
        BOSS_BAR;

        public static final Codec<Placement> CODEC = Codec.STRING.comapFlatMap((string) -> DataResult.success(Placement.valueOf(string)), Enum::name);
    }

    public interface DisplayManager {
        @Contract(value = "_ -> new", pure = true)
        static @NonNull DisplayManager createNew(final ServerPlayer entityPlayer) {
            return new DisplayManager() {
                private Component display = Component.text("Waiting for region update...");
                private final BossBar ramBar = BossBar.bossBar(this.display, 0.0F, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);

                private volatile boolean enabled = false;
                private Placement placement = Placement.BOSS_BAR;
                private float progress = 0.0F;
                private boolean dirty = true;

                @Override
                public void tick() {
                    if (this.dirty) {
                        final CraftPlayer bukkitEntity = entityPlayer.getBukkitEntity();
                        if (this.placement == Placement.BOSS_BAR) {
                            if (this.enabled) {
                                this.ramBar.addViewer(bukkitEntity);
                            } else {
                                this.ramBar.removeViewer(bukkitEntity);
                            }
                        } else {
                            this.ramBar.removeViewer(bukkitEntity);
                        }
                        this.dirty = false;
                    }

                    if (!this.enabled) {
                        return;
                    }

                    switch (this.placement) {
                        case BOSS_BAR -> {
                            this.ramBar.name(this.display);
                            this.ramBar.progress(this.progress);
                        }
                        case ACTION_BAR -> entityPlayer.connection.send(
                            new ClientboundSetActionBarTextPacket(PaperAdventure.asVanillaNullToEmpty(this.display))
                        );
                    }
                }

                @Override
                public void setDisplay(final Component component) {
                    this.display = component;
                }

                @Override
                public void setProgress(final float progress) {
                    this.progress = progress;
                }

                @Override
                public void updateFromEntry(final Entry entry) {
                    this.enabled = entry.enabled();
                    this.placement = entry.placement();
                    this.dirty = true;
                }

                @Override
                public Entry serializeDisplay() {
                    return new Entry(this.enabled, this.placement);
                }
            };
        }

        void tick();

        void setDisplay(Component component);

        void setProgress(float progress);

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
                .replace("%used%", "<used>")
                .replace("%xmx%", "<xmx>")
                .replace("%percent%", "<percent>");
        }

        private static @NonNull List<Segment> buildSegments(final String normalized) {
            final List<Segment> result = new ArrayList<>();
            final String[] keys = {"used", "xmx", "percent"};
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
                remaining = remaining.substring(earliestIdx + earliestKey.length() + 2);
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
            return Config.INSTANCE.enableRamBar && this.enabled;
        }
    }
}

