package tools.redstone.abstracraft.core;

import org.objectweb.asm.Type;

import java.lang.reflect.Method;

// ASM method/field information
public record ReferenceInfo(String ownerInternalName, String ownerClassName, String name, String desc, Type type) {
    public static ReferenceInfo forMethod(Method method) {
        String desc = Type.getMethodDescriptor(method);
        return new ReferenceInfo(method.getDeclaringClass().getName().replace('.', '/'),
                method.getDeclaringClass().getName(),
                method.getName(),
                desc,
                Type.getMethodType(desc));
    }

    public static ReferenceInfo forMethodInfo(String ownerName, String name, String desc) {
        return new ReferenceInfo(ownerName.replace('.', '/'), ownerName.replace('/', '.'),
                name, desc, Type.getMethodType(desc));
    }

    public static ReferenceInfo forMethodInfo(Class<?> klass, String name, Class<?> returnType, Class<?>... argTypes) {
        Type type = Type.getMethodType(Type.getType(returnType), ASMUtil.asTypes(argTypes));
        return new ReferenceInfo(klass.getName().replace('.', '/'), klass.getName(),
                name, type.getDescriptor(), type);
    }

    public static ReferenceInfo forFieldInfo(String ownerName, String name, String desc) {
        return new ReferenceInfo(ownerName.replace('.', '/'), ownerName.replace('/', '.'),
                name, desc, Type.getType(desc));
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
