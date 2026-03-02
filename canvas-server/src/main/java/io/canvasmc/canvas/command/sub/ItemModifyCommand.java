package io.canvasmc.canvas.command.sub;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
import io.canvasmc.canvas.command.Command;
import io.canvasmc.canvas.command.RootCommandTree;
import io.canvasmc.canvas.util.JsonArgumentParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.SnbtGrammar;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.util.parsing.packrat.commands.CommandArgumentParser;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.Variant;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.component.Weapon;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static io.canvasmc.canvas.command.sub.ItemModifyCommand.ComponentType.adventureModePredicateComponent;
import static io.canvasmc.canvas.command.sub.ItemModifyCommand.ComponentType.booleanComponent;
import static io.canvasmc.canvas.command.sub.ItemModifyCommand.ComponentType.chatComponentComponent;
import static io.canvasmc.canvas.command.sub.ItemModifyCommand.ComponentType.eitherHolderComponent;
import static io.canvasmc.canvas.command.sub.ItemModifyCommand.ComponentType.enumComponent;
import static io.canvasmc.canvas.command.sub.ItemModifyCommand.ComponentType.floatComponent;
import static io.canvasmc.canvas.command.sub.ItemModifyCommand.ComponentType.holderComponent;
import static io.canvasmc.canvas.command.sub.ItemModifyCommand.ComponentType.identifierComponent;
import static io.canvasmc.canvas.command.sub.ItemModifyCommand.ComponentType.integerComponent;
import static io.canvasmc.canvas.command.sub.ItemModifyCommand.ComponentType.unitComponent;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.ComponentArgument.ERROR_INVALID_COMPONENT;

public class ItemModifyCommand implements Command {

    private static final Map<DataComponentType<?>, ComponentType<?>> REGISTRY = new ConcurrentHashMap<>();

