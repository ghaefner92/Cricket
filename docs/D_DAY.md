# D-Day — Passport dinámico y evidencia longitudinal

Alias: D-Day, D.day, d.day. Acordado el 9 de octubre de 2026.

## Objetivo

Preparar un Passport que aprenda entre deliberaciones a partir de experiencias
observadas, condicionado por las circunstancias de cada viaje. Conservar las
declaraciones originales, las versiones del Passport y todas las trayectorias
para estudiar posteriormente persistencia y patrones temporales.
HOTCO-CT es un modelo experimental del autor; este trabajo no lo valida.

## Plan acordado

| Fase | Acción | Resultado verificable |
|---|---|---|
| 1 | Definir el contrato teórico | Separación de declaraciones, metas diarias, expectativas, predicciones y observaciones |
| 2 | Auditar lo implementado | Inventario de datos, discrepancias y exclusión de F3 del flujo D-Day |
| 3 | Consolidar el registro longitudinal | Exportación consistente y versiones enlazadas de Passport, deliberación, recomendación, elección, viaje y Experience |
| 4 | Diseñar aprendizaje bayesiano para una necesidad inicial | Priors, incertidumbre, predictores contextuales y modelo de observación explícitos |
| 5 | Implementar actualización del Passport | Posteriores trazables derivados de experiencias confirmadas, conservando declaraciones |
| 6 | Integrar expectativas contextuales con HOTCO | Entradas/conexiones condicionadas por contexto, sin copia automática del estado terminal |
| 7 | Evaluar aprendizaje | Comparación temporal con Passport estático y actualización simple; separar predicción de validación psicológica |
| 8 | Preparar características temporales | Estados y trayectorias vinculados con intervalos reales entre eventos |
| 9 | Explorar patrones mediante ML | Evaluación con separación temporal y por individuo, evitando fuga de información |
| 10 | Evaluar una hipótesis explícita de persistencia | Incorporar continuidad/decay solo si aporta evidencia en observaciones independientes |

La autorización inicial comprendía 1–4; fue ampliada el 11 de octubre de 2026
según la continuación autorizada registrada debajo. Las fases 1–3 se completaron en 0.4.58;
la fase 4 se implementa en 0.4.59 como laboratorio aislado y recogida de evidencia.
En 0.4.60 se amplía con aprendizaje afectivo y medición afectiva obligatoria
por modo utilizado: [D_DAY_AFFECT_DELIVERY.md](D_DAY_AFFECT_DELIVERY.md).
En 0.4.61 se automatizan predicciones prospectivas en segundo plano y su
evaluación con Experience: [D_DAY_PROSPECTIVE.md](D_DAY_PROSPECTIVE.md).
La evaluación temporal autorizada continúa offline con candidatos y simulaciones:
[D_DAY_TEMPORAL.md](D_DAY_TEMPORAL.md). No activa adaptación en la app.
La comparación se amplía al contexto meteorológico completo sin UV, posponiendo
cambios de UI: [D_DAY_TEMPORAL_WEATHER.md](D_DAY_TEMPORAL_WEATHER.md).
La siguiente comparación desacopla clima/tiempo y estudia cambios específicos
de lluvia: [D_DAY_ATTRIBUTION.md](D_DAY_ATTRIBUTION.md).
La fase 4 incorpora trazabilidad de unidades, semántica y cobertura por tramos
en predicciones y evidencias: [D_DAY_CONTEXT_AUDIT.md](D_DAY_CONTEXT_AUDIT.md).
La entrega técnica 0.4.62 añade captura ambiental durante el viaje, reloj de
Walter compartido con Winfred confirmado por el autor, revisión del piloto y
sensibilidad offline de la red completa: [D_DAY_PHASE4_CLOSE.md](D_DAY_PHASE4_CLOSE.md).
No declara cerrado el componente científico: vigencia/semántica de sensores,
calibración y reglas de precisión/activación siguen pendientes.
Las fases posteriores quedan pendientes de implementación según el nuevo orden.
No actualizar el Passport activo ni HOTCO con sus resultados sin implementar
las entregas y reglas de uso correspondientes.

Las decisiones del autor para preparar la fase 4 están en
[D_DAY_PHASE4_PROTOCOL.md](D_DAY_PHASE4_PROTOCOL.md): confort físico percibido,
recogida obligatoria posterior en un piloto remunerado, expectativas individuales
contextuales, meteorología disponible sin UV y priors investigados mediante
literatura y simulaciones. El autor confirmó la escala bipolar de ayuda–dificulta
satisfacer comodidad: 1 dificulta mucho, 4 ni ayuda ni dificulta, 7 ayuda mucho,
con codificación firmada `(r−4)/3`. Implementación, supuestos, simulaciones y
límites: [D_DAY_PHASE4_DELIVERY.md](D_DAY_PHASE4_DELIVERY.md).

