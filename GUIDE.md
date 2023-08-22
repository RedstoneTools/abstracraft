## How to use abstracraft in a feature
Abstracraft provides abstractions for Minecraft features powered by the `abstracraft-core` module. 
It provides a way to cleanly use abstractions which may change across versions
without any runtime checks. Internally it utilizes bytecode analysis/transformation.

The [`Usage`](https://github.com/RedstoneTools/abstracraft/blob/dev/abstracraft-core/src/main/java/tools/redstone/abstracraft/usage/Usage.java) class provides the most important methods for usage of abstractions in your code.
These methods here have extensive javadocs which you can always check for information.
There are 3 important methods:
- `boolean optionally(Runnable code)` - Executes the given block and returns true if the dependencies
referenced in the given runnable are all implemented, otherwise it returns false.
- `Optional<T> optionally(Supplier<T> code)` - Executes the given block of code and returns the result as
a present optional if all dependencies required by the given code block are 
implemented, otherwise it returns an empty optional.
- `T either(Supplier<T>... codeBlocks)` - Requires at least one of the blocks provided to be implemented.
If so it chooses the first implemented block and executes it returning the result, otherwise calling the method
will throw a `NoneImplementedException`.

Referencing a method normally (outside one of these blocks) is known as a 
required reference. If the dependency is present the call will execute normally,
otherwise it throws a `NotImplementedException`.

The feature system is responsible for omitting any features which have unimplemented required dependencies
from being loaded and available to the user.

**Example:**
Say you have a feature class `MyFeature`.
```java
import static tools.redstone.abstracraft.usage.Usage.*;

public class MyFeature extends CommandFeature {
    // Called when executing the command associated
    // with this feature.
    void execute(CommandContext ctx) {
        // Get the World *abstraction*
        World world = ctx.source().world();
        
        // Make a *required* reference to `.setWeather`
        // If weather is not supported in a specific version, the `setWeather`
        // method will not be implemented and this code shouldn't run. If it does
        // run it will throw a NotImplementedException.
        world.setWeather("clear");
        
        // Make an *optional* reference to `getEntitiesInRange`.
        // On versions where `getEntitiesInRange` is supported, this call will run that code block
        // and return a present optional, not executing the `orElse` call.
        List<Entity> entitiesA = optionally(() -> world.getEntitiesInRange(ctx.source().pos(), 20))
                .orElse(world.getEntities());
        
        // Another way to write that, which is preferred in this scenario,
        // is using `either`.
        
        // Here we say that either `getEntitiesInRange` or `getEntities` has to be
        // implemented. If they're not, this code shouldn't run because the feature shouldn't
        // be loaded.
        List<Entity> entitiesB = either(
                () -> world.getEntitiesInRange(ctx.source().pos(), 20),
                () -> world.getEntities()
        );
        
        // Note that all of these parameters are suppliers/runnables, which can be written using
        // () -> { return ...; } lambdas as well.
        // Example:
        optionally(/* Runnable */ () -> {
            world.setBlock(0, 0, 0, Blocks.DIRT);
            world.setBlock(0, 1, 0, Blocks.GRASS_BLOCK);
        });
    }
}
```