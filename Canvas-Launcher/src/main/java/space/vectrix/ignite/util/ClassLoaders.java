package space.vectrix.ignite.util;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;
import sun.misc.Unsafe;

/**
 * Taken from <a href="https://github.com/cpw/grossjava9hacks/blob/1.3/src/main/java/cpw/mods/gross/Java9ClassLoaderUtil.java">grossjava9hacks</a>.
 *
 * @author cpw
 * @since 1.0.0
 */
public final class ClassLoaders {
  /**
   * Returns the system class path {@link URL}s.
   *
   * @return the system class path urls
   * @since 1.0.0
   */
  @SuppressWarnings({"restriction", "unchecked"})
  public static URL@NotNull [] systemClassPaths() {
    final ClassLoader classLoader = ClassLoaders.class.getClassLoader();
    if(classLoader instanceof URLClassLoader) {
      return ((URLClassLoader) classLoader).getURLs();
    }

    if(classLoader.getClass().getName().startsWith("jdk.internal.loader.ClassLoaders$")) {
      try {
        final Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        final Unsafe unsafe = (Unsafe) field.get(null);

        // jdk.internal.loader.ClassLoaders.AppClassLoader.ucp
        Field ucpField;
        try {
          ucpField = classLoader.getClass().getDeclaredField("ucp");
        } catch(final NoSuchFieldException | SecurityException e) {
          ucpField = classLoader.getClass().getSuperclass().getDeclaredField("ucp");
        }

        final long ucpFieldOffset = unsafe.objectFieldOffset(ucpField);
        final Object ucpObject = unsafe.getObject(classLoader, ucpFieldOffset);

        // jdk.internal.loader.URLClassPath.path
        final Field pathField = ucpField.getType().getDeclaredField("path");
        final long pathFieldOffset = unsafe.objectFieldOffset(pathField);
        final ArrayList<URL> path = (ArrayList<URL>) unsafe.getObject(ucpObject, pathFieldOffset);

        return path.toArray(new URL[0]);
      } catch(final Exception exception) {
        Logger.error(exception, "Failed to retrieve system classloader paths!");
        return new URL[0];
      }
    }

    return new URL[0];
  }

  private ClassLoaders() {
  }
}
