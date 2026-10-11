# Daily Mobility Goals — Android 0.4.77 / 88

Entrega autorizada: sustituir Your priorities en Home por un perfil esférico
interactivo con iconos y saliencia, editar su importancia al tocar y guardar
en una nueva versión del Passport. El autor corrigió Weekly a Daily y pidió
UX intuitiva con estética JRPG.

La esfera usa proyección con perspectiva, profundidad, retícula y rotación
manual; no mantiene un bucle de animación. Los sprites existentes conservan
su identidad. Tamaño del sprite y borde luminoso representan la importancia;
las posiciones no representan relaciones psicológicas ni un presupuesto.
Cada botón tiene 52 dp; la descripción accesible incluye nombre y valoración.
Las metas posteriores se pueden descubrir girando la esfera. Puede haber
solapamiento en ciertas orientaciones; no constituye una jerarquía de metas.

Al tocar se abre un editor con nombre, sprite, importancia ordinal 1–7,
anclas not important / very important, controles y Save / Cancel. El borrador
del diálogo es local; cancelar no escribe nada. Los valores desconocidos se
muestran sin valoración y no se convierten en neutral. Si quedan otras metas
sin responder, se solicita completar la revisión diaria antes de guardar.

Save reutiliza /api/dyconet/daily-passport/confirm y la transacción existente
commitDailyPassport: se archiva el Passport anterior y se enlaza la revisión.
Solo cambia la meta elegida; el guardado confirma el conjunto diario completo.
Se verifica que la propuesta inicial siga vigente antes de enviar y al recibir
la respuesta; la transacción rechaza además un Passport activo concurrente.
Una respuesta fallida no cierra el editor ni presenta éxito.

Se conserva la escala ordinal y normalización (r−1)/6. Las metas declaradas
actualizan la entrada de importancia utilizada por HOTCO; esta acción no
activa posteriores bayesianos de comodidad/afecto ni copia estados simulados.
Los viajes históricos conservan su Passport de decisión.

Validación: compilación APK y 72 pruebas JVM aprobadas, incluidas edición
independiente sin mutar el original, rechazo de escalas heredadas/ausencias
y accesibilidad geométrica mediante rotación. Cinco pruebas backend de
confirmación diaria aprobadas. APK instalada conservando almacenamiento.
En teléfono se comprobó la esfera y apertura del editor de comodidad física
con el valor existente 6/7, sin modificar ni guardar respuestas del usuario.
No se inició seguimiento GPS ni Experience. La persistencia se comprobó con
pruebas de contrato, no escribiendo preferencias ficticias en el teléfono.
