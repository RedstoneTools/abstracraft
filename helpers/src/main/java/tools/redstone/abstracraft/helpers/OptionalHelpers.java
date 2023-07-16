package tools.redstone.abstracraft.helpers;

import java.util.Optional;

public class OptionalHelpers {
    private OptionalHelpers() {
    }

    public static <TThis, TSub extends TThis> Optional<TSub> getOptionalSubclass(TThis self, Class<TSub> subclass) {
        if (subclass.isInstance(self)) {
            return Optional.of(subclass.cast(self));
        }

        return Optional.empty();
    }
}
