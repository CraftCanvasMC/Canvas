package io.canvasmc.canvas.item.components;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.NonNull;

public class AttributeModifiersComponent extends ComponentType<ItemAttributeModifiers> {

    private static final Map<String, FieldInfo> ENTRY_FIELDS = Map.of(
        "attribute", FieldInfo.identifierField(ctx -> BuiltInRegistries.ATTRIBUTE.keySet()),
        "amount", FieldInfo.floatField(),
        "operation", FieldInfo.dynamicStringField(ctx ->
            Arrays.stream(AttributeModifier.Operation.values())
                .map(o -> o.name().toLowerCase())
                .toList()
        ),
        "slot_group", FieldInfo.dynamicStringField(ctx ->
            Arrays.stream(EquipmentSlotGroup.values())
                .map(EquipmentSlotGroup::getSerializedName)
                .toList()
        )
    );

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "modifiers", FieldInfo.objectListField(ENTRY_FIELDS)
        );
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final @NonNull SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public ItemAttributeModifiers parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,{:])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)(?=[\\s,}\\]])",
                "$1\"$2\""
            );
            JsonElement json = GSON.fromJson(fixed, JsonElement.class);
            JsonArray array = json.getAsJsonObject().getAsJsonArray("modifiers");

            List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();
            int i = 0;
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();

                Identifier attributeId = Identifier.parse(obj.get("attribute").getAsString());
                Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
                    .get(attributeId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown attribute: " + attributeId));

                double amount = obj.get("amount").getAsDouble();

                AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(
                    obj.get("operation").getAsString().toUpperCase()
                );

                EquipmentSlotGroup slotGroup = EquipmentSlotGroup.valueOf(
                    obj.get("slot_group").getAsString().toUpperCase()
                );

                entries.add(new ItemAttributeModifiers.Entry(
                    attribute,
                    new AttributeModifier(
                        Identifier.parse("canvas:modifier_" + attributeId.getPath() + "_" + amount + "_" + i),
                        amount,
                        operation
                    ),
                    slotGroup
                ));
                i++;
            }

            return new ItemAttributeModifiers(entries);
        } catch (Throwable thrown) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(thrown.getMessage());
        }
    }

    @Override
    public DataComponentType<ItemAttributeModifiers> nms() {
        return DataComponents.ATTRIBUTE_MODIFIERS;
    }
}
