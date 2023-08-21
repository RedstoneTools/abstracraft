package tools.redstone.abstracraft.util.data;

import java.util.Stack;

/**
 * Extended delegating stack.
 */
public class ExStack<T> extends Stack<T> implements Cloneable {

    public T popOrNull() {
        return isEmpty() ? null : pop();
    }

}
