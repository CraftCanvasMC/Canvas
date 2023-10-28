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

public final class IgniteExclusions {
  public static final String[] TRANSFORMATION_EXCLUDED_PATHS = {
    "org/spongepowered/asm/"
  };

  public static final String[] TRANSFORMATION_EXCLUDED_PACKAGES = {
    "space.vectrix.ignite.api.",
    "space.vectrix.ignite.applaunch.",

    // Mixin
    "org.spongepowered.asm.",

    // Logging
    "org.apache.logging.log4j.",
    "org.jline.",
    "org.fusesource.",
    "net.minecrell.terminalconsole.",

    // Configuration
    "io.leangen.geantyref.",
    "org.spongepowered.configurate.",

    // Guice
    "com.google.inject."
  };

  public static final String[] RESOURCE_EXCLUDED_PATHS = {
    "net/minecraft",
    "it/unimi"
  };
}
