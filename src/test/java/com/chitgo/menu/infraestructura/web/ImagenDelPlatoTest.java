package com.chitgo.menu.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.pruebas.ImagenesDePrueba;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * ST-29 de punta a punta: el Admin de La Esquina sube la foto de un plato y se ve en la ruta pública. Contra el
 * MySQL del Compose y la seed de dev, como CartaDelAdminTest: cada test vuelve la imagen de los platos a como
 * estaba y borra la auditoría que dejó. Las fotos van a una carpeta temporal, que queda vacía después de cada test
 * y se borra al final.
 *
 * <p>La seed de dev no tiene un Admin de El Puerto: el aislamiento se prueba al revés, el Admin de La Esquina con
 * un plato de El Puerto, como en CartaDelAdminTest (decidido por Eze el 23/11).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ImagenDelPlatoTest {

    private static final Path CARPETA =
            Path.of(System.getProperty("java.io.tmpdir"), "chitgo-imagenes-" + UUID.randomUUID());
    private static final String PLATOS = "/api/v1/admin/platos";
    private static final String RUTA_PUBLICA = "/api/v1/public/imagenes/";
    private static final String UUID_QUE_NO_EXISTE = "3f2a1b4c-0000-4000-8000-000000000000.jpg";

    @DynamicPropertySource
    static void carpetaTemporal(DynamicPropertyRegistry propiedades) {
        propiedades.add("chitgo.imagenes.carpeta", CARPETA::toString);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private MultipartConfigElement multipart;

    private long ultimoRegistroAntes;
    private List<Map<String, Object>> imagenesAntes;

    @BeforeEach
    void anotarLoQueHabia() {
        ultimoRegistroAntes = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM registro_auditoria", Long.class);
        imagenesAntes = jdbc.queryForList("SELECT id, imagen_url FROM plato");
    }

    @AfterEach
    void dejarLaBaseYLaCarpetaComoEstaban() throws IOException {
        jdbc.update("DELETE FROM registro_auditoria WHERE id > ?", ultimoRegistroAntes);
        for (Map<String, Object> p : imagenesAntes) {
            jdbc.update("UPDATE plato SET imagen_url = ? WHERE id = ?", p.get("imagen_url"), p.get("id"));
        }
        for (Path archivo : archivos()) {
            Files.delete(archivo);
        }
    }

    @AfterAll
    static void borrarLaCarpeta() throws IOException {
        Files.deleteIfExists(CARPETA);
    }

    @Test
    void elAdminSubeUnaFotoConExifYSeVeSinElExifEnLaRutaPublica() throws Exception {
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        byte[] delCelular = ImagenesDePrueba.conExif(ImagenesDePrueba.jpeg(80, 40), 1, "-34.6037 -58.3816");

        String respuesta = subir(sesionDelAdmin(), milanesa, foto("milanesa.jpg", delCelular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(milanesa))
                .andExpect(jsonPath("$.nombre").value("Milanesa napolitana"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String ruta = JsonPath.read(respuesta, "$.imagenUrl");
        assertThat(ruta).matches("/api/v1/public/imagenes/[0-9a-f-]{36}\\.jpg");
        assertThat(jdbc.queryForObject("SELECT imagen_url FROM plato WHERE id = ?", String.class, milanesa))
                .isEqualTo(ruta);
        assertThat(archivos()).hasSize(1);

        byte[] servida = mvc.perform(get(ruta))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImagenesDePrueba.marcadores(servida)).doesNotContain(ImagenesDePrueba.APP1);
        assertThat(new String(servida, StandardCharsets.ISO_8859_1)).doesNotContain("Exif").doesNotContain("-34.6037");
        assertThat(ImagenesDePrueba.abrir(servida).getWidth()).isEqualTo(80);

        // La auditoría, como un EDITAR_PLATO de imagenUrl.
        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT accion, entidad, entidad_id, valor_anterior, valor_nuevo FROM registro_auditoria
                WHERE id > ?""", ultimoRegistroAntes);
        assertThat(registro).containsEntry("accion", "EDITAR_PLATO").containsEntry("entidad", "PLATO");
        assertThat(((Number) registro.get("entidad_id")).longValue()).isEqualTo(milanesa);
        assertThat(String.valueOf(registro.get("valor_anterior"))).contains("imagenUrl");
        assertThat(String.valueOf(registro.get("valor_nuevo"))).contains(ruta);
    }

    @Test
    void unPngSeGuardaComoPng() throws Exception {
        String respuesta = subir(sesionDelAdmin(), plato("Flan casero", "La Esquina"),
                foto("flan.png", ImagenesDePrueba.png(30, 30)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String ruta = JsonPath.read(respuesta, "$.imagenUrl");
        assertThat(ruta).endsWith(".png");
        mvc.perform(get(ruta)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void reemplazarLaFotoBorraLaVieja() throws Exception {
        Cookie admin = sesionDelAdmin();
        Long ensalada = plato("Ensalada mixta", "La Esquina");
        String primera = JsonPath.read(subir(admin, ensalada, foto("a.jpg", ImagenesDePrueba.jpeg(10, 10)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.imagenUrl");

        String segunda = JsonPath.read(subir(admin, ensalada, foto("b.jpg", ImagenesDePrueba.jpeg(20, 20)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.imagenUrl");

        assertThat(segunda).isNotEqualTo(primera);
        mvc.perform(get(primera)).andExpect(status().isNotFound());
        mvc.perform(get(segunda)).andExpect(status().isOk());
        assertThat(archivos()).extracting(p -> RUTA_PUBLICA + p.getFileName()).containsExactly(segunda);
    }

    @Test
    void laFotoDeUnPlatoDeElPuertoDa404ComoSiNoExistiera() throws Exception {
        // RNF-10: la misma respuesta que un plato que no existe.
        Cookie admin = sesionDelAdmin();
        Long pizza = plato("Pizza muzzarella", "El Puerto");
        String antes = jdbc.queryForObject("SELECT imagen_url FROM plato WHERE id = ?", String.class, pizza);

        subir(admin, pizza, foto("pizza.jpg", ImagenesDePrueba.jpeg(10, 10)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El plato no existe"));
        subir(admin, 999999L, foto("pizza.jpg", ImagenesDePrueba.jpeg(10, 10)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El plato no existe"));

        assertThat(jdbc.queryForObject("SELECT imagen_url FROM plato WHERE id = ?", String.class, pizza))
                .isEqualTo(antes);
        assertThat(archivos()).isEmpty();
        assertThat(registrosNuevos()).isZero();
    }

    @Test
    void unTextoRenombradoAJpgDa400YNoGuardaNada() throws Exception {
        byte[] texto = "esto es un texto, no una foto".getBytes(StandardCharsets.UTF_8);

        subir(sesionDelAdmin(), plato("Milanesa napolitana", "La Esquina"), foto("foto.jpg", texto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La imagen tiene que ser JPEG o PNG"));

        assertThat(archivos()).isEmpty();
        assertThat(registrosNuevos()).isZero();
    }

    @Test
    void unArchivoDeMasDe3MbDa400() throws Exception {
        // MockMvc no pasa por el parser de multipart de Tomcat: acá el tope lo controla el servicio. El de Spring
        // (MaxUploadSizeExceededException) se mira con el bean de abajo y se probó con curl contra el back.
        byte[] grande = new byte[3 * 1024 * 1024 + 1];
        System.arraycopy(ImagenesDePrueba.jpeg(10, 10), 0, grande, 0, 3);

        subir(sesionDelAdmin(), plato("Milanesa napolitana", "La Esquina"), foto("grande.jpg", grande))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La imagen pesa más de 3 MB"));
        assertThat(archivos()).isEmpty();
    }

    @Test
    void elTopeDeSpringEsDe3MbPorArchivo() {
        assertThat(multipart.getMaxFileSize()).isEqualTo(3L * 1024 * 1024);
        assertThat(multipart.getMaxRequestSize()).isGreaterThan(multipart.getMaxFileSize());
    }

    @Test
    void sinElCampoImagenDa400() throws Exception {
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Cookie admin = sesionDelAdmin();

        subir(admin, milanesa, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Elegí una imagen"));
        subir(admin, milanesa, new MockMultipartFile("foto", "a.jpg", "image/jpeg", ImagenesDePrueba.jpeg(10, 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Elegí una imagen"));
        subir(admin, milanesa, foto("vacia.jpg", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Elegí una imagen"));
        assertThat(archivos()).isEmpty();
    }

    @Test
    void sinSesionDa401YSinElHeaderDeCsrf403() throws Exception {
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Cookie xsrf = xsrf();

        mvc.perform(multipart(PLATOS + "/" + milanesa + "/imagen").file(foto("a.jpg", ImagenesDePrueba.jpeg(10, 10)))
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(PLATOS + "/" + milanesa + "/imagen").file(foto("a.jpg", ImagenesDePrueba.jpeg(10, 10)))
                        .cookie(sesionDelAdmin(), xsrf()))
                .andExpect(status().isForbidden());
        assertThat(archivos()).isEmpty();
    }

    @Test
    void laRutaPublicaSoloDevuelveNombresConFormaDeUuid() throws Exception {
        Files.createDirectories(CARPETA);
        Path suelto = CARPETA.resolve("menu.jpg");
        Files.write(suelto, ImagenesDePrueba.jpeg(10, 10));

        mvc.perform(get(RUTA_PUBLICA + "menu.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La imagen no existe"));
        mvc.perform(get(RUTA_PUBLICA + "application.yml")).andExpect(status().isNotFound());
        mvc.perform(get(RUTA_PUBLICA + UUID_QUE_NO_EXISTE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La imagen no existe"));

        // Con ".." el firewall de Spring Security corta antes (400); codificado, tampoco llega a un archivo.
        for (String raro : List.of("../../application.yml", "..%2F..%2Fapplication.yml", "%2e%2e%2fapplication.yml")) {
            int estado = mvc.perform(get(RUTA_PUBLICA + raro)).andReturn().getResponse().getStatus();
            assertThat(estado).as(raro).isIn(400, 404);
        }
    }

    // --- Ayudas

    private static MockMultipartFile foto(String nombre, byte[] contenido) {
        return new MockMultipartFile("imagen", nombre, "image/jpeg", contenido);
    }

    /** El POST multipart con la sesión y el CSRF, como lo manda el front con FormData. */
    private ResultActions subir(Cookie sesion, Long platoId, MockMultipartFile archivo) throws Exception {
        Cookie xsrf = xsrf();
        MockMultipartHttpServletRequestBuilder pedido = multipart(PLATOS + "/" + platoId + "/imagen");
        if (archivo != null) {
            pedido.file(archivo);
        }
        pedido.cookie(sesion, xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
        return mvc.perform(pedido);
    }

    /** Entra con el email y la contraseña, como CartaDelAdminTest. */
    private Cookie sesionDelAdmin() throws Exception {
        Cookie xsrf = xsrf();
        Cookie sesion = mvc.perform(post("/api/v1/auth/admin/login").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "admin@laesquina.test",
                                "password", "Chitgo2026!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private Long plato(String nombre, String local) {
        return jdbc.queryForObject("""
                SELECT p.id FROM plato p JOIN local l ON l.id = p.local_id
                WHERE p.nombre = ? AND l.nombre = ?""", Long.class, nombre, local);
    }

    private int registrosNuevos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE id > ?", Integer.class,
                ultimoRegistroAntes);
    }

    private static List<Path> archivos() throws IOException {
        if (!Files.exists(CARPETA)) {
            return List.of();
        }
        try (Stream<Path> lista = Files.list(CARPETA)) {
            return lista.toList();
        }
    }
}
