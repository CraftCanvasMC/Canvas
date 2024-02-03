package space.vectrix.ignite;

import org.jetbrains.annotations.NotNull;
import space.vectrix.ignite.mod.Mods;

/**
 * Provides static access to the main functions of Ignite.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class Ignite {
  private static Platform PLATFORM;

  /* package */ static void initialize(final @NotNull Platform platform) {
    Ignite.PLATFORM = platform;
  }

  /**
   * Returns the {@link Mods}.
   *
   * @return the mods
   * @since 1.0.0
   */
  public static @NotNull Mods mods() {
    if(Ignite.PLATFORM == null) throw new IllegalStateException("Ignite has not been initialized yet!");
    return Ignite.PLATFORM.mods();
  }

  private Ignite() {
  }
}
