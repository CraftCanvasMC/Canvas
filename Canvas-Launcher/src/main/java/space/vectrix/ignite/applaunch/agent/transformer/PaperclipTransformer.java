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
package space.vectrix.ignite.applaunch.agent.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class PaperclipTransformer implements ClassFileTransformer {
  private final String target;

  public PaperclipTransformer(final String target) {
    this.target = target;
  }

  @Override
  public byte[] transform(final ClassLoader loader, final String className,
                          final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain,
                          final byte[] classfileBuffer) {
    if (!className.equals(this.target)) return null;
    final ClassReader reader = new ClassReader(classfileBuffer);
    final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    reader.accept(new PaperclipClassVisitor(writer), ClassReader.EXPAND_FRAMES);
    return writer.toByteArray();
  }

  public static final class PaperclipClassVisitor extends ClassVisitor {
    public PaperclipClassVisitor(final ClassVisitor visitor) {
      super(Opcodes.ASM9, visitor);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
      final MethodVisitor mv = this.cv.visitMethod(access, name, descriptor, signature, exceptions);
      return new PaperclipMethodVisitor(descriptor, mv);
    }
  }

  public static final class PaperclipMethodVisitor extends MethodVisitor {
    private final String descriptor;

    public PaperclipMethodVisitor(final String descriptor, final MethodVisitor visitor) {
      super(Opcodes.ASM9, visitor);

      this.descriptor = descriptor;
    }

    @Override
    public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor, final boolean isInterface) {
      if (name.equals("setupClasspath")) {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        // After the method is written return.
        this.visitInsn(Opcodes.RETURN);
        return;
      }

      // Return before system exit calls.
      if (owner.equals("java/lang/System") && name.equals("exit")) {
        if (this.descriptor.endsWith("V")) {
          // Void descriptor return type, will return normally...
          this.visitInsn(Opcodes.RETURN);
        } else {
          // Otherwise, return null.
          this.visitInsn(Opcodes.ACONST_NULL);
          this.visitInsn(Opcodes.ARETURN);
        }
      }

      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
  }
}
