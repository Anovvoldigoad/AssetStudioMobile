import io
import json
import sys
import tempfile
import threading
import types
import unittest
import zipfile
from copy import deepcopy
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from PIL import Image

from studio.mlbb import classify, normalized_name
from studio.core import Studio
from studio.server import make_handler


def png_bytes():
    out = io.BytesIO()
    Image.new("RGBA", (16, 8), (10, 120, 230, 255)).save(out, format="PNG")
    return out.getvalue()


def zip_bytes(files):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as z:
        for filename, contents in files.items():
            z.writestr(filename, contents)
    return out.getvalue()


class FakeObject:
    def __init__(self, index, typ, name, tree):
        self.type = types.SimpleNamespace(name=typ)
        self.path_id = 1000 + index
        self.byte_size = 55
        self.tree = deepcopy(tree)
        self.name = name
        self.data = types.SimpleNamespace(m_Name=name)
        self.modified = False
        if typ == "Texture2D":
            self.data.image = Image.new("RGBA", (2, 2))
            self.data.m_Width = 2
            self.data.m_Height = 2
        if typ == "TextAsset":
            self.data.m_Script = "original"

    def peek_name(self):
        return self.name

    def parse_as_object(self):
        return self.data

    def parse_as_dict(self):
        return deepcopy(self.tree)

    def get_raw_data(self):
        return b"raw-" + str(self.path_id).encode()

    def set_raw_data(self, value):
        assert value.startswith(b"raw-")
        self.modified = False

    def patch(self, value):
        self.modified = True
        if isinstance(value, dict):
            self.tree = deepcopy(value)
        else:
            self.data = value


class FakeEnv:
    def __init__(self):
        self.objects = [
            FakeObject(0, "Texture2D", "Hero_Portrait", {"m_Name": "Hero_Portrait"}),
            FakeObject(1, "TextAsset", "Skill_Attack01", {"m_Name": "Skill_Attack01", "m_Script": "original"}),
            FakeObject(2, "AnimationClip", "Hero_Anim_Run", {"m_Name": "Hero_Anim_Run", "m_AnimationClipSettings": {"m_LoopTime": False}, "m_SampleRate": 30, "m_Events": []}),
            FakeObject(3, "Transform", "Hero_Body", {"m_LocalPosition": {"x": 0., "y": 1., "z": 0.}, "m_LocalScale": {"x": 1., "y": 1., "z": 1.}, "m_Father": 8}),
            FakeObject(4, "Material", "Hero_Mat", {"m_SavedProperties": {"m_Colors": [{"first": "_Color", "second": {"r": 1., "g": 1., "b": 1., "a": 1.}}]}}),
            FakeObject(5, "Mesh", "Hero_Weapon", {"m_Name": "Hero_Weapon"}),
            FakeObject(6, "Texture2D", "Hero_Portrait", {"m_Name": "Hero_Portrait"}),
        ]
        self.file = types.SimpleNamespace(save=lambda: b"fake-bundle-valid")


