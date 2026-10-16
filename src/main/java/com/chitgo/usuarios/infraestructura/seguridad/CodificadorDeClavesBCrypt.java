package com.chitgo.usuarios.infraestructura.seguridad;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;

/**
 * BCrypt con costo 10, el mismo de los hashes de los datos de prueba ($2a$10$).
 * BCrypt solo mira los primeros 72 bytes: el largo de la clave se valida antes de llegar acá.
 */
@Component
public class CodificadorDeClavesBCrypt implements CodificadorDeClaves {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String hashear(String clave) {
        return bcrypt.encode(clave);
    }

    @Override
    public boolean verificar(String clave, String hash) {
        if (clave == null || hash == null || hash.isEmpty()) {
            return false;
        }
        return bcrypt.matches(clave, hash);
    }
}
