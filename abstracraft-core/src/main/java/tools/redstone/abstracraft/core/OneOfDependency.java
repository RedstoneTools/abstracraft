package tools.redstone.abstracraft.core;

import java.util.List;

/**
 * Records the result of a dependency switch.
 */
public record OneOfDependency(List<MethodDependency> dependencies, List<MethodDependency> optionalDependencies, boolean implemented) {

}
