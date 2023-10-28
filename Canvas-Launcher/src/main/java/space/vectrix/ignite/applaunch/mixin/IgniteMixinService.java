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
package space.vectrix.ignite.applaunch.mixin;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.asm.launch.platform.container.ContainerHandleModLauncher;
import org.spongepowered.asm.service.modlauncher.MixinServiceModLauncher;

import java.util.Collection;
import java.util.Collections;

public final class IgniteMixinService extends MixinServiceModLauncher {
  private final LauncherContainer container = new LauncherContainer(this.getName());

  @Override
  public final boolean isValid() {
    return true;
  }

  @Override
  public final @NonNull ContainerHandleModLauncher getPrimaryContainer() {
    return this.container;
  }

  @Override
  public final @NonNull Collection<String> getPlatformAgents() {
    return Collections.singleton("space.vectrix.ignite.applaunch.mixin.IgniteMixinPlatformService");
  }

  private static final class LauncherContainer extends ContainerHandleModLauncher {
    public LauncherContainer(final @NonNull String name) {
      super(name);
    }
  }
}
