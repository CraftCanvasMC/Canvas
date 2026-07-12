package io.canvasmc.canvas.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;

public class PlayerPostRespawnAsyncEventTest {

    @Test
    public void exposesCompletedRespawnState() {
        final Player player = mock(Player.class);
        final Location location = new Location(mock(World.class), 1.0, 2.0, 3.0);
        final PlayerPostRespawnAsyncEvent event = new PlayerPostRespawnAsyncEvent(
            player,
            location,
            false,
            true,
            false,
            PlayerRespawnEvent.RespawnReason.END_PORTAL
        );

        assertSame(player, event.getPlayer());
        assertEquals(location, event.getRespawnLocation());
        assertNotSame(location, event.getRespawnLocation());
        assertFalse(event.isBedSpawn());
        assertTrue(event.isAnchorSpawn());
        assertFalse(event.isMissingRespawnBlock());
        assertSame(PlayerRespawnEvent.RespawnReason.END_PORTAL, event.getRespawnReason());
        assertSame(PlayerPostRespawnAsyncEvent.getHandlerList(), event.getHandlers());
    }
}
