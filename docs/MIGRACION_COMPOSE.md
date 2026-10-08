# Cricket: migración a Kotlin y Jetpack Compose

## Objetivo

Conservar las funciones y el arte pixel de Cricket, con navegación propia de
Android y tarjetas organizadas para teléfono. El proyecto nativo está en
`android-native`. El frontend Vue se conserva como versión web y referencia.

## Distribución

- Inicio: compañero, acceso al viaje y prioridades confirmadas.
- Viaje: buscar, comparar alternativas por modo, mapa y reflexión de la elección.
- Passport: metas, emociones, creencias y disponibilidad, en vistas separadas.
- Menú: conexión, accesibilidad visual, disponibilidad, compañero, historial y datos.
- Cuestionario: compañero y equipamiento, presupuesto de metas, cuatro emociones,
  cuatro conexiones y revisión explícita del Passport.

El presupuesto de metas y los controles de avance permanecen visibles mientras
se consulta una tarjeta. Las rutas se presentan por modo; los horarios posteriores
se agrupan por línea y paradas. La narración se lee por páginas y permite mostrar
todo el texto o los detalles de la simulación.

## Correspondencia de componentes

| Vue | Kotlin / Compose |
| --- | --- |
| App, CreateCompanionPage | CricketApp, Onboarding, Scaffold y barra inferior |
| LoadingScene | Importación inicial y estado de preparación |
| CompanionIdentityPage, AvailabilityKit | IdentityScreen, disponibilidad explícita |
| CompanionAvatar, RobotSprite, DialoguePortrait | CompanionSprite, hojas originales |
| GoalScene, PixelJourneyIcon, PixelHeart | GoalScene, GoalIcon, PixelIcon, TravelScene |
| WeeklyGoalsPage | GoalsScreen, tarjeta principal y lista de metas |
| ValencePage, AffectivePortrait, TransportScene | FeelingsScreen, RatingControl, TravelScene |
| BeliefsPage, BeliefScene | BeliefsScreen, calibración de cuatro preguntas |
| PassportPage | PassportScreen, revisión y confirmación |
| RoutePlanner, AddressSearch, RecentPlaces | JourneyScreen, SearchScreen, AddressField |
| RouteRecommendations, RouteJourneyCard | AlternativesScreen, RouteCardUi |
| RouteMap | NativeJourneyMap, Canvas, Mercator, gestos y geometría por tramo |
| TransitJourney | TransitDetails y grupos de salidas |
| WeatherInventory | WeatherUi, valores desconocidos y datos Orion guardados |
| ChoiceReflection, AvatarReaction | ReflectionScreen, páginas, retrato y catálogo verificado |
| SettingsMenu, BackendConnection | SettingsScreen y comprobación de conexión |
| SimulationHistory | HistoryScreen, HistoryDetails, exportación de simulación |
| stores, IndexedDB, dataTransfer | Store, SQLite, documentos Android e importación transaccional |
| tolerancias | Excluidas del cuestionario y del Passport nativo |

## Contratos conservados

Las API de HOTCO, onboarding adaptativo, routing, Orion, OTP y Groq siguen en el
backend Python. Se conserva la regla de recomendación por consenso entre
simulaciones; las activaciones de ejecuciones distintas no se comparan como una
única competición. Se conserva la identidad de itinerarios, elecciones y evidencia.
Las alternativas independientes y los trazados aproximados siguen señalados.

Los nuevos Passport omiten `environmental_tolerances` en el bootstrap. Las
búsquedas nativas envían `tolerance_profile: {}` y routing lo propaga al solver.
Esto evita usar tolerancias históricas sin inventar valores sustitutos. Los
Passport históricos permanecen intactos para conservar su identidad. Esta
migración no sustituye el solver por el modelo bayesiano propuesto previamente.

## Datos anteriores

Se mantiene el ID Android `de.ovgu.imiq.cricket` y la firma debug del equipo para
actualizar la app conservando su directorio privado. En el primer inicio, un
WebView local se usa únicamente para extraer localStorage e IndexedDB de la
versión Capacitor. La interfaz cotidiana es Compose; no carga las páginas Vue.

La importación de claves y búsquedas se realiza dentro de una transacción SQLite.
El origen antiguo se conserva. Si la importación falla se muestra el error y un
botón de reintento. Las copias de usuario siguen usando `cricket-backup-v1`;
las importaciones archivan el Passport anterior y rechazan IDs de viaje en conflicto.
La revisión de respuestas es explícita y permite cancelar un Passport nuevo.

## Compilación y revisión realizadas

Se ha compilado e instalado la APK nativa en el Samsung conectado. En el primer
inicio se observaron un Passport importado, cuatro búsquedas y quince claves
locales. Se revisó visualmente la pantalla de inicio conservando Circk y el arte.
La compilación no equivale a una validación completa de todos los flujos y servicios.
No se añadieron ni ejecutaron suites de pruebas automáticas.

El tracking continuo y el aprendizaje bayesiano siguen siendo fases posteriores;
no formaban parte de las funciones operativas del frontend que se migra aquí.
