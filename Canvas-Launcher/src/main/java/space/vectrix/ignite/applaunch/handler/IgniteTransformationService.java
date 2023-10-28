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
package space.vectrix.ignite.applaunch.handler;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.asm.launch.MixinBootstrap;
import space.vectrix.ignite.applaunch.IgniteBootstrap;
import space.vectrix.ignite.applaunch.util.IgniteConstants;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IgniteTransformationService implements ITransformationService {
  @Override
  public final @NonNull String name() {
    return IgniteConstants.IGNITE_TRANSFORMATION_SERVICE;
  }

  @Override
  public void initialize(final @NonNull IEnvironment environment) {
    MixinBootstrap.init();
  }

  @Override
  public void beginScanning(final @NonNull IEnvironment environment) {
    // No-op
  }

  @Override
  public final @NonNull List<Map.Entry<String, Path>> runScan(final @NonNull IEnvironment environment) {
    if (IgniteBootstrap.getInstance().getModEngine().locateResources()) {
      final List<Map.Entry<String, Path>> targetResources = IgniteBootstrap.getInstance().getModEngine().loadResources();
      if (!targetResources.isEmpty()) {
        IgniteBootstrap.getInstance().getModEngine().loadTransformers(environment);

        return targetResources;
      }
    }

    return Collections.emptyList();
  }

  @Override
  public void onLoad(final @NonNull IEnvironment env, final @NonNull Set<String> otherServices) throws IncompatibleEnvironmentException {
    // No-op
  }

  @Override
  @SuppressWarnings("rawtypes")
  public final @NonNull List<ITransformer> transformers() {
    return Collections.emptyList();
  }
}
