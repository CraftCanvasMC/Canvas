package space.vectrix.ignite.util;

/**
 * Provides static access to the transformation excluded paths and packages.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class IgniteExclusions {
  /**
   * The resource paths excluded from transformation.
   *
   * @since 1.0.0
   */
  public static final String[] TRANSFORMATION_EXCLUDED_PATHS = {
    "org/spongepowered/asm/"
  };

  /**
   * The packages excluded from transformation.
   *
   * @since 1.0.0
   */
  public static final String[] TRANSFORMATION_EXCLUDED_PACKAGES = {
    // Launcher
    "com.astrafell.ignite.",

    // Mixin
    "org.spongepowered.asm."
  };

  private IgniteExclusions() {
  }
}
