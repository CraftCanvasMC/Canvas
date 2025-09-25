package io.canvasmc.canvas.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;

// Note: formatted print is in syntax of a yaml file
//   for easier readability when logged to console
public record GsonTextFormatter(String indent, int offset) {

    private static final TextColor KEY_COLOR = TextColor.color(0x5DBBEA);
    private static final TextColor STRING_COLOR = TextColor.color(0x4EF4E2);
    private static final TextColor NUMBER_COLOR = TextColor.color(0xC71FFF);
    private static final TextColor BOOLEAN_COLOR = TextColor.color(0x49CD22);
    private static final TextColor NULL_COLOR = TextColor.color(0x49CD22);

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    public GsonTextFormatter(int indentSize) {
        this(" ".repeat(indentSize), 0);
    }

    public Component apply(String jsonText) {
        JsonElement root = JsonParser.parseString(jsonText);
        return format(root);
    }

    private Component format(JsonElement element) {
        if (element.isJsonNull()) {
            return Component.text("null").color(NULL_COLOR);
        } else if (element.isJsonObject()) {
            return formatObject(element.getAsJsonObject());
        } else if (element.isJsonArray()) {
            return formatArray(element.getAsJsonArray());
        } else { // primitive
            return formatPrimitive(element.getAsJsonPrimitive());
        }
    }

    private @NotNull Component formatObject(@NotNull JsonObject obj) {
        Component result = Component.empty();

        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            Component keyComp = Component.text(indent.repeat(offset) + entry.getKey() + ": ").color(KEY_COLOR);
            JsonElement value = entry.getValue();

            if (value.isJsonObject() || value.isJsonArray()) {
                result = result.append(keyComp.appendNewline());
                result = result.append(new GsonTextFormatter(indent, offset + 1).format(value));
            } else {
                Component valueComp = new GsonTextFormatter(indent, offset + 1).format(value);
                result = result.append(keyComp.append(valueComp));
            }

            result = result.appendNewline();
        }

        return result;
    }

    private @NotNull Component formatArray(@NotNull JsonArray array) {
        Component result = Component.empty();

        for (JsonElement item : array) {
            Component dash = Component.text(indent.repeat(offset) + "- ");
            Component content = new GsonTextFormatter(indent, offset + 1).format(item);

            if (item.isJsonObject() || item.isJsonArray()) {
                result = result.append(dash).appendNewline().append(content);
            } else {
                result = result.append(dash.append(content));
            }

            result = result.appendNewline();
        }

        return result;
    }

    private @NotNull Component formatPrimitive(@NotNull JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return Component.text(primitive.getAsBoolean() + "").color(BOOLEAN_COLOR);
        } else if (primitive.isNumber()) {
            return Component.text(primitive.getAsNumber().toString()).color(NUMBER_COLOR);
        } else if (primitive.isString()) {
            return Component.text("\"" + primitive.getAsString() + "\"").color(STRING_COLOR);
        } else {
            return Component.text(primitive.toString()).color(NULL_COLOR);
        }
    }
}
