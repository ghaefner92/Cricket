# D-Day: comparación temporal y priors con intensidad V2

Entrega del 11 de octubre de 2026. Continúa el contrato de
[meteorología V2](D_DAY_WEATHER_INTENSITY_V2.md). Laboratorio offline, con datos
nuevos sintéticos: no convierte acumulados históricos ni usa experiencias del
autor. No requiere adquisición GPS. Conserva comodidad y afecto separados y no
actualiza el Passport activo, HOTCO, UI ni el servicio instalado.

## Diseño congelado antes de evaluar

`comfort_pilot/temporal_intensity_experiment.py` archiva `design.json` antes de
ejecutar. Once escenarios, dos objetivos, cuatro modos y dos repeticiones
independientes por combinación: 176 secuencias de 48 eventos, 8.448 respuestas
sintéticas. Los candidatos comparten eventos dentro de cada secuencia; los
objetivos tienen respuestas propias. Se puntúa desde el noveno evento y se
predice siempre antes de incorporar su respuesta. Historia completa retenida.

Escenarios: relación estable, prior inicial erróneo, cambio abrupto, cambio
gradual, cambio de clima sin cambio personal, episodio negativo pasajero,
calendario irregular con pausa de veinte días, cambio de relación con lluvia,
reportes horarios, reloj de lluvia desconocido y ausencia de meteorología.

Meteorología completa sin UV, intensidad en mm/h codificada con `log1p`, luz
sintética explícita en lux. Coeficientes generadores elegidos para esta simulación,
sin afirmación de validez psicológica o física. La verdad simulada se archiva
separada del contexto disponible al filtro. La fecha artificial de lluvia prueba
el contrato, sin simular que Orion real dispone actualmente de ese reloj.
Reportes antiguos o sin hora quedan ausentes, incluso cuando su crudo es cero.
La auditoría V2 se ejecuta antes de dar el contexto al modelo.

Siete controles/candidatos: Passport inicial fijo, básico acumulativo, promedio
básico, meteorológico acumulativo, meteorológico con q=0.15 o q=0.4 y promedio
meteorológico. q es desviación estándar del intercepto latente por raíz de día;
no porcentaje de olvido. Solo cambia el intercepto: los coeficientes de clima
permanecen fijos. El promedio actualiza pesos por evidencia después de observar
la respuesta; conserva los pesos anteriores para el pronóstico archivado.

Cinco configuraciones de priors: referencia inicial moderada y lluvia SD=0.35;
inicial débil o fuerte manteniendo lluvia; lluvia SD=0.7 o 0.175 manteniendo el
inicial. El prior de lluvia permanece centrado en cero. Afecto conserva el prior
individual original y no toma priors de comodidad de literatura. Son candidatos,
no parámetros empíricamente validados.
Las SD iniciales latentes son 2.4, 1.2 y 0.4 (débil, moderada, fuerte);
comodidad de bike/car usa procedencia estimada con factor 1.5, como control
del mecanismo existente. Estas procedencias también son sintéticas.

## Comparación y límites

`temporal_intensity_report.py` empareja por secuencia y evento exactos, incluyendo
comparaciones básico/clima cuando hay contexto suficiente. No usa todas las
predicciones básicas contra solo las meteorológicas disponibles. Promedia
primero dentro de cada secuencia y luego da el mismo peso a las ocho secuencias
por objetivo/escenario. Modos agrupados solo para descripción; dos repeticiones
por modo no sustentan conclusiones poblacionales.

Métricas: log score de la respuesta observada, RPS ordinal y error de expectativa
frente a verdad sintética. Cobertura del intervalo de expectativa latente se
conserva únicamente porque el simulador conoce esa expectativa; no evalúa la
cobertura de una respuesta individual. Contexto parcial es una prueba de estrés,
no calibración bajo un modelo generativo correctamente especificado.

El escenario de cambio de pendiente de lluvia evalúa un modelo deliberadamente
incompleto. Difusión del intercepto no identifica un cambio de relación con
lluvia; una mejora predictiva no demuestra atribución causal o psicológica.
Estabilidad de pronóstico tampoco demuestra precisión de HOTCO.
El reporte conserva cobertura/anchura por secuencia y cambio absoluto entre
pronósticos elegibles de pasos consecutivos. Ese cambio incluye variación de
contexto; no se interpreta como cambio personal ni mide estabilidad de la
recomendación HOTCO. En dropout no une pasos distantes para inventar continuidad.

## Auditoría numérica

