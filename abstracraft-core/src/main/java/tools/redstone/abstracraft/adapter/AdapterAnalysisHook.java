package tools.redstone.abstracraft.adapter;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceClassVisitor;
import tools.redstone.abstracraft.AbstractionManager;
import tools.redstone.abstracraft.analysis.*;
import tools.redstone.abstracraft.util.ASMUtil;
import tools.redstone.abstracraft.util.MethodWriter;

import java.io.PrintWriter;
import java.util.function.Function;

public class AdapterAnalysisHook implements ClassAnalysisHook {

    static final Type TYPE_Object = Type.getType(Object.class);
    static final Type TYPE_AdapterRegistry = Type.getType(AdapterRegistry.class);
    static final Type TYPE_Function = Type.getType(Function.class);
    static final String NAME_Function = TYPE_Function.getInternalName();

    private final AdapterRegistry adapterRegistry;                               // The adapter registry to source adapters from
    private int adapterIdCounter = 0;                                            // The counter for the $$adapter_xx fields
    private final AbstractionManager.ClassInheritanceChecker inheritanceChecker; // The inheritance checker to check for `adapt` calls

    public AdapterAnalysisHook(Class<?> adaptMethodOwner, AdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
        inheritanceChecker = AbstractionManager.ClassInheritanceChecker.forClass(adaptMethodOwner);
    }

    static class TrackedReturnValue {
        final ClassDependencyAnalyzer.ReturnValue returnValue; // The analyzer return value
        String dstType;                                        // The destination type

        TrackedReturnValue(ClassDependencyAnalyzer.ReturnValue returnValue) {
            this.returnValue = returnValue;
        }
    }

    @Override
    public MethodVisitorHook visitMethod(AnalysisContext context, MethodWriter writer) {
        final ReferenceInfo currMethod = context.currentMethod();
        final ReferenceAnalysis currAnalysis = context.currentAnalysis();
        return new MethodVisitorHook() {
            @Override
            public boolean visitMethodInsn(AnalysisContext ctx, int opcode, ReferenceInfo info) {
                // check for #adapt(Object)
                if (inheritanceChecker.checkClassInherits(ctx.abstractionManager(), info.className()) && info.name().equals("adapt")) {
                    boolean isStatic = opcode == Opcodes.INVOKESTATIC;
                    Object instanceValue = isStatic ? context.currentComputeStack().pop() : null;
                    Object srcValue = context.currentComputeStack().pop();
                    String srcType = ((ClassDependencyAnalyzer.StackValue)srcValue).signature();

                    // push tracked return value
                    var trackedReturnValue = new TrackedReturnValue(new ClassDependencyAnalyzer.ReturnValue(info, TYPE_Object, TYPE_Object.toString()));
                    context.currentComputeStack().push(trackedReturnValue);

                    // add field to class
                    String fieldName = "$$adapter_" + (adapterIdCounter++);
                    currAnalysis.classNode()
                            // create static field
                            .visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, fieldName, TYPE_Function.getDescriptor(), TYPE_Function.getDescriptor(), null /* set in static initializer */);

                    // potentially modify <clinit>
                    writer.addInsn(v -> {
                        String dstType = trackedReturnValue.dstType;
                        if (dstType == null)
                            return;

                        // modify <cinit>
                        var mCInit = ASMUtil.findMethod(currAnalysis.classNode(), "<clinit>", "()V");
                        boolean created = false;
                        if (mCInit == null) {
                            mCInit = (MethodNode) currAnalysis.classNode().visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "<clinit>", "()V", "()V", new String[] { });
                            created = true;
                        }

                        mCInit.visitCode();

                        mCInit.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE_AdapterRegistry.getInternalName(), "getInstance", "()L" + TYPE_AdapterRegistry.getInternalName() + ";", false);
                        mCInit.visitLdcInsn(srcType);
                        mCInit.visitLdcInsn(dstType);
                        mCInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TYPE_AdapterRegistry.getInternalName(), "lazyRequireMonoDirectional", "(Ljava/lang/String;Ljava/lang/String;)" + TYPE_Function.getDescriptor(), false);
                        mCInit.visitFieldInsn(Opcodes.PUTSTATIC, currMethod.internalClassName(), fieldName, TYPE_Function.getDescriptor());
                        if (created) mCInit.visitInsn(Opcodes.RETURN);

                        mCInit.visitEnd();
                    });

                    // replace instruction
                    writer.addInsn(v -> {
                        // if, when we come to write this instruction,
                        // the dst type still has not been determined we throw an error
                        String dstType = trackedReturnValue.dstType;
                        if (dstType == null)
                            throw new IllegalStateException("Could not determine dst type for `adapt(value)` call in " + currMethod + " with src type `" + srcType + "`");

                        // check if the adapter exists
                        if (adapterRegistry.findMonoDirectional(srcType, dstType) == null)
                            throw new IllegalStateException("No adapter found for src = " + srcType + ", dst = " + dstType + " in method " + currMethod);

                        // pop original instance variable after
                        if (!isStatic) {
                                                       // val - this
                            v.visitInsn(Opcodes.SWAP); // this - val
                            v.visitInsn(Opcodes.POP);  // val
                        }

                        // push adapter and swap
                        v.visitVarInsn(Opcodes.ALOAD, 0);
                        v.visitFieldInsn(Opcodes.GETFIELD, currMethod.internalClassName(), fieldName, TYPE_Function.getDescriptor());
                        v.visitInsn(Opcodes.SWAP); // val - function

                        // make it call Function#apply
                        v.visitMethodInsn(Opcodes.INVOKEINTERFACE, NAME_Function, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
                    });

                    return true;
                }

                // didn't intercept shit
                return false;
            }

            @Override
            public boolean visitVarInsn(AnalysisContext ctx, int opcode, int varIndex, Type type, String signature) {
                if (opcode == Opcodes.ASTORE && ctx.currentComputeStack().peek() instanceof TrackedReturnValue rv) {
                    ctx.currentComputeStack().pop();

                    rv.dstType = signature;
                }

                return false;
            }

            @Override
            public boolean visitFieldInsn(AnalysisContext ctx, int opcode, ReferenceInfo fieldInfo) {
                if (opcode == Opcodes.PUTFIELD && ctx.currentComputeStack().peek() instanceof TrackedReturnValue rv) {
                    ctx.currentComputeStack().pop();

                    rv.dstType = fieldInfo.signature() == null ? fieldInfo.desc() : fieldInfo.signature();
                }

                return false;
            }

            @Override
            public boolean visitInsn(AnalysisContext ctx, int opcode) {
                if (opcode == Opcodes.ARETURN && !ctx.currentComputeStack().isEmpty() && ctx.currentComputeStack().peek() instanceof TrackedReturnValue rv) {
                    ctx.currentComputeStack().pop();

                    rv.dstType = ctx.currentMethod().type().getReturnType().getDescriptor();
                }

                return false;
            }

            @Override
            public boolean visitTypeInsn(AnalysisContext ctx, int opcode, Type type) {
                if (opcode == Opcodes.CHECKCAST && !ctx.currentComputeStack().isEmpty() && ctx.currentComputeStack().peek() instanceof TrackedReturnValue rv) {
                    ctx.currentComputeStack().pop();

                    rv.dstType = type.getDescriptor();
                }

                return false;
            }
        };
    }
}
