package com.chitgo.compartido.infraestructura.seguridad;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.chitgo.usuarios.infraestructura.seguridad.AutenticadorPorToken;
import com.chitgo.usuarios.infraestructura.seguridad.ControlDeCredencialProvisoria;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;
import com.chitgo.usuarios.infraestructura.seguridad.FiltroDeSesion;

/**
 * Sesión sin estado: el JWT viaja en la cookie HttpOnly y FiltroDeSesion lo valida en cada request.
 *
 * <p>CSRF con el token de Spring para SPA ({@code csrf.spa()}): el back deja la cookie XSRF-TOKEN, legible
 * desde JavaScript, y el front la devuelve en el header X-XSRF-TOKEN en cada POST, PUT, PATCH y DELETE,
 * también en los logins. Sin ese header responde 403.
 *
 * <p>Con la credencial provisoria (primer ingreso o blanqueo) solo se puede elegir la propia, consultar la
 * sesión y cerrarla: el resto da 403 con el código CREDENCIAL_PROVISORIA.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CookieDeSesion cookieDeSesion,
            AutenticadorPorToken autenticador,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errores) throws Exception {

        ControlDeCredencialProvisoria credencialPropia = new ControlDeCredencialProvisoria();

        // El mismo repositorio que arma spa(), con SameSite=Strict y el Secure de la cookie de sesión.
        CookieCsrfTokenRepository tokensCsrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokensCsrf.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(cookieDeSesion.esSegura()));

        http.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.spa().csrfTokenRepository(tokensCsrf))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .addFilterBefore(new FiltroDeSesion(cookieDeSesion, autenticador),
                    UsernamePasswordAuthenticationFilter.class)
            // Los 401 y 403 salen como ProblemDetail desde ManejadorDeErrores.
            .exceptionHandling(excepciones -> excepciones
                .authenticationEntryPoint((request, response, e) -> errores.resolveException(request, response, null, e))
                .accessDeniedHandler((request, response, e) ->
                        errores.resolveException(request, response, null, credencialPropia.traducir(e))))
            .authorizeHttpRequests(rutas -> rutas
                .requestMatchers("/api/v1/ping", "/api/v1/public/**").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/pin", "/api/v1/auth/mozo/login", "/api/v1/auth/admin/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/dispositivo").permitAll()
                .requestMatchers("/error").permitAll()
                // Las únicas que se pueden usar con la credencial provisoria.
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/credencial", "/api/v1/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/sesion").authenticated()
                .anyRequest().access(credencialPropia));
        return http.build();
    }
}
