# D-Day — decisiones para preparar la fase 4

Decisiones del autor recibidas tras la entrega de fases 1–3. Este documento
registra el protocolo en preparación; no activa aprendizaje en la app ni amplía
la entrega a las fases 5–6.

## Decisiones aceptadas

| Aspecto | Decisión |
|---|---|
| Constructo | Confort físico percibido al viajar |
| Creencia | Expectativa contextual de la relación modo–comodidad, comparada con un modelo básico sin contexto |
| Medición posterior | Evaluación de la contribución del modo a satisfacer comodidad; atribución al modo o tramo realmente identificado |
| Frecuencia | Una pregunta obligatoria de comodidad después de cada viaje realizado en el piloto remunerado, sin repetir el cuestionario inicial completo |
| Passport inicial | Conservar procedencia declarada o estimada por Ridge; experimentar con distintas fuerzas y centros del prior |
| Priors de literatura | Revisar evidencia pertinente para justificar candidatos; documentar población, contexto, medición, efecto e incertidumbre antes de trasladarlos al modelo |
| Contexto | Modo, duración prevista y todas las variables meteorológicas disponibles y válidas, excepto UV |
| Personalización | Comenzar con aprendizaje individual; estudiar incorporación de datos de población cuando exista una base pertinente |
| Objetivo inicial | Evaluar predicción de experiencias futuras; no afirmar todavía que el posterior reproduce cambios psicológicos del individuo |
| Actualización | El prototipo acumula y aprende desde la primera experiencia; sigue separado del Passport activo |
| Activación futura | Dependerá de precisión, fiabilidad y evaluación definidas mediante simulaciones; no de un mínimo arbitrario de tres respuestas |
| Tiempo | Aprendizaje acumulativo inicial; cambios de relación como pruebas de estrés, sin olvido automático implementado |

## Escala acordada: bipolar con lenguaje de satisfacción

El autor eligió explícitamente la primera opción: ayuda–dificulta satisfacer
comodidad física. La decisión semántica está cerrada; no utilizar una escala
unipolar nada–mucho como si fuese equivalente.

Pregunta posterior propuesta para comprobar comprensión en el piloto:

> Durante este trayecto, ¿cuánto ayudó o dificultó viajar en [modo] satisfacer
> tu necesidad de comodidad física?

Para una evaluación parcial, sustituir «trayecto» por «tramo» e identificarlo.
Los anclajes del ítem de experiencia son:

| Respuesta ordinal | Anclaje |
|---|---|
| 1 | Dificultó mucho satisfacer mi comodidad física |
| 2 | Dificultó moderadamente satisfacer mi comodidad física |
| 3 | Dificultó un poco satisfacer mi comodidad física |
| 4 | Ni ayudó ni dificultó satisfacer mi comodidad física |
| 5 | Ayudó un poco a satisfacer mi comodidad física |
| 6 | Ayudó moderadamente a satisfacer mi comodidad física |
| 7 | Ayudó mucho a satisfacer mi comodidad física |

Conservar el valor ordinal original. La codificación firmada operativa mantiene
`(r−4)/3`: 1 → −1, 4 → 0, 7 → +1. Esta codificación no demuestra distancias
psicológicas iguales entre categorías; la likelihood candidata sigue siendo
ordinal. El valor 4 significa relación neutral, no desconocimiento.

La redacción y sus traducciones deben comprobarse con usuarios y conservar
versión propia antes de desplegarse. No reetiquetar respuestas históricas de
satisfacción como si se hubiesen recogido con este nuevo ítem bipolar.
Esta definición no modifica todavía el frontend, los registros ni el motor.

## Atribución, obligatoriedad y evidencia

- La pregunta obligatoria corresponde a viajes realizados. Un viaje no tomado
  no aporta una evaluación de comodidad; un viaje parcial permite evaluar solo
  un modo/tramo que sí se realizó.
- Para viajes multimodales, una respuesta sobre un tramo no se propaga al resto
  de modos. Debe identificarse el modo/tramo evaluado y registrar cómo se eligió.
  El protocolo aún debe fijar esa selección manteniendo la carga acordada de
  una pregunta por viaje, así como excepciones de imposibilidad de evaluar.
