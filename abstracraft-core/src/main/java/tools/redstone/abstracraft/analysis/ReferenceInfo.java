package tools.redstone.abstracraft.analysis;

import org.objectweb.asm.Type;
import tools.redstone.abstracraft.util.ASMUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

// ASM method/field information
public class ReferenceInfo {

    final String ownerInternalName;
    final String ownerClassName;
    final String name;
    final String desc;
    final Type type;
    final boolean isStatic;

    // cached hash code
    int hashCode = 0;

    public ReferenceInfo(String ownerInternalName, String ownerClassName, String name, String desc, Type type, boolean isStatic) {
        this.ownerInternalName = ownerInternalName;
        this.ownerClassName = ownerClassName;
        this.name = name;
        this.desc = desc;
        this.type = type;
        this.isStatic = isStatic;
    }

    public String ownerInternalName() {
        return ownerInternalName;
    }

    public String ownerClassName() {
        return ownerClassName;
    }

    public String name() {
        return name;
    }

    public String desc() {
        return desc;
    }

    public Type type() {
        return type;
    }

    public boolean isStatic() {
        return isStatic;
    }

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReferenceInfo that = (ReferenceInfo) o;
        return isStatic == that.isStatic && hashCode == that.hashCode && Objects.equals(ownerInternalName, that.ownerInternalName) && Objects.equals(ownerClassName, that.ownerClassName) && Objects.equals(name, that.name) && Objects.equals(desc, that.desc) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        if (hashCode != 0)
            return hashCode;

        int hc = 1;
        hc = 31 * hc + ownerInternalName.hashCode();
        hc = 31 * hc + name.hashCode();
        hc = 31 * hc + desc.hashCode();
        hc = 31 * hc + (isStatic ? 1 : 0);
        return this.hashCode = hc;
    }

    @Override
    public String toString() {
        if (isField()) return "field " + ownerClassName + "." + name + ":" + desc;
        else return "method " + ownerClassName + "." + name + desc;
    }
}
