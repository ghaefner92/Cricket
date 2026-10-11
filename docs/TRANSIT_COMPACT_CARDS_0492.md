# Tarjetas PT compactas — 0.4.92

El autor informa tarjetas grandes y ausencia de la tercera alternativa. Solicita
colores distintos con estilo JRPG, etiqueta de llegada y símbolos de vehículo.

La inspección del teléfono mostró dos tarjetas de 281 px de alto, ocupando la
fila completa. El código anterior descartaba salidas distintas por compartir
patrón de líneas/paradas; esto puede reducir las tres opciones a dos. Se cambia
la deduplicación a identidad de itinerario del proveedor (o identidad de tarjeta
si falta), conservando distintas salidas sobre las mismas líneas. Hasta tres
itinerarios disponibles/no vencidos, ordenados por llegada; no fabricar una
tercera opción si faltan tres salidas utilizables.

Confirmación posterior mediante backup íntegro del teléfono: búsqueda de las
20:58:50 Europe/Berlin con cuatro identidades OTP distintas, dos salidas del
73 y dos de 6 → 48. El agrupamiento anterior reducía esas cuatro a dos tarjetas.
La copia verificada conserva 40 búsquedas y Passport revisión 9; APK 0.4.92
instalado con reemplazo, sin desinstalar.

Diseño: altura mínima 72 dp en lugar de 100, medallas 18 dp en lugar de 24,
espaciado compacto, marcos y fondos suaves verde menta/violeta/ámbar. Marco más
marcado para selección. Fila de líneas con símbolo real bus/tram/train para cada
tramo; conexiones mixtas conservan ambos tipos. La fila de líneas puede
desplazarse horizontalmente si supera el ancho. Cada tarjeta dice Arrival/Ankunft
y la hora, más símbolo de caminar y minutos iniciales. Se conserva la llegada
del resumen principal según petición. Texto compacto sans serif para lectura;
medallas y paleta JRPG se mantienen.

90 pruebas JVM aprobadas: regresión que retiene tres salidas aunque dos usen
las mismas líneas, y eliminación de duplicados de la misma identidad del
proveedor. APK compilado. No cambia horarios, rutas, Passport ni HOTCO.
