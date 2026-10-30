package com.chitgo.usuarios.dominio.puertos;

import java.util.Set;

public interface PermisosPorRol {

    /** Los códigos de permiso del rol en rol_permiso (menu.gestionar), o un conjunto vacío si el rol no existe. */
    Set<String> de(String codigoRol);
}
