# Recuperación de servicios — 2026-10-11

El autor autorizó restablecer los servicios de la app. Se iniciaron OTP 8091
y el monitor GTFS-RT 8092 con ventanas ocultas, se reinició Flask 8077 y se
restauró adb reverse tcp:8077. Recursos OTP existentes en Cricket-OTP-Probe;
sin reconstruir el grafo ni cambiar datos/configuraciones del usuario.
Arranque reproducible: `.local-backups/restore-app-services.ps1`.

Validación: backend HTTP 200/status ok, Orion HTTP 200/AVAILABLE, monitor
HTTP 200/ready y feed reciente. La búsqueda técnica a través del backend
respondió HTTP 200. No registró Experience ni activó Passport/HOTCO.
Detalle: `output/service-recovery-2026-10-11.json`.

PT volvió a responder, pero el límite del usuario de 1.500 m excluyó los
itinerarios devueltos (caminata total 1.791,9–1.856,8 m). Consulta diagnóstica
con 1.900 m: siete opciones PT y feed reciente; no modifica la preferencia
guardada. Detalle: `output/service-recovery-pt-walk-probe-2026-10-11.json`.

Auto: GraphHopper respondió HTTP 200 y 115 puntos. Inicio coincide; final
a 265,9 m del destino solicitado, medidos por distancia geográfica entre
extremos. El filtro de 250 m rechaza el recorrido. Esa distancia no es un
tramo caminable calculado ni prueba por sí sola una restricción vial.

Propuesta pendiente, no implementada: conservar destinos solicitados y
extremos devueltos; calcular y verificar accesos a pie con un proveedor
antes de presentar el viaje completo, respetando disponibilidad/límite de
caminata e incluyendo sus tiempos. Mostrar el alcance de cada simulación.
Si falta contexto de ruta, considerar indicadores de la simulación básica
del Passport con procedencia explícita, sin atribuirle evidencia contextual.
No aumentar automáticamente el filtro ni unir extremos con trazados ficticios.