## Contrato de variables y evidencia

| Variable | Fuente y escala | Tratamiento en fases 1–3 | Posible aprendizaje posterior |
|---|---|---|---|
| Metas del día | Importancia declarada 1–7; `(r−1)/6` por necesidad | Medición fechada que inicializa necesidades; sin exigir suma uno | Nueva declaración diaria; no inferir importancia directamente de satisfacción |
| Creencias iniciales | Declaración 1–7; `(r−4)/3`, y celdas estimadas | Conservar procedencia y base original del estimador Ridge congelado | Expectativas de satisfacción contextual con posterior e incertidumbre separados de la declaración |
| Valencias declaradas | −3…+3; `r/3` | Conservar declaración original | Expectativa afectiva por modo, distinta de satisfacción de necesidades |
| Estados/activaciones simulados | Trayectoria HOTCO con parámetros, topología y orden de columnas | Predicción archivada; tiempo del modelo distinto del reloj real | Características analíticas; nunca feedback observado ni nueva inicialización automática |
| Recomendación y elección | Salida calculada y alternativa elegida con IDs/fecha | Conservar ambas por separado; calcular no demuestra que el usuario vio una recomendación | Modelo de elección explícito que considere disponibilidad e influencia de recomendaciones |
| Contexto y rutas | Observaciones APP/proveedores, consultas, cobertura, tiempos y geometría | Guardar contexto observado sin fuerzas F3 ni actualización cognitiva | Predictores para expectativas; contexto por sí solo no demuestra satisfacción personal |
| Viaje registrado | GPS/eventos y métricas, o declaración manual identificada | Conservar origen, calidad y estados; cancelación no equivale a viaje completado | Evidencia objetiva solo en dimensiones medibles y con modelo de observación |
| Experience | Comodidad bipolar 1–7 obligatoria; afecto −3…+3 obligatorio por modo utilizado, `r/3`; otras satisfacciones opcionales | Evidencia identificada e idempotente, versión antes/después; sin actualización activa | Dos modelos bayesianos ordinales separados en fase 4; omisión histórica significa desconocido, nunca cero |

F3 queda eliminado del código el 12 de octubre de 2026, por decisión del autor.
El flujo D-Day conserva `context_policy=observations_only`: archiva observaciones
y rutas sin añadir fuerzas contextuales a la ODE. Es la única política admitida;
el modo histórico de fuerzas ya no puede solicitarse ni activarse por entorno.
Hasta la fase 6, las diferencias entre alternativas provienen de las entradas
existentes y no de un aprendizaje contextual aún inexistente. Los originales y
resultados históricos se conservan para lectura, sin reinterpretarlos ni volver
a calcular sus fuerzas retiradas. Detalle: [F3_REMOVAL_NATIVE_INDEPENDENCE.md](F3_REMOVAL_NATIVE_INDEPENDENCE.md).

La capa bayesiana de fase 4 estima expectativas en un laboratorio separado.
La actualización de entradas activas entre deliberaciones pertenece a fases 5–6.
`learning_applied=false` y `state_carryover_applied=false` se mantienen en los
viajes y simulaciones activas; el análisis informa aprendizaje experimental por separado.

No concatenar trayectorias como si constituyeran una única evolución continua:
cada deliberación tiene condiciones iniciales propias y tiempo interno; enlazar
con timestamps reales permite estudiar persistencia sin asumirla.

## Memoria y estado

Revisión posterior de textos del Passport y Experience: entrega 0.4.69 en
[DDAY_PSYCHOMETRIC_COPY_AUDIT.md](DDAY_PSYCHOMETRIC_COPY_AUDIT.md). El autor
autoriza modificar solo texto, conservando los elementos estéticos. Revisión
basada en validez de contenido, traducción y medición longitudinal; no constituye
validación científica. Mantiene constructos y escalas separados, conserva datos
históricos y documenta comparabilidad de versiones/atribución multimodal como
problemas todavía no resueltos. Las entrevistas y recogidas reales son posteriores.

### Continuación autorizada el 11 de octubre de 2026

El autor autoriza continuar la implementación completa en este orden:
contrato Orion y elegibilidad de evidencia; política temporal y reglas de uso;
Passport aprendido versionado (fase 5); integración contextual HOTCO (fase 6);
evaluación/registro longitudinal; UI y operación al final. Esta autorización
amplía el límite anterior de fases 1–4. Bayes se mantiene; F3, RL y deep learning
siguen excluidos. La exploración ML de fase 9 no se incluye: preparar análisis
longitudinales y de persistencia sin activar ML.

La adquisición de experiencias reales con GPS y movimiento se pospone hasta
después de esta implementación; no es un bloqueo de desarrollo ni una condición
para completar infraestructura. Pruebas y simulaciones verifican implementación,
no validación científica. Mantener aprendizaje operativo desactivado por defecto
hasta contar con reglas explícitas y evaluación para su activación.

