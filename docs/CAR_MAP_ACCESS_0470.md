# Ruta de auto — 0.4.70

La búsqueda del teléfono del 11 de octubre de 2026 mostraba auto con tiempo y
distancia, pero sin geometría. La auditoría devolvía `GeometryError` y
`supplemental_geometry_unavailable`; las otras modalidades tenían trazado.

La consulta directa reprodujo dos casos: la estación como origen estaba a
62.05 m del inicio del acceso peatonal, y el destino se ajustaba al mismo punto
de red que el final de auto, a 17.57 m del lugar solicitado, con camino peatonal
de duración y distancia cero.

Se admite el desplazamiento del lugar de origen igual que el del destino,
manteniendo el límite de 250 m del proveedor y los 25 m de unión auto–pie.
Se conservan únicamente las geometrías obtenidas. El acceso inicial sin trazar
se registra como `origin_access_offset_m` y `origin_access_time_unknown` en el
resumen y la evidencia. Android informa esa separación, muestra el tiempo de
los tramos cartografiados y la caminata, y deja desconocida la llegada cuando
el acceso inicial carece de tiempo. No se añade una recta ni un tiempo estimado.

Un camino peatonal de distancia/tiempo cero se acepta únicamente si todos sus
puntos coinciden dentro de 1 m y ambos extremos están a 25 m como máximo de lo
solicitado. No se añade como tramo de caminata. Los desplazamientos mayores y
las uniones auto–pie incompatibles siguen rechazándose.

Verificación: siete pruebas de accesos a pie en backend; 61 pruebas Android;
APK compilada e instalada preservando datos. Consulta directa de la ruta
original correcta tras el cambio. Las búsquedas históricas conservan su
resultado original; la corrección se aplica al consultar nuevamente.

Comprobación visual posterior: se repitió la búsqueda estación–Business School
con los lugares recientes del usuario. La pantalla mostró el trazado de auto
resaltado y el acceso peatonal, 10 min de tiempo cartografiado, 6 min a pie,
3.1 km y acceso inicial de 62 m con llegada desconocida. No se inició GPS ni
se registró una elección o Experience. Captura privada en
`.local-backups/car-map-0470/verified-screen.png`.
