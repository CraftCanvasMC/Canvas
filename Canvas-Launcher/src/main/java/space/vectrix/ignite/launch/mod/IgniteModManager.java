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
package space.vectrix.ignite.launch.mod;

import org.checkerframework.checker.nullness.qual.NonNull;
import space.vectrix.ignite.api.event.platform.PlatformConstructEvent;
import space.vectrix.ignite.api.event.platform.PlatformInitializeEvent;
import space.vectrix.ignite.api.mod.ModContainer;
import space.vectrix.ignite.api.mod.ModManager;
import space.vectrix.ignite.applaunch.mod.ModEngine;
import space.vectrix.ignite.launch.IgnitePlatform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public final class IgniteModManager implements ModManager {
  private final Map<Object, ModContainer> containerInstances = new IdentityHashMap<>();
  private final List<ModContainer> containersSorted = new ArrayList<>();
  private final Map<String, ModContainer> containers = new HashMap<>();
  private final ModLoader containerLoader = new ModLoader();
  private final IgnitePlatform platform;

  public IgniteModManager(final @NonNull IgnitePlatform platform) {
    this.platform = platform;
  }

  @Override
  public @NonNull Optional<ModContainer> getContainer(final @NonNull String mod) {
    requireNonNull(mod, "mod");
    return Optional.ofNullable(this.containers.get(mod));
  }

  @Override
  public @NonNull Optional<ModContainer> getContainer(final @NonNull Object mod) {
    requireNonNull(mod, "mod");
    return Optional.ofNullable(this.containerInstances.get(mod));
  }

  @Override
  public boolean isLoaded(final @NonNull String mod) {
    requireNonNull(mod, "mod");
    return this.containers.containsKey(mod);
  }

  @Override
  public boolean isInstance(final @NonNull Object mod) {
    requireNonNull(mod, "mod");
    return this.containerInstances.containsKey(mod);
  }

  @Override
  public @NonNull Collection<ModContainer> getContainers() {
    return Collections.unmodifiableList(this.containersSorted);
  }

  public void loadPlugins(final @NonNull ModEngine engine) {
    this.containersSorted.addAll(this.containerLoader.loadContainers(this.platform, engine.getContainers(), this.containers, this.containerInstances));

    this.platform.getEventManager().post(new PlatformConstructEvent());

    this.platform.getLogger().info("Loading [" + this.containers.values().stream().map(ModContainer::toString).collect(Collectors.joining(", ")) + "].");

    this.platform.getEventManager().post(new PlatformInitializeEvent());
  }
}
