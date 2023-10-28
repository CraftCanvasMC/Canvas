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
package space.vectrix.ignite.api.service;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * A singleton instance of this, loaded by the bootstrapper to manipulate
 * launch properties before they occur.
 *
 * @since 0.5.0
 */
public interface IBootstrapService {
  /**
   * The bootstrap service name.
   *
   * @return the name
   * @since 0.5.0
   */
  @NonNull String name();

  /**
   * Returns {@code true} if the service is in the right environment
   * to be used, otherwise returns {@code false}.
   *
   * @return whether the service is in a valid environment
   * @since 0.5.0
   */
  boolean validate();

  /**
   * Executes the underlying functions to manipulate the launch properties.
   *
   * @throws Throwable any errors from the functions
   * @since 0.5.0
   */
  void execute() throws Throwable;
}
