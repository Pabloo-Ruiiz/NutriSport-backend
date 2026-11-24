-- V2: recordar si la sesión es persistente ("Mantener la sesión iniciada", §3.A y §5.5).
-- El navegador no envía el Max-Age de la cookie, así que al rotar el refresh token el servidor
-- necesita este dato para emitir la cookie nueva con la misma duración.

ALTER TABLE refresh_token
    ADD COLUMN persistente boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN refresh_token.persistente IS
    'true si el usuario marcó "Mantener la sesión iniciada": la cookie lleva Max-Age; false: cookie de sesión.';
