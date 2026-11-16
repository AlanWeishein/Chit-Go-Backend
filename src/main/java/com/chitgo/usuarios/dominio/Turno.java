package com.chitgo.usuarios.dominio;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.chitgo.compartido.dominio.SolicitudInvalidaException;

/**
 * Horario semanal recurrente de un empleado. Si la hora de fin es menor que la de inicio, el turno termina al
 * día siguiente (P-23c, decidido el 13/10): el de 20:00 a 02:00 del lunes sigue hasta las 2 del martes.
 */
@Entity
@Table(name = "turno")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Empleado empleado;

    @Enumerated(EnumType.STRING)
    private DiaSemana diaSemana;

    private LocalTime horaInicio;

    private LocalTime horaFin;

    private static final int SEGUNDOS_DEL_DIA = 24 * 60 * 60;
    private static final int SEGUNDOS_DE_LA_SEMANA = 7 * SEGUNDOS_DEL_DIA;

    /** Como se muestran: por día, por hora de inicio y por hora de fin. */
    public static final Comparator<Turno> EN_ORDEN = Comparator.comparing(Turno::getDiaSemana)
            .thenComparing(Turno::getHoraInicio).thenComparing(Turno::getHoraFin);

    protected Turno() {
    }

    /** @throws SolicitudInvalidaException si falta el día o una hora, o si empieza y termina a la misma hora */
    public Turno(Long localId, Empleado empleado, DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFin) {
        if (diaSemana == null) {
            throw new SolicitudInvalidaException("Falta el día del turno");
        }
        if (horaInicio == null || horaFin == null) {
            throw new SolicitudInvalidaException("Falta la hora de inicio o de fin del turno");
        }
        if (horaInicio.equals(horaFin)) {
            throw new SolicitudInvalidaException("El turno no puede empezar y terminar a la misma hora");
        }
        this.localId = localId;
        this.empleado = empleado;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public Empleado getEmpleado() {
        return empleado;
    }

    public DiaSemana getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public boolean terminaAlDiaSiguiente() {
        return horaFin.isBefore(horaInicio);
    }

    /**
     * Si comparten algún momento de la semana. El que cruza la medianoche sigue en el día siguiente, y el del
     * domingo, en el lunes. Dos que se tocan (uno termina a la hora en que empieza el otro) no se pisan.
     */
    public boolean sePisaCon(Turno otro) {
        return empiezaDurante(otro, this) || empiezaDurante(this, otro);
    }

    /**
     * Una plantilla semanal no puede tener dos turnos que se pisen.
     *
     * @throws SolicitudInvalidaException con los días de los dos turnos
     */
    public static void verificarQueNoSePisan(List<Turno> turnos) {
        List<Turno> enLaSemana = turnos.stream()
                .sorted(Comparator.comparingInt(Turno::inicioEnLaSemana)).toList();
        for (int i = 0; i < enLaSemana.size(); i++) {
            for (int j = i + 1; j < enLaSemana.size(); j++) {
                Turno uno = enLaSemana.get(i);
                Turno otro = enLaSemana.get(j);
                if (uno.sePisaCon(otro)) {
                    throw new SolicitudInvalidaException(uno.diaSemana == otro.diaSemana
                            ? "Los turnos del " + nombre(uno.diaSemana) + " se pisan"
                            : "El turno del " + nombre(uno.diaSemana) + " se pisa con el del " + nombre(otro.diaSemana));
                }
            }
        }
    }

    /** Para la auditoría: "LUN 20:00-02:00". */
    public String describir() {
        return diaSemana + " " + horaInicio + "-" + horaFin;
    }

    /** Si {@code b} empieza mientras dura {@code a}, en la semana vista como una vuelta. */
    private static boolean empiezaDurante(Turno a, Turno b) {
        int desdeElInicioDeA = Math.floorMod(b.inicioEnLaSemana() - a.inicioEnLaSemana(), SEGUNDOS_DE_LA_SEMANA);
        return desdeElInicioDeA < a.duracion();
    }

    /** Segundos desde el lunes a las 00:00. */
    private int inicioEnLaSemana() {
        return diaSemana.ordinal() * SEGUNDOS_DEL_DIA + horaInicio.toSecondOfDay();
    }

    private int duracion() {
        return Math.floorMod(horaFin.toSecondOfDay() - horaInicio.toSecondOfDay(), SEGUNDOS_DEL_DIA);
    }

    private static String nombre(DiaSemana dia) {
        return switch (dia) {
            case LUN -> "lunes";
            case MAR -> "martes";
            case MIE -> "miércoles";
            case JUE -> "jueves";
            case VIE -> "viernes";
            case SAB -> "sábado";
            case DOM -> "domingo";
        };
    }
}
