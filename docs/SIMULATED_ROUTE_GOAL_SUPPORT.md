# Metas simuladas en la tarjeta resumida — 0.4.50

La tarjeta muestra hasta tres aportes positivos y tres negativos de necesidades
a la alternativa representada. Verde ↑ significa apoyo y rojo ↓ obstáculo en la
simulación; no representan satisfacción observada ni importancia declarada.

## Definición

Para cada necesidad `n` y modo `m`, el aporte es
`W[action:m, n] × activación_terminal[n]`. Se usa la topología real almacenada
en `simulation_evidence` y el estado final de `node_activations.needs.final`
del candidato correspondiente a esa ruta. El orden de nodos se obtiene de
`state_columns`, sin asumir el orden de los campos JSON.

Este valor es la entrada firmada de una necesidad a una acción al terminar la
simulación. Su parte positiva entra en la excitación y su parte negativa en la
inhibición del solver. No mide el efecto causal total sobre la elección, la
derivada completa, una probabilidad ni el grado de satisfacción del viaje.
El cálculo no añade perturbaciones ambientales ni modifica F3 o el solver.

Los tres positivos se ordenan de mayor a menor; los negativos por magnitud
negativa, empezando por el más negativo. Si existen menos de tres, se muestran
solo los disponibles. Las entradas cero no rellenan ninguno de esos grupos.
Una ruta sin evidencia completa muestra ausencia de datos; no usa las respuestas
iniciales como sustituto de resultados simulados.

Al tocar el grupo se abre un diálogo con las once conexiones, ordenadas por
intensidad absoluta, signo, valor y procedencia declarada/estimada de la conexión
cuando esa procedencia está disponible. El listado se puede desplazar.
Las vistas existentes de detalles de ruta no se modifican.

## Distribución

La tarjeta mantiene el límite de 15% de altura de pantalla y máximo 112 dp.
En pantallas estrechas usa dos filas: identificación, seis indicadores y
controles arriba; tiempo, distancia, llegada y rostro abajo. El retraso real
de transporte público aparece junto a su identificación y no ocupa ancho de
las métricas. Un icono de información abre los detalles.

La disposición horizontal se selecciona según el ancho disponible (640 dp),
en lugar de deducirla de una altura pequeña. Los valores se ajustan al ancho
sin elipsis ni saltos de línea. Los servicios muestran sus líneas en secuencia
y el primer tipo de vehículo; si hay más de dos, se indica cuántos quedan y
el detalle conserva el itinerario completo. La etiqueta de metas se omite
visualmente cuando falta altura, conservando su descripción accesible.

## Validación

- 20 pruebas unitarias Android, incluidas cuatro del nuevo cálculo: clasificación
  por aportes firmados, modo correcto y alias `foot`, datos incompletos/inválidos,
  grupos menores de tres e inmutabilidad de la simulación.
- Dos pruebas instrumentadas en celular: cuatro modos, anchos verticales de
  320/360 dp, horizontal de 720 dp, escala de fuente 1.3, seis indicadores,
  tiempo/distancia/llegada/rostro, retraso en transporte público, apertura de
  detalles/listado desplazable y acciones play/pausa/reanudar/stop.
- Se comprueba que los elementos accesibles quedan dentro de la tarjeta y que
  su altura respeta el presupuesto; además se revisan capturas visualmente para
  detectar texto cortado que una prueba de límites no detectaría.
- Las pruebas usan tarjetas sintéticas y no guardan elecciones ni viajes.

Capturas de validación en `output/ui-validation/route-summary-*.png`.
