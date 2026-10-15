package com.chitgo.usuarios.infraestructura.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.usuarios.aplicacion.ConsultarSesionService;
import com.chitgo.usuarios.aplicacion.dto.SesionDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacionController {

    private final ConsultarSesionService consultarSesion;
    private final CookieDeSesion cookieDeSesion;

    public AutenticacionController(ConsultarSesionService consultarSesion, CookieDeSesion cookieDeSesion) {
        this.consultarSesion = consultarSesion;
        this.cookieDeSesion = cookieDeSesion;
    }

    /** No deja una cookie nueva: consultar la sesión no la renueva. */
    @GetMapping("/sesion")
    public SesionDto sesion(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return consultarSesion.consultar(usuario);
    }

    /** Un JWT no se puede revocar: cerrar la sesión es borrar la cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieDeSesion.borrar().toString())
                .build();
    }
}
