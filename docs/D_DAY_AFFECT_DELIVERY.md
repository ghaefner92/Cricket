# D-Day: aprendizaje afectivo de fase 4 — 0.4.60

El autor pidió aprendizaje bayesiano afectivo y medición obligatoria −3…+3,
normalizada igual que el Passport inicial. Implementado como laboratorio de
fase 4; las fases 5–6, actualización activa e integración con HOTCO, siguen
pendientes. No incorpora F3, olvido ni estados terminales como evidencia.

## Medición y trazabilidad

Experience exige comodidad física y una respuesta emocional explícita para
cada modo utilizado. Una respuesta `0` es neutral; una omitida no es neutral.
Si el viaje no se realizó no se pide ni aprende emoción. Guarda:

- `mode_valences_raw_minus3_to_3`: respuestas ordinales originales.
- `mode_valences_normalized_minus1_to_1`: cada respuesta dividida por tres.
- `affect_scale_version`: `mode-affect-ordinal-minus3-to3-v1`.

La UI y los contratos Android/backend impiden confirmar respuestas incompletas
en registros nuevos. Los respaldos históricos continúan siendo legibles; no
se inventan respuestas ni se reescriben mediciones sin versión. El adaptador
de análisis excluye esas observaciones antiguas e informa el motivo.

## Modelo y alcance

Modelo ordinal bayesiano de siete categorías por individuo/modo. Internamente
`raw+4` asigna las categorías 1…7; la codificación de salida es exactamente
`raw/3`. El modelo aprende probabilidades por categoría y entrega esperanza
afectiva normalizada con intervalo epistémico del 90%. La esperanza de esta
codificación es una decisión analítica; no demuestra distancias psicológicas
iguales entre categorías ni garantiza el valor de la siguiente respuesta.

El prior se centra usando la valencia original declarada, no comodidad ni
creencias imputadas con Ridge. Se conserva el mapeo finito de extremos ±1 a
±0.98 para la ubicación latente. Se comparan versiones básica y contextual,
con la misma arquitectura ordinal y muestreo de posterior completo que el
piloto de comodidad. Priors weak/moderate/strong y puntos de corte siguen
siendo supuestos para sensibilidad. Se rechaza `literature_shape=true` para
afecto: los priors cualitativos de comodidad no constituyen evidencia afectiva.

Predictores: duración prevista y siete variables meteorológicas, sin UV,
con calidad, unidades, timestamps y procedencia. Los datos del contexto se
congelan al seleccionar la ruta; no representan automáticamente exposición
GPS de todo el viaje. Para modos cambiados se informa contexto desconocido
si no puede atribuirse la ruta prevista. Sensores disponibles no garantizan
cobertura del historial individual para esas condiciones.

Se excluyen experiencias posteriores al instante de predicción; se rechazan
duplicados por viaje/modo y evento/modo. Dos modos de un mismo viaje pueden
aportar una respuesta cada uno a sus modelos separados. Afecto y comodidad
permanecen asociados al viaje y separados como objetivos; esta implementación
no estima su covarianza conjunta ni supone independencia psicológica.

La actualización es acumulativa, sin umbral arbitrario de tres viajes. Siempre
informa incertidumbre y diagnósticos; todavía no hay regla de precisión para
activar resultados. Una asociación emoción–lluvia no demuestra causalidad.
El cambio temporal permanece pendiente de una decisión metodológica.

## Uso reproducible

Endpoint de solo análisis:
`POST /api/dyconet/d-day/affect-pilot/analyze`.
Schema: `d-day-affect-pilot-request-v1`; resultado:
`d-day-affect-pilot-result-v1`; modelo `d-day-affect-ordinal-v1`.

Desde `app-backend/services/dyconet`:

```text
python -m comfort_pilot.affect request.json --output result.json
python -m comfort_pilot.affect backup.json --backup --contexts contexts.json --output result.json
python -m comfort_pilot.affect_experiment --output ../../../output/d-day-affect
```

Ejemplos y simulaciones están en `output/d-day-affect`. El endpoint no almacena
datos ni modifica el Passport o HOTCO. El análisis experimental requiere la
solicitud/API o CLI; no se ejecuta automáticamente después de confirmar un
viaje. La app registra y exporta la evidencia para ello.

## Comprobación

Ocho perfiles sintéticos con expectativas acertadas/equivocadas, lluvia,
ruido, contexto nuevo y cambio temporal; cuatro modos, tres priors, tamaños
3 y 40, dos modelos: 384 ajustes. Los 384 pasaron los diagnósticos de muestreo.
Se conserva el diseño y las respuestas crudas/normalizadas, resultados y
errores predictivos. Solo una repetición por escenario: prueba exploratoria,
no comparación concluyente ni garantía de suficiencia de muestra.

Ejemplo exploratorio con 40 experiencias y prior moderado: para bicicleta en
el escenario sensible a lluvia, el contextual estima +0.405 en seco y −0.165
con lluvia; el básico estima +0.037 en ambos contextos. El error de expectativa
normalizada es 0.293 frente a 0.562. Sin embargo, el intervalo nominal del 90%
cubre la expectativa verdadera solo en el 58% de los 24 contextos de prueba
de ese escenario: convergencia del sampler no garantiza precisión/calibración
predictiva. Esta limitación respalda mantenerlo en fase 4 sin activación.

Los escenarios de cambio temporal evalúan una limitación del modelo acumulativo;
no implementan ni validan adaptación temporal. No reinterpretar las antiguas
simulaciones de comodidad como resultados afectivos.

Verificación local y con dispositivo se registra en
`output/d-day-affect/verification.json`. La validez del constructo afectivo,
la comparabilidad declaración inicial/experiencia, las reglas de precisión y
el rendimiento con observaciones reales requieren evaluación posterior.
