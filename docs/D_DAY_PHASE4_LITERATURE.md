# D-Day fase 4 — referencias y procedencia de priors

Consulta: 9 de octubre de 2026. Separamos evidencia publicada, inferencias
transferibles y decisiones experimentales del piloto. Ninguna fuente valida
HOTCO-CT ni proporciona coeficientes para nuestro nuevo ítem ayuda–dificulta.

| Fuente primaria | Evidencia pertinente | Uso y límite |
|---|---|---|
| [Kenny et al., 2009, COMFA](https://pubmed.ncbi.nlm.nih.gov/19396470/), DOI 10.1007/s00484-009-0226-3 | Estudio de campo con 27 sujetos realizando actividad física; relaciona sensación térmica con condiciones ambientales y actividad | Justifica investigar temperatura, viento y entorno térmico. Sensación térmica no equivale a satisfacción de comodidad física; no trasladamos sus coeficientes |
| [Kim y Macdonald, 2016](https://escholarship.org/content/qt6gz6t90p/qt6gz6t90p.pdf), DOI 10.3390/su8030257 | Estudio de campo sobre viento, microclima y elección de transporte en San Francisco | Motiva exposición contextual por modo. Otra ciudad, resultado y cuestionario; no estima nuestro efecto causal ni nuestro prior numérico |
| [Schad, Betancourt y Vasishth, workflow bayesiano](https://arxiv.org/abs/1904.12765) | Priors fundamentados, comprobaciones predictivas y evaluación del cálculo | Separamos prior predictivo, calibración computacional, sensibilidad y evaluación temporal |
| [Stan: regresión ordinal](https://mc-stan.org/docs/stan-users-guide/regression.html#ordered-logistic-and-probit-regression) | Likelihood por categorías ordenadas y problemas de identificación | Adoptamos logistic ordinal; fijar nuestros puntos de corte es una decisión explícita del piloto |
| [Stan: calibración mediante simulaciones](https://mc-stan.org/docs/stan-users-guide/simulation-based-calibration.html) | Comprobación de inferencia bajo datos generados desde el modelo | Los rangos y cobertura evalúan el algoritmo bajo sus supuestos; no validan el constructo psicológico |
| [Vehtari et al., diagnósticos MCMC](https://arxiv.org/abs/1903.08008) | R-hat por rangos, plegado y eficiencia local | Cuatro cadenas, R-hat ≤ 1.01 y ESS de cuerpo/colas ≥ 100 como filtros computacionales exploratorios |

El prior principal tiene pendientes centradas en cero. No impone efectos
meteorológicos obtenidos de otra escala. La localización del intercepto proviene
del Passport original, conservando si la celda fue declarada o estimada por Ridge.
Los candidatos de desviación 2.4, 1.2 y 0.4, el multiplicador 1.5 para una celda
estimada, las pendientes 0.35 y los indicadores de falta 0.2 son **decisiones
experimentales**, sin estimación empírica publicada para este instrumento.

La variante `literature_shape=true` ensaya medias −0.15 para lluvia y temperatura
cuadrática. Es una hipótesis cualitativa candidata inspirada en investigar
confort ambiental, no un efecto estimado por los artículos ni un prior validado.
Está desactivada en el modelo principal y se compara por sensibilidad.
La referencia térmica 20 °C y las escalas de covariables también son supuestos.
Iluminancia en lux no es radiación térmica en W/m².

Antes de usar población, estimar priors desde estudios compatibles exige conocer
el instrumento, escalas, unidades, población, incertidumbre y transportabilidad.
El piloto individual conserva por ahora la declaración inicial y evalúa
cuánto depende de ella la predicción posterior.
