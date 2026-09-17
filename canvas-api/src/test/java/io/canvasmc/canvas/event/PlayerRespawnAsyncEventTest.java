package io.canvasmc.canvas.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;

public class PlayerRespawnAsyncEventTest {

    @Test
    public void validatesAndCopiesUpdatedRespawnLocations() {
        final Player player = mock(Player.class);
        final Location initial = new Location(mock(World.class), 1.0, 2.0, 3.0);
        final PlayerRespawnAsyncEvent event = new PlayerRespawnAsyncEvent(
            player,
            initial,
            true,
            false,
            true,
            PlayerRespawnEvent.RespawnReason.DEATH
        );

        assertSame(player, event.getPlayer());
        assertEquals(initial, event.getRespawnLocation());
        assertNotSame(initial, event.getRespawnLocation());
        assertTrue(event.isBedSpawn());
        assertFalse(event.isAnchorSpawn());
        assertTrue(event.isMissingRespawnBlock());
        assertSame(PlayerRespawnEvent.RespawnReason.DEATH, event.getRespawnReason());
        assertSame(PlayerRespawnAsyncEvent.getHandlerList(), event.getHandlers());

        final Location updated = new Location(mock(World.class), 4.0, 5.0, 6.0);
        event.setRespawnLocation(updated);
        assertEquals(updated, event.getRespawnLocation());
        assertNotSame(updated, event.getRespawnLocation());

        assertThrows(IllegalArgumentException.class, () -> event.setRespawnLocation(null));
        assertThrows(IllegalArgumentException.class, () -> event.setRespawnLocation(new Location(null, 0.0, 0.0, 0.0)));
    }
}
