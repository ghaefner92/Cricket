# Route goals and personal valence — 0.4.21

Shared RouteProfileIcons renders in every compact map route panel and every detailed route card, including modes that are not the recommendation. Replaces the three text rows previously under Connections in your profile.

- Each 48 dp goal touch target displays an existing 28 dp JRPG goal sprite. All positive links for goals with positive priority are included, sorted by needs × belief strength. The single horizontal row scrolls without extending the card vertically.
- Goal dialogs show the goal title and the positive association saved in the profile. Direct measurements and initial estimates are distinguished using observed_measurements.beliefs_raw_1_to_7. These are profile links, not observed travel outcomes or isolated causal attribution of HOTCO's recommendation.
- A distinct round face depicts personal valence and a seven-position indicator. The confirmed profile's normalized [-1,1] value is mapped back to the questionnaire [-3,+3] via ×3. Missing values remain unknown rather than neutral.
- Tapping the face opens the seven-step display, selected personal value, verbal scale meaning and explanation. This is a readout; it does not silently modify the passport or recommendation.
- Profile data comes from the cognitive_passport in the stored search request; current confirmed passport is a fallback. Draft onboarding feelings are never substituted into a historical search.
- When no positive goal links exist, an information icon opens a concise explanation rather than fabricated goals.
- Existing motion settings control goal sprite animations. Valence faces are native Compose drawings, not new bitmap assets.

Validation: debug APK compiles. No automated tests or physical journey trial requested for this change.
