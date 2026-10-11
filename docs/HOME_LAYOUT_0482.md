# Home y sprites compartidos — Android 0.4.82 / 93

Orden: cabecera compacta de planificación, Daily Mobility Goals, Current weather.
La bienvenida de 80 dp con botón en otra fila se sustituye por retrato de 40 dp,
Where to?/Wohin? y Plan journey/Reise planen en una sola fila. En anchos menores
de 310 dp o con la preferencia de letra grande, el botón pasa a una fila completa.
Se elimina el reloj de animación que recomponía toda la cabecera para pulsar el botón.

Se mantienen sprites, colores, botones y títulos pixelados JRPG. El texto de
instrucciones/etiquetas de Home utiliza SansSerif 12–14 sp; valores 16 sp, con
incrementos al activar letra grande. Títulos pixelados 12/14 sp para mantener
sus proporciones y encajar EN/DE; no escalar ese tipo de letra como una fuente
proporcional normal. Márgenes exteriores y entre tarjetas 12 dp. La mascota y
el área de selección conservan 232/382 dp y sus controles Save/Cancel.

Clima Home: seis mediciones en tres columnas y dos filas; valores más visibles
que etiquetas, iconos distintos y targets de al menos 48 dp. En anchos pequeños
o con letra grande se usan dos columnas. La hora conocida de lluvia se sitúa
debajo cuando está disponible; no inventar reloj ni modificar elegibilidad.
Las pantallas de rutas conservan su presentación meteorológica anterior.

Ampliación explícita del autor: utilizar los once sprites nuevos en toda la app.
PixelIcon intercepta claves de metas y ContextSprite resuelve aliases needKeys
antes de renderizar, ambos mediante GoalInventorySprite y su caché compartida.
Así se unifican Passport, creación/revisión de metas, Experience, comodidad,
conexiones y perfiles de rutas, escenas y diálogos. No cambiar iconos de modos,
clima ni navegación que no representen una meta. Mientras se carga el PNG se
reserva el mismo espacio, sin mostrar un emblema antiguo ni recursión de fallback.
Los PNG y prompts originales se conservan en docs/GOAL_SELECTOR_0481.md.

Validación: compilación y 79 pruebas JVM aprobadas. APK instalada con install -r
tras copia completa previa verificada (20261011-181629, 26 históricos). El autor
autorizó actualizar pese a la búsqueda abierta; no se introdujeron respuestas,
se guardaron metas ni se inició GPS/Experience para las pruebas.
Se inspeccionaron Home EN/DE, las once metas visibles al entrar, clima 3×2 y
los nuevos sprites en el listado Goals del Passport. El selector se verificó
tras reorganizar Home: arrastre/nombre/release/editor/cancel y tap directo,
sin guardar. La alternativa de letra grande/ancho pequeño se implementó y
compiló, pero no se verificó visualmente en dispositivo en esta entrega.
No cambios de esquema, escala, aprendizaje ni API de Passport/HOTCO.
