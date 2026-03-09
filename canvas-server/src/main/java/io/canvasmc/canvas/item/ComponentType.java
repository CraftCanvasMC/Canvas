package io.canvasmc.canvas.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
import io.canvasmc.canvas.command.RootCommandTree;
import io.canvasmc.canvas.item.components.AttackRangeComponent;
import io.canvasmc.canvas.item.components.AttributeModifiersComponent;
import io.canvasmc.canvas.item.components.ChargedProjectilesComponent;
import io.canvasmc.canvas.item.components.CustomModelDataComponent;
import io.canvasmc.canvas.item.components.DamageResistantComponent;
import io.canvasmc.canvas.item.components.EquippableComponent;
import io.canvasmc.canvas.item.components.FoodPropertiesComponent;
import io.canvasmc.canvas.item.components.ItemLoreComponent;
import io.canvasmc.canvas.item.components.PiercingWeaponComponent;
import io.canvasmc.canvas.item.components.PotDecorationsComponent;
import io.canvasmc.canvas.item.components.RepairableComponent;
import io.canvasmc.canvas.item.components.ResolvableProfileComponent;
import io.canvasmc.canvas.item.components.SuspiciousStewEffectsComponent;
import io.canvasmc.canvas.item.components.SwingAnimationComponent;
import io.canvasmc.canvas.item.components.TooltipDisplayComponent;
import io.canvasmc.canvas.item.components.UseCooldownComponent;
import io.canvasmc.canvas.item.components.UseEffectsComponent;
import io.canvasmc.canvas.item.components.UseRemainderComponent;
import io.canvasmc.canvas.item.components.WeaponComponent;
import io.canvasmc.canvas.item.components.WritableBookContentComponent;
import io.canvasmc.canvas.item.components.WrittenBookContentComponent;
import io.canvasmc.canvas.util.JsonArgumentParser;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
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
import net.minecraft.util.Unit;
import net.minecraft.util.parsing.packrat.commands.CommandArgumentParser;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.Variant;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.component.OminousBottleAmplifier;
import net.minecraft.world.item.component.ProvidesTrimMaterial;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.arguments.ComponentArgument.ERROR_INVALID_COMPONENT;

public abstract class ComponentType<T> implements JsonArgumentParser {

    private static final Map<DataComponentType<?>, ComponentType<?>> REGISTRY = new ConcurrentHashMap<>();

