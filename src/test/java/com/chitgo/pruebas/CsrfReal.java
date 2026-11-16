package com.chitgo.pruebas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Arrays;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Usa el contrato de cookies real sin sustituir el repositorio CSRF del contexto compartido. */
public final class CsrfReal {
    private CsrfReal() { }
    public static RequestPostProcessor csrfReal(MockMvc mvc) throws Exception {
        Cookie cookie = mvc.perform(get("/api/v1/ping")).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) throw new AssertionError("El GET inicial no dejó XSRF-TOKEN");
        return request -> {
            Cookie[] anteriores = request.getCookies();
            Cookie[] cookies = anteriores == null ? new Cookie[1] : Arrays.copyOf(anteriores, anteriores.length + 1);
            cookies[cookies.length - 1] = cookie;
            request.setCookies(cookies);
            request.addHeader("X-XSRF-TOKEN", cookie.getValue());
            return request;
        };
    }
}
