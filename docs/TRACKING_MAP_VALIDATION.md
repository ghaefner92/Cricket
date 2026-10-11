# Seguimiento con mapa · Cricket 0.4.3

Al iniciar GPS o reanudar un viaje pausado se abre una vista dedicada con el
mapa de la ruta elegida. La ruta proviene del registro del viaje, por lo que
una búsqueda posterior no la sustituye. Durante el seguimiento se ocultan las
pestañas de búsqueda y la navegación inferior.

El panel muestra estado, tiempo transcurrido y distancia aproximada; ofrece
Pausar/Reanudar, Finalizar y Cancelar (con confirmación). Finalizar detiene GPS
y abre el formulario de experiencia. Cancelar conserva historial y puntos,
libera el viaje pendiente y permite iniciar otro. Home permite salir de la
vista manteniendo el estado del seguimiento.

El mapa admite zoom, encuadrar la ruta, seguimiento automático y volver a
centrar en la posición. Los puntos GPS retenidos se representan con un marcador
azul y círculo de precisión. La traza respeta los segmentos: no une pausas o
huecos. Sin un punto suficientemente preciso se muestra espera, no una posición
inventada. Las teselas se recortan al área del mapa para no tapar controles.

Un viaje pausado se identifica como GPS sin grabar y tiene un acceso Manage.
La recuperación elimina referencias huérfanas y pausa una grabación interrumpida;
no cancela viajes ni genera experiencias automáticamente.

## Pruebas ejecutadas

- 7 pruebas Kotlin (`Phase1Test`): contratos compartidos, ciclo de tracking,
  distancia y rechazo de puntos inválidos, pausas, confirmación y recomendaciones.
- 4 pruebas Python (`validation/phase1_test.py`): contratos y endpoints.
- 1 prueba instrumentada (`TrackingMapDeviceTest`) en Samsung SM-A566B:
  registro de más de 4 MB con Unicode/emojis, almacenamiento e historial,
  mapa con geometría, controles reales Pause/Resume/Finish/Cancel, recreación
  de Activity y paso a confirmación. Viajes temporales eliminados al terminar;
  se preservan registros y referencias previas del usuario.

Los sensores reales se activan durante la prueba, sin inyectar GPS ficticio.
En la captura revisada el teléfono estaba esperando una posición precisa:
la distancia de un recorrido físico debe comprobarse al aire libre.

APK: `android-native/app/build/outputs/apk/debug/app-debug.apk`.
Versión 0.4.3, código 14. Instalación con `adb install -r` conserva los datos.

## Para probarlo

1. Buscar una ruta, elegirla y pulsar Start journey with GPS.
2. Comprobar mapa y panel; caminar al aire libre con ubicación precisa activada.
3. Pulsar Pause y Resume. La pausa no añade distancia entre segmentos.
4. Pulsar Finish: aparece la confirmación de experiencia.
5. Para abandonar una grabación, usar Cancel journey y confirmar.
