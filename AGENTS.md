# Memoria del proyecto

Companion 0.4.103: acceso Passport sustituido por Companion, icono del rostro
elegido y una tarjeta de avatar/narración en primera persona. Fuente SNES al
mismo tamaño y emojis; sin HOTCO/Passport/modelos en esa tarjeta. Groq reutiliza
transporte/validación, catálogo longitudinal separado de choice; datos acotados
proyectados en SQLite, exclusiones compartidas y caché ligada a evidencia.
Registro de revisión ausente no permite patrones del histórico. Groq real 403,
respaldo identificado. Sin activar posteriores/Passport/HOTCO. 162 backend y
108 JVM aprobadas. docs/COMPANION_REFLECTION_04103.md.

Companion 0.4.102: inicio con un único título suave menta y C del icono
integrada; sin símbolo separado ni fuente pixel en el título. Avatares con
32 fotogramas, preocupación durante fuga simultánea y risa al llegar; retrato
universitario con 24 y anclaje fijo. Cuatro mascotas +50 %, fantasma orbita
Let's go. Fundidos premultiplicados en capa aislada evitan caída de opacidad;
reloj leído en Canvas para evitar recomposición por fotograma. Atlas startup_*
separados de otros usos. docs/COMPANION_STARTUP_04102.md.

Travel 0.4.101: historial proyectado en SQLite antes de cargar JSON; prioriza
viajes y elecciones, acceso directo a Companion explanation. Narración admite
importancia ordinal 1–7 además de reparto histórico; guardar explicación con
json_set, sin cargar archivos grandes. Groq configurado pero consulta real 403
por red; fallback identificado, sin afirmar LLM. docs/TRAVEL_HISTORY_04101.md.

Travel 0.4.100: reproducción sin GPS continúa fuera de pantalla con reloj
monotónico y estado separado ACTIVE_PLAYBACK; servicio foreground specialUse,
notificación de apertura/pausa/reanudación/finalizar. Cambiar menú/Back no cancela.
No es GPS observado ni experiencia confirmada. docs/TRAVEL_BACKGROUND_04100.md.


Travel 0.4.99: cierre OOM confirmado en logcat, lectura de históricos desde
ProspectiveLearning en 0.4.98. Lectura JSON por streaming desde SQLite y proyección
de historial de investigación sin result/forecast.request/tracking; originales
sin cambios. UI omite solo forecast.request al leer. Sin GPS vuelve a controles
RoutePlay/Pause/Stop originales. Permiso USER_FIXED bloqueado con GPS global
encendido: ofrecer ajustes de app, nunca inferir acceso por GPS encendido.
docs/TRAVEL_CRASH_LOCATION_0499.md.


Travel 0.4.98: Play solicita permiso y activación de ubicación mediante Android;
al rechazarlos abre reproducción sin GPS, sin crear fixes/experiencias simuladas.
Botón inferior de ubicación: pedir activación si apagada, ajustes Android si
encendida (el interruptor global no puede cambiarse directamente). Estado real
y diagonal roja si faltan servicios o permiso. docs/TRAVEL_LOCATION_FLOW_0498.md.


Travel 0.4.97: autor rechaza sprites raster de botones; usar como referencia
los tres controles inferiores izquierdos del mapa. Iconos vectoriales estáticos
de 22 dp, tarjeta de 124 dp con controles de 40 dp y A/swap/B conservado.
Sin animación de acciones ni A/B. docs/TRAVEL_STATIC_COMPACT_0497.md.


Travel 0.4.96: sprites simplificados tomando naturalist-sheet-v2.png como
referencia de avatares; quitar ornamentos grandes, tamaños 28/24 dp y animación
discreta cada 640 ms. docs/TRAVEL_AVATAR_STYLE_0496.md.


Travel 0.4.95: seis sprites raster SNES JRPG nuevos y animados para búsqueda,
preferencias, recientes, intercambio, A/B. Izquierda A/swap/B estrictamente
vertical, sin líneas horizontales. Detalle: docs/TRAVEL_SNES_SPRITES_0495.md.


Travel 0.4.94: swap entre A/B; columna derecha buscar/preferencias/recientes con
accesos directos, eliminando menú de tres puntos. docs/TRAVEL_SEARCH_ACTIONS_0494.md.


Travel 0.4.93: parking por defecto oculto solo en resultados PT con opción
independiente de reactivar; Connections, salida sin repetir líneas, aviso de
bus aproximado eliminado. Clima compacto desplegable; metas simuladas y mascota
en información, conservadas. No cambiar nombres de estaciones en esta entrega.
Detalle: docs/TRAVEL_DECLUTTER_0493.md.


