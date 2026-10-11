# D-Day: continuación de implementación

Autorizada el 11 de octubre de 2026. La recogida de experiencias con desplazamiento
real se hará después; no condiciona la entrega de infraestructura. Conservar Bayes,
comodidad y afecto separados, metas declaradas independientes, historia completa
y resultados reproducibles. UI al final. Sin F3, RL, deep learning ni ML operativo.

## Orden y criterios de entrega

| Orden | Entrega | Criterio técnico | Estado |
|---|---|---|---|
| 1 | Orion y elegibilidad | Separar reloj publicado/recepción; exclusiones trazables en aprendizaje y puntuación | Recepción/exclusiones y modelo meteorológico V2 implementados; relojes upstream y otras ventanas pendientes |
| 2 | Política temporal y reglas de uso | Comparación de priors/tasas, costes en estabilidad, incertidumbre y tratamiento de contexto insuficiente | Comparación V2 y política candidata offline implementadas; selección empírica/activación pendientes |
| 3 | Passport aprendido versionado | Original inmutable, evidencia/modelo/configuración archivados, actualizaciones únicas, reversión y reproducción | Implementado en 0.4.63 como cadena experimental persistente; activación y comprobación SQLite en teléfono pendientes |
| 4 | Integración contextual HOTCO | Expectativas por modo/contexto; conservar otras metas/conexiones; sin copia del estado terminal | Pendiente |
| 5 | Evaluación y registro longitudinal | Predicciones previas a elección, respuestas elegibles posteriores, comparación emparejada y tiempos reales | Infraestructura de fase 4 disponible; ampliación pendiente |
| 6 | UI y operación | Procedencia comprensible, exportación/migraciones, recuperación y procesamiento en segundo plano | Pendiente, al final |

La evaluación empírica y la validación psicológica quedan para después. Preparar
reglas explícitas de uso sin presentar umbrales operativos candidatos como precisión
validada. Mantener activación deshabilitada por defecto hasta acordar esas reglas.
No convertir resultados favorables de simulaciones en una regla universal.

## Primer bloque implementado

`comfort_pilot/sensor_time.py` define `orion-current-values-reception-v1` para
Walter y Winfred. Las nuevas QueryLists guardan cuándo se recibió la respuesta;
el caché de una consulta conserva esa misma hora, sin rejuvenecerla por segmento.
La garantía de valores actuales se registra como comunicación del proveedor
confirmada por el autor el 11 de octubre. No certifica una fecha de medición.

El auditor admite actualidad por garantía/recepción, conservando la fecha
publicada como registro no mantenido. Edad de medición desconocida y edad de
recepción se informan por separado. La recepción futura se rechaza y una respuesta
reutilizada más de 30 minutos después no se admite como actual. Este límite es
una política operativa heredada, no una validación científica del sensor.
Lecturas históricas sin el nuevo contrato mantienen su política original.

La consulta pública de comprobación produjo seis variables utilizables de Walter
y cinco de Winfred. Lluvia sigue excluida por unidad ausente/incompatible; no se
convierte mm/h a mm. Viento de diez minutos ausente en Winfred sigue ausente.
Auditoría: `output/d-day-implementation-2026-10-11/orion-reception-audit.json`.
Semántica de ventanas y comparabilidad meteorológica permanecen pendientes.

La investigación posterior [D_DAY_ORION_RESEARCH.md](D_DAY_ORION_RESEARCH.md)
confirma series públicas, omisiones de lluvia en la suscripción y cadencia horaria
de Winfred. La luz de Walter procede de `t1solrad`: revisar la inferencia previa
de lux; el conteo seis no certifica unidades. Separar edad de consulta de edad de
adquisición y completar contratos por atributo antes del siguiente bloque temporal.

`comfort_pilot/eligibility.py` centraliza exclusiones explícitas por experiencia,
viaje y objetivo. Si una exclusión aporta ambos IDs, ambos deben coincidir.
Pruebas, simulaciones, cancelaciones y experiencias declaradas no realizadas no
entran como observaciones empíricas. Una experiencia de modo cambiado puede
entrenar con contexto desconocido; no puntúa la predicción de la ruta original.
La ausencia de GPS no identifica por sí sola una prueba: los registros manuales
pueden representar experiencias reales.

