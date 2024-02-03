package space.vectrix.ignite.mod;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.jar.Manifest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

/**
 * Represents a mod resource.
 *
 * @author vectrix
 * @since 1.0.0
 */
@ApiStatus.NonExtendable
public interface ModResource {
  /**
   * Returns the resource locator type.
   *
   * @return the resource locator type
   * @since 1.0.0
   */
  @NotNull String locator();

  /**
   * Returns the resource path.
   *
   * @return the resource path
   * @since 1.0.0
   */
  @NotNull Path path();

  /**
   * Returns the {@link Manifest} for this resource.
   *
   * @return the manifest
   * @since 1.0.0
   */
  @UnknownNullability Manifest manifest();

  /**
   * Returns the {@link FileSystem} for this resource.
   *
   * @return the file system
   * @since 1.0.0
   */
  @NotNull FileSystem fileSystem();
}
