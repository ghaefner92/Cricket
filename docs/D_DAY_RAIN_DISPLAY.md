# Lluvia mostrada: intensidad de Winfred

Decisión e implementación del 11 de octubre de 2026. El autor autoriza este
cambio concreto de UI antes del cierre general de la interfaz.

La única lectura de lluvia visible es `Sensor:Weather:Winfred.rainIntensity`,
en mm/h, identificada como última intensidad registrada. Android Home, rutas e
historial y la interfaz web comparten filtros que impiden mostrar lluvia Walter,
acumulados, unidades mm o una sustitución por otra magnitud. Cero es un valor
válido; ausencia o valor inválido produce desconocido. No se muestra un rango
mezclando estaciones. Los snapshots históricos se filtran para presentación,
sin reescribir sus originales.

La ventana documentada se conserva como diez minutos/600 segundos. El intervalo
de reporte se toma de `uplinkInterval` de Winfred (minutos); no se inventa.
La antigüedad solo se calcula si el atributo aporta `receivedAt` explícito con
zona horaria. `datetime` compartido de Walter, `dateModified` y recepción de la
consulta no se utilizan como hora del reporte. En ausencia de ese dato se muestra
“Report age unknown” / “Alter des Berichts unbekannt”. La UI informa que volver
a consultar no fuerza una nueva medición y que puede diferir de la lluvia actual.
Los datos consultados hasta esta entrega no certifican la hora exacta del reporte.

`cumulativeRainfall` se conserva como observación separada en mm en nuevas
capturas/contextos, con uso `INTERVAL_AUDIT_ONLY` y normalización deshabilitada.
No aparece ni en visualizaciones ni en detalles técnicos de UI. Permite preparar
auditorías de diferencias entre lecturas; no se implementa todavía integración de
intervalos ni se ocultan reinicios o huecos mediante interpolación.

El modelo experimental previo sigue esperando mm y excluye esta intensidad
mm/h; esta entrega implementa adquisición/presentación, sin cambiar coeficientes
ni activar aprendizaje o HOTCO. La adaptación de la covariable requiere entrega
versionada propia. No se modifican suscripciones externas.

Verificación: 86 pruebas backend y 47 Android/JVM aprobadas; APK debug compilado;
verificación específica web `validation/verify_rain_presentation.mjs` y build Vite.
Tres pruebas backend cubren separación intensidad/contador, falta/invalidez y
procedencia temporal; tres JVM cubren exclusividad, cero y edad desconocida/reportada.
El APK generado está en `android-native/app/build/outputs/apk/debug/app-debug.apk`.
No se instala ni se reinicia el backend en ejecución en esta entrega.

Entrega posterior: docs/D_DAY_WEATHER_INTENSITY_V2.md implementa un modelo
separado de intensidad. El estado pendiente indicado arriba corresponde a esta
entrega original; la UI sigue mostrando solo el último valor de Winfred y su edad.
