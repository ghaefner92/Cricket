# Travel: búsqueda flotante — Android 0.4.85 / 96

El autor acepta sustituir la distribución 60/40 por un mapa que ocupa toda el
área disponible de Travel, manteniendo navegación inferior. Se conserva el
motor MapLibre y se reutiliza la adquisición opcional de ubicación de 0.4.83.

Una tarjeta JRPG flotante superior reúne dos filas de dirección A/B, intercambio,
menú de opciones y Find routes. Cada fila muestra una sola línea con elipsis.
Tocar la letra A/B activa la selección en el mapa; tocar la dirección abre un
panel inferior con AddressField, teclado y sugerencias existentes. Elegir un
resultado cierra el editor y, para A, pasa el objetivo a B. El botón Map o Back
cierra el editor sin confirmar una dirección escrita que no sea un resultado.
El borrador permanece disponible y una dirección no seleccionada impide buscar.

El toque directo del mapa sigue seleccionando inmediatamente el punto activo,
sin confirmación. Se elimina el selector anterior duplicado y Search journey.
Find routes mantiene una etiqueta breve y flecha; no se sustituye por una lupa
que pueda confundirse con geocodificar. Solo ese botón calcula las alternativas
y congela contexto/Passport. El menú contiene recientes y preferencias en paneles
inferiores. Se conservan selección A/B desde recientes y edición de preferencias.
No existe un segundo botón My location en la tarjeta: permanece el del mapa.

Los controles inferiores del mapa siguen libres. Brújula, leyenda de movilidad
y aviso de carga se desplazan por debajo de la tarjeta en planificación.
Navegación Travel: contenido de64dp más margen de navegación Android, sin reducir
los targets interactivos de la tarjeta por debajo de48dp. Otras pantallas
conservan la navegación anterior. Colores/sprites JRPG originales se mantienen.

Validación: build Debug y82 pruebas JVM aprobadas; copia previa SQLite/preferences
verificada antes de install-r: 20261011-191444,31 búsquedas, Passport revisión9. No se introdujeron respuestas, elecciones de modo,
experiencias ni cambios Passport para probar. La revisión visual del editor,
teclado, navegación compacta y gestos en el nuevo diseño queda pendiente en el
teléfono. Las pruebas JVM no acreditan esa interacción en dispositivo.
