package space.vectrix.ignite.mod;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.Manifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * Represents a mod resource that may not be resolved.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class ModResourceImpl implements ModResource {
  private final String locator;
  private final Path path;
  private final Manifest manifest;

  private FileSystem fileSystem;

  /* package */ ModResourceImpl(final @NotNull String locator,
                                final @NotNull Path path,
                                final @UnknownNullability Manifest manifest) {
    this.locator = locator;
    this.path = path;
    this.manifest = manifest;
  }

  @Override
  public @NotNull String locator() {
    return this.locator;
  }

  @Override
  public @NotNull Path path() {
    return this.path;
  }

  @Override
  public @UnknownNullability Manifest manifest() {
    return this.manifest;
  }

  @Override
  public @NotNull FileSystem fileSystem() {
    if(this.fileSystem == null) {
      try {
        this.fileSystem = FileSystems.newFileSystem(this.path(), this.getClass().getClassLoader());
      } catch(final IOException exception) {
        throw new RuntimeException(exception);
      }
    }

    return this.fileSystem;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.locator, this.path, this.manifest);
  }

  @Override
  public boolean equals(final @Nullable Object other) {
    if(this == other) return true;
    if(!(other instanceof ModResourceImpl)) return false;
    final ModResourceImpl that = (ModResourceImpl) other;
    return Objects.equals(this.locator, that.locator)
      && Objects.equals(this.path, that.path)
      && Objects.equals(this.manifest, that.manifest);
  }

  @Override
  public String toString() {
    return "ModResourceImpl{locator='" + this.locator + ", path=" + this.path + ", manifest=" + this.manifest + "}";
  }
}
