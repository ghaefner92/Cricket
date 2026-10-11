# D-Day fase 4 — piloto de comodidad física

Entrega: 9 de octubre de 2026, aplicación Android **0.4.59**, código 70.
El piloto recoge evidencia nueva y estima un posterior individual en un
laboratorio separado. Las creencias activas, las metas, las valencias y las
conexiones de HOTCO no reciben actualizaciones bayesianas en esta fase.
El registro de Experience sigue creando versiones de evidencia enlazadas,
conservando el perfil cognitivo. Fases 5–6 pendientes.

## Qué cambia en la aplicación

Después de un viaje realizado, Experience requiere una respuesta sobre cuánto
el modo **ayudó o dificultó satisfacer comodidad física**. La escala ordinal
es 1–7: mucho/moderadamente/poco dificulta, neutral 4, poco/moderadamente/mucho
ayuda. No hay respuesta preseleccionada: neutral es una respuesta, no ausencia.
Los siete valores se conservan sin tratar sus distancias como psicométricamente
iguales. `(r−4)/3` es una codificación operativa para análisis y compatibilidad.

Un viaje declarado como no realizado no exige ni admite comodidad inventada.
Cuando se utilizaron varios modos, la persona identifica explícitamente uno;
la respuesta no se propaga a los otros. Cambiar de modo borra la respuesta
anterior. En esta entrega 0.4.59 las valoraciones de otras necesidades y afecto
eran opcionales. La extensión 0.4.60 hace obligatorio el afecto e incorpora su
aprendizaje separado: [D_DAY_AFFECT_DELIVERY.md](D_DAY_AFFECT_DELIVERY.md).
La comodidad se retira del menú unipolar opcional para evitar dos medidas
incompatibles del mismo objetivo. Se conservan fuentes SNES y el estilo existente.

El dato versionado `comfort_observation` incluye ítem, escala, valor ordinal,
modo real declarado, método de selección, idioma y fecha de respuesta. El
contrato exige que la respuesta ocurra entre el fin del viaje y su confirmación.
`comfort_pilot_required=true` marca búsquedas nuevas; los registros históricos
siguen siendo válidos. No se convierten retrospectivamente respuestas antiguas.
La identificación del modo es autoinformada, no una clasificación comprobada por GPS.

## Modelo implementado

Por persona y modo, sin préstamo de datos poblacionales:

`η = α_m + xᵀβ_m`

`P(Y=k | η) = logistic(c_k − η) − logistic(c_(k−1) − η)`

Hay siete categorías, límites exteriores infinitos y seis cortes fijos
`c_k = log(k/(7−k))`. En η=0, las siete categorías tienen igual probabilidad.
La fijación de cortes identifica la localización/escala del piloto; no demuestra
espaciado psicológico uniforme. La sensibilidad compara multiplicadores
0.75, 1 y 1.5, manteniendo la localización de referencia del prior principal.

| Componente | Implementación | Supuesto/límite |
|---|---|---|
| Modelo básico | Intercepto individual por modo | Expectativa constante sin covariables |
| Modelo contextual | Intercepto, ocho covariables, temperatura cuadrática y ocho indicadores de falta | 18 parámetros; con poca evidencia depende mucho del prior |
| Covariables | Duración planificada, temperatura, humedad, lluvia, viento, ráfagas, viento de 10 minutos, iluminancia | Se preservan unidades; UV excluido. Iluminancia no equivale a radiación térmica |
| Priors | Intercepto centrado mediante inversión de la expectativa codificada del Passport original; pendientes Gaussianas | Declaración inicial es una referencia aproximada para el nuevo instrumento, no una medición idéntica |
| Procedencia | Comfort walking/PT declarada; bike/car estimada por Ridge cuando no existe declaración original | Desviación ×1.5 para estimaciones es un candidato experimental |
| Fuerza del prior | Desviaciones 2.4, 1.2 y 0.4; principal moderado 1.2 | No son parámetros estimados por la literatura |
| Pendientes | Media cero, desviación 0.35; indicadores de falta 0.2 | Shrinkage, sin efectos meteorológicos impuestos por defecto |
| Variante cualitativa | Medias −0.15 para lluvia y temperatura cuadrática, desactivada por defecto | Sensibilidad candidata; no coeficientes publicados |
| Inferencia | Cuatro cadenas Metropolis-Hastings con propuesta Student-t basada en MAP/Hessiano | Se corrige contra likelihood y prior completos; el posterior no es una solución Ridge/MAP |
| Incertidumbre | Probabilidades de siete respuestas e intervalo epistémico 90% de su expectativa codificada | No es intervalo para una respuesta individual ni validación de una creencia humana |
| Diagnósticos | R-hat por rangos/plegado ≤1.01, ESS cuerpo/colas ≥100; reintento hasta 4096 muestras por cadena | Si falla, `prediction_usable_for_research=false`; se conservan intentos, nunca se fuerza aprobación |

