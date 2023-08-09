package tools.redstone.abstracraft.core;

public class NotImplementedException extends RuntimeException {

    private final ReferenceInfo method; // The method that wasn't implemented

    public NotImplementedException() {
        this(null);
    }

    public NotImplementedException(ReferenceInfo info) {
        this.method = info;
    }

    public ReferenceInfo getMethod() {
        return method;
    }

    @Override
    public String getMessage() {
        if (method == null) return null;
        return "Method " + method + " is not implemented";
    }

}
