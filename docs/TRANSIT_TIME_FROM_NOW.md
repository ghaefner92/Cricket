# Public transport: arrival time from now

Companion 0.4.34 (Android version code 45).

## Calculation

OTP's `summary.duration_seconds` covers the itinerary from departure at the origin to arrival at the destination. It includes walking legs and waits within the itinerary. It does not include the time before the itinerary starts.

For a valid, selectable transit itinerary with departure and arrival timestamps:

- Time before leaving the origin: `max(0, (start_time_ms - current_time_ms) / 1000)`.
- Total time until arrival from now: `(end_time_ms - current_time_ms) / 1000`.
- Displayed countdown minutes round upward and refresh every 15 seconds while the route screen is shown.

Example: 8 minutes before leaving the origin plus a 25 minute itinerary displays 33 minutes from now. The first bus or tram departure is later than the origin departure when the itinerary begins with walking. Adding the wait until boarding to the complete itinerary would count that initial walking time twice.

## Interface

The map comparison, chosen journey preview, route details and alternative departures share the countdown calculation. The compact map panel shows when to leave and the expected arrival time. Expanded details distinguish time before leaving from itinerary duration and label waiting inside the itinerary separately.

Missing or invalid timestamps fall back to the original itinerary duration labelled as journey time. Details explain when the countdown is unavailable. Existing expired departure warnings and selection restrictions are preserved.

## Data and scope

The countdown uses the phone clock and the arrival timestamp in the search response. It does not fetch fresh delay predictions on each countdown tick; a new search retrieves updated itinerary data.

The original OTP summary, saved choice and HOTCO inputs are preserved. This change corrects presentation and does not add predeparture waiting as a new recommendation signal.

## Delivery

The Android debug APK compiled successfully. No automated tests were added or run for this change.
