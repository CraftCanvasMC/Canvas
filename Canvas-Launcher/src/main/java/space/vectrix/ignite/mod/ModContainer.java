package space.vectrix.ignite.mod;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.tinylog.TaggedLogger;

/**
 * Represents a mod container.
 *
 * @author vectrix
 * @since 1.0.0
 */
@ApiStatus.NonExtendable
public interface ModContainer {
  /**
   * Returns a {@link TaggedLogger} for this mod.
   *
   * @return the mod logger
   * @since 1.0.0
   */
  @NotNull TaggedLogger logger();

  /**
   * Returns the mod id.
   *
   * @return the id
   * @since 1.0.0
   */
  @NotNull String id();

  /**
   * Returns the mod version.
   *
   * @return the version
   * @since 1.0.0
   */
  @NotNull String version();

  /**
   * Returns the mod resource.
   *
   * @return the resource
   * @since 1.0.0
   */
  @NotNull ModResource resource();
}
