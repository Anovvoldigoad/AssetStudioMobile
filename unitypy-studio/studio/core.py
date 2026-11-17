"""UnityPy-backed asset inspector and non-destructive editor.

UnityPy is imported only on demand so the UI can show an actionable installation error.
"""
from __future__ import annotations

import base64
from collections import Counter
from io import BytesIO
import json
import math
import os
from pathlib import Path
import re
import tempfile
import threading
import zipfile

from . import mlbb


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
        self.archive_items = []
        self.replacement_plan = {}
        self.replacement_report = None
        self._mlbb_catalog = None

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
            self._mlbb_catalog = None
            self.replacement_plan.clear()
            self.replacement_report = None
            if old:
                old.close()
            return self.summary()

    def open_project(self, path):
        file_path = Path(path).expanduser().resolve(strict=True)
        if file_path.suffix.lower() == ".zip":
            return self.open_archive(file_path)
        with self.lock:
            self.archive_items = []
        return self.open(file_path)

    def open_archive(self, path):
        """Safe ZIP extraction with hard uncompressed size/file-count limits.

        External streaming dependencies may not resolve, so report this on the UI.
        """
        root = self.root / ("archive_" + os.urandom(6).hex())
        root.mkdir(exist_ok=True)
        candidates = []
        max_total = 768 * 1024 * 1024
        with zipfile.ZipFile(path) as z:
            members = [m for m in z.infolist() if not m.is_dir()]
            if len(members) > 3000 or sum(m.file_size for m in members) > max_total:
                raise ValueError("Arsip terlalu besar: maksimal 3000 file / 768 MiB hasil ekstraksi")
            for info in members:
                rel = Path(info.filename.replace("\\", "/"))
                if rel.is_absolute() or ".." in rel.parts or not rel.parts or info.file_size > max_total:
                    raise ValueError("ZIP memiliki path tidak aman")
                if info.external_attr >> 16 & 0o170000 == 0o120000:
                    raise ValueError("Symlink di dalam ZIP tidak diizinkan")
                out_path = (root / rel).resolve()
                if not out_path.is_relative_to(root.resolve()):
                    raise ValueError("ZIP memiliki path di luar workspace")
                out_path.parent.mkdir(parents=True, exist_ok=True)
                with z.open(info) as src, out_path.open("wb") as dst:
                    remaining = info.file_size
                    while remaining:
                        chunk = src.read(min(1024 * 1024, remaining))
                        if not chunk:
                            raise ValueError("Isi ZIP terpotong")
                        dst.write(chunk)
                        remaining -= len(chunk)
                if out_path.suffix.lower() in (".unity3d", ".assets", ".bundle", ".ab") or out_path.name.startswith("CAB-"):
                    candidates.append({"label": str(rel), "path": str(out_path), "size": info.file_size})
        if not candidates:
            raise ValueError("ZIP tidak mengandung .unity3d/.assets/.bundle/.ab yang dikenali")
        # Safest path: pick first loadable candidate, surface the rest for selection.
        failures = []
        for item in candidates:
            try:
                result = self.open(item["path"])
                with self.lock:
                    self.archive_items = candidates
                result["archive"] = [{"index": i, "label": row["label"], "size": row["size"]} for i, row in enumerate(candidates)]
                result["archive_warning"] = "Arsip multi-file: repack hanya bundle yang sedang dibuka; resource eksternal belum diverifikasi"
                return result
            except Exception as exc:
                failures.append(str(exc)[:120])
        raise ValueError("Tidak ada AssetBundle yang bisa dibuka: " + "; ".join(failures[:2]))

    def select_archive_item(self, index):
        with self.lock:
            items = list(self.archive_items)
        idx = int(index)
        if idx < 0 or idx >= len(items):
            raise ValueError("File arsip tidak ditemukan")
        result = self.open(items[idx]["path"])
        with self.lock:
            self.archive_items = items
        result["archive"] = [{"index": i, "label": row["label"], "size": row["size"]} for i, row in enumerate(items)]
        result["archive_warning"] = "Repack hanya untuk bundle yang dipilih"
        return result

    def store_upload(self, stream, length, filename="upload.unity3d"):
        if length < 1 or length > 1024 * 1024 * 1024:
            raise ValueError("Batas unggahan 1 GiB; gunakan fitur buka lewat path untuk berkas besar")
        target = self.root / ("input_" + os.urandom(5).hex() + "_" + _safe_name(filename))
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
            "archive": [{"index": i, "label": row["label"], "size": row["size"]} for i, row in enumerate(self.archive_items)],
        }

    def get_mlbb_catalog(self):
        with self.lock:
            self._require()
            if self._mlbb_catalog is None:
                self._mlbb_catalog = mlbb.catalog(self.objects, _obj_name)
            return self._mlbb_catalog

    def mlbb_overview(self):
        with self.lock:
            c = self.get_mlbb_catalog()
            return {"groups": c["groups"], "total": len(c["assets"]),
                    "disclaimer": "Pengelompokan adalah heuristik dari nama dan tipe; referensi gameplay MLBB belum diverifikasi"}

    def mlbb_assets(self, category="", search="", offset=0, limit=80):
        with self.lock:
            rows = self.get_mlbb_catalog()["assets"]
            query = search.strip().casefold()
            found = [dict(row, edited=row["id"] in self.dirty) for row in rows
                     if (not category or row["category"] == category)
                     and (not query or query in row["name"].casefold() or query in row["type"].casefold()
                          or query in str(self.objects[row["id"]].path_id))]
            offset = max(0, int(offset))
            return {"total": len(found), "offset": offset, "items": found[offset:offset+min(max(1,int(limit)),200)]}

    def mlbb_textures(self, search="", limit=150):
        with self.lock:
            rows = self.get_mlbb_catalog()["assets"]
            q = search.casefold().strip()
            matches = [{"id": row["id"], "name": row["name"], "category": row["category"]}
                       for row in rows if row["type"] == "Texture2D" and (not q or q in row["name"].casefold())]
            return {"items": matches[:min(int(limit), 200)], "total": len(matches)}

    def related_assets(self, index, limit=18):
        """Only lexical similarity. Asset references require game-specific validation."""
        with self.lock:
            self._get(index)
            catalog = self.get_mlbb_catalog()["assets"]
            current = catalog[int(index)]
            ignored = {"hero", "skin", "asset", "cab", "mesh", "tex", "texture", "fx", "vfx", "material", "anim", "high", "low"}
            own = {part for part in mlbb.normalized_name(current["name"]).split("_") if len(part) > 2 and part not in ignored}
            results = []
            for row in catalog:
                if row["id"] == int(index):
                    continue
                tokens = {part for part in mlbb.normalized_name(row["name"]).split("_") if len(part) > 2 and part not in ignored}
                common = own & tokens
                if common:
                    results.append(dict(row, common=sorted(common), score=len(common)))
            results.sort(key=lambda row: (-row["score"], row["name"]))
            return {"items": results[:max(1, min(int(limit), 50))],
                    "warning": "Kemiripan berdasarkan nama saja; bukan dependency graph terverifikasi"}

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
                if obj.type.name == "AnimationClip":
                    for key in ("m_SampleRate", "m_AnimationType", "m_Legacy"):
                        val = getattr(data, key, None)
                        if isinstance(val, (bool, int, float, str)):
                            info["metadata"][key] = val
                    try:
                        tree = obj.parse_as_dict()
                        settings = tree.get("m_AnimationClipSettings", {})
                        info["animation"] = {"sample_rate": tree.get("m_SampleRate"),
                                             "loop": settings.get("m_LoopTime"),
                                             "events": len(tree.get("m_Events", [])),
                                             "float_curves": len(tree.get("m_FloatCurves", [])),
                                             "position_curves": len(tree.get("m_PositionCurves", [])),
                                             "rotation_curves": len(tree.get("m_RotationCurves", [])),
                                             "editor_note": "Metadata clip; bukan playback skeletal in-game"}
                    except Exception as exc:
                        info["animation_error"] = str(exc)
                if obj.type.name == "Transform":
                    try:
                        tree = obj.parse_as_dict()
                        info["transform"] = {key: tree[key] for key in
                                             ("m_LocalPosition", "m_LocalRotation", "m_LocalScale") if key in tree}
                    except Exception as exc:
                        info["transform_error"] = str(exc)
                if obj.type.name == "Material":
                    try:
                        tree = obj.parse_as_dict()
                        vals = tree.get("m_SavedProperties", {}).get("m_Colors", [])
                        info["colors"] = [{"property": v.get("first"), "rgba": v.get("second")}
                                          for v in vals if isinstance(v, dict) and isinstance(v.get("first"), str)]
                    except Exception as exc:
                        info["material_error"] = str(exc)
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

    def edit_animation_loop(self, index, loop):
        with self.lock:
            obj = self._get(index)
            if obj.type.name != "AnimationClip" or not isinstance(loop, bool):
                raise ValueError("Edit loop hanya untuk AnimationClip dengan nilai boolean")
            tree = obj.parse_as_dict()
            cfg = tree.get("m_AnimationClipSettings")
            if not isinstance(cfg, dict) or "m_LoopTime" not in cfg:
                raise ValueError("AnimationClip tidak mempunyai m_LoopTime yang bisa diedit")
            cfg["m_LoopTime"] = loop
            obj.patch(tree)
            self.dirty.add(int(index))
            self.last_output = None
            return self.inspect(index)

    def edit_transform(self, index, values):
        with self.lock:
            obj = self._get(index)
            if obj.type.name != "Transform" or not isinstance(values, dict):
                raise ValueError("Edit transform hanya untuk Transform")
            keys = {"m_LocalPosition": ("x", "y", "z"),
                    "m_LocalScale": ("x", "y", "z")}
            if not values or set(values) - set(keys):
                raise ValueError("Hanya posisi dan skala lokal yang dapat diubah")
            tree = obj.parse_as_dict()
            for key, coords in values.items():
                if key not in tree or not isinstance(coords, dict) or set(coords) != set(keys[key]):
                    raise ValueError("Struktur Transform tidak sesuai")
                output = {}
                for axis in keys[key]:
                    val = coords[axis]
                    if isinstance(val, bool) or not isinstance(val, (int, float)) or not math.isfinite(val) or abs(val) > 1000:
                        raise ValueError("Nilai Transform harus angka terbatas (±1000)")
                    output[axis] = float(val)
                tree[key].update(output)
            obj.patch(tree)
            self.dirty.add(int(index))
            self.last_output = None
            return self.inspect(index)

    def edit_material_color(self, index, prop, rgba):
        with self.lock:
            obj = self._get(index)
            if obj.type.name != "Material":
                raise ValueError("Editor warna hanya untuk Material")
            if not isinstance(prop, str) or len(prop) > 90 or not isinstance(rgba, dict) or set(rgba) != {"r", "g", "b", "a"}:
                raise ValueError("Properti warna / RGBA tidak valid")
            if any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) or not (0 <= v <= 16) for v in rgba.values()):
                raise ValueError("RGBA harus berupa nilai 0–16")
            tree = obj.parse_as_dict()
            colors = tree.get("m_SavedProperties", {}).get("m_Colors", [])
            for entry in colors:
                if isinstance(entry, dict) and entry.get("first") == prop and isinstance(entry.get("second"), dict):
                    for key, val in rgba.items():
                        if key not in entry["second"]:
                            raise ValueError("Property Material bukan warna RGBA")
                        entry["second"][key] = float(val)
                    obj.patch(tree)
                    self.dirty.add(int(index))
                    self.last_output = None
                    return self.inspect(index)
            raise ValueError("Properti warna Material tidak ditemukan")

    def plan_replacements(self, payload):
        """Plan exact-stem PNG/TXT substitutions, never fuzzy match or overwrite originals."""
        from PIL import Image
        if len(payload) > 96 * 1024 * 1024:
            raise ValueError("ZIP penggantian maksimal 96 MiB")
        with self.lock:
            self._require()
            targets = {}
            for i, obj in enumerate(self.objects):
                typ = obj.type.name
                if typ in ("Texture2D", "TextAsset"):
                    targets.setdefault((mlbb.match_name(_obj_name(obj)), typ), []).append(i)
            plan, skipped, ambiguous = {}, [], []
            with zipfile.ZipFile(BytesIO(payload)) as z:
                members = [m for m in z.infolist() if not m.is_dir()]
                if len(members) > 1000 or sum(m.file_size for m in members) > 192 * 1024 * 1024:
                    raise ValueError("ZIP melebihi 1000 file / 192 MiB konten")
                seen = set()
                for item in members:
                    ext = Path(item.filename).suffix.lower()
                    typ = {".png": "Texture2D", ".txt": "TextAsset"}.get(ext)
                    if typ is None or item.file_size > 32 * 1024 * 1024:
                        skipped.append({"file": item.filename, "reason": "Hanya PNG/TXT <=32 MiB"})
                        continue
                    key = (mlbb.match_name(Path(item.filename).stem), typ)
                    if key in seen:
                        ambiguous.append({"file": item.filename, "reason": "Nama file pengganti duplikat"})
                        plan.pop(key, None)
                        continue
                    seen.add(key)
                    matches = targets.get(key, [])
                    if len(matches) != 1:
                        (ambiguous if matches else skipped).append({"file": item.filename,
                            "reason": "Lebih dari satu target" if matches else "Nama asset tidak ditemukan"})
                        continue
                    target = matches[0]
                    if target in self.dirty:
                        skipped.append({"file": item.filename, "reason": "Target sudah diedit dalam sesi"})
                        continue
                    content = z.read(item)
                    if typ == "Texture2D":
                        with Image.open(BytesIO(content)) as img:
                            img.verify()
                        with Image.open(BytesIO(content)) as img:
                            if img.width * img.height > 8192 * 8192:
                                raise ValueError("PNG replacement melebihi 8192² piksel")
                    else:
                        content.decode("utf-8", "surrogateescape")
                    plan[key] = (target, content, typ, item.filename)
            self.replacement_plan = {item[0]: item for item in plan.values()}
            self.replacement_report = {"matches": [{"id": idx, "asset": _obj_name(self.objects[idx]),
                                         "file": filename, "type": typ, "bytes": len(data)}
                                        for idx, data, typ, filename in self.replacement_plan.values()],
                                       "skipped": skipped, "ambiguous": ambiguous,
                                       "note": "Nama harus cocok persis setelah normalisasi; belum menerapkan perubahan"}
            return self.replacement_report

    def apply_replacements(self, indices):
        if not isinstance(indices, list) or not indices or len(indices) > 500:
            raise ValueError("Pilih 1–500 target hasil pencocokan")
        with self.lock:
            ids = [int(i) for i in indices]
            if len(ids) != len(set(ids)) or any(i not in self.replacement_plan for i in ids):
                raise ValueError("Pilihan tidak ada dalam rencana penggantian")
            snapshots = {}
            try:
                for idx in ids:
                    snapshots[idx] = self._get(idx).get_raw_data()
                for idx in ids:
                    _, payload, typ, _ = self.replacement_plan[idx]
                    if typ == "Texture2D":
                        self.edit_texture(idx, payload)
                    else:
                        self.edit_text(idx, payload.decode("utf-8", "surrogateescape"))
            except Exception:
                for idx, previous in snapshots.items():
                    if hasattr(self._get(idx), "set_raw_data"):
                        self._get(idx).set_raw_data(previous)
                    self.dirty.discard(idx)
                self.last_output = None
                raise
            self.replacement_plan.clear()
            self.replacement_report = None
            return {"applied": ids, "count": len(ids),
                    "warning": "Belum diverifikasi dalam gameplay; repack dan uji hasilnya"}

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
