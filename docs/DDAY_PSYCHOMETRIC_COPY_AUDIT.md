# Auditoría psicométrica del contenido · 0.4.69

Fecha: 2026-10-11. Revisión de contenido `psychometric-copy-1.0`.
El autor autoriza cambiar preguntas, instrucciones y etiquetas, conservando
los elementos estéticos. Esta entrega modifica texto y metadatos de versión;
no cambia componentes, sprites, colores, tamaños, escalas, algoritmos ni
reglas de obligatoriedad. No constituye validación científica del instrumento
ni de HOTCO-CT.

## Objetivo y puente con HOTCO-CT

La app recoge declaraciones individuales para comparar alternativas de
movilidad y conservar experiencias longitudinales. Debe distinguir qué quiere
la persona, qué espera de un modo, cómo lo valora afectivamente y qué ocurrió
en un viaje identificado. En el HOTCO-CT implementado, importancia inicializa
necesidades; creencias describen conexiones modo–necesidad; valencias inicializan
componentes afectivos; disponibilidad delimita alternativas. Las activaciones
posteriores son resultados de una simulación, no nuevas respuestas del usuario.

La separación entre metas, acciones, relaciones de facilitación y valoración
afectiva tiene antecedente conceptual en [Thagard (2001)](https://zimmer.fresnostate.edu/~sasanr/Teaching-Material/MIS/DM/how-to-make-decisions.html).
Ese antecedente no demuestra la validez del HOTCO-CT experimental del autor,
sus parámetros ni este cuestionario. La revisión del puente concreto se apoya
en `input_mapping_v4_3.py`, `hotco_ct_v4_3.py`, D_DAY.md y los protocolos del piloto.

| Familia | Referente y constructo operativo | Escala y uso | Inferencia que no permite |
|---|---|---|---|
| Identidad/compañero | Nombre y apariencia elegidos | Configuración | Personalidad o estado psicológico |
| Uso habitual | Declaración de uso usual de cada modo | Booleano descriptivo | Frecuencia cuantitativa o automaticidad del hábito |
| Disponibilidad | Modos que la persona permite usar en sugerencias | Booleano de elegibilidad | Preferencia, emoción o importancia |
| Metas del día | Importancia de cada objetivo para los viajes de hoy | 1–7; `(r−1)/6` operativo | Satisfacción, logro o pesos relativos que suman uno |
| Creencias iniciales | Opinión habitual sobre cuánto ayuda/dificulta un modo a satisfacer una meta | 1–7 bipolar; `(r−4)/3` | Efecto causal o rendimiento objetivo del modo |
| Afecto inicial | Valoración habitual de agrado/desagrado al usar un modo | −3…+3; `r/3` | Emoción de un viaje futuro, rasgo estable validado o intensidad de emociones específicas |
| Realización | Qué viaje/modos se realizaron | Declaración identificada; GPS separado | Comodidad, emoción o motivos inferidos de GPS |
| Comodidad posterior | Contribución percibida de un modo realizado a satisfacer comodidad física | 1–7 bipolar, neutral 4 | Comodidad absoluta, agrado emocional o efecto causal aislado del modo |
| Afecto posterior | Agrado/desagrado global durante la parte del viaje realizada en cada modo | −3…+3, neutral 0 explícito | Arousal, estrés, tristeza, ira, mezcla emocional o causalidad meteorológica |
| Otras satisfacciones | Qué tan bien el viaje realizado satisfizo cada objetivo elegido | 1–7 muy mal–muy bien, opcional | Importancia de la meta o escala equivalente a ayuda–dificulta |
| Nota libre | Información adicional del viaje | Texto opcional, no puntuado | Explicación causal comprobada de la decisión |
| Explicación/reflexión | Lectura de resultados guardados y preguntas opcionales sobre la elección | Sin puntuación psicométrica | Motivos reales deducidos del modelo, acierto psicológico o experiencia observada |

Las once metas son dimensiones distintas de una decisión, no once ítems
intercambiables de una única escala reflectiva. No sumar todas como un rasgo
general ni usar alfa/omega global para certificar su calidad. Tampoco tratar
las celdas imputadas por Ridge como observaciones independientes del usuario.

## Criterios empleados y fuentes

[COSMIN: estudio metodológico de Terwee y colaboradores (2018)](https://pmc.ncbi.nlm.nih.gov/articles/PMC5891557/)
prioriza pertinencia, cobertura y comprensión del contenido, incluidos
opciones de respuesta y periodo recordado. Se usan esos criterios como guía
de revisión; COSMIN fue desarrollado para medidas reportadas por pacientes,
y aquí su aplicación a decisiones de movilidad es una adaptación razonada,
no una certificación COSMIN.

[ITC, Guidelines for Translating and Adapting Tests (2017)](https://www.intestcom.org/files/guideline_test_adaptation_2ed.pdf)
fundamenta revisar equivalencia de significado y procesos de respuesta entre
idiomas, además de traducción literal. EN/DE se revisaron conjuntamente; no se
ha comprobado equivalencia con participantes ni traducción profesional.

[Dejonckheere y colaboradores (2022)](https://doi.org/10.1037/pas0001178)
estudia cómo evaluar error de medición de ítems afectivos únicos en experience
sampling mediante repeticiones cuidadosamente diseñadas. Es una referencia
para una futura evaluación de fiabilidad; no se traslada su fiabilidad al ítem
de esta app ni se añaden repeticiones en producción.

[McNeish y colaboradores (2021)](https://asu.elsevierpure.com/en/publications/measurement-in-intensive-longitudinal-data/)
describe modelos de medición e invariancia entre personas y ocasiones en datos
longitudinales intensivos. Motiva separar cambio del fenómeno y cambio del
funcionamiento de la medición. Su modelo multítem no es identificable como tal
con el único ítem afectivo de cada modo de esta app.

[Myin-Germeys y colaboradores (2018)](https://doi.org/10.1002/wps.20513)
presenta el uso de muestreo de experiencias para estudiar variación dentro de
la persona en su contexto cotidiano. Nuestra recogida es por evento de viaje,
con resumen posterior; una respuesta tardía no debe describirse como medición
instantánea solo por haberse obtenido en un teléfono.

Las decisiones específicas descritas debajo son aplicaciones al código y
protocolo de D-Day, no resultados empíricos de esos artículos sobre Cricket.

## Revisión aplicada por familia

### Metas e instrucciones de importancia

La pregunta central ahora pide la importancia de cada meta para los viajes
de hoy, con extremos explícitos 1 no importante y 7 muy importante. Mantiene
respuestas independientes y la posibilidad de dar igual importancia a varias.
No describe esas respuestas como probabilidades ni como un reparto fijo.

Se sustituyen explicaciones imperativas como «dar más importancia» por
definiciones neutrales del dominio. El Passport dice «importancia guardada»:
su vista también puede mostrar un snapshot histórico y no debe llamarlo
automáticamente «de hoy». Las explicaciones de rutas usan importancia guardada,
sin el antiguo supuesto de prioridades semanales.

Los títulos y definiciones estrechan «comodidad» a comodidad física y
«proteger la salud» a evitar infección. Esto concuerda con las necesidades
`comfort` y `health_infection` ya existentes; no introduce un constructo nuevo.
Los textos sobre seguridad describen su importancia y percepción, no garantizan
protección objetiva. El coste, tiempo y emisiones mantienen referentes distintos.

Flexibilidad–independencia y espacio–privacidad siguen siendo metas compuestas.
La explicación identifica que se pide una valoración conjunta. Esto reduce
ambigüedad, pero no resuelve posibles respuestas opuestas a sus componentes.
Separar esas dimensiones exigiría decidir nuevos ítems y revisar el puente
con HOTCO; no se hace dentro de una entrega limitada al texto.

### Passport: uso, disponibilidad, creencias y afecto

La pregunta de uso habitual se refiere a **este modo**, no a «cómo te mueves»
en general mientras los botones responden a una única opción. Uso habitual y
disponibilidad se distinguen en las instrucciones. No se fija una frecuencia
ni se interpreta el booleano como medida validada de hábito psicológico.

Las cuatro preguntas de creencias mantienen el modo/meta ya seleccionados,
pero preguntan explícitamente cuánto, en opinión de la persona, ayuda o
dificulta satisfacer la meta en sus viajes habituales. La instrucción separa
esa relación de importancia y agrado. Neutral 4 se elige explícitamente;
ausencia no se llama neutral. Los siete anclajes existentes se conservan.

El afecto inicial pregunta por el agrado/desagrado global de usar el modo en
los viajes habituales. El referente es el modo y su uso, no el ánimo general
de la persona. Las etiquetas y descripciones accesibles reflejan la dirección
desagradable–agradable. Neutral cero y respuesta ausente se distinguen.
La etiqueta de perfil aclara que es una declaración habitual, no una predicción
contextual del recorrido abierto.

«Habitual» sigue siendo un periodo subjetivo. No se impone retrospectivamente
una ventana de siete o treinta días: cambiarla sería una decisión de protocolo
y requeriría evaluar su efecto y versionarla como tal.

### Experience: evento, atribución y recuerdo

La instrucción pide informar del viaje realmente realizado cuanto antes. No
establece una ventana numérica de exclusión ni convierte una respuesta tardía
en momento presente. Si una respuesta obligatoria no se puede juzgar, indica
dejar el formulario sin confirmar, en vez de usar neutral como desconocimiento.
El flujo existente sigue requiriendo respuestas para confirmar.

La pregunta central de comodidad del protocolo permanece intacta. Se aclara
la definición física y, cuando hay varios modos, que una respuesta describe
solo la parte realizada en el modo elegido. Se conserva una respuesta de
comodidad por viaje: no se propaga a los demás modos ni se exige un cuestionario
completo adicional.

Cada respuesta afectiva se refiere al agrado/desagrado global durante la parte
del viaje realizada en ese modo. El texto no pregunta por el estado emocional
actual al contestar ni por preferencias en general. Se conserva −3…+3 y el
registro por modo. Un único resumen ordinal no identifica emociones específicas
ni diferencia neutralidad de combinaciones de emociones que den un balance
cercano a cero; no se añade una medición de ambivalencia.

La satisfacción opcional pregunta por la meta en el viaje completo, con
anclas muy mal–muy bien. Se explica que no es importancia ni emoción, y que
se puede dejar sin responder si no se puede juzgar. La nota pasa de «por qué
esta elección» a información adicional del viaje; su texto no se usa como
evidencia causal ni como respuesta psicométrica puntuable.

### Interpretación, aprendizaje y reflexión

Los textos identifican opiniones habituales, estimaciones iniciales y
resultados de simulación. Una conexión positiva representa una expectativa
modo–meta, no satisfacción observada en una ruta concreta. El texto posterior
al guardado conserva declaraciones originales y experiencia separadas; se
elimina la promesa desactualizada de que el aprendizaje se añadirá después.

Se revisaron las preguntas opcionales de reflexión del catálogo de voz.
Ahora se refieren al momento de la elección, sin llamar «hoy» a una elección
que también puede consultarse después en el historial. Preguntan qué importó
sin exigir una única prioridad máxima, y si las metas señaladas importaron
realmente, sin presuponer que explicaron la decisión. Permanecen sin puntuación.
Sus respuestas/no respuestas no alimentan el aprendizaje de
comodidad o afecto. «Texto verificado» ahora significa comprobado frente a la
elección y resultados guardados; no implica validación de estados psicológicos.

Las nuevas explicaciones usan `choice-voice-1.4`. La versión 1.3 y todos los
catálogos anteriores se conservan literalmente en `legacy`, para representar
el historial sin sustituir sus textos originales. Las referencias internas
`weekly_goals` y los algoritmos se conservan por compatibilidad; cambiar un
nombre interno no es necesario para corregir la presentación al usuario.

Nombre del compañero, búsqueda de direcciones, fechas/horas, permisos GPS,
exportación, importación y preferencias de visualización son campos operativos,
no ítems psicométricos. Sus preguntas no deben convertirse en evidencia de
personalidad, preferencias afectivas ni satisfacción. No requieren reescritura
por el mero hecho de estar en una pantalla de la app.

## Problemas que el texto solo no puede resolver

1. **Comparabilidad longitudinal de redacción.** Se conserva un manifiesto
   antes/después y versión de APK. Los contratos guardan versión de escala y
   del ítem de comodidad, pero no la variante exacta de todas las instrucciones.
   Una búsqueda antigua respondida con APK nueva puede conservar la versión
   antigua de búsqueda: no basta para reconstruir su redacción. No reetiquetar
   registros históricos ni asumir invariancia de instrucciones v1/v2. Queda
   pendiente guardar revisión de contenido por respuesta y estudiar un puente
   entre variantes. No se amplía el contrato dentro del cambio solo de texto.
2. **Ausencia y desconocimiento.** Passport exige valorar los cuatro modos;
   hay personas que no pueden juzgar alguno. Texto honesto evita fingir neutral,
   pero no crea una opción «no puedo juzgar» ni resuelve el bloqueo del flujo.
   Haría falta una política explícita de datos faltantes y elegibilidad.
3. **Recorridos multimodales.** «Como estaba previsto» actualmente representa
   solo el modo principal; puede haber accesos a pie de auto/PT. La selección
   «con cambios» permite informar varios modos, pero el texto no corrige el
   contrato de realización ni su atribución automáticamente. No afirmar que
   toda la exposición multimodal queda medida.
4. **Selección de comodidad.** La persona elige un modo cuando hay varios.
   Esa selección puede ser informativa; no es un tramo aleatorio. Registrar
   selección no elimina sesgo. Hay que predefinir una regla futura sin cambiar
   la carga acordada de una pregunta por viaje.
5. **Tiempo y contexto.** La hora de confirmación permite estudiar demora
   aproximada desde el final, pero no es la hora de cada respuesta afectiva.
   Hay que separar recuerdo tardío, cambio real y contexto. Los sensores al
   seleccionar una ruta no certifican exposición personal de todo el viaje.
6. **Proceso de respuesta visual.** Escenas, expresiones y reacciones del
   compañero pueden influir en la respuesta. No se cambiaron por orden del
   autor. Su ausencia de sesgo necesita un estudio, no una afirmación basada
   en esta revisión textual.

## Validación psicométrica posterior: decisiones y orden

Primero, definir población, uso previsto y el dominio de cada constructo con
usuarios. Entrevistas cognitivas EN/DE deben comprobar cómo interpretan la
pregunta, qué recuerdan y cómo eligen categoría, especialmente neutral, modo
parcial, infección y metas compuestas. No declarar un número mágico de
entrevistas como garantía; documentar diversidad y temas aún no resueltos.
Estas entrevistas y recogidas reales quedan para después; no se realizan ahora.

Después, diseñar una recogida por eventos con referentes e instrucciones
versionados, demora observable y omisiones conservadas. Predefinir criterios de
exclusión y análisis de sensibilidad a demora/selección; no inventar un corte
de diez o sesenta minutos sin decidir y evaluar su función. Evitar condicionar
incentivos al signo de respuestas o al acuerdo con la recomendación.

Analizar datos crudos con modelos ordinales. Las normalizaciones actuales son
puentes operativos con HOTCO, no prueba de distancias psicológicas iguales.
Investigar distribución/categorías poco usadas, efectos suelo/techo, omisiones
y sensibilidad a puntos de corte. Afecto habitual puede informar un prior, pero
no es la misma medición que un episodio: evaluar discrepancias y fuerza del prior.

La fiabilidad dentro de la persona debe separar fluctuación real de error.
Considerar repeticiones diseñadas en una submuestra o varios indicadores en un
módulo de validación; repetir después de cambiar el contexto no es un test–retest
de estado estable. Con un solo ítem por constructo no se identifican libremente
error de medición, estado latente y funcionamiento diferencial. No calcular
alfa de cuatro modos como si fueran cuatro indicadores de la misma emoción.

Con datos adecuados, evaluar equivalencia de umbrales/redacción por idioma,
persona, tiempo y situación mediante modelos ordinales multigrupo/multinivel
o modelos de medición con varios indicadores. IRT/DIF, generalizabilidad o
DSEM son opciones según diseño e identificabilidad, no resultados obtenidos
por nombrarlos ni algoritmos activados en esta entrega.

La evidencia de constructo debe incluir hipótesis de convergencia y
discriminación: comodidad física y agrado pueden correlacionar sin ser el
mismo constructo; importancia, satisfacción y preferencia siguen separadas.
Una mayor predicción del modelo contextual no prueba esa discriminación ni
causalidad de la lluvia. Comparar dentro de la persona y entre personas por
separado; no asumir que un patrón poblacional explica un cambio individual.

Finalmente, evaluar predicciones prospectivas ordinales con orden temporal,
calibración, puntuaciones probabilísticas y experiencias comunes entre modelos.
Separar precisión de medición, predicción de respuesta, fidelidad de explicación
y sensibilidad de la recomendación. Acuerdo con una elección influida por la
app no valida estados mentales. Bayes continúa como método; no se introduce
F3, RL, deep learning ni activación de posteriores en HOTCO.

## Evidencia técnica y cobertura

`validation/verify_psychometric_copy.py` compara el código con snapshots
tomados antes de esta entrega: Kotlin conserva todos los tokens fuera de
literales de texto, salvo el número de versión de APK. Los tres módulos Python
de explicación conservan su AST ejecutable salvo cadenas. Comprueba arte,
claves, anclajes, sincronización de catálogos y preservación de las versiones
históricas. Ejercita selección de frases EN/DE y rechazo de cobertura incompleta
sin llamar a un LLM ni guardar experiencias.

Inventario: `output/psychometric-copy-0469/native-text-inventory.json` contiene
554 pares estáticos EN/DE encontrados en fuentes nativas. Los textos que usan
ramas de idioma, catálogos o interpolaciones se revisan también en las familias
descritas arriba; el número no representa ítems psicométricos ni comprensión
empíricamente demostrada. Cambios exactos: `native-changes.json`, catálogo
vigente y snapshots locales `.local-backups/psychometric-copy-0469/`.

La entrega no migra respuestas, no altera anclas/normalizaciones ni ejecuta
simulaciones como evidencia. La fecha de esta revisión y la versión de APK
identifican una variante de contenido; no garantizan equivalencia longitudinal.

Comprobación final: APK 0.4.69/code 80 instalada y versión comprobada;
assembleDebug y 60 pruebas JVM aprobadas, además de 6 pruebas D-Day y 5 de
Passport diario. Verificador de alcance y compatibilidad de texto aprobado.
Backend reiniciado para servir el nuevo catálogo, health `ok`. El teléfono
estaba bloqueado al final: revisión visual de los textos nuevos pendiente.
No se introdujeron puntuaciones, confirmaron experiencias ni iniciaron viajes
para esta entrega.
