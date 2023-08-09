package tools.redstone.abstracraft.core.usage;

/** Purely to denote that a class is an abstraction */
public interface Abstraction {

    /**
     * Method which signifies that the caller is not implemented.
     *
     * @throws NotImplementedException Always.
     */
    default <T> T unimplemented() {
        throw new NotImplementedException(null);
    }

}
