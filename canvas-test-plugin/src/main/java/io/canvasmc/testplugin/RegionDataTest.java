package io.canvasmc.testplugin;

import io.canvasmc.canvas.region.RegionTickData;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

// note: this is complete bullshit, not really intended
//       to be anything special or functional, just needed
//       to show how exactly this new API worked and see
//       if it is actually working correctly
public class RegionDataTest implements Listener {
    static final RegionTickData.IRegionizedData<TestRegionData> TEST_IREGIONDATA = Bukkit.getServer().createRegionizedData(
        TestRegionData::new, new RegionTickData.IRegionizedData.IRegionizedCallback<>() {
            @Override
            public void merge(final TestRegionData from, final TestRegionData into, final long fromTickOffset) {
                for (Map.Entry<Long, AtomicInteger> packed2Val : from.chunkMappingsTest.entrySet()) {
                    into.chunkMappingsTest.compute(packed2Val.getKey(), (k, v) -> {
                        if (v == null) return new AtomicInteger(packed2Val.getValue().get());
                        v.addAndGet(packed2Val.getValue().get());
                        return v;
                    });
                }
            }

            @Override
            public void split(final TestRegionData from, final int chunkToRegionShift, final Long2ReferenceOpenHashMap<TestRegionData> regionToData, final ReferenceOpenHashSet<TestRegionData> dataSet) {
                for (final Map.Entry<Long, AtomicInteger> packed2Val : from.chunkMappingsTest.entrySet()) {
                    long packed = packed2Val.getKey();
                    int chunkX = (int) (packed & 0xFFFFFFFFL);
                    int chunkZ = (int) (packed >>> 32);
                    int regionSectionX = chunkX >> chunkToRegionShift;
                    int regionSectionZ = chunkZ >> chunkToRegionShift;
                    long regionSectionKey = ((long) regionSectionZ << 32) | (regionSectionX & 0xFFFFFFFFL);
                    AtomicInteger val = packed2Val.getValue();
                    TestRegionData target = regionToData.get(regionSectionKey);
                    if (target != null) {
                        target.chunkMappingsTest.compute(packed, (k, v) -> {
                            if (v == null) return new AtomicInteger(val.get());
                            v.addAndGet(val.get());
                            return v;
                        });
                    }
                }
            }
        }
    );

    public static void init() {
        Bukkit.getServer().getCommandMap().register(
            "regiontest", new BukkitCommand("regiontest") {
                @Override
                public boolean execute(@NotNull final CommandSender sender, @NotNull final String commandLabel, final @NotNull String @NotNull [] args) {
                    if (sender instanceof Player player) {
                        World world = player.getWorld();
                        final int x = player.getChunk().getX();
                        final int z = player.getChunk().getZ();
                        long packed = ((long) z & 0xFFFFFFFFL) << 32 | ((long) x & 0xFFFFFFFFL);
                        final Map<Long, AtomicInteger> chunkMappings = Objects.requireNonNull(world.getRegionizer().getRegionAtUnsynchronised(x, z), "Region shouldn't be null!")
                            .getTickData()
                            .getOrCreateFromIRegionizedData(TEST_IREGIONDATA)
                            .chunkMappingsTest;
                        int i = chunkMappings.computeIfAbsent(packed, aLong -> new AtomicInteger(0)).incrementAndGet();
                        sender.sendMessage("Incremented data value at chunk to: " + i);
                        sender.sendMessage("The total value of the full region is: " + chunkMappings.values().stream().mapToInt(AtomicInteger::get).sum());
                    }
                    return false;
                }
            }
        );
    }

    public static class TestRegionData {
        private final RegionTickData tickData;
        private final World world;
        private final Map<Long, AtomicInteger> chunkMappingsTest = new ConcurrentHashMap<>();

        public TestRegionData(@NotNull RegionTickData tickData, @NotNull World world) {
            this.tickData = tickData;
            this.world = world;
        }

        public RegionTickData getTickData() {
            return tickData;
        }

        public World getWorld() {
            return world;
        }
    }
}
