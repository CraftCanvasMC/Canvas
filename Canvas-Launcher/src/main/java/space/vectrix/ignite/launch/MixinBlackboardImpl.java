package space.vectrix.ignite.launch;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;
import space.vectrix.ignite.Blackboard;
import space.vectrix.ignite.util.BlackboardMap;

/**
 * Represents the mixin blackboard provider.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class MixinBlackboardImpl implements IGlobalPropertyService {
  private final Map<String, IPropertyKey> keys = new HashMap<>();

  @Override
  public IPropertyKey resolveKey(final @NotNull String name) {
    return this.keys.computeIfAbsent(name, key -> new Key<>(key, Object.class));
  }

  @Override
  public <T> T getProperty(final @NotNull IPropertyKey key) {
    return this.getProperty(key, null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setProperty(final @NotNull IPropertyKey key, final @NotNull Object other) {
    Blackboard.put(((Key<Object>) key).key, other);
  }

  @Override
  public @Nullable String getPropertyString(final @NotNull IPropertyKey key, final @Nullable String defaultValue) {
    return this.getProperty(key, defaultValue);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> @Nullable T getProperty(final @NotNull IPropertyKey key, final @Nullable T defaultValue) {
    return Blackboard.get(((Key<T>) key).key).orElse(defaultValue);
  }

  private static class Key<V> implements IPropertyKey {
    private final BlackboardMap.Key<V> key;

    /* package */ Key(final @NotNull String name, final @NotNull Class<V> clazz) {
      this.key = Blackboard.key(name, clazz, null);
    }
  }
}
