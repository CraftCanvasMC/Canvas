package io.canvasmc.canvas.event;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

public class EntityPostPortalAsyncEventTest {

    @Test
    public void preservesCompletedPortalTransitionState() {
        final Entity entity = mock(Entity.class);
        final World from = mock(World.class);
        final World to = mock(World.class);
        final EntityPostPortalAsyncEvent event = new EntityPostPortalAsyncEvent(entity, from, to, PortalType.ENDER);

        assertSame(entity, event.getEntity());
        assertSame(from, event.getFrom());
        assertSame(to, event.getTo());
        assertSame(PortalType.ENDER, event.getPortalType());
        assertSame(EntityPostPortalAsyncEvent.getHandlerList(), event.getHandlers());
    }
}
