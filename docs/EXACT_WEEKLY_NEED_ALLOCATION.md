# Reparto exacto de necesidades — 0.4.47

La app envía el reparto original en `responses.needs_allocation`, con esquema `weekly_need_allocation_1.0`, presupuesto 10 y los 11 puntos por clave canónica. El backend valida enteros de 0 a 10, claves completas y suma exactamente 10; calcula cada peso como `points / 10.0`. No convierte a Likert ni redondea.

Ejemplo: 1 punto → 0,1; 2 → 0,2; 3 → 0,3; 4 → 0,4. Los pesos suman 1 dentro de la precisión numérica de coma flotante. Cero puntos da peso cero.

El reparto se conserva en la procedencia de onboarding, en las mediciones observadas del Passport y en las actualizaciones explícitas de creencias y valencias. Las necesidades del perfil y `routing_parameters.need_weights` usan los mismos pesos. No se fabrican respuestas `needs_raw_1_to_7` para este formato. Los cambios en pesos también entran en la personalización de creencias antes de HOTCO; el estimador y la dinámica HOTCO permanecen sin cambios.

Los contratos previos de necesidades enteras de 1 a 7 siguen admitidos con su fórmula original. Ambos formatos son mutuamente excluyentes. Para un reparto de presupuesto no se generan micro-preguntas que cambien una necesidad individual en escala 1–7, ya que romperían la suma de 10; los puntos se cambian mediante una nueva distribución completa. Las micro-preguntas de creencias y valencias conservan el reparto exacto.

Los nuevos Passports guardados por Android llevan `conversion: points_div10_exact_v2`. Los Passports confirmados y búsquedas históricas existentes no se reescriben: la conversión nueva se aplica al generar o renovar y confirmar el Passport. Una calibración pendiente con la huella anterior debe volver a prepararse con las entradas actuales.

## Validación

- 12 pruebas Python aprobadas: seis específicas de reparto exacto, cuatro de compresión y dos de reutilización de simulaciones.
- El recorrido específico comprueba todos los valores de 0 a 10, rechazo de presupuestos/valores/claves inválidos, entradas ambiguas y pesos alterados, generación y serialización de Passport, reconstrucción longitudinal, actualización explícita de creencias y compatibilidad con la escala antigua.
- 16 pruebas unitarias Android aprobadas y compilación del APK principal y de pruebas correcta.
- Tres pruebas en Samsung aprobadas: conservación de todos los valores para las 11 claves (121 repartos), rechazo de presupuestos inválidos y petición real desde Android a `/start`, `/complete` y `/bootstrap`, con verificación de cada peso final en perfil y rutas y procedencia sin respuestas 1–7 inventadas.
- La prueba de red usa respuestas sintéticas y almacenamiento de prueba aislado; no cambia el Passport del usuario.
- Backend reiniciado en el puerto 8077, `/health` correcto y versión 0.4.47 (58) instalada en el celular.
