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
package space.vectrix.ignite.applaunch.util;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.objectweb.asm.Opcodes;

public final class IgniteConstants {
  public static final int ASM_VERSION = Opcodes.ASM9;

  public static final @NonNull String MOD_CONFIG = "canvas.mod.json"; // Canvas

  public static final @NonNull String IGNITE_LAUNCH_SERVICE = "ignitelaunch";
  public static final @NonNull String IGNITE_TRANSFORMATION_SERVICE = "ignitetransformer";
  public static final @NonNull String ACCESS_WIDENER_SERVICE = "access_widener";
  public static final @NonNull String MIXIN_SERVICE = "mixin";

  public static final @NonNull String MANIFEST = "MANIFEST.MF";
  public static final @NonNull String META_INF = "META-INF";
  public static final @NonNull String ACCESS_WIDENER = "AccessWidener";

  public static final @NonNull String ACCESS_WIDENER_EXTENSION = "accesswidener";

  private IgniteConstants() {}
}
