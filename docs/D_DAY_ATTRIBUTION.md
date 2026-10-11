# D-Day: separar cambios personales y meteorológicos

## Entrega autorizada

El autor autorizó los puntos 1–2 de la siguiente comparación: desacoplar clima
y tiempo, y estudiar expectativa general dinámica, reacción a lluvia dinámica
y ambas. Esta entrega conserva contexto meteorológico completo sin UV,
comodidad y afecto independientes, historia completa y escalas originales.
Los cambios de UI quedan para el final. No activa posteriores, Passport ni
conexiones HOTCO; la app y sus endpoints de producción permanecen iguales.

## Diseño previo a resultados

Seis situaciones: estable, cambia únicamente el clima, cambia únicamente el
intercepto, cambia únicamente el coeficiente de lluvia, cambian ambos y episodio
negativo pasajero. Dos diseños meteorológicos: lluvia independiente de las otras
variables meteorológicas, y lluvia asociada a temperatura/humedad/viento/luz.
En ambos, no hay una tendencia suave de temperatura con el tiempo. El segundo
diseño conserva correlaciones que dificultan identificar efectos específicos;
el primero es un control experimental, no una descripción de clima natural.

Todas las variables de `model.design_row` se conservan: duración, siete medidas
meteorológicas sin UV, temperatura cuadrática e indicadores de ausencia. Las
medidas están completas. Un cambio de clima modifica la probabilidad de lluvia
(.2 → .8) y las condiciones asociadas en el diseño correlacionado; no cambia
los parámetros generadores. Los demás escenarios tienen clima estacionario.

Cuatro candidatos reciben los mismos viajes:

| Candidato | Intercepto | Coeficiente de lluvia |
|---|---|---|
| Acumulativo | Fijo | Fijo |
| General dinámico | Difusión .15 | Fijo |
| Lluvia dinámica | Fijo | Difusión .08 |
| Ambos dinámicos | Difusión .15 | Difusión .08 |

Otros coeficientes permanecen estáticos pero inciertos.
"Fijo" describe la relación generadora asumida por el modelo, no un posterior
congelado: todos los candidatos actualizan su conocimiento con cada respuesta.
Las difusiones son desviaciones latentes por raíz de día: candidatos no validados, no tasas de
olvido ni umbrales de activación. El prior principal es moderado; ampliar priors
y tasas corresponde al siguiente punto del plan y no queda resuelto aquí.

192 secuencias: seis situaciones × dos diseños × dos objetivos × cuatro modos
× dos replicados, con 72 viajes por secuencia y puntuación desde el viaje 13.
Los dos objetivos comparten contexto de viaje: son 6912 instancias de viaje
sintético emparejadas con 13824 respuestas, no 13824 viajes independientes.
Cambios persistentes comienzan en el viaje 37. Los contextos usan las mismas
innovaciones en situaciones/objetivos emparejados, con RNG de respuestas
separado. Son **16 bloques meteorológicos independientes**, no 192 perfiles
independientes. Cada contraste por situación/objetivo/diseño tiene ocho
secuencias modo/replicado; los resultados por modo tienen solo dos replicados.
No agrupar viajes ni escenarios emparejados como observaciones independientes.

La expectativa inicial normalizada es 1/3: comodidad 5 para walk/pt, afecto +1
para los cuatro modos. Comodidad bike/car conserva procedencia estimada Ridge,
crudo inicial nulo y prior 1.5 veces más ancho; no se vuelve a entrenar Ridge.
Las respuestas nuevas son comodidad 1–7 y afecto −3…+3, conservando codificación.

## Inferencia y atribución

`AttributionFilter` extiende el filtro de partículas previo en un módulo
separado, preservando su implementación y artefactos anteriores. Coeficiente
de lluvia = coeficiente inicial incierto + trayectoria gaussiana iniciada en
cero. Esta trayectoria y el intercepto tienen incrementos independientes según
días reales. Remuestreo y movimientos pCN incluyen la trayectoria de lluvia;
cada aceptación MH usa likelihood de **todo** el historial. No se eliminan
experiencias, ni se copian estados simulados como feedback humano.

Se archivan probabilidades y puntuaciones antes de revelar la respuesta.
Además del log-score/RPS y cobertura/error de expectativa, se mide error y
cobertura del intercepto y coeficiente de lluvia conocidos sintéticamente.
Una prueba controlada contrasta lluvia 0/2 con los demás predictores codificados
en cero. Este contraste ordinal es no lineal: cambia también con el intercepto;
por eso se mide el coeficiente separadamente. No es una estimación causal de
personas y puede estar fuera del soporte del clima correlacionado.

El contraste con lluvia difusa cero reproduce el filtro anterior. Una rejilla
bidimensional independiente integra intercepto/lluvia con otros predictores
nulos; su frontera reflectante exige auditar sensibilidad y masa en bordes.
Los intervalos de expectativa se comparan con verdad latente, no respuestas
individuales. Buena predicción no demuestra atribución correcta ni validación
psicológica. Con lluvia y otros factores correlacionados puede faltar evidencia
para separar sus efectos incluso cuando todas las medidas están disponibles.

