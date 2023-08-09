package tools.redstone.abstracraft.core.analysis;

import tools.redstone.abstracraft.core.AbstractionManager;

/**
 * Represents the dependency of a piece of code on a specific method.
 */
public record MethodDependency(
        boolean optional,
        ReferenceInfo info,
        Boolean implemented
) {
    public MethodDependency asOptional(boolean optional) {
        return new MethodDependency(optional, info, implemented);
    }

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
}
