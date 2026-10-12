# Travel: ubicación en vivo y centrado — 0.4.104

El autor indica que tiene permisos pero el botón no muestra su posición; además
se encuentra en movimiento y el avatar queda fijo. Aclara que el botón debe
servir exclusivamente para centrar el mapa en la posición actual, no abrir
ajustes ni gestionar un interruptor global. Esta decisión sustituye para ese
botón el comportamiento descrito en TRAVEL_LOCATION_FLOW_0498.md.

## Corrección

El botón solicita acceso Android solo si falta permiso o servicios; con ambos
disponibles centra el mapa sin salir de la app. Restablece seguimiento después
de un gesto manual. No hay pulsación larga para ajustes. La diagonal roja sigue
indicando falta de permisos/servicios, sin inferir permisos del interruptor global.
El centrado no modifica A/B ni empieza un viaje. Se conserva el comportamiento
previo de proponer el primer fix como origen únicamente cuando el origen está
vacío al entrar en la planificación.

Antes la planificación solicitaba un único fix y después esperaba cancelación.
Ahora escucha GPS y red mientras el mapa permanece RESUMED, solicitando un
intervalo de 1 s sin distancia mínima. La posición y el avatar se actualizan al
recibir observaciones reales; el intervalo solicitado no garantiza que Android
entregue un fix nuevo cada segundo. Precisión y disponibilidad dependen del
proveedor, cobertura y entorno. No se inventan movimientos entre lecturas.

La búsqueda y los mapas de resultados comparten mapLocations. El mapa puede
desplazarse a mano sin interrumpir el cursor en vivo; el botón vuelve a centrarlo.
Los controles existentes de grabación siguen independientes. Este flujo de
visualización no escribe puntos en SQLite, no crea Journey/Experience ni aporta
exposición ambiental a aprendizaje. El tracking explícito conserva su servicio.

Se rechazan coordenadas/precisión inválidas, fixes futuros, fuera de orden o de
más de 30 s. Durante cinco segundos se evita que un eco con precisión más de
dos veces peor sustituya inmediatamente una lectura GPS más precisa. Esa regla
de visualización no altera la captura de viajes. La escucha se cancela mediante
awaitClose/removeUpdates al dejar RESUMED o desmontar la pantalla. Si no llega
un primer fix en 25 s, la planificación avisa y sigue escuchando mientras está
visible, en lugar de cancelar y quedarse con un avatar fijo indefinidamente.

## Comprobación

Permisos FINE y COARSE concedidos, servicios globales activos en el Samsung.
APK 0.4.104 / código 115 compilada e instalada con reemplazo. Suite JVM aprobada,
incluidas dos pruebas nuevas de validez/antigüedad de posiciones.

`validation/verify_live_map_location.py` usa el teléfono real sin inyectar GPS,
pulsar Play, buscar rutas ni confirmar experiencias. Captura dos momentos,
desplaza el mapa y pulsa centrar. En la comprobación, Android entregó sucesivas
observaciones GPS con tiempos y coordenadas distintos; cambiaron las posiciones
del avatar entre capturas. Tras centrar, el avatar volvió al centro y la actividad
de Cricket permaneció abierta. No es una evaluación de precisión absoluta del GPS.

Artefactos privados: `.local-backups/location-04104/verification.json`, capturas
live-1/live-2/panned/recentered y volcados de ubicación. Los volcados contienen
ubicaciones reales; no se incorporan al repositorio ni se exportan al proveedor LLM.

Recomprobación del 12 de octubre: la suite JVM sigue aprobada y el teléfono
entrega lecturas GPS con tiempos y coordenadas cambiantes. En esta repetición,
el avatar conserva sus coordenadas de pantalla mientras está centrado: la cámara
sigue su posición. Después del desplazamiento manual aparece fuera del centro
y el botón lo devuelve al centro sin abrir ajustes. Por tanto, comparar solo
los bounds del avatar entre dos capturas centradas no comprueba si el GPS cambia;
el script conserva también los bounds después del desplazamiento manual.

Archivos: Location.kt, TravelPlanningUi.kt, NativeMap.kt y MapLocationFreshnessTest.kt.