Adaptadores de comodidad/afecto, pronósticos prospectivos, evaluación y revisión
usan esa política. Los comandos offline aceptan `--exclusions archivo.json`.
Un backup puede conservar el mismo registro en la clave de storage
`d-day.evidence-exclusions.v1`; se aplica automáticamente al leer ese backup.
Android incluye ese registro en nuevas solicitudes y preserva clasificación y
estado en su evidencia compacta. No se reescriben solicitudes ya congeladas.

La exclusión privada de la prueba estacionaria del autor puede pasarse a los
comandos offline. No se ha instalado ni inyectado en la base del teléfono el nuevo
registro de exclusiones: la importación/configuración de ese registro en la app
forma parte del trabajo operativo pendiente. No se declara que una app ya
instalada haya cambiado por compilar fuentes nuevas.

Verificación: 83 pruebas backend aprobadas; las diez pruebas del bloque volvieron
a pasar después de añadir trazabilidad de exclusiones por objetivo. APK Android
compilado y suite JVM de 44 pruebas aprobada, incluida clasificación explícita.
Resultados: `output/d-day-implementation-2026-10-11/verification.json`.
Esta entrega no modifica el Passport activo ni las conexiones HOTCO.

Entrega posterior de presentación de lluvia: [D_DAY_RAIN_DISPLAY.md](D_DAY_RAIN_DISPLAY.md).
Exclusividad Winfred rainIntensity en UI, mm/h, reporte anterior con edad
explícita/desconocida y acumulado retenido solo para auditoría. Cambio puntual de
UI autorizado por el autor. Verificación: 86 backend, 47 JVM, build Vite y APK.
El modelo previo sigue excluyendo intensidad por esperar mm; adaptar la
covariable requiere versión propia, sin reinterpretar históricos.

Adaptación posterior entregada: [D_DAY_WEATHER_INTENSITY_V2.md](D_DAY_WEATHER_INTENSITY_V2.md).
Modelo nuevo de intensidad con prior propio, antigüedad de reporte y contexto
insuficiente explícitos, exclusión de lux inferidos de Walter, captura/forecast
Android V2 y evaluación separada por versión. 97 backend y 49 JVM aprobadas.
No activa aprendizaje sobre Passport/HOTCO. El siguiente bloque es política
temporal y sensibilidad de priors/tasas bajo este contrato.

Bloque temporal posterior: [D_DAY_TEMPORAL_INTENSITY_V2.md](D_DAY_TEMPORAL_INTENSITY_V2.md).
176 secuencias sintéticas, 8.448 eventos y 236.800 pronósticos puntuados; cinco
configuraciones de priors, q=0/0.15/0.4 y promedio de modelos. Comparaciones por
eventos comunes y secuencias independientes, comodidad/afecto separados.
Auditoría seleccionada SMC–MCMC y sensibilidad a tamaño/semilla; 103 pruebas
backend aprobadas. Política candidata mantiene referencia acumulativa e historia
completa, sin tasa universal ni activación automática. Siguiente bloque: Passport
aprendido versionado, linaje, reproducción e idempotencia/reversión.

Passport aprendido implementado: [D_DAY_LEARNED_PASSPORT.md](D_DAY_LEARNED_PASSPORT.md).
Una versión por Experience elegible, dos objetivos separados, reconstrucción
desde prior original e historia única, posteriores completos archivados. Cola
Android durable, commits atómicos y reanudación; selección experimental reversible
independiente del tip y del Passport activo. Revisión de backups conserva ancestros
y originales. 113 pruebas backend/56 JVM aprobadas; APK 0.4.63 code 74 compilado,
sin instalar ni reiniciar servicio. Dos pruebas SQLite instrumentadas compiladas,
no ejecutadas en teléfono. Importación del registro privado de exclusiones sigue
pendiente para procesar historia existente; la app conserva el trabajo fallido
y evita incorporarla silenciosamente. Siguiente bloque: integración contextual
HOTCO con reglas de uso explícitas y activación deshabilitada por defecto.
