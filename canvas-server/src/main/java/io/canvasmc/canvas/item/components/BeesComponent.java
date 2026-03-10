package io.canvasmc.canvas.item.components;

import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.jspecify.annotations.NonNull;

public class BeesComponent extends ComponentType<Bees> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "count", FieldInfo.intField(),
            "ticks_in_hive", FieldInfo.intField(),
            "has_nectar", FieldInfo.bool()
        );
    }

    @Override
    public Bees parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            );
            JsonObject json = GSON.fromJson(fixed, JsonObject.class);
            int ticksInHive = json.get("ticks_in_hive").getAsInt();
            int minTicksInHive = json.has("has_nectar") && json.get("has_nectar").getAsBoolean() ? 2400 : 600;
            List<BeehiveBlockEntity.Occupant> occupants = new ArrayList<>();
            for (int i = 0; i < json.get("count").getAsInt(); i++) {
                occupants.add(new BeehiveBlockEntity.Occupant(TypedEntityData.of(EntityType.BEE, new CompoundTag()), ticksInHive, minTicksInHive));
            }
            return new Bees(occupants);
        } catch (Throwable thrown) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(thrown.getMessage());
        }
    }

    @Override
    public DataComponentType<Bees> nms() {
        return DataComponents.BEES;
    }
}
