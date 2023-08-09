package test.abstracraft.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import tools.redstone.abstracraft.core.*;

public class SimpleDependencyTest {

    public static void main(String[] args) throws Throwable {
        TestSystem.runTests(SimpleDependencyTest.class, true);
    }

    /* --------------------------------------------------- */

    /** Example abstraction */
    public interface Abc extends Abstraction {
        default String a() { return unimplemented(); }
        default String b() { return unimplemented(); }
        default String c() { return unimplemented(); }
        default String d() { return "DDDDDD"; }
        default String e() { return unimplemented(); }
    }

    /** Example impl */
    public static class AbcImpl implements Abc {
        @Override
        public String a() {
            return "AAAAAA";
        }
    }

    /* --------------------------------------------------- */

    public interface Tests {
        default String testA(Abc abc) { return null; }
        default String testB(Abc abc) { return null;  }
        default String testC(Abc abc) { return null;  }
        default String testD(Abc abc) { return null;  }
        String testE(Abc abc);
    }

    /** The class with test code */
    @Disabled
    public static class TestClass implements Tests {
        String deep(Abc abc) {
            return abc.c();
        }

        public String testA(Abc abc) {
            return abc.a();
        }

        public String testB(Abc abc) {
            return Usage.requireAtLeastOne(() -> deep(abc), abc::b, abc::d);
        }

        @Override
        public String testC(Abc abc) {
            return abc.b();
        }

        @Override
        public String testD(Abc abc) {
            return Usage.requireAtLeastOne(abc::b, abc::c);
        }

        @Override
        public String testE(Abc abc) {
            return Usage.optionally(() -> abc.e())
                    .orElse("ABC");
        }
    }

    @TestSystem.Test(testClass = "TestClass", abstractionImpl = "AbcImpl")
    void test_Unimplemented(Tests testInstance, AbstractionManager abstractionManager, Abc abc) throws Throwable {
        Assertions.assertTrue(abstractionManager.isImplemented(ReferenceInfo.forMethodInfo(Abc.class, "d", String.class)));
        Assertions.assertTrue(abstractionManager.isImplemented(ReferenceInfo.forMethodInfo(Abc.class, "a", String.class)));
        Assertions.assertFalse(abstractionManager.isImplemented(ReferenceInfo.forMethodInfo(Abc.class, "c", String.class)));
        Assertions.assertFalse(abstractionManager.allImplemented(testInstance.getClass()));
        Assertions.assertDoesNotThrow(() -> testInstance.testA(abc));
        Assertions.assertDoesNotThrow(() -> testInstance.testB(abc));
        Assertions.assertThrows(NotImplementedException.class, () -> testInstance.testC(abc));
        Assertions.assertThrows(NoneImplementedException.class, () -> testInstance.testD(abc));
        Assertions.assertEquals("ABC", testInstance.testE(abc));
        TestSystem.assertDependenciesEquals(abstractionManager.getClassAnalysis(testInstance.getClass()).dependencies, "required Abc.a", "required Abc.b", "required Abc.d", "optional Abc.c", "optional Abc.e");
    }

}
