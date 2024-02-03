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
 * Provides a transformer for replacing Spigots {@link System#exit(int)}s
 * with returns.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class SpigotTransformer implements ClassFileTransformer {
  private final String target;

  /**
   * Creates a new spigot transformer.
   *
   * @param target the target class
   * @since 1.0.0
   */
  public SpigotTransformer(final @NotNull String target) {
    this.target = target;
  }

  @Override
  public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined,
                          final ProtectionDomain protectionDomain, final byte[] classFileBuffer) throws IllegalClassFormatException {
    if(!className.equals(this.target)) return null;
    final ClassReader reader = new ClassReader(classFileBuffer);
    final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    reader.accept(new SpigotClassVisitor(writer), ClassReader.EXPAND_FRAMES);
    return writer.toByteArray();
  }

  private static final class SpigotClassVisitor extends ClassVisitor {
    private SpigotClassVisitor(final ClassVisitor visitor) {
      super(IgniteConstants.ASM_VERSION, visitor);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
      final MethodVisitor mv = this.cv.visitMethod(access, name, descriptor, signature, exceptions);
      return new SpigotMethodVisitor(descriptor, mv);
    }
  }

  private static final class SpigotMethodVisitor extends MethodVisitor {
    private final String descriptor;

    private int index;

    private SpigotMethodVisitor(final String descriptor, final MethodVisitor visitor) {
      super(IgniteConstants.ASM_VERSION, visitor);

      this.descriptor = descriptor;
    }

    @Override
    public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor, final boolean isInterface) {
      if (owner.equals("java/io/PrintStream") && name.equals("println") && this.index++ == 1) {
        // Return before the specified position.
        this.visitInsn(Opcodes.RETURN);
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
