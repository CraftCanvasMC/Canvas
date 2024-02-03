package space.vectrix.ignite.launch.ember;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 * Represents a transformer service for Ember.
 *
 * @author vectrix
 * @since 1.0.0
 */
public interface TransformerService {
  /**
   * Executed after mixin has completed bootstrapping, but before the game has
   * launched.
   *
   * @since 1.0.0
   */
  void prepare();

  /**
   * Returns the priority of this transformer for the given {@link TransformPhase}.
   *
   * <p>A result of -1 means this transformer should not be applied during
   * the given phase.</p>
   *
   * <p>This method will be called multiple times for sorting the transformers
   * each class.</p>
   *
   * @param phase the transform phase
   * @return the priority
   * @since 1.0.0
   */
  int priority(final @NotNull TransformPhase phase);

  /**
   * Returns {@code true} if this transformer should transform the given
   * {@link Type} and {@link ClassNode}, otherwise returns {@code false}.
   *
   * @param type the type
   * @param node the class node
   * @return whether the class should be transformed
   * @since 1.0.0
   */
  boolean shouldTransform(final @NotNull Type type, final @NotNull ClassNode node);

  /**
   * Attempts to transform a class, with the given {@link Type}, {@link ClassNode}
   * and {@link TransformPhase} and returns {@code true} if modifications were
   * made, otherwise returns {@code false}.
   *
   * @param type the type
   * @param node the class node
   * @param phase the transform phase
   * @return whether the class was transformed
   * @since 1.0.0
   */
  boolean transform(final @NotNull Type type, final @NotNull ClassNode node, final @NotNull TransformPhase phase) throws Throwable;
}