El intercepto no codifica certeza absoluta: extremos ±1 se aproximan por ±0.98.
El centro transformado fija la mediana en condiciones de referencia, mientras
el promedio predictivo depende de la dispersión del prior; esa diferencia se
registra en las comprobaciones predictivas. Bajo falta de covariables, el valor
de diseño de referencia y su indicador de falta son una aproximación explícita,
no una observación imputada a cero ni integración de una exposición desconocida.
Se mantienen por separado las mediciones originales y los avisos de calidad.

Escalados fijos: log-duración referida a 20 minutos, temperatura referida a
20 °C/10 °C, humedad a 50%/25%, viento por 5 m/s y log-transformaciones para
lluvia e iluminancia. Estos parámetros son decisiones reproducibles del piloto,
no una norma térmica. No se estiman coeficientes causales.

## Evidencia y tiempo

El análisis parte siempre del Passport original y del conjunto completo de
eventos únicos anteriores a la fecha de predicción. Recalcular desde el mismo
prior evita aplicar un posterior antiguo y volver a contar sus observaciones.
Se rechazan IDs duplicados y más de una respuesta de comodidad por viaje.
Cada modo aprende únicamente de sus experiencias. No se usan estados terminales
HOTCO como datos ni inicializaciones para el siguiente posterior.

El adaptador valida la exportación longitudinal y toma el contexto congelado
de la ruta seleccionada. No consulta Orion retrospectivamente. Si se declaró
otro modo o hubo cambios de viaje, conserva el contexto como desconocido.
Si los segmentos difieren, no inventa una media espacial. Una medición sin
unidad, fuente, tiempo explícito, estado válido o cobertura identificable se
excluye como covariable y conserva un aviso. Una observación posterior a su
consulta es inválida. Se ensaya una ventana de frescura de **30 minutos entre
medición y consulta**, elegida operacionalmente, todavía sin validación.

El tiempo de consulta inferido desde el archivo es un límite superior procedente
de la finalización de la búsqueda/selección, no un timestamp de sensor fabricado.
La meteorología archivada describe la búsqueda y puede diferir de la exposición
durante el viaje: el modelo contextual actual es una aproximación a esa expectativa.
No sustituye un pronóstico futuro ni mediciones de todo el trayecto. Se reportan
covariables nunca observadas y predicciones fuera de los rangos individuales.
La lluvia sin unidad confirmada y timestamps Orion sin zona explícita permanecen
desconocidos. El modelo básico sigue siendo un comparador cuando falta contexto.

## Resultados reproducibles

Los ocho perfiles son: expectativas acertadas, optimismo equivocado, pesimismo
equivocado, neutralidad dependiente del contexto, extremos equivocados, ruido
correlacionado, datos faltantes/contexto novedoso y cambio temporal.
La verdad generadora es independiente del Passport inicial. Las elecciones
humanas y el efecto de recomendaciones no se simulan ni validan aquí.

3 repeticiones × 8 perfiles × 4 modos × 6 cantidades (1, 3, 5, 10, 20, 40)
× 3 priors × 2 modelos = **3456 comparaciones**. Cada ajuste se evalúa en los
mismos 24 eventos futuros, separados de su entrenamiento. Se comparan log-score,
ranked probability score, error de expectativa, cobertura y precisión con
Passport estático y una actualización categórica simple con pseudocuentas
exploratorias. Los tamaños de muestra son por relación modo–comodidad.

El primer muestreo de 512 muestras por cadena incumplió diagnósticos en 464
ajustes. Se conservaron resultados originales y se repitió únicamente la
inferencia necesaria a 4096, sin cambiar modelos, priors ni conjuntos de prueba.
**3455/3456** ajustes cumplieron finalmente los filtros. El restante queda
marcado como no fiable. El reporte incluye promedios solo de ajustes válidos;
las curvas generales conservan todos y no ocultan el fallo residual.

