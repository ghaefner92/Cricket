# D-Day: Passport aprendido versionado

Entrega 0.4.63, 11 de octubre de 2026. Continúa el orden autorizado en
[D_DAY.md](D_DAY.md), después de la comparación temporal V2. Implementa versiones
persistentes del aprendizaje acumulativo y su reproducción. Activación del
Passport aprendido e integración con HOTCO siguen separadas y deshabilitadas.

## Dos cadenas enlazadas al mismo original

La app conserva las revisiones de declaraciones/metas/evidencia existentes.
Nueva cadena `d-day-learned-passport-v1`: identifica el Passport original y su
digest, antecesor aprendido, revisión, Experience desencadenante, corte temporal,
evidencia incorporada, configuración, contexto auditado y posteriores por
objetivo/modo. No sustituye `profile.beliefs`, `profile.valences`, otras necesidades
ni metas diarias del Passport activo. Una versión aprendida representa expectativas
contextuales y su incertidumbre, sin reinterpretar la declaración original.

Cada Experience elegible crea una versión aprendida, aunque aporte respuestas
para dos objetivos o varios modos. Una Experience excluida, cancelada, no realizada
o sin respuestas versionadas pertinentes produce un resultado sin nueva versión;
la exclusión queda archivada en el trabajo. Ausencia de GPS no identifica una
prueba. Neutral afectivo cero sigue siendo respuesta explícita.

El primer procesamiento puede reconstruir una versión por cada Experience
histórica elegible, en orden de confirmación e ID. Con un registro de exclusiones
explícito, Android prepara trabajos históricos separados antes de la primera
incorporación; evita devolver toda la reconstrucción retrospectiva en una sola
respuesta móvil. El nuevo cliente identifica
original/individuo en cada registro compacto; el backend rechaza otra procedencia.
En una nueva confirmación, el corte incluye esa Experience y excluye respuestas
posteriores. Pronosticar con la versión requiere una fecha posterior a su evidencia.

## Aprendizaje contado una sola vez

La estrategia es `original_prior_plus_unique_full_history`. Para los objetivos/
modos afectados se reconstruye el posterior desde el prior original y todas sus
observaciones únicas. No se toma el posterior anterior como prior para volver a
incorporar toda la historia. Los modelos de modos no afectados se conservan.

Se mantienen modelo básico y contextual de comodidad y afecto separados, con
escala bipolar 1–7 y afecto −3…+3 originales. Afecto usa su valencia inicial;
comodidad conserva procedencia declarada/estimada. Priors moderados, lluvia
N(0,0.35²), cutpoints y meteorología V2 son candidatos congelados, sin validación
empírica. Sin difusión ni olvido. No reutiliza posteriores/coeficientes V1.

Muestreo de cuatro cadenas, 512 draws y 256 warmup, semilla reproducible por
evidencia/objetivo/modo/modelo. Si los diagnósticos no pasan, reintenta con 4.096
draws y conserva ambos intentos. Un posterior con diagnóstico insuficiente se
archiva como experimental y no se declara utilizable para investigación. No hay
un mínimo arbitrario de tres experiencias ni una regla de activación implícita.

## Archivo y recuperación

Android guarda en su SQLite existente, namespace
`imiq.experimental.learned-passport.v1`:

- `.job.<event_id>`: trabajo durable, corte temporal, solicitud congelada y resultado/error.
- `.version.<passport_id>`: snapshot inmutable con posteriores completos.
- `.tip.<original_id>`: última versión calculada.
- `.review.<original_id>`: selección experimental explícita, independiente del tip.
- `.selections.<original_id>`: historial de cambios de selección experimental.

La confirmación de Experience y la creación del trabajo se guardan en la misma
transacción. Una cola serial procesa trabajos por tiempo e ID en segundo plano;
al abrir/refrescar la app reanuda pendientes y fallidos. Conserva solicitudes
congeladas en los reintentos. Ante fallo de un linaje, detiene sus trabajos
posteriores en ese pase para mantener el orden; otros linajes pueden continuar.
La captura de contexto y la UI no esperan el cálculo bayesiano.

Resultado y versiones se confirman en una transacción SQLite, comprobando que
el tip sigue siendo el antecesor solicitado. Conflicto o interrupción antes del
commit no deja una actualización parcial. Repetir un trabajo completado no
duplica versión ni cambia el tip. La app mantiene `PASSPORT` activo independiente.

