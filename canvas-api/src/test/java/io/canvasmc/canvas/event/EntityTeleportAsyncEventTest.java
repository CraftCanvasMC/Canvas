package io.canvasmc.canvas.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

public class EntityTeleportAsyncEventTest {

    @Test
    public void handlesFallbackDestinationMutationAndCancellation() {
        final Entity entity = mock(Entity.class);
        final Location from = new Location(mock(World.class), 1.0, 2.0, 3.0);
        final EntityTeleportAsyncEvent event = new EntityTeleportAsyncEvent(
            entity,
            from,
            null,
            PlayerTeleportEvent.TeleportCause.COMMAND,
            EntityTeleportAsyncEvent.TeleportType.CROSS_WORLD
        );

        assertSame(entity, event.getEntity());
        assertSame(from, event.getFrom());
        assertSame(from, event.getTo());
        assertSame(PlayerTeleportEvent.TeleportCause.COMMAND, event.getCause());
        assertSame(EntityTeleportAsyncEvent.TeleportType.CROSS_WORLD, event.getType());
        assertSame(EntityTeleportAsyncEvent.getHandlerList(), event.getHandlers());
        assertFalse(event.isCancelled());

        final Location destination = new Location(mock(World.class), 4.0, 5.0, 6.0);
        event.setTo(destination);
        event.setCancelled(true);

        assertEquals(destination, event.getTo());
        assertNotSame(destination, event.getTo());
        assertTrue(event.isCancelled());
    }
}
