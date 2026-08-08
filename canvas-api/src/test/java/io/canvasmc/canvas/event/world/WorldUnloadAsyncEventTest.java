package io.canvasmc.canvas.event.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

public class WorldUnloadAsyncEventTest {

    @Test
    public void exposesWorldAndTracksCancellation() {
        final World world = mock(World.class);
        final WorldUnloadAsyncEvent event = new WorldUnloadAsyncEvent(world);

        assertSame(world, event.getWorld());
        assertSame(WorldUnloadAsyncEvent.getHandlerList(), event.getHandlers());
        assertFalse(event.isCancelled());

        event.setCancelled(true);

        assertTrue(event.isCancelled());
    }
}
