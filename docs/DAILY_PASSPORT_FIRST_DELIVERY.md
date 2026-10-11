# Passport diario: primera entrega — 0.4.49

Implementación del ciclo metas del día → deliberación → elección → experiencia,
con versiones enlazadas y evidencia exportable. HOTCO-CT sigue siendo el modelo
experimental del autor; esta entrega no establece validación científica.

## Comportamiento

- El editor pregunta qué importa hoy y conserva fuentes SNES, escala ordinal
  1–7, avatar, luces y reacciones existentes.
- Home permite confirmar o revisar las metas del día. Antes de buscar rutas se
  exige una confirmación correspondiente a la fecha y zona horaria actuales.
  Al confirmarla se continúa la búsqueda solicitada.
- Las respuestas anteriores se proponen para el nuevo día, sin confirmarlas
  automáticamente. Los antiguos repartos de diez puntos no se transforman en
  respuestas ordinales inventadas.
- Cada confirmación crea un Passport con padre, revisión y medición diaria;
  se archiva su antecedente. Una escritura concurrente invalida la confirmación
  pendiente en lugar de sobrescribir el Passport más reciente.
- Experience ofrece satisfacción de cualquiera de las once necesidades, 1–7,
  y emoción por cada modo realmente utilizado, −3…+3. Las respuestas son
  opcionales: omitirlas representa información desconocida.
- Confirmar Experience guarda el viaje y una nueva versión de evidencia en la
  misma transacción SQLite. Repetir idéntica confirmación conserva el resultado;
  repetirla con respuestas distintas se rechaza. Un viaje no realizado no puede
  contener satisfacción ni emoción del modo. Cancelar conserva el registro sin
  inventar una experiencia completada.

## Separación de medición, predicción y observación

| Registro | Significado | Efecto en esta entrega |
|---|---|---|
| `daily_goals` | Importancia declarada hoy, fecha, zona, confirmación, ordinales y normalización | Cambia únicamente la inicialización de necesidades |
| `belief_estimation_inputs` | Entradas originales del estimador de creencias | Conserva exactamente las conexiones estimadas al cambiar metas diarias |
| `simulation_evidence` | Predicción del motor, parámetros, topología y trayectoria | Se registra; no se convierte en experiencia observada |
| `choice_event_id` | Elección efectivamente registrada | Vincula el viaje a la alternativa y al Passport de decisión |
| `evidence_history` | Feedback explícito de Experience y referencias a búsqueda, elección y viaje | Crea una versión de evidencia; no aplica aprendizaje |

La transformación de necesidades sigue siendo `(r−1)/6`, independiente por
necesidad. No se exige suma uno. Creencias y valencias conservan sus escalas y
la dinámica del motor conserva las ecuaciones y parámetros existentes.
Reconfirmar metas no recalcula creencias a partir de la importancia del nuevo día:
la personalización reconstruida usa las entradas originales del estimador.

No se copia el estado terminal de necesidades, acciones o valencias al siguiente
Passport. El indicador `longitudinal_policy` es
`measurement_snapshots_only_no_state_carryover`. Los eventos de Experience
declaran `learning_applied=false` y `state_carryover_applied=false`.
La actualización bayesiana, olvido temporal y cualquier propagación de estados
quedan para una entrega posterior con modelo de observación y validación propios.

## Contratos y almacenamiento

`POST /api/dyconet/daily-passport/confirm` recibe
`schema_version=daily-passport-confirm-v1`, el `previous_passport` completo y
`daily_goals={local_date,timezone,confirmed_at,ratings}`. Valida once enteros
1–7 y coherencia entre fecha, zona e instante. El bootstrap inicial también
admite `daily_goals` y exige que coincida con el cuestionario.

La app solicita `record_trajectories=true` en la deliberación. El resultado
guarda los 19 estados: once necesidades, cuatro acciones y cuatro valencias,
con orden de columnas explícito. El archivo `trajectory_archive` conserva
trayectorias completas; cada simulación apunta a su `trajectory_id`. Las
trayectorias idénticas se almacenan una sola vez. Se guardan también parámetros,
`dt`, topología, disponibilidad, correcciones de proyección, contexto y resultados
finales. El tiempo es tiempo del modelo, distinto del tiempo real de viaje.
Las solicitudes que omiten el indicador no reciben trayectorias completas.

El registro de búsqueda conserva el Passport confirmado y sus referencias,
metas diarias, solicitud, contexto, recomendación y resultado. Los contratos
de viaje admiten los campos adicionales de satisfacción y emoción manteniendo
compatibilidad con registros anteriores.

La persistencia longitudinal está en SQLite del teléfono e incluida en la
exportación/importación. El backend construye y valida los contratos, pero no
añade un repositorio longitudinal central ni sincronización entre dispositivos.
Esto mantiene la arquitectura local existente y requiere conservar copias de
seguridad para llevar los datos a análisis posterior.

## Validación

- 17 pruebas de backend aprobadas: incluye confirmación inicial y diaria,
  conservación exacta de 44 creencias y cuatro valencias, cambio de día,
  errores de escala/fecha/zona e idempotencia, y trayectorias completas frente
  a solicitudes sin registro.
- 16 pruebas unitarias Android aprobadas y compilación de APK principal e
  instrumentación.
- Prueba integrada en celular con backend real y base aislada: onboarding,
  confirmación diaria, deliberación con 2001 muestras de 19 estados,
  segunda elección enlazada, Experience, revisión de evidencia sin modificar
  perfil, repetición sin duplicados, rechazo de feedback contradictorio,
  validación de contrato en ambos lados, propuesta para mañana y exportación /
  restauración. No modifica el perfil personal del usuario.
- Prueba del editor en celular aprobada: independencia de respuestas, límites,
  desplazamiento, cuatro avatares, aprobación/negación y movimiento reducido.
- Dos pruebas existentes de concurrencia SQLite aprobadas.

La prueba integrada usa una ruta sintética de caminata; no constituye una prueba
de los proveedores externos de routing, clima u Orion ni una validación del modelo.
