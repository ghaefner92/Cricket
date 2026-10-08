"""Visible OSM tiles for USB clients, cached for at least seven days."""
import base64
import tempfile
import threading
import time
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import URLError

from flask import jsonify

CACHE = Path(tempfile.gettempdir()) / 'cricket-map-tiles-v1'
TTL = 7 * 24 * 60 * 60
LOCK = threading.Lock()


def register_map_tiles(app):
    @app.get('/api/dyconet/map-tiles/<int:z>/<int:x>/<int:y>')
    def map_tile(z, x, y):
        if not 0 <= z <= 19 or not 0 <= x < 2 ** z or not 0 <= y < 2 ** z:
            return jsonify(error='invalid_map_tile'), 400
        tile_path = CACHE / str(z) / str(x) / f'{y}.png'
        try:
            with LOCK:
                if tile_path.exists() and time.time() - tile_path.stat().st_mtime < TTL:
                    data = tile_path.read_bytes()
                else:
                    upstream = Request(
                        f'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                        headers={'User-Agent': 'Cricket/0.2 (+https://github.com/ghaefner92/Cricket)',
                                 'Accept': 'image/png'},
                    )
                    with urlopen(upstream, timeout=12) as response:
                        data = response.read()
                    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
                        return jsonify(error='invalid_map_tile_response'), 502
                    tile_path.parent.mkdir(parents=True, exist_ok=True)
                    tile_path.write_bytes(data)
            response = jsonify(data_url='data:image/png;base64,' + base64.b64encode(data).decode('ascii'))
            response.headers['Cache-Control'] = f'private, max-age={TTL}'
            return response
        except (URLError, TimeoutError, OSError):
            return jsonify(error='map_tiles_unavailable'), 503
