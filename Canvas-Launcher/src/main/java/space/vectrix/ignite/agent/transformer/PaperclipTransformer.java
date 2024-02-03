package space.vectrix.ignite.agent.transformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import space.vectrix.ignite.util.IgniteConstants;

/**
 * Provides a transformer for replacing Paperclips {@link System#exit(int)}s
 * with returns.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class PaperclipTransformer implements ClassFileTransformer {
  private final String target;

  /**
   * Creates a new paperclip transformer.
   *
   * @param target the target class
   * @since 1.0.0
   */
  public PaperclipTransformer(final @NotNull String target) {
    this.target = target;
  }

  @Override
  public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined,
                          final ProtectionDomain protectionDomain, final byte[] classFileBuffer) throws IllegalClassFormatException {
    if(!className.equals(this.target)) return null;
    final ClassReader reader = new ClassReader(classFileBuffer);
    final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    reader.accept(new PaperclipClassVisitor(writer), ClassReader.EXPAND_FRAMES);
    return writer.toByteArray();
  }

  private static final class PaperclipClassVisitor extends ClassVisitor {
    private PaperclipClassVisitor(final @NotNull ClassVisitor visitor) {
      super(IgniteConstants.ASM_VERSION, visitor);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final @NotNull String name, final @NotNull String descriptor, final @NotNull String signature, final @NotNull String[] exceptions) {
      final MethodVisitor mv = this.cv.visitMethod(access, name, descriptor, signature, exceptions);
      return new PaperclipMethodVisitor(descriptor, mv);
    }
  }

  private static final class PaperclipMethodVisitor extends MethodVisitor {
    private final String descriptor;

    private PaperclipMethodVisitor(final @NotNull String descriptor, final @NotNull MethodVisitor visitor) {
      super(IgniteConstants.ASM_VERSION, visitor);

      this.descriptor = descriptor;
    }

    @Override
    public void visitMethodInsn(final int opcode, final @NotNull String owner, final @NotNull String name,
                                final @NotNull String descriptor, final boolean isInterface) {
      if(name.equals("setupClasspath")) {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        // After the method is written return.
        this.visitInsn(Opcodes.RETURN);
        return;
      }

      // Return before system exit calls.
      if(owner.equals("java/lang/System") && name.equals("exit")) {
        if(this.descriptor.endsWith("V")) {
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
