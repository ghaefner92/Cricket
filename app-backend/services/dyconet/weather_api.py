"""Fresh home weather without routing, passport data or sensor timestamp inference."""
import math
from datetime import datetime, timezone
from flask import jsonify, request
from orion_context import OrionProviderError
from orion_query_list import OrionQueryListProvider


def register_weather(app):
    @app.get('/api/dyconet/weather')
    def weather():
        try:
            lat, lon = float(request.args.get('lat', '52.13')), float(request.args.get('lon', '11.62'))
            if not math.isfinite(lat) or not math.isfinite(lon) or abs(lat) > 90 or abs(lon) > 180:
                raise ValueError()
        except (TypeError, ValueError):
            return jsonify(error='invalid_weather_location'), 400
        queried = datetime.now(timezone.utc).isoformat()
        try:
            # New provider per request: its QueryList never survives a Home visit.
            readings = OrionQueryListProvider().get_weather(
                geometry='point', coords=f'{lat},{lon}', georel='near;maxDistance:5000', nearest_only=False)
            response = jsonify(schema_version='cricket-weather-v1', queried_at=queried,
                               status='AVAILABLE' if readings else 'EMPTY',
                               raw_observations=[item.to_dict() for item in readings])
            response.headers['Cache-Control'] = 'no-store'
            return response
        except OrionProviderError:
            app.logger.warning('Orion weather retrieval failed', exc_info=True)
            response = jsonify(schema_version='cricket-weather-v1', queried_at=queried,
                               status='UNAVAILABLE', raw_observations=[])
            response.headers['Cache-Control'] = 'no-store'
            return response, 503
