package com.chitgo.compartido.infraestructura.seguridad;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.chitgo.usuarios.aplicacion.RenovarSesionService;
import com.chitgo.usuarios.infraestructura.seguridad.AutenticadorPorToken;
import com.chitgo.usuarios.infraestructura.seguridad.ControlDeCredencialProvisoria;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;
import com.chitgo.usuarios.infraestructura.seguridad.FiltroDeSesion;

/**
 * Sesión sin estado: el JWT viaja en la cookie HttpOnly y FiltroDeSesion lo valida en cada request. Casi
 * todas las rutas con sesión lo renuevan, así que a los T minutos sin actividad vence solo (RNF-14).
 *
 * <p>CSRF con el token de Spring para SPA ({@code csrf.spa()}): el back deja la cookie XSRF-TOKEN, legible
 * desde JavaScript, y el front la devuelve en el header X-XSRF-TOKEN en cada POST, PUT, PATCH y DELETE,
 * también en los logins. Sin ese header responde 403.
 *
 * <p>Con la credencial provisoria (primer ingreso o blanqueo) solo se puede elegir la propia, consultar la
 * sesión y cerrarla: el resto da 403 con el código CREDENCIAL_PROVISORIA.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CookieDeSesion cookieDeSesion,
            AutenticadorPorToken autenticador, RenovarSesionService renovacion,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errores) throws Exception {

        ControlDeCredencialProvisoria credencialPropia = new ControlDeCredencialProvisoria();

        // El mismo repositorio que arma spa(), con SameSite=Strict y el Secure de la cookie de sesión.
        CookieCsrfTokenRepository tokensCsrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokensCsrf.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(cookieDeSesion.esSegura()));

        RequestMatcher publicas = new OrRequestMatcher(
                pathPattern("/api/v1/ping"), pathPattern("/api/v1/public/**"),
                pathPattern(HttpMethod.POST, "/api/v1/auth/pin"),
                pathPattern(HttpMethod.POST, "/api/v1/auth/mozo/login"),
                pathPattern(HttpMethod.POST, "/api/v1/auth/admin/login"),
                pathPattern(HttpMethod.GET, "/api/v1/auth/dispositivo"),
                pathPattern("/error"));
        // No renuevan el token: las públicas, que no piden sesión (los logins dejan la suya); /auth/sesion,
        // porque consultarla no cuenta como actividad; /auth/credencial, que deja su cookie nueva, y
        // /auth/logout, que la borra.
        RequestMatcher noRenuevan = new OrRequestMatcher(publicas,
                pathPattern(HttpMethod.GET, "/api/v1/auth/sesion"),
                pathPattern(HttpMethod.POST, "/api/v1/auth/credencial"),
                pathPattern(HttpMethod.POST, "/api/v1/auth/logout"));

        http.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.spa().csrfTokenRepository(tokensCsrf))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .addFilterBefore(new FiltroDeSesion(cookieDeSesion, autenticador, renovacion, noRenuevan),
                    UsernamePasswordAuthenticationFilter.class)
            // Los 401 y 403 salen como ProblemDetail desde ManejadorDeErrores.
            .exceptionHandling(excepciones -> excepciones
                .authenticationEntryPoint((request, response, e) -> errores.resolveException(request, response, null, e))
                .accessDeniedHandler((request, response, e) ->
                        errores.resolveException(request, response, null, credencialPropia.traducir(e))))
            .authorizeHttpRequests(rutas -> rutas
                .requestMatchers(publicas).permitAll()
                // Las únicas que se pueden usar con la credencial provisoria.
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/credencial", "/api/v1/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/sesion").authenticated()
                .anyRequest().access(credencialPropia));
        return http.build();
    }
}
