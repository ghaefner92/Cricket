# Journey routing mode aliases — 0.4.23

Device inspection found the latest chosen route stored as `foot`, alongside `bike`, `car`, `pt` alternatives. The shared journey contract accepted only `walk`, `bike`, `pt`, `car`, so TrackingEngine.create failed validation before a GPS or manual journey could be saved.

The selection whitelist now accepts routing aliases `foot`, `car_driver`, `pt_bus_tram` as well as the four canonical modes. Selection and itinerary identity comparisons retain their exact original routing keys. Saved searches and choices are not rewritten.

Confirmed experience actual_modes stays canonical. ExperienceForm maps the selected routing key through modelMode; Android and backend as_planned comparisons perform the same canonicalization. Unknown selections and incorrect actual modes remain rejected.

Regression results:

- Android: 8 Phase1Test methods passed, including alias creation and confirmation for both tracked and manual captures across seven routing keys, plus mismatched confirmation rejection.
- Backend: 5 Phase1Contracts tests passed, including alias cases for manual confirmed, tracked finished and recording states and the validation API.
- Debug APK compiled; 0.4.23 installed.
- Backend restarted on 8077 and its live contract endpoint confirms the updated whitelist; health status ok.

No field GPS journey was performed. The tests exercise creation, contracts and confirmation without creating artificial journeys in the user's device history.
