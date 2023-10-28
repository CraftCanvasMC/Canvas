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
package space.vectrix.ignite.launch.event;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import net.kyori.event.EventSubscriber;
import net.kyori.event.PostResult;
import net.kyori.event.SimpleEventBus;
import net.kyori.event.method.MethodScanner;
import net.kyori.event.method.MethodSubscriptionAdapter;
import net.kyori.event.method.SimpleMethodSubscriptionAdapter;
import net.kyori.event.method.asm.ASMEventExecutorFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import space.vectrix.ignite.api.event.EventHandler;
import space.vectrix.ignite.api.event.EventManager;
import space.vectrix.ignite.api.event.PostPriority;
import space.vectrix.ignite.api.event.Subscribe;
import space.vectrix.ignite.launch.IgnitePlatform;
import space.vectrix.ignite.launch.mod.ModClassLoader;

import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;

import static java.util.Objects.requireNonNull;

public final class IgniteEventManager implements EventManager {
  private final ListMultimap<Object, Object> registeredListenersByMod = Multimaps
    .synchronizedListMultimap(Multimaps.newListMultimap(new IdentityHashMap<>(), ArrayList::new));
  private final ListMultimap<Object, EventHandler<?>> registeredHandlersByMod = Multimaps
    .synchronizedListMultimap(Multimaps.newListMultimap(new IdentityHashMap<>(), ArrayList::new));
  private final SimpleEventBus<Object> bus;
  private final MethodSubscriptionAdapter<Object> methodAdapter;
  private final IgnitePlatform platform;

  public IgniteEventManager(final @NonNull IgnitePlatform platform) {
    // Add event executors to the mod class loader.
    final ModClassLoader classLoader = AccessController.doPrivileged((PrivilegedAction<ModClassLoader>) () -> new ModClassLoader(new URL[0]));
    classLoader.addLoaders();

    this.platform = platform;

    this.bus = new SimpleEventBus<Object>(Object.class) {
      @Override
      protected final boolean shouldPost(final @NonNull Object event, final @NonNull EventSubscriber<?> subscriber) {
        return true;
      }
    };

    this.methodAdapter = new SimpleMethodSubscriptionAdapter<>(
      bus,
      new ASMEventExecutorFactory<>(classLoader),
      new IgniteMethodScanner()
    );
  }

  @Override
  public void register(final @NonNull Object mod, final @NonNull Object listener) {
    requireNonNull(mod, "mod");
    requireNonNull(listener, "listener");

    this.ensureMod(mod);

    if (mod == listener && this.registeredListenersByMod.containsEntry(mod, mod)) {
      throw new IllegalArgumentException("The mod instance if automatically registered!");
    }

    this.registeredListenersByMod.put(mod, listener);
    this.methodAdapter.register(listener);
  }

  @Override
  public <E> void register(final @NonNull Object mod,
                           final @NonNull Class<E> event,
                           final @NonNull PostPriority priority,
                           final @NonNull EventHandler<E> handler) {
    requireNonNull(mod, "mod");
    requireNonNull(event, "event");
    requireNonNull(priority, "priority");
    requireNonNull(handler, "handler");

    this.ensureMod(mod);

    this.registeredHandlersByMod.put(mod, handler);
    this.bus.register(event, new KyoriToIgniteHandler<>(handler, priority));
  }

  @Override
  public void unregister(final @NonNull Object mod) {
    requireNonNull(mod, "mod");

    this.ensureMod(mod);

    final Collection<Object> listeners = this.registeredListenersByMod.removeAll(mod);
    final Collection<EventHandler<?>> handlers = this.registeredHandlersByMod.removeAll(mod);
    listeners.forEach(this.methodAdapter::unregister);
    handlers.forEach(this::unregisterHandler);
  }

  @Override
  public void unregister(final @NonNull Object mod, final @NonNull Object listener) {
    requireNonNull(mod, "mod");
    requireNonNull(listener, "listener");

    this.ensureMod(mod);

    if (this.registeredListenersByMod.remove(mod, listener)) {
      this.methodAdapter.unregister(listener);
    }
  }

  @Override
  public <E> void unregister(final @NonNull Object mod, final @NonNull EventHandler<E> handler) {
    requireNonNull(mod, "mod");
    requireNonNull(handler, "handler");

    this.ensureMod(mod);

    if (this.registeredHandlersByMod.remove(mod, handler)) {
      this.unregisterHandler(handler);
    }
  }

  @Override
  public void post(final @NonNull Object event) {
    requireNonNull(event, "event");

    if (!this.bus.hasSubscribers(event.getClass())) return;

    final PostResult result = this.bus.post(event);
    if (!result.exceptions().isEmpty()) {
      this.platform.getLogger().error("An error occurred attempting to post an event '" + event + "'!");

      int i = 0;
      for (final Throwable throwable : result.exceptions().values()) {
        this.platform.getLogger().error("#{}: \n", ++i, throwable);
      }
    }
  }

  private void unregisterHandler(final EventHandler<?> handler) {
    this.bus.unregister(subscriber -> subscriber instanceof KyoriToIgniteHandler && ((KyoriToIgniteHandler<?>) subscriber).handler == handler);
  }

  private void ensureMod(final Object mod) {
    if (!this.platform.getModManager().isInstance(mod)) throw new IllegalArgumentException("Specified mod is not loaded!");
  }

  /* package */ static final class IgniteMethodScanner implements MethodScanner<Object> {
    @Override
    public final boolean shouldRegister(final @NonNull Object listener, final @NonNull Method method) {
      return method.isAnnotationPresent(Subscribe.class);
    }

    @Override
    public final int postOrder(final @NonNull Object listener, final @NonNull Method method) {
      return method.getAnnotation(Subscribe.class).priority().ordinal();
    }

    @Override
    public final boolean consumeCancelledEvents(final @NonNull Object listener, final @NonNull Method method) {
      return true;
    }
  }

  /* package */ static final class KyoriToIgniteHandler<E> implements EventSubscriber<E> {
    private final EventHandler<E> handler;
    private final int priority;

    /* package */ KyoriToIgniteHandler(final @NonNull EventHandler<E> handler, final @NonNull PostPriority priority) {
      this.handler = handler;
      this.priority = priority.ordinal();
    }

    @Override
    public void invoke(final @NonNull E event) {
      handler.execute(event);
    }

    @Override
    public final int postOrder() {
      return this.priority;
    }
  }
}
