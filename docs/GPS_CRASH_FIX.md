# Corrección GPS / apertura · Cricket 0.4.2

El 8 de octubre de 2026 el Android registró `SQLiteBlobTooBigException` en
`Store.record`, invocado por `TripScreen` al iniciar GPS y por `TripBanner`
al reabrir la app. SQLite había guardado el registro correctamente, pero el
contenido de una fila superaba el límite de `CursorWindow` de Android.

`Data.kt` carga ahora los textos de `searches` y `kv` en bloques de 256 KiB
mediante `substr(CAST(value AS BLOB), ...)`. Se reconstruyen todos los bytes
antes de decodificar UTF-8 para conservar caracteres y emojis aunque una
secuencia multibyte cruce un bloque. Una transacción asegura una lectura
consistente mientras el servicio actualiza el viaje. History y Export leen
primero identificadores/keys y luego cargan cada contenido por bloques.

Se conserva el esquema y todo el contenido previo, sin borrar ni truncar filas.
Antes de instalar se guardó una copia privada en
`.local-backups/pre-gps-crash-fix.tar` (no versionada).

La APK 0.4.2 (código 13) compila y se instala mediante `adb install -r`.
La copia previa contiene un registro de 4.216.372 bytes, con un viaje en estado
recording: explica el fallo de la fila. Tras instalar y abrir 0.4.2 el proceso
permanece activo y el buffer de crash no contiene errores de ese proceso.
La comprobación interactiva de GPS queda pendiente de desbloquear el teléfono.
