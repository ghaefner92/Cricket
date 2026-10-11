# D-Day — auditoría de atributos contextuales

Actualización posterior 0.4.62: contratos temporales confirmados por el autor,
auditoría real de Orion y captura ambiental durante viajes en
[D_DAY_PHASE4_CLOSE.md](D_DAY_PHASE4_CLOSE.md). Los pendientes descritos aquí
corresponden al estado de esta primera entrega.

Entrega de fase 4, 10 de octubre de 2026. Backend y laboratorio; UI pendiente.

## Resultado

Cada predicción del piloto y del flujo prospectivo incluye `context_audit`.
El resultado del análisis incluye `observation_context_audits`, identificadas
por evento y modo, para comodidad y afecto. No se modifican las declaraciones,
el Passport activo, HOTCO, los priors ni las tasas temporales candidatas.

La auditoría conserva la lectura, unidad, fuente, entidad, timestamp y metadatos;
informa inclusión/exclusión, antigüedad al consultar, unidad esperada, problemas
semánticos y el vector codificado con sus columnas. No convierte unidades por
suposición. Se mantienen las siete variables meteorológicas, sin UV, y la
duración planificada como predictor identificado explícitamente.

Valores fuera del dominio (humedad fuera de 0–100, magnitudes no negativas con
valor negativo) quedan ausentes con motivo registrado, sin invalidar las otras
lecturas. Se mantienen los rechazos por duplicados y observaciones futuras.
Una lectura ausente no equivale a cero: se conserva el indicador de ausencia.
El límite de antigüedad existente de 30 minutos es una regla operativa del
piloto, no una garantía científica ni una regla específica validada por sensor.

## Cobertura espacial y significado

El adaptador registra número de tramos, fracción de tramos con lectura y
lecturas originales por atributo. Si falta un atributo en algún tramo, no se
usa como contexto de toda la ruta. Si hay lecturas diferentes, no se crea un
promedio sin pesos de exposición. Una misma lectura compartida por todos los
tramos produce un único predictor, no múltiples observaciones independientes.
La fracción representa cobertura por número de tramos, no por tiempo/distancia.

Se conserva la procedencia de la unidad: atributo del sensor, contrato del
proveedor o desconocida. Los metadatos originales de Orion no se completan con
periodos de medición inventados. `measurement_semantics` y
`measurement_window_seconds`, cuando existen en `source_metadata`, permiten
documentar significado y ventana. Lluvia, ráfagas y viento de diez minutos
requieren una ventana positiva; una ventana distinta de 600 segundos para este
último genera una advertencia semántica. No se deduce una ventana de lluvia a
partir de `mm`. La auditoría preserva los metadatos espaciales disponibles sin
afirmar que representan la exposición personal.

`semantic_metadata_complete` solo indica presencia/coherencia básica de esos
metadatos para lecturas incluidas; no certifica al proveedor. Una lectura con
unidad y valor válidos puede seguir entrando al modelo exploratorio aunque su
semántica esté pendiente. Por tanto, esta entrega hace visible el problema,
pero no garantiza comparabilidad entre acumulaciones de lluvia de periodos
distintos. La activación del aprendizaje permanece pendiente.

El contexto sigue siendo el snapshot congelado de búsqueda/selección.
`actual_trip_exposure_reconstructed=false`: no se integra todavía una serie
de sensores durante el viaje ni se sustituye la duración prevista por la real.
Si cambia el modo, no se le atribuye el contexto de la ruta elegida originalmente.

## Uso offline

Desde `app-backend/services/dyconet`, una solicitud del piloto puede auditarse
sin ajustar posteriores ni consultar Orion:

```powershell
python -m comfort_pilot.context_audit solicitud.json --output auditoria.json
python -m comfort_pilot.context_audit solicitud-afecto.json --target affect --output auditoria-afecto.json
```

## Verificación y pendiente

Suite backend: 60 pruebas aprobadas, incluyendo seis pruebas de auditoría.
Cubren unidades incompatibles, dominios inválidos, antigüedad, UV, lectura cero,
ventana de lluvia, lecturas futuras/duplicadas, cobertura parcial, discrepancias
por tramo, sensor compartido, procedencia de duración y ambos objetivos.
El flujo prospectivo verifica la presencia de auditoría para ambos objetivos.

Estas pruebas emplean fixtures; no constituyen una auditoría en vivo de Orion.
Antes del piloto queda contrastar contratos reales por entidad/atributo,
documentar periodos de acumulación/promedio y determinar una política de
comparabilidad. También quedan medir exposición real durante el viaje y
validar umbrales específicos por sensor; no se simulan como datos observados.
