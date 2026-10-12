# Inicio Companion 0.4.102

La pantalla presenta un único título COMPANION, con letras suaves, degradado
menta y la C curva con punto dorado integrada como primera letra. Sustituye el
título pixel anterior; no hay un segundo símbolo encima. El brillo es estable.

Los cuatro avatares de inicio tienen 32 fotogramas nuevos cada uno: neutral,
preocupación, alivio y risa. Las cuatro mascotas salen a la vez en t=1,75 y
llegan en t=5,15 del reloj narrativo (75 % del tiempo real). Los avatares se
preocupan durante la fuga y pasan al alivio/risa al llegar las mascotas. El
fantasma conserva la órbita alrededor de los límites medidos de Let's go.

El cachorro, la araña, el dron y el fantasma son un 50 % mayores. Se amplía el
margen de la órbita y se desplaza el dron y su pantalla para conservar espacio
para el título. La araña conserva su hilo en la O; el cachorro llega a la C.

El retrato universitario tiene 24 fotogramas nuevos. La placa permanece fija;
la representación registra el centro inferior de pies/cuellos de los nuevos
atlas y conserva una escala común, evitando que manos o expresiones cambien
el anclaje. Se miden componentes de la silueta para ignorar motas aisladas.
El inspector solo lee PNG y escribe metadatos: no modifica píxeles.

Se corrige la pérdida de opacidad de los fundidos: las dos poses se suman con
alfa premultiplicada en una capa aislada (SrcOver + Plus). Superponer dos poses
con SrcOver bajaba la opacidad de sus píxeles comunes a mitad de transición.
Avatares, mascotas, retrato y hongo usan ahora la mezcla corregida. Se extiende
la interpolación del cachorro y el hongo ya no cambia por cortes de fotograma.
El reloj se lee dentro de Canvas; actualizarlo invalida dibujo sin recomponer
texto y distribución en cada fotograma. Movimiento reducido usa poses fijas
y no inicia el bucle de reloj. El bucle se suspende fuera de STARTED.

Los atlas de inicio usan claves startup_* separadas de los anteriores, que
siguen disponibles para otras pantallas. Recursos nuevos:
`android-native/app/src/main/assets/art/jrpg/companion-startup-*-v3.png`.
Generados mediante la herramienta integrada image_gen.imagegen; referencias y
prompts exactos: [COMPANION_STARTUP_04102_PROMPTS.json](COMPANION_STARTUP_04102_PROMPTS.json).
Dimensiones, transparencia y recuentos:
[COMPANION_STARTUP_04102_ASSETS.json](COMPANION_STARTUP_04102_ASSETS.json).

## Comprobación

`node android-native/scripts/build.mjs assembleDebug` terminó correctamente.
Instalación de reemplazo en Samsung SM-A566B/RZCY70K63HV; versión 0.4.102,
código 113. Se conserva USB reverse 8077. Capturas de entrada, fuga, llegada
y reposo en `.local-backups/startup-04102`, obtenidas con
`validation/capture_startup_04102.py` sin pulsar Let's go. Las capturas permiten
revisar composición y expresiones; no prueban por sí solas fluidez perceptiva.
El archivo gfxinfo.txt conserva las métricas del renderizado en el dispositivo.
En la captura final con las mascotas ampliadas: 1.952 fotogramas, 8 fuera del
plazo de Android (0,41 %), percentil 95 de 13 ms y percentil 99 de 17 ms.
Incluye el arranque y las capturas; no es una garantía de cero saltos en todos
los dispositivos. Se inspeccionaron las capturas de fuga y risa, la C integrada,
la legibilidad del título/botón y la placa universitaria.
No se añaden pruebas que repliquen el código de dibujo.
