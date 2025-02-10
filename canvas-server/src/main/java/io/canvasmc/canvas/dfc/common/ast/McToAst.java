package io.canvasmc.canvas.dfc.common.ast;

import io.canvasmc.canvas.dfc.common.ast.binary.AddNode;
import io.canvasmc.canvas.dfc.common.ast.binary.MaxNode;
import io.canvasmc.canvas.dfc.common.ast.binary.MaxShortNode;
import io.canvasmc.canvas.dfc.common.ast.binary.MinNode;
import io.canvasmc.canvas.dfc.common.ast.binary.MinShortNode;
import io.canvasmc.canvas.dfc.common.ast.binary.MulNode;
import io.canvasmc.canvas.dfc.common.ast.misc.CacheLikeNode;
import io.canvasmc.canvas.dfc.common.ast.misc.ConstantNode;
import io.canvasmc.canvas.dfc.common.ast.misc.DelegateNode;
import io.canvasmc.canvas.dfc.common.ast.misc.RangeChoiceNode;
import io.canvasmc.canvas.dfc.common.ast.misc.YClampedGradientNode;
import io.canvasmc.canvas.dfc.common.ast.noise.DFTNoiseNode;
import io.canvasmc.canvas.dfc.common.ast.noise.DFTShiftANode;
import io.canvasmc.canvas.dfc.common.ast.noise.DFTShiftBNode;
import io.canvasmc.canvas.dfc.common.ast.noise.DFTShiftNode;
import io.canvasmc.canvas.dfc.common.ast.noise.DFTWeirdScaledSamplerNode;
import io.canvasmc.canvas.dfc.common.ast.noise.ShiftedNoiseNode;
import io.canvasmc.canvas.dfc.common.ast.spline.SplineAstNode;
import io.canvasmc.canvas.dfc.common.ast.unary.AbsNode;
import io.canvasmc.canvas.dfc.common.ast.unary.CubeNode;
import io.canvasmc.canvas.dfc.common.ast.unary.NegMulNode;
import io.canvasmc.canvas.dfc.common.ast.unary.SquareNode;
import io.canvasmc.canvas.dfc.common.ast.unary.SqueezeNode;
import io.canvasmc.canvas.dfc.common.ducks.IEqualityOverriding;
import io.canvasmc.canvas.dfc.common.ducks.IFastCacheLike;
import io.canvasmc.canvas.dfc.common.vif.AstVanillaInterface;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class McToAst {
    public McToAst() {
    }

    public static AstNode toAst(DensityFunction df) {
        Objects.requireNonNull(df);
        return switch (df) {
            case AstVanillaInterface f -> f.getAstNode();

            case NoiseChunk.BlendAlpha f -> new ConstantNode(1.0);
            case NoiseChunk.BlendOffset f -> new ConstantNode(0.0);
            case DensityFunctions.BlendAlpha f -> new ConstantNode(1.0);
            case DensityFunctions.BlendOffset f -> new ConstantNode(0.0);
            case DensityFunctions.TwoArgumentSimpleFunction f -> switch (f.type()) {
                case ADD -> new AddNode(toAst(f.argument1()), toAst(f.argument2()));
                case MUL -> new MulNode(toAst(f.argument1()), toAst(f.argument2()));
                case MIN -> {
                    double rightMin = f.argument2().minValue();
                    if (f.argument1().minValue() < rightMin) {
                        yield new MinShortNode(toAst(f.argument1()), toAst(f.argument2()), rightMin);
                    } else {
                        yield new MinNode(toAst(f.argument1()), toAst(f.argument2()));
                    }
                }
                case MAX -> {
                    double rightMax = f.argument2().maxValue();
                    if (f.argument1().maxValue() > rightMax) {
                        yield new MaxShortNode(toAst(f.argument1()), toAst(f.argument2()), rightMax);
                    } else {
                        yield new MaxNode(toAst(f.argument1()), toAst(f.argument2()));
                    }
                }
            };
            case DensityFunctions.BlendDensity f -> toAst(f.input());
            case DensityFunctions.Clamp f -> new MaxNode(new ConstantNode(f.minValue()), new MinNode(new ConstantNode(f.maxValue()), toAst(f.input())));
            case DensityFunctions.Constant f -> new ConstantNode(f.value());
            case DensityFunctions.HolderHolder f -> toAst(f.function().value());
            case DensityFunctions.Mapped f -> switch (f.type()) {
                case ABS -> new AbsNode(toAst(f.input()));
                case SQUARE -> new SquareNode(toAst(f.input()));
                case CUBE -> new CubeNode(toAst(f.input()));
                case HALF_NEGATIVE -> new NegMulNode(toAst(f.input()), 0.5);
                case QUARTER_NEGATIVE -> new NegMulNode(toAst(f.input()), 0.25);
                case SQUEEZE -> new SqueezeNode(toAst(f.input()));
            };
            case DensityFunctions.RangeChoice f -> new RangeChoiceNode(toAst(f.input()), f.minInclusive(), f.maxExclusive(), toAst(f.whenInRange()), toAst(f.whenOutOfRange()));
            case DensityFunctions.Marker f -> {
                DensityFunctions.Marker wrapping = new DensityFunctions.Marker(f.type(), new AstVanillaInterface(toAst(f.wrapped()), null));
                ((IEqualityOverriding) (Object) wrapping).c2me$overrideEquality(wrapping);
                yield new DelegateNode(wrapping);
            }
            case IFastCacheLike f -> new CacheLikeNode(f, toAst(f.c2me$getDelegate()));
            case DensityFunctions.ShiftedNoise f -> new ShiftedNoiseNode(toAst(f.shiftX()), toAst(f.shiftY()), toAst(f.shiftZ()), f.xzScale(), f.yScale(), f.noise());
            case DensityFunctions.Noise f -> new DFTNoiseNode(f.noise(), f.xzScale(), f.yScale());
            case DensityFunctions.Shift f -> new DFTShiftNode(f.offsetNoise());
            case DensityFunctions.ShiftA f -> new DFTShiftANode(f.offsetNoise());
            case DensityFunctions.ShiftB f -> new DFTShiftBNode(f.offsetNoise());
            case DensityFunctions.YClampedGradient f -> new YClampedGradientNode(f.fromY(), f.toY(), f.fromValue(), f.toValue());
            case DensityFunctions.WeirdScaledSampler f -> new DFTWeirdScaledSamplerNode(toAst(f.input()), f.noise(), f.rarityValueMapper());
            case DensityFunctions.Spline f -> new SplineAstNode(f.spline());

            default -> {
//                delegateStatistics.computeIfAbsent(df.getClass(), unused -> new LongAdder()).increment();;
                yield new DelegateNode(df);
            }
        };
    }

    public static @NotNull DensityFunction wrapVanilla(DensityFunction densityFunction) {
        return new AstVanillaInterface(toAst(densityFunction), densityFunction);
    }
}
