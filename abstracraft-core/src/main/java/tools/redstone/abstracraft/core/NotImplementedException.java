package tools.redstone.abstracraft.core;

public class NotImplementedException extends RuntimeException {

    private final MethodInfo method; // The method that wasn't implemented

    public NotImplementedException(MethodInfo info) {
        this.method = info;
    }

    public MethodInfo getMethod() {
        return method;
    }

    @Override
    public String getMessage() {
        return "Method " + method.ownerClassName() + "." + method.name() + method.desc() + " is not implemented";
    }

}
