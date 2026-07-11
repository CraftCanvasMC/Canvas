package io.canvasmc.canvas.util;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.jetbrains.annotations.UnknownNullability;
import org.jspecify.annotations.Nullable;

/**
 * A wrapper of a generic type {@code E} that is wrapped in a reentrant read write lock for all operations
 *
 * @param <E>
 *     the generic type element
 *
 * @author dueris
 */
public class ReadWriteLockedReference<E> {
    private final ReadWriteLock rwlock = new ReentrantReadWriteLock(true);

    @UnknownNullability
    private E value;

    public ReadWriteLockedReference(final @Nullable E default_) {
        this.value = default_;
    }

    public ReadWriteLockedReference() {
        this(null);
    }

    /**
     * Gets this reference as an {@link java.util.Optional}, returning an empty optional if the value is null when
     * calling
     *
     * @return the reference wrapped as an optional
     */
    public Optional<E> asOptional() {
        return Optional.ofNullable(getValue());
    }

    /**
     * Gets if the value currently is set
     *
     * @return {@code true} if the value is set, {@code false} otherwise
     *
     * @apiNote Holds the read lock
     */
    public boolean isSet() {
        rwlock.readLock().lock();
        try {
            return value != null;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    /**
     * If the value is present it executes the consumer {@code ifPresent}, otherwise if the {@code orElse} runnable is
     * nonnull then that gets run
     *
     * @param ifPresent
     *     the consumer to run if the value is present
     * @param orElse
     *     the runnable to run if the value is not present
     *
     * @apiNote Holds the read lock
     */
    public void runIfPresentOrElse(final Consumer<E> ifPresent, final @Nullable Runnable orElse) {
        rwlock.readLock().lock();
        try {
            if (value != null) {
                ifPresent.accept(value);
            }
            else if (orElse != null) orElse.run();
        } finally {
            rwlock.readLock().unlock();
        }
    }

    /**
     * Sets the value to {@code null} via {@link io.canvasmc.canvas.util.LockedReference#swapValue(UnaryOperator)}
     */
    public void unset() {
        swapValue((_) -> null);
    }

    /**
     * Sets the value to the new value provided
     *
     * @param newValue
     *     the new value
     */
    public void swapValue(final @Nullable E newValue) {
        swapValue((_) -> newValue);
    }

    /**
     * Sets the value to the new value via a unary operator. The returned value from the operator MUST be a new object
     *
     * @param operator
     *     the operator to apply, the returned value can be {@code null}
     *
     * @apiNote Holds the write lock
     */
    public void swapValue(final UnaryOperator<@Nullable E> operator) {
        rwlock.writeLock().lock();
        try {
            value = operator.apply(value);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    /**
     * Sets the value to the new value via a unary operator. The returned value from the operator MUST be a new object
     *
     * @param operator
     *     the operator to apply, the returned value can be {@code null}
     *
     * @return the new value
     *
     * @apiNote Holds the write lock
     */
    public E swapAndGet(final UnaryOperator<E> operator) {
        rwlock.writeLock().lock();
        try {
            return value = operator.apply(value);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    /**
     * Gets the current value
     *
     * @return the value
     *
     * @apiNote Holds the read lock
     */
    @UnknownNullability
    public E getValue() {
        rwlock.readLock().lock();
        try {
            return value;
        } finally {
            rwlock.readLock().unlock();
        }
    }
}
