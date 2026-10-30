package com.chitgo.usuarios.dominio.puertos;

/** Hashea y verifica PINes y contraseñas. Nunca se guarda ni se loguea la clave en texto plano. */
public interface CodificadorDeClaves {

    String hashear(String clave);

    /** Falso también si no hay hash, por ejemplo un Admin que no tiene PIN. */
    boolean verificar(String clave, String hash);
}
