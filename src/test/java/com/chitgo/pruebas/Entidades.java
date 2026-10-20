package com.chitgo.pruebas;

import java.lang.reflect.Field;

/**
 * Las entidades JPA no tienen setters (el id lo pone la base y el estado cambia por métodos de negocio).
 * Para armar una en un test unitario, sin base, se pisan los campos por reflexión.
 */
public final class Entidades {

    private Entidades() {
    }

    public static <T> T conId(T entidad, Long id) {
        return con(entidad, "id", id);
    }

    public static <T> T con(T entidad, String campo, Object valor) {
        try {
            Field f = buscar(entidad.getClass(), campo);
            f.setAccessible(true);
            f.set(entidad, valor);
            return entidad;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No pude poner " + campo + " en " + entidad.getClass().getSimpleName(), e);
        }
    }

    private static Field buscar(Class<?> tipo, String campo) throws NoSuchFieldException {
        for (Class<?> c = tipo; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(campo);
            } catch (NoSuchFieldException ignorada) {
                // sigue con la clase de arriba
            }
        }
        throw new NoSuchFieldException(campo);
    }
}
