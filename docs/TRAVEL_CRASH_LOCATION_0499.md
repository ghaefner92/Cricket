# Cierre, permiso y controles — 0.4.99

Logcat conservado en .local-backups/crash-0498.txt. Cierres recientes:
11/10 21:54:08 y 21:55:48, OutOfMemoryError, límite Java de 256 MiB;
Store.largeText → record → forEachResearchRecord → ProspectiveLearning
(resume/enqueue). La lectura creaba buffer expandible, copia completa de bytes,
String UTF-16 y árbol JSON de cada búsqueda incluso sin experiencias.
En el respaldo existen búsquedas de 15,4 millones de caracteres, aproximadamente
la mitad resultado y mitad solicitud de pronóstico congelada con contexto repetido.

Store.record ahora parsea directamente un Reader UTF-8 sobre fragmentos SQLite
de 256 KiB, sin texto/bytes completos intermedios. Conserva snapshot transaccional
y todos los campos en lecturas destinadas a mutación/contrato.
forEachResearchRecord omite result, solicitud de pronóstico y tracking, campos
que sus consumidores no utilizan para construir evidencia. Conserva Passport,
selección, experiencia, plan/contexto y clasificación. La proyección no se guarda.
UI omite la solicitud de forecast; resultados y contexto siguen disponibles.
Enqueue comprueba cabecera antes de cargar tareas finalizadas o fallidas.
Ningún dato original se borra o reetiqueta; no se cambian criterios de aprendizaje.

Android: location_mode=3, pero ACCESS_FINE_LOCATION y COARSE_LOCATION
granted=false con USER_FIXED. Activar ubicación global no concede permiso a la
app. El botón prioriza permiso antes de servicios; permiso bloqueado ofrece
ajustes de aplicación y continuar sin GPS. Play ya no salta directamente al modo
sin GPS al denegar sin mostrar esta opción de recuperación. Android requiere
interacción del usuario para concederlo; no se fuerzan permisos mediante ADB.

Sin GPS reutiliza RouteSummaryPanel y RoutePlayButton/RoutePauseButton/
RouteStopButton originales: pausa/reanuda, finalizar abre Experience; pulsación
larga de Stop cancela reproducción. No se persisten puntos simulados ni se
confirma automáticamente experiencia. Sin cabecera duplicada; mantiene
estado explícito Route playback · No GPS. Controles GPS originales conservados.

Pruebas nuevas: JSON completo Unicode/escapes/números/null, proyección de evidencia,
proyección UI, campo omitido de 30 MB mediante Reader de memoria acotada y error
de registro incompleto. No se aceptan viajes ni se cambian permisos del autor
durante validación. Revisión interactiva de autorización Android pendiente.

El disco del ordenador se llenó durante la extracción del respaldo. Archivo
comprimido completo verificado y extracción incompleta eliminada; memoria de
proyecto y fuente afectadas al escribir se recuperan de la sesión antes de compilar.

Entrega: assembleDebug y 102 pruebas JVM correctas, cero fallos/errores/exclusiones.
Respaldo 20261011-220206 verificado con quick_check, 48 búsquedas, Passport
0504e5fa-b367-5835-8e36-890f4a08b68a revisión 9. install -r correcto;
versión comprobada 0.4.99, código 110. El permiso bloqueado se mantiene hasta
que el autor lo conceda mediante Android; no se acepta un viaje para validarlo.

Arranque observado: PID 26981 se mantiene, sin entradas de crash para ese
proceso tras la observación. Java Heap PSS inicial 28.104 KiB y tamaño privado
48.832 KiB. Esto verifica arranque/revisión del historial; no certifica todavía
el recorrido GPS ni todas las interacciones de la pantalla de viaje.
