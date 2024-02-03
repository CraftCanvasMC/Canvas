package space.vectrix.ignite.mod;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the mod manager.
 *
 * @author vectrix
 * @since 1.0.0
 */
@ApiStatus.NonExtendable
public interface Mods {
  /**
   * Returns {@code true} if the given mod is loaded, otherwise it returns
   * {@code false}.
   *
   * @param id the mod identifier
   * @return whether the mod is loaded
   * @since 1.0.0
   */
  boolean loaded(final @NotNull String id);

  /**
   * Returns the {@link ModContainer} for the given mod identifier.
   *
   * @param id the mod identifier
   * @return the mod container
   * @since 1.0.0
   */
  @NotNull Optional<ModContainer> container(final @NotNull String id);

  /**
   * Returns a list of the located mod resources.
   *
   * @return the located mod resources
   * @since 1.0.0
   */
  @NotNull List<ModResource> resources();

  /**
   * Returns a collection of the resolved mod containers.
   *
   * @return the resolved mod containers
   * @since 1.0.0
   */
  @NotNull Collection<ModContainer> containers();
}
