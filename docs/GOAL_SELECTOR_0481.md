# Daily Mobility Goals — selector 0.4.81 / 92

Arrastrar desde la mascota original 2D hacia una meta y soltar abre
su editor existente. La mascota gira en el plano 2D usando los dibujos originales.
La meta apuntada crece un 30%, su borde/fondo cambia a mint y muestra su nombre.
Las restantes se atenúan sin recortar los rangos/empates. Un toque directo en una meta sigue abriendo el editor.
Soltar en el centro o fuera del límite cancela. Interrumpir la app cancela el gesto.
Corrección solicitada por el autor: eliminar el tiempo de espera de long press
y la reacción al tocar/pulsar la mascota de esta tarjeta. El detector empieza
al superar el umbral normal de arrastre; la reacción alegre original se recicla
al apuntar a una alternativa y se reinicia al cambiar de meta, sin detector
decorativo compitiendo por eventos. Movimiento reducido conserva pose fija.

Labels EN/DE según idioma de la app:

| EN | DE |
|---|---|
| Eco-mode | Öko-Modus |
| Physical Activity | Körperliche Aktivität |
| Savings | Sparen |
| Time | Zeit |
| Comfort | Komfort |
| Reliability | Zuverlässigkeit |
| Flexibility | Flexibilität |
| Security | Schutz |
| Infection | Infektion |
| Space | Freiraum |
| Traffic | Verkehr |

Solo Save confirma importancia ordinal independiente 1–7 mediante la transacción
diaria existente y genera una revisión enlazada del Passport. El gesto no guarda
puntuaciones ni evidencia afectiva. Ranking y empates (=) se conservan.
Sin cambios de aprendizaje, Experience, HOTCO ni adquisición de GPS.

Once objetos JRPG nuevos generados individualmente con image_gen integrado.
Referencia estética: naturalist-sheet-v2.png. Assets originales guardados sin
modificar. Decodificación en IO con muestreo a máximo 512 px y caché 8 MiB.

## Assets y nombres acordados

- **Eco-mode** (env): [sprite](../android-native/app/src/main/assets/art/jrpg/goal-env-inventory-v1.png). Subject del prompt: a rich emerald leaf embracing a small blue green planet, one cohesive object.
- **Physical Activity** (health_activity): [sprite](../android-native/app/src/main/assets/art/jrpg/goal-health_activity-inventory-v1.png). Subject del prompt: a sturdy mint and cream athletic sneaker with a small footprint and motion streak.
- **Savings** (cost): [sprite](../android-native/app/src/main/assets/art/jrpg/goal-cost-inventory-v1.png). Subject del prompt: a small brown leather coin purse with one golden coin.
- **Time** (time): [sprite](../android-native/app/src/main/assets/art/jrpg/goal-time-inventory-v1.png). Subject del prompt: a golden wooden hourglass with tiny cream feather wings.
- **Comfort** (comfort_physical): [sprite](../android-native/app/src/main/assets/art/jrpg/goal-comfort_physical-inventory-v1.png). Subject del prompt: a plush mint green upholstered travel seat with a warm cream cushion.
- **Reliability** (reliable): [sprite](../android-native/app/src/main/assets/art/jrpg/goal-reliable-inventory-v1.png). Subject del prompt: a compact parchment calendar with a small round clock and bold green check mark.
- **Flexibility** (flex): [sprite](../android-native/app/src/main/assets/art/jrpg/goal-flex-inventory-v1.png). Subject del prompt: a gold and mint compass above two visibly branching pathways, one cohesive emblem.
- **Security** (safety_crime): [sprite](../android-native/app/src/main/assets/art/jrpg/goal-safety_crime-inventory-v1.png). Subject del prompt: a warm glowing lantern inside a sturdy metal shield, personal security emblem.
- **Infection** (health_infection): [sprite](../android-native/app/src/main/assets/art/jrpg/goal-health_infection-inventory-v1.png). Subject del prompt: a mint metal shield blocking three small purple microbe particles outside its edge.
- **Space** (crowding): [sprite](../android-native/app/src/main/assets/art/jrpg/goal-crowding-inventory-v1.png). Subject del prompt: one small friendly neutral human silhouette surrounded by a clearly visible spacious circular boundary, privacy and personal space.
- **Traffic** (safety_accident): [sprite](../android-native/app/src/main/assets/art/jrpg/goal-safety_accident-inventory-v1.png). Subject del prompt: a sturdy golden cycling safety helmet beside a small pedestrian crossing road sign, traffic safety.

## Prompt común

Use case: stylized-concept. Asset type: ONE transparent inventory item sprite for a JRPG Android mobility goals interface. The input image is ONLY an art style reference, not an edit target: match its detailed 2D pixel-art drawing, dark readable contours, warm shaded colors, softly painted pixel clusters and charming drawn volume. Primary subject: [subject indicado arriba]. Single cohesive item centered and complete, about 80% of square canvas, ample transparent padding. Designed to stay instantly recognizable at 40 to 64 display pixels, clear silhouette and a few bold details, high resolution source. Palette mint/emerald, warm gold, cream, rich brown and soft dark violet, light from top left. True transparent background. No characters from reference, no scene, no labels, no text, no numbers, no circular button, no decorative frame, no UI, no photorealism, no 3D render, no extra variants, not a sprite sheet.

## Validación

APK compilada e instalada con adb install -r; 79 pruebas JVM aprobadas.
Once PNG RGBA de 1254×1254 con alpha 0–255; se inspeccionaron arte y presentación.
Prueba de navegación en teléfono EN y DE: DOWN/MOVE sin espera deliberada de
long press muestra Eco-mode/Öko-Modus y Physical Activity/Körperliche Aktivität,
resalta la alternativa y gira la mascota. UP abre el editor; Cancel no guarda.
Soltar en el centro cancela y el toque directo conserva la alternativa accesible.
No se ingresaron ratings ni se pulsó Save. No instrumentación ni GPS/Experience.
Esto verifica ausencia de espera programada, no mide latencia completa ni FPS.

Copias comprobadas antes 20261011-175909 y después 20261011-180623: Passport,
todos los valores KV salvo idioma y los 25 históricos completos son idénticos.
La prueba EN/DE añadió preferencia explícita EN, restaurando el idioma visible.
Las copias parciales de verificación por falta de espacio se descartaron; se
mantienen copias completas verificadas y no se borraron datos del teléfono.
