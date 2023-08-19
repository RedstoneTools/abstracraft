package test.abstracraft.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.redstone.abstracraft.AbstractionManager;
import tools.redstone.abstracraft.usage.Abstraction;
import tools.redstone.abstracraft.util.PackageWalker;

public class FindImplTest {

    interface A extends Abstraction { }
    interface B extends Abstraction { }

    static class AImpl implements A { }
    static class BImpl implements B { }

    @Test
    void test_FindAndRegisterImpls() {
        final AbstractionManager abstractionManager = new AbstractionManager();
        abstractionManager.registerImplsFromResources(
                new PackageWalker(this.getClass(), "test.abstracraft.core")
                .findResources()
                .filter(r -> r.trimmedName().endsWith("Impl"))
        );

        // check impls registered
        Assertions.assertEquals(AImpl.class, abstractionManager.getImplByClass(A.class));
        Assertions.assertEquals(BImpl.class, abstractionManager.getImplByClass(B.class));
    }

}
