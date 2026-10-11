# Orion: unidades, ventanas e histórico público

Investigación de solo lectura del 11 de octubre de 2026, solicitada por el autor.
Complementa [D_DAY_IMPLEMENTATION.md](D_DAY_IMPLEMENTATION.md). No modifica
servicios remotos ni activa aprendizaje, Passport o HOTCO. No requiere viajes reales.

## Fuentes y reproducibilidad

- Infrastructure, commit `05ed18dba94958272a733eca41853a56b0aebb5c`:
  [ingesta Bresser](https://github.com/imiq-project/infrastructure/blob/05ed18dba94958272a733eca41853a56b0aebb5c/services/sensors/src/main.go),
  [decoder SenseCAP](https://github.com/imiq-project/infrastructure/blob/05ed18dba94958272a733eca41853a56b0aebb5c/services/sensors/src/sensecap.go),
  [suscripciones históricas](https://github.com/imiq-project/infrastructure/blob/05ed18dba94958272a733eca41853a56b0aebb5c/services/migrate/main.py),
  [infraestructura persistente](https://github.com/imiq-project/infrastructure/blob/05ed18dba94958272a733eca41853a56b0aebb5c/compose.yml).
- [Manual oficial S2120 v1.0.4](https://files.seeedstudio.com/products/SenseCAP/101990961_SenseCAP%20S2120/SenseCAP%20S2120%20LoRaWAN%208-in-1%20Weather%20Station%20User%20Guide.pdf),
  secciones 6.3.2, 6.3.6 y 13.3. Define intensidad mm/h a partir del acumulado
  de diez minutos, intervalo de envío configurable y contador reiniciable.
- [Decoder oficial Seeed](https://github.com/Seeed-Solution/SenseCAP-Decoder/blob/d0a234205fb9574dd03abd49561b60c243c0d1e3/S2120/TTN/SenseCAP_S2120_TTN_Decoder.js).
- [SpaceTime](https://github.com/imiq-project/SpaceTime/tree/21e76c3c632756104ee396b1bd101c9a80de22bf).
- Script local: `validation/audit_imiq_sensor_history.py`; usa consultas GET.
  Evidencia archivada en `output/imiq-sensor-research-2026-10-11/sample-090926/`:
  `summary.json`, `weather-query-list.json`, historiales Walter/Winfred,
  consultas por atributo y fuentes SpaceTime/decoder oficial.
  La muestra es de 24 filas por estación; no audita toda la retención histórica.

## Sí existen secuencias temporales

Orion publica el estado actual. QuantumLeap recoge notificaciones y Crate almacena
el histórico con volumen persistente. El dashboard consulta ese histórico.
Ambas consultas públicas por entidad devolvieron HTTP 200 con índices UTC.

En Winfred se observan grupos a `08:02:31` y `09:02:31 UTC`: temperatura
11.1 → 11.8 °C, humedad 72 → 67 y luz 9770 → 25312. Esto confirma cambios
almacenados; concuerda con la aclaración del autor sobre valores actualizados.
`uplinkInterval=60` y firmware `2.1` constan en la respuesta actual; la cadencia
observada concuerda con envío horario, sin certificar toda su historia.

Cada grupo contiene múltiples filas con valores repetidos y diferencias de
milisegundos. No tratarlas como mediciones independientes. La causa exacta de
esa multiplicación no está demostrada. Conservar filas originales y construir
una vista por evento de adquisición cuando existan identificadores fiables;
agrupar provisionalmente por proximidad temporal no certifica identidad física.

El índice histórico no certifica la hora física de medición. También hay
`dateModified` UTC por atributo, aunque `datetime` publicado no sea fiable.
Un cero de lluvia puede conservar una modificación antigua porque no cambia:
fecha de modificación, fecha de recepción y fecha de medición son distintas.
No reemplazar el reloj publicado por ninguno de estos sin declarar su semántica.
Consultar hoy una entidad tampoco demuestra que todos sus atributos se midieran hoy.

## Lluvia: contratos distintos

| Estación/campo | Hallazgo | Pendiente |
|---|---|---|
| Winfred `rainIntensity` | Decoder divide por 1000; manual: mm/h, acumulado de los últimos diez minutos multiplicado por seis | Verificar correspondencia de ventana con firmware instalado y trazabilidad de cada envío |
| Winfred `cumulativeRainfall` | Decoder divide por 1000; acumulado en mm, reiniciable | Eventos de reinicio/desbordamiento, huecos y continuidad del contador |
| Walter `rain` | Copia de `t1rainra`; probable tasa por nombre del protocolo | Modelo exacto, configuración de unidades y ventana; el repositorio no los certifica |
| Walter `rainHourly`, `rainDaily`, `rainWeekly`, `rainMonthly`, `rainYearly` | Copias de `t1rainhr`, `t1raindy`, `t1rainwy`, `t1rainmth`, `t1rainyr` | Unidades, ventanas móviles frente a calendario, zona horaria y reinicios |

Ejemplo conforme al manual: intensidad 6 mm/h representa 1 mm en sus diez
minutos de referencia. Con transmisión cada 60 minutos, esa muestra no describe
los otros cincuenta minutos. No multiplicar esa tasa por una hora para obtener
el total horario. Las diferencias de un contador acumulativo válido permitirían
estimar el total entre dos lecturas, pero no localizar la lluvia dentro del intervalo.
No activar conversiones retrospectivas antes de confirmar el contrato instalado.

La configuración publicada de suscripciones incluye `rain`, pero omite
`rainIntensity`, `cumulativeRainfall` y los acumulados de Walter. En la consulta
actual: Walter `rain` tiene histórico HTTP 200; Walter `rainHourly`, Winfred
`rainIntensity` y Winfred `cumulativeRainfall` devuelven HTTP 404. El histórico
de Winfred contiene `rain` nulo. Esto demuestra ausencia en el servicio/ruta
consultados, no que nunca se hayan conservado en otro almacenamiento.
Corregir suscripciones futuras no recupera automáticamente datos anteriores.

## Otros hallazgos que afectan la interpretación

1. **Luz de Walter:** `lightIntensity` procede de `t1solrad`. Es un indicio de
   radiación solar; no basta para certificar lux. Winfred mide iluminación en lux.
   La inferencia previa de lux para Walter requiere revisión antes de combinar
   ambas estaciones. No aplicar una conversión constante de W/m² a lux.
   El conteo anterior Walter 6/7 no certifica comparabilidad de esas seis variables.
2. **Temperaturas negativas:** el decoder Go interpreta temperatura con
   `Uint16`, mientras el oficial contempla signo. Para los bytes `FF CE`, el
   cálculo Go produce 6548.6 y la interpretación con signo produce −5.0 °C.
   Es un defecto potencial demostrado en la transformación del código publicado,
   no evidencia de que una lectura negativa concreta de producción se haya corrompido.
3. **Metadatos perdidos:** el webhook TTN modela `received_at` y metadatos de
   radio, pero la salida simplifica a identificación/payload. Conservar recepción
   upstream, contador de paquete, firmware, estado/error y atributos efectivamente
   presentes permitiría comprobar adquisición y duplicados. El decoder descarta
   el código de error 0x06; actualizaciones parciales pueden dejar valores anteriores.
4. **Historia meteorológica incompleta:** la suscripción también omite campos
   propios de Winfred como `barometricPressure` y `peakWindGust`. No asumir
   cobertura histórica de una variable porque exista en la entidad actual.
5. **SpaceTime:** el lector continuo examinado recibe un argumento temporal
   pero consulta Orion actual; el fallback usa clima actual de Open-Meteo.
   Su descripción de interpolación no demuestra reconstrucción histórica:
   distinguir sensor, proveedor meteorológico y estimación. No reconstruir una
   predicción pasada con datos actuales o posteriores a la elección.

## Prioridad para D-Day

1. Contratos por estación/atributo: magnitud, unidad, ventana, origen, firmware y
   reloj. Revisar luz Walter; mantener lluvia fuera de la covariable mm actual
   hasta decidir cómo representarla y confirmar unidades/ventanas.
2. Auditor histórico por atributo: cobertura, huecos, actualizaciones parciales,
   duplicados, límites físicos, reinicios y coherencia intensidad/acumulado.
   Un contador descendente exige marcar discontinuidad; no inventar lluvia negativa.
3. Separar antigüedad del caché de antigüedad de adquisición: la política actual
   de recepción de 30 minutos controla reutilización de la consulta, pero no
   prueba muestreo menor de 30 minutos. No aumentar ese límite para esconder
   el envío horario; añadir cadencia y edad upstream cuando puedan verificarse.
4. Preparar corrección de suscripción y decoder en código revisable; desplegar
   cambios del proveedor exige acceso y autorización propios. Esta investigación
   no modifica infraestructura externa ni envía mensajes al proveedor.
5. Incorporar el histórico verificado a análisis temporales con corte previo a la
   elección, manteniendo snapshots originales y contexto desconocido explícito.

Estos controles validan transporte, codificación y coherencia de datos. La
exactitud física requiere referencias/calibración y condiciones de instalación;
el histórico por sí solo no la demuestra ni constituye exposición personal del viaje.
La adquisición de recorridos reales sigue pospuesta según lo acordado.
