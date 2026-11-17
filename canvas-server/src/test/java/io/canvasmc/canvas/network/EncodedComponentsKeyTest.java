package io.canvasmc.canvas.network;

import io.papermc.paper.adventure.AdventureComponent;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.RegistryAccess;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Plugin components are keyed by the adventure component they wrap, so equal components share a key without being
 * converted, while anything that changes the encoded bytes makes another key.
 */
@Normal
class EncodedComponentsKeyTest {

    private static final RegistryAccess REGISTRIES = RegistryAccess.EMPTY;

    @Test
    void equalPluginComponentsShareAKey() {
        final EncodedComponents.Key first = key(Component.text("Lore line", NamedTextColor.GRAY), Locale.US);
        final EncodedComponents.Key second = key(Component.text("Lore line", NamedTextColor.GRAY), Locale.US);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void otherTextOrColourMakesAnotherKey() {
        final EncodedComponents.Key base = key(Component.text("Lore line", NamedTextColor.GRAY), Locale.US);

        assertNotEquals(base, key(Component.text("Lore lines", NamedTextColor.GRAY), Locale.US));
        assertNotEquals(base, key(Component.text("Lore line", NamedTextColor.RED), Locale.US));
    }

    @Test
    void anotherLanguageMakesAnotherKey() {
        assertNotEquals(key(Component.text("Lore line"), Locale.US), key(Component.text("Lore line"), Locale.GERMANY));
    }

    @Test
    void vanillaComponentsAreKeyedByWhatTheySay() {
        final EncodedComponents.Key first = EncodedComponents.key(net.minecraft.network.chat.Component.literal("Hi"), Locale.US, REGISTRIES);
        final EncodedComponents.Key second = EncodedComponents.key(net.minecraft.network.chat.Component.literal("Hi"), Locale.US, REGISTRIES);

        assertEquals(first, second);
        assertNotEquals(first, key(Component.text("Hi"), Locale.US));
    }

    private static EncodedComponents.Key key(final Component component, final Locale locale) {
        return EncodedComponents.key(new AdventureComponent(component), locale, REGISTRIES);
    }
}
