package space.vectrix.ignite.launch.ember;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/**
 * Provides the mixin bootstrap service for Ember.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class EmberMixinBootstrap implements IMixinServiceBootstrap {
  /**
   * Creates a new mixin bootstrap service.
   *
   * @since 1.0.0
   */
  public EmberMixinBootstrap() {
  }

  @Override
  public String getName() {
    return "Ember";
  }

  @Override
  public String getServiceClassName() {
    return "space.vectrix.ignite.launch.ember.EmberMixinService";
  }

  @Override
  public void bootstrap() {
  }
}
