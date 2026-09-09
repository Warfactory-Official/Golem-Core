package dev.golem.core.flywheel.gl;

//? if neoforge && >=26.1 {

import jdk.internal.misc.Unsafe;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * NeoForge opens {@code java.base/java.lang.invoke} to {@code ALL-UNNAMED}, but this layer lives in module {@code golemcore}.
 * So, instead we construct a class in the unnamed module that can access the private {@code IMPL_LOOKUP} field,
 * and use it to export the necessary package to our module.
 *
 * @author mlbv
 */
final class UnsafeBootstrap {
    static final MethodHandles.Lookup IMPL_LOOKUP = bootstrapImplLookup();

    static {
        exportJavaBasePackage("jdk.internal.misc");
    }

    static final Unsafe U = Unsafe.getUnsafe();

    private UnsafeBootstrap() {
    }

    private static void exportJavaBasePackage(String packageName) {
        try {
            MethodHandle implAddExports = IMPL_LOOKUP.findVirtual(Module.class, "implAddExports", MethodType.methodType(void.class, String.class, Module.class));
            implAddExports.invokeExact(/*java.base*/Object.class.getModule(), packageName, /*golemcore*/UnsafeBootstrap.class.getModule());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to export " + packageName + " to " + UnsafeBootstrap.class.getModule(), t);
        }
    }

    private static MethodHandles.Lookup bootstrapImplLookup() {
        try {
            Class<?> bridge = new LookupBridgeLoader(UnsafeBootstrap.class.getClassLoader()).defineLookupBridge();
            return (MethodHandles.Lookup) bridge.getMethod("lookup").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to bootstrap IMPL_LOOKUP", e);
        }
    }

    private static byte[] lookupBridgeBytes() {
        String className = LookupBridgeLoader.BRIDGE_NAME.replace('.', '/');
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, className, null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", null, null);
        mv.visitCode();
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        mv.visitTryCatchBlock(start, end, handler, "java/lang/ReflectiveOperationException");
        mv.visitLabel(start);
        mv.visitLdcInsn(Type.getType("Ljava/lang/invoke/MethodHandles$Lookup;"));
        mv.visitLdcInsn("IMPL_LOOKUP");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/AccessibleObject", "setAccessible", "(Z)V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/invoke/MethodHandles$Lookup");
        mv.visitLabel(end);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(handler);
        mv.visitVarInsn(Opcodes.ASTORE, 0);
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V", false);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static final class LookupBridgeLoader extends ClassLoader {
        private static final String BRIDGE_NAME = "dev.golem.bootstrap.LookupBridge";

        private LookupBridgeLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> defineLookupBridge() {
            byte[] bytes = lookupBridgeBytes();
            return defineClass(BRIDGE_NAME, bytes, 0, bytes.length);
        }
    }
}
//?}
