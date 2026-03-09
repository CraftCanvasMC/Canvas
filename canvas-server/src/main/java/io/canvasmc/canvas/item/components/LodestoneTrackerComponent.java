package io.canvasmc.canvas.item.components;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.LodestoneTracker;
import org.jspecify.annotations.NonNull;

public class LodestoneTrackerComponent extends ComponentType<LodestoneTracker> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "world", FieldInfo.identifierField((context) -> MinecraftServer.getServer().levelKeys().stream().map(ResourceKey::identifier).collect(Collectors.toSet())),
            "x", FieldInfo.intField(),
            "y", FieldInfo.intField(),
            "z", FieldInfo.intField(),
            "tracked", FieldInfo.bool()
        );
    }

    @Override
    public LodestoneTracker parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,{:])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)(?=[\\s,}\\]])",
                "$1\"$2\""
            );
            JsonObject json = JsonParser.parseString(fixed).getAsJsonObject();
            GlobalPos pos = new GlobalPos(
                MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.DIMENSION).get(
                    Identifier.parse(json.get("world").getAsString())
                ).orElseThrow().key(),
                new BlockPos(
                    json.get("x").getAsInt(),
                    json.get("y").getAsInt(),
                    json.get("z").getAsInt()
                )
            );
            boolean tracked = json.get("tracked").getAsBoolean();
            return new LodestoneTracker(Optional.of(pos), tracked);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public DataComponentType<LodestoneTracker> nms() {
        return DataComponents.LODESTONE_TRACKER;
    }
}