UI PT 0.4.92: tarjetas compactas con colores menta/violeta/ámbar, medallas
pequeñas, Arrival/Ankunft y símbolos por vehículo en cada conexión. Mostrar
hasta tres itinerarios por identidad, no descartar distintas salidas por usar
las mismas líneas. No fabricar alternativas ni incluir vencidas para llenar.
La llegada permanece también en el resumen. docs/TRANSIT_COMPACT_CARDS_0492.md.


UI PT 0.4.91: tres conexiones presentadas en tarjetas JRPG con marcos y mini
medallas animadas oro/plata/bronce (1/2/3), respetando movimiento reducido.
Quitado aviso visible de reference tracks/service path unverified; conservar
metadatos/verificaciones y orden de llegada. docs/TRANSIT_CARDS_0491.md.


Decisión de transporte público 0.4.90: primera conexión por llegada más temprana
al destino entre alternativas disponibles/no vencidas, sustituyendo la propuesta
de prioridad por parada cercana. Mostrar hasta tres conexiones distintas con
llegada y caminata inicial; destacar plataformas próximas al origen antes de
buscar. Distancia de mapa en línea recta, no tiempo de acceso. No altera HOTCO,
Passport ni evidencia congelada. Detalle: docs/TRANSIT_ARRIVAL_NEARBY_0490.md.

Actualización de autorización, 11 de octubre de 2026: el autor pide continuar
la implementación D-Day en el orden registrado en docs/D_DAY.md, incluyendo
fases 5–6 como nuevas entregas. Esto sustituye el límite anterior de fases 1–4
para implementación; no certifica validación científica ni autoriza activar
automáticamente posteriores. Adquisición de experiencias GPS reales pospuesta
hasta después: no bloquear implementación por recorridos/participantes.
Orion entrega valores actuales según aclaración del autor aunque la fecha
publicada no se actualice: versionar recepción/garantía por separado, preservar
originales y no reinterpretar snapshots históricos. Bayes; sin F3/RL/deep
learning/ML operativo. UI al final. Consultar la continuación autorizada en D_DAY.

Cuando el usuario diga **D-Day**, **D.day** o **d.day**, se refiere al plan de
Passport dinámico y registro longitudinal en [docs/D_DAY.md](docs/D_DAY.md).
Ese documento conserva el objetivo, las diez fases, las decisiones teóricas y
el estado de la entrega. Consultarlo antes de continuar esa tarea.

La entrega inicialmente autorizada comprendía las fases 1–4: el autor pidió
implementar el piloto y la fase 4. La continuación del 11 de octubre autoriza
implementar las siguientes entregas según docs/D_DAY_IMPLEMENTATION.md.
La fase 4 permanece aislada del Passport activo y de las conexiones HOTCO.
Su implementación y resultados están en docs/D_DAY_PHASE4_DELIVERY.md.
F3 está descartado en este enfoque; no volver a
introducirlo. HOTCO-CT es el modelo experimental del autor, sin validación
científica establecida. No interpretar una trayectoria simulada como evidencia
observada ni copiar automáticamente su estado final al siguiente Passport.

Las decisiones para preparar la fase 4 están en
[docs/D_DAY_PHASE4_PROTOCOL.md](docs/D_DAY_PHASE4_PROTOCOL.md). El autor confirmó
una escala bipolar de ayuda–dificulta satisfacer comodidad física, 1–7, neutral
4, codificación `(r−4)/3`. No sustituirla por una escala unipolar nada–mucho ni
reetiquetar mediciones históricas. El aprendizaje de fase 4 se implementa aislado
antes de modificar el Passport activo o HOTCO.

Decisión posterior del autor: mantener Bayes; RL y deep learning quedan fuera
de esta entrega. Evaluar por separado fidelidad/incertidumbre de la explicación
y sensibilidad de la recomendación HOTCO a creencias plausibles de comodidad.
No confundir estabilidad de recomendación con precisión o acierto. Los umbrales,
la política de contexto insuficiente y el tratamiento temporal siguen pendientes;
esta aceptación no autoriza por sí sola implementar fases 5–6.

