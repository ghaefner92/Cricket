# D-Day: predicción prospectiva automática — 0.4.61

Entrega autorizada tras la revisión D-Day: automatizar el registro de
predicciones experimentales y su evaluación posterior. Sigue en fase 4;
no actualiza perfil cognitivo activo, metas ni conexiones HOTCO. No introduce
F3, estados terminales como observaciones, olvido ni umbrales de activación.

## Flujo y contenido

1. Una búsqueda completada deja un trabajo `queued` persistido en SQLite.
2. Un trabajo independiente del estado `busy` de la UI prepara un paquete
   reducido: Passport original, experiencias del mismo linaje y contexto de
   las alternativas disponibles. No envía GPS ni trayectorias HOTCO.
3. `POST /api/dyconet/d-day/prospective/forecast` calcula comodidad y afecto
   con los modelos básico/contextual y referencia fija del Passport inicial.
4. Conserva solicitud, contexto, corte temporal, digest, versiones, priors,
   eventos usados, probabilidades, incertidumbre, diagnósticos y hora de
   recepción en el dispositivo dentro de `d_day_forecast` del registro.
5. Al confirmar Experience, `d_day_evaluations` vincula puntuaciones con
   evento, viaje, elección, alternativa y predicción. Su escritura comparte
   la transacción del viaje y de la revisión de evidencia del Passport.

Los trabajos se serializan; alternativas del mismo modo con la misma evidencia
reutilizan el ajuste y predicen con sus respectivos contextos. No bloquean
Find routes, play o confirmación. Los trabajos queued/pending se retoman al
refrescar/iniciar la app; no se garantiza ejecución mientras Android haya
terminado el proceso. Fallos quedan registrados y no bloquean el viaje; en
esta entrega no se reintentan automáticamente estados failed. Los resultados
completados no se recalculan después de recibir feedback.

La preparación recorre registros uno a uno, con snapshots individuales:
evita retener todas las trayectorias en memoria y una transacción de escritura
durante la lectura del historial completo mientras se registra GPS.

Límites del endpoint: 4 MiB, 512 registros/evidencias por objetivo y 32
alternativas. Un exceso queda como fallo explícito; no se trunca silenciosamente
el historial. El servidor no persiste el contenido. Almacenamiento y exportación
continúan en el dispositivo. Predicciones de alternativas no elegidas se
archivan, pero no reciben respuestas inventadas ni puntuaciones.

## Temporalidad y atribución

El corte `as_of` es la finalización de la búsqueda. Solo experiencias
confirmadas estrictamente antes entran al ajuste. El paquete queda persistido
antes de solicitar inferencia, y su resultado se registra con hora local.
Para puntuar se exige recepción estrictamente anterior a la elección:
predicciones tardías permanecen archivadas como `unscored` con motivo. Que
el paquete se conserve antes del feedback no convierte una recepción tardía
en una predicción disponible antes de elegir.

No se puntúan viajes no realizados ni viajes declarados con cambios: todavía
no contamos con atribución suficiente del contexto previsto a esos cambios.
Comodidad se atribuye únicamente al modo de su pregunta; afecto al modo de
la alternativa realizada. Las demás respuestas se conservan como evidencia
para futuros ajustes pero no se propagan a otras rutas/modos.

Contexto meteorológico/duración sigue siendo el de búsqueda, no exposición
medida a lo largo del viaje. Se mantienen controles de unidades, timestamps
y procedencia, y la exclusión UV. Los registros de otro linaje o sin archivo
original resoluble se excluyen con IDs; si falta el original del registro
actual, se informa fallo. No reconstruir declaraciones históricas.

## Plan de evaluación

Se fijan métricas antes de nuevas respuestas: log-score (mayor es mejor) y
ranked probability score (menor es mejor) sobre las siete categorías.
El error absoluto de la respuesta normalizada es complementario: incluye
variabilidad de una respuesta y no mide directamente error de la expectativa
latente. No contar una respuesta fuera de un intervalo epistémico como fallo
de cobertura predictiva: esos intervalos describen expectativas, no respuestas.

Comodidad y afecto se analizan separados, por modo. Las comparaciones de los
tres modelos utilizan exactamente los mismos eventos elegibles con diagnósticos
válidos; se conservan también motivos de exclusión. Datos elegidos por el
participante permiten evaluar predicción condicional al modo utilizado, no
validar respuestas de modos no elegidos ni atribuir causalidad a recomendaciones.

El prior moderado y puntos de corte existentes son candidatos congelados para
esta recogida, no parámetros científicamente validados. La referencia estática
usa las probabilidades de la ubicación latente correspondiente a la expectativa
inicial (extremos recortados a ±0.98); no aplica aprendizaje ni nueva encuesta.

El informe se reproduce desde un backup exportado:

```text
python -m comfort_pilot.prospective_evaluation backup.json --output report.json
```

Los validadores Android/backend recomputan puntuaciones y comprueban vínculos;
el backend comprueba también el digest de la solicitud congelada. No equivale
a una firma contra manipulación coordinada de un backup.

## Verificación y siguiente decisión

Pruebas unitarias: exclusión temporal, modelos/objetivos separados, reutilización
del ajuste, neutral explícito, puntuaciones comprobadas, casos sin evaluación,
idempotencia y alteración de resultados. El ciclo de dispositivo usa una base
aislada y el trabajo automático real, y valida exportación/importación contra
el backend. Artefactos: `output/d-day-prospective`.

La entrega permite comenzar recogida prospectiva. No acredita validación humana
de la pregunta ni precisión de los posteriores. Quedan pendientes adaptación
temporal, mayor replicación afectiva, calibración, política para contexto nuevo,
evaluación separada de explicación/recomendación y reglas para fases 5–6.
