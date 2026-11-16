package com.chitgo.pedidos.infraestructura.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.chitgo.compartido.aplicacion.PaginaDto;
import com.chitgo.pedidos.aplicacion.GestionarMesasService;
import com.chitgo.pedidos.aplicacion.dto.MesaAdminDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@RestController
@RequestMapping("/api/v1/admin/mesas")
@PreAuthorize("hasAuthority('mesa.gestionar')")
public class MesasAdminController {
    private final GestionarMesasService mesas;

    public MesasAdminController(GestionarMesasService mesas) { this.mesas = mesas; }

    @GetMapping
    public PaginaDto<MesaAdminDto> listar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return mesas.listar(usuario, page, size);
    }

    @GetMapping("/{id}")
    public MesaAdminDto detalle(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return mesas.detalle(usuario, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MesaAdminDto crear(@AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody Numero cuerpo) {
        return mesas.crear(usuario, cuerpo.numero());
    }

    @PutMapping("/{id}")
    public MesaAdminDto editar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            @Valid @RequestBody Numero cuerpo) { return mesas.editar(usuario, id, cuerpo.numero()); }

    @PatchMapping("/{id}/inhabilitada")
    public MesaAdminDto disponibilidad(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            @Valid @RequestBody Disponibilidad cuerpo) {
        return mesas.disponibilidad(usuario, id, cuerpo.inhabilitada(), cuerpo.motivo());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void baja(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        mesas.darDeBaja(usuario, id);
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("Content-Disposition", "inline; filename=mesa-" + id + ".png")
                .contentType(MediaType.IMAGE_PNG).body(QrDeMesa.png(mesas.detalle(usuario, id).tokenQr()));
    }

    public record Numero(@NotBlank @Size(max = 20) String numero) { }
    public record Disponibilidad(@NotNull Boolean inhabilitada, @Size(max = 255) String motivo) { }
}
