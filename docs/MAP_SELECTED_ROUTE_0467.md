# Resaltado de ruta seleccionada — 0.4.67

Cambio de UI autorizado por el autor: reducir saturación visual del mapa y
resaltar la alternativa seleccionada con trazado luminoso animado.

La ruta seleccionada se dibuja encima de las demás, con halo del color de
cada tramo, contorno suavizado, línea principal y luz blanca en movimiento.
Las alternativas tienen línea más fina, opacidad de color 19% y contorno 10%;
el contorno ya no permanece negro opaco. Los caminos aproximados/suplementales
conservan trazos discontinuos y sus metadatos de procedencia. No cambia la
geometría, tiempos, selección, exposición, Passport ni HOTCO.

Animación solo con ajuste de movimiento completo y pantalla STARTED. La fase
se lee al dibujar Canvas; no reproyecta puntos ni reconstruye geometrías en
cada frame. Con movimiento reducido queda un resaltado luminoso estático.

APK 0.4.67/code 78 compilada e instalada preservando datos, versión comprobada.
Se inspeccionó el mapa físico con caminar seleccionado y alternativas
semitransparentes, y después en la pantalla de seguimiento del usuario.
Capturas locales: `.local-backups/map-highlight-0467-walk.png` y
`.local-backups/map-highlight-0467-tracking.png`.
Validación de compilación y presentación; no se añadieron pruebas para este
cambio de dibujo. No se iniciaron viajes ni se emitieron controles GPS como
parte de esta validación.
