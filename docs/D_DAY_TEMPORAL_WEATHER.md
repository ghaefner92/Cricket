# D-Day: comparación temporal con meteorología completa

## Alcance autorizado

El autor pidió ampliar la comparación al contexto meteorológico completo sin
UV, dejando los cambios de UI para el final. Esta entrega es offline y mantiene
el aprendizaje aislado del Passport activo y HOTCO. No modifica endpoints,
cuestionarios, predicciones de producción ni las escalas de respuesta.

## Diseño

Se reutiliza `model.design_row` del piloto: duración, temperatura, humedad,
lluvia, velocidad del viento, ráfagas, viento a diez minutos e intensidad
luminosa; término cuadrático de temperatura e indicadores de medición ausente.
La comparación principal genera todas las mediciones, con unidades y
transformaciones iguales a las del modelo existente. Los indicadores se
conservan por compatibilidad; no se simula ausencia como experiencia neutral.
UV no es predictor ni se genera como evidencia.

128 secuencias: ocho escenarios × comodidad/afecto × cuatro modos × dos
replicados. Cada secuencia tiene 72 viajes; se puntúan los viajes 13–72.
La unidad de replicación es la secuencia. Se conserva la respuesta cruda,
normalizada, contexto, fecha sintética y verdad generadora. Las declaraciones
iniciales son categorías exactas; comodidad bike/car conserva procedencia
Ridge y prior más ancho, sin inventar una declaración observada.

Los siete candidatos reciben exactamente los mismos viajes:

- Passport inicial fijo.
- Acumulativo básico sin contexto.
- Acumulativo con lluvia sola.
- Acumulativo con meteorología completa.
- Meteorología completa con intercepto dinámico, difusión .15.
- Meteorología completa con intercepto dinámico, difusión .4.
- Promedio bayesiano de los tres candidatos meteorológicos completos.

Las difusiones son desviación latente por raíz de día, no porcentajes de olvido.
El prior principal es moderado, con los mismos centros, escalas y procedencias
que el piloto. Esta ampliación no repite todavía la sensibilidad completa a
priors fuertes/débiles. Los coeficientes meteorológicos permanecen estáticos;
no se presenta la variación del intercepto como solución a un cambio de
reacción específica a la lluvia.

Los escenarios incluyen relación estable, prior inicial equivocado, cambio
abrupto, cambio gradual, cambio de frecuencia meteorológica con relación
constante, episodio negativo pasajero, intervalos de calendario desiguales y
cambio de coeficiente de lluvia. Se generan viento/ráfagas/viento a diez
minutos correlacionados y covariación entre lluvia, humedad, luz y temperatura.
Son relaciones sintéticas plausibles para pruebas de estrés, sin calibración
empírica ni pretensión de representar un clima observado o un efecto causal.
Comodidad y afecto tienen procesos y respuestas separados.

## Inferencia y límites numéricos

La rejilla anterior no escala a 18 parámetros. Se implementa un filtro
resample–move de 4096 partículas, resampling sistemático cuando ESS < N/2,
y cuatro barridos de movimientos pCN de coeficientes y trayectoria completa.
Cada propuesta conserva su prior gaussiano y recibe una corrección
Metropolis–Hastings por la likelihood de **todo** el historial. El estado
dinámico se propaga con incrementos gaussianos según días transcurridos.
No se reemplaza el posterior por Ridge, MAP ni una aproximación gaussiana.