Para el prior moderado a N=40, el log-score contextual agregado fue −1.694,
frente a −1.701 del básico, −1.746 de la referencia simple y −2.893 del Passport
estático (mayor es mejor). La ventaja contextual sobre el básico fue pequeña
en este conjunto de escenarios. Cuando la declaración inicial era correcta,
aprender no siempre mejoró el pronóstico. No seleccionar el modelo contextual
solo por este promedio ni extrapolarlo a usuarios reales.

La cobertura agregada de intervalos nominales 90% fue aproximadamente 82%
para el contextual moderado y 57% para el básico a N=40. Incluye modelos mal
especificados y cambio temporal: **intervalos más estrechos no garantizan
estimaciones correctas**. Un prior fuerte equivocado mostró mala cobertura.
El modelo acumulativo todavía no incorpora olvido ni detección de cambios.

Además se ejecutaron **128** verificaciones exploratorias de calibración desde
el modelo, **96** comprobaciones de priors y **192** ajustes de sensibilidad.
Se preservan rangos, intervalos binomiales, diagnósticos y comprobaciones
predictivas posteriores. Solo 64 réplicas por modelo y tres repeticiones de
estrés son una exploración inicial, no una garantía de calibración o potencia.

Los semianchos 0.1, 0.2 y 0.3 son candidatos exploratorios; no se eligió un umbral
de activación a partir de resultados favorables. Ningún N queda certificado.
Antes de fases 5–6 se deberá predefinir precisión aceptable, validación del ítem,
comparación temporal en el piloto real, cobertura contextual y tratamiento de
cambio temporal. Convergencia computacional, precisión y validez del constructo
son cuestiones distintas. Acercarse a cero/cambiar de signo se conserva como
hipótesis futura; no se usa como prueba de cambio psicológico o elección.

Artefactos: [reporte completo](../output/d-day-phase4/REPORT.md),
[gráfico](../output/d-day-phase4/comparison.png),
[resultados detallados](../output/d-day-phase4/results.json),
[calibración/sensibilidad](../output/d-day-phase4/calibration.json),
[referencias y priors](D_DAY_PHASE4_LITERATURE.md).

## Uso del laboratorio

El endpoint `POST /api/dyconet/d-day/comfort-pilot/analyze` recibe un Passport
original, evidencia versionada y contextos de predicción. No almacena datos
en el servidor, no modifica el Passport y no llama a HOTCO. Hasta 512 eventos,
ocho predicciones y 4 MiB; usar CLI para conjuntos mayores. Una exportación con
varias raíces originales exige separar explícitamente el individuo/linaje.
Los identificadores del endpoint de investigación son etiquetas suministradas,
no prueba de identidad; el adaptador de backup añade validación longitudinal.

Desde `app-backend/services/dyconet`:

```powershell
python -m comfort_pilot.api ../../../output/d-day-phase4/request_example.json --output ../../../output/d-day-phase4/result_example.json
python -m comfort_pilot.api BACKUP.json --backup --contexts CONTEXTS.json --output ANALYSIS.json
python -m comfort_pilot.experiment --output ../../../output/d-day-phase4 --repetitions 3 --draws 512
python -m comfort_pilot.refine --output ../../../output/d-day-phase4
python -m comfort_pilot.calibration --output ../../../output/d-day-phase4 --replicates 64
```

`CONTEXTS.json` contiene un array de modo, prediction_id, as_of y contexto
con queried_at/measurements; véase el ejemplo generado. Las unidades requeridas
son s, C, %, mm, m/s y lux. Los resultados incluyen priors, eventos usados,
fecha límite, digest de entrada, intentos de muestreo e incertidumbre. Semillas,
versiones de NumPy/SciPy/Python y hashes del código permiten reproducirlos.

## Verificación de la entrega

- Compilación Android, APK y pruebas JVM: 31 pruebas pasaron.
- Backend: 32 pruebas; contrato compartido adicional: 5 pruebas.
- Teléfono físico: 5 pruebas pasaron, incluyendo controles de comodidad,
  flujo diario–simulación–Experience–exportación y concurrencia SQLite.
- Ausencia de respuesta, neutral, modo incorrecto, respuesta futura, viaje no
  realizado, doble conteo, evidencias históricas y atribución multimodal comprobados.
- Posterior básico contrastado con integración numérica independiente.
- Análisis offline y endpoint real 8077 producen el mismo resultado sintético.
- Se instaló 0.4.59 y se reinició el backend con el contrato nuevo. Se corrigió
  una instancia antigua simultánea que seguía atendiendo 8077.

Estas verificaciones acreditan comportamiento de software y exploración del
modelo. El piloto humano y la validez científica de HOTCO-CT siguen pendientes.
