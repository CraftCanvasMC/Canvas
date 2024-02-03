package space.vectrix.ignite.launch.ember;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.tinylog.Logger;
import space.vectrix.ignite.util.IgniteConstants;

/**
 * Represents the transformer.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class EmberTransformer {
  private final ServiceLoader<TransformerService> serviceLoader = ServiceLoader.load(TransformerService.class, Ember.class.getClassLoader());
  private final Map<Class<? extends TransformerService>, TransformerService> transformers = new IdentityHashMap<>();

  private Predicate<String> exclusionFilter = path -> false;

  /* package */ EmberTransformer() {
    for(final TransformerService service : this.serviceLoader) {
      this.transformers.put(service.getClass(), service);
    }
  }

  /**
   * Adds a new exclusion filter.
   *
   * <p>If the predicate results to {@code true}, transformation will not be
   * applied.</p>
   *
   * @param predicate the filter
   * @since 1.0.0
   */
  public void exclude(final @NotNull Predicate<String> predicate) {
    this.exclusionFilter = this.exclusionFilter.or(predicate);
  }

  /**
   * Returns the transformer for the given class.
   *
   * @param transformer the transformer class
   * @param <T> the transformer type
   * @return the transformer, if present
   * @since 1.0.0
   */
  public <T extends TransformerService> @Nullable T transformer(final @NotNull Class<T> transformer) {
    return transformer.cast(this.transformers.get(transformer));
  }

  /**
   * Returns an unmodifiable collection of transformers.
   *
   * @return the transformers
   * @since 1.0.0
   */
  public @NotNull Collection<TransformerService> transformers() {
    return Collections.unmodifiableCollection(this.transformers.values());
  }

  /* package */ byte@NotNull [] transform(final @NotNull String className, final byte@NotNull [] input, final @NotNull TransformPhase phase) {
    final String internalName = className.replace('.', '/');

    // Check if the path is excluded from transformation.
    if(this.exclusionFilter.test(internalName)) {
      return input;
    }

    final Type type = Type.getObjectType(internalName);
    final ClassNode node = new ClassNode(IgniteConstants.ASM_VERSION);
    if(input.length > 0) {
      final ClassReader reader = new ClassReader(input);
      reader.accept(node, 0);
    } else {
      node.name = type.getInternalName();
      node.version = MixinEnvironment.getCompatibilityLevel().getClassVersion();
      node.superName = "java/lang/Object";
    }

    final List<TransformerService> transformers = this.order(phase);
    boolean transformed = false;
    {
      for(final TransformerService service : transformers) {
        try {
          // If the transformer should not transform the class, skip it.
          if(!service.shouldTransform(type, node)) continue;
          // Attempt to transform the class.
          transformed |= service.transform(type, node, phase);
        } catch(final Throwable throwable) {
          Logger.error(throwable, "Failed to transform {} with {}", type.getClassName(), service.getClass().getName());
        }
      }
    }

    // If no transformations were applied, return the original input.
    if(!transformed) return input;

    final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    node.accept(writer);

    return writer.toByteArray();
  }

  private List<TransformerService> order(final @NotNull TransformPhase phase) {
    return this.transformers.values().stream()
      .filter(value -> value.priority(phase) != -1) // Filter out transformers that do not apply to the given phase.
      .sorted((first, second) -> {
        final int firstPriority = first.priority(phase);
        final int secondPriority = second.priority(phase);
        return Integer.compare(firstPriority, secondPriority);
      })
      .collect(Collectors.toList());
  }
}
