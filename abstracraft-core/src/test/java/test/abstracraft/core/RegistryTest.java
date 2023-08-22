package test.abstracraft.core;

import org.junit.jupiter.api.Test;
import tools.redstone.abstracraft.registry.Keyed;
import tools.redstone.abstracraft.registry.KeyedRegistry;
import tools.redstone.abstracraft.registry.Registry;

public class RegistryTest {

    record Block(String id, int hardness) implements Keyed<String> {
        @Override
        public String getKey() {
            return id;
        }
    }

    @Test
    void test_UnorderedRegistry() {
        var registry = KeyedRegistry.ordered(Block.class);
        registry.register(new Block("minecraft:dirt", 3));
        registry.register(new Block("minecraft:stone", 10));
    }

}
