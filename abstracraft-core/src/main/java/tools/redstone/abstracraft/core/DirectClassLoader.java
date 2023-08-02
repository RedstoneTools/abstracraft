package tools.redstone.abstracraft.core;

import java.security.Permissions;
import java.security.ProtectionDomain;

/**
 * A class loader which allows for classes to be defined directly from
 * a byte array.
 */
public class DirectClassLoader extends ClassLoader {

    public DirectClassLoader() {
        super(ClassLoader.getSystemClassLoader());
    }

    public DirectClassLoader(ClassLoader parent) {
        super(parent);
    }

    // the protection domain to
    // load the class in
    private ProtectionDomain protectionDomain;

    {
        try {
            Permissions permissions = new Permissions();
            permissions.add(new RuntimePermission("*"));
            protectionDomain = new ProtectionDomain(
                    null,
                    permissions
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Defines a new class.
     *
     * @param name The name.
     * @param code The class bytecode.
     * @return The class instance.
     */
    public Class<?> define(String name,
                           byte[] code) {
        return defineClass(name, code, 0, code.length,
                protectionDomain);
    }

}
