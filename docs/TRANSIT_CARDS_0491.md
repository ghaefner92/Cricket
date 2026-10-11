# Tarjetas de conexiones — 0.4.91

El autor solicita quitar la alerta de trazado de referencia no verificado del
mapa y presentar las tres conexiones PT como tarjetas con marcos y sprites
animados de primer, segundo y tercer lugar.

La barra de conexiones del menú inferior ahora tiene hasta tres tarjetas JRPG
independientes. Cada tarjeta muestra líneas, hora de llegada y caminata inicial.
El marco dorado identifica la tarjeta seleccionada; pulsarla cambia el trazado
visualizado, sin registrar una elección ni iniciar un viaje. El orden conserva
la decisión anterior: llegada más temprana al destino entre alternativas
disponibles y no vencidas, una salida por patrón de conexión.

Sprites de medalla en oro, plata y bronce, con dígitos pixelados 1/2/3, pequeños
destellos y movimiento por poses. Se dibujan con Canvas, coherentes con los
iconos existentes, y solo se anima su área de 24 dp. La preferencia de movimiento
reducido los mantiene estáticos. Etiquetas accesibles en inglés y alemán incluyen
puesto, conexión, llegada y caminata; los sprites son decorativos.

Se elimina el aviso visible “Reference tracks · service path unverified” y su
traducción. Los metadatos de geometría y las verificaciones de rutas se conservan.
No se modifica el Passport, HOTCO, evidencia, horarios ni cálculos de llegada.

Verificación: compilación APK y suite JVM existente de 89 pruebas. No se añaden
pruebas que reproduzcan el diseño estético; la comprobación visual interactiva
de tamaño, lectura y animación corresponde al teléfono.
