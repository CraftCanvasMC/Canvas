package io.canvasmc.canvas.region;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RegionizedDataCallbackTest {

    @ParameterizedTest
    @CsvSource({
        "0, 0, 4",
        "15, 15, 4",
        "16, 16, 4",
        "-1, -1, 4",
        "-16, -16, 4",
        "-17, -17, 4",
        "2147483647, -2147483648, 5"
    })
    public void packsSignedRegionSectionCoordinates(final int chunkX, final int chunkZ, final int shift) {
        final long coordinates = callback().getRegionSectionCoordinates(chunkX, chunkZ, shift);

        assertEquals(chunkX >> shift, (int) coordinates);
        assertEquals(chunkZ >> shift, (int) (coordinates >>> 32));
    }

    private static RegionTickData.IRegionizedData.IRegionizedCallback<Object> callback() {
        return new RegionTickData.IRegionizedData.IRegionizedCallback<>() {
            @Override
            public void merge(final Object from, final Object into, final long fromTickOffset) {
            }

            @Override
            public void split(
                final Object from,
                final int chunkToRegionShift,
                final Long2ReferenceOpenHashMap<Object> regionToData,
                final ReferenceOpenHashSet<Object> dataSet
            ) {
            }
        };
    }
}