`temporal_intensity_audit.py` congela tres casos y compara 1.024 y 4.096 partículas,
dos semillas por tamaño, frente a 16.384 partículas, para q=0, 0.15 y 0.4.
El acumulativo también se compara con MCMC independiente de 4.096 draws y 1.024
warmup. Casos: estable/comodidad, cambio de lluvia/afecto y reportes horarios/
comodidad; incluyen prior de lluvia SD=0.7 en el último.

Los diagnósticos MCMC pasaron en los tres casos. Diferencia máxima de expectativa
normalizada SMC–MCMC: 0.00378. Sensibilidad máxima seleccionada en log score:
0.00767 con 1.024 partículas y 0.00456 con 4.096. Son comprobaciones seleccionadas,
no cotas globales ni demostración exhaustiva de convergencia. Diferencias pequeñas
entre candidatos se interpretan con esta limitación, sin declarar ganador.

## Reglas candidatas de uso

`temporal_use_policy.json` conserva referencia acumulativa, historia completa,
fallback básico sin meteorología, lluvia desconocida/antigua ausente y objetivos
separados. Los candidatos dinámicos y sus priors quedan offline. No selecciona
una tasa universal ni activa automáticamente un posterior por resultados de
simulación. La activación operativa y los criterios empíricos de precisión son
decisiones posteriores, distintas del cierre técnico.

## Resultados descriptivos de esta ejecución

Se archivaron 236.800 pronósticos puntuados. La revisión verificó claves únicas,
probabilidades válidas, historia hasta el evento anterior, pesos de promedio
válidos, contexto meteorológico elegible y lluvia excluida realmente ausente.
Los contrastes básico/clima usan solo pasos comunes, incluido el escenario de
dropout; el conteo de respuestas no se usa como número de réplicas independientes.

Diferencia media de log score de q=0.4 respecto al clima acumulativo, prior de
referencia (positivo favorece q=0.4; ocho secuencias por celda):

| Escenario | Comodidad | Afecto |
|---|---:|---:|
| Relación estable | −0.0402 | −0.0415 |
| Cambio abrupto persistente | +0.1138 | +0.1738 |
| Cambio gradual | +0.0624 | +0.0316 |
| Cambio de clima sin cambio personal | −0.0311 | −0.0379 |
| Episodio pasajero negativo | −0.0388 | −0.0241 |
| Cambio de pendiente de lluvia | −0.0306 | −0.0292 |

q=0.15 y el promedio presentan costes menores en varios escenarios estables,
pero tampoco dominan todos los diseños. El cambio de pendiente no se resuelve
activando difusión del intercepto. Varias diferencias pequeñas son comparables
a la sensibilidad numérica seleccionada; no justifican selección concluyente.

En relación estable, la semianchura media del intervalo latente aumenta de
0.3164 a 0.5344 para comodidad y de 0.3194 a 0.5261 para afecto al pasar de
acumulativo a q=0.4. El cambio absoluto medio entre expectativas consecutivas
aumenta de 0.1265 a 0.1605 y de 0.1463 a 0.1631, respectivamente, incluyendo
variación climática. Un intervalo más ancho no implica una predicción más precisa.

Prior inicial fuerte con creencia equivocada: diferencia de log score frente
al moderado −0.1637 en comodidad y −0.1688 en afecto, bajo clima acumulativo.
El prior débil mejora +0.0169 y +0.0074 en ese caso. Esto muestra sensibilidad
del mecanismo bajo el generador elegido, sin determinar cuánta confianza merece
el Passport real. Variar solo la SD de lluvia no produce una mejora universal.

Regla conservada: acumulativo como referencia offline, alternativas archivadas
y activación posterior explícita. Los resultados no validan una tasa, un prior
o un criterio psicológico de persistencia para usuarios reales.

## Reproducción y resultados

Desde `app-backend/services/dyconet`:

```powershell
python -m comfort_pilot.temporal_intensity_experiment --output ../../../output/d-day-temporal-intensity-v2 --particles 1024 --repetitions 2 --steps 48
python -m comfort_pilot.temporal_intensity_audit --output ../../../output/d-day-temporal-intensity-v2
python -m comfort_pilot.temporal_intensity_report --output ../../../output/d-day-temporal-intensity-v2
python -m unittest discover -s tests -v
```

Artefactos: `output/d-day-temporal-intensity-v2/`, incluidos streams originales,
pronósticos previos a respuesta, métricas por secuencia/fase, auditoría numérica,
contrastes emparejados, política candidata e imagen `comparison.png`.
Los laboratorios V1 conservan contrato y comportamiento anteriores.

Verificación de código: 103 pruebas backend aprobadas. No hay cambios
Android en este bloque. El siguiente bloque del orden autorizado es versionar
el Passport aprendido y su linaje/reversión, con activación explícita y objetivos
separados; las tasas dinámicas no se promueven por defecto.
