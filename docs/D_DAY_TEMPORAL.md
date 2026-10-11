# D-Day: evaluación temporal de fase 4

Ampliación posterior al contexto meteorológico completo, sin UV:
[D_DAY_TEMPORAL_WEATHER.md](D_DAY_TEMPORAL_WEATHER.md). Esta comparación
con lluvia se conserva como control y registro reproducible.

El autor autorizó proseguir con simulaciones temporales mientras la app puede
recoger evidencia prospectiva. Se implementa un laboratorio offline; el frontend,
los endpoints de predicción de producción y las entradas de Passport/HOTCO
continúan como en 0.4.61. No se elige ni activa un ritmo de olvido.

## Pregunta y candidatos

¿Permitir que cambie la expectativa entre viajes mejora predicciones cuando
la relación cambia, sin deteriorar demasiado relaciones estables o confundir
meteorología diferente con cambio personal?

Likelihood ordinal de siete categorías, puntos de corte existentes y valores
originales: comodidad 1…7 con `(r−4)/3`, afecto −3…+3 con `r/3`. Se aprende una
ubicación latente `a(t)` y, en la variante contextual, un coeficiente de lluvia
fijo `b`: `eta(t)=a(t)+b*log(1+lluvia)`.

Candidatos registrados antes de generar resultados:

| Candidato | Supuesto |
|---|---|
| Passport estático | Referencia fija inicial, sin aprendizaje |
| Acumulativo | `a` constante; todas las experiencias informan los mismos parámetros |
| Dinámico .15 | `a(t+dt)=a(t)+N(0, .15²*dt)` |
| Dinámico .4 | Igual, con varianza `.4²*dt` |
| Promedio bayesiano | Prior discreto equiprobable sobre q=0/.15/.4; pesos actualizados con evidencia anterior |

`dt` se mide en días, por lo que q expresa desviación de ubicación latente
por raíz de día, no porcentaje de olvido ni número de respuestas. Los valores
.15/.4 son candidatos propios de sensibilidad, no tasas extraídas de estudios
de movilidad o parámetros psicológicos validados. Los datos antiguos no se
borran; influyen a través de la distribución del estado y los parámetros fijos.

