# Pausa y SQLite — 0.4.43

El error se reprodujo en Samsung con dos Store independientes sobre una base aislada: una transacción de lectura mantenida 3,5 segundos y una pausa concurrente producían `SQLiteDatabaseLockedException: database is locked (code 5 SQLITE_BUSY)` en `TripRepository.changeActive`.

Cada Store heredaba de SQLiteOpenHelper y abría un pool independiente para el mismo archivo. WAL permitía lecturas paralelas, pero los pools competían por el único bloqueo de escritura; las lecturas por bloques utilizan una transacción para mantener consistente el registro completo.

Store ahora obtiene un SQLiteOpenHelper compartido por la ruta canónica de la base, usando el contexto de aplicación. Android coordina las transacciones en un único pool: la pausa espera a que quede libre la conexión de escritura. Las preferencias pequeñas siguen siendo legibles mediante WAL mientras espera.

El cierre usa referencias e idempotencia: cerrar el Store del servicio no cierra la base si la interfaz aún la utiliza. El último cierre libera el helper; una apertura posterior conserva los datos. No se modifica el esquema, se borra historial ni se reintenta una transacción parcialmente ejecutada.

Validación en dispositivo:

- La prueba de contención falló con 0.4.42 y pasa con 0.4.43, manteniendo exactamente la misma lectura de 3,5 segundos.
- Se verifica pausa, reanudación y nueva pausa, con estado visible desde la otra instancia y validación del contrato de viaje.
- Se verifica compartir el mismo pool, cierre del servicio, doble cierre y persistencia al reabrir.
- Pasan los dos tests de lectura de registros grandes/experiencias pendientes y transporte gzip en Android.
- Pasan los 16 tests JVM y la compilación de APK principal e instrumentación.

Las pruebas usan bases con nombres aleatorios y las eliminan al terminar. No activan GPS ni modifican el viaje real del usuario. Versión instalada: 0.4.43, código 54.