    static {
        final CommandBuildContext context = RootCommandTree.INSTANCE.buildContext;
        // register all component mappings here
        register(integerComponent(DataComponents.MAX_STACK_SIZE));
        register(integerComponent(DataComponents.MAX_DAMAGE));
        register(integerComponent(DataComponents.DAMAGE));
        register(unitComponent(DataComponents.UNBREAKABLE));
        register(new UseEffectsComponent());
        register(chatComponentComponent(DataComponents.CUSTOM_NAME, context));
        register(floatComponent(DataComponents.MINIMUM_ATTACK_CHARGE));
        register(eitherHolderComponent(DataComponents.DAMAGE_TYPE, Registries.DAMAGE_TYPE, context));
        register(chatComponentComponent(DataComponents.ITEM_NAME, context));
        register(identifierComponent(DataComponents.ITEM_MODEL, () -> {
            return MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.ITEM).keySet().stream();
        }));
        register(new ItemLoreComponent(context));
        register(enumComponent(DataComponents.RARITY, Rarity.class));
        register(adventureModePredicateComponent(DataComponents.CAN_PLACE_ON, context));
        register(adventureModePredicateComponent(DataComponents.CAN_BREAK, context));
        register(new AttributeModifiersComponent());
        register(new CustomModelDataComponent());
        register(new TooltipDisplayComponent());
        register(integerComponent(DataComponents.REPAIR_COST));
        register(unitComponent(DataComponents.CREATIVE_SLOT_LOCK));
        register(booleanComponent(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        register(unitComponent(DataComponents.INTANGIBLE_PROJECTILE));
        register(new FoodPropertiesComponent());
        // TODO - CONSUMABLE
        register(new UseRemainderComponent());
        register(new UseCooldownComponent());
        register(new DamageResistantComponent());
        // TODO - TOOL
        register(new WeaponComponent());
        register(new AttackRangeComponent());
        register(integerOnlyComponent(DataComponents.ENCHANTABLE, Enchantable::new, 0, Integer.MAX_VALUE));
        register(new EquippableComponent());
        register(new RepairableComponent());
        register(unitComponent(DataComponents.GLIDER));
        register(identifierComponent(DataComponents.TOOLTIP_STYLE, Stream::empty));
        // TODO - DEATH_PROTECTION
        // TODO - BLOCKS_ATTACKS
        register(new PiercingWeaponComponent());
        // TODO - KINETIC_WEAPON
        register(new SwingAnimationComponent());
        register(integerOnlyComponent(DataComponents.DYED_COLOR, DyedItemColor::new, Integer.MIN_VALUE, Integer.MAX_VALUE));
        register(integerOnlyComponent(DataComponents.MAP_COLOR, MapItemColor::new, Integer.MIN_VALUE, Integer.MAX_VALUE));
        register(integerOnlyComponent(DataComponents.MAP_ID, MapId::new, Integer.MIN_VALUE, Integer.MAX_VALUE));
        // TODO - MAP_DECORATIONS
        register(enumComponent(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.class));
        register(new ChargedProjectilesComponent());
        // TODO - BUNDLE_CONTENTS
        // TODO - POTION_CONTENTS
        register(floatComponent(DataComponents.POTION_DURATION_SCALE));
        register(new SuspiciousStewEffectsComponent());
        register(new WritableBookContentComponent());
        register(new WrittenBookContentComponent());
        // TODO - TRIM
        // TODO - DEBUG_STICK_STATE
        // TODO - ENTITY_DATA
        // TODO - BLOCK_ENTITY_DATA
        register(eitherHolderOnlyComponent(DataComponents.INSTRUMENT, InstrumentComponent::new, Registries.INSTRUMENT));
        register(eitherHolderOnlyComponent(DataComponents.PROVIDES_TRIM_MATERIAL, ProvidesTrimMaterial::new, Registries.TRIM_MATERIAL));
        register(integerOnlyComponent(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, OminousBottleAmplifier::new, 0, 5));
        register(eitherHolderOnlyComponent(DataComponents.JUKEBOX_PLAYABLE, JukeboxPlayable::new, Registries.JUKEBOX_SONG));
        // TODO - PROVIDES_BANNER_PATTERNS
        // TODO - RECIPES
        // TODO - LODESTONE_TRACKER
        // TODO - FIREWORK_EXPLOSION
        // TODO - FIREWORKS
        register(new ResolvableProfileComponent());
        register(identifierComponent(DataComponents.NOTE_BLOCK_SOUND, Stream::empty));
        // TODO - BANNER_PATTERNS
        register(enumComponent(DataComponents.BASE_COLOR, DyeColor.class));
        register(new PotDecorationsComponent());
        // TODO - CONTAINER
        // TODO - BLOCK_STATE
        // TODO - BEES
        // TODO - LOCK
        // TODO - CONTAINER_LOOT
        register(holderComponent(DataComponents.BREAK_SOUND, Registries.SOUND_EVENT, context));
        register(holderComponent(DataComponents.VILLAGER_VARIANT, Registries.VILLAGER_TYPE, context));
        register(holderComponent(DataComponents.WOLF_VARIANT, Registries.WOLF_VARIANT, context));
        register(holderComponent(DataComponents.WOLF_SOUND_VARIANT, Registries.WOLF_SOUND_VARIANT, context));
        register(enumComponent(DataComponents.WOLF_COLLAR, DyeColor.class));
        register(enumComponent(DataComponents.FOX_VARIANT, Fox.Variant.class));
        register(enumComponent(DataComponents.SALMON_SIZE, Salmon.Variant.class));
        register(enumComponent(DataComponents.PARROT_VARIANT, Parrot.Variant.class));
        register(enumComponent(DataComponents.TROPICAL_FISH_PATTERN, TropicalFish.Pattern.class));
        register(enumComponent(DataComponents.TROPICAL_FISH_BASE_COLOR, DyeColor.class));
        register(enumComponent(DataComponents.TROPICAL_FISH_PATTERN_COLOR, DyeColor.class));
        register(enumComponent(DataComponents.MOOSHROOM_VARIANT, MushroomCow.Variant.class));
        register(enumComponent(DataComponents.RABBIT_VARIANT, Rabbit.Variant.class));
        register(holderComponent(DataComponents.PIG_VARIANT, Registries.PIG_VARIANT, context));
        register(holderComponent(DataComponents.COW_VARIANT, Registries.COW_VARIANT, context));
        register(eitherHolderComponent(DataComponents.CHICKEN_VARIANT, Registries.CHICKEN_VARIANT, context));
        register(eitherHolderComponent(DataComponents.ZOMBIE_NAUTILUS_VARIANT, Registries.ZOMBIE_NAUTILUS_VARIANT, context));
        register(holderComponent(DataComponents.FROG_VARIANT, Registries.FROG_VARIANT, context));
        register(enumComponent(DataComponents.HORSE_VARIANT, Variant.class));
        register(holderComponent(DataComponents.PAINTING_VARIANT, Registries.PAINTING_VARIANT, context));
        register(enumComponent(DataComponents.LLAMA_VARIANT, Llama.Variant.class));
        register(enumComponent(DataComponents.AXOLOTL_VARIANT, Axolotl.Variant.class));
        register(holderComponent(DataComponents.CAT_VARIANT, Registries.CAT_VARIANT, context));
        register(enumComponent(DataComponents.CAT_COLLAR, DyeColor.class));
        register(enumComponent(DataComponents.SHEEP_COLOR, DyeColor.class));
        register(enumComponent(DataComponents.SHULKER_COLOR, DyeColor.class));

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

    private static <T> void register(ComponentType<T> type) {
        REGISTRY.put(type.nms(), type);
    }

    public static <T> @Nullable ComponentType<T> get(DataComponentType<T> nms) {
        //noinspection unchecked
        return (ComponentType<T>) REGISTRY.get(nms);
    }

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

    @Contract(value = "_,_ -> new", pure = true)
    static @NonNull ComponentType<Identifier> identifierComponent(DataComponentType<Identifier> nms, Supplier<Stream<Identifier>> suggestionsSupplier) {
        return new ComponentType<>() {
            @Override
            public Identifier parse(@NonNull final String raw) {
                return Identifier.parse(raw.replaceAll(
                    "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                    "$1\"$2\""
                ));
            }

            @Override
            public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                return SharedSuggestionProvider.suggestResource(suggestionsSupplier.get(), builder);
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
                else super.apply(stack, value);
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

    @Contract(value = "_, _, _, _ -> new", pure = true)
    static <T> @NonNull ComponentType<T> integerOnlyComponent(DataComponentType<T> nms, Function<Integer, T> parser, int min, int max) {
        return new ComponentType<>() {
            @Override
            public T parse(@NonNull final String raw) throws CommandSyntaxException {
                try {
                    int integer = Integer.parseInt(raw);
                    if (integer < min || integer > max) {
                        throw new NumberFormatException();
                    }
                    return parser.apply(integer);
                } catch (NumberFormatException outofbounds) {
                    throw new DynamicCommandExceptionType(
                        obj -> Component.literal(obj.toString())
                    ).create(String.format("Integer out of range: %s (valid: %.2E to %.2E)", raw, (double) min, (double) max));
                }
            }

            @Override
            public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                return IntegerArgumentType.integer().listSuggestions(context, builder);
            }

            @Override
            public DataComponentType<T> nms() {
                return nms;
            }
        };
    }

    @Contract(value = "_, _, _ -> new", pure = true)
    static <T, R> @NonNull ComponentType<T> eitherHolderOnlyComponent(DataComponentType<T> nms, Function<EitherHolder<R>, T> parser, ResourceKey<Registry<R>> registry) {
        return new ComponentType<T>() {
            @Override
            public T parse(@NonNull final String raw) throws CommandSyntaxException {
                return parser.apply(new EitherHolder<>(MinecraftServer.getServer().registryAccess().lookupOrThrow(registry).get(Objects.requireNonNull(Identifier.tryParse(raw), "Couldn't parse " + raw)).orElseThrow()));
            }

            @Override
            public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
                return SharedSuggestionProvider.suggestResource(MinecraftServer.getServer().registryAccess().lookupOrThrow(registry).keySet(), builder);
            }

            @Override
            public DataComponentType<T> nms() {
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

    public static Stream<Identifier> ids() {
        return REGISTRY.values().stream().map(ComponentType::identifier);
    }

    public abstract CompletableFuture<Suggestions> suggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder);

    public abstract DataComponentType<T> nms();

    public Identifier identifier() {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(nms());
    }

    public T parse(@NonNull String raw) throws CommandSyntaxException {
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

    public CompletableFuture<Suggestions> listOfSuggestions(CommandContext<CommandSourceStack> context, @NonNull SuggestionsBuilder builder, BiFunction<CommandContext<CommandSourceStack>, SuggestionsBuilder, CompletableFuture<Suggestions>> suggestionProvider) {
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

        return suggestionProvider.apply(context, builder.createOffset(absoluteEntryStart));
    }

    public void apply(@NonNull ItemStack stack, T value) {
        stack.set(nms(), value);
    }
}
