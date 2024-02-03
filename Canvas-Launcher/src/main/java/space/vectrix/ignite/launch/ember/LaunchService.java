package space.vectrix.ignite.launch.ember;

import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the launch service for Ember.
 *
 * @author vectrix
 * @since 1.0.0
 */
public interface LaunchService {
  /**
   * Executed at the very beginning of the launch process, before mixin has
   * been initialized.
   *
   * @since 1.0.0
   */
  void initialize();

  /**
   * Configures the class loader, before mixin has been initialized.
   *
   * @param classLoader the class loader
   * @param transformer the transformer
   * @since 1.0.0
   */
  void configure(final @NotNull EmberClassLoader classLoader, final @NotNull EmberTransformer transformer);

  /**
   * Executed after mixin has been initialized, but before the game has
   * launched.
   *
   * @param transformer the transformer
   * @since 1.0.0
   */
  void prepare(final @NotNull EmberTransformer transformer);

  /**
   * Launches the game.
   *
   * @param arguments the launch arguments
   * @param loader the class loader
   * @return a callable
   * @since 1.0.0
   */
  @NotNull Callable<Void> launch(final @NotNull String@NotNull [] arguments, final @NotNull EmberClassLoader loader);
}