La estructura sigue los modelos de estado y filtrado bayesiano tratados por
[Särkkä, Bayesian Filtering and Smoothing (2013), capítulos 3–4](https://users.aalto.fi/~ssarkka/pub/cup_book_online_20131111.pdf).
No usamos un filtro Kalman gaussiano como si la respuesta ordinal fuese continua:
se integra en una rejilla una likelihood ordinal no gaussiana.

El promedio de modelos se evalúa como otro candidato. Sus pesos no prueban un
cambio psicológico y son sensibles a la lista de modelos y priors; el caso
de una realidad fuera de esa lista requiere especial cautela, discutida por
[Yao et al., Using stacking to average Bayesian predictive distributions](https://sites.stat.columbia.edu/gelman/research/published/stacking.pdf).
No implementamos stacking ni presentamos BMA como solución universal.

## Diseño congelado

Ocho escenarios: estabilidad con referencia adecuada, estabilidad con prior
equivocado, cambio brusco, cambio gradual, cambio solo de frecuencia de lluvia,
tres experiencias desfavorables transitorias, calendario irregular con pausa
y cambio del coeficiente de lluvia. Cuatro modos y dos objetivos separados,
tres repeticiones independientes por combinación: 192 secuencias de 72 viajes.

Cada secuencia se evalúa con priors moderado/fuerte, diseño básico/solo lluvia
y los cinco modelos: 230 400 predicciones puntuadas desde el viaje 13. Los
primeros 12 viajes sí se usan para aprendizaje; se excluyen de las puntuaciones.
Todos predicen antes de observar la respuesta y reciben exactamente los mismos
datos. La comparación antes/después del viaje 36 es un corte descriptivo común:
en los escenarios estables ese corte no implica que exista un cambio real.

Los escenarios son pruebas de estrés y no se generan todos desde los modelos
estimadores: incluyen saltos, cambios de pendiente y episodios pasajeros. La
verdad generadora está archivada separada de las declaraciones iniciales.
Las declaraciones iniciales usan categorías exactas: comodidad 5 o 6 para
walk/pt, afecto +1 o +2 para los cuatro modos, normalizadas a 1/3 o 2/3.
Para comodidad bike/car se simula una referencia estimada con procedencia
Ridge y prior 1.5 veces más ancho, como en el piloto existente; no se inventa
una respuesta inicial observada ni se vuelve a entrenar Ridge. El valor crudo
inicial queda nulo en esas dos relaciones.
Se simula selección equilibrada de modos, no elecciones humanas ni efectos
de recomendaciones. Comodidad y afecto tienen sus propias respuestas/procesos;
el episodio transitorio es más negativo para afecto, no se deriva de comodidad.

La reducción a lluvia permite comparar modelos con los mismos predictores.
No representa las ocho variables del modelo de producción ni justifica
eliminarlas de la app. Solo cambia el intercepto: el escenario de pendiente
variable evalúa una limitación del candidato, no implementa esa pendiente dinámica.

## Métricas y comprobaciones

Log-score y ranked probability score sobre respuestas futuras, por objetivo,
escenario, modo y fase temporal; error de expectativa e intervalos contra la
verdad sintética conocida. Cobertura latente no se calcula comparando el intervalo
de expectativa con una respuesta individual. La referencia fija no tiene un
intervalo epistémico del 90% y se excluye de esa comparación.

La unidad de replicación es la secuencia, no cada viaje. Los tres replicados
por modo/escenario siguen siendo exploratorios; no se selecciona una tasa ni
regla de activación buscando resultados favorables.

La integración usa una rejilla con a entre −12 y 12 (241 valores) y b entre
−3 y 3 (41 valores). Se aplica una frontera reflectante numérica al movimiento
gaussiano; se audita masa en bordes y sensibilidad a 481×81 valores. No es
una inferencia exacta sobre una caminata sin límites. Se contrasta el acumulativo
básico con integración independiente y posterior MCMC corregido del núcleo
actual. Se incluyen 128 secuencias de calibración generadas desde el prior y
modelo candidato (64 por diseño contextual), con intervalos binomiales.
Estas pruebas numéricas no validan el constructo psicológico.

## Reproducción

Desde `app-backend/services/dyconet`:

```text
python -m comfort_pilot.temporal_experiment --output ../../../output/d-day-temporal --repetitions 3
python -m comfort_pilot.temporal_calibration --output ../../../output/d-day-temporal
python -m comfort_pilot.temporal_report --output ../../../output/d-day-temporal
```

Artefactos: diseño previo, respuestas crudas y normalizadas, tiempos, verdad
generadora, predicciones, resumen, sensibilidad/calibración, reporte y gráfico
en `output/d-day-temporal`. Resultados interpretados y comprobación final en
[el reporte](../output/d-day-temporal/REPORT.md).

## Resultados de la primera evaluación

La ejecución completó 192 secuencias, 13 824 experiencias sintéticas y
230 400 predicciones puntuadas. Se superaron 46 pruebas backend y ocho
contrastes independientes con MCMC. Las predicciones se auditaron para
comprobar que solo utilizan respuestas anteriores.

| Situación | Hallazgo exploratorio |
|---|---|
| Relación estable | El acumulativo predice ligeramente mejor que los candidatos temporales. |
| Cambio abrupto o gradual | Los candidatos temporales mejoran las predicciones de comodidad y afecto. |
| Cambia la frecuencia de lluvia, no la relación | No aparece una ventaja de adaptar el intercepto. |
| Episodio negativo pasajero | La adaptación rápida puede perjudicar las predicciones posteriores. |
| Cambia la reacción específica a la lluvia | Adaptar solo el intercepto no resuelve el problema. |

Con un prior fuerte equivocado, un candidato temporal puede mejorar aunque
la relación real sea estable. Por eso sus pesos no demuestran cambio
psicológico. El promedio bayesiano ofrece un compromiso, pero no gana en
todos los escenarios.

La calibración generativa obtuvo cobertura de 57/64 secuencias para el
modelo básico y 60/64 para el contextual; ambos intervalos binomiales del
95% contienen el 90% nominal. Es una comprobación bajo el modelo generador,
no una validación con personas. La sensibilidad a una rejilla más fina
se evaluó en 16 secuencias representativas con difusión .15.

Antes de activar algo en fases 5–6, debe ampliarse la arquitectura al contexto
completo, evaluarse con evidencia real y definir precisión y reglas de uso.
