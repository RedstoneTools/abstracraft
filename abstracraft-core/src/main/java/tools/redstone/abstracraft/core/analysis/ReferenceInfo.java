package tools.redstone.abstracraft.core.analysis;

import org.objectweb.asm.Type;
import tools.redstone.abstracraft.core.util.ASMUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

// ASM method/field information
public record ReferenceInfo(String ownerInternalName, String ownerClassName, String name, String desc, Type type, boolean isStatic) {
    public static ReferenceInfo forMethod(Method method) {
        String desc = Type.getMethodDescriptor(method);
        return new ReferenceInfo(method.getDeclaringClass().getName().replace('.', '/'),
                method.getDeclaringClass().getName(),
                method.getName(),
                desc,
                Type.getMethodType(desc),
                Modifier.isStatic(method.getModifiers()));
    }

    public static ReferenceInfo forMethodInfo(String ownerName, String name, String desc, boolean isStatic) {
        return new ReferenceInfo(ownerName.replace('.', '/'), ownerName.replace('/', '.'),
                name, desc, Type.getMethodType(desc), isStatic);
    }

    public static ReferenceInfo forMethodInfo(Class<?> klass, String name, boolean isStatic, Class<?> returnType, Class<?>... argTypes) {
        Type type = Type.getMethodType(Type.getType(returnType), ASMUtil.asTypes(argTypes));
        return new ReferenceInfo(klass.getName().replace('.', '/'), klass.getName(),
                name, type.getDescriptor(), type, isStatic);
    }

    public static ReferenceInfo forFieldInfo(String ownerName, String name, String desc, boolean isStatic) {
        return new ReferenceInfo(ownerName.replace('.', '/'), ownerName.replace('/', '.'),
                name, desc, Type.getType(desc), isStatic);
    }

    /**
     * Check whether this reference describes a field.
     *
     * @return Whether it describes a field.
     */
    public boolean isField() {
        return type.getSort() != Type.METHOD;
    }

    @Override
    public String toString() {
        if (isField()) return "field " + ownerClassName + "." + name + ":" + desc;
        else return "method " + ownerClassName + "." + name + desc;
    }
}
