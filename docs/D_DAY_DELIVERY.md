# D-Day — entrega de fases 1–3

## Auditoría y decisiones

| Elemento auditado | Estado encontrado | Resultado de esta entrega |
|---|---|---|
| Metas del día | Medición fechada, ordinal 1–7, `(r−1)/6`, revisiones y base de estimación congelada | Se conserva; contrato teórico documentado |
| Inicialización de creencias | Ridge convencional congelado; cuatro preguntas fijas y 40 celdas estimadas | Se distingue de aprendizaje bayesiano; no se sustituye en fases 1–3 |
| Preguntas iniciales | `pt__comfort`, `walk__comfort`, `bike__privacy`, `car__reliable` | Selección fija derivada de entrenamiento; no adaptativa a respuestas previas |
| Utilidad de preguntas | Valor del artefacto más bonificaciones de cobertura | El código del generador de esos valores no está disponible: no atribuirles una interpretación bayesiana |
| Contexto | El orquestador todavía podía aplicar fuerzas experimentales F3 | App y backend pasan a `observations_only`; conservan contexto sin fuerza añadida. Reproducción histórica requiere política explícita |
| Simulación | Trayectorias completas de 19 estados, parámetros, topología y archivo deduplicado | Referencias y dimensiones comprobadas; tiempo interno separado del reloj real |
| Elección/viaje/Experience | IDs, rutas, contexto a la selección y confirmación transaccional/idempotente | Se conserva y enlaza en un índice longitudinal exportable |
| Passport antes/después | Solo se guardaba la última referencia de evidencia de cada búsqueda | Cada Experience añade su propia transición y archiva ambos extremos |
| Exportación | Lecturas independientes de Passport e historial | Un único snapshot SQLite para evitar una mezcla de versiones |
| Archivos de Passport | Una identidad podía sobrescribirse | Revisiones archivadas inmutables; conflicto explícito para contenido distinto con el mismo ID |
| Presentación de recomendación | Se conservaba la recomendación calculada | Se declara `not_instrumented`; no se afirma que fue vista por el usuario |
| Registro central/ML/Bayes | No implementados en este flujo | Continúan pendientes; validación del backend es de solo lectura |

Archivos principales: `DailyPassportState.kt`, `Data.kt`, `TripRepository.kt`,
`LongitudinalEvidence.kt`, `daily_passport.py`, `contextual_deliberation.py`,
`routing_bridge.py`, `d_day_evidence.py` y contrato `d-day-longitudinal-v1.schema.json`.

## Registro longitudinal y uso

El botón existente de exportar datos produce `cricket-backup-v1` con una nueva
sección `longitudinal`, versión `d-day-longitudinal-v1`. Las trayectorias grandes
permanecen en sus registros originales: el índice apunta a ellas sin duplicarlas.
El mismo archivo contiene Passport, fuentes de contexto/rutas, salida de la
simulación, recomendación, elecciones, GPS/eventos y feedback disponible.

Cada `passport_transition` identifica el evento de Experience y las versiones
antes/después de confirmarlo. El Passport de la decisión sigue siendo una
referencia distinta: puede haber una nueva medición diaria entre elegir y
confirmar. La revisión de evidencia no altera el perfil cognitivo ni sustituye
las declaraciones por estados simulados. Se comprueba también que el feedback
coincida con la evidencia del Passport y que el historial previo se conserve.

La importación verifica el índice contra sus fuentes antes de escribir. Los
backups anteriores siguen siendo compatibles. Los registros históricos sin
transición, trayectoria completa o snapshot generan advertencias explícitas;
no se reconstruyen datos no observados. No existe garantía contra manipulación
coordinada del archivo: son comprobaciones estructurales, sin firma criptográfica.

Validación opcional de solo lectura:

```text
POST /api/dyconet/d-day/validate
Body: archivo completo de backup JSON
```

Validación offline, sin límite HTTP de 64 MiB:

```powershell
python app-backend/services/dyconet/d_day_evidence.py ruta/al/backup.json
```

El almacenamiento sigue en el dispositivo. Borrar datos/historial mediante las
acciones explícitas de la app sigue siendo posible: exportar antes si se desea
conservarlos para investigación. La confirmación transaccional y la exportación
consistente no equivalen a una política de retención permanente.

## Validación de entrega

Entrega completada el 9 de octubre de 2026, Android **0.4.58** (versionCode 69),
instalada en el teléfono RZCY70K63HV. Backend activo en el puerto 8077, con
`observations_only` y F3 desactivado por defecto.

- 28 pruebas JVM Android: cero errores/fallos, incluidas cinco de D-Day.
- 23 pruebas backend: cero errores/fallos, incluidas seis de D-Day.
- Cuatro pruebas en el teléfono: ciclo diario completo con backend real,
  exportación/validación/restauración, snapshot consistente bajo escrituras
  concurrentes, archivo inmutable y pausa concurrente sin bloqueo SQLite.
- Fixture compartido Kotlin/Python y validador offline: válido. Sus datos
  deliberadamente antiguos no incluyen simulación y generan la advertencia
  correspondiente; el test de ciclo real sí conserva 2001 muestras de 19 estados.
- APK principal y APK de instrumentación compilados correctamente.

Las pruebas usan perfiles sintéticos y bases aisladas, sin modificar el Passport
personal. Verificar consistencia de datos no valida la teoría HOTCO-CT ni acredita
un aprendizaje bayesiano todavía pendiente.
