# Inicio de viaje y ubicación — 0.4.98

Play estaba limitado a solicitar permisos: si se rechazaban emitía un error;
si los servicios estaban apagados exigía activarlos manualmente. El botón
inferior intentaba obtener fixes sin resolver antes el interruptor de Android.

Ahora el inicio comparte una puerta de acceso Android: permisos de ubicación,
resolución nativa de LocationSettingsRequest y, si el dispositivo no permite
resolución, acceso a ajustes. No confundir permiso de app y servicios globales.
Denegar permiso o cancelar activación abre reproducción sin GPS. Notificaciones
no se usan como requisito de permiso para iniciar ubicación. El servicio GPS
conserva sus propias comprobaciones de acceso y no arranca tras una denegación.
La selección pendiente permanece en ViewModel durante recreación de Activity.

El botón de ubicación funciona tanto antes de buscar como en resultados:
servicios apagados solicita permiso/activación; servicios encendidos abre los
ajustes globales de Android, donde el usuario puede apagarlos. Android no permite
a una app ordinaria conmutar directamente el interruptor global. Al volver,
con servicios y permisos válidos, centra la posición. Un permiso permanentemente
rechazado ofrece ajustes de la aplicación. Estado inicial real y actualización
cada segundo con Activity RESUMED; diagonal roja por falta de permiso o servicios.
El botón sigue disponible durante una búsqueda de posición.

Sin GPS se muestra una reproducción explícitamente etiquetada: avatar sobre
geometría planificada, duración prevista como escala de progreso, controles
de pausa y retorno. No interpola entre tramos separados, no utiliza geometrías
no dibujables ni inventa posiciones observadas. El progreso se detiene con la
pantalla no reanudada y al llegar al final; no equivale a tiempo personal observado.
Sin duración válida permanece al inicio. No crea un Journey ni GPS/Orion capturado.
Experience abre un registro manual pendiente únicamente tras pulsación explícita;
la reproducción no confirma experiencia ni actualiza Passport o aprendizaje.

Validación de permisos/servicios y reproducción mediante pruebas JVM: solicitudes
en el orden correcto, interpolación/clamp, separación de tramos, geometría ausente,
progreso no finito y longitud cero. No se ejecutan viajes reales ni se cambian
permisos globales del teléfono para probar esta entrega.

assembleDebug y testDebugUnitTest correctos: 97 pruebas, cero fallos, errores
o exclusiones. Respaldo previo verificado 20261011-215226: 45 búsquedas y
Passport 0504e5fa-b367-5835-8e36-890f4a08b68a revisión 9. install -r correcto,
versión comprobada 0.4.98/código 109. Los diálogos Android y su interacción
con los permisos actuales requieren revisión por el autor; no se prueban
alterando sus permisos ni aceptando viajes en el dispositivo.
