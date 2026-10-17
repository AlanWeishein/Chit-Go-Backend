package com.chitgo.usuarios.infraestructura.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.usuarios.aplicacion.ConsultarDispositivoService;
import com.chitgo.usuarios.aplicacion.ConsultarSesionService;
import com.chitgo.usuarios.aplicacion.ElegirCredencialService;
import com.chitgo.usuarios.aplicacion.HabilitarDispositivoService;
import com.chitgo.usuarios.aplicacion.IngresarConEmailService;
import com.chitgo.usuarios.aplicacion.IngresarConPinService;
import com.chitgo.usuarios.aplicacion.ResultadoDeHabilitacion;
import com.chitgo.usuarios.aplicacion.ResultadoDeIngreso;
import com.chitgo.usuarios.aplicacion.dto.CambioDeCredencialDto;
import com.chitgo.usuarios.aplicacion.dto.DispositivoDto;
import com.chitgo.usuarios.aplicacion.dto.IngresoConEmailDto;
import com.chitgo.usuarios.aplicacion.dto.IngresoConPinDto;
import com.chitgo.usuarios.aplicacion.dto.IngresoDeMozoDto;
import com.chitgo.usuarios.aplicacion.dto.SesionDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacionController {

    private final ConsultarSesionService consultarSesion;
    private final IngresarConPinService ingresarConPin;
    private final IngresarConEmailService ingresarConEmail;
    private final HabilitarDispositivoService habilitarDispositivo;
    private final ConsultarDispositivoService consultarDispositivo;
    private final ElegirCredencialService elegirCredencial;
    private final CookieDeSesion cookieDeSesion;
    private final CookieDeDispositivo cookieDeDispositivo;

    public AutenticacionController(ConsultarSesionService consultarSesion, IngresarConPinService ingresarConPin,
            IngresarConEmailService ingresarConEmail, HabilitarDispositivoService habilitarDispositivo,
            ConsultarDispositivoService consultarDispositivo, ElegirCredencialService elegirCredencial,
            CookieDeSesion cookieDeSesion, CookieDeDispositivo cookieDeDispositivo) {
        this.consultarSesion = consultarSesion;
        this.ingresarConPin = ingresarConPin;
        this.ingresarConEmail = ingresarConEmail;
        this.habilitarDispositivo = habilitarDispositivo;
        this.consultarDispositivo = consultarDispositivo;
        this.elegirCredencial = elegirCredencial;
        this.cookieDeSesion = cookieDeSesion;
        this.cookieDeDispositivo = cookieDeDispositivo;
    }

    /** Deja la cookie con el JWT y devuelve lo mismo que /auth/sesion. El mozo, además, necesita la de dispositivo. */
    @PostMapping("/pin")
    public ResponseEntity<SesionDto> pin(@Valid @RequestBody IngresoConPinDto pedido,
            @CookieValue(name = CookieDeDispositivo.NOMBRE, required = false) String habilitacion) {
        ResultadoDeIngreso resultado = ingresarConPin.ingresar(pedido.tokenStaff(), pedido.usuarioId(), pedido.pin(),
                habilitacion);
        return conCookie(resultado);
    }

    /**
     * Usuario y contraseña del mozo (P-32). No abre la sesión: deja la cookie de dispositivo y devuelve el mozo,
     * para que el front vaya directo a su PIN.
     */
    @PostMapping("/mozo/login")
    public ResponseEntity<DispositivoDto> mozoLogin(@Valid @RequestBody IngresoDeMozoDto pedido) {
        ResultadoDeHabilitacion resultado = habilitarDispositivo.habilitar(pedido.tokenStaff(),
                pedido.nombreUsuario(), pedido.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieDeDispositivo.crear(resultado.token()).toString())
                .body(resultado.dispositivo());
    }

    /**
     * Público: el front lo llama al abrir la app del mozo, porque no puede leer la cookie de dispositivo. 200 con
     * el mozo si hay una vigente; 204 si no, y entonces muestra P-32.
     */
    @GetMapping("/dispositivo")
    public ResponseEntity<DispositivoDto> dispositivo(
            @CookieValue(name = CookieDeDispositivo.NOMBRE, required = false) String habilitacion) {
        return consultarDispositivo.consultar(habilitacion)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Admin y Gerente/Dueño (P-12). Como el de PIN: deja la cookie y devuelve lo mismo que /auth/sesion. */
    @PostMapping("/admin/login")
    public ResponseEntity<SesionDto> adminLogin(@Valid @RequestBody IngresoConEmailDto pedido) {
        return conCookie(ingresarConEmail.ingresar(pedido.email(), pedido.password()));
    }

    /**
     * Primer ingreso o después de un blanqueo (P-29, P-30, P-31 y P-32b). Pide la sesión que abrió el login con
     * la credencial provisoria. Deja una cookie nueva, ya sin la marca, y devuelve lo mismo que /auth/sesion.
     */
    @PostMapping("/credencial")
    public ResponseEntity<SesionDto> credencial(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody CambioDeCredencialDto pedido) {
        return conCookie(elegirCredencial.elegir(usuario.usuarioId(), pedido.pin(), pedido.pinRepetido(),
                pedido.password(), pedido.passwordRepetida()));
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

    private ResponseEntity<SesionDto> conCookie(ResultadoDeIngreso resultado) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieDeSesion.crear(resultado.token(), resultado.duracion()).toString())
                .body(resultado.sesion());
    }
}
