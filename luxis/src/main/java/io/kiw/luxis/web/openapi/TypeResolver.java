package io.kiw.luxis.web.openapi;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class TypeResolver {
    private TypeResolver() {
    }

    public static Type[] resolveTypeArguments(final Class<?> concreteClass, final Class<?> targetSuperclass) {
        if (targetSuperclass.isInterface()) {
            return resolveFromInterfaces(concreteClass, targetSuperclass);
        }
        return resolveFromSuperclass(concreteClass, targetSuperclass);
    }

    private static Type[] resolveFromSuperclass(final Class<?> concreteClass, final Class<?> targetSuperclass) {
        Type type = concreteClass.getGenericSuperclass();
        while (type != null) {
            if (type instanceof ParameterizedType pt) {
                if (pt.getRawType() == targetSuperclass) {
                    return pt.getActualTypeArguments();
                }
            }
            if (type instanceof Class<?> cls) {
                type = cls.getGenericSuperclass();
            } else {
                break;
            }
        }
        return null;
    }

    private static Type[] resolveFromInterfaces(final Class<?> concreteClass, final Class<?> targetInterface) {
        for (final Type iface : concreteClass.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType pt && pt.getRawType() == targetInterface) {
                return pt.getActualTypeArguments();
            }
        }
        final Class<?> superclass = concreteClass.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return resolveFromInterfaces(superclass, targetInterface);
        }
        return null;
    }
}
