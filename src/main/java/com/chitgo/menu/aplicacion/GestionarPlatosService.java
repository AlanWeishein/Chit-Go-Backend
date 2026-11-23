package com.chitgo.menu.aplicacion;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.menu.aplicacion.dto.DatosDePlato;
import com.chitgo.menu.aplicacion.dto.PlatoAdminDto;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.menu.dominio.Etiqueta;
import com.chitgo.menu.dominio.FormatoDeImagen;
import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.CategoriaMenuRepository;
import com.chitgo.menu.dominio.puertos.EtiquetaRepository;
import com.chitgo.menu.dominio.puertos.ImagenesDePlatos;
import com.chitgo.menu.dominio.puertos.PlatoRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-12: el Admin da de alta, edita y destaca platos (RF-24 y RF-28). Sacar un plato de la carta no está acá: es
 * el cambio de disponibilidad de la ST-12 (decidido el 29/10).
 *
 * <p>Un plato, una categoría o una etiqueta de otro local da 404, el mismo que si no existiera (RNF-10).
 */
@Service
public class GestionarPlatosService {

    static final String ENTIDAD = "PLATO";

    /** ST-29: hasta 3 MB por foto (17/11). Es el mismo número que spring.servlet.multipart.max-file-size. */
    static final int PESO_MAXIMO = 3 * 1024 * 1024;
    static final String SIN_IMAGEN = "Elegí una imagen";
    static final String PESA_DE_MAS = "La imagen pesa más de 3 MB";
    static final String FORMATO_DE_IMAGEN_INVALIDO = "La imagen tiene que ser JPEG o PNG";

    private final PlatoRepository platos;
    private final CategoriaMenuRepository categorias;
    private final EtiquetaRepository etiquetas;
    private final ImagenesDePlatos imagenes;
    private final Auditor auditor;

    public GestionarPlatosService(PlatoRepository platos, CategoriaMenuRepository categorias,
            EtiquetaRepository etiquetas, ImagenesDePlatos imagenes, Auditor auditor) {
        this.platos = platos;
        this.categorias = categorias;
        this.etiquetas = etiquetas;
        this.imagenes = imagenes;
        this.auditor = auditor;
    }

    /** P-14: todos los del local, también los fuera de carta. */
    @Transactional(readOnly = true)
    public List<PlatoAdminDto> listar(UsuarioAutenticado usuario) {
        return platos.listarDelLocal(localDe(usuario)).stream().map(GestionarPlatosService::aDto).toList();
    }

    /** Nace disponible y sin destacar. */
    @Transactional
    public PlatoAdminDto crear(UsuarioAutenticado usuario, DatosDePlato datos) {
        Long localId = localDe(usuario);
        CategoriaMenu categoria = buscarCategoria(datos.categoriaId(), localId);
        List<Etiqueta> etiquetasDelPlato = buscarEtiquetas(datos.etiquetaIds(), localId);

        Plato plato = platos.guardar(new Plato(localId, categoria, datos.nombre(), datos.descripcion(), datos.precio(),
                datos.imagenUrl()));
        plato.reemplazarEtiquetas(etiquetasDelPlato); // necesita el id del plato: van después del primer guardar

        auditor.registrar(localId, usuario.usuarioId(), "CREAR_PLATO", ENTIDAD, plato.getId(), null, valores(plato));
        return aDto(plato);
    }

    /**
     * Cambia los datos y reemplaza las etiquetas enteras. No toca disponible ni destacado. La auditoría lleva solo
     * lo que cambió (un cambio de precio, el anterior y el nuevo: CP-25-03); si no cambió nada, no audita.
     */
    @Transactional
    public PlatoAdminDto editar(UsuarioAutenticado usuario, Long platoId, DatosDePlato datos) {
        Long localId = localDe(usuario);
        Plato plato = buscarPlato(platoId, localId);
        CategoriaMenu categoria = buscarCategoria(datos.categoriaId(), localId);
        List<Etiqueta> etiquetasDelPlato = buscarEtiquetas(datos.etiquetaIds(), localId);

        Map<String, Object> antes = valores(plato);
        plato.editar(categoria, datos.nombre(), datos.descripcion(), datos.precio(), datos.imagenUrl());
        plato.reemplazarEtiquetas(etiquetasDelPlato);
        Map<String, Object> despues = valores(plato);

        antes.keySet().removeIf(clave -> iguales(antes.get(clave), despues.get(clave)));
        if (!antes.isEmpty()) {
            despues.keySet().retainAll(antes.keySet());
            platos.guardar(plato);
            auditor.registrar(localId, usuario.usuarioId(), "EDITAR_PLATO", ENTIDAD, plato.getId(), antes, despues);
        }
        return aDto(plato);
    }

    /** RF-28. Si ya estaba así, no cambia nada y no audita. */
    @Transactional
    public void destacar(UsuarioAutenticado usuario, Long platoId, boolean destacado) {
        Long localId = localDe(usuario);
        Plato plato = buscarPlato(platoId, localId);
        boolean anterior = plato.isDestacado();
        if (anterior == destacado) {
            return;
        }
        plato.marcarDestacado(destacado);
        platos.guardar(plato);
        auditor.registrar(localId, usuario.usuarioId(), "DESTACAR_PLATO", ENTIDAD, plato.getId(),
                Map.of("destacado", anterior), Map.of("destacado", destacado));
    }

