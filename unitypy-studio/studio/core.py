"""UnityPy-backed asset inspector and non-destructive editor.

UnityPy is imported only on demand so the UI can show an actionable installation error.
"""
from __future__ import annotations

import base64
from collections import Counter
from io import BytesIO
import json
import os
from pathlib import Path
import re
import tempfile
import threading
import zipfile


def _safe_name(value: object) -> str:
    value = re.sub(r"[^\w. -]", "_", str(value or "unnamed"), flags=re.UNICODE)
    return value.strip(" .")[:90] or "unnamed"


def _jsonable(value):
    if isinstance(value, bytes):
        return {"__studio_b64__": base64.b64encode(value).decode("ascii")}
    if isinstance(value, dict):
        return {str(key): _jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_jsonable(item) for item in value]
    if isinstance(value, (int, float, bool, str)) or value is None:
        return value
    return str(value)


def _unjsonable(value):
    if isinstance(value, dict):
        if set(value) == {"__studio_b64__"}:
            return base64.b64decode(value["__studio_b64__"], validate=True)
        return {key: _unjsonable(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_unjsonable(item) for item in value]
    return value


def _obj_name(obj):
    try:
        return obj.peek_name() or f"{obj.type.name}_{obj.path_id}"
    except Exception:
        return f"{obj.type.name}_{obj.path_id}"


def _bone_names(renderer):
    result = []
    for pointer in getattr(renderer, "m_Bones", []) or []:
        try:
            transform = pointer.deref()
            if not transform:
                result.append("<missing>")
                continue
            go_ptr = getattr(transform, "m_GameObject", None)
            game_object = go_ptr.deref() if go_ptr else None
            result.append(getattr(game_object, "m_Name", None) or "<unnamed>")
        except Exception:
            result.append("<unresolved>")
    return result


class Studio:
    def __init__(self):
        self.lock = threading.RLock()
        self.tmp = tempfile.TemporaryDirectory(prefix="unitypy_studio_")
        self.root = Path(self.tmp.name)
        self.env = None
        self.handle = None
        self.path = None
        self.objects = []
        self.dirty = set()
        self.last_output = None
        self.version = None

    def close(self):
        with self.lock:
            if self.handle:
                self.handle.close()
            self.handle = None
            self.tmp.cleanup()

    def open(self, path):
        import UnityPy
        file_path = Path(path).expanduser().resolve(strict=True)
        if not file_path.is_file():
            raise ValueError("Lokasi harus menunjuk ke file, bukan folder")
        # Keep the backing file open for UnityPy lazy data / resource access.
        new_handle = file_path.open("rb")
        try:
            new_env = UnityPy.load(new_handle)
            new_objects = list(new_env.objects)
        except Exception:
            new_handle.close()
            raise
        with self.lock:
            old = self.handle
            self.env = new_env
            self.handle = new_handle
            self.objects = new_objects
            self.path = file_path
            self.dirty.clear()
            self.last_output = None
            self.version = getattr(UnityPy, "__version__", "unknown")
            if old:
                old.close()
            return self.summary()

    def store_upload(self, stream, length, filename="upload.unity3d"):
        if length < 1 or length > 1024 * 1024 * 1024:
            raise ValueError("Batas unggahan 1 GiB; gunakan fitur buka lewat path untuk berkas besar")
        target = self.root / ("input_" + _safe_name(filename))
        with target.open("wb") as out:
            remaining = length
            while remaining:
                block = stream.read(min(1024 * 1024, remaining))
                if not block:
                    raise ValueError("Berkas terpotong saat diunggah")
                out.write(block)
                remaining -= len(block)
        return target

    def _require(self):
        if self.env is None:
            raise ValueError("Buka file Unity terlebih dahulu")

    def _get(self, index):
        self._require()
        index = int(index)
        if index < 0 or index >= len(self.objects):
            raise ValueError("Asset ID tidak ditemukan")
        return self.objects[index]

    def summary(self):
        self._require()
        return {
            "path": str(self.path), "filename": self.path.name,
            "bytes": self.path.stat().st_size, "unitypy": self.version,
            "objects": len(self.objects),
            "types": dict(Counter(obj.type.name for obj in self.objects)),
            "changes": sorted(self.dirty),
            "save_supported": getattr(self.env, "file", None) is not None,
        }

    def list_assets(self, search="", asset_type="", offset=0, limit=100):
        with self.lock:
            self._require()
            search = search.casefold().strip()
            offset, limit = max(int(offset), 0), min(max(int(limit), 1), 500)
            found = []
            for i, obj in enumerate(self.objects):
                t = obj.type.name
                if asset_type and t != asset_type:
                    continue
                name = _obj_name(obj)
                if search and search not in name.casefold() and search not in t.casefold() and search not in str(obj.path_id):
                    continue
                found.append({"id": i, "type": t, "name": name,
                              "path_id": str(obj.path_id), "size": getattr(obj, "byte_size", None),
                              "edited": i in self.dirty})
            return {"total": len(found), "offset": offset, "items": found[offset:offset + limit]}

    def inspect(self, index, include_tree=False):
        with self.lock:
            obj = self._get(index)
            info = {"id": int(index), "type": obj.type.name, "name": _obj_name(obj),
                    "path_id": str(obj.path_id), "bytes": getattr(obj, "byte_size", None),
                    "edited": int(index) in self.dirty}
            try:
                data = obj.parse_as_object()
                info["metadata"] = {}
                for key in ("m_Name", "m_Width", "m_Height", "m_VertexCount", "m_BoneNameHashes", "m_AnimationType", "m_Length"):
                    value = getattr(data, key, None)
                    if isinstance(value, (str, int, float, bool)):
                        info["metadata"][key] = value
                if obj.type.name == "SkinnedMeshRenderer":
                    info["bones"] = _bone_names(data)
                if obj.type.name == "TextAsset":
                    script = getattr(data, "m_Script", "")
                    info["text"] = script[:100000] if isinstance(script, str) else None
            except Exception as exc:
                info["read_error"] = str(exc)
            if include_tree:
                try:
                    info["tree"] = _jsonable(obj.parse_as_dict())
                except Exception as exc:
                    info["tree_error"] = str(exc)
            return info

    def export(self, index, mode="auto"):
        with self.lock:
            obj = self._get(index)
            typ, name = obj.type.name, _safe_name(_obj_name(obj))
            if mode == "raw":
                return name + ".bin", obj.get_raw_data(), "application/octet-stream"
            if mode == "json":
                payload = json.dumps(_jsonable(obj.parse_as_dict()), ensure_ascii=False, indent=2)
                return name + ".json", payload.encode("utf-8"), "application/json"
            data = obj.parse_as_object()
            if typ in ("Texture2D", "Sprite"):
                dst = BytesIO()
                data.image.save(dst, format="PNG")
                return name + ".png", dst.getvalue(), "image/png"
            if typ == "Mesh":
                return name + ".obj", data.export().encode("utf-8"), "text/plain"
            if typ == "TextAsset":
                script = data.m_Script
                raw = script.encode("utf-8", "surrogateescape") if isinstance(script, str) else bytes(script)
                return name + ".txt", raw, "application/octet-stream"
            if typ == "AudioClip":
                samples = data.samples
                if not samples:
                    raise ValueError("Tidak ada sampel audio yang dapat diekspor")
                if len(samples) == 1:
                    key, value = next(iter(samples.items()))
                    return _safe_name(key) + ".wav", value, "audio/wav"
                out = BytesIO()
                with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
                    for key, value in samples.items():
                        z.writestr(_safe_name(key) + ".wav", value)
                return name + "_audio.zip", out.getvalue(), "application/zip"
            if typ == "Font" and getattr(data, "m_FontData", None):
                raw = bytes(data.m_FontData)
                return name + (".otf" if raw[:4] == b"OTTO" else ".ttf"), raw, "application/octet-stream"
            if typ == "Texture2DArray":
                out = BytesIO()
                with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
                    for i, img in enumerate(data.images):
                        png = BytesIO()
                        img.save(png, format="PNG")
                        z.writestr(f"{name}_{i}.png", png.getvalue())
                return name + "_textures.zip", out.getvalue(), "application/zip"
            return self.export(index, mode="json")

    def batch_export(self, indices):
        with self.lock:
            self._require()
            if not isinstance(indices, list) or len(indices) > 5000:
                raise ValueError("Pilih maksimal 5000 aset untuk sekali ekspor")
            out = BytesIO()
            report = {"success": 0, "errors": []}
            with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, allowZip64=True) as z:
                for raw_idx in dict.fromkeys(indices):
                    try:
                        idx = int(raw_idx)
                        obj = self._get(idx)
                        name, contents, _ = self.export(idx)
                        z.writestr(f"{_safe_name(obj.type.name)}/{idx}_{name}", contents)
                        report["success"] += 1
                    except Exception as exc:
                        report["errors"].append({"id": str(raw_idx), "error": str(exc)})
                z.writestr("EXPORT_REPORT.json", json.dumps(report, indent=2, ensure_ascii=False))
            return "UnityPyStudio_Export.zip", out.getvalue(), "application/zip"

    def edit_text(self, index, new_text):
        with self.lock:
            obj = self._get(index)
            if obj.type.name != "TextAsset":
                raise ValueError("Editor teks hanya untuk TextAsset")
            if not isinstance(new_text, str) or len(new_text) > 4_000_000:
                raise ValueError("Teks terlalu besar atau tidak valid")
            data = obj.parse_as_object()
            data.m_Script = new_text
            obj.patch(data)
            self.dirty.add(int(index))
            self.last_output = None
            return self.inspect(index)

    def edit_texture(self, index, png):
        from PIL import Image
        with self.lock:
            obj = self._get(index)
            if obj.type.name != "Texture2D":
                raise ValueError("Penggantian PNG hanya untuk Texture2D")
            if len(png) > 64 * 1024 * 1024:
                raise ValueError("PNG terlalu besar (maksimal 64 MiB)")
            img = Image.open(BytesIO(png))
            img.load()
            if img.width * img.height > 8192 * 8192:
                raise ValueError("Resolusi gambar terlalu besar")
            data = obj.parse_as_object()
            data.image = img.convert("RGBA")
            obj.patch(data)
            self.dirty.add(int(index))
            self.last_output = None
            return self.inspect(index)

    def edit_json(self, index, tree):
        with self.lock:
            obj = self._get(index)
            if not isinstance(tree, dict):
                raise ValueError("TypeTree JSON harus berupa object")
            existing = obj.parse_as_dict()
            if set(tree) != set(existing):
                raise ValueError("Kunci paling luar TypeTree tidak boleh ditambah atau dihapus")
            obj.patch(_unjsonable(tree))
            self.dirty.add(int(index))
            self.last_output = None
            return self.inspect(index)

    def rig_report(self, other_path=None):
        import UnityPy
        def collect(objects):
            renderers = []
            for i, obj in enumerate(objects):
                if obj.type.name != "SkinnedMeshRenderer":
                    continue
                try:
                    bones = _bone_names(obj.parse_as_object())
                    renderers.append({"id": i, "name": _obj_name(obj), "bones": bones,
                                      "count": len(bones)})
                except Exception as exc:
                    renderers.append({"id": i, "error": str(exc), "bones": [], "count": 0})
            return renderers
        with self.lock:
            self._require()
            source = collect(self.objects)
            result = {"source": source}
            if other_path:
                with open(other_path, "rb") as other:
                    other_env = UnityPy.load(other)
                    target = collect(list(other_env.objects))
                left = set(name for item in source for name in item["bones"])
                right = set(name for item in target for name in item["bones"])
                result.update({"target": target, "shared": sorted(left & right),
                               "source_only": sorted(left - right), "target_only": sorted(right - left)})
            return result

    def save(self):
        import UnityPy
        with self.lock:
            self._require()
            if not self.dirty:
                raise ValueError("Belum ada perubahan yang disimpan")
            root = getattr(self.env, "file", None)
            if root is None:
                raise ValueError("Sumber multi-file tidak didukung untuk repack otomatis")
            # Never overwrite originals. Save into the temporary workspace for download.
            out_path = self.root / ("MOD_" + _safe_name(self.path.name))
            tmp_path = self.root / (out_path.name + ".tmp")
            with tmp_path.open("wb") as f:
                f.write(root.save())
            if tmp_path.stat().st_size == 0:
                raise ValueError("Output repack kosong")
            with tmp_path.open("rb") as f:
                check = UnityPy.load(f)
                count = sum(1 for _ in check.objects)
            if count != len(self.objects):
                tmp_path.unlink(missing_ok=True)
                raise ValueError(f"Verifikasi repack gagal: {count} objek, seharusnya {len(self.objects)}")
            os.replace(tmp_path, out_path)
            self.last_output = out_path
            return {"name": out_path.name, "bytes": out_path.stat().st_size, "objects": count,
                    "note": "Struktur telah dibuka ulang; kompatibilitas gameplay tetap harus diuji"}
