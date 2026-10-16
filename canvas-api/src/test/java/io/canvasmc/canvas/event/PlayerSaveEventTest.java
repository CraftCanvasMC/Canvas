package io.canvasmc.canvas.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PlayerSaveEventTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void exposesPlayerAndQuitReason(final boolean quit) {
        final Player player = mock(Player.class);
        final PlayerSaveEvent event = new PlayerSaveEvent(player, quit);

        assertSame(player, event.getPlayer());
        assertEquals(quit, event.isQuit());
        assertSame(PlayerSaveEvent.getHandlerList(), event.getHandlers());
    }
}
