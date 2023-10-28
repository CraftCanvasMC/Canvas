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
package space.vectrix.ignite.api.event;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Provides access to manage event listeners for event handlers.
 *
 * @since 0.5.0
 */
public interface EventManager {
  /**
   * Registers the specified {@link Object} listener for the specified
   * {@link Object} mod instance.
   *
   * @param mod the mod instance
   * @param listener the event listener
   * @since 0.5.0
   */
  void register(final @NonNull Object mod, final @NonNull Object listener);

  /**
   * Registers the specified {@link EventHandler} handler and {@link Class} event,
   * for the specified {@link Object} mod instance.
   *
   * @param mod the mod instance
   * @param event the event class
   * @param handler the event handler
   * @param <E> the event type
   * @since 0.5.0
   */
  default <E> void register(final @NonNull Object mod, final @NonNull Class<E> event, final @NonNull EventHandler<E> handler) {
    this.register(mod, event, PostPriority.NORMAL, handler);
  }

  /**
   * Registers the specified {@link EventHandler} handler and {@link Class} event,
   * under the {@link PostPriority}, for the specified {@link Object} mod instance.
   *
   * @param mod the mod instance
   * @param event the event class
   * @param priority the post priority
   * @param handler the event handler
   * @param <E> the event type
   * @since 0.5.0
   */
  <E> void register(final @NonNull Object mod, final @NonNull Class<E> event, final @NonNull PostPriority priority, final @NonNull EventHandler<E> handler);

  /**
   * Unregisters all listeners for the specified {@link Object} mod instance.
   *
   * @param mod the mod instance
   * @since 0.5.0
   */
  void unregister(final @NonNull Object mod);

  /**
   * Unregisters the specified {@link Object} listener, for the specified
   * {@link Object} mod instance.
   *
   * @param mod the mod instance
   * @param listener the event listener
   * @since 0.5.0
   */
  void unregister(final @NonNull Object mod, final @NonNull Object listener);

  /**
   * Unregisters the specified {@link EventHandler} listener, for the specified
   * {@link Object} mod instance.
   *
   * @param mod the mod instance
   * @param handler the event handler
   * @param <E> the event type
   * @since 0.5.0
   */
  <E> void unregister(final @NonNull Object mod, final @NonNull EventHandler<E> handler);

  /**
   * Posts the specified {@link Object} event to the event bus.
   *
   * <p>Any errors thrown from handling events will be handled.</p>
   *
   * @param event the event
   * @since 0.5.0
   */
  void post(final @NonNull Object event);
}
