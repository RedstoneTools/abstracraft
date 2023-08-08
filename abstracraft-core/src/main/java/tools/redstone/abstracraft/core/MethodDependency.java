package tools.redstone.abstracraft.core;

/**
 * Represents the dependency of a piece of code on a specific method.
 */
public record MethodDependency(
        boolean optional,
        MethodInfo info,
        // Details of the call of the method
        MethodInfo calledIn
) {
    public MethodDependency asOptional(boolean opt) {
        return new MethodDependency(opt, info, calledIn);
    }

    @Override
    public String toString() {
        return "MethodDependency(" + (optional ? "optional " : "required ") +
                info.ownerClassName() + "." + info.name() +
                ')';
    }
}
