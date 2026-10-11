# Planificación Travel — Android 0.4.83 / 94

Travel abre la planificación, salvo durante un viaje activo. Debajo de Find routes
se muestra NativeJourneyMap con sus estilos, capas, aparcamientos, paradas y radio
configurable existentes. La planificación excluye las geometrías, recomendaciones
y simulaciones de búsquedas anteriores. No solicita itinerarios al tocar un POI:
en esta pantalla ese toque propone un punto de origen o destino.

El usuario puede escribir una dirección o seleccionar A/B y tocar el mapa.
El punto se confirma con Use as origin/destination y actualiza el campo superior.
Confirmar A pasa el selector a B. Cambiar el campo enfocado cambia el objetivo
del selector. Cancelar o cambiar A/B descarta la propuesta sin modificar el lugar
confirmado. Find routes queda deshabilitado mientras existe un punto pendiente.

La geocodificación inversa aporta una etiqueta de dirección cercana, nunca mueve
las coordenadas elegidas ni acredita un número de edificio. Si falla o tarda,
las coordenadas permiten confirmar el punto. La consulta se cancela al cambiar
la selección; Android anterior a API33 utiliza la API bloqueante fuera del hilo UI.

Al entrar por primera vez en Travel durante la sesión se solicita permiso Android
de ubicación precisa/aproximada. Si la ubicación está apagada, SettingsClient
ofrece el diálogo Android; hay alternativa a los ajustes del sistema cuando
no está disponible la resolución. Rechazar permite continuar manualmente.
Las entradas siguientes no reiteran automáticamente el permiso rechazado;
el botón de ubicación permite solicitarlo explícitamente. Las solicitudes
simultáneas se bloquean y el botón muestra el progreso.

Se obtiene una posición reciente (máximo 30 segundos al recibirla), con tiempo
límite de 25 segundos. El avatar se sitúa en esa posición y A tiene su propio pin.
Solo se completa A automáticamente si no hay origen confirmado ni texto escrito.
La posición se vuelve a obtener al reanudar la pantalla; no se inicia seguimiento
del viaje ni se guarda una trayectoria. La adquisición se cancela al abandonar
o pausar la pantalla. Sin permiso/fix no se inventa la posición del avatar.

Escribir, tocar y confirmar puntos no llama a search ni modifica el Passport.
Find routes mantiene el flujo existente de búsqueda y congelación prospectiva
de contexto/Passport. No se cambian escalas, aprendizaje ni activación HOTCO.
Los nuevos registros toman app_version de BuildConfig.VERSION_NAME, evitando
guardar una versión antigua fijada en código; los históricos no se reescriben.

Validación: compilación Debug y 82 pruebas JVM aprobadas, incluidas tres pruebas
de planificación: conservar coordenadas exactas y no certificar un número de
edificio, funcionar sin dirección inversa y ocultar resultados actuales/históricos.
APK instalada mediante install -r con copia previa verificada
20261011-184711 (29 búsquedas, Passport revisión9). Arranque comprobado.
La prueba interactiva de permiso/activación Android, fix y selección táctil en
Travel queda pendiente: no se adquirió GPS ni se registraron elecciones o
experiencias para validar esta entrega. Las pruebas JVM no validan esos diálogos
ni el comportamiento táctil de MapLibre en el dispositivo.
