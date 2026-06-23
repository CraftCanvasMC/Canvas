package io.canvasmc.canvas.world;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public abstract class RegionResourceBar {
    protected static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Function<RegionizedWorldData, Boolean> enabled;
    private long nextTick = System.nanoTime();

    public RegionResourceBar(final @NonNull Function<RegionizedWorldData, Boolean> enabled) {
        this.enabled = enabled;
    }

    public RegionizedWorldData getWorldData() {
        return TickRegionScheduler.getCurrentRegionizedWorldData();
    }

    public void tick() {
        // use system nano time, more reliable with runtime tick rate changes
        if (enabled.apply(getWorldData()) && this.nextTick <= System.nanoTime()) {
            // update maps and players
            long startTime = System.nanoTime();
            updatePlayerDisplaysAndTick(buildComponent());
            this.nextTick = startTime + 1_000_000_000;
        }
    }

    abstract void updatePlayerDisplaysAndTick(final Pair<Component, Float> componentAndProgress);

    abstract Pair<Component, Float> buildComponent();

    abstract String normalize(String input);

    abstract String getDefaultFormat();

    abstract String[] getKeys();

    @Contract("_, _, _ -> new")
    @NonNull Component number(final double value, final @NonNull ThreadLocal<DecimalFormat> fmt, final TextColor color) {
        return Component.text(fmt.get().format(value), color);
    }

    public enum Placement {
        ACTION_BAR, BOSS_BAR;
        public static final Codec<Placement> CODEC = Codec.STRING.comapFlatMap((string) -> DataResult.success(Placement.valueOf(string)), Enum::name);
    }

    public interface DisplayManager {
        @Contract(value = "_, _ -> new", pure = true)
        static @NonNull DisplayManager createNew(ServerPlayer entityPlayer, BossBar.Color color) {
            return new DisplayManager() {
                private Component display = Component.text("Waiting for region update...");
                public final BossBar resourceBar =
                    BossBar.bossBar(
                        this.display,
                        0.0F,
                        color,
                        BossBar.Overlay.PROGRESS
                    );

                private volatile boolean enabled = false;
                private Placement placement = Placement.BOSS_BAR;
                private float progress = 0.0F;
                private boolean dirty = true; // force initial sync

                @Override
                public void tick() {
                    // handle state changes if marked dirty
                    if (dirty) {
                        final CraftPlayer bukkitEntity = entityPlayer.getBukkitEntity();

                        if (placement == Placement.BOSS_BAR) {
                            if (enabled) {
                                resourceBar.addViewer(bukkitEntity);
                            }
                            else {
                                resourceBar.removeViewer(bukkitEntity);
                            }
                        }
                        else {
                            resourceBar.removeViewer(bukkitEntity);
                        }

                        dirty = false;
                    }

                    if (!enabled) {
                        return;
                    }

                    switch (placement) {
                        case BOSS_BAR -> {
                            resourceBar.name(display);
                            resourceBar.progress(progress);
                        }
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
                public void setProgress(final float progress) {
                    this.progress = progress;
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
                public void updateFromEntry(final RegionResourceBar.Entry entry) {
                    this.enabled = entry.enabled();
                    this.placement = entry.placement();
                    this.dirty = true;
                }

                @Override
                public RegionResourceBar.Entry serializeDisplay() {
                    return new RegionResourceBar.Entry(
                        this.enabled, this.placement
                    );
                }
            };
        }

        void tick();

        void setDisplay(Component component);

        void setProgress(float progress);

        void enable();

        void disable();

        void updateFromEntry(RegionResourceBar.Entry entry);

        RegionResourceBar.Entry serializeDisplay();
    }

    record FormatEntry(String raw, List<Segment> segments) {
        private static @NonNull List<Segment> buildSegments(final String normalized, final String[] keys) {
            final List<Segment> result = new ArrayList<>();
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

        static @NonNull FormatEntry compile(final @NonNull String effectiveRaw, final @NonNull RegionResourceBar resourceBar) {
            final String normalized = effectiveRaw.isEmpty() ? resourceBar.getDefaultFormat() : resourceBar.normalize(effectiveRaw);
            return new FormatEntry(effectiveRaw, buildSegments(normalized, resourceBar.getKeys()));
        }

        sealed interface Segment permits Segment.Static, Segment.Dynamic {
            record Static(Component component) implements Segment {}

            record Dynamic(String key) implements Segment {}
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
    }

}