    /**
     * ST-29: la foto del plato, subida al server. Valida en el orden de la nota: que venga, el tamaño y el formato por
     * los primeros bytes; el ancho, el alto y que se pueda abrir los mira ImagenesDePlatos. Queda en la auditoría
     * como un EDITAR_PLATO de imagenUrl, igual que en editar. La foto anterior se borra después del commit si era
     * una de las guardadas acá; un link https o una ruta del front no se tocan.
     */
    @Transactional
    public PlatoAdminDto cambiarImagen(UsuarioAutenticado usuario, Long platoId, byte[] contenido) {
        Long localId = localDe(usuario);
        Plato plato = buscarPlato(platoId, localId);
        if (contenido == null || contenido.length == 0) {
            throw new SolicitudInvalidaException(SIN_IMAGEN);
        }
        if (contenido.length > PESO_MAXIMO) {
            throw new SolicitudInvalidaException(PESA_DE_MAS);
        }
        FormatoDeImagen formato = FormatoDeImagen.detectar(contenido)
                .orElseThrow(() -> new SolicitudInvalidaException(FORMATO_DE_IMAGEN_INVALIDO));

        String anterior = plato.getImagenUrl();
        String nombre = imagenes.guardar(contenido, formato);
        plato.cambiarImagen(ImagenesDePlatos.RUTA_PUBLICA + nombre);
        platos.guardar(plato);
        auditor.registrar(localId, usuario.usuarioId(), "EDITAR_PLATO", ENTIDAD, plato.getId(),
                soloLaImagen(anterior), soloLaImagen(plato.getImagenUrl()));
        if (anterior != null && anterior.startsWith(ImagenesDePlatos.RUTA_PUBLICA)) {
            imagenes.borrar(anterior.substring(ImagenesDePlatos.RUTA_PUBLICA.length()));
        }
        return aDto(plato);
    }

    private Plato buscarPlato(Long platoId, Long localId) {
        return platos.buscarPorId(platoId, localId).orElseThrow(() -> new NoEncontradoException("El plato no existe"));
    }

    private CategoriaMenu buscarCategoria(Long categoriaId, Long localId) {
        return categorias.buscarPorId(categoriaId, localId)
                .orElseThrow(() -> new NoEncontradoException("La categoría no existe"));
    }

    /** Las repetidas cuentan una vez. Si falta alguna (no existe o es de otro local), 404. */
    private List<Etiqueta> buscarEtiquetas(List<Long> etiquetaIds, Long localId) {
        Set<Long> ids = new LinkedHashSet<>(etiquetaIds);
        List<Etiqueta> encontradas = etiquetas.buscarPorIds(localId, ids);
        if (encontradas.size() != ids.size()) {
            throw new NoEncontradoException("La etiqueta no existe");
        }
        return encontradas;
    }

    /** Lo que queda en la auditoría. LinkedHashMap y no Map.of porque la descripción y la imagen pueden ser NULL. */
    private static Map<String, Object> valores(Plato plato) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("nombre", plato.getNombre());
        valores.put("descripcion", plato.getDescripcion());
        valores.put("precio", plato.getPrecio());
        valores.put("categoriaId", plato.getCategoria().getId());
        valores.put("imagenUrl", plato.getImagenUrl());
        valores.put("etiquetaIds",
                plato.getPlatoEtiquetas().stream().map(pe -> pe.getId().getEtiquetaId()).sorted().toList());
        valores.put("destacado", plato.isDestacado());
        valores.put("disponible", plato.isActivo());
        return valores;
    }

    /** Como el EDITAR_PLATO de editar cuando cambia solo la imagen. La anterior puede ser NULL. */
    private static Map<String, Object> soloLaImagen(String imagenUrl) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("imagenUrl", imagenUrl);
        return valores;
    }

    /** 9500 y 9500.00 son el mismo precio. */
    private static boolean iguales(Object antes, Object despues) {
        if (antes instanceof BigDecimal a && despues instanceof BigDecimal b) {
            return a.compareTo(b) == 0;
        }
        return Objects.equals(antes, despues);
    }

    private static PlatoAdminDto aDto(Plato plato) {
        CategoriaMenu categoria = plato.getCategoria();
        List<PlatoAdminDto.EtiquetaDto> etiquetasDto = plato.getEtiquetas().stream()
                .map(e -> new PlatoAdminDto.EtiquetaDto(e.getId(), e.getNombre()))
                .sorted(Comparator.comparing(PlatoAdminDto.EtiquetaDto::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new PlatoAdminDto(plato.getId(), plato.getNombre(), plato.getDescripcion(), plato.getPrecio(),
                new PlatoAdminDto.CategoriaDto(categoria.getId(), categoria.getNombre()), plato.getImagenUrl(),
                plato.isDestacado(), plato.isActivo(), etiquetasDto);
    }

    /** El Gerente/Dueño no tiene local fijo y no gestiona la carta. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