La base metodológica de remuestrear y rejuvenecer parámetros está en
[Chopin (2002)](https://academic.oup.com/biomet/article-abstract/89/3/539/251804).
Los [recursos de Arnaud Doucet](https://www.stats.ox.ac.uk/~doucet/smc_resources.html)
explican la relación entre resample–move, parámetros estáticos y degeneración.
Esta implementación particular necesita sus propias auditorías: finiteza de
partículas y pérdida de diversidad ancestral pueden afectar parámetros e
intervalos. ESS alto o aceptación MH no prueban convergencia.

Antes de incorporar cada respuesta se archivan/puntúan todos los pronósticos.
El promedio de modelos actualiza sus pesos después de esa puntuación usando
evidencia predictiva pasada; no es un detector causal de cambio psicológico.

La auditoría independiente compara acumulativo completo con cuatro cadenas
MCMC del núcleo existente; también compara 4096 y 16384 partículas con semillas
independientes, en cuatro escenarios y ambos objetivos, para difusión 0/.15.
La prueba de reducción a predictores nulos contrasta el filtro con la rejilla
escalar. Las diferencias se informan como sensibilidad numérica; no son
umbrales psicológicos de precisión ni autorizan activar un modelo.
Se audita también sensibilidad de log-score y se conservan estimaciones de
coeficientes del MCMC estático. La covariación entre temperatura y tiempo puede
permitir que el modelo atribuya a meteorología un cambio temporal: mejor
predicción no demuestra identificación correcta de esos efectos.

Además se generan 64 secuencias independientes desde el prior y el modelo
dinámico completo (.15), con 24 viajes por secuencia. Se comprueba cobertura
del intervalo latente del último pronóstico antes de la respuesta y se informa
un intervalo binomial. Esta comprobación bajo el propio modelo generador no
representa calibración con participantes ni prueba validez del constructo.

## Reproducción

Desde `app-backend/services/dyconet`:

```text
python -m comfort_pilot.temporal_weather_experiment --output ../../../output/d-day-temporal-weather --repetitions 2 --particles 4096
python -m comfort_pilot.temporal_weather_audit --output ../../../output/d-day-temporal-weather
python -m comfort_pilot.temporal_weather_calibration --output ../../../output/d-day-temporal-weather
python -m comfort_pilot.temporal_weather_report --output ../../../output/d-day-temporal-weather
```

Diseños previos, respuestas, predicciones, resumen y auditoría quedan en
`output/d-day-temporal-weather`. Las conclusiones deben considerar ambos
objetivos, escenarios estables y cambiantes, incertidumbre, error predictivo
y sensibilidad numérica, sin seleccionar una difusión universal.

## Resultado de esta entrega

Se completaron 128 secuencias, 9216 experiencias sintéticas y 53760
predicciones puntuadas. Pasaron 50 pruebas backend; las ocho referencias MCMC
cumplen sus diagnósticos. La máxima diferencia de expectativa posterior
SMC/MCMC fue .00534; la sensibilidad 4096/16384 partículas y semillas distintas
alcanzó .03087 en expectativa y .00348 en log-score medio por secuencia auditada.
La calibración generativa cubrió 56/64 expectativas (87.5%; intervalo binomial
95% aproximadamente 76.8–94.4%).

La meteorología completa mejora varios casos frente a lluvia sola, pero no
todos. Con los mismos predictores completos, la adaptación temporal mejora
cambios abruptos/graduales e intervalos desiguales; perjudica varios casos
estables/pasajeros. El promedio no gana siempre. Cuando cambia el coeficiente
de lluvia, variar solo el intercepto no mejora las puntuaciones respecto al
acumulativo completo en estas secuencias.

En cambios abruptos, la cobertura del promedio fue 78.5% para comodidad y
79.2% para afecto, por debajo del 90% nominal. La calibración bajo el propio
modelo no garantiza cobertura cuando la relación generadora cambia de otra
forma. Antes de activar posteriores: desacoplar clima/tiempo en nuevas pruebas,
estudiar cambios de coeficientes con regularización y sensibilidad de priors,
y contrastar predicción e incertidumbre con evidencia prospectiva real.

[Reporte y tablas completos](../output/d-day-temporal-weather/REPORT.md),
[gráfico](../output/d-day-temporal-weather/comparison.png). La app conserva su
modelo de producción; no se modificó UI, Passport activo ni HOTCO.
