"""Compress negotiated JSON responses without changing their evidence or schema."""

import gzip

from flask import request


def register_json_compression(app):
    @app.after_request
    def compress_json(response):
        if (response.mimetype != "application/json" or response.direct_passthrough
                or response.is_streamed or response.status_code == 206
                or "Content-Encoding" in response.headers
                or "Content-Range" in response.headers
                or "ETag" in response.headers):
            return response
        response.vary.add("Accept-Encoding")
        if request.method == "HEAD" or request.accept_encodings["gzip"] <= 0:
            return response
        body = response.get_data()
        if len(body) < 1024:
            return response
        compressed = gzip.compress(body, compresslevel=6, mtime=0)
        if len(compressed) < len(body):
            response.set_data(compressed)
            response.headers["Content-Encoding"] = "gzip"
        return response