- Remuneración no convierte respuestas en verdad objetiva ni determina su
  calidad. La recogida conserva valores crudos, fechas, versión de pregunta y
  fuente; una respuesta duplicada nunca cuenta como nueva experiencia.

## Meteorología

El autor establece como supuesto operativo del piloto que Orion siempre
aportará condiciones contextuales mediante sensores. El diseño principal
asumirá contexto disponible; no centrar la siguiente discusión en escenarios
de ausencia de contexto. Esta afirmación es un supuesto del protocolo, no una
garantía de disponibilidad auditada ni una orden de eliminar validaciones.
Conservar procedencia, unidades, tiempos y cobertura para interpretar los datos.
Separar disponibilidad de sensores de cobertura del aprendizaje individual:
puede haber condiciones medidas que el usuario todavía no haya experimentado.
La política ante contextos nuevos y el tratamiento temporal siguen pendientes.

La selección de variables cubre todas las condiciones disponibles excepto UV.
El contrato Weather actual de Orion incluye temperatura, humedad, lluvia,
velocidad de viento, ráfagas, velocidad de viento de diez minutos, intensidad
lumínica y timestamp. La disponibilidad real debe auditarse por fuente y fecha.
No declarar presión, nubosidad u otras variables como observadas si el proveedor
no las aporta. Campos ausentes permanecen desconocidos.

Conservar unidades, origen, ubicación/cobertura espacial, hora de observación,
hora de consulta y validez. Meteorología al buscar describe ese momento, no
demuestra por sí sola condiciones futuras ni exposición personal durante todo
el trayecto. Para predicción se utilizarán solo datos disponibles entonces.

Cubrir todas las variables no implica introducirlas como coeficientes
independientes sin evaluación. Comprobar redundancia/colinealidad, cobertura y
sensibilidad de priors. Las simulaciones también incluirán falta de datos y
contextos fuera de la cobertura conocida. F3 sigue descartado.

## Literatura y simulaciones

Primero recopilar evidencia sobre confort físico percibido y contexto por modo,
evaluando su correspondencia con el ítem elegido. No asignar automáticamente
coeficientes numéricos a conclusiones cualitativas de otro estudio. Separar
prior de análisis y supuestos utilizados para generar escenarios.

Evaluar candidatos débiles, moderados y fuertes, así como priors equivocados.
Usar ocho perfiles difíciles y cantidades 1, 3, 5, 10, 20 y 40 como diseños de
simulación, no umbrales certificados. La verdad generadora será independiente
del Passport inicial y también diferirá de los supuestos del estimador en
pruebas de robustez. Conservar semillas, modelos, parámetros y evidencia usada.

La validación abarcará calibración computacional, comprobaciones predictivas,
sensibilidad de priors y comparación temporal con Passport estático y una
actualización simple. La precisión requerida y los criterios de activación
deben fijarse antes de escoger resultados favorables; sus valores numéricos
todavía no están acordados.

## Decisión posterior: evaluación separada de explicación y recomendación

El autor confirmó mantener el enfoque bayesiano y dejar fuera de esta entrega
reinforcement learning y deep learning. Aceptó evaluar por separado las
consecuencias de incertidumbre/error en comodidad sobre:

- **Explicación:** fidelidad a la evidencia y al cálculo, atribución al modo y
  contexto pertinentes, y lenguaje acorde con la incertidumbre. No presentar
  una expectativa incierta como una conclusión firme ni como un efecto causal.
- **Recomendación:** sensibilidad del resultado HOTCO a las estimaciones
  plausibles de comodidad, manteniendo las demás entradas constantes dentro
  de cada comparación. Registrar alternativas ganadoras y sus activaciones;
  estabilidad de la recomendación no equivale a precisión de la creencia ni a
  acierto empírico. El acierto predictivo requiere experiencias posteriores.

Esta aceptación fija dos ejes de evaluación; no fija umbrales numéricos, no
autoriza todavía fases 5–6 y no resuelve las políticas de contexto insuficiente
ni cambio temporal/olvido. Esas decisiones siguen pendientes de discusión.
Las metas diarias y valencias continúan separadas de las creencias aprendidas;
F3 y la copia automática de estados terminales permanecen descartados.

