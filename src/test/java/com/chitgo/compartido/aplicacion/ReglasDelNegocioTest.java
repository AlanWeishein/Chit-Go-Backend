package com.chitgo.compartido.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Cómo se leen las reglas de chitgo.reglas, con un contexto chico que solo arma ReglasDelNegocio. */
class ReglasDelNegocioTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(SoloLasReglas.class);

    @EnableConfigurationProperties(ReglasDelNegocio.class)
    static class SoloLasReglas {
    }

    @Test
    void sinChitgoReglasSalenLosValoresDeHoy() {
        contexto.run(c -> assertThat(c.getBean(ReglasDelNegocio.class)).isEqualTo(ReglasDePrueba.DE_HOY));
    }

    /** El application.yml de main repite los valores por defecto: si alguien cambia uno solo, esto falla. */
    @Test
    void elApplicationYmlTieneLosMismosValoresQueLosDeHoy() throws IOException {
        PropertySource<?> yml = new YamlPropertySourceLoader()
                .load("application.yml de main", new ClassPathResource("application.yml")).get(0);
        assertThat(yml.getProperty("chitgo.reglas.intentos-para-bloquear")).isNotNull();

        contexto.withInitializer(c -> c.getEnvironment().getPropertySources().addFirst(yml))
                .run(c -> assertThat(c.getBean(ReglasDelNegocio.class)).isEqualTo(ReglasDePrueba.DE_HOY));
    }

    @Test
    void cadaReglaSaleDeSuPropiedad() {
        contexto.withPropertyValues(
                        "chitgo.reglas.demora-del-pedido=2m",
                        "chitgo.reglas.sesion-de-caja=90m",
                        "chitgo.reglas.sesion-sin-local=15m",
                        "chitgo.reglas.vigencia-del-dispositivo=3d",
                        "chitgo.reglas.intentos-para-bloquear=3",
                        "chitgo.reglas.zona-horaria=America/Montevideo")
                .run(c -> assertThat(c.getBean(ReglasDelNegocio.class)).isEqualTo(new ReglasDelNegocio(
                        Duration.ofMinutes(2), Duration.ofMinutes(90), Duration.ofMinutes(15), Duration.ofDays(3), 3,
                        ZoneId.of("America/Montevideo"))));
    }

    @Test
    void lasQueFaltanQuedanConElValorDeHoy() {
        contexto.withPropertyValues("chitgo.reglas.intentos-para-bloquear=3")
                .run(c -> {
                    ReglasDelNegocio reglas = c.getBean(ReglasDelNegocio.class);
                    assertThat(reglas.intentosParaBloquear()).isEqualTo(3);
                    assertThat(reglas.demoraDelPedido()).isEqualTo(Duration.ofSeconds(180));
                    assertThat(reglas.zonaHoraria()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
                });
    }

    /** Sin unidad, cada una se lee en la suya, no en milisegundos. */
    @Test
    void unNumeroSinUnidadSeLeeEnLaUnidadDeCadaRegla() {
        contexto.withPropertyValues(
                        "chitgo.reglas.demora-del-pedido=180",
                        "chitgo.reglas.sesion-de-caja=60",
                        "chitgo.reglas.sesion-sin-local=10",
                        "chitgo.reglas.vigencia-del-dispositivo=7")
                .run(c -> assertThat(c.getBean(ReglasDelNegocio.class)).isEqualTo(ReglasDePrueba.DE_HOY));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "chitgo.reglas.intentos-para-bloquear=0",
            "chitgo.reglas.intentos-para-bloquear=-1",
            "chitgo.reglas.demora-del-pedido=0s",
            "chitgo.reglas.demora-del-pedido=-5s",
            "chitgo.reglas.demora-del-pedido=180500ms",
            "chitgo.reglas.sesion-de-caja=0m",
            "chitgo.reglas.sesion-sin-local=-10m",
            "chitgo.reglas.vigencia-del-dispositivo=0d",
            "chitgo.reglas.zona-horaria=Marte/Olimpo"})
    void unValorSinSentidoNoDejaArrancar(String propiedad) {
        contexto.withPropertyValues(propiedad).run(c -> assertThat(c).hasFailed());
    }
}
