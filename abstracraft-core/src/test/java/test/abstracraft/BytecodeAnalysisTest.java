package test.abstracraft;

import org.junit.jupiter.api.Test;
import tools.redstone.abstracraft.core.RawOptionalMethod;

public class BytecodeAnalysisTest {

    static class MyMethod implements RawOptionalMethod {
        void call(Object a, int b) {

        }
    }

    public static final MyMethod myMethod = new MyMethod();

    class TestClass {
        void abc() {
            myMethod.call("abc", 4);
        }
    }

    @Test
    void test_GetRequiredDependencies() {

    }

}
