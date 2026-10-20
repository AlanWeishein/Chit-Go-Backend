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
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;
import com.chitgo.usuarios.infraestructura.seguridad.FiltroDeSesion;

/**
 * Sesión sin estado: el JWT viaja en la cookie HttpOnly y FiltroDeSesion lo valida en cada request.
 *
 * <p>CSRF con el token de Spring para SPA ({@code csrf.spa()}): el back deja la cookie XSRF-TOKEN, legible
 * desde JavaScript, y el front la devuelve en el header X-XSRF-TOKEN en cada POST, PUT, PATCH y DELETE,
 * también en los logins. Sin ese header responde 403.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CookieDeSesion cookieDeSesion,
            AutenticadorPorToken autenticador,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errores) throws Exception {

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
                .accessDeniedHandler((request, response, e) -> errores.resolveException(request, response, null, e)))
            .authorizeHttpRequests(rutas -> rutas
                // /ws: el handshake pasa y la autenticación la hace el interceptor del CONNECT de STOMP.
                .requestMatchers("/api/v1/ping", "/api/v1/public/**", "/ws/**").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/pin", "/api/v1/auth/mozo/login", "/api/v1/auth/admin/login").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
