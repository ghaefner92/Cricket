# Continuidad y notificaciones — 0.4.100

El teléfono mantiene permisos GPS bloqueados y POST_NOTIFICATIONS concedido;
no había servicio de viaje. En reproducción sin GPS el reloj dependía de una
corutina RESUMED de la pantalla. Al salir, se detenía y no existía notificación.

Se conserva estado separado en cricket.active-route-playback.v1: selección,
ancla monotónica, tiempo acumulado de reproducción, pausa, duración y boot_count.
La pantalla solo presenta ese reloj: desmontarla no cambia el estado.
Pausa congela el progreso, reanudar excluye pausa, duración limita reproducción;
un reinicio del dispositivo no inventa tiempo de otro reloj. No captura GPS,
contexto ambiental ni distancia personal, no crea experiencia al avanzar.

TrackingService atiende también reproducción sin GPS mediante foreground
specialUse (permiso normal y subtype declarados). Inicio explícito del usuario,
stopWithTask=false, START_STICKY para esta modalidad. GPS conserva foreground
location y recuperación conservadora. La notificación es persistente, categoría
navegación, privada: abrir, pausa/reanudar y finalizar. Finish sin GPS abre
Activity y registro manual pendiente mediante acción explícita; no confirma
experiencia. La pantalla conserva controles existentes. Cancelar es explícito;
Back/Home y cambio de menú conservan progreso. Banner y Travel recuperan el activo.
Se evita iniciar una segunda selección mientras hay reproducción activa.

Android 13+ pide notificaciones al iniciar si faltan; denegarlas no concede GPS
ni impide la modalidad sin GPS, pero Android puede ocultar la notificación.
Referencia de tipos de servicio:
https://developer.android.com/develop/background-work/services/fgs/service-types

Pruebas: avance sin ticks UI, pausa/reanudación, restauración del estado serializado,
cambio de boot y fin previsto sin Experience/Tracking. No se aceptan viajes ni
se cambian permisos del teléfono para validar. Interacciones de notificación
y continuidad del viaje real requieren comprobación del autor.

Entrega: assembleDebug y 107 pruebas JVM correctos. Respaldo 20261011-221705,
50 búsquedas y Passport 0504e5fa-b367-5835-8e36-890f4a08b68a revisión 9.
Instalación con conservación de datos 0.4.100/código 111.
