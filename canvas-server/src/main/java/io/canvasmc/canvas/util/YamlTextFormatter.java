package io.canvasmc.canvas.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

public class YamlTextFormatter {

    private static final TextColor KEY_COLOR = NamedTextColor.AQUA;
    private static final TextColor STRING_COLOR = NamedTextColor.GREEN;
    private static final TextColor NUMBER_COLOR = NamedTextColor.GOLD;
    private static final TextColor BOOLEAN_COLOR = NamedTextColor.BLUE;
    private static final TextColor NULL_COLOR = NamedTextColor.DARK_GRAY;

    private final String indent;
    private final int offset;

    public YamlTextFormatter(int indentSize) {
        this(String.valueOf(' ').repeat(indentSize), 0);
    }

    public YamlTextFormatter(String indent, int offset) {
        this.indent = indent;
        this.offset = offset;
    }

    public Component apply(String yamlText) {
        Object data = new Yaml().load(yamlText);
        return format(data);
    }

    private Component format(Object obj) {
        if (obj == null) {
            return Component.text("null").color(NULL_COLOR);
        } else if (obj instanceof Map<?, ?> map) {
            return formatMap(map);
        } else if (obj instanceof List<?> list) {
            return formatList(list);
        } else {
            return formatScalar(obj);
        }
    }

    /* // without indent
    private @NotNull Component formatMap(@NotNull Map<?, ?> map) {
        Component result = Component.empty();

        List<?> list = new LinkedList<>(map.keySet());
        for (int i = 0; i < list.size(); i++) {
            Object key = list.get(i);
            Object value = map.get(key);

            Component keyComp = Component.text(indent.repeat(offset) + key + ": ").color(KEY_COLOR);

            if (value instanceof Map || value instanceof List) {
                result = result.append(keyComp.appendNewline());
                result = result.append(new YamlTextFormatter(indent, offset + 1).format(value));
            } else {
                Component valueComp = new YamlTextFormatter(indent, offset + 1).format(value);
                result = result.append(keyComp.append(valueComp));
            }

            if (!(i >= list.size() - 1)) {
                result = result.appendNewline();
            }
        }

        return result;
    }
     */ // with indent
    private @NotNull Component formatMap(@NotNull Map<?, ?> map) {
        Component result = Component.empty();

        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = map.get(key);

            Component keyComp = Component.text(indent.repeat(offset) + key + ": ").color(KEY_COLOR);

            if (value instanceof Map || value instanceof List) {
                result = result.append(keyComp.appendNewline());
                result = result.append(new YamlTextFormatter(indent, offset + 1).format(value));
            } else {
                Component valueComp = new YamlTextFormatter(indent, offset + 1).format(value);
                result = result.append(keyComp.append(valueComp));
            }

            result = result.appendNewline();
        }

        return result;
    }

    private @NotNull Component formatList(@NotNull List<?> list) {
        Component result = Component.empty();

        for (Object item : list) {
            Component dash = Component.text(indent.repeat(offset) + "- ");
            Component content = new YamlTextFormatter(indent, offset + 1).format(item);

            if (item instanceof Map || item instanceof List) {
                result = result.append(dash).appendNewline().append(content);
            } else {
                result = result.append(dash.append(content));
            }

            result = result.appendNewline();
        }

        return result;
    }

    private @NotNull Component formatScalar(Object obj) {
        if (obj instanceof Boolean b) {
            return Component.text(b.toString()).color(BOOLEAN_COLOR);
        } else if (obj instanceof Number n) {
            return Component.text(n.toString()).color(NUMBER_COLOR);
        } else if (obj instanceof String s) {
            return Component.text("\"" + s + "\"").color(STRING_COLOR);
        } else {
            return Component.text(obj.toString()).color(NULL_COLOR);
        }
    }
}