## Reproducción

Desde `app-backend/services/dyconet`:

```text
python -m comfort_pilot.attribution_experiment --output ../../../output/d-day-attribution --repetitions 2 --particles 4096
python -m comfort_pilot.attribution_audit --output ../../../output/d-day-attribution
python -m comfort_pilot.attribution_report --output ../../../output/d-day-attribution
```

Se conservan diseño, respuestas, contextos, parámetros generadores, predicciones
y puntuaciones por secuencia en `output/d-day-attribution`. La interpretación
debe considerar costes en relaciones estables y episodios pasajeros, modos con
efectos débiles, incertidumbre y sensibilidad numérica. No seleccionar un modelo
universal ni una regla de activación con estas simulaciones exploratorias.

## Comprobación numérica de esta entrega

Pasaron 54 pruebas backend, incluyendo equivalencia con el filtro previo cuando
la difusión de lluvia es cero y contraste independiente bidimensional. La
auditoría obtuvo diferencia máxima de expectativa .00762 frente a rejilla fina;
la diferencia entre resoluciones de rejilla fue 1.24e−9, con masa en bordes
aproximadamente 6e−20. Esto comprueba el caso reducido, no todas las condiciones.

En meteorología completa, cambiar 4096/16384 partículas y semillas produjo
diferencias máximas de .03309 en expectativa, .06534 en coeficiente de lluvia
y .00521 en log-score medio de secuencias auditadas. No interpretar diferencias
predictivas muy pequeñas como ventajas robustas de un candidato.

La calibración generativa incluyó 64 secuencias de 24 viajes: cobertura de
expectativa 58/64 (90.6%), intercepto 60/64 (93.8%) y coeficiente de lluvia 57/64
(89.1%). Los respectivos intervalos binomiales del 95% contienen el 90% nominal.
La cobertura usa el último pronóstico antes de la respuesta por secuencia,
evitando tratar viajes del mismo individuo sintético como replicados independientes.
Estas comprobaciones no garantizan cobertura ante cambios abruptos ni validación
psicológica; los resultados de escenarios deben evaluarse separadamente.

## Resultados de los escenarios

Se completaron las 192 secuencias emparejadas de 16 bloques meteorológicos,
6912 instancias de viaje con 13824 respuestas y 46080 predicciones puntuadas.
Se verificaron códigos ordinales, contextos emparejados, probabilidades e
historia previa al pronóstico. Todos los candidatos reciben la respuesta
después de archivarse los pronósticos de ese viaje.

| Situación | Hallazgo exploratorio |
|---|---|
| Relación estable o cambia solo el clima | El acumulativo obtiene mejor log-score agregado en ambos objetivos. |
| Cambia solo el estado general | Adaptarlo mejora claramente la predicción y reduce su error de atribución. |
| Cambia solo la reacción a lluvia | Variar el coeficiente reduce su error frente al acumulativo en los cuatro cruces diseño/objetivo. |
| Cambian ambos componentes | El candidato con ambos dinámicos mejora la predicción, pero no identifica siempre mejor cada componente. |
| Episodio pasajero | No hay ventaja predictiva robusta universal de adaptación; en afecto las diferencias agregadas son pequeñas. |

Ejemplo que separa predicción y explicación: en afecto con clima correlacionado,
cuando cambia únicamente la lluvia, el general dinámico mejora log-score
−1.865 → −1.843 pero aumenta RMSE general .266 → .584 y no mejora el error del
coeficiente de lluvia (aproximadamente 1.05). Lluvia dinámica mantiene mejor el
estado general (RMSE .273) y reduce RMSE de lluvia a .793. Ambos dinámicos dan
log-score parecido a lluvia dinámica (−1.822), con mayor error general (.543).
No seleccionar por puntuación predictiva únicamente.

La incertidumbre **no queda resuelta**: con ambos dinámicos y cambios en ambos
componentes, la cobertura de expectativa es 76.7% en comodidad y 73.8% en afecto,
por debajo del 90% nominal. Cuando cambia solo lluvia, ese mismo candidato
cubre aproximadamente 90.1% de expectativas afectivas pero solo 63.4% de sus
coeficientes de lluvia. Cobertura predictiva latente aceptable no garantiza
incertidumbre correcta sobre la explicación.

Siguiente paso: sensibilidad a priors y tasas (punto 3), comprobar cobertura
ante distintos ritmos/saltos y medir costes en relaciones estables antes de
considerar una regla de uso. Después se requiere evaluación prospectiva real.
No activar estos posteriores en Passport/HOTCO ni presentar una tasa universal.

[Reporte completo](../output/d-day-attribution/REPORT.md),
[predicción](../output/d-day-attribution/comparison.png) y
[atribución](../output/d-day-attribution/attribution.png). UI permanece para el final.
