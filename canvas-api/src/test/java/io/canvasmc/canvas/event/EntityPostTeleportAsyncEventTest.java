package io.canvasmc.canvas.event;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

public class EntityPostTeleportAsyncEventTest {

    @Test
    public void preservesCompletedTeleportStateAndNullFallback() {
        final Entity entity = mock(Entity.class);
        final Location from = new Location(mock(World.class), 1.0, 2.0, 3.0);
        final EntityPostTeleportAsyncEvent event = new EntityPostTeleportAsyncEvent(
            entity,
            from,
            null,
            PlayerTeleportEvent.TeleportCause.ENDER_PEARL,
            EntityTeleportAsyncEvent.TeleportType.CROSS_REGION
        );

        assertSame(entity, event.getEntity());
        assertSame(from, event.getFrom());
        assertSame(from, event.getTo());
        assertSame(PlayerTeleportEvent.TeleportCause.ENDER_PEARL, event.getCause());
        assertSame(EntityTeleportAsyncEvent.TeleportType.CROSS_REGION, event.getType());
        assertSame(EntityPostTeleportAsyncEvent.getHandlerList(), event.getHandlers());
    }
}
