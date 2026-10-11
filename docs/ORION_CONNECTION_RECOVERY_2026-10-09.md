# Recuperación de condiciones Orion — 2026-10-09

Home devolvía HTTP 503 y `status=UNAVAILABLE`, aunque la consulta directa al
endpoint público de Orion recuperaba dos entidades Weather y 18 observaciones.

Había dos instancias del backend `main.py`. La instancia anterior, PID 22732,
seguía atendiendo consultas y escribiendo en `backend-0449.stderr.log` mientras
los procesos reiniciados anunciaban el mismo puerto sin recibir esas consultas.
Su conexión externa producía `ConnectionRefusedError` bajo las restricciones
de red del entorno en que se inició.

Se detuvo la instancia duplicada y quedó una sola instancia con acceso externo,
PID 14776, puerto 8077. El proceso de routing independiente no se detuvo.
La consulta meteorológica real volvió a devolver HTTP 200, `AVAILABLE` y 18
observaciones. El registro del backend también confirmó una consulta desde la
app con HTTP 200. No se alteraron las mediciones ni el Passport del usuario.

Se añadió registro de excepciones al endpoint meteorológico para que futuros
fallos de transporte no queden ocultos detrás de la respuesta UNAVAILABLE.
No se cambiaron filtros geográficos, normalización ni proveedor meteorológico.

Logs activos: `.local-backups/backend-orion-diagnostic.stdout.log` y
`.local-backups/backend-orion-diagnostic.stderr.log`.
