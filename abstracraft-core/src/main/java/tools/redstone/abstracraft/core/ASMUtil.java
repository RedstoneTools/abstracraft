package tools.redstone.abstracraft.core;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ASMUtil {

    /**
     * Find the method node by the given name and descriptor in the given class.
     *
     * @return The node or null if not found.
     */
    public static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode m : node.methods) {
            if (m.name.equals(name) && m.desc.equals(descriptor)) {
                return m;
            }
        }

        return null;
    }

    public static MethodNode findMethod(ClassNode node, MethodInfo info) {
        return findMethod(node, info.name(), info.desc());
    }

    public static Type[] getTypes(Class<?>[] classes) {
        Type[] types = new Type[classes.length];
        for (int i = 0, n = classes.length; i < n; i++)
            types[i] = Type.getType(classes[i]);
        return types;
    }

}
