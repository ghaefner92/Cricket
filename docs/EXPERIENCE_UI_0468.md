# Experience UI · Android 0.4.68 / code 79

Entrega visual autorizada el 2026-10-11. Se inspeccionó Experience abierto en
el teléfono y se comparó con los controles de Cognitive Passport.

Experience separa confirmación del recorrido, comodidad, afecto y preguntas
opcionales en tarjetas PixelCard. Incluye el compañero del usuario, iconos de
modo/meta, contador de respuestas obligatorias y explicaciones pequeñas. El
resumen del recorrido pendiente queda compacto; otros objetivos y notas se
pliegan para reducir la densidad visual.

Comodidad reutiliza RatingControl del Passport con escala bipolar 1–7 y
neutral explícito 4. Conserva pregunta, versión, atribución, idioma, fecha y
codificación del piloto. Un control centrado sin respuesta no registra neutral.

Afecto reutiliza ValenceEditor y sus escenas/sprites/corazones. Experience
proporciona una pregunta sobre este recorrido; Passport conserva su pregunta
sobre viajes habituales. Escala ordinal −3…+3, respuesta neutral cero explícita,
ausencia separada. Se mantiene una respuesta requerida por cada modo realizado;
si hay varios modos, el selector conserva respuestas y señala los pendientes.
La escena no muestra flechas de cambio de modo si solo hay uno.

Los otros objetivos conservan satisfacción 1–7 (muy mal–muy bien), opcional,
con borrado de respuesta. No se sustituyen por anclas de comodidad. El contrato
de guardado y la confirmación final permanecen intactos. No se activa aprendizaje
en Passport/HOTCO ni se registra una experiencia durante la inspección.

Validación: assembleDebug y testDebugUnitTest aprobados. APK instalada con
reemplazo que conserva los datos. Se inspeccionaron en el teléfono resumen,
tarjetas y escena afectiva para Walking sin puntuaciones. Se restauró únicamente
la selección de borrador «Yes, as planned» que el usuario ya tenía antes del
reinicio. Confirmación deshabilitada mientras faltan puntuaciones.
No se validó interactivamente el flujo de varios modos con respuestas reales.
Los borradores siguen siendo estado de pantalla; esta entrega no añade
persistencia de formularios incompletos.

Archivos: TripUi.kt, ExperienceUi.kt, ComfortPilotUi.kt, ValenceEditorUi.kt,
ValenceWorldUi.kt. Inspección reproducible: validation/inspect_experience_ui.py;
capturas locales: .local-backups/experience-0468/.
