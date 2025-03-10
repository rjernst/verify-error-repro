import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Base64;

public class TestAgent {

    private static final String CLASS_TO_BREAK = "java.time.Duration";
    private static final String INTERNAL_CLASS_TO_BREAK = CLASS_TO_BREAK.replace('.', '/');

    private static class BadTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            //System.out.println("Maybe transforming module class " + className);
            if (className.equals(INTERNAL_CLASS_TO_BREAK)) {
                System.out.println("Instrumenting modular class " + INTERNAL_CLASS_TO_BREAK);

                var methodTransform = MethodTransform.transformingCode((builder, element) -> {
                    if (element instanceof ReturnInstruction) {
                        System.out.println("Injecting bug");
                        // THE BUG! insert broken function call

                        var checkerDesc = ClassDesc.of("checker", "TestChecker");
                        builder.invokestatic(checkerDesc, "instance", MethodTypeDesc.of(checkerDesc), true);

                        // dup the instance ref, this is just to get a bad argument to the next method call
                        builder.dup();

                        // then call a check method that doesn't take that type, but we have the wrong desc
                        builder.invokeinterface(checkerDesc, "check", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Integer));

                        System.out.println("Done injecting bug");
                    }
                    builder.with(element);
                });
                var classTransform = ClassTransform.transformingMethods(mm -> mm.methodName().stringValue().equals("getSeconds"), methodTransform);

                byte[] bytes;
                try {
                    var cf = ClassFile.of();
                    var existingClass = cf.parse(classfileBuffer);
                    bytes = cf.transformClass(existingClass, classTransform);

                    Files.write(Path.of("bad.class"), bytes);
                } catch (Throwable e) {
                    System.out.println(e);
                    throw new AssertionError(e);
                }
                return bytes;
            }
            return null;
        }
    }

    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        System.out.println("Premain");
        // double check our class hasn't been loaded yet
        for (Class clazz : inst.getAllLoadedClasses()) {
            if (clazz.getName().equals(CLASS_TO_BREAK)) {
                throw new AssertionError("Oops! Class " + CLASS_TO_BREAK + " is already loaded, the test can't work");
            }
        }

        boolean retransform = Boolean.getBoolean("agent.retransform");
        if (retransform) {
            // for the bug the class we call must be already loaded, so that we retransform it
            var clazz = Class.forName(CLASS_TO_BREAK);
            inst.addTransformer(new BadTransformer(), true);
            inst.retransformClasses(clazz);
        } else {
            // no verify error, the class is used in TestMain
            inst.addTransformer(new BadTransformer());
        }
    }
}