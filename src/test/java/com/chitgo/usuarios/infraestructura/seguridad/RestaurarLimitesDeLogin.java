package com.chitgo.usuarios.infraestructura.seguridad;

import java.time.Instant;
import java.util.Map;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Aísla el estado en memoria entre tests que reutilizan el contexto Spring, como ya se restaura MySQL.
 * No deshabilita el limiter ni reinicia cupos entre requests de un mismo test.
 */
public class RestaurarLimitesDeLogin extends AbstractTestExecutionListener {
    @Override
    public void beforeTestMethod(TestContext contexto) {
        contexto.getApplicationContext().getBeanProvider(LimitadorDeLoginPorEmail.class).ifAvailable(limite -> {
            synchronized (limite) {
                ((Map<?, ?>) ReflectionTestUtils.getField(limite, "ventanas")).clear();
                ReflectionTestUtils.setField(limite, "ultimaHora", Instant.MIN);
            }
        });
    }
}
