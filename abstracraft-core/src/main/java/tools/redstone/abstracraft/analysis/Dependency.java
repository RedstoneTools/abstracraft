package tools.redstone.abstracraft.analysis;

import tools.redstone.abstracraft.AbstractionManager;

public interface Dependency {

    /** Check whether this dependency is fully implemented */
    boolean isImplemented(AbstractionManager manager);

}
