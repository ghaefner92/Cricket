# Cricket Android nativo

La app Android principal está en `android-native`: Kotlin, Jetpack Compose,
SQLite y mapa dibujado con Canvas. Conserva la identidad pixel y los recursos
originales. El backend Python mantiene HOTCO, routing, Orion, OTP y Groq.

## Compilar

Requiere JDK 21, Android SDK 36 y acceso a las dependencias Gradle. El mínimo
Android es API 26. Abrir la carpeta `android-native` en Android Studio.

Desde la raíz:

```powershell
node android-native/scripts/build.mjs
```

El comando existente desde `experimental-frontend` también compila Kotlin:

```powershell
npm run android:apk
```

APK: `android-native/app/build/outputs/apk/debug/app-debug.apk`.
El script detecta el JDK de Android Studio y el SDK de este equipo; también
acepta `CRICKET_JAVA_HOME` y `ANDROID_HOME`. Las rutas locales no se versionan.

## Instalar y conectar por USB

```powershell
adb -s RZCY70K63HV reverse tcp:8077 tcp:8077
adb -s RZCY70K63HV install -r android-native/app/build/outputs/apk/debug/app-debug.apk
adb -s RZCY70K63HV shell am start -n de.ovgu.imiq.cricket/.MainActivity
```

El ID Android se mantiene. `install -r` conserva los datos de la app siempre que
la firma sea la misma; la versión debug utiliza la firma de desarrollo del equipo.
El primer inicio importa el almacenamiento de la versión Capacitor a SQLite.
Ese WebView local se usa para la importación; las pantallas de uso diario son nativas.

La dirección predeterminada es `http://localhost:8077`. En Menú → Connection se
puede guardar otra dirección y comprobar el backend. Para el emulador, utilizar
`http://10.0.2.2:8077`. Para Wi-Fi, utilizar la dirección del ordenador y comprobar
el firewall. Debug permite HTTP para desarrollo; release exige HTTPS excepto
las direcciones locales de desarrollo indicadas en la configuración de red.

Las direcciones y las imágenes del mapa se descargan a través del backend, por
lo que basta la conexión USB para el teléfono. El ordenador necesita Internet.
OTP y Orion se configuran en el backend; las claves de Groq permanecen allí.

## Pantallas

- Inicio: compañero y prioridades confirmadas.
- Travel: búsqueda, alternativas por modo, mapa y reflexión de la elección.
- Passport: metas, emociones, creencias y disponibilidad. Sin tarjeta de tolerancias.
- Menu: conexión, display, modos disponibles, compañero, historial y datos.

El nuevo cuestionario contiene metas, emociones y cuatro creencias medidas.
No solicita tolerancias ni rellena valores artificiales. Las búsquedas nativas
excluyen las tolerancias históricas mediante `tolerance_profile: {}`.
Los Passport e itinerarios antiguos conservan su identidad y sus datos originales.

## Datos

Menú → My data permite exportar/importar `cricket-backup-v1` mediante el selector
de documentos Android. Cada búsqueda se puede exportar por separado. El historial
incluye elecciones, clima y narraciones guardadas. Las importaciones archivan el
Passport anterior y rechazan simulaciones con el mismo ID y contenido distinto.
La creación de un Passport nuevo conserva el anterior y puede cancelarse.

La ubicación puntual pide permisos sólo al utilizarla. El seguimiento continuo y
el aprendizaje bayesiano todavía corresponden a fases posteriores del modelo.

## Referencias

[Correspondencia de componentes y alcance](MIGRACION_COMPOSE.md).
[APK híbrida anterior](ANDROID_LEGACY.md). Vue permanece disponible para web;
`npm run android:legacy:apk` recompila la versión Capacitor. Instalar una versión
anterior requiere gestionar sus códigos de versión y copias de datos; el archivo
WebView anterior se conserva para recuperación y no recibe nuevos registros nativos.
