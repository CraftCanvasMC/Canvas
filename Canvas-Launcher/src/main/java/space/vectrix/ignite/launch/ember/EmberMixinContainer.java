package space.vectrix.ignite.launch.ember;

import java.nio.file.Path;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;

/**
 * Represents the root container.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class EmberMixinContainer extends ContainerHandleVirtual {
  /**
   * Creates a new root container handle.
   *
   * @param name the name
   * @since 1.0.0
   */
  public EmberMixinContainer(final @NotNull String name) {
    super(name);
  }

  /**
   * Adds a resource to this container.
   *
   * @param name the name
   * @param path the path
   * @since 1.0.0
   */
  public void addResource(final @NotNull String name, final @NotNull Path path) {
    this.add(new ResourceContainer(name, path));
  }

  /**
   * Adds a resource to this container.
   *
   * @param entry the entry
   * @since 1.0.0
   */
  public void addResource(final Map.@NotNull Entry<String, Path> entry) {
    this.add(new ResourceContainer(entry.getKey(), entry.getValue()));
  }

  @Override
  public String toString() {
    return "EmberMixinContainer{name=" + this.getName() + "}";
  }

  /* package */ static class ResourceContainer extends ContainerHandleURI {
    private final String name;
    private final Path path;

    /* package */ ResourceContainer(final @NotNull String name, final @NotNull Path path) {
      super(path.toUri());

      this.name = name;
      this.path = path;
    }

    public @NotNull String name() {
      return this.name;
    }

    public @NotNull Path path() {
      return this.path;
    }

    @Override
    public @NotNull String toString() {
      return "ResourceContainer{name=" + this.name + ", path=" + this.path + "}";
    }
  }
}
