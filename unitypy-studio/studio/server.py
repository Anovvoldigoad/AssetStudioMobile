"""Small offline localhost-only server for Android browser GUI; no Flask required."""
from __future__ import annotations

import json
import mimetypes
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import secrets
from urllib.parse import parse_qs, urlparse

try:
    from .core import Studio
except ImportError:
    from core import Studio

ROOT = Path(__file__).resolve().parent


def make_handler(studio, secret):
    class Handler(BaseHTTPRequestHandler):
        def _json(self, status, data):
            payload = json.dumps(data, ensure_ascii=False, default=str).encode("utf-8")
            self._send(status, payload, "application/json; charset=utf-8")

        def _send(self, status, body, mime, filename=None):
            self.send_response(status)
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            if filename:
                from urllib.parse import quote
                self.send_header("Content-Disposition", f"attachment; filename*=UTF-8''{quote(filename)}")
            self.end_headers()
            self.wfile.write(body)

        def _qs(self):
            return {key: val[-1] for key, val in parse_qs(urlparse(self.path).query).items()}

        def _path(self):
            return urlparse(self.path).path

        def _authorized(self):
            return self.headers.get("X-Studio-Token") == secret

        def _incoming(self, maximum=4_000_000):
            size = int(self.headers.get("Content-Length", "0"))
            if size < 0 or size > maximum:
                raise ValueError(f"Ukuran request melebihi batas {maximum} byte")
            return self.rfile.read(size)

        def do_GET(self):
            try:
                path = self._path()
                if path in ("/", "/advanced"):
                    page = "mlbb.html" if path == "/" else "index.html"
                    html = (ROOT / page).read_text(encoding="utf-8")
                    html = html.replace("__STUDIO_TOKEN__", secret)
                    return self._send(200, html.encode(), "text/html; charset=utf-8")
                if path in ("/mlbb.js", "/mesh_viewer.js"):
                    return self._send(200, (ROOT / path.lstrip("/")).read_bytes(), "application/javascript; charset=utf-8")
                if path == "/api/health":
                    try:
                        import UnityPy
                        version = getattr(UnityPy, "__version__", "unknown")
                    except Exception as ex:
                        version = "Tidak tersedia: " + str(ex)
                    return self._json(200, {"ok": True, "unitypy": version})
                if not self._authorized():
                    return self._json(403, {"error": "Token sesi tidak valid"})
                if path == "/api/mlbb/overview":
                    return self._json(200, studio.mlbb_overview())
                if path == "/api/mlbb/textures":
                    return self._json(200, studio.mlbb_textures(self._qs().get("search", "")))
                if path == "/api/mlbb/related":
                    return self._json(200, studio.related_assets(self._qs()["id"]))
                if path == "/api/mlbb/assets":
                    q = self._qs()
                    return self._json(200, studio.mlbb_assets(q.get("category", ""), q.get("search", ""), q.get("offset", 0)))
                if path == "/api/summary":
                    return self._json(200, studio.summary())
                if path == "/api/assets":
                    q = self._qs()
                    return self._json(200, studio.list_assets(q.get("search", ""), q.get("type", ""), q.get("offset", 0)))
                if path == "/api/inspect":
                    q = self._qs()
                    return self._json(200, studio.inspect(q["id"], q.get("tree") == "1"))
                if path == "/api/rig":
                    return self._json(200, studio.rig_report(self._qs().get("target")))
                if path == "/api/export":
                    q = self._qs()
                    name, binary, mime = studio.export(q["id"], q.get("mode", "auto"))
                    return self._send(200, binary, mime, name)
                if path == "/api/preview":
                    q = self._qs()
                    _, binary, mime = studio.export(q["id"], q.get("mode", "auto"))
                    if mime not in ("image/png", "text/plain", "application/json"):
                        raise ValueError("Preview hanya untuk gambar, mesh OBJ, atau data teks")
                    return self._send(200, binary, mime)
                if path == "/api/download-output":
                    if not studio.last_output:
                        raise ValueError("Belum ada output repack")
                    path_obj = studio.last_output
                    return self._send(200, path_obj.read_bytes(), "application/octet-stream", path_obj.name)
                return self._json(404, {"error": "Endpoint tidak ditemukan"})
            except Exception as exc:
                return self._json(400, {"error": str(exc)})

        def do_POST(self):
            if not self._authorized():
                return self._json(403, {"error": "Token sesi tidak valid"})
            try:
                path = self._path()
                if path == "/api/upload":
                    length = int(self.headers.get("Content-Length", "0"))
                    file_path = studio.store_upload(self.rfile, length, self.headers.get("X-Filename", "upload.unity3d"))
                    return self._json(200, studio.open_project(file_path))
                if path == "/api/plan-replacements":
                    payload = self._incoming(96 * 1024 * 1024)
                    return self._json(200, studio.plan_replacements(payload))
                if path == "/api/edit-texture":
                    binary = self._incoming(64 * 1024 * 1024)
                    return self._json(200, studio.edit_texture(self._qs()["id"], binary))
                data = json.loads(self._incoming().decode("utf-8"))
                if path == "/api/open":
                    return self._json(200, studio.open_project(data["path"]))
                if path == "/api/archive-select":
                    return self._json(200, studio.select_archive_item(data["index"]))
                if path == "/api/apply-replacements":
                    return self._json(200, studio.apply_replacements(data["ids"]))
                if path == "/api/edit-animation":
                    return self._json(200, studio.edit_animation_loop(data["id"], data["loop"]))
                if path == "/api/edit-transform":
                    return self._json(200, studio.edit_transform(data["id"], data["values"]))
                if path == "/api/edit-material-color":
                    return self._json(200, studio.edit_material_color(data["id"], data["property"], data["rgba"]))
                if path == "/api/edit-text":
                    return self._json(200, studio.edit_text(data["id"], data["text"]))
                if path == "/api/edit-json":
                    return self._json(200, studio.edit_json(data["id"], data["tree"]))
                if path == "/api/batch-export":
                    name, binary, mime = studio.batch_export(data["ids"])
                    return self._send(200, binary, mime, name)
                if path == "/api/save":
                    return self._json(200, studio.save())
                return self._json(404, {"error": "Endpoint tidak ditemukan"})
            except Exception as exc:
                return self._json(400, {"error": str(exc)})

    return Handler


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Android-local UnityPy Studio")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    studio = Studio()
    token = secrets.token_urlsafe(24)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), make_handler(studio, token))
    print(f"UnityPy Studio: http://127.0.0.1:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        studio.close()
        server.server_close()


if __name__ == "__main__":
    main()
