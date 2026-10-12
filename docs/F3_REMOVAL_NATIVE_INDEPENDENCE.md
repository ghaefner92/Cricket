# Retirada de F3 e independencia del frontend anterior

12 de octubre de 2026. El autor solicita eliminar F3 completamente del código
y comprobar si se puede retirar `experimental-frontend` conservando la app actual.

## Cambio de backend

Se eliminan `context_models.py` y `context_perturbation.py`, el constructor de
fuerzas, la selección por entorno y el modo de ejecución histórico. HOTCO ya no
admite `alpha_context` ni un argumento de perturbación en la simulación, RK4 o
campo vectorial. La versión pasa a `hotco_ct_v4.3-online.2`.

`contextual_deliberation` admite exclusivamente `observations_only`, tanto al
leer la solicitud como al ejecutar directamente el servicio. Comparte dinámica
y comprobación de robustez entre rutas con las mismas entradas, manteniendo sus
observaciones, procedencia y avisos separados. Las respuestas nuevas no generan
objetos de perturbación ni una versión de ese mecanismo retirado.

Se retiran las ramas específicas del narrador/XAI. La lectura de contribuciones
ya archivadas se conserva como proyección de evidencia, sin calcular fuerzas.
Los snapshots, backups y resultados anteriores no se reescriben. El índice
longitudinal conserva la identificación histórica de políticas antiguas para
mantener comparabilidad Kotlin/Python; esa etiqueta no habilita su ejecución.

`/health` declara `external_context_drive_supported=false`. La meteorología,
captura de contexto, aprendizaje bayesiano y versión experimental de Passport
se conservan. No se implementa ni activa aquí la fase 6.

## Comprobaciones

- 164 pruebas backend aprobadas, incluidas pruebas de rechazo del modo retirado,
  ausencia de entrada/parámetro de fuerzas y separación de evidencia por ruta.
- Doce perfiles exclusivamente sintéticos, con distintas disponibilidades y
  respuestas: trayectoria completa, topología y correcciones de proyección
  coinciden exactamente con HOTCO sin fuerzas del commit `308abec`.
- HTTP local Flask: health, deliberación y explicación devuelven 200; solicitud
  del modo retirado devuelve 422. Estas pruebas no consultan proveedores externos.
- Gradle clean, 108 pruebas JVM sin fallos, APK principal y APK de pruebas
  compilados con `experimental-frontend` fuera de su ubicación original.
  Los 76 pasos Gradle se ejecutaron en ese build limpio.
- Importación del backend y GET /health vuelven a pasar con esa carpeta ausente.

Pruebas reproducibles:

```powershell
python validation/verify_no_context_drive.py
.\validation\verify_native_without_vue.ps1
.\validation\verify_native_without_vue.ps1 -BackendOnly
```

El aislamiento verifica que las rutas del movimiento permanezcan en D:/Cricket
y restaura el frontend con finally. La primera ejecución sin permisos para la
caché Gradle falló y también restauró la carpeta; la comprobación autorizada
posterior terminó correctamente. Evidencia: `output/no-context-drive/`.

## Retirar experimental-frontend

La app actual usa Kotlin/Compose en `android-native`; Gradle incluye únicamente
`:app`. Sus recursos están en `android-native/app/src/main/assets`, con contratos
adicionales en `contracts` y fixtures de instrumentación en `validation/fixtures`.
El backend usa sus propios módulos, artefactos y catálogo de narración.

No necesita los archivos Vue/Capacitor, node_modules, Vite ni los builds del
frontend anterior. El WebView de MainActivity solo ejecuta un script inline para
migrar localStorage/IndexedDB antiguos a SQLite; tampoco lee esa carpeta.

Puede retirarse `experimental-frontend` manteniendo la app nativa. Se perderían
la interfaz web anterior, sus alias npm y la posibilidad de recompilar aquel APK
Capacitor. Compilar la app actual desde la raíz con
`node android-native/scripts/build.mjs` o Gradle desde `android-native`.
Conservar `android-native`, `app-backend`, `contracts` y `validation/fixtures`.

La comprobación restauró primero la carpeta. Después el autor confirmó eliminarla:
`experimental-frontend` fue retirada el 12 de octubre de 2026, comprobando que
android-native, app-backend, contracts y validation/fixtures permanecen presentes.
Esta retirada no elimina datos del teléfono, instala APK ni reinicia el backend.
Los nuevos cambios backend se cargan al reiniciar su proceso. La compilación,
pruebas y comprobaciones HTTP no equivalen a probar un viaje nuevo con GPS/OTP/
Orion ni a validar científicamente HOTCO.
