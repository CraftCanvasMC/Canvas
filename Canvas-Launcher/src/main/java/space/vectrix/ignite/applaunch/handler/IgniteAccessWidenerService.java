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
package space.vectrix.ignite.applaunch.handler;

import cpw.mods.modlauncher.api.ITransformerActivity;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerClassVisitor;
import net.fabricmc.accesswidener.AccessWidenerReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import space.vectrix.ignite.applaunch.util.IgniteConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

public final class IgniteAccessWidenerService implements ILaunchPluginService {
  private static final EnumSet<Phase> MATCH = EnumSet.of(Phase.BEFORE);
  private static final EnumSet<Phase> FAIL = EnumSet.noneOf(Phase.class);

  private final Logger logger = LogManager.getLogger("Ignite Access Widener");
  private final AccessWidener widener = new AccessWidener();
  private final AccessWidenerReader reader = new AccessWidenerReader(this.widener);

  @Override
  public String name() {
    return IgniteConstants.ACCESS_WIDENER_SERVICE;
  }

  @Override
  public int processClassWithFlags(final @NonNull Phase phase, final @NonNull ClassNode classNode,
                                   final @NonNull Type classType, final @NonNull String reason) {
    if (!this.widener.getTargets().contains(classNode.name.replace('/', '.')) || !reason.equals(ITransformerActivity.CLASSLOADING_REASON)) {
      this.logger.debug("Processing class '{}' in reason '{}', but access wideners did not contain the class!", classNode.name, reason);
      return ComputeFlags.NO_REWRITE;
    }

    final ClassNode node = new ClassNode(IgniteConstants.ASM_VERSION);
    classNode.accept(node);

    final ClassVisitor visitor = AccessWidenerClassVisitor.createClassVisitor(IgniteConstants.ASM_VERSION, classNode, this.widener);

    classNode.visibleAnnotations = null;
    classNode.invisibleAnnotations = null;
    classNode.visibleTypeAnnotations = null;
    classNode.invisibleTypeAnnotations = null;
    classNode.attrs = null;
    classNode.nestMembers = null;
    classNode.permittedSubclasses = null;
    classNode.recordComponents = null;
    classNode.innerClasses.clear();
    classNode.fields.clear();
    classNode.methods.clear();
    classNode.interfaces.clear();
    node.accept(visitor);

    return ComputeFlags.SIMPLE_REWRITE;
  }

  @Override
  public void offerResource(final @NonNull Path resource, final @NonNull String name) {
    if (resource.getFileName().toString().endsWith(IgniteConstants.ACCESS_WIDENER_EXTENSION)) {
      try (final BufferedReader reader = Files.newBufferedReader(resource, StandardCharsets.UTF_8)) {
        this.logger.debug("Applying access widener for '{}' at '{}'!", name, resource);
        this.reader.read(reader);
      } catch (final IOException exception) {
        this.logger.error("Failed to apply access widener for '{}' at '{}'!", name, resource, exception);
      }
    } else {
      this.logger.warn("Could not find the appropriate extension '{}' for '{}' at '{}'!",
        IgniteConstants.ACCESS_WIDENER_EXTENSION, name, resource);
    }
  }

  @Override
  public EnumSet<Phase> handlesClass(final @NonNull Type classType, final boolean isEmpty) {
    throw new UnsupportedOperationException("Outdated ModLauncher!");
  }

  @Override
  public EnumSet<Phase> handlesClass(final @NonNull Type classType, final boolean isEmpty, final @NonNull String reason) {
    if (reason.equals(ITransformerActivity.CLASSLOADING_REASON) && this.widener.getTargets().contains(classType.getClassName())) {
      return IgniteAccessWidenerService.MATCH;
    } else {
      return IgniteAccessWidenerService.FAIL;
    }
  }
}