    static {
        final CommandBuildContext context = RootCommandTree.INSTANCE.buildContext;
        // register all component mappings here
        integerComponent(DataComponents.MAX_STACK_SIZE).register();
        integerComponent(DataComponents.MAX_DAMAGE).register();
        integerComponent(DataComponents.DAMAGE).register();
        unitComponent(DataComponents.UNBREAKABLE).register();
        new ComponentType.UseEffectsComponent().register();
        chatComponentComponent(DataComponents.CUSTOM_NAME, context).register();
        floatComponent(DataComponents.MINIMUM_ATTACK_CHARGE).register();
        eitherHolderComponent(DataComponents.DAMAGE_TYPE, Registries.DAMAGE_TYPE, context).register();
        chatComponentComponent(DataComponents.ITEM_NAME, context).register();
        identifierComponent(DataComponents.ITEM_MODEL).register();
        new ComponentType.ItemLoreComponent(context).register();
        enumComponent(DataComponents.RARITY, Rarity.class).register();
        adventureModePredicateComponent(DataComponents.CAN_PLACE_ON, context).register();
        adventureModePredicateComponent(DataComponents.CAN_BREAK, context).register();
        // TODO - ATTRIBUTE_MODIFIERS
        // TODO - CUSTOM_MODEL_DATA
        // TODO - TOOLTIP_DISPLAY
        integerComponent(DataComponents.REPAIR_COST).register();
        unitComponent(DataComponents.CREATIVE_SLOT_LOCK).register();
        booleanComponent(DataComponents.ENCHANTMENT_GLINT_OVERRIDE).register();
        unitComponent(DataComponents.INTANGIBLE_PROJECTILE).register();
        new ComponentType.FoodPropertiesComponent().register();
        // TODO - CONSUMABLE
        new ComponentType.UseRemainderComponent().register();
        new ComponentType.UseCooldownComponent().register();
        new ComponentType.DamageResistantComponent().register();
        // TODO - TOOL
        new ComponentType.WeaponComponent().register();
        new ComponentType.AttackRangeComponent().register();
        // TODO - ENCHANTABLE
        // TODO - EQUIPPABLE
        // TODO - REPAIRABLE
        unitComponent(DataComponents.GLIDER).register();
        identifierComponent(DataComponents.TOOLTIP_STYLE).register();
        // TODO - DEATH_PROTECTION
        // TODO - BLOCKS_ATTACKS
        // TODO - PIERCING_WEAPON
        // TODO - KINETIC_WEAPON
        // TODO - SWING_ANIMATION
        // TODO - DYED_COLOR
        // TODO - MAP_COLOR
        // TODO - MAP_ID
        // TODO - MAP_DECORATIONS
        enumComponent(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.class).register();
        // TODO - CHARGED_PROJECTILES
        // TODO - BUNDLE_CONTENTS
        // TODO - POTION_CONTENTS
        floatComponent(DataComponents.POTION_DURATION_SCALE).register();
        // TODO - SUSPICIOUS_STEW_EFFECTS
        // TODO - WRITABLE_BOOK_CONTENT
        // TODO - WRITTEN_BOOK_CONTENT
        // TODO - TRIM
        // TODO - DEBUG_STICK_STATE
        // TODO - ENTITY_DATA
        // TODO - BLOCK_ENTITY_DATA
        // TODO - INSTRUMENT
        // TODO - PROVIDES_TRIM_MATERIAL
        // TODO - OMINOUS_BOTTLE_AMPLIFIER
        // TODO - JUKEBOX_PLAYABLE
        // TODO - PROVIDES_BANNER_PATTERNS
        // TODO - RECIPES
        // TODO - LODESTONE_TRACKER
        // TODO - FIREWORK_EXPLOSION
        // TODO - FIREWORKS
        new ComponentType.ResolvableProfileComponent().register();
        identifierComponent(DataComponents.NOTE_BLOCK_SOUND).register();
        // TODO - BANNER_PATTERNS
        enumComponent(DataComponents.BASE_COLOR, DyeColor.class).register();
        // TODO - POT_DECORATIONS
        // TODO - CONTAINER
        // TODO - BLOCK_STATE
        // TODO - BEES
        // TODO - LOCK
        // TODO - CONTAINER_LOOT
        holderComponent(DataComponents.BREAK_SOUND, Registries.SOUND_EVENT, context).register();
        holderComponent(DataComponents.VILLAGER_VARIANT, Registries.VILLAGER_TYPE, context).register();
        holderComponent(DataComponents.WOLF_VARIANT, Registries.WOLF_VARIANT, context).register();
        holderComponent(DataComponents.WOLF_SOUND_VARIANT, Registries.WOLF_SOUND_VARIANT, context).register();
        enumComponent(DataComponents.WOLF_COLLAR, DyeColor.class).register();
        enumComponent(DataComponents.FOX_VARIANT, Fox.Variant.class).register();
        enumComponent(DataComponents.SALMON_SIZE, Salmon.Variant.class).register();
        enumComponent(DataComponents.PARROT_VARIANT, Parrot.Variant.class).register();
        enumComponent(DataComponents.TROPICAL_FISH_PATTERN, TropicalFish.Pattern.class).register();
        enumComponent(DataComponents.TROPICAL_FISH_BASE_COLOR, DyeColor.class).register();
        enumComponent(DataComponents.TROPICAL_FISH_PATTERN_COLOR, DyeColor.class).register();
        enumComponent(DataComponents.MOOSHROOM_VARIANT, MushroomCow.Variant.class).register();
        enumComponent(DataComponents.RABBIT_VARIANT, Rabbit.Variant.class).register();
        holderComponent(DataComponents.PIG_VARIANT, Registries.PIG_VARIANT, context).register();
        holderComponent(DataComponents.COW_VARIANT, Registries.COW_VARIANT, context).register();
        eitherHolderComponent(DataComponents.CHICKEN_VARIANT, Registries.CHICKEN_VARIANT, context).register();
        eitherHolderComponent(DataComponents.ZOMBIE_NAUTILUS_VARIANT, Registries.ZOMBIE_NAUTILUS_VARIANT, context).register();
        holderComponent(DataComponents.FROG_VARIANT, Registries.FROG_VARIANT, context).register();
        enumComponent(DataComponents.HORSE_VARIANT, Variant.class).register();
        holderComponent(DataComponents.PAINTING_VARIANT, Registries.PAINTING_VARIANT, context).register();
        enumComponent(DataComponents.LLAMA_VARIANT, Llama.Variant.class).register();
        enumComponent(DataComponents.AXOLOTL_VARIANT, Axolotl.Variant.class).register();
        holderComponent(DataComponents.CAT_VARIANT, Registries.CAT_VARIANT, context).register();
        enumComponent(DataComponents.CAT_COLLAR, DyeColor.class).register();
        enumComponent(DataComponents.SHEEP_COLOR, DyeColor.class).register();
        enumComponent(DataComponents.SHULKER_COLOR, DyeColor.class).register();

        for (final DataComponentType<?> nms : BuiltInRegistries.DATA_COMPONENT_TYPE.stream().toList()) {
            Identifier identifier = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(nms);
            // We will not touch this, or enchantments, use Bukkit or the enchant command
            if (identifier.getPath().equalsIgnoreCase("custom_data") ||
                identifier.getPath().equalsIgnoreCase("enchantments") ||
                identifier.getPath().equalsIgnoreCase("stored_enchantments") ||
                identifier.getPath().equalsIgnoreCase("bucket_entity_data"))
                continue;
            if (!REGISTRY.containsKey(nms)) {
                // TODO - enable this after PR done
                // throw new IllegalStateException("Unregistered component detected! " + identifier);
            }
        }
    }

