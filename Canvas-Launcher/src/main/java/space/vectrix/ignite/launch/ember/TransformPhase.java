package space.vectrix.ignite.launch.ember;

/**
 * Represents the phase of a transformation.
 *
 * @author vectrix
 * @since 1.0.0
 */
public enum TransformPhase {
  /**
   * The transformation phase that is used when the class is loaded.
   *
   * @since 1.0.0
   */
  INITIALIZE,

  /**
   * The transformation phase that is used when mixin is being applied.
   *
   * @since 1.0.0
   */
  MIXIN
}
