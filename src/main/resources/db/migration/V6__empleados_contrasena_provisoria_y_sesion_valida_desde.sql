-- HU-14 (ST-22): contrasena_provisoria distingue el primer ingreso de un blanqueo de PIN (reunion del 20/10,
-- punto 10). sesion_valida_desde es el "valido desde" del logout: un token emitido antes de esa fecha no vale
-- (la usa la ST-23). NULL quiere decir que vale cualquier token.
-- Los usuarios que ya existen quedan en FALSE y NULL.
ALTER TABLE usuario
    ADD COLUMN contrasena_provisoria BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN sesion_valida_desde   TIMESTAMP NULL;
