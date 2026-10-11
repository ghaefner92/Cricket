# Importancia independiente de necesidades — 0.4.48

El editor conserva fuentes SNES, filas, controles +/− de 48 dp, avatar reactivo,
luces y mascotas animadas. Sustituye el presupuesto de diez puntos por once
valoraciones independientes de 1 a 7. La leyenda muestra todos los niveles y los
extremos «Not important» / «Very important» en inglés y alemán.

Las metas sin responder muestran un guion. El cero es exclusivamente un estado
local de respuesta pendiente: nunca se transmite al cuestionario. Guardar exige
las once respuestas; cada control respeta los límites 1 y 7 sin afectar otra meta.
La barra usa `(r−1)/6`, por lo que 1 es el extremo vacío y 7 el completo.

## Contrato y persistencia

La app vuelve a enviar `responses.needs` con once respuestas originales 1–7,
en lugar de `responses.needs_allocation`. El backend conserva su transformación
independiente `(rating−1)/6`, sin normalización por la suma. No cambian las
ecuaciones, el mecanismo de inicialización del motor, ni las escalas de creencias o
valencias. La propuesta de prioridad persistente no se implementa aquí.

El plan tiene versión 2 y marcador `ordinal_importance_1_7`; el campo interno
`points` se mantiene por compatibilidad con vistas e historial, pero ahora almacena
respuestas ordinales. El Passport nuevo guarda `goalRatings`, `needsSource` y
`conversion=rating_minus1_div6_v1`. Los consumidores admiten el historial anterior.

Los repartos antiguos no se convierten en valoraciones inventadas: el editor pide
nuevas respuestas. Los Passports ya confirmados siguen disponibles; para que un
perfil existente use esta medición hay que renovarlo y confirmar el nuevo Passport.
El nuevo payload invalida por fingerprint las calibraciones pendientes anteriores.

## Validación

- Compilación de APK principal e instrumentación y 16 pruebas unitarias Android.
- Prueba en dispositivo del editor: respuestas independientes, límites, guardar,
  desplazamiento, aprobación/negación, cuatro avatares y movimiento reducido.
- Prueba de contrato en dispositivo: start → complete → bootstrap contra el
  backend local, todos los niveles 1–7, necesidades y pesos de ruta `(r−1)/6`,
  suma mayor que uno y procedencia `needs_raw_1_to_7`.
- Compatibilidad: presupuestos anteriores no se reinterpretan; respuestas
  incompletas no pueden enviarse. Las pruebas usan estado y bases aislados.
