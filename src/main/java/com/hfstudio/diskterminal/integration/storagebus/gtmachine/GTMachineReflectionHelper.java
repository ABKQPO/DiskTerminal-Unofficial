package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class GTMachineReflectionHelper {

    public static final Class<?>[] NO_ARG_TYPES = new Class<?>[0];
    public static final Class<?>[] INT_ARG_TYPES = new Class<?>[] { int.class };
    public static final Class<?>[] BOOLEAN_ARG_TYPES = new Class<?>[] { boolean.class };
    public static final Class<?>[] INT_ITEMSTACK_ARG_TYPES = new Class<?>[] { int.class, ItemStack.class };
    public static final Class<?>[] INT_FLUIDSTACK_ARG_TYPES = new Class<?>[] { int.class, FluidStack.class };

    private static final Map<FieldKey, Optional<Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<MethodKey, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();

    private GTMachineReflectionHelper() {}

    public static boolean hasClassName(Object target, String className) {
        return target != null && target.getClass()
            .getName()
            .equals(className);
    }

    public static boolean hasAnyClassName(Object target, Set<String> classNames) {
        return target != null && classNames.contains(
            target.getClass()
                .getName());
    }

    public static Optional<Boolean> readBooleanField(Object target, String fieldName) {
        return readField(target, fieldName).filter(Boolean.class::isInstance)
            .map(Boolean.class::cast);
    }

    public static Optional<Integer> readIntField(Object target, String fieldName) {
        return readField(target, fieldName).filter(Number.class::isInstance)
            .map(Number.class::cast)
            .map(Number::intValue);
    }

    public static Optional<Long> readLongField(Object target, String fieldName) {
        return readField(target, fieldName).filter(Number.class::isInstance)
            .map(Number.class::cast)
            .map(Number::longValue);
    }

    public static Optional<Object> readField(Object target, String fieldName) {
        if (target == null) {
            return Optional.empty();
        }

        try {
            Field field = findField(target.getClass(), fieldName);
            if (field == null) {
                return Optional.empty();
            }

            return Optional.ofNullable(field.get(target));
        } catch (IllegalAccessException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<ItemStack[]> readItemStackArrayField(Object target, String fieldName) {
        return readField(target, fieldName).filter(ItemStack[].class::isInstance)
            .map(ItemStack[].class::cast);
    }

    public static Optional<FluidStack[]> readFluidStackArrayField(Object target, String fieldName) {
        return readField(target, fieldName).filter(FluidStack[].class::isInstance)
            .map(FluidStack[].class::cast);
    }

    public static Optional<Boolean> invokeBoolean(Object target, String methodName, Class<?>[] parameterTypes,
        Object... args) {
        return invoke(target, methodName, parameterTypes, args).filter(Boolean.class::isInstance)
            .map(Boolean.class::cast);
    }

    public static Optional<Integer> invokeInt(Object target, String methodName, Class<?>[] parameterTypes,
        Object... args) {
        return invoke(target, methodName, parameterTypes, args).filter(Number.class::isInstance)
            .map(Number.class::cast)
            .map(Number::intValue);
    }

    public static Optional<Long> invokeLong(Object target, String methodName, Class<?>[] parameterTypes,
        Object... args) {
        return invoke(target, methodName, parameterTypes, args).filter(Number.class::isInstance)
            .map(Number.class::cast)
            .map(Number::longValue);
    }

    public static Optional<ItemStack> invokeItemStack(Object target, String methodName, Class<?>[] parameterTypes,
        Object... args) {
        return invoke(target, methodName, parameterTypes, args).filter(ItemStack.class::isInstance)
            .map(ItemStack.class::cast);
    }

    public static Optional<FluidStack> invokeFluidStack(Object target, String methodName, Class<?>[] parameterTypes,
        Object... args) {
        return invoke(target, methodName, parameterTypes, args).filter(FluidStack.class::isInstance)
            .map(FluidStack.class::cast);
    }

    public static boolean invokeVoid(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return false;
        }

        try {
            Method method = findMethod(target.getClass(), methodName, parameterTypes);
            if (method == null) {
                return false;
            }

            method.invoke(target, args);
            return true;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
    }

    public static boolean invokeVoid(Object target, String methodName) {
        return invokeVoid(target, methodName, NO_ARG_TYPES);
    }

    public static Optional<Object> invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return Optional.empty();
        }

        try {
            Method method = findMethod(target.getClass(), methodName, parameterTypes);
            if (method == null) {
                return Optional.empty();
            }

            return Optional.ofNullable(method.invoke(target, args));
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Boolean> invokeBoolean(Object target, String methodName) {
        return invokeBoolean(target, methodName, NO_ARG_TYPES);
    }

    public static Optional<Integer> invokeInt(Object target, String methodName) {
        return invokeInt(target, methodName, NO_ARG_TYPES);
    }

    public static Optional<Long> invokeLong(Object target, String methodName) {
        return invokeLong(target, methodName, NO_ARG_TYPES);
    }

    public static Optional<Long> invokeLong(Object target, String methodName, int slot) {
        return invokeLong(target, methodName, INT_ARG_TYPES, slot);
    }

    public static Optional<ItemStack> invokeItemStack(Object target, String methodName, int slot) {
        return invokeItemStack(target, methodName, INT_ARG_TYPES, slot);
    }

    public static Optional<FluidStack> invokeFluidStack(Object target, String methodName, int slot) {
        return invokeFluidStack(target, methodName, INT_ARG_TYPES, slot);
    }

    private static Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        return METHOD_CACHE
            .computeIfAbsent(new MethodKey(type, methodName, parameterTypes), GTMachineReflectionHelper::resolveMethod)
            .orElse(null);
    }

    private static Field findField(Class<?> type, String fieldName) {
        return FIELD_CACHE.computeIfAbsent(new FieldKey(type, fieldName), GTMachineReflectionHelper::resolveField)
            .orElse(null);
    }

    private static Optional<Method> resolveMethod(MethodKey key) {
        Class<?> current = key.owner();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(key.methodName(), key.parameterTypes());
                method.setAccessible(true);
                return Optional.of(method);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        return Optional.empty();
    }

    private static Optional<Field> resolveField(FieldKey key) {
        Class<?> current = key.owner();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(key.fieldName());
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        return Optional.empty();
    }

    private record FieldKey(Class<?> owner, String fieldName) {}

    private record MethodKey(Class<?> owner, String methodName, Class<?>[] parameterTypes) {

        @Override
        public int hashCode() {
            int result = owner.hashCode();
            result = 31 * result + methodName.hashCode();
            for (Class<?> parameterType : parameterTypes) {
                result = 31 * result + parameterType.hashCode();
            }
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MethodKey other)) {
                return false;
            }
            if (owner != other.owner || !methodName.equals(other.methodName())
                || parameterTypes.length != other.parameterTypes.length) {
                return false;
            }

            for (int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i] != other.parameterTypes[i]) {
                    return false;
                }
            }

            return true;
        }
    }
}
