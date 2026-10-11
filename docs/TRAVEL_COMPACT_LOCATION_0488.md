# Barra compacta y ubicación visual — 0.4.88 / 99

El autor pide retirar el mensaje de permiso rechazado y sustituirlo por una
diagonal roja en el botón de ubicación inferior izquierdo. Se interpreta como
ubicación sin acceso: falta permiso Android o servicios de ubicación apagados.
GPS encendido por sí solo no concede permiso a la app. La pregunta de aclaración
se presentó durante el trabajo; se aplica esa interpretación explicitada.

La planificación deja de mostrar el bloque flotante de mensajes de ubicación;
el botón conserva progreso durante una solicitud. Tras denegación muestra
icono con diagonal roja, sin mensaje, tanto en planificación como en el mapa de
resultados. El estado se comprueba al reanudar y cada segundo mientras la pantalla
está RESUMED, leyendo permisos/estado del sistema; no adquiere GPS ni almacena
posición para esa comprobación. Al salir/pausar cesan esas comprobaciones.
El botón sigue permitiendo pedir acceso/activar ubicación con el flujo existente.
Descripción accesible EN/DE distingue ubicación disponible y sin acceso.

Solicitud adicional: Find routes ocupaba una fila entera. La tarjeta superior
ahora contiene solo dos filas A/B, intercambio/opciones y un botón lateral de
lupa48dp para calcular rutas. Sin fila extra ni etiqueta larga visible.
La acción accesible conserva Find routes/Wege suchen; está deshabilitada hasta
tener dos lugares válidos. No cambian búsqueda manual, sugerencias, selección
directa en mapa ni el disparador exclusivo de búsqueda/congelación prospectiva.

Validación:84 pruebas JVM y build Debug aprobados. Una prueba adicional distingue
permiso y servicios activados, incluyendo GPS encendido sin permiso. Copia previa
verificada e instalación install-r. Composición/interacción final y cambios de
permiso/GPS en teléfono pendientes; no se registraron GPS/Experience/elecciones
para probar, ni se modificaron Passport o escalas.
