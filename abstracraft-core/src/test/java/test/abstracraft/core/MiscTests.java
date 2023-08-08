package test.abstracraft.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.redstone.abstracraft.core.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class MiscTests {

    final AbstractionManager abstractionManager = new AbstractionManager();

    public static void main(String[] args) throws Throwable {
        new MiscTests().test_Unimplemented();
    }

    // System.out.println
    static <T> T println(T o) {
        System.out.println(o);
        return o;
    }

    // Run code with exceptions caught
    static void runSafe(Executable executable) {
        try {
            executable.execute();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** Example abstraction */
    public interface Abc extends Abstraction {
        static Abc impl() {
            return (Abc) Proxy.newProxyInstance(Abc.class.getClassLoader(), new Class[] { Abc.class }, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("equals") || name.equals("hashCode")) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }

                return "Abc#" + method.getName();
            });
        }

        String a();
        String b();
        String c();
        String d();
    }

    public interface Tests {
        String testA(Abc abc);
        String testB(Abc abc);
    }

    /** The class with test code */
    @Disabled
    public static class TestClass implements Tests {
        String db(Abc abc) {
            return abc.c();
        }

        public String testA(Abc abc) {
            return abc.a();
        }

        public String testB(Abc abc) {
            return Usage.requireAtLeastOne(() -> {
                return db(abc);
            }, abc::c, abc::d);
        }
    }

    @Test
    void test_Unimplemented() throws Throwable {
        ReflectUtil.ensureLoaded(Tests.class); // ensure this is loaded so its not transformed

        ClassLoader loader = MethodDependencyAnalyzer.transformingLoader(
                n -> n.startsWith("test"),
                abstractionManager, this.getClass().getClassLoader());
        abstractionManager.setImplementedByDefault(false);
        abstractionManager.setImplemented(MethodInfo.forInfo(Abc.class, "d", String.class), true);
        abstractionManager.setImplemented(MethodInfo.forInfo(Abc.class, "a", String.class), true);

        Abc abc = Abc.impl();
        Tests testInstance = (Tests) Class.forName("test.abstracraft.core.MiscTests$TestClass", true, loader)
                .newInstance();

        // TEST CODE //
        runSafe(() -> println(testInstance.testA(abc)));
        runSafe(() -> println(testInstance.testB(abc)));
    }

}
