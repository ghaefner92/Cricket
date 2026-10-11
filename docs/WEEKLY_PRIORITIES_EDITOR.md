# Distribución de prioridades — 0.4.46

La pantalla anterior obligaba a navegar entre prioridades para editar sus puntos. Ahora las 11 prioridades se ajustan directamente en una lista estable con controles de suma y resta de 48 dp. Cada título abre su explicación; las etiquetas son más breves. La versión 0.4.45 restaura la fuente SNES existente, también en los títulos de las explicaciones.

El avatar elegido aparece en un espacio de 36 dp junto a la última prioridad ajustada, con el icono de esa prioridad en pequeño. Inicialmente acompaña a la prioridad con más puntos. Al sumar asiente con un pulgar de aprobación y un halo dorado; al restar niega con un movimiento lateral y brazos cruzados, acompañado de luces violetas. Las poses reutilizan los atlas existentes con brazos dibujados para esta interacción.

Cada meta tiene una barra con su proporción de los 10 puntos. Su mascota (robot pequeño, araña, espíritu o cachorro) sigue el extremo interpolado de la barra y gira en el sentido del cambio con destellos. El relleno verde/dorado o violeta y el borde resaltan el cambio. Las reacciones duran 900 ms, se reinician al tocar de nuevo y terminan sin bucles permanentes. Volver a una fila al desplazar no repite una reacción antigua. Con movimiento reducido las barras actualizan su posición directamente, sin giros ni luces animadas.

Las filas mantienen los controles de 48 dp y añaden una franja de 26 dp para la barra y mascota, bajo una fila de controles de al menos 64 dp. Los avatares y mascotas quedan pequeños, sin escenas grandes.

El contador de puntos y el botón Guardar permanecen visibles mientras se desplaza la lista. Al completar los 10 puntos se desactiva la suma y se explica cómo reasignar: quitar un punto de otra prioridad. Guardar conserva la condición original de tener exactamente 10 puntos y continúa al paso de valencias.

Se conservan las claves, el presupuesto, la persistencia del borrador y la conversión al modelo. No se asignan puntos automáticamente ni se cambia el perfil existente. La interfaz mantiene los idiomas inglés y alemán y respeta la preferencia de texto grande.

## Validación

- Compilación del APK principal y de pruebas correcta; 16 pruebas unitarias existentes aprobadas.
- Prueba de interacción en Samsung: completar el presupuesto, bloquear incrementos, transferir un punto entre prioridades, comprobar que el avatar cambia a la fila ajustada y desaparece de la anterior, verificar aprobación y negación para los cuatro avatares, comprobar valores de las barras, abrir y cerrar una explicación, llegar con gestos a la última prioridad, comprobar el contador y Guardar, verificar movimiento reducido y ejecutar el callback de guardado. Una prueba aprobada.
- La prueba monta el editor real con estado local; no guarda prioridades del usuario. La captura utiliza áreas seguras; la pantalla de producción recibe sus márgenes del Scaffold existente.
- Capturas revisadas: `output/ui-validation/weekly-priorities-0.4.46-approval.png`, `weekly-priorities-0.4.46-negation.png` y negación de los demás avatares.
- Versión 0.4.46 (57) instalada en el celular conectado.

Esta validación comprueba funcionamiento y distribución visual en el dispositivo; no sustituye una prueba de usabilidad con usuarios.
