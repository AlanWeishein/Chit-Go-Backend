package com.chitgo.usuarios.infraestructura.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.usuarios.aplicacion.AltaDeLocalService;
import com.chitgo.usuarios.aplicacion.AltaDeLocalService.ResultadoDelDesbloqueo;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeLocal;
import com.chitgo.usuarios.aplicacion.dto.LocalCreado;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/** Prototipo experimental de dos operaciones de plataforma. No expone consultas globales. */
@RestController
@RequestMapping("/api/v1/superadmin")
public class SuperadminController {
    private final AltaDeLocalService locales;

    public SuperadminController(AltaDeLocalService locales) { this.locales = locales; }

    @PostMapping("/locales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('local.crear')")
    public LocalCreado crear(@AuthenticationPrincipal UsuarioAutenticado operador,
            @Valid @RequestBody DatosDeAltaDeLocal datos) {
        return locales.darDeAltaComoSuperadmin(operador, datos);
    }

    @PostMapping("/gerentes/desbloquear")
    @PreAuthorize("hasAuthority('gerente.desbloquear')")
    public Desbloqueo desbloquear(@AuthenticationPrincipal UsuarioAutenticado operador,
            @Valid @RequestBody EmailDeGerente datos) {
        ResultadoDelDesbloqueo resultado = locales.desbloquearGerenteComoSuperadmin(operador, datos.email());
        if (resultado == ResultadoDelDesbloqueo.NO_ES_GERENTE || resultado == ResultadoDelDesbloqueo.SIN_LOCAL) {
            throw new NoEncontradoException("El Gerente/Dueño no existe");
        }
        return new Desbloqueo(resultado);
    }

    public record EmailDeGerente(@NotBlank @Email @Size(max = 160) String email) {
        public EmailDeGerente {
            if (email != null && email.length() <= 160) email = email.strip();
        }
    }

    public record Desbloqueo(ResultadoDelDesbloqueo resultado) { }
}