Los posteriores se almacenan en float64 little endian, comprimidos zlib/base64,
con dimensiones, digest, diagnósticos, priors, semillas e IDs de evidencia. Se
verifica longitud máxima, terminación de compresión, integridad y valores finitos
antes de reutilizarlos. IDs/digests aprendidos usan una codificación tipada común
de cadenas UTF-8 y números binary64, soportando el round-trip de `JSONObject`
(1.0 → 1), exponentes y Unicode. Rechaza enteros fuera del rango exacto de 53 bits.
Python y Android verifican el digest del snapshot completo y de la solicitud;
una modificación no conserva un ID válido.

Exportación/importación conserva las nuevas claves y valida ancestros, originales,
referencias y trabajos completados. La sustitución de un backup preserva archivos
inmutables existentes y rechaza conflictos. El índice longitudinal V1 mantiene
su significado histórico; el namespace nuevo guarda la cadena aprendida.

El archivo SQLite offline `comfort_pilot.learned_passport.Archive` conserva
solicitudes, versiones, resultados y selección; permite reabrir y reproducir sin
depender de datos que solo estaban en memoria. La reproducción exacta se verifica
en el mismo runtime, cuyos Python/NumPy/SciPy quedan registrados. Cambios de
dependencias no garantizan igualdad bit a bit; el posterior archivado sigue
disponible para predecir sin reajustar.

## Reversión y cambios de evidencia

Seleccionar una versión anterior o el original para revisión experimental no
elimina evidencia ni archivos, no cambia el tip de incorporación y no modifica
HOTCO. Volver al tip es otra selección trazable. No es todavía una reversión de
entradas HOTCO: esa integración pertenece al siguiente bloque.

Si una respuesta ya incorporada cambia, se elimina, se excluye después o aparece
un evento anterior que altera el prefijo, el backend rechaza continuar esa cadena.
Una reconstrucción explícita se realiza en un archivo separado, desde el original
y la evidencia corregida; no sobrescribe retrospectivamente versiones previas.
Configuraciones distintas también requieren una cadena separada. No se ofrece
todavía esa operación en UI.

La prueba estacionaria del autor sigue excluida mediante el registro privado
documentado anteriormente. Antes del primer aprendizaje retrospectivo Android
exige configurar el registro de exclusiones si hay experiencias anteriores al
evento desencadenante. Evita incorporar silenciosamente la prueba conocida por
faltar todavía ese registro en el teléfono. Importar un registro explícito vacío
es válido para un historial revisado sin exclusiones; no clasifica automáticamente
un viaje por falta de GPS. Los trabajos sin registro quedan fallidos y recuperables,
sin versión aprendida ni modificación del Passport activo.

## Predicción desde una versión

`/api/dyconet/d-day/learned-passport/update` construye versiones; devuelve datos
para persistencia del cliente, sin guardarlos en servidor.
`/api/dyconet/d-day/learned-passport/predict` usa el posterior archivado, identificando
versión, objetivo, modo y contexto. Sin clima utilizable usa el básico; con clima
parcial mantiene ausencia, auditoría, variables sin evidencia y rangos no observados.
Modo sin experiencias solicita el prior original; no inventa un posterior aprendido.
Lluvia desconocida/antigua sigue ausente según V2; acumulado no es predictor.
Los endpoints no activan expectativas ni cambian la ODE/conexiones HOTCO.

## Verificación y estado operativo

`validation/verify_learned_passport.py` usa exclusivamente fixtures sintéticos:
dos Experience, cuatro observaciones objetivo/modo, dos versiones. Verifica
equivalencia secuencial/reconstrucción, reintentos, exclusión estacionaria,
persistencia tras reapertura y reversión de selección. Contexto sintético para
probar contratos, sin afirmar exposición real o precisión de sensores.

113 pruebas backend y 56 pruebas JVM Android aprobadas. Diez pruebas backend
nuevas cubren también commits fallidos, cambio de evidencia, raíces ajenas,
alteración de archivos, predicción y backups; siete JVM usan el fixture compartido.
APK 0.4.63 (code 74) compilado. Dos pruebas instrumentadas de SQLite Android
compiladas en APK de pruebas; no ejecutadas en teléfono en esta entrega.

Artefactos: `output/d-day-learned-passport/`; fixture compartido en
`validation/fixtures/learned_passport_cases.json`. No se instala APK, no se
reinicia backend ni se adquieren viajes reales. Registro privado de exclusiones
en teléfono y prueba instrumentada siguen como verificaciones operativas.
Siguiente bloque: integración contextual HOTCO con selección identificable y
reglas de uso explícitas, conservando desactivación por defecto.

Reproducción:

```powershell
python validation/verify_learned_passport.py
```

Desde `app-backend/services/dyconet`:

```powershell
python -m unittest discover -s tests -v
python -m comfort_pilot.learned_passport input.json --output versions.json
```

Desde `android-native`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```