## Ampliación del diseño temporal: experiencia afectiva

El autor señala que experiencias afectivas reiteradamente negativas bajo
condiciones como lluvia deben formar parte del análisis longitudinal: HOTCO
incluye emociones, además de necesidades. Conservar la asociación entre modo,
contexto, comodidad y experiencia afectiva de cada viaje, sin reducir afecto a
satisfacción de comodidad ni inferir importancia de metas desde una emoción.

El siguiente diseño debe considerar dos objetivos distintos: expectativa de
contribución a comodidad física y expectativa afectiva contextual por modo.
Se deben preservar la valencia original declarada, las respuestas posteriores
crudas y las estimaciones afectivas aprendidas con incertidumbre/tiempo. Dos
mediciones en un mismo viaje pueden estar relacionadas; separar sus resultados
no implica independencia estadística ni contar la misma respuesta dos veces.
No concluir causalidad de lluvia ni desagrado general por un modo a partir de
una asociación contextual. Evaluar cambios persistentes frente a diferencias
de contexto; no copiar estados emocionales terminales simulados como feedback.

Decisión posterior e implementación 0.4.60: Experience exige afecto ordinal
−3…+3 por cada modo utilizado y conserva el crudo junto a `r/3`, igual que el
Passport inicial. El piloto aprende afecto por individuo/modo mediante una
likelihood ordinal de siete categorías, separado del modelo de comodidad.
Utiliza la valencia inicial como referencia del prior y compara modelo básico
y contextual; los resultados permanecen aislados del Passport activo y HOTCO.
Los priors son candidatos de sensibilidad, no parámetros psicológicos validados.
No transferir el prior meteorológico derivado de literatura de comodidad al
afecto. Detalles: [D_DAY_AFFECT_DELIVERY.md](D_DAY_AFFECT_DELIVERY.md).
El aprendizaje temporal no está activado en producción. Se implementó una
comparación experimental offline con modelos acumulativos y temporales,
conservando todo el historial: [D_DAY_TEMPORAL.md](D_DAY_TEMPORAL.md).
Los resultados no fijan un ritmo de olvido ni modifican el Passport activo.

## Estado de la implementación de la fase 4

El autor autorizó explícitamente implementar el modelo piloto y la fase 4.
La entrega 0.4.59 mantiene el aprendizaje aislado del Passport activo/HOTCO.
Detalles, decisiones numéricas candidatas, referencias, simulaciones y pruebas:
[D_DAY_PHASE4_DELIVERY.md](D_DAY_PHASE4_DELIVERY.md).
Los umbrales de activación siguen sin validación ni aplicación automática.

La entrega prospectiva 0.4.61 automatiza predicciones y conserva las escalas
y modelos candidatos existentes. Su plan de análisis está fijado antes de
recoger respuestas nuevas: comparar distribuciones predictivas de Passport
inicial/básico/contextual mediante log-score y ranked probability score,
por objetivo/modo y en los mismos eventos elegibles. El error absoluto de
respuesta codificada es complementario; no equivale a error de la expectativa
latente, que no se observa en un solo viaje. Conservar también contexto,
cobertura, diagnósticos, exclusiones y orden temporal. No seleccionar modelos
ni umbrales a partir de resultados favorables. La comprensión del instrumento,
calibración con suficientes observaciones y reglas de precisión/activación
requieren evaluación posterior. Detalles: [D_DAY_PROSPECTIVE.md](D_DAY_PROSPECTIVE.md).

## Hipótesis futuras, fuera del objetivo inicial

El autor propone estudiar si una creencia firmada se acerca a cero o cambia de
signo, y si esto coincide con cambios de selección de modo. Conservarlo como
hipótesis para una evaluación posterior. Un cambio de estimación puntual no
demuestra por sí solo un cambio psicológico ni que el aprendizaje causó la
elección: examinar incertidumbre, contexto, metas, valencias, disponibilidad y
exposición a recomendaciones. No activar esta hipótesis en la fase 4.
