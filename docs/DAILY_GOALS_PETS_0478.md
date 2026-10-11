# Daily goal rankings and pet portraits — 0.4.78 / 89

El autor solicita un puesto junto a cada esfera de meta, con puesto compartido
cuando las respuestas de importancia coinciden, y rostros 3D animados de la
mascota propia de cada avatar. Aclara explícitamente que quiere las mascotas,
no el rostro del avatar humano.

Ranking descendente sobre respuestas ordinales independientes 1–7: puesto
1 + número de metas con respuesta superior. Ejemplo 7,7,5 produce 1=,1=,3.
El símbolo = comunica empate y una leyenda aclara su significado. Ausencias
y valores inválidos no reciben puesto. El ranking se recalcula al actualizar
las metas; no modifica los valores, la normalización ni el contrato de Passport.
Las descripciones accesibles incluyen puesto y empate.

La esfera conserva giro manual y reserva el centro para el rostro de la
mascota elegida: Naturalista/perrito, Explorador/araña, Robot/minidron,
Criatura/espíritu. Se conservaron rasgos y colores de las referencias existentes.
Se interpretó «64 bit» como una referencia estética a JRPG de la era Nintendo64,
no como profundidad de bits del PNG. El resultado es un sprite 3D prerenderizado,
no una malla 3D giratoria.

Cuatro atlases PNG RGBA con nueve fotogramas en retícula 3×3 y transparencia
real. Naturalista, Explorador y Criatura: 1254×1254 (418×418 por fotograma).
Robot: 1278×1230 (426×410 por fotograma). Reposo/parpadeo, risa y duda.
Los originales generados se copiaron completos al proyecto sin editar píxeles.
Generador utilizado: herramienta integrada image_gen. Prompts completos y
referencias: [PET_PORTRAITS_0478_PROMPTS.md](PET_PORTRAITS_0478_PROMPTS.md).

Archivos finales en android-native/app/src/main/assets/art/jrpg:

- pet-naturalist-face-3d-v1.png
- pet-explorer-face-3d-v1.png
- pet-robot-face-3d-v1.png
- pet-creature-face-3d-v1.png

Toque breve activa risa, mantener presionado activa duda; la reacción dura
aproximadamente un segundo y vuelve a reposo. Respeta movimiento reducido
mostrando una expresión fija brevemente. Animación ligada al ciclo STARTED;
no usa reloj continuo de 60 fps ni gira automáticamente la esfera. Estas
expresiones decorativas no son feedback afectivo ni respuestas de Experience.

Validación: APK compilada, 74 pruebas JVM aprobadas, incluida clasificación
con empates/ausencias e identidad, formato y dimensiones de los cuatro atlases.
validation/verify_pet_portraits.py comprueba transparencia y contenido de cada
uno de los 36 fotogramas, sin editar imágenes. Inspección visual de los cuatro
atlases aprobada. APK instalada conservando almacenamiento, versión 0.4.78/89
confirmada mediante paquete. El teléfono se bloqueó antes de verificar la UI
final: quedan pendientes gestos y composición en el dispositivo; no se afirma
que hayan sido comprobados allí. No se modificaron respuestas del usuario, no
se inició GPS ni Experience, no se activó aprendizaje ni se cambió el backend.
