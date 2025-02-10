package io.canvasmc.canvas.dfc.common.gen;

import com.google.common.base.Suppliers;
import io.canvasmc.canvas.dfc.common.ducks.IBlendingAwareVisitor;
import io.canvasmc.canvas.dfc.common.ducks.IFastCacheLike;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.DensityFunction;

public class CompiledDensityFunction extends SubCompiledDensityFunction {
    private final CompiledEntry compiledEntry;

    public CompiledDensityFunction(CompiledEntry compiledEntry, DensityFunction blendingFallback) {
        super(compiledEntry, compiledEntry, blendingFallback);
        this.compiledEntry = (CompiledEntry)Objects.requireNonNull(compiledEntry);
    }

    private CompiledDensityFunction(CompiledEntry compiledEntry, Supplier<DensityFunction> blendingFallback) {
        super(compiledEntry, compiledEntry, blendingFallback);
        this.compiledEntry = (CompiledEntry)Objects.requireNonNull(compiledEntry);
    }

    public DensityFunction mapAll(Visitor visitor) {
        if (visitor instanceof IBlendingAwareVisitor blendingAwareVisitor) {
            if (blendingAwareVisitor.c2me$isBlendingEnabled()) {
                DensityFunction fallback1 = this.getFallback();
                if (fallback1 == null) {
                    throw new IllegalStateException("blendingFallback is no more");
                }

                return fallback1.mapAll(visitor);
            }
        }

        boolean modified = false;
        List<Object> args = this.compiledEntry.getArgs();
        ListIterator<Object> iterator = args.listIterator();

        Object next;
        while(iterator.hasNext()) {
            next = iterator.next();
            if (next instanceof DensityFunction df) {
                if (!(df instanceof IFastCacheLike)) {
                    DensityFunction applied = df.mapAll(visitor);
                    if (df != applied) {
                        iterator.set(applied);
                        modified = true;
                    }
                }
            }

            if (next instanceof NoiseHolder noise) {
                NoiseHolder applied = visitor.visitNoise(noise);
                if (noise != applied) {
                    iterator.set(applied);
                    modified = true;
                }
            }
        }

        iterator = args.listIterator();

        while(iterator.hasNext()) {
            next = iterator.next();
            if (next instanceof IFastCacheLike cacheLike) {
                DensityFunction applied = visitor.apply(cacheLike);
                if (applied == cacheLike.c2me$getDelegate()) {
                    iterator.set((Object)null);
                    modified = true;
                } else {
                    if (!(applied instanceof IFastCacheLike)) {
                        throw new UnsupportedOperationException("Unsupported transformation on Wrapping node");
                    }

                    IFastCacheLike newCacheLike = (IFastCacheLike)applied;
                    iterator.set(newCacheLike);
                    modified = true;
                }
            }
        }

        Supplier<DensityFunction> fallback = this.blendingFallback != null ? Suppliers.memoize(() -> {
            DensityFunction densityFunction = (DensityFunction)this.blendingFallback.get();
            return densityFunction != null ? densityFunction.mapAll(visitor) : null;
        }) : null;
        if (fallback != this.blendingFallback) {
            modified = true;
        }

        if (modified) {
            return new CompiledDensityFunction(this.compiledEntry.newInstance(args), fallback);
        } else {
            return this;
        }
    }
}