Nueva aclaración del autor: Orion sigue enviando valores de sensores actualizados
en tiempo real aunque el proveedor no actualiza su registro temporal. Conservar
fecha publicada y hora de recepción por separado, registrando la garantía de
actualización comunicada por el autor. No inventar fecha de medición ni aplicar
esa garantía retrospectivamente a snapshots históricos. La incompatibilidad de
mm/h con mm y las ventanas meteorológicas siguen requiriendo contratos propios.

Prioridades de éxito: exclusión efectiva de pruebas/cancelaciones/no realizados,
versionado y reproducción de posteriores, idempotencia, ausencia de fuga temporal,
reversión, incertidumbre y contexto insuficiente explícitos, metas diarias
independientes, recuperación/migraciones y procesamiento en segundo plano.
Evaluar predicción, explicación y sensibilidad HOTCO por separado. Una emoción
negativa contextual no implica por sí sola una creencia general negativa.

Primer bloque: versionar el contrato de actualidad por recepción de Orion y
centralizar elegibilidad/exclusiones en adaptadores de aprendizaje y evaluación,
preservando evidencia y resultados históricos.

Estado de la continuación: [D_DAY_IMPLEMENTATION.md](D_DAY_IMPLEMENTATION.md).

Investigación Orion del 11 de octubre: [D_DAY_ORION_RESEARCH.md](D_DAY_ORION_RESEARCH.md).
Histórico público confirmado, pero con filas repetidas y atributos meteorológicos
omitidos. Winfred comunica intervalo de envío de 60 minutos; el manual define
intensidad de lluvia sobre diez minutos. Revisar unidad de luz Walter (`t1solrad`)
y decoder de temperaturas negativas. Recepción reciente no certifica adquisición
reciente ni comparabilidad de unidades; el conteo previo Walter 6/7 no la demuestra.

Decisión posterior de lluvia implementada: [D_DAY_RAIN_DISPLAY.md](D_DAY_RAIN_DISPLAY.md).
Mostrar exclusivamente intensidad Winfred en mm/h como último reporte, con edad
desconocida si falta reloj fiable. Acumulado reservado a auditoría de intervalos,
sin UI. El autor autoriza este cambio puntual de UI; el resto sigue al final.

Adaptación meteorológica implementada: [D_DAY_WEATHER_INTENSITY_V2.md](D_DAY_WEATHER_INTENSITY_V2.md).
V2 usa covariable rainIntensity mm/h, coeficiente nuevo con prior candidato centrado
en cero, reglas de reporte reciente/conocido y fallback básico sin clima utilizable.
Walter no aporta lux inferidos. Solicitudes Android nuevas usan V2; V1 conserva
reproducción histórica. Evaluación y capturas separan versiones. 97 backend y 49
JVM aprobadas; APK compilado. Hora upstream de lluvia sigue pendiente; en su
ausencia se muestra el último valor, pero no se incorpora como covariable actual.

Comparación temporal V2 implementada: [D_DAY_TEMPORAL_INTENSITY_V2.md](D_DAY_TEMPORAL_INTENSITY_V2.md).
176 secuencias y 8.448 respuestas nuevas sintéticas; 236.800 pronósticos previos
a respuesta, cinco configuraciones de priors y tres tasas con promedio de modelos.
Contrastes emparejados, incertidumbre/variación y auditoría numérica seleccionada;
103 pruebas backend aprobadas. Referencia acumulativa y candidatos dinámicos
offline, sin elección universal ni activación Passport/HOTCO. Siguiente entrega
del orden: Passport aprendido versionado con evidencia, linaje y reversión.

Entrega siguiente implementada: [D_DAY_LEARNED_PASSPORT.md](D_DAY_LEARNED_PASSPORT.md),
0.4.63. Cadena de versiones aprendidas por Experience elegible, separada del
Passport activo, con originales y modelos/priors/contexto/evidencia completos.
Aprendizaje acumulativo cuenta cada observación una vez, reconstruyendo desde
el prior original; cola durable Android y commits atómicos, reproducción y
selección experimental reversible. 113 backend/56 JVM aprobadas; APK compilado,
sin instalar/reiniciar backend. Pruebas SQLite instrumentadas compiladas, ejecución
en teléfono e importación del registro privado de exclusiones pendientes.
No modifica metas diarias ni activa posteriores/HOTCO. Siguiente bloque del orden:
integración contextual HOTCO con versión identificada y reglas de uso explícitas.

Este archivo y el enlace en `AGENTS.md` constituyen la memoria persistente local
del proyecto. No dependen de una memoria global de ChatGPT.

Estado de entrega, auditoría y pruebas: [D_DAY_DELIVERY.md](D_DAY_DELIVERY.md).
