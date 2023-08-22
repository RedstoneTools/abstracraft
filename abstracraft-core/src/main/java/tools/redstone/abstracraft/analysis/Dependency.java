package tools.redstone.abstracraft.analysis;

import tools.redstone.abstracraft.AbstractionProvider;

public interface Dependency {

    /** Check whether this dependency is fully implemented */
    boolean isImplemented(AbstractionProvider manager);

}
