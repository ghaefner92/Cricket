# Cricket Android

La aplicación Android empaqueta el frontend Vue mediante Capacitor 8. El backend
Python sigue ejecutando HOTCO, routing, Orion, OTP y Groq como servicio externo.
Esta migración incluye ubicación puntual nativa; el seguimiento continuo de viajes
y el aprendizaje bayesiano del plan todavía son trabajo posterior.

## Proyecto y compilación

Abrir `experimental-frontend/android` en Android Studio. Requiere JDK 21,
Android SDK 36 y Node.js 22 o posterior. El proyecto tiene un mínimo de Android 7
(API 24); los plugins y la disponibilidad de WebView pueden limitar dispositivos
antiguos. La compatibilidad real debe comprobarse en los dispositivos objetivo.

Desde `experimental-frontend`:

```powershell
npm ci
npm run android:apk
```

La APK de desarrollo se genera en:
`experimental-frontend/android/app/build/outputs/apk/debug/app-debug.apk`.

`npm run android:sync` compila y copia el frontend al proyecto Android.
`npm run android:open` abre Android Studio. Después de modificar Vue, sincronizar
de nuevo antes de recompilar Android.

El script de compilación busca el JDK incluido con Android Studio en las rutas
habituales de este equipo. Para otra instalación, configurar `CRICKET_JAVA_HOME`
con la carpeta de un JDK 21 o posterior. Configurar `ANDROID_HOME` si el SDK no está
en su ubicación habitual. Las rutas locales y los archivos generados se excluyen
de Git.

## Conectar con el backend del ordenador

### Teléfono conectado por USB

Activar depuración USB y autorizar el ordenador. Con el backend en el puerto 8077:

```powershell
adb reverse tcp:8077 tcp:8077
adb install -r experimental-frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

Si `adb` no está en PATH, usar `platform-tools/adb.exe` del SDK.
La dirección predeterminada de la app es `http://localhost:8077`.
La redirección USB debe restablecerse tras desconectar o reiniciar el dispositivo.

### Emulador Android

En la app, abrir Menu -> Connection y guardar `http://10.0.2.2:8077`.
Este host permite al emulador estándar acceder al ordenador.

### Teléfono por Wi-Fi o servidor remoto

En Menu -> Connection, introducir la URL del servidor Cricket y pulsar
"Save and connect". Para desarrollo en la misma red puede utilizarse
`http://IP_DEL_ORDENADOR:8077`, siempre que Flask escuche en la interfaz adecuada
y el firewall permita la conexión.

La APK debug admite HTTP en la red local. Release admite HTTPS y las direcciones
locales explícitas de desarrollo; no permite HTTP general. La compilación release
necesita configurar el servidor y la firma antes de distribuirse.

No configurar aquí las URLs de OTP u Orion: el teléfono habla con Flask y Flask
accede a esos proveedores. Las credenciales de Groq permanecen en el backend.
La APK puede empaquetar una dirección inicial mediante `VITE_BACKEND_BASE_URL`;
no introducir secretos en variables `VITE_*`.

## Ubicación y navegación

La interfaz móvil conserva la tipografía y el arte pixel. Las metas muestran una
tarjeta completa con navegación anterior/siguiente y vistas de todas las metas
y prioridades. Las alternativas de ruta se consultan por modo y el mapa tiene
una vista independiente. Los detalles de las tarjetas permanecen completos.

La búsqueda de direcciones en Android utiliza `/api/dyconet/geocoding`: el backend
consulta Photon, por lo que la conexión USB basta para recibir sugerencias aunque
el teléfono no pueda acceder a Photon directamente. El ordenador necesita Internet.
Las imágenes del mapa en Android también pasan por el backend mediante
`/api/dyconet/map-tiles/{z}/{x}/{y}` y se cargan al abrir la vista del mapa.
El backend conserva las imágenes solicitadas durante siete días para evitar
descargas repetidas; mantiene la atribución visible a OpenStreetMap.
La conexión USB basta para el teléfono y el ordenador necesita Internet.

"Use my location" solicita permiso Android solamente al utilizar la función.
Se conserva la precisión de la posición y el comportamiento de cancelación de la
interfaz. No se solicita ubicación en segundo plano ni se registra una trayectoria.

El botón Atrás cierra el diálogo abierto; si no hay diálogo o navegación anterior,
minimiza la app. Las barras del sistema tienen sus márgenes gestionados nativamente.
El frontend está empaquetado dentro de la APK, sin depender de un servidor Vite.
Mapas, geocodificación y operaciones backend necesitan red.

## Transferir el perfil actual

La web y Android tienen almacenamientos separados; el perfil no aparece
automáticamente en el teléfono.

1. En la web, Menu -> My data -> Export all data.
2. Transferir el JSON al teléfono.
3. En Android, Menu -> My data -> Import backup y confirmar la importación.

La exportación Android abre el menú nativo para compartir/guardar el JSON.
La importación utiliza el selector de archivos. Se conservan los controles de
validación de backups y el historial de simulaciones. La dirección del servidor
es configuración local de cada instalación, separada del backup personal.

## Alcance de la entrega

Compilar la APK no demuestra por sí solo que todos los flujos funcionen en un
teléfono. La revisión en dispositivo debe cubrir onboarding, importación, ubicación,
mapas, búsqueda de los cuatro modos, Orion, narración, exportación y reinicio.
El tracking continuo será una fase posterior con un servicio Android dedicado.