class TestMLBB(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.source = Path(self.tmp.name) / "test.unity3d"
        self.source.write_bytes(b"fake")
        self.patch = patch.dict(sys.modules, {"UnityPy": types.SimpleNamespace(load=lambda f: FakeEnv(), __version__="fake")})
        self.patch.start()
        self.studio = Studio()
        self.studio.open(self.source)

    def tearDown(self):
        self.studio.close()
        self.patch.stop()
        self.tmp.cleanup()

    def test_category_heuristics_reveal_uncertainty(self):
        self.assertEqual(classify("FX_Mesh_hero_A", "Mesh")["category"], "vfx")
        self.assertEqual(classify("Skill_Attack01", "TextAsset")["category"], "skill")
        self.assertEqual(classify("Hero_Portrait", "Texture2D")["category"], "portrait")
        self.assertEqual(classify("Hero_zeo_skin", "Mesh")["category"], "hero")
        self.assertEqual(classify("no_clue", "Mesh")["category"], "mesh")
        self.assertEqual(normalized_name("Hero-Icon.PNG"), "hero_icon_png")
        self.assertEqual(classify("Hero_Portrait", "Texture2D")["confidence"], "perkiraan")

    def test_catalog_search_groups_and_related(self):
        overview = self.studio.mlbb_overview()
        self.assertEqual(overview["total"], 7)
        self.assertEqual(self.studio.mlbb_assets("portrait")["total"], 2)
        self.assertEqual(self.studio.mlbb_assets("skill")["total"], 1)
        self.assertEqual(self.studio.mlbb_textures("hero")["total"], 2)
        self.assertGreater(self.studio.related_assets(0)["items"][0]["score"], 0)

    def test_replace_zip_exact_only_skips_ambiguous(self):
        result = self.studio.plan_replacements(zip_bytes({"Hero_Portrait.png": png_bytes(), "Skill_Attack01.txt": b"changed"}))
        self.assertEqual(len(result["matches"]), 1)  # portrait is duplicated in fake bundle
        self.assertEqual(result["matches"][0]["id"], 1)
        self.assertEqual(len(result["ambiguous"]), 1)
        applied = self.studio.apply_replacements([1])
        self.assertEqual(applied["count"], 1)
        self.assertEqual(self.studio.inspect(1)["text"], "changed")
        self.assertEqual(self.source.read_bytes(), b"fake")
        self.assertEqual(self.studio.summary()["changes"], [1])

    def test_plan_does_not_reapply_to_already_edited_assets(self):
        self.studio.edit_text(1, "previous")
        result = self.studio.plan_replacements(zip_bytes({"Skill_Attack01.txt": b"new"}))
        self.assertEqual(result["matches"], [])
        self.assertEqual(result["skipped"][0]["reason"], "Target sudah diedit dalam sesi")

    def test_edit_animation_with_strict_type(self):
        self.studio.edit_animation_loop(2, True)
        self.assertTrue(self.studio.objects[2].parse_as_dict()["m_AnimationClipSettings"]["m_LoopTime"])
        with self.assertRaisesRegex(ValueError, "boolean"):
            self.studio.edit_animation_loop(2, "true")
        with self.assertRaisesRegex(ValueError, "AnimationClip"):
            self.studio.edit_animation_loop(1, True)

    def test_edit_transform_validation_preserves_references(self):
        self.studio.edit_transform(3, {"m_LocalPosition": {"x": 1, "y": 2.5, "z": -1}})
        tree = self.studio.objects[3].parse_as_dict()
        self.assertEqual(tree["m_Father"], 8)
        self.assertEqual(tree["m_LocalPosition"]["y"], 2.5)
        with self.assertRaisesRegex(ValueError, "terbatas"):
            self.studio.edit_transform(3, {"m_LocalScale": {"x": float('nan'), "y": 2, "z": 2}})

    def test_edit_material_colors_only_existing_key(self):
        self.studio.edit_material_color(4, "_Color", {"r": .3, "g": .2, "b": .8, "a": 1})
        colors = self.studio.objects[4].parse_as_dict()["m_SavedProperties"]["m_Colors"]
        self.assertEqual(colors[0]["second"]["b"], .8)
        with self.assertRaisesRegex(ValueError, "tidak ditemukan"):
            self.studio.edit_material_color(4, "_NotExists", {"r": 1, "g": 1, "b": 1, "a": 1})

    def test_zip_traversal_rejected(self):
        z = Path(self.tmp.name) / "danger.zip"
        z.write_bytes(zip_bytes({"../escape.unity3d": b"fake"}))
        with self.assertRaisesRegex(ValueError, "aman"):
            self.studio.open_archive(z)
        self.assertFalse((self.studio.root.parent / "escape.unity3d").exists())

    def test_zip_bundle_selection(self):
        z = Path(self.tmp.name) / "assets.zip"
        z.write_bytes(zip_bytes({"hero.unity3d": b"fake", "skills/FX.unity3d": b"fake"}))
        summary = self.studio.open_project(z)
        self.assertEqual(len(summary["archive"]), 2)
        selected = self.studio.select_archive_item(1)
        self.assertIn("FX.unity3d", selected["filename"])
        self.assertEqual(len(selected["archive"]), 2)

    def test_http_auth_and_preview_routes(self):
        httpd = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(self.studio, "token"))
        worker = threading.Thread(target=httpd.serve_forever, daemon=True)
        worker.start()
        base = f"http://127.0.0.1:{httpd.server_address[1]}"
        try:
            with urlopen(base + "/") as res:
                self.assertIn(b"MOD STUDIO", res.read())
            with urlopen(base + "/mesh_viewer.js") as res:
                self.assertIn(b"MeshViewer", res.read())
            with self.assertRaises(HTTPError) as denied:
                urlopen(base + "/api/mlbb/overview")
            self.assertEqual(denied.exception.code, 403)
            req = Request(base + "/api/mlbb/overview", headers={"X-Studio-Token": "token"})
            with urlopen(req) as res:
                self.assertEqual(json.load(res)["total"], 7)
        finally:
            httpd.shutdown()
            worker.join(timeout=2)
            httpd.server_close()


if __name__ == '__main__':
    unittest.main()
