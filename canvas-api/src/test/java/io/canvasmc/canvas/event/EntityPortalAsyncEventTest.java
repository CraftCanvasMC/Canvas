package io.canvasmc.canvas.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

public class EntityPortalAsyncEventTest {

    @Test
    public void exposesAndAllowsUpdatingPortalTransitionState() {
        final Entity entity = mock(Entity.class);
        final World from = mock(World.class);
        final World initialDestination = mock(World.class);
        final World updatedDestination = mock(World.class);
        final EntityPortalAsyncEvent event = new EntityPortalAsyncEvent(entity, from, initialDestination, PortalType.NETHER);

        assertSame(entity, event.getEntity());
        assertSame(from, event.getFrom());
        assertSame(initialDestination, event.getTo());
        assertSame(PortalType.NETHER, event.getPortalType());
        assertSame(EntityPortalAsyncEvent.getHandlerList(), event.getHandlers());
        assertFalse(event.isCancelled());

        event.setTo(updatedDestination);
        event.setCancelled(true);

        assertSame(updatedDestination, event.getTo());
        assertTrue(event.isCancelled());
    }
}
