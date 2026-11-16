package com.chitgo.menu.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import com.chitgo.compartido.dominio.SolicitudInvalidaException;

/** Plato de la carta. Con activo en false deja de ofrecerse sin borrarse. */
@Entity
@Table(name = "plato")
public class Plato {

    public static final int LARGO_MAXIMO_IMAGEN = 255;

    static final String SIN_NOMBRE = "Poné un nombre";
    static final String PRECIO_INVALIDO = "El precio tiene que ser mayor a cero";
    static final String PRECIO_CON_DECIMALES_DE_MAS = "El precio tiene hasta dos decimales";
    static final String IMAGEN_INVALIDA = "La imagen tiene que ser un link https o una ruta del front";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CategoriaMenu categoria;

    private String nombre;

    private String descripcion;

    @Column(precision = 10, scale = 2)
    private BigDecimal precio;

    private String imagenUrl;

    private boolean destacado = false;

    private boolean activo = true;

    @OneToMany(mappedBy = "plato", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PlatoEtiqueta> platoEtiquetas = new HashSet<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    protected Plato() {
    }

    /** Nace disponible y sin destacar. Sin nombre, con precio de cero o menos o con otra imagen, 400. */
    public Plato(Long localId, CategoriaMenu categoria, String nombre, String descripcion, BigDecimal precio, String imagenUrl) {
        this.localId = localId;
        asignar(categoria, nombre, descripcion, precio, imagenUrl);
    }

    /** HU-12: cambia los datos de la carta. No toca activo (cambiarDisponibilidad) ni destacado. Si algo no vale, no cambia nada. */
    public void editar(CategoriaMenu categoria, String nombre, String descripcion, BigDecimal precio, String imagenUrl) {
        asignar(categoria, nombre, descripcion, precio, imagenUrl);
    }

    /** RF-28: los destacados salen arriba en la carta del cliente. */
    public void marcarDestacado(boolean destacado) {
        this.destacado = destacado;
    }

    /**
     * HU-09: no disponible y fuera de carta son lo mismo, la columna activo (09/10). Devuelve si cambió: repetir el
     * estado que ya tiene no se guarda, no se audita ni se avisa (29/10).
     */
    public boolean cambiarDisponibilidad(boolean disponible) {
        if (activo == disponible) {
            return false;
        }
        this.activo = disponible;
        return true;
    }

    /**
     * Deja exactamente esas etiquetas. Las que ya tenía y siguen no se tocan: sacarlas y volver a ponerlas haría que
     * Hibernate inserte la fila nueva antes de borrar la vieja, y chocaría la clave de plato_etiqueta.
     */
    public void reemplazarEtiquetas(Collection<Etiqueta> etiquetas) {
        if (id == null) {
            throw new IllegalStateException("El plato se guarda antes de ponerle etiquetas");
        }
        Set<Long> nuevas = etiquetas.stream().map(Etiqueta::getId).collect(Collectors.toSet());
        platoEtiquetas.removeIf(pe -> !nuevas.contains(pe.getId().getEtiquetaId()));
        Set<Long> puestas = platoEtiquetas.stream().map(pe -> pe.getId().getEtiquetaId()).collect(Collectors.toSet());
        for (Etiqueta etiqueta : etiquetas) {
            if (puestas.add(etiqueta.getId())) {
                new PlatoEtiqueta(localId, this, etiqueta);
            }
        }
    }

    /** Valida todo antes de asignar: si un dato no vale, el plato queda como estaba. */
    private void asignar(CategoriaMenu categoria, String nombre, String descripcion, BigDecimal precio, String imagenUrl) {
        String nombreLimpio = limpiar(nombre);
        if (nombreLimpio == null) {
            throw new SolicitudInvalidaException(SIN_NOMBRE);
        }
        BigDecimal precioValido = precioValido(precio);
        String imagen = imagenValida(imagenUrl);

        this.categoria = Objects.requireNonNull(categoria, "categoria");
        this.nombre = nombreLimpio;
        this.descripcion = limpiar(descripcion);
        this.precio = precioValido;
        this.imagenUrl = imagen;
    }

    /** Siempre con dos decimales, como la columna: 10500 y 10500.00 son el mismo precio. */
    private static BigDecimal precioValido(BigDecimal precio) {
        if (precio == null || precio.signum() <= 0) {
            throw new SolicitudInvalidaException(PRECIO_INVALIDO);
        }
        if (precio.stripTrailingZeros().scale() > 2) {
            throw new SolicitudInvalidaException(PRECIO_CON_DECIMALES_DE_MAS);
        }
        return precio.setScale(2);
    }

    /**
     * Un link https o una ruta del front (/img/milanesa.jpg), decidido el 29/10. Vacía es NULL. No vale http (el
     * navegador lo bloquea en una página https) ni una ruta que empieza con // o /\, que el navegador toma como
     * otro sitio.
     */
    private static String imagenValida(String imagenUrl) {
        String imagen = limpiar(imagenUrl);
        if (imagen == null) {
            return null;
        }
        boolean https = imagen.regionMatches(true, 0, "https://", 0, 8) && imagen.length() > 8;
        boolean rutaDelFront = imagen.startsWith("/") && !imagen.startsWith("//") && !imagen.startsWith("/\\");
        if (!(https || rutaDelFront) || imagen.length() > LARGO_MAXIMO_IMAGEN) {
            throw new SolicitudInvalidaException(IMAGEN_INVALIDA);
        }
        return imagen;
    }

    /** Sin espacios en las puntas; vacío o en blanco es NULL. */
    private static String limpiar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.strip();
    }

    /** Solo arma la relación: lo llama el constructor de PlatoEtiqueta. */
    void agregarEtiqueta(PlatoEtiqueta platoEtiqueta) {
        platoEtiquetas.add(platoEtiqueta);
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public CategoriaMenu getCategoria() {
        return categoria;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public boolean isDestacado() {
        return destacado;
    }

    public boolean isActivo() {
        return activo;
    }

    public Set<PlatoEtiqueta> getPlatoEtiquetas() {
        return Collections.unmodifiableSet(platoEtiquetas);
    }

    public Set<Etiqueta> getEtiquetas() {
        return platoEtiquetas.stream().map(PlatoEtiqueta::getEtiqueta).collect(Collectors.toUnmodifiableSet());
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
