# Mapa de movilidad — 0.4.72 / 83

Entrega autorizada: símbolos de aparcamiento y zonas de espera, controles de
visibilidad/radio, conexiones desde una parada y planificación de un itinerario.
El autor precisa que la persona espera en el andén o vereda, no sobre los rieles.

## Interacción

- P azul rectangular y H verde circular; agrupación separada al alejar.
- Configuración en el mapa: aparcamientos, paradas, posición GPS disponible,
  aparcamientos restringidos y radio 50–2000 m, inicial 200 m. Ajustes persistidos.
- El radio visual se aplica alrededor del origen, destino y GPS disponible.
  Incluir GPS no inicia seguimiento. La propuesta de parking mantiene su política
  independiente de 2 km; no cambiarla silenciosamente al cambiar el radio visual.
- Zonas de espera vinculadas al itinerario permanecen destacadas. Los puntos
  GTFS sin plataforma identificada no generan un H sobre los rieles.
- Ventana inferior con encabezado fijo y contenido desplazable: parking y
  selección en ambos extremos; paradas, destino, ahora/+30/+60 minutos,
  actualización, salidas, llegadas, líneas, dirección del tramo, transbordos,
  caminata, pasos y botón de planificación. Diferencia horario de datos en vivo.
- Planificar busca rutas y conserva parada GTFS, viaje y salida concretos.
  El seguimiento requiere la acción separada de inicio de viaje.

## Zonas de espera y referencias del vehículo

La instantánea compartida contiene 1125 elementos de paradas: 770 plataformas
y 355 stop_position. Los stop_position quedan excluidos de los símbolos H,
incluso si también incluyen highway=bus_stop. Las plataformas pueden estar en
veredas, islas o estaciones. No exigir una vereda de calle cuando existe un andén.
Los símbolos genéricos bus/rail/parking de las teselas se excluyen para evitar
duplicación y para que la configuración controle el overlay interactivo.

Se añaden 1249 pares stop/platform procedentes de miembros consecutivos con
roles de las relaciones OSM de rutas bus/tram/trolleybus. El catálogo GTFS
conserva sus coordenadas originales y añade waiting_platform solo cuando hay
un vínculo único: IFOPT explícito, coincidencia <=3 m sin alternativa cercana,
o punto GTFS <=15 m del stop_position inequívoco y plataforma única en las
relaciones (margen de 5 m respecto del segundo candidato). Son reglas técnicas
conservadoras, no una certificación del sentido ni de accesibilidad física.

Consulta local: 733 paradas GTFS, 392 con plataforma asociada. No asignar una
vereda simplemente por cercanía o nombre. Una plataforma puede reunir varios
IDs GTFS; la ventana combina solo sus conexiones vinculadas. Una parada sin
asociación clara se muestra como zona de espera sin atribuirle horarios ajenos.
Las paradas provisionales pueden carecer de una plataforma en la instantánea.
Consulta HTTP desde la plataforma way/35917677 (Willy-Brandt-Platz): no hay
itinerarios compatibles con el destino/límite/horario del caso probado. Mostrar
estado vacío, no trasladarle servicios de City Carré por cercanía.

La línea de vehículo conserva su extremo GTFS sobre la vía cuando corresponde;
el H representa el andén. No desplazar una línea del tranvía a la vereda ni
dibujar un segmento peatonal inexistente entre ambos. El acceso peatonal procede
del proveedor de rutas; una plataforma asociada no certifica el acceso exacto.

## Integridad de planificación

OTP recibe fromPlace con el ID GTFS; se filtra además la primera parada de
embarque porque consultar desde una parada puede devolver otras cercanas.
GraphHopper aporta el acceso desde el origen personal. Se incluyen tiempo y
distancia de caminata, se respeta el límite total y se descartan salidas que ya
no permiten llegar. La salida del origen se deriva del horario menos el acceso;
no contar dos veces la espera inicial. Seleccionar un viaje filtra su trip ID y
hora programada; no sustituirlo silenciosamente por otro servicio.

El momento de planificación futuro se guarda separado del datetime de captura
de contexto actual. Passport/HOTCO, escalas y aprendizaje permanecen intactos.
Errores parciales al consultar servicios de una misma plataforma se indican.

Extremos PT a <=2 m se normalizan por redondeo de la polilínea; >25 m se rechaza.
No inventar conexiones en separaciones intermedias. Sin shapes.txt, la geometría
del vehículo sigue APPROXIMATE_NO_SHAPES y se conserva su presentación discontinua.
También se conserva el acceso de origen desconocido de 62 m del caso de estación.

## Verificación

67 pruebas JVM, 9 de paradas/itinerarios, 7 de acceso auto–pie y 4 de parking.
Compilación y APK instalados. Catálogo HTTP real confirmado: 733/392.
Consulta real produjo cinco itinerarios y la búsqueda completa conservó un
único PT con parada gtfsde:108972 y viaje gtfsde:1932490. Su geometría comenzó
exactamente en las coordenadas GTFS. Esta parada provisional no tiene un andén
OSM asociado: la prueba comprueba selección exacta, no valida una zona de espera.

En teléfono se inspeccionaron configuración inicial 200 m, ajuste/aplicación,
agrupaciones, zoom, parking y ventana de zona de espera. La inspección inicial
detectó varios IDs en una plataforma y dio lugar al agregado por plataforma.
La selección completa desde un listado con servicios y su desplazamiento en
teléfono requieren una comprobación adicional; no declarar experiencia real.
No iniciar GPS ni introducir puntuaciones para validar esta entrega.

Límite previo independiente: forecast prospectivo puede responder 413 en
búsquedas grandes; esta entrega no lo corrige ni implica aprendizaje evaluado.

Referencias: [OSM platform](https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dplatform),
[OSM stop_position](https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dstop_position),
[OTP plan](https://docs.opentripplanner.org/api/dev-2.x/graphql-gtfs/queries/plan),
[Material bottom sheets](https://m3.material.io/components/bottom-sheets/overview).
