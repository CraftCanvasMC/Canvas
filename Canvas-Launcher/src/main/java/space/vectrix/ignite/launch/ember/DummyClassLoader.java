package space.vectrix.ignite.launch.ember;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* package */ final class DummyClassLoader extends ClassLoader {
  private static final Enumeration<URL> NULL_ENUMERATION = new Enumeration<URL>() {
    @Override
    public boolean hasMoreElements() {
      return false;
    }

    @Override
    public @NotNull URL nextElement() {
      throw new NoSuchElementException();
    }
  };

  static {
    ClassLoader.registerAsParallelCapable();
  }

  @Override
  protected @NotNull Class<?> loadClass(final @NotNull String name, final boolean resolve) throws ClassNotFoundException {
    throw new ClassNotFoundException(name);
  }

  @Override
  public @Nullable URL getResource(final @NotNull String name) {
    return null;
  }

  @Override
  public @NotNull Enumeration<URL> getResources(final @NotNull String name) throws IOException {
    return DummyClassLoader.NULL_ENUMERATION;
  }
}
