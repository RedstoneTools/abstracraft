package tools.redstone.abstracraft.core;

import org.objectweb.asm.Type;

import java.lang.reflect.Method;

// ASM method information
public record MethodInfo(String ownerInternalName, String ownerClassName, String name, String desc, Type asmType) {
    public static MethodInfo forMethod(Method method) {
        String desc = Type.getMethodDescriptor(method);
        return new MethodInfo(method.getDeclaringClass().getName().replace('.', '/'),
                method.getDeclaringClass().getName(),
                method.getName(),
                desc,
                Type.getMethodType(desc));
    }

    public static MethodInfo forInfo(String ownerName, String name, String desc) {
        return new MethodInfo(ownerName.replace('.', '/'), ownerName.replace('/', '.'),
                name, desc, Type.getMethodType(desc));
    }

    public static MethodInfo forInfo(Class<?> klass, String name, Class<?> returnType, Class<?>... argTypes) {
        Type type = Type.getMethodType(Type.getType(returnType), ASMUtil.asTypes(argTypes));
        return new MethodInfo(klass.getName().replace('.', '/'), klass.getName(),
                name, type.getDescriptor(), type);
    }

    @Override
    public String toString() {
        return ownerClassName + "." + name + desc;
    }
}
