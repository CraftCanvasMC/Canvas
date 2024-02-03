package space.vectrix.ignite.util;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides static access to collection utilities.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class IgniteCollections {
  /**
   * Returns a {@link Stream} of the given {@link Iterable}.
   *
   * @param iterable the iterable
   * @param <T> the type
   * @return a stream
   * @since 1.0.0
   */
  public static <T> @NotNull Stream<T> stream(final @NotNull Iterable<T> iterable) {
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  /**
   * Returns the first element in the given {@link Iterator} or {@code null} if
   * the iterator is empty.
   *
   * @param iterator the iterator
   * @param <T> the type
   * @return the first element if present
   * @since 1.0.0
   */
  public static <T> @Nullable T firstOrNull(final @NotNull Iterator<? extends T> iterator) {
    return iterator.hasNext() ? iterator.next() : null;
  }

  private IgniteCollections() {
  }
}
