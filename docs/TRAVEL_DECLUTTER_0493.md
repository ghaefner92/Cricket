# Limpieza visual de Travel — 0.4.93

El autor aplica los puntos 2–7 de la inspección visual de su teléfono, con dos
aclaraciones: quitar el aviso de bus aproximado y conservar el contenido de
clima, metas simuladas y mascota mediante acceso a detalles. No autoriza cambiar
los nombres superpuestos de las estaciones (punto 1).

- Parking oculto por defecto en resultados PT. Interruptor específico de PT en
  los ajustes del mapa; su valor no modifica el parking de auto/otros modos ni
  la vista de búsqueda. Se conservan radio y preferencias existentes.
- “Connections / Verbindungen” como encabezado de las tres tarjetas.
- Resumen PT muestra “Leave in…” junto a información/inicio, sin repetir líneas.
  Llegada permanece en tarjeta y resumen; métricas se conservan.
- Aviso visible de bus aproximado eliminado. Geometría, trazado discontinuo y
  metadatos de procedencia permanecen intactos.
- Franja meteorológica: temperatura, lluvia y viento; botón +/− despliega
  humedad, ráfagas y luz. Todas las lecturas e instrucciones individuales siguen
  accesibles. Home en cuadrícula conserva su presentación completa.
- Metas simuladas y mascota se conservan en el panel del botón de información,
  con la misma evidencia de ruta y Passport. Resumen del mapa reducido de 112 a
  88 dp como máximo. Controles de viaje activo conservan su disposición propia.

No se alteran medidas, horarios, ranking por llegada, Passport ni HOTCO.
91 pruebas JVM (incluye política independiente de parking), APK compilado.
La utilidad de backup ahora comprime el archivo completo antes de verificar
SQLite, reduciendo espacio necesario sin perder bases de datos ni preferencias.
