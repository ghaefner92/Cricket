# Journey history and companion explanations — 0.4.101 / 112

Corrección del 12 de octubre de 2026, solicitada porque Journey history no
mostraba correctamente el registro de viajes y la explicación del compañero.

## Causas y corrección

`historyPreviews` construía primero cada JSON completo y después lo reducía.
Esto volvía a cargar archivos de simulación y solicitudes prospectivas grandes
en el heap Java. La base del teléfono contiene 52 búsquedas y unos 560 MB de
registros. Ahora SQLite proyecta únicamente lugares, elecciones e indicadores
de viaje antes de devolver filas a CursorWindow/JVM. Los registros con viajes
aparecen primero, después elecciones y finalmente búsquedas sin elección.
Los viajes/experiencias y sus controles son visibles sin abrir otro desplegable;
hay un acceso explícito `Companion explanation` para las elecciones guardadas.

La lectura de UI omite el archivo de trayectorias y la solicitud prospectiva;
la de explicación omite además captura GPS, experiencias, trabajos y pronósticos
ajenos a la interpretación de esa elección. Conserva el Passport original,
la selección, las alternativas y todos los diagnósticos usados por la explicación.
Los originales permanecen en SQLite. Guardar una explicación usa `json_set`
en una transacción, sin materializar el documento grande en Java; repetir la
misma respuesta no duplica la entrada.

El backend de explicación todavía exigía el reparto histórico de diez puntos.
Ahora reconoce importancia ordinal independiente 1–7, comprueba las respuestas
crudas y `(r−1)/6` contra el Passport de decisión y conserva su escala. No crea
un presupuesto ficticio ni interpreta importancia como saliencia focal. La rama
histórica de reparto de diez puntos conserva su contrato. La plantilla usa
referencias a `ratings` para las respuestas ordinales; 1 no se presenta como
prioridad positiva. Las respuestas históricas no se reetiquetan.

## LLM y estado del proveedor

Groq está configurado y activado, pero la consulta real respondió HTTP 403 con
`Access denied. Please check your network settings.`. Esto no demuestra ausencia
de clave ni que la clave sea válida. El endpoint devuelve una explicación
verificada con `TEMPLATE_FALLBACK`, sin afirmar generación por LLM. La UI muestra
si la narración AI está disponible o si se utiliza la explicación de resultados
guardados. Una explicación fallback se vuelve a consultar al abrirla para poder
recuperar la narración cuando se resuelva el proveedor; `Refresh story` también
fuerza la consulta. No se cambiaron claves ni servicios externos.

## Verificación

- 108 pruebas JVM aprobadas y APK principal/de pruebas compiladas.
- Tres pruebas backend nuevas de importancia/historia y cinco de revisión diaria.
- Dos pruebas instrumentadas aprobadas en el teléfono: proyección SQLite,
  preservación de trayectoria/registro al guardar y deduplicación de explicación,
  lectura de historial real y solicitud verificada al backend.
- Antes de priorizar viajes, 50 entradas reales se cargaron en 2.801 ms; había
  once elecciones y tres registros de viaje en esas entradas. La solicitud de
  explicación proyectada pesó 3.745.067 bytes y obtuvo HTTP 200, fallback verificado.
- Copia privada comprimida de SQLite/WAL/SHM previa a la instalación bajo
  `.local-backups/history-repair/`; no se borró historial ni se registró una
  Experience ficticia en la base del usuario.

APK 0.4.101 instalada con actualización conservando datos; backend recargado.
Las pruebas instrumentadas usan una base temporal para los casos sintéticos y
lectura de la base real para la comprobación funcional. No adquieren GPS ni
activan posteriores sobre Passport/HOTCO.
