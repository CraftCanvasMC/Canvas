package space.vectrix.ignite.util;

import com.google.gson.Gson;
import org.objectweb.asm.Opcodes;
import space.vectrix.ignite.IgniteBootstrap;

/**
 * Provides static access to the constants.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class IgniteConstants {
  /**
   * The API name.
   *
   * @since 1.0.0
   */
  public static final String API_TITLE = IgniteBootstrap.class.getPackage().getSpecificationTitle();

  /**
   * The API version.
   *
   * @since 1.0.0
   */
  public static final String API_VERSION = IgniteBootstrap.class.getPackage().getSpecificationVersion();

  /**
   * The implementation version.
   *
   * @since 1.0.0
   */
  public static final String IMPLEMENTATION_VERSION = IgniteBootstrap.class.getPackage().getImplementationVersion();

  /**
   * The ASM version to use.
   *
   * @since 1.0.0
   */
  public static final int ASM_VERSION = Opcodes.ASM9;

  /**
   * The mod configuration file name.
   *
   * @since 1.0.0
   */
  public static final String MOD_CONFIG = "mixin-plugin.json";

  /**
   * The gson instance.
   *
   * @since 1.0.0
   */
  public static final Gson GSON = new Gson();

  private IgniteConstants() {
  }
}
