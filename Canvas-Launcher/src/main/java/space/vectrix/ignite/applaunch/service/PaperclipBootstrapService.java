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
import space.vectrix.ignite.applaunch.agent.transformer.PaperclipTransformer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PaperclipBootstrapService implements IBootstrapService {
  private static final BlackboardMap.@NonNull Key<String> MINECRAFT_VERSION_KEY = Blackboard.key("ignite.paper.minecraft", String.class);
  private static final BlackboardMap.@NonNull Key<String> PAPERCLIP_BRANCH_KEY  = Blackboard.key("ignite.paper.branch", String.class);
  private static final BlackboardMap.@NonNull Key<Path>   PAPERCLIP_JAR_KEY     = Blackboard.key("ignite.paper.jar", Path.class);
  private static final BlackboardMap.@NonNull Key<String> PAPERCLIP_TARGET_KEY  = Blackboard.key("ignite.paper.target", String.class);
  private static final BlackboardMap.@NonNull Key<Boolean> LAUNCH_OVERRIDE_KEY  = Blackboard.key("ignite.paper.override", Boolean.class);

  /**
   * The minecraft version.
   */
  public static final @NonNull String MINECRAFT_VERSION = System.getProperty(PaperclipBootstrapService.MINECRAFT_VERSION_KEY.getName(), "1.20.2"); // Canvas

  /**
   * The paperclip branch name.
   */
  public static final @NonNull String PAPERCLIP_BRANCH = System.getProperty(PaperclipBootstrapService.PAPERCLIP_BRANCH_KEY.getName(), "paper"); // Canvas

  /**
   * The paperclip jar path.
   */
  public static final @NonNull Path PAPERCLIP_JAR = Paths.get(System.getProperty(PaperclipBootstrapService.PAPERCLIP_JAR_KEY.getName(), "./canvas-serverData.jar")); // Canvas

  /**
   * The paperclip jar target class path.
   */
  public static final @NonNull String PAPERCLIP_TARGET = System.getProperty(PaperclipBootstrapService.PAPERCLIP_TARGET_KEY.getName(), "io.papermc.paperclip.Paperclip");

  /**
   * Whether to override the launch jar with one set by this service.
   */
  public static final boolean FORCE_LAUNCH_JAR = Boolean.parseBoolean(System.getProperty(PaperclipBootstrapService.LAUNCH_OVERRIDE_KEY.getName(), "false")); // Canvas

  @Override
  public @NonNull String name() {
    return "paper";
  }

  @Override
  public boolean validate() {
    return true;
  }

  @Override
  public void execute() throws Throwable {
    // Add paperclip transformer to the Agent.
    Agent.addTransformer(new PaperclipTransformer(PaperclipBootstrapService.PAPERCLIP_TARGET.replace('.', '/')));

    // Set paperclip to patch only, we launch the server ourselves.
    System.setProperty("paperclip.patchonly", "true");

    // Load the paperclip jar on the provided ClassLoader via the Agent.
    try {
      Agent.addJar(PaperclipBootstrapService.PAPERCLIP_JAR);
    } catch (final IOException exception) {
      throw new IllegalStateException("Unable to add paperclip jar to classpath!");
    }

    // Launch Paperclip
    try {
      final Class<?> paperclipClass = Class.forName(PaperclipBootstrapService.PAPERCLIP_TARGET);
      paperclipClass
        .getMethod("main", String[].class)
        .invoke(null, (Object) new String[0]);
    } catch (final ClassNotFoundException exception) {
      throw new RuntimeException(exception);
    }

    if(PaperclipBootstrapService.FORCE_LAUNCH_JAR) {
      // Update the launch jar. (Forced)
      Blackboard.putProperty(Blackboard.LAUNCH_JAR, this.getServerJar());
    }

    // Remove the patchonly flag.
    System.getProperties().remove("paperclip.patchonly");
  }

  public Path getServerJar() {
    return Paths.get(String.format("./versions/%s/%s-%s.jar", PaperclipBootstrapService.MINECRAFT_VERSION, PaperclipBootstrapService.PAPERCLIP_BRANCH, PaperclipBootstrapService.MINECRAFT_VERSION));
  }
}
