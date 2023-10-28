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
package space.vectrix.ignite.applaunch;

import org.spongepowered.configurate.gson.GsonConfigurationLoader;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import space.vectrix.ignite.api.Blackboard;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;

public final class IgniteConfigurations {
  private static final OpenOption[] SINK_OPTIONS = new OpenOption[] {
    StandardOpenOption.CREATE,
    StandardOpenOption.TRUNCATE_EXISTING,
    StandardOpenOption.WRITE,
    StandardOpenOption.DSYNC
  };

  public static void configure() {
    Blackboard.computeProperty(Blackboard.GSON_LOADER, path -> GsonConfigurationLoader.builder()
      .source(() -> Files.newBufferedReader(path, StandardCharsets.UTF_8))
      .sink(() -> Files.newBufferedWriter(path, StandardCharsets.UTF_8, IgniteConfigurations.SINK_OPTIONS))
      .defaultOptions(options -> options.shouldCopyDefaults(true))
      .build()
    );

    Blackboard.computeProperty(Blackboard.HOCON_LOADER, path -> HoconConfigurationLoader.builder()
      .source(() -> Files.newBufferedReader(path, StandardCharsets.UTF_8))
      .sink(() -> Files.newBufferedWriter(path, StandardCharsets.UTF_8, IgniteConfigurations.SINK_OPTIONS))
      .defaultOptions(options -> options.shouldCopyDefaults(true))
      .build()
    );

    Blackboard.computeProperty(Blackboard.YAML_LOADER, path -> YamlConfigurationLoader.builder()
      .source(() -> Files.newBufferedReader(path, StandardCharsets.UTF_8))
      .sink(() -> Files.newBufferedWriter(path, StandardCharsets.UTF_8, IgniteConfigurations.SINK_OPTIONS))
      .defaultOptions(options -> options.shouldCopyDefaults(true))
      .build()
    );
  }

  private IgniteConfigurations() {}
}
