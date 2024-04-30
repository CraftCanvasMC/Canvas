package space.vectrix.ignite;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import space.vectrix.ignite.util.BlackboardMap;

/**
 * Represents a map of startup flags.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class Blackboard {
  private static final BlackboardMap BLACKBOARD = BlackboardMap.create();

  // formatting:off
  public static final BlackboardMap.@NotNull Key<Boolean> DEBUG = key("ignite.debug", Boolean.class, true);
  public static final BlackboardMap.@NotNull Key<String> GAME_LOCATOR = key("ignite.locator", String.class, "paper");
  public static final BlackboardMap.@NotNull Key<Path> GAME_JAR = key("ignite.jar", Path.class, Paths.get("./.launcher/canvas-1.20.5-R0.1-SNAPSHOT.jar"));
  public static final BlackboardMap.@NotNull Key<String> GAME_TARGET = key("ignite.target", String.class, "org.bukkit.craftbukkit.Main");
  public static final BlackboardMap.@NotNull Key<Path> GAME_LIBRARIES = key("ignite.libraries", Path.class, Paths.get("./libraries"));
  public static final BlackboardMap.@NotNull Key<Path> MODS_DIRECTORY = key("ignite.mods", Path.class, Paths.get("./plugins"));
  // formatting:on

  /**
   * Returns the value associated with the {@link BlackboardMap.Key}.
   *
   * @param key the key
   * @param <T> the value type
   * @return the value
   * @since 1.0.0
   */
  public static <T> @NotNull Optional<T> get(final BlackboardMap.@NotNull Key<T> key) {
    return Blackboard.BLACKBOARD.get(key);
  }

  /**
   * Returns the value associated with the {@link BlackboardMap.Key}.
   *
   * @param key the key
   * @param <T> the value type
   * @return the value
   * @since 1.0.0
   */
  public static <T> @UnknownNullability T raw(final BlackboardMap.@NotNull Key<T> key) {
    return Blackboard.BLACKBOARD.get(key).orElse(key.defaultValue());
  }

  /**
   * Supplies the value associated with the {@link BlackboardMap.Key}.
   *
   * @param key the key
   * @param supplier the supplier
   * @param <T> the value type
   * @since 1.0.0
   */
  public static <T> void compute(final BlackboardMap.@NotNull Key<T> key, final @NotNull Supplier<T> supplier) {
    Blackboard.BLACKBOARD.put(key, Blackboard.supplyOrNull(supplier));
  }

  /**
   * Sets the value associated with the {@link BlackboardMap.Key}.
   *
   * @param key the key
   * @param value the value
   * @param <T> the value type
   * @since 1.0.0
   */
  public static <T> void put(final BlackboardMap.@NotNull Key<T> key, final @Nullable T value) {
    Blackboard.BLACKBOARD.put(key, value);
  }

  /**
   * Returns a new {@link BlackboardMap.Key} for the given key, type and
   * default value.
   *
   * @param key the key
   * @param type the type
   * @param defaultValue the default value
   * @param <T> the value type
   * @return a new blackboard key
   * @since 1.0.0
   */
  public static <T> BlackboardMap.@NotNull Key<T> key(final @NotNull String key, final @NotNull Class<? super T> type, final @Nullable T defaultValue) {
    return BlackboardMap.Key.of(Blackboard.BLACKBOARD, key, type, defaultValue);
  }

  private static <T> @Nullable T supplyOrNull(final @NotNull Supplier<T> supplier) {
    try {
      return supplier.get();
    } catch(final Throwable throwable) {
      return null;
    }
  }

  private Blackboard() {
  }
}
