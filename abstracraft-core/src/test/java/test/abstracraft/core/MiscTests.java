package test.abstracraft.core;

import org.junit.jupiter.api.Test;
import tools.redstone.abstracraft.core.Abstraction;
import tools.redstone.abstracraft.core.MethodDependencyAnalyzer;
import tools.redstone.abstracraft.core.ReflectUtil;

import java.util.function.Supplier;

public class MiscTests {

    interface Abc extends Abstraction {
        void a();
    }

    static class TestClass {
        void myMethod(Supplier<Object> supplier) {
            supplier.get();
        }

        void abc() {
            Abc a = null;
            myMethod(() -> {
                a.a();
                return null;
            });
        }
    }

    @Test
    void test() {
        var analyzer = new MethodDependencyAnalyzer(null, ReflectUtil.reader(TestClass.class), null);
        analyzer.analyze();
        System.out.println(analyzer.getAnalysis());
    }

}
