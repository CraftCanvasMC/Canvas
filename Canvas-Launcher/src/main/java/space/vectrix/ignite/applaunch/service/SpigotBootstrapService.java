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
package space.vectrix.ignite.applaunch.service;

import org.checkerframework.checker.nullness.qual.NonNull;
import space.vectrix.ignite.api.Blackboard;
import space.vectrix.ignite.api.service.IBootstrapService;
import space.vectrix.ignite.api.util.BlackboardMap;
import space.vectrix.ignite.applaunch.agent.Agent;
import space.vectrix.ignite.applaunch.agent.transformer.SpigotTransformer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SpigotBootstrapService implements IBootstrapService {
  private static final BlackboardMap.@NonNull Key<Path> SPIGOT_BUNDLER_KEY = Blackboard.key("bundlerRepoDir", Path.class);
  private static final BlackboardMap.@NonNull Key<Path> SPIGOT_JAR_KEY = Blackboard.key("ignite.spigot.jar", Path.class);
  private static final BlackboardMap.@NonNull Key<String> SPIGOT_VERSION_KEY = Blackboard.key("ignite.spigot.version", String.class);
  private static final BlackboardMap.@NonNull Key<String> SPIGOT_TARGET_KEY = Blackboard.key("ignite.spigot.target", String.class);

  /**
   * The spigot bundler directory.
   */
  public static final @NonNull Path SPIGOT_BUNDLER = Paths.get(System.getProperty(SpigotBootstrapService.SPIGOT_BUNDLER_KEY.getName(), "bundler"));

  /**
   * The spigot bootstrap jar path.
   */
  public static final @NonNull Path SPIGOT_JAR = Paths.get(System.getProperty(SpigotBootstrapService.SPIGOT_JAR_KEY.getName(), "./spigot.jar"));

  /**
   * The spigot bootstrap target version.
   */
  public static final @NonNull String SPIGOT_VERSION = System.getProperty(SpigotBootstrapService.SPIGOT_VERSION_KEY.getName(), "1.19.3-R0.1-SNAPSHOT");

  /**
   * The spigot bootstrap target class path.
   */
  public static final @NonNull String SPIGOT_TARGET = System.getProperty(SpigotBootstrapService.SPIGOT_TARGET_KEY.getName(), "org.bukkit.craftbukkit.bootstrap.Main");

  @Override
  public @NonNull String name() {
    return "spigot";
  }

  @Override
  public boolean validate() {
    return true;
  }

  @Override
  public void execute() throws Throwable {
    // Clear the bundler main class.
    System.setProperty("bundlerMainClass", "");

    // Add spigot bootstrap transformer to the Agent.
    Agent.addTransformer(new SpigotTransformer(SpigotBootstrapService.SPIGOT_TARGET.replace('.', '/')));

    // Load spigot bootstrap jar on the provided ClassLoader via the Agent.
    try {
      Agent.addJar(SpigotBootstrapService.SPIGOT_JAR);
    } catch (final IOException exception) {
      throw new IllegalStateException("Unable to add spigot jar to classpath!");
    }

    // Launch spigot bootstrap.
    try {
      final Class<?> spigotClass = Class.forName(SpigotBootstrapService.SPIGOT_TARGET);
      spigotClass
        .getMethod("main", String[].class)
        .invoke(null, (Object) new String[0]);
    } catch (final ClassNotFoundException exception) {
      throw new RuntimeException(exception);
    }

    // Update the launch jar. (Forced)
    Blackboard.putProperty(Blackboard.LAUNCH_JAR, this.getServerJar());
    Blackboard.putProperty(Blackboard.LIBRARIES_DIRECTORY_PATH, this.getLibrariesDirectory());
  }

  public Path getServerJar() {
    return Paths.get(String.format("./%s/versions/spigot-%s.jar", SpigotBootstrapService.SPIGOT_BUNDLER, SpigotBootstrapService.SPIGOT_VERSION));
  }

  public Path getLibrariesDirectory() {
    return Paths.get(String.format("./%s/libraries", SpigotBootstrapService.SPIGOT_BUNDLER));
  }
}