El siguiente diseño D-Day debe incluir experiencia afectiva contextual y
temporal además de comodidad: el autor destaca emociones negativas reiteradas
bajo lluvia. Conservar valencias originales y feedback afectivo por modo;
no confundir emoción, satisfacción de comodidad e importancia de metas.
Desde 0.4.60, el autor autoriza y se implementa aprendizaje bayesiano afectivo
de fase 4, separado de comodidad y del Passport activo. Experience exige una
valencia ordinal −3…+3 para cada modo utilizado, guarda el crudo y `r/3`;
neutral cero es una respuesta explícita, ausencia no equivale a neutral.
Detalle: docs/D_DAY_AFFECT_DELIVERY.md. El modelo afectivo utiliza la valencia
original como prior individual y compara versiones básica/contextual sin UV.
El aprendizaje temporal/olvido y la activación de posteriores siguen pendientes;
no reutilizar automáticamente priors de literatura de comodidad para afecto.

Siguiente entrega autorizada e implementada: 0.4.61 automatiza predicciones
prospectivas experimentales y evaluación posterior (docs/D_DAY_PROSPECTIVE.md).
No autoriza activar fases 5–6. Predicciones de comodidad y afecto en segundo
plano, contexto/evidencia congelados al obtener rutas; puntuar solo si se
registraron antes de la elección y coinciden con el modo/ruta realizado.
Mantener versiones originales, registrar exclusiones y comparar en experiencias
comunes Passport inicial/modelo básico/contextual. No usar un intervalo de la
expectativa latente para juzgar cobertura de una única respuesta observada.

El autor autorizó continuar con evaluación temporal mediante simulaciones.
Laboratorio offline: comfort_pilot/temporal_lab.py, temporal_experiment.py y
temporal_calibration.py; protocolo/resultados en docs/D_DAY_TEMPORAL.md.
Candidatos de estado latente ordinal con intercepto cambiante y lluvia fija,
tiempo real en días, comparación acumulativa y promedio de modelos. Son una
reducción controlada para investigación, no reemplazan el modelo meteorológico
completo de la app. No activar difusión, olvido ni posteriores en producción.
Las tasas candidatas no están validadas; conservar historia completa y separar
cambio de contexto, cambio de intercepto y cambio de relación modo–contexto.

Ampliación autorizada: comparación temporal offline con meteorología completa
sin UV, manteniendo cambios de UI para el final. Usa la codificación del piloto
y controles básico/lluvia, sin activar cambios en producción ni Passport/HOTCO.
Protocolo: docs/D_DAY_TEMPORAL_WEATHER.md. Auditar inferencia de partículas
frente a MCMC y sensibilidad numérica; coeficientes contextuales siguen fijos.

Siguiente entrega autorizada: puntos 1–2 de comparación de atribución temporal,
desacoplando clima/tiempo y evaluando intercepto, lluvia o ambos dinámicos.
Laboratorio aislado: docs/D_DAY_ATTRIBUTION.md. Mantener UI para el final,
meteorología completa sin UV, objetivos separados e historia completa. Diseños
emparejados requieren distinguir bloques meteorológicos de secuencias; no
interpretar mejor predicción como atribución causal o validación psicológica.

Auditoría contextual autorizada: docs/D_DAY_CONTEXT_AUDIT.md. El piloto informa
unidades, ventanas semánticas pendientes y cobertura por tramos para ambos
objetivos. No atribuir lecturas parciales a la ruta completa ni inventar medias
sin exposición. Contexto congelado de selección, no exposición real del viaje.
Metadatos completos no certifican semántica del proveedor. La auditoría en vivo
se añadió en la entrega posterior descrita debajo. Mantener fases 5–6 y UI sin activar.

Entrega posterior autorizada para avanzar el cierre técnico de fase 4: 0.4.62,
docs/D_DAY_PHASE4_CLOSE.md. El autor confirma Europe/Berlin para fechas locales
y garantía del proveedor: Walter.datetime también fecha Winfred. Solo compartir
ese reloj entre entidades de la misma QueryList, conservando referencia/original.
La consulta real confirmó lecturas antiguas excluidas por el piloto; no ampliar
vigencia ni convertir mm/h a mm para simular contexto válido. La captura Android
conserva consultas ambientales ligadas a GPS, errores/interrupciones, fuera de
la cola de controles; no implica exposición personal ni sustituye predicciones
preelección. Sensibilidad HOTCO offline completa: 864 trayectorias, sin activar
pesos/posteriores en producción. La fase 4 NO está científicamente validada;
persisten contratos meteorológicos, recorrido técnico completo, datos de usuarios,
subcobertura temporal y criterios de precisión/activación. Dos comprobaciones UI
del teléfono estaban pendientes porque estaba bloqueado; ambas pasaron el
11 de octubre de 2026, junto con tres pruebas de captura/contrato/persistencia.
Seguimiento: output/d-day-phase4-followup-2026-10-11/verification.json.
La nueva consulta Orion conserva cero variables utilizables por antigüedad.
El autor aclaró que realizó una prueba estacionaria en la cama, sin recorrido.
El registro del 11 de octubre duró 4.046 s, cero puntos GPS, sin distancia,
pausa/reanudación ni muestras ambientales; Experience confirmada y contrato
válido no lo convierten en un viaje real. Conservar original y excluir esa
Experience de evaluación empírica mediante el sidecar privado documentado en
docs/D_DAY_PHASE4_CLOSE.md. Recorrido real y exportación de backup UI pendientes.
La conexión USB al backend se pierde al desconectar el cable;
conservar errores de captura sin confundirlos con muestras exitosas.
La UI sigue pospuesta.

