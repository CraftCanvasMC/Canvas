/*
 * This file is part of Ignite, licensed under the MIT License (MIT).
 *
 * Copyright (c) vectrix.space <https://vectrix.space/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package space.vectrix.ignite.applaunch.agent;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class Agent {
  /**
   * The agents launch instrumentation.
   */
  private static Instrumentation LAUNCH_INSTRUMENTATION = null;

  public static void addTransformer(final @NonNull ClassFileTransformer transformer) {
    if (LAUNCH_INSTRUMENTATION != null) LAUNCH_INSTRUMENTATION.addTransformer(transformer);
  }

  /**
   * Adds the specified JAR file to the system class loader.
   *
   * @param path The path to the JAR file
   * @throws IOException If the target cannot be added
   */
  public static void addJar(final @NonNull Path path) throws IOException {
    final File file = path.toFile();
    if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
    if (file.isDirectory() || !file.getName().endsWith(".jar")) throw new IOException("Provided path is not a jar file: " + path);
    if (LAUNCH_INSTRUMENTATION != null) {
      LAUNCH_INSTRUMENTATION.appendToSystemClassLoaderSearch(new JarFile(file));
      return;
    }
    throw new IllegalStateException("Unable to addUrl for '" + path + "'.");
  }

  /**
   * The agent premain is called by the JRE.
   *
   * @param agentArgs The agent arguments
   * @param instrumentation The instrumentation
   */
  public static void premain(final @NonNull String agentArgs, final @Nullable Instrumentation instrumentation) {
    agentmain(agentArgs, instrumentation);
  }

  /**
   * The agent main is called by the JRE.
   *
   * <p>You should launch the agent in premain!</p>
   *
   * @param agentArgs The agent arguments
   * @param instrumentation The instrumentation
   */
  public static void agentmain(final @NonNull String agentArgs, final @Nullable Instrumentation instrumentation) {
    if (LAUNCH_INSTRUMENTATION == null) LAUNCH_INSTRUMENTATION = instrumentation;
    if (LAUNCH_INSTRUMENTATION == null) throw new NullPointerException("instrumentation");
  }

  public static void updateSecurity() {
    final Set<Module> systemUnnamed = Set.of(ClassLoader.getSystemClassLoader().getUnnamedModule());
    Agent.LAUNCH_INSTRUMENTATION.redefineModule(
      Manifest.class.getModule(),
      Set.of(),
      Map.of("sun.security.util", systemUnnamed), // ModLauncher
      Map.of(
        // ModLauncher -- needs Manifest.jv, and various JarVerifier methods
        "java.util.jar", systemUnnamed
      ),
      Set.of(),
      Map.of()
    );
  }

  private Agent() {}
}
