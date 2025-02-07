package io.canvasmc.canvas.config;

import com.google.common.base.Strings;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.Yaml;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;

public class YamlTextFormatter {

    private static final ChatFormatting KEY_COLOR = ChatFormatting.AQUA;
    private static final ChatFormatting STRING_COLOR = ChatFormatting.GREEN;
    private static final ChatFormatting NUMBER_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting BOOLEAN_COLOR = ChatFormatting.BLUE;

    private final String indent;
    private final int offset;

    public YamlTextFormatter(char indent, int size) {
        this.indent = Strings.repeat(String.valueOf(indent), size);
        this.offset = 0;
    }

    public YamlTextFormatter(int size) {
        this(' ', size);
    }

    public Component apply(String yamlString) {
        Yaml yaml = new Yaml();
        Object yamlData = yaml.load(yamlString);
        return applyInternal(yamlData, 0);
    }

    private Component applyInternal(Object data, int depth) {
        if (data instanceof Map<?, ?> map) {
            return visitMap(map, depth);
        } else if (data instanceof List<?> list) {
            return visitList(list, depth);
        } else {
            return visitValue(data);
        }
    }

    private Component visitMap(@NotNull Map<?, ?> map, int depth) {
        MutableComponent result = Component.literal("");
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            Component key = Component.literal(entry.getKey().toString()).withStyle(KEY_COLOR);
            Component value = applyInternal(entry.getValue(), depth + 1);
            result.append(Strings.repeat(indent, depth)).append(key).append(": ").append(value);
            if (iterator.hasNext()) {
                result.append("\n");
            }
        }

        return result;
    }

    private Component visitList(@NotNull List<?> list, int depth) {
        MutableComponent result = Component.literal("");
        Iterator<?> iterator = list.iterator();

        while (iterator.hasNext()) {
            Component value = applyInternal(iterator.next(), depth + 1);
            result.append(Strings.repeat(indent, depth)).append("- ").append(value);
            if (iterator.hasNext()) {
                result.append("\n");
            }
        }

        return result;
    }

    private Component visitValue(@NotNull Object value) {
        if (value instanceof Boolean bool) {
            return Component.literal(bool.toString()).withStyle(BOOLEAN_COLOR);
        } else if (value instanceof Number num) {
            return Component.literal(num.toString()).withStyle(NUMBER_COLOR);
        } else {
            return Component.literal(value.toString()).withStyle(STRING_COLOR);
        }
    }
}
