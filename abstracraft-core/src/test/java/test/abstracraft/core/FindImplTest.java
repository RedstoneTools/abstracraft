package test.abstracraft.core;

import org.junit.jupiter.api.Assertions;
import tools.redstone.abstracraft.AbstractionManager;
import tools.redstone.abstracraft.usage.Abstraction;
import tools.redstone.abstracraft.util.PackageWalker;
import tools.redstone.abstracraft.util.ReflectUtil;

public class FindImplTest {

    public static void main(String[] args) {
        new FindImplTest().test_FindAndRegisterImpls();
    }

    public interface A extends Abstraction { }
    public interface B extends Abstraction { }

    public static class AImpl implements A { }
    public static class BImpl implements B { }

    void test_FindAndRegisterImpls() {
        ReflectUtil.ensureLoaded(A.class);
        ReflectUtil.ensureLoaded(B.class);
        final AbstractionManager abstractionManager = new AbstractionManager();
        abstractionManager.registerImplsFromResources(
                new PackageWalker(this.getClass(), "test.abstracraft.core")
                .findResources()
                .filter(r -> r.trimmedName().endsWith("Impl"))
        );

        // check impls registered
        Assertions.assertEquals(AImpl.class.getName(), abstractionManager.getImplByClass(A.class).getName());
        Assertions.assertEquals(BImpl.class.getName(), abstractionManager.getImplByClass(B.class).getName());
    }

}
