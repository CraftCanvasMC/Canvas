package io.canvasmc.canvas.util;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class NbtTextFormatter {

    private static final ChatFormatting BRACE_COLOR = ChatFormatting.WHITE;
    private static final ChatFormatting KEY_COLOR = ChatFormatting.AQUA;
    private static final ChatFormatting STRING_COLOR = ChatFormatting.GREEN;
    private static final ChatFormatting NUMBER_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting SUFFIX_COLOR = ChatFormatting.RED;
    private static final ChatFormatting BOOLEAN_COLOR = ChatFormatting.LIGHT_PURPLE;
    private static final ChatFormatting NULL_COLOR = ChatFormatting.DARK_GRAY;
    private static final ChatFormatting BRACKET_COLOR = ChatFormatting.YELLOW;

    private final String indent;
    private final int offset;

    private static @NotNull String quoteIfNeeded(@NotNull String key) {
        if (key.matches("[A-Za-z0-9_+\\-.]+")) {
            return key;
        }
        return "\"" + escape(key) + "\"";
    }

    private static @NotNull String escape(@NotNull String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public NbtTextFormatter(int indentSize) {
        this(" ".repeat(indentSize), 0);
    }

    public NbtTextFormatter(String indent, int offset) {
        this.indent = indent;
        this.offset = offset;
    }

    public Component apply(@NotNull CompoundTag tag) {
        return formatCompound(tag);
    }

    private Component format(@NonNull Tag tag) {
        switch (tag) {
            case EndTag endTag -> {
                return Component.literal("END").withStyle(NULL_COLOR);
            }
            case CompoundTag compound -> {
                return formatCompound(compound);
            }
            case ListTag list -> {
                return formatList(list);
            }
            case ByteArrayTag byteArray -> {
                return formatByteArray(byteArray);
            }
            case IntArrayTag intArray -> {
                return formatIntArray(intArray);
            }
            case LongArrayTag longArray -> {
                return formatLongArray(longArray);
            }
            case StringTag stringTag -> {
                return formatString(stringTag.value());
            }
            case ByteTag byteTag -> {
                byte val = byteTag.byteValue();
                return numberWithSuffix(Byte.toString(val), "B");
            }
            case ShortTag shortTag -> {
                return numberWithSuffix(Short.toString(shortTag.shortValue()), "");
            }
            case IntTag intTag -> {
                return Component.literal(Integer.toString(intTag.intValue())).withStyle(NUMBER_COLOR);
            }
            case LongTag longTag -> {
                return numberWithSuffix(Long.toString(longTag.longValue()), "L");
            }
            case FloatTag floatTag -> {
                return numberWithSuffix(Float.toString(floatTag.floatValue()), "F");
            }
            case DoubleTag doubleTag -> {
                return numberWithSuffix(Double.toString(doubleTag.doubleValue()), "D");
            }
            case NumericTag numericTag -> {
                return Component.literal(numericTag.toString()).withStyle(NUMBER_COLOR);
            }
            default -> {
                return Component.literal(tag.toString()).withStyle(NULL_COLOR);
            }
        }
    }

    private @NotNull Component formatCompound(@NotNull CompoundTag compound) {
        MutableComponent result = Component.empty();

        List<String> keys = compound.keySet().stream().sorted().toList();

        result.append(Component.literal("{").withStyle(BRACE_COLOR));

        if (keys.isEmpty()) {
            return result.append(Component.literal("}").withStyle(BRACE_COLOR));
        }

        result.append(Component.literal("\n"));

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Tag value = compound.get(key);

            String indentStr = indent.repeat(offset + 1);
            MutableComponent keyComp = Component.literal(indentStr + quoteIfNeeded(key) + ": ").withStyle(KEY_COLOR);

            MutableComponent line = Component.empty().append(keyComp);

            if (value instanceof CompoundTag || value instanceof ListTag
                || value instanceof ByteArrayTag || value instanceof IntArrayTag
                || value instanceof LongArrayTag) {
                line.append(new NbtTextFormatter(indent, offset + 1).format(value));
            }
            else if (value != null) {
                line.append(format(value));
            }

            if (i < keys.size() - 1) {
                line.append(Component.literal(",").withStyle(BRACE_COLOR));
            }

            result.append(line);
            result.append(Component.literal("\n"));
        }

        result.append(Component.literal(indent.repeat(offset) + "}").withStyle(BRACE_COLOR));
        return result;
    }

    private @NotNull Component formatList(@NotNull ListTag list) {
        MutableComponent result = Component.empty();
        result.append(Component.literal("[").withStyle(BRACKET_COLOR));

        if (list.isEmpty()) {
            return result.append(Component.literal("]").withStyle(BRACKET_COLOR));
        }

        result.append(Component.literal("\n"));

        for (int i = 0; i < list.size(); i++) {
            Tag item = list.get(i);
            String indentStr = indent.repeat(offset + 1);

            MutableComponent line = Component.literal(indentStr);

            if (item instanceof CompoundTag || item instanceof ListTag
                || item instanceof ByteArrayTag || item instanceof IntArrayTag
                || item instanceof LongArrayTag) {
                line.append(new NbtTextFormatter(indent, offset + 1).format(item));
            }
            else {
                line.append(format(item));
            }

            if (i < list.size() - 1) {
                line.append(Component.literal(",").withStyle(BRACKET_COLOR));
            }

            result.append(line);
            result.append(Component.literal("\n"));
        }

        result.append(Component.literal(indent.repeat(offset) + "]").withStyle(BRACKET_COLOR));
        return result;
    }

    private @NotNull Component formatByteArray(@NotNull ByteArrayTag tag) {
        MutableComponent result = Component.empty();
        result.append(Component.literal("[B; ").withStyle(BRACKET_COLOR));

        byte[] bytes = tag.getAsByteArray();
        for (int i = 0; i < bytes.length; i++) {
            result.append(numberWithSuffix(Byte.toString(bytes[i]), "b"));
            if (i < bytes.length - 1) {
                result.append(Component.literal(", ").withStyle(BRACKET_COLOR));
            }
        }

        result.append(Component.literal("]").withStyle(BRACKET_COLOR));
        return result;
    }

    private @NotNull Component formatIntArray(@NotNull IntArrayTag tag) {
        MutableComponent result = Component.empty();
        result.append(Component.literal("[I; ").withStyle(BRACKET_COLOR));

        int[] ints = tag.getAsIntArray();
        for (int i = 0; i < ints.length; i++) {
            result.append(Component.literal(Integer.toString(ints[i])).withStyle(NUMBER_COLOR));
            if (i < ints.length - 1) {
                result.append(Component.literal(", ").withStyle(BRACKET_COLOR));
            }
        }

        result.append(Component.literal("]").withStyle(BRACKET_COLOR));
        return result;
    }

    private @NotNull Component formatLongArray(@NotNull LongArrayTag tag) {
        MutableComponent result = Component.empty();
        result.append(Component.literal("[L; ").withStyle(BRACKET_COLOR));

        long[] longs = tag.getAsLongArray();
        for (int i = 0; i < longs.length; i++) {
            result.append(numberWithSuffix(Long.toString(longs[i]), "L"));
            if (i < longs.length - 1) {
                result.append(Component.literal(", ").withStyle(BRACKET_COLOR));
            }
        }

        result.append(Component.literal("]").withStyle(BRACKET_COLOR));
        return result;
    }

    private @NotNull Component formatString(String s) {
        return Component.literal("\"" + escape(s) + "\"").withStyle(STRING_COLOR);
    }

    private @NotNull MutableComponent numberWithSuffix(String number, String suffix) {
        MutableComponent initial = Component.empty()
            .append(Component.literal(number).withStyle(NUMBER_COLOR));
        if (suffix != null && !suffix.isEmpty()) {
            initial.append(Component.literal(suffix).withStyle(SUFFIX_COLOR));
        }
        return initial;

    }
}
