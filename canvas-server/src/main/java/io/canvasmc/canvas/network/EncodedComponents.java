package io.canvasmc.canvas.network;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.canvasmc.canvas.GlobalConfiguration;
import io.papermc.paper.adventure.AdventureComponent;
import io.papermc.paper.adventure.PaperAdventure;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * Caches the bytes text components encode to on the network, so a name, lore line or scoreboard line that is sent
 * again is copied instead of being encoded through the component codec again.
 * <p>
 * Components are keyed by value, so an item rebuilt with the same lore still hits. The locale and the registries are
 * part of the key. Plugin components holding a translation key are never cached while the server has its own
 * translations, since those are resolved while encoding and can change at runtime.
 */
public final class EncodedComponents {
    // Larger components are rare, and mostly chat that is never sent twice
    private static final int MAX_BYTES = 16384;
    private static final long MAX_WEIGHT = 64L * 1024L * 1024L;
    // The key holds the component too, which weighs about twice its encoded bytes
    private static final int WEIGHT_PER_BYTE = 3;

    private static final Cache<Key, byte[]> ENCODED = CacheBuilder.newBuilder()
        .maximumWeight(MAX_WEIGHT)
        .weigher((Key key, byte[] bytes) -> bytes.length * WEIGHT_PER_BYTE)
        .expireAfterAccess(Duration.ofMinutes(5L))
        .concurrencyLevel(16)
        .build();
    // Adventure components are immutable, so these are cached per instance, weakly and by identity
    private static final Cache<net.kyori.adventure.text.Component, Boolean> TRANSLATABLE = CacheBuilder.newBuilder()
        .weakKeys()
        .maximumSize(8192)
        .concurrencyLevel(16)
        .build();
    private static final Cache<net.kyori.adventure.text.Component, Integer> HASHES = CacheBuilder.newBuilder()
        .weakKeys()
        .maximumSize(16384)
        .concurrencyLevel(16)
        .build();

    private EncodedComponents() {
    }

    /**
     * Wraps a component stream codec so it writes cached bytes for components it has encoded before
     *
     * @param codec
     *     the codec to wrap
     *
     * @return the caching codec
     */
    public static StreamCodec<RegistryFriendlyByteBuf, Component> cached(final StreamCodec<RegistryFriendlyByteBuf, Component> codec) {
        return new StreamCodec<>() {
            @Override
            public Component decode(final RegistryFriendlyByteBuf input) {
                return codec.decode(input);
            }

            @Override
            public void encode(final RegistryFriendlyByteBuf output, final Component value) {
                final GlobalConfiguration configuration = GlobalConfiguration.getInstance();
                if (configuration == null || !configuration.networking.cacheEncodedComponents) {
                    if (ENCODED.size() > 0L) ENCODED.invalidateAll();
                    codec.encode(output, value);
                    return;
                }

                if (translatedOnEncode(value)) {
                    codec.encode(output, value);
                    return;
                }

                final Key key = key(value, output.adventure$locale, output.registryAccess());
                final byte[] bytes = ENCODED.getIfPresent(key);
                if (bytes != null) {
                    output.writeBytes(bytes);
                    return;
                }

                final int start = output.writerIndex();
                codec.encode(output, value);
                final int length = output.writerIndex() - start;
                if (length <= MAX_BYTES) {
                    final byte[] written = new byte[length];
                    output.getBytes(start, written);
                    ENCODED.put(key, written);
                }
            }
        };
    }

    /**
     * Gets if the server's own translations decide what a component encodes to
     *
     * @param value
     *     the component about to be encoded
     *
     * @return {@code true} if it is a plugin component holding a translation key while the server has translations
     */
    static boolean translatedOnEncode(final Component value) {
        if (!(value instanceof AdventureComponent wrapper)) return false;
        final net.kyori.adventure.text.Component adventure = wrapper.adventure$component();
        Boolean translatable = TRANSLATABLE.getIfPresent(adventure);
        if (translatable == null) {
            translatable = holdsTranslatable(adventure);
            TRANSLATABLE.put(adventure, translatable);
        }
        return translatable && PaperAdventure.hasAnyTranslations();
    }

    private static boolean holdsTranslatable(final net.kyori.adventure.text.Component component) {
        if (component instanceof TranslatableComponent) return true;
        final HoverEvent<?> hover = component.style().hoverEvent();
        if (hover != null && hover.value() instanceof net.kyori.adventure.text.Component text && holdsTranslatable(text)) return true;
        for (final net.kyori.adventure.text.Component child : component.children()) {
            if (holdsTranslatable(child)) return true;
        }
        return false;
    }

    /**
     * Creates the cache key of a component. Plugin components are keyed by the adventure component they wrap, which
     * compares and hashes without being converted, and its hash is computed once per instance. Vanilla components are
     * keyed by themselves, since they are mutable and their hash can't be kept.
     *
     * @param value
     *     the component about to be encoded
     * @param locale
     *     the locale of the receiving player
     * @param registries
     *     the registries it is encoded with
     *
     * @return the key
     */
    static Key key(final Component value, final @Nullable Locale locale, final RegistryAccess registries) {
        if (!(value instanceof AdventureComponent wrapper)) return new Key(value, value.getClass(), locale, registries, value.hashCode());
        final net.kyori.adventure.text.Component adventure = wrapper.adventure$component();
        Integer hash = HASHES.getIfPresent(adventure);
        if (hash == null) {
            hash = adventure.hashCode();
            HASHES.put(adventure, hash);
        }
        return new Key(adventure, value.getClass(), locale, registries, hash);
    }

    record Key(Object component, Class<?> type, @Nullable Locale locale, RegistryAccess registries, int hash) {
        @Override
        public int hashCode() {
            int result = this.hash;
            result = 31 * result + this.type.hashCode();
            result = 31 * result + Objects.hashCode(this.locale);
            return 31 * result + System.identityHashCode(this.registries);
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Key key
                && this.hash == key.hash
                && this.type == key.type
                && this.registries == key.registries
                && Objects.equals(this.locale, key.locale)
                && this.component.equals(key.component);
        }
    }
}
