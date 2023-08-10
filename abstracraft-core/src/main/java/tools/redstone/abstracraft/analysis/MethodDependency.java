package tools.redstone.abstracraft.analysis;

import tools.redstone.abstracraft.AbstractionManager;

import java.util.Objects;

/**
 * Represents the dependency of a piece of code on a specific method.
 */
public record MethodDependency(
        boolean optional,
        ReferenceInfo info,
        Boolean implemented
) implements Dependency {
    public MethodDependency asOptional(boolean optional) {
        return new MethodDependency(optional, info, implemented);
    }

    @Override
    public boolean isImplemented(AbstractionManager abstractionManager) {
        if (implemented != null)
            return implemented;
        return abstractionManager.isImplemented(info);
    }

    public String toString() {
        return "MethodDependency(" + (optional ? "optional " : "required ") +
                info.ownerClassName() + "." + info.name() +
                ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodDependency that = (MethodDependency) o;
        return info.equals(that.info) && implemented == that.implemented;
    }

    @Override
    public int hashCode() {
        return info.hashCode() + 31 * (implemented == Boolean.TRUE ? 1 : 0);
    }
}
