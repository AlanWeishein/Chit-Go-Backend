package com.chitgo.usuarios.aplicacion;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.usuarios.aplicacion.dto.StaffDto;
import com.chitgo.usuarios.aplicacion.dto.StaffDto.UsuarioDeStaffDto;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/** La lista del login con PIN que abre el QR de staff (P-04, P-22). */
@Service
public class ConsultarStaffService {

    /** El mismo para cualquier token que no sirve: no dice nada de ningún local. */
    static final String QR_INVALIDO = "El QR de staff no es válido.";

    private final LocalRepository locales;
    private final UsuarioRepository usuarios;

    public ConsultarStaffService(LocalRepository locales, UsuarioRepository usuarios) {
        this.locales = locales;
        this.usuarios = usuarios;
    }

    /** Solo los activos que entran con PIN: sin el Admin, el Gerente/Dueño ni los dados de baja. */
    @Transactional(readOnly = true)
    public StaffDto consultar(String tokenStaff) {
        Local local = locales.buscarPorTokenStaff(tokenStaff)
                .orElseThrow(() -> new NoEncontradoException(QR_INVALIDO));
        List<UsuarioDeStaffDto> lista = usuarios.listarActivosDelLocal(local.getId(), Rol.CODIGOS_CON_PIN).stream()
                .map(u -> new UsuarioDeStaffDto(u.getId(), u.getNombre()))
                .toList();
        return new StaffDto(local.getNombre(), lista);
    }
}
