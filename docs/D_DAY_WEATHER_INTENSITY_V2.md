# D-Day: contexto meteorológico con intensidad de lluvia

Entrega del 11 de octubre de 2026. Autorizada al comenzar el punto 1 de la
continuación: adaptar el aprendizaje a Winfred mm/h, controlar antigüedad y
contexto insuficiente, revisar luz Walter y preservar versiones anteriores.
No activa aprendizaje sobre Passport ni HOTCO; no requiere recorridos reales.

## Versiones y escala

`d-day-weather-accumulation-v1` conserva el diseño anterior, su campo `rain`
en mm, los priors y el comportamiento de solicitudes que omiten versión.
Los laboratorios temporales anteriores siguen utilizando ese diseño; sus
resultados no validan la nueva magnitud. No se convierten mm a mm/h.

`d-day-weather-intensity-v2` tiene un predictor distinto: `rainIntensity`,
en mm/h, transformado mediante `log1p(intensidad / (1 mm/h))`. La referencia
de 1 mm/h hace explícita la escala de la transformación; no es un umbral de lluvia
ni una calibración empírica. Los otros predictores y sus indicadores de ausencia
se conservan; UV sigue fuera. Ausencia y cero generan filas diferentes.

El modelo ordinal conserva las siete categorías y el muestreo de cuatro cadenas
Metropolis–Hastings con corrección al posterior completo. Comodidad y afecto
se ajustan por separado. Los resultados nuevos se identifican como
`d-day-comfort-ordinal-intensity-v2` y `d-day-affect-ordinal-intensity-v2`.
No se transfieren posteriores ni coeficientes de lluvia acumulada. El coeficiente
de intensidad comienza con prior candidato N(0, 0.35²), ligado a esta escala,
sin validación empírica. Se rechaza el prior opcional de forma de literatura V1
en V2; no se presenta la lluvia como necesariamente negativa para cada persona.

## Política de uso candidata

La adquisición de valores y su presentación no equivalen a elegibilidad para
aprendizaje contextual. La UI mantiene el último valor Winfred y su antigüedad
explícita/desconocida según [D_DAY_RAIN_DISPLAY.md](D_DAY_RAIN_DISPLAY.md).

Para incorporar intensidad como covariable de V2 se exige origen Winfred/Orion,
atributo `rainIntensity`, mm/h, valor finito en 0–450 y contrato de ventana de
diez minutos. Además exige hora de reporte explícita `receivedAt` con zona
horaria y procedencia registrada; Walter.datetime, dateModified y recepción de
la consulta no la sustituyen. Se conserva por separado edad del reporte, edad
de consulta y edad de medición (esta última sigue desconocida).

La política `d-day-weather-use-v2` admite reportes de hasta 600 segundos en el
contexto congelado. El límite es una decisión operativa candidata ligada a la
ventana, no una precisión validada ni una modificación del envío del proveedor.
Un reporte futuro rechaza la solicitud; uno más antiguo o sin hora fiable queda
excluido como intensidad, incluso si vale cero. La recepción reciente no lo
rejuvenece. El intervalo configurado de 60 minutos no se usa para ocultar esa
antigüedad ni para calcular un acumulado.

Para otros sensores se conserva el control existente de 30 minutos de medición
o de reutilización de respuesta bajo garantía de proveedor. La garantía y la
recepción no certifican edad física. Sus ventanas/semánticas no verificadas
siguen apareciendo en auditoría; no se declara resuelto todo el contrato del proveedor.

Con meteorología parcial, el modelo conserva indicadores de ausencia y reporta
las limitaciones, covariables sin evidencia y rangos no observados. Sin ninguna
variable meteorológica utilizable, el modelo contextual se marca no elegible
para puntuación y su política indica fallback al básico. El básico y la referencia
inicial conservan sus comprobaciones propias. No se sustituyen silenciosamente
probabilidades contextuales por básicas ni se cuentan como un tercer modelo.
La activación en producción permanece deshabilitada en todas las situaciones.

