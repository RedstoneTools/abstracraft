package tools.redstone.abstracraft.abstractions;

import java.util.Objects;

public abstract class Method<TSelf, TThis extends Method<TSelf, TThis>> {
    private final TSelf self;

    public Method() {
        this.self = null;
    }

    protected Method(TSelf self) {
        this.self = self;
    }

    public final TThis bind(TSelf self) {
        throwIfAlreadyBound();

        return withSelf(self);
    }

    protected final TSelf getSelf() {
        return Objects.requireNonNull(self);
    }

    private void throwIfAlreadyBound() {
        if (isBound()) {
            throw new RuntimeException(this + " is already bound to " + self);
        }
    }

    private boolean isBound() {
        return this.self != null;
    }

    protected abstract TThis withSelf(TSelf self);
}
