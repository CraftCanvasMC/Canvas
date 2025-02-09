package io.canvasmc.canvas.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.NotNull;
import oshi.util.tuples.Pair;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static net.minecraft.commands.Commands.*;

public abstract class AbstractCommand {
    private final List<Predicate<CommandSourceStack>> requirements = new ArrayList<>();
    private final Map<String, Node> subCommands = new HashMap<>();

    public @NotNull LiteralArgumentBuilder<CommandSourceStack> build(final @NotNull LiteralArgumentBuilder<CommandSourceStack> root) {
        root.requires(Util.allOf(this.requirements));
        this.subCommands.forEach((name, node) -> {
            if (!node.isArgument()) {
                LiteralArgumentBuilder<CommandSourceStack> subNode = literal(name);
                subNode.executes((commandContext) -> {
                    for (final ExecutionConsumer commandExecutor : node.sourceConsumer) {
                        commandExecutor.run(commandContext, commandContext.getSource());
                    }
                    return 1;
                });
                root.then(subNode);
            } else {
                processArgument(name, node.getArgument(), root);
            }
        });
        return root;
    }

    private void processArgument(String name, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") @NotNull Optional<ArgumentNode<?>> argumentNode, final @NotNull ArgumentBuilder<CommandSourceStack, ?> root) {
        if (argumentNode.isPresent()) {
            RequiredArgumentBuilder<CommandSourceStack, ?> argumentBuilder = Commands.argument(name, argumentNode.get().argumentType());
            ArgumentNode<?> argument = argumentNode.get();

            if (argument.suggestions != null) {
                argumentBuilder = argumentBuilder.suggests((_, builder) -> SharedSuggestionProvider.suggest(argument.suggestions.get(), builder));
            }

            for (final Supplier<Pair<String, Node>> nextNodeSupplier : argument.nextNodes) {
                Pair<String, Node> nextNode = nextNodeSupplier.get();
                Node node = nextNode.getB();

                if (!node.isArgument()) {
                    String subcommandName = nextNode.getA();
                    LiteralArgumentBuilder<CommandSourceStack> subNode = literal(subcommandName);

                    subNode.executes((commandContext) -> {
                        for (final ExecutionConsumer commandExecutor : node.sourceConsumer) {
                            commandExecutor.run(commandContext, commandContext.getSource());
                        }
                        return 1;
                    });

                    argumentBuilder = argumentBuilder.then(subNode);
                } else {
                    processArgument(nextNode.getA(), node.getArgument(), argumentBuilder);
                }
            }

            root.then(argumentBuilder);
        }
    }

    public void requirePermission(int level) {
        this.requirements.add((source) -> source.hasPermission(level));
    }

    public void requirePermission(int level, String permission) {
        this.requirements.add((source) -> source.hasPermission(level, permission));
    }

    public void registerNode(String name, Node node) {
        this.subCommands.put(name, node);
    }

    public void registerNode(String name, @NotNull Supplier<Node> nodeSupplier) {
        this.registerNode(name, nodeSupplier.get());
    }

    @SafeVarargs
    protected final ArgumentNode<String> multiArg(ArgumentNode<String> node, Supplier<Pair<String, Node>> @NotNull ... handlers) {
        for (Supplier<Pair<String, Node>> handler : handlers) {
            node.then(handler);
        }
        return node;
    }

    public static class Node {
        private final List<ExecutionConsumer> sourceConsumer = new ArrayList<>();
        private ArgumentNode<?> node = null;
        private boolean isArg = false;

        public Node executes(ExecutionConsumer sourceConsumer) {
            this.sourceConsumer.add(sourceConsumer);
            return this;
        }

        public boolean isArgument() {
            return isArg;
        }

        public Node argument(ArgumentNode<?> argumentNode) {
            this.node = argumentNode;
            this.isArg = true;
            return this;
        }

        public Optional<ArgumentNode<?>> getArgument() {
            return Optional.ofNullable(this.node);
        }
    }

    public static class ArgumentNode<T> {
        private final ArgumentType<T> argument;
        private Supplier<String[]> suggestions;
        private final List<Supplier<Pair<String, Node>>> nextNodes;

        public ArgumentNode(ArgumentType<T> argument) {
            this.argument = argument;
            this.nextNodes = new ArrayList<>();
        }

        public ArgumentType<T> argumentType() {
            return argument;
        }

        public ArgumentNode<T> suggests(@NotNull Supplier<String[]> suggestions) {
            this.suggestions = suggestions;
            return this;
        }

        public ArgumentNode<T> then(@NotNull Pair<String, Node> next) {
            this.nextNodes.add(() -> next);
            return this;
        }

        public ArgumentNode<T> then(Pair<String, Node> @NotNull [] next) {
            for (final Pair<String, Node> pair : next) {
                this.nextNodes.add(() -> pair);
            }
            return this;
        }

        public ArgumentNode<T> then(@NotNull Supplier<Pair<String, Node>> next) {
            this.nextNodes.add(next);
            return this;
        }

        public ArgumentNode<T> then(Supplier<Pair<String, Node>> @NotNull [] next) {
            Collections.addAll(this.nextNodes, next);
            return this;
        }
    }
}
