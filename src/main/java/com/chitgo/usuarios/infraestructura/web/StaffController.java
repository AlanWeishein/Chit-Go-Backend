package com.chitgo.usuarios.infraestructura.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.usuarios.aplicacion.ConsultarStaffService;
import com.chitgo.usuarios.aplicacion.dto.StaffDto;

/** Pública: la abre el QR de staff, antes del login. */
@RestController
@RequestMapping("/api/v1/public/staff")
public class StaffController {

    private final ConsultarStaffService consultarStaff;

    public StaffController(ConsultarStaffService consultarStaff) {
        this.consultarStaff = consultarStaff;
    }

    @GetMapping("/{tokenStaff}")
    public StaffDto staff(@PathVariable String tokenStaff) {
        return consultarStaff.consultar(tokenStaff);
    }
}
