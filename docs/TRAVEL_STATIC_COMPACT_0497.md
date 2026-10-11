# Travel estático y compacto — 0.4.97

El autor pide sustituir el último estilo de botones por el de los tres
controles inferiores izquierdos del mapa y eliminar sus animaciones.
Búsqueda, preferencias e historial reutilizan PixelIcon, el mismo renderer
vectorial empleado por ajustes y ubicación del mapa. Intercambio mantiene
flechas verticales opuestas; A/B se dibujan como letras simples con la misma
paleta menta/crema. Ningún control carga los atlas raster v1/v2 ni ejecuta
un reloj de animación. Atlas anteriores conservados como fuente histórica.

La tarjeta mantiene A/intercambio/B a la izquierda y buscar/ajustes/historial
a la derecha. Iconos de 22 dp, botones de 40 dp, relleno vertical de 2 dp:
altura nominal 124 dp frente a 152 dp anteriores (28 dp menos, aproximadamente
18%). Se reduce también relleno horizontal de 8 a 4 dp para ampliar direcciones.
Las direcciones conservan una línea, truncamiento y editor completo. Los
controles mantienen etiquetas de accesibilidad EN/DE, estado habilitado y
selección menta de A/B; no se cambia la lógica de búsqueda, mapa o preferencias.
El mínimo táctil de 40 dp se aplica solo a esta tarjeta, sin áreas superpuestas.

Sin cambios en Passport, HOTCO, rutas, evidencia ni registro de experiencias.
Cambios visuales escritos en código vectorial; no se generan nuevas imágenes.

Verificación: assembleDebug y 91 pruebas JVM correctas (sin fallos, errores
ni exclusiones). Instalación install -r correcta en RZCY70K63HV y versión
confirmada 0.4.97, código 108. Respaldo verificado 20261011-214133 con 44
búsquedas y Passport 0504e5fa-b367-5835-8e36-890f4a08b68a revisión 9.
No se introducen viajes, selecciones reales ni respuestas para validar UI.
