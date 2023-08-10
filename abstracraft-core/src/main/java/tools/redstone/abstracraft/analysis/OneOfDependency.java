package tools.redstone.abstracraft.analysis;

import tools.redstone.abstracraft.AbstractionManager;

import java.util.List;
import java.util.Objects;

/**
 * Records the result of a dependency switch.
 */
public record OneOfDependency(List<MethodDependency> dependencies, List<MethodDependency> optionalDependencies, boolean implemented) implements Dependency {
    @Override
    public boolean isImplemented(AbstractionManager manager) {
        return this.implemented;
    }

    @Override
    public boolean equals(Object o) {
        return this == o; // there shouldn't be any duplicates so this isnt important
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this); // there shouldn't be any duplicates so this isnt important
    }
}