Primer bloque de la continuación implementado: contrato Orion por recepción
versionado para consultas nuevas (sin cambiar historia), elegibilidad/exclusiones
compartidas por aprendizaje y evaluación, soporte en evidencia Android.
83 pruebas backend y 44 JVM aprobadas; APK compilado, sin nueva instalación.
Nueva auditoría con garantía de actualidad: Walter 6/7, Winfred 5/7 variables
utilizables; lluvia sigue pendiente por unidades/ventanas. Detalle y próximos
bloques en docs/D_DAY_IMPLEMENTATION.md. No usar los anteriores conteos cero
como conclusión vigente bajo el nuevo contrato; conservarlos como históricos.

Investigación posterior: docs/D_DAY_ORION_RESEARCH.md. QuantumLeap público
confirma histórico cambiante, con filas repetidas y omisiones por atributo.
Winfred: intervalo publicado 60 minutos, intensidad mm/h basada en diez minutos
según manual oficial; no inferir total horario de una muestra ni confundir
recepción con adquisición. Revisar luz Walter derivada de t1solrad antes de
asumir lux y decoder SenseCAP unsigned para temperatura. Conteos de disponibilidad
anteriores no certifican unidades ni precisión física. Preservar fuentes y crudos.

Decisión posterior implementada: docs/D_DAY_RAIN_DISPLAY.md. Única lluvia en UI:
rainIntensity de Winfred, mm/h, último reporte con ventana de diez minutos y
edad explícita/desconocida; no rejuvenecer con consulta/dateModified/Walter.
cumulativeRainfall conservado para auditoría de intervalos, excluido de UI y
normalización. Cambio puntual de UI autorizado; resto de UI al final.

Adaptación posterior implementada: docs/D_DAY_WEATHER_INTENSITY_V2.md.
V2 distingue rainIntensity mm/h del rain mm de V1; prior nuevo centrado en cero,
sin transferir coeficientes/posteriores ni priors de forma de literatura V1.
Lluvia contextual exige hora de reporte explícita y <=600 s (política candidata,
no validada); desconocida/stale queda ausente, aunque UI conserva último valor.
Sin meteorología utilizable: fallback básico, contextual no puntuable. Luz Walter
sin lux explícitos excluida. Nuevas solicitudes Android V2; historia V1 intacta;
scores y métricas separados por versión. 97 backend/49 JVM aprobadas y APK
compilado, sin instalar/reiniciar backend. Activación Passport/HOTCO sigue fuera.

Comparación temporal con intensidad V2 implementada:
docs/D_DAY_TEMPORAL_INTENSITY_V2.md. Laboratorio aislado, 176 secuencias y 8.448
eventos sintéticos, 236.800 pronósticos puntuados antes de respuesta; cinco priors,
q=0/0.15/0.4 en SD latente por raíz de día, promedio de modelos. Solo intercepto
dinámico; cambio de relación con lluvia es prueba de especificación incompleta,
no atribución psicológica. Contrastes por eventos comunes y secuencias, objetivos
separados. Auditoría seleccionada SMC–MCMC y sensibilidad numérica; 103 pruebas
backend aprobadas. Política candidata conserva acumulativo de referencia e
historia completa, sin tasa universal ni activación automática. Artefactos en
output/d-day-temporal-intensity-v2. Próximo bloque del orden autorizado: Passport
aprendido versionado, linaje, reproducción, idempotencia y reversión. No promover
tasas/posteriores por resultados sintéticos ni requerir GPS real en esta fase.