## Luz, acumulado y cobertura

La ingesta publicada de Walter deriva `lightIntensity` de `t1solrad`.
Las consultas nuevas no infieren lux de ese alias: sin unidad explícita compatible
queda no elegible y cruda. Lux/lx explícitos con dominio válido se normalizan a
lux. El auditor V2 también rechaza la luz histórica con unidad atribuida por
el piloto sin unidad original. El contexto congelado y archivo original no se
reescriben. No hay conversión fija W/m²–lux.

Para rutas, la lluvia V2 solo toma Winfred y exige cobertura completa por tramos,
misma lectura y metadatos coherentes. No crea promedios de exposición. Walter no
genera un conflicto con Winfred cuando su lluvia queda fuera del contrato V2.
Los valores contradictorios, tiempos de reporte diferentes o cobertura parcial
se conservan en auditoría y no se proyectan como una lectura de toda la ruta.

`cumulativeRainfall` sigue conservado para auditoría de intervalos, fuera de
predictores y UI. Las capturas V2 lo retienen sin normalizarlo. Los resúmenes
separan variables antiguas y nuevas para no mezclar mm con mm/h.

## Integración y reproducción

Los endpoints de piloto aceptan `weather_model_version` explícito. Los comandos
de comodidad/afecto aceptan `--weather-model-version` para solicitudes o backups.
La reconstrucción V2 de un backup es una nueva solicitud de análisis identificada;
no modifica evidencia, predicciones ni evaluaciones históricas.

Las solicitudes prospectivas nuevas de Android seleccionan V2. Las solicitudes
ya congeladas sin ese campo mantienen V1. La captura ambiental acepta versión
opcional; clientes antiguos mantienen V1, nuevos solicitan V2. Android acepta
auditorías V1/V2 coherentes con el contexto y conserva identificación GPS/eventos.
El muestreo durante un viaje sigue siendo contexto ambiental, no exposición personal.

Los scores nuevos conservan `weather_model_version` en Android/backend; los
informes separan métricas y bloques diarios por versión. Los resultados históricos
sin ese campo conservan su representación y recomputación originales.

## Comprobaciones y límites

- 97 pruebas backend aprobadas. Diez pruebas específicas V2 verifican unidades,
  ventana/edad, cero/ausencia, cortes temporales, exclusión de acumulado, luz,
  cobertura, posterior y reproducción V1; además se comprueba separación de métricas.
- La prueba de inferencia utiliza 60 observaciones sintéticas alternadas, con
  comodidad 6 bajo intensidad cero y 2 bajo 6 mm/h. El posterior distingue esas
  condiciones y pasa sus diagnósticos. Es una comprobación de implementación,
  no datos observados, precisión del sensor ni validación psicológica.
- 49 pruebas JVM aprobadas, incluyendo aceptación de versiones de captura y
  trazabilidad de versiones en scores; APK debug compilado.
- `validation/audit_weather_intensity_v2.py` revisa offline el archivo público
  del 11 de octubre. Resultado: Walter 5/7 y Winfred 5/7 variables meteorológicas
  utilizables bajo V2; intensidad excluida por hora de reporte desconocida.
  Walter pierde la luz antes inferida como lux. Es revisión retrospectiva de
  contrato, no nuevo evento entrenable ni nueva consulta en vivo.
- Evidencia: `output/d-day-weather-intensity-v2-2026-10-11/archived-query-review.json`
  y `verification.json`. No se instala APK ni se reinicia el backend en ejecución.

Persisten acceso a recepción upstream, contrato de otras ventanas, corrección de
suscripción histórica y decoder de temperatura negativa del proveedor. Ninguno
se oculta con fechas inventadas o conversiones de unidades. La siguiente entrega
es política temporal y comparación de priors/tasas bajo el contrato V2 antes de
crear el Passport aprendido e integrarlo con HOTCO.