    private static <T> @Nullable ComponentType<T> get(DataComponentType<T> nms) {
        //noinspection unchecked
        return (ComponentType<T>) REGISTRY.get(nms);
    }

    private static <T> void register(DataComponentType<T> nms, ComponentType<T> type) {
        REGISTRY.put(nms, type);
    }

    @Override
    public @NotNull String getName() {
        return "itemmodify";
    }

    @Override
    public @Nullable String getDescription() {
        return "Modifies an item in the player inventory";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final @NonNull LiteralArgumentBuilder<CommandSourceStack> base) {
        return base
            .then(argument("players", EntityArgument.players())
                .then(argument("slot", SlotArgument.slot())
                    .then(literal("remove").then(argument("component", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(REGISTRY.values().stream().map(ComponentType::identifier), builder))
                        .executes(context -> {
                            final Identifier identifier = context.getArgument("component", Identifier.class);
                            final ComponentType type = get(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(identifier));
                            final int slot = SlotArgument.getSlot(context, "slot");
                            Objects.requireNonNull(type, "Unregistered component type " + identifier);
                            for (final ServerPlayer player : context.getArgument("players", EntitySelector.class).findPlayers(context.getSource())) {
                                player.scheduleToOrRun(() -> {
                                    SlotAccess slotAccess = player.getSlot(slot);
                                    if (slotAccess == null) return;
                                    ItemStack stack = slotAccess.get();
                                    if (stack.isEmpty()) return;

                                    stack.remove(type.nms());
                                    slotAccess.set(stack);
                                });
                            }
                            return 0;
                        })
                    ))
                    .then(literal("set")
                        .then(argument("component", IdentifierArgument.id())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(REGISTRY.values().stream().map(ComponentType::identifier), builder))
                            .then(argument("value", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    final Identifier identifier = context.getArgument("component", Identifier.class);
                                    ComponentType<?> type = get(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(identifier));
                                    return Objects.requireNonNull(type, "Unregistered component type " + identifier).suggestions(context, builder);
                                }).executes(context -> {
                                    final ComponentType type = get(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(context.getArgument("component", Identifier.class)));
                                    final Object value = type.parse(context.getArgument("value", String.class));
                                    final int slot = SlotArgument.getSlot(context, "slot");
                                    for (final ServerPlayer player : context.getArgument("players", EntitySelector.class).findPlayers(context.getSource())) {
                                        player.scheduleToOrRun(() -> {
                                            SlotAccess slotAccess = player.getSlot(slot);
                                            if (slotAccess == null) return;
                                            ItemStack stack = slotAccess.get();
                                            if (stack.isEmpty()) return;

                                            type.apply(stack, value);
                                            slotAccess.set(stack);
                                        });
                                    }
                                    return 0;
                                })))
                    )));
    }

    public interface ComponentType<T> extends JsonArgumentParser {
        @Contract(value = "_ -> new", pure = true)
        static @NonNull ComponentType<Integer> integerComponent(DataComponentType<Integer> nms) {
            return new ComponentType<>() {
                @Override
                public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                    return IntegerArgumentType.integer().listSuggestions(context, builder);
                }

                @Override
                public DataComponentType<Integer> nms() {
                    return nms;
                }
            };
        }

        @Contract(value = "_ -> new", pure = true)
        static @NonNull ComponentType<Float> floatComponent(DataComponentType<Float> nms) {
            return new ComponentType<>() {
                @Override
                public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                    return FloatArgumentType.floatArg().listSuggestions(context, builder);
                }

                @Override
                public DataComponentType<Float> nms() {
                    return nms;
                }
            };
        }

        @Contract(value = "_, _ -> new", pure = true)
        static <E extends Enum<E>> @NonNull ComponentType<E> enumComponent(DataComponentType<E> nms, Class<E> enumClazz) {
            return new ComponentType<>() {
                @Override
                public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                    return SharedSuggestionProvider.suggest(Arrays.stream(enumClazz.getEnumConstants()).map(e -> e.toString().toLowerCase()), builder);
                }

                @Override
                public DataComponentType<E> nms() {
                    return nms;
                }
            };
        }

        @Contract(value = "_ -> new", pure = true)
        static @NonNull ComponentType<Identifier> identifierComponent(DataComponentType<Identifier> nms) {
            return new ComponentType<>() {
                @Override
                public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                    return IdentifierArgument.id().listSuggestions(context, builder);
                }

                @Override
                public DataComponentType<Identifier> nms() {
                    return nms;
                }
            };
        }

        static @NonNull ComponentType<Component> chatComponentComponent(DataComponentType<Component> nms, @NonNull CommandBuildContext context) {
            final CommandArgumentParser<Tag> tagParser = SnbtGrammar.createParser(NbtOps.INSTANCE);
            final CommandArgumentParser<Component> parser =
                tagParser.withCodec(context.createSerializationContext(NbtOps.INSTANCE), tagParser, ComponentSerialization.CODEC, ERROR_INVALID_COMPONENT);
            return new ComponentType<>() {
                @Override
                public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                    return parser.parseForSuggestions(builder);
                }

                @Override
                public DataComponentType<Component> nms() {
                    return nms;
                }
            };
        }

        @Contract(value = "_ -> new", pure = true)
        static @NonNull ComponentType<Unit> unitComponent(DataComponentType<Unit> nms) {
            return new ComponentType<>() {
                @Override
                public Unit parse(final @NonNull String raw) {
                    return raw.equalsIgnoreCase("true") ? Unit.INSTANCE : null;
                }

                @Override
                public void apply(@NonNull final ItemStack stack, final Unit value) {
                    if (value == null) {
                        stack.remove(nms);
                    }
                    else ComponentType.super.apply(stack, value);
                }

                @Override
                public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                    return BoolArgumentType.bool().listSuggestions(context, builder);
                }

                @Override
                public DataComponentType<Unit> nms() {
                    return nms;
                }
            };
        }

        @Contract(value = "_ -> new", pure = true)
        static @NonNull ComponentType<Boolean> booleanComponent(DataComponentType<Boolean> nms) {
            return new ComponentType<>() {
                @Override
                public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                    return BoolArgumentType.bool().listSuggestions(context, builder);
                }

                @Override
                public DataComponentType<Boolean> nms() {
                    return nms;
                }
            };
        }

        @Contract(value = "_, _, _ -> new", pure = true)
        static <R> @NonNull ComponentType<Holder<R>> holderComponent(DataComponentType<Holder<R>> nms, ResourceKey<Registry<R>> resourceKey, CommandBuildContext buildContext) {
            return new ComponentType<>() {
                @Override
                public Holder<R> parse(final @NonNull String raw) {
                    String fixed = raw.replaceAll(
                        "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                        "$1\"$2\""
                    );
                    Optional<Holder.Reference<R>> oHolder = MinecraftServer.getServer().registryAccess()
                        .lookup(resourceKey).orElseThrow()
                        .get(Objects.requireNonNull(Identifier.tryParse(fixed), "Couldn't find id for " + fixed));
                    return oHolder.orElseThrow(() -> new IllegalArgumentException("Unable to locate holder for " + fixed));
                }

                @Override
                public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                    return ResourceArgument.resource(buildContext, resourceKey).listSuggestions(context, builder);
                }

                @Override
                public DataComponentType<Holder<R>> nms() {
                    return nms;
                }
            };
        }

        @Contract(value = "_, _, _ -> new", pure = true)
        static <R> @NonNull ComponentType<EitherHolder<R>> eitherHolderComponent(DataComponentType<EitherHolder<R>> nms, ResourceKey<Registry<R>> resourceKey, CommandBuildContext buildContext) {
            return new ComponentType<>() {
                @Override
                public EitherHolder<R> parse(final @NonNull String raw) throws CommandSyntaxException {
                    return new EitherHolder<>(holderComponent(null, resourceKey, null).parse(raw));
                }

                @Override
                public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                    return ResourceArgument.resource(buildContext, resourceKey).listSuggestions(context, builder);
                }

                @Override
                public DataComponentType<EitherHolder<R>> nms() {
                    return nms;
                }
            };
        }

        @Contract(value = "_, _ -> new", pure = true)
        static @NonNull ComponentType<AdventureModePredicate> adventureModePredicateComponent(DataComponentType<AdventureModePredicate> nms, CommandBuildContext context) {
            return new ComponentType<>() {
                @Override
                public AdventureModePredicate parse(final @NonNull String raw) throws CommandSyntaxException {
                    try {
                        String fixed = raw.replaceAll(
                            "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                            "$1\"$2\""
                        );
                        JsonElement json = JsonParser.parseString(fixed);
                        return AdventureModePredicate.CODEC
                            .parse(context.createSerializationContext(JsonOps.INSTANCE), json)
                            .getOrThrow(msg -> new IllegalArgumentException("Invalid predicate: " + msg));
                    } catch (JsonSyntaxException | IllegalArgumentException e) {
                        throw new DynamicCommandExceptionType(
                            obj -> Component.literal(obj.toString())
                        ).create(e.getMessage());
                    }
                }

                @Override
                public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                    String remaining = builder.getRemaining().stripTrailing();

                    if (remaining.isEmpty()) {
                        return SharedSuggestionProvider.suggest(List.of("{"), builder);
                    }

                    if (remaining.equals("{")) {
                        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + 1);
                        return SharedSuggestionProvider.suggest(List.of("\"blocks\":[", "}"), offset);
                    }

                    if (remaining.contains("\"blocks\":[")) {
                        int arrayStart = remaining.indexOf("\"blocks\":[") + "\"blocks\":[".length();
                        String afterArray = remaining.substring(arrayStart);

                        if (afterArray.contains("]")) {
                            if (!remaining.endsWith("}")) {
                                SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
                                return SharedSuggestionProvider.suggest(List.of("}"), offset);
                            }
                            return builder.buildFuture();
                        }

                        if (afterArray.matches(".*[a-z0-9_]+\\s*")) {
                            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
                            return SharedSuggestionProvider.suggest(List.of(",", "]"), offset);
                        }

                        int lastSep = Math.max(afterArray.lastIndexOf('['), afterArray.lastIndexOf(','));
                        String afterSep = lastSep >= 0 ? afterArray.substring(lastSep + 1) : afterArray;
                        int whitespace = afterSep.length() - afterSep.stripLeading().length();
                        int absoluteStart = builder.getStart() + arrayStart + lastSep + 1 + whitespace;

                        SuggestionsBuilder entryBuilder = builder.createOffset(absoluteStart);
                        return SharedSuggestionProvider.suggestResource(
                            BuiltInRegistries.BLOCK.keySet().stream(),
                            entryBuilder
                        );
                    }

                    if (remaining.startsWith("{")) {
                        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + 1);
                        return SharedSuggestionProvider.suggest(List.of("\"blocks\":[", "}"), offset);
                    }

                    return builder.buildFuture();
                }

                @Override
                public DataComponentType<AdventureModePredicate> nms() {
                    return nms;
                }
            };
        }

        CompletableFuture<Suggestions> suggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder);

        DataComponentType<T> nms();

        default Identifier identifier() {
            return BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(nms());
        }

        default T parse(@NonNull String raw) throws CommandSyntaxException {
            try {
                String fixed = raw.replaceAll(
                    "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                    "$1\"$2\""
                );
                JsonElement json = JsonParser.parseString(fixed);
                return nms().codec().parse(JsonOps.INSTANCE, json)
                    .getOrThrow(msg -> new IllegalArgumentException("Invalid value: " + msg));
            } catch (JsonSyntaxException | IllegalArgumentException e) {
                throw new DynamicCommandExceptionType(
                    obj -> Component.literal(obj.toString())
                ).create(e.getMessage());
            }
        }

        default void register() {
            ItemModifyCommand.register(nms(), this);
        }

        default void apply(@NonNull ItemStack stack, T value) {
            stack.set(nms(), value);
        }

        class UseEffectsComponent implements ComponentType<UseEffects> {
            @Override
            public Map<String, FieldInfo> jsonFields() {
                return Map.of(
                    "can_sprint", FieldInfo.bool(),
                    "interact_vibrations", FieldInfo.bool(),
                    "speed_multiplier", FieldInfo.floatField()
                );
            }

            @Override
            public CompletableFuture<Suggestions> suggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
                return jsonSuggestions(context, builder);
            }

            @Override
            public DataComponentType<UseEffects> nms() {
                return DataComponents.USE_EFFECTS;
            }
        }

        class WeaponComponent implements ComponentType<Weapon> {
            @Override
            public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                return jsonSuggestions(context, builder);
            }

            @Override
            public Map<String, FieldInfo> jsonFields() {
                return Map.of(
                    "item_damage_per_attack", FieldInfo.intField("1"),
                    "disable_blocking_for_seconds", FieldInfo.floatField("0.0F")
                );
            }

            @Override
            public DataComponentType<Weapon> nms() {
                return DataComponents.WEAPON;
            }
        }

        class ItemLoreComponent implements ComponentType<ItemLore> {
            private final CommandArgumentParser<Component> componentParser;

            ItemLoreComponent(@NonNull CommandBuildContext buildContext) {
                final CommandArgumentParser<Tag> tagParser = SnbtGrammar.createParser(NbtOps.INSTANCE);
                this.componentParser = tagParser.withCodec(
                    buildContext.createSerializationContext(NbtOps.INSTANCE),
                    tagParser,
                    ComponentSerialization.CODEC,
                    ERROR_INVALID_COMPONENT
                );
            }

            @Override
            public ItemLore parse(final @NonNull String raw) throws CommandSyntaxException {
                try {
                    String fixed = raw.replaceAll(
                        "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                        "$1\"$2\""
                    );
                    JsonElement json = JsonParser.parseString(fixed);
                    return ItemLore.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(msg -> new IllegalArgumentException("Invalid lore: " + msg));
                } catch (JsonSyntaxException | IllegalArgumentException e) {
                    throw new DynamicCommandExceptionType(
                        obj -> Component.literal(obj.toString())
                    ).create(e.getMessage());
                }
            }

            @Override
            public CompletableFuture<Suggestions> suggestions(CommandContext<CommandSourceStack> context, @NonNull SuggestionsBuilder builder) {
                String remaining = builder.getRemaining().stripTrailing();

                if (remaining.isEmpty()) {
                    return SharedSuggestionProvider.suggest(List.of("["), builder);
                }

                if (remaining.endsWith("]")) {
                    return builder.buildFuture();
                }

                if (remaining.matches(".*}\\s*")) {
                    SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
                    return SharedSuggestionProvider.suggest(List.of(",", "]"), offset);
                }

                int entryStart = Math.max(remaining.lastIndexOf('['), remaining.lastIndexOf(',')) + 1;
                String afterSep = remaining.substring(entryStart);
                int whitespace = afterSep.length() - afterSep.stripLeading().length();
                int absoluteEntryStart = builder.getStart() + entryStart + whitespace;

                return componentParser.parseForSuggestions(builder.createOffset(absoluteEntryStart));
            }

            @Override
            public DataComponentType<ItemLore> nms() {
                return DataComponents.LORE;
            }
        }

        class UseRemainderComponent implements ComponentType<UseRemainder> {
            @Override
            public UseRemainder parse(@NonNull final String raw) throws CommandSyntaxException {
                return new UseRemainder(BuiltInRegistries.ITEM.getValue(Identifier.tryParse(raw.replaceAll("\"", ""))).getDefaultInstance());
            }

            @Override
            public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                return SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder);
            }

            @Override
            public DataComponentType<UseRemainder> nms() {
                return DataComponents.USE_REMAINDER;
            }
        }

        class DamageResistantComponent implements ComponentType<DamageResistant> {
            @Override
            public DamageResistant parse(@NonNull final String raw) throws CommandSyntaxException {
                TagKey<DamageType> tagKey = TagKey.create(Registries.DAMAGE_TYPE, Objects.requireNonNull(Identifier.tryParse(raw), "Identifier " + raw + " invalid"));
                return new DamageResistant(tagKey);
            }

            @Override
            public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                List<Identifier> identifiers = new ArrayList<>();
                MinecraftServer.getServer().registryAccess().lookup(Registries.DAMAGE_TYPE).orElseThrow().listTagIds().forEach(id -> {
                    identifiers.add(id.location());
                });
                return SharedSuggestionProvider.suggestResource(identifiers, builder);
            }

            @Override
            public DataComponentType<DamageResistant> nms() {
                return DataComponents.DAMAGE_RESISTANT;
            }
        }

        class ResolvableProfileComponent implements ComponentType<ResolvableProfile> {
            @Override
            public ResolvableProfile parse(@NonNull final String raw) throws CommandSyntaxException {
                final ServerPlayer searchedLocally = MinecraftServer.getServer().getPlayerList().getPlayerByName(raw);
                if (searchedLocally != null) {
                    return ResolvableProfile.createResolved(searchedLocally.getGameProfile());
                }
                return ResolvableProfile.createUnresolved(raw.replaceAll("\"", ""));
            }

            @Override
            public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                return SharedSuggestionProvider.suggest(MinecraftServer.getServer().getPlayerList().getPlayerNamesArray(), builder);
            }

            @Override
            public DataComponentType<ResolvableProfile> nms() {
                return DataComponents.PROFILE;
            }
        }

        class UseCooldownComponent implements ComponentType<UseCooldown> {
            @Override
            public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                return jsonSuggestions(context, builder);
            }

            @Override
            public Map<String, FieldInfo> jsonFields() {
                return Map.of(
                    "seconds", FieldInfo.floatField(),
                    "cooldown_group", FieldInfo.stringField()
                );
            }

            @Override
            public DataComponentType<UseCooldown> nms() {
                return DataComponents.USE_COOLDOWN;
            }
        }

        class FoodPropertiesComponent implements ComponentType<FoodProperties> {
            @Override
            public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                return jsonSuggestions(context, builder);
            }

            @Override
            public Map<String, FieldInfo> jsonFields() {
                return Map.of(
                    "nutrition", FieldInfo.intField(),
                    "saturation", FieldInfo.floatField(),
                    "can_always_eat", FieldInfo.bool()
                );
            }

            @Override
            public DataComponentType<FoodProperties> nms() {
                return DataComponents.FOOD;
            }
        }

        class AttackRangeComponent implements ComponentType<AttackRange> {
            @Override
            public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                return jsonSuggestions(context, builder);
            }

            @Override
            public Map<String, FieldInfo> jsonFields() {
                return Map.of(
                    "min_reach", FieldInfo.floatField("0.0"),
                    "max_reach", FieldInfo.floatField("3.0"),
                    "min_creative_reach", FieldInfo.floatField("0.0"),
                    "max_creative_reach", FieldInfo.floatField("5.0"),
                    "hitbox_margin", FieldInfo.floatField("0.3"),
                    "mob_factor", FieldInfo.floatField("1.0")
                );
            }

            @Override
            public DataComponentType<AttackRange> nms() {
                return DataComponents.ATTACK_RANGE;
            }
        }
    }
}
