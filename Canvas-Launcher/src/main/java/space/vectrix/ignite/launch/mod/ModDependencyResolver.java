/*
 * This file is part of Ignite, licensed under the MIT License (MIT).
 *
 * Copyright (c) vectrix.space <https://vectrix.space/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package space.vectrix.ignite.launch.mod;

import com.google.common.collect.Maps;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import org.checkerframework.checker.nullness.qual.NonNull;
import space.vectrix.ignite.api.mod.ModContainer;
import space.vectrix.ignite.launch.IgnitePlatform;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public final class ModDependencyResolver {
  public static @NonNull List<ModContainer> resolveDependencies(final @NonNull IgnitePlatform platform, final @NonNull Collection<ModContainer> containers) throws IllegalStateException {
    final List<ModContainer> sortedContainers = new ArrayList<>(containers);
    sortedContainers.sort(Comparator.comparing(ModContainer::getId));

    final MutableGraph<ModContainer> graph = GraphBuilder.directed()
      .allowsSelfLoops(false)
      .expectedNodeCount(sortedContainers.size())
      .build();

    final Map<String, ModContainer> candidateMap = Maps.uniqueIndex(sortedContainers, ModContainer::getId);

    for (final ModContainer container : sortedContainers) {
      graph.addNode(container);

      // Required Dependencies
      final List<String> requiredDependencies = container.getConfig().getRequiredDependencies();
      if (requiredDependencies != null) {
        for (final String requiredDependency : requiredDependencies) {
          final ModContainer dependency = candidateMap.get(requiredDependency);
          if (dependency != null) {
            graph.putEdge(container, dependency);
          } else {
            platform.getLogger().error("Unable to resolve required dependency '" + requiredDependency + "' for '" + container.getId() + "'!");
            graph.removeNode(container);
          }
        }
      }

      // Optional Dependencies
      final List<String> optionalDependencies = container.getConfig().getOptionalDependencies();
      if (optionalDependencies != null) {
        for (final String optionalDependency : optionalDependencies) {
          final ModContainer dependency = candidateMap.get(optionalDependency);
          if (dependency != null) {
            graph.putEdge(container, dependency);
          } else {
            platform.getLogger().warn("Unable to resolve optional dependency '" + optionalDependency + "' for '" + container.getId() + "'!");
          }
        }
      }
    }

    final List<ModContainer> sorted = new ArrayList<>();
    final Map<ModContainer, Mark> marks = new HashMap<>();

    for (final ModContainer node : graph.nodes()) {
      ModDependencyResolver.visitNode(graph, node, marks, sorted, new ArrayDeque<>());
    }

    return sorted;
  }

  private static void visitNode(final Graph<ModContainer> dependencyGraph,
                                final ModContainer node,
                                final Map<ModContainer, Mark> marks,
                                final List<ModContainer> sorted,
                                final Deque<ModContainer> currentIteration) throws IllegalStateException {
    final Mark mark = marks.getOrDefault(node, Mark.NOT_VISITED);
    if (mark == Mark.PERMANENT) {
      return;
    } else if (mark == Mark.TEMPORARY) {
      // Circular dependency.
      currentIteration.addLast(node);

      final StringBuilder loopGraph = new StringBuilder();
      for (final ModContainer container : currentIteration) {
        loopGraph.append(container.getId());
        loopGraph.append(" -> ");
      }

      loopGraph.setLength(loopGraph.length() - 4);
      throw new IllegalStateException("Circular dependency detected: " + loopGraph.toString());
    }

    currentIteration.addLast(node);
    marks.put(node, Mark.TEMPORARY);

    for (final ModContainer edge : dependencyGraph.successors(node)) {
      ModDependencyResolver.visitNode(dependencyGraph, edge, marks, sorted, currentIteration);
    }

    marks.put(node, Mark.PERMANENT);
    currentIteration.removeLast();
    sorted.add(node);
  }

  private ModDependencyResolver() {}

  private enum Mark {
    NOT_VISITED,
    TEMPORARY,
    PERMANENT
  }
}
