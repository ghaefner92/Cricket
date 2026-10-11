# Mascotas originales — Android 0.4.80 / 91

El autor rechazó tanto los rostros generados de 0.4.78 como los cuerpos
procedurales 3D de 0.4.79 por pérdida de identidad. Autoriza volver a 2D:
cuerpo completo con los sprites originales, reacción alegre al tocar e
inclinación curiosa al mantener pulsado; conservar metas/ranking alrededor.

La tarjeta utiliza directamente companion-intro-dogs-v1.png, spider-v1.png,
drone-v1.png y spirit-v1.png, con los rectángulos del atlas original. No se
regeneraron imágenes ni modificaron píxeles. Los límites transparentes comunes
a todos los fotogramas se calculan en segundo plano para encuadrar el cuerpo
completo de forma estable en 232 dp, sin recortar una cabeza ni rediseñar rasgos.

Se usan secuencias propias del atlas para reposo/alegría/curiosidad. La curiosidad
inclina hasta nueve grados el sprite completo; la alegría añade un salto leve.
No se inventa una boca de risa para mascotas que no tienen ese dibujo. Reacción
finita de aproximadamente 1.4 s, ciclo ligado a STARTED y visibilidad de tarjeta,
movimiento reducido con pose fija. El renderizador GL, cuerpos procedurales y
pantalla/prueba de GPU se retiran. No esfera, retícula ni gestos de giro.

Se conserva edición diaria, escalas, rankings compartidos y versiones de
Passport. Las reacciones son decorativas y no registran evidencia afectiva.

Validación: APK compilada y 76 pruebas JVM aprobadas. Se comprueban los índices
de todas las secuencias contra los cuatro atlases originales, movimiento reducido
y encuadre común sin mutación de píxeles. APK instalada mediante adb install -r.
En teléfono se verificaron minidron original, cuerpo completo y ambas reacciones
con capturas inmediatas, sin modificar metas ni registrar Experience/GPS.
Copias antes/después: almacenamiento completo y hashes de los 25 registros
idénticos; el Passport activo también se conserva en esta actualización.

## Incidente de la entrega anterior

La ejecución connectedDebugAndroidTest de 0.4.79 utilizó la configuración por
defecto del instalador UTP: uninstall_after_test=true, retirando app y datos
al concluir la prueba. Se informó al autor y se reinstaló de inmediato. La
recuperación verificó y restauró la copia local de hoy 16:05 Europe/Berlin:
25 registros, Passport revisión 3. Ediciones posteriores a esa copia no están
garantizadas y no se reconstruyeron ni inventaron respuestas.

Se fija android.injected.androidTest.leaveApksInstalledAfterRun=true en
gradle.properties. Para 0.4.80 no se ejecutó instrumentación. Se guarda copia
completa antes de actualizar y se compara después. Los artefactos privados se
mantienen en .local-backups/pet-0479-recovery y pet-0480-preservation, fuera de
los assets/versionado de la app. No describir la recuperación como conservación
íntegra de todos los datos anteriores al incidente.
