# Companion: reflexión longitudinal — 0.4.103

El autor autoriza sustituir el acceso habitual a Cognitive Passport por una
tarjeta Companion: avatar elegido y reflexión en primera persona, sin HOTCO,
Passport, nombres de modelos ni paneles técnicos en esa tarjeta. Solicita
después fuente SNES al mismo tamaño y emojis. Se conservan bodyLarge/bodySmall
y se usa PixelFont; apertura y prioridades incorporan dos emojis como máximo.

La navegación ahora dice Companion y utiliza solo el rostro del personaje
seleccionado, extraído de los atlas existentes. La tarjeta reutiliza el retrato
parlante, revelado progresivo por puntos de código, Show text, actualización,
movimiento reducido y desplazamiento. Ya no expone las pestañas ni tablas de
metas, afecto, creencias o disponibilidad del Passport confirmado. La revisión
previa a confirmar el cuestionario inicial sigue en el flujo de onboarding.

Los accesos narrativos de Travel, vista previa e historial llevan a Companion.
Abrir un viaje guardado lleva a sus alternativas/vista previa, sin generar otra
narración de elección. ReflectionScreen queda como adaptador de navegación
antigua a la nueva tarjeta. Los contratos y explicaciones históricas conservan
compatibilidad y sus originales; no se reescriben.

## Arquitectura reutilizada

- `/api/dyconet/companion-reflection` construye una reflexión independiente del
  endpoint histórico de elección. Comparte proveedor, configuración Groq,
  transporte con timeout, esquema de selección y validador del narrador previo.
- El LLM selecciona apertura y variantes verificadas. No genera hechos libres,
  recalcula HOTCO ni ajusta posteriores. El catálogo determina los hechos y
  límites de la explicación. Versión de voz: `companion-pattern-voice-1.1`.
- Solo el catálogo textual sale al proveedor. No se envían nombres, direcciones,
  identificadores de persona/viaje, respuestas crudas, notas, GPS, credenciales
  ni muestras meteorológicas. La solicitud al backend local conserva evidencia.
- SQLite proyecta los últimos 60 registros de Experience de la misma persona.
  Lee únicamente perfil guardado, identificación, respuestas, selección y
  entradas/activaciones finales necesarias. No carga históricos completos,
  trayectorias, tracking, forecast.request ni notas.
- La respuesta y caché por idioma quedan ligadas al digest tipado de la
  solicitud, versión de voz, catálogo/selección y digest de respuesta. Android
  comprueba procedencia, frases exactas, proveedor y estado LLM antes de mostrar.
  Cambios de evidencia o versión invalidan la caché. Reflect again fuerza consulta.

## Significado de lo que cuenta el compañero

La tarjeta habla de prioridades declaradas, afecto por modo, comodidad atribuida
al modo respondido y satisfacción opcional del viaje completo. Cuenta respuestas
positivas/negativas/neutrales; no promedia escalas ordinales. Una ausencia no se
convierte en neutral. No atribuye satisfacción de un viaje multimodal a todos
sus modos, ni colapsa una sustitución de modo en la ruta originalmente elegida.

Usa la política compartida de exclusiones para cada objetivo: pruebas,
sintéticos, cancelaciones y viajes no realizados no se convierten en patrones
reales. Deduplica eventos y rechaza duplicados conflictivos/personas distintas.
Un registro explícito de revisión es necesario para interpretar el histórico:
si falta, puede narrar las declaraciones, pero explica en lenguaje cotidiano
que todavía debe comprobar esos informes. No crea ni importa un registro de
exclusiones en nombre del usuario y no infiere prueba por ausencia de GPS.

Para expectativas guardadas reutiliza `selected_relations` y su reconstrucción
algebraica de entradas. Exige disponibilidad, escalado conocido y coincidencia
con el modo efectivamente declarado sin cambios. Describe apoyos y tensiones
como expectativas anteriores del compañero, separadas de emociones y metas
observadas; nunca diagnostica disonancia ni atribuye causas psicológicas.

El encabezado del Passport aprendido se proyecta sin cargar posteriores. Si hay
una versión experimental archivada y desactivada, puede explicar que mantiene
una imagen tentativa separada de las respuestas originales y las expectativas
anteriores. Esta entrega no narra una comparación cuantitativa de posteriores,
no activa aprendizaje contextual sobre HOTCO ni modifica el Passport activo.
Las condiciones ambientales no disponibles no se reconstruyen o inventan.

## Comprobación y estado

162 pruebas backend y 108 JVM aprobadas; incluyen 13 pruebas nuevas de separación
entre informes/expectativas, exclusiones por objetivo, modo cambiado, neutral,
duplicados, identidad, reconstrucción y fallo/validación LLM. APK 0.4.103,
versionCode 114 compilada e instalada con reemplazo en Samsung SM-A566B.
Backend reiniciado en 8077 y USB reverse conservado.

Inspección reproducible: `validation/verify_companion_04103.py`.
Capturas y resultados en `.local-backups/companion-04103/`. Se verifica
navegación, tarjeta, ausencia de términos técnicos y emojis sin crear elección,
viaje o Experience. La apariencia/nombre guardados del usuario no se cambian.

La consulta real a Groq devolvió HTTP 403. La tarjeta mostró la reflexión de
respaldo e indicó Live conversation unavailable / saved reflection; no se afirma
generación LLM exitosa. La configuración enabled/configured no certifica acceso.
El histórico del teléfono carece del registro de revisión en esta comprobación,
por lo que la tarjeta no presenta esos informes como patrones empíricos.
