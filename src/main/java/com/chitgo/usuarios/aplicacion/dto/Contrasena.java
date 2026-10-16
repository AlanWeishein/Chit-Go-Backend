package com.chitgo.usuarios.aplicacion.dto;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Contraseña de 8 a 64 caracteres y de hasta 72 bytes en UTF-8. BCrypt solo usa los primeros 72 bytes: con
 * acentos o emojis, 64 caracteres pueden pasarse y lo que sobra no contaría.
 *
 * <p>Los caracteres se cuentan como los ve el usuario (un emoji es uno), no como {@code String.length()}.
 * Un NULL es válido: eso lo controla {@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = Contrasena.Validador.class)
@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
public @interface Contrasena {

    int MIN_CARACTERES = 8;
    int MAX_CARACTERES = 64;
    int MAX_BYTES = 72;

    String message() default "La contraseña tiene que tener de 8 a 64 caracteres";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Contrasena, String> {

        @Override
        public boolean isValid(String clave, ConstraintValidatorContext contexto) {
            if (clave == null) {
                return true;
            }
            int caracteres = clave.codePointCount(0, clave.length());
            return caracteres >= MIN_CARACTERES && caracteres <= MAX_CARACTERES
                    && clave.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
        }
    }
}
