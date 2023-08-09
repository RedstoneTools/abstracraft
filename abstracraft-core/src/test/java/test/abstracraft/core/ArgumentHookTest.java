package test.abstracraft.core;

import org.objectweb.asm.Type;
import tools.redstone.abstracraft.core.*;

import java.util.*;

public class ArgumentHookTest {

    public static void main(String[] args) {
        TestSystem.runTests(ArgumentHookTest.class, true);
    }

    public static class Argument {
        public int bound;

        public Argument(int bound) {
            this.bound = bound;
        }
    }

    public interface CommandContext extends Abstraction {
        default int get(Argument argument) {
            return new Random().nextInt(argument.bound);
        }

        default String a() { return unimplemented(); }
        default String b() { return unimplemented(); }
        default String c() { return unimplemented(); }
        default String d() { return "DDDDDD"; }
    }

    /** Example impl */
    public static class CommandContextImpl implements CommandContext {
        @Override
        public String a() {
            return "AAAAAA";
        }
    }

    public static class MyHook implements DependencyAnalysisHook {
        static final String NAME_CommandContext = Type.getType(CommandContext.class).getInternalName();

        // Registers whether fields should be registered or not
        final Set<ReferenceInfo> excludeFields = new HashSet<>();

        final Map<ReferenceInfo, ReferenceHook> referenceHooksByField = new HashMap<>();

        public ReferenceHook refHook(AnalysisContext context, ClassDependencyAnalyzer.ReferenceAnalysis called, boolean optional) {
            if (!called.isField())
                return null;
            ReferenceInfo fieldInfo = called.ref;

            // check for cached hook
            ReferenceHook hook = referenceHooksByField.get(fieldInfo);
            if (hook != null)
                return hook;

            // check for argument field type
            if (!Argument.class.isAssignableFrom(ASMUtil.asClass(called.ref.type())))
                return null;

            // return the hook
            referenceHooksByField.put(fieldInfo, hook = new ReferenceHook() {
                int referenceCounter = 0;

                @Override
                public void requiredReference(AnalysisContext context) {
                    referenceCounter++;
                }

                @Override
                public void optionalReference(AnalysisContext context) {
                    referenceCounter++;
                }

                @Override
                public void optionalBlockDiscarded(AnalysisContext context) {
                    referenceCounter -= 2;
                }

                @Override
                public void postAnalyze() {
                    if (referenceCounter <= 0)
                        excludeFields.add(fieldInfo);
                }
            });

            return hook;
        }

        @Override
        public ReferenceHook optionalReference(AnalysisContext context, ClassDependencyAnalyzer.ReferenceAnalysis called) {
            return refHook(context, called, true);
        }

        @Override
        public ReferenceHook requiredReference(AnalysisContext context, ClassDependencyAnalyzer.ReferenceAnalysis called) {
            return refHook(context, called, false);
        }
    }

    /* --------------------------------------------------- */

    public interface Tests {
        void testA(CommandContext ctx);
    }

    public static class TestClass implements Tests {
        Argument a;
        Argument b;
        Argument c;

        @Override
        public void testA(CommandContext ctx) {
            ctx.get(a); // This should be required

            Usage.optionally(() -> {
                ctx.a();
                ctx.get(b); // The should be registered because ctx.a() is implemented
            });

            Usage.optionally(() -> {
                ctx.b();
                ctx.get(c); // This should not be registered because ctx.b() is not implemented
            });
        }
    }

    @TestSystem.Test(testClass = "TestClass", abstractionImpl = "CommandContextImpl", hooks = {"MyHook"})
    void test_ArgHook(MyHook hook, Tests tests, CommandContext ctx) {
        System.out.println("Exclude Argument fields: " + hook.excludeFields);
    }

}
