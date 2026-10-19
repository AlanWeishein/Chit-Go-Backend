package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.chitgo.usuarios.dominio.Permiso;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.puertos.PermisosPorRol;

/** Lee los permisos de cada rol una sola vez, al arrancar: son datos fijos de V2 y no cambian sin reiniciar. */
@Repository
class PermisosPorRolAdapter implements PermisosPorRol {

    private final Map<String, Set<String>> permisosPorRol = new HashMap<>();

    PermisosPorRolAdapter(RolJpaRepository jpa) {
        for (Rol rol : jpa.buscarTodosConPermisos()) {
            permisosPorRol.put(rol.getCodigo(),
                    rol.getPermisos().stream().map(Permiso::getCodigo).collect(Collectors.toUnmodifiableSet()));
        }
    }

    @Override
    public Set<String> de(String codigoRol) {
        return permisosPorRol.getOrDefault(codigoRol, Set.of());
    }
}
