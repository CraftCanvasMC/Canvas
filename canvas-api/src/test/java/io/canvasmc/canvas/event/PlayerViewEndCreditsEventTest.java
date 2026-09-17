package io.canvasmc.canvas.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.util.stream.Stream;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class PlayerViewEndCreditsEventTest {

    public static Stream<Arguments> creditResults() {
        return Stream.of(
            Arguments.of(false, Event.Result.DEFAULT, false),
            Arguments.of(true, Event.Result.DEFAULT, true),
            Arguments.of(false, Event.Result.ALLOW, true),
            Arguments.of(true, Event.Result.ALLOW, true),
            Arguments.of(false, Event.Result.DENY, false),
            Arguments.of(true, Event.Result.DENY, false)
        );
    }

    @ParameterizedTest
    @MethodSource("creditResults")
    public void resolvesVanillaAndOverriddenCreditDecisions(final boolean vanillaResult, final Event.Result result, final boolean expected) {
        final Player player = mock(Player.class);
        final PlayerViewEndCreditsEvent event = new PlayerViewEndCreditsEvent(player, vanillaResult);

        event.setResult(result);

        assertSame(player, event.getPlayer());
        assertEquals(vanillaResult, event.willVanillaShowCredits());
        assertEquals(expected, event.willShowCredits());
        assertSame(PlayerViewEndCreditsEvent.getHandlerList(), event.getHandlers());
    }
}
