package space.vectrix.ignite.agent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides static access to add additional resources to the system
 * classloader.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class IgniteAgent {
  private static Instrumentation INSTRUMENTATION = null;

  /**
   * Adds a {@link ClassFileTransformer} to this agent.
   *
   * @param transformer the transformer
   * @since 1.0.0
   */
  public static void addTransformer(final @NotNull ClassFileTransformer transformer) {
    if(IgniteAgent.INSTRUMENTATION != null) IgniteAgent.INSTRUMENTATION.addTransformer(transformer);
  }

  /**
   * Adds a jar {@link Path} to this agent.
   *
   * @param path the path
   * @throws IOException if there is an error resolving the path
   * @since 1.0.0
   */
  public static void addJar(final @NotNull Path path) throws IOException {
    final File file = path.toFile();
    if(!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
    if(file.isDirectory() || !file.getName().endsWith(".jar")) throw new IOException("Provided path is not a jar file: " + path);
    IgniteAgent.addJar(new JarFile(file));
  }

  /**
   * Adds a {@link JarFile} to this agent.
   *
   * @param jar the jar file
   * @since 1.0.0
   */
  public static void addJar(final @NotNull JarFile jar) {
    if(IgniteAgent.INSTRUMENTATION != null) {
      IgniteAgent.INSTRUMENTATION.appendToSystemClassLoaderSearch(jar);
      return;
    }

    throw new IllegalStateException("Unable to addJar for '" + jar.getName() + "'.");
  }

  /**
   * The agent premain entrypoint.
   *
   * @param arguments the arguments
   * @param instrumentation the instrumentation
   * @since 1.0.0
   */
  public static void premain(final @NotNull String arguments, final @Nullable Instrumentation instrumentation) {
    IgniteAgent.agentmain(arguments, instrumentation);
  }

  /**
   * The agent main entrypoint.
   *
   * @param arguments the arguments
   * @param instrumentation the instrumentation
   * @since 1.0.0
   */
  public static void agentmain(final @NotNull String arguments, final @Nullable Instrumentation instrumentation) {
    if(IgniteAgent.INSTRUMENTATION == null) IgniteAgent.INSTRUMENTATION = instrumentation;
    if(IgniteAgent.INSTRUMENTATION == null) throw new NullPointerException("Unable to get instrumentation instance!");
  }

  private IgniteAgent() {
  }
}
