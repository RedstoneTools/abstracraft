package test.abstracraft;

import org.junit.jupiter.api.Test;
import tools.redstone.abstracraft.core.*;

// Emulates a realistic feature-like system.
public class EmulatedTest {

    @Test
    void test_v1() {
        // Setup
        ClassLoader classLoader = ClassUtil.transformingClassLoader(RequiredDependencyFinder::transformClass);
        AbstractionManager abstractionManager = new AbstractionManager();

        // Register Implementation
        abstractionManager.registerImpl(WorldImpl_V1.class);

        // Test Features
    }

    @Test
    void test_v2() {

    }

    /* -------- Abstractions -------- */
    @BaseAbstraction
    interface World extends DynamicExtensible, Abstraction {
        String getName();
        String getDefaultBlock();
    }

    interface SetBlockSupport {
        void setBlock(int x, int y, int z, String name);
    }

    interface GetBlockSupport {
        String getBlock(int x, int y, int z);
    }

    /* -------- Implementations -------- */
    class WorldImpl_V1 implements World, SetBlockSupport {
        @Override
        public String getName() {
            return "MyWorld";
        }

        @Override
        public String getDefaultBlock() {
            return "defaultBlock";
        }

        @Override
        public void setBlock(int x, int y, int z, String name) {
            // too lazy to do shit lol
        }
    }

    class WorldImpl_V2 implements World, GetBlockSupport, SetBlockSupport {
        @Override
        public String getName() {
            return "MyWorld";
        }

        @Override
        public String getDefaultBlock() {
            return "defaultBlock";
        }

        @Override
        public String getBlock(int x, int y, int z) {
            return "block";
        }

        @Override
        public void setBlock(int x, int y, int z, String name) {
            // too lazy to do shit lol
        }
    }

    /* -------- Features -------- */
    class BlockSetFeature {
        void execute(World world) {
            world.require(SetBlockSupport.class).setBlock(1, 2, 3, "block2");
        }
    }

    class BlockGetFeature {
        String execute(World world) {
            return world.optionally(GetBlockSupport.class)
                    .map(s -> s.getBlock(0, 0, 0))
                    .orElse(world.getDefaultBlock());
        }
    }

}
