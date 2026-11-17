import io
import json
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch

from studio.core import Studio, _safe_name, _jsonable, _unjsonable


class FakeData:
    def __init__(self, name, script=None):
        self.m_Name = name
        self.m_Script = script


class FakeObject:
    def __init__(self, index, type_name, name, text=None):
        self.path_id = 100 + index
        self.type = types.SimpleNamespace(name=type_name)
        self.data = FakeData(name, text)
        self.byte_size = 12
        self.patched = False

    def peek_name(self):
        return self.data.m_Name

    def parse_as_object(self):
        return self.data

    def parse_as_dict(self):
        return {"m_Name": self.data.m_Name, "m_Script": self.data.m_Script}

    def patch(self, d):
        self.patched = True
        if isinstance(d, dict):
            self.data.m_Name = d["m_Name"]
            self.data.m_Script = d["m_Script"]

    def get_raw_data(self):
        return b"raw asset"


class FakeEnv:
    def __init__(self):
        self.objects = [FakeObject(0, "TextAsset", "Demo", "hello"), FakeObject(1, "Shader", "Shade")]
        self.file = types.SimpleNamespace(save=lambda: b"fake-bundle-valid")


class TestCore(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.source = Path(self.tmp.name) / "demo.unity3d"
        self.source.write_bytes(b"fake source")
        self.env = FakeEnv()
        fake_module = types.SimpleNamespace(load=lambda f: FakeEnv(), __version__="test")
        self.patch = patch.dict(sys.modules, {"UnityPy": fake_module})
        self.patch.start()
        self.studio = Studio()
        self.studio.open(self.source)

    def tearDown(self):
        self.studio.close()
        self.patch.stop()
        self.tmp.cleanup()

    def test_listing_filtering_and_inspector(self):
        self.assertEqual(self.studio.summary()["objects"], 2)
        self.assertEqual(self.studio.list_assets("demo")["total"], 1)
        self.assertEqual(self.studio.list_assets(asset_type="Shader")["items"][0]["id"], 1)
        self.assertEqual(self.studio.inspect(0)["text"], "hello")

    def test_non_destructive_text_edit_and_repack(self):
        self.studio.edit_text(0, "edit baru")
        self.assertEqual(self.studio.inspect(0)["text"], "edit baru")
        saved = self.studio.save()
        self.assertEqual(saved["objects"], 2)
        self.assertEqual(self.source.read_bytes(), b"fake source")
        self.assertTrue(self.studio.last_output.exists())

    def test_raw_json_and_batch_exports(self):
        self.assertEqual(self.studio.export(0, "raw")[1], b"raw asset")
        self.assertEqual(json.loads(self.studio.export(0, "json")[1])["m_Name"], "Demo")
        name, contents, mime = self.studio.batch_export([0, 1])
        import zipfile
        with zipfile.ZipFile(io.BytesIO(contents)) as z:
            self.assertIn("EXPORT_REPORT.json", z.namelist())
            self.assertEqual(json.loads(z.read("EXPORT_REPORT.json"))["success"], 2)

    def test_typetree_edit_and_validation(self):
        data = self.studio.inspect(0, include_tree=True)["tree"]
        data["m_Name"] = "New"
        self.studio.edit_json(0, data)
        self.assertEqual(self.studio.inspect(0)["name"], "New")
        with self.assertRaisesRegex(ValueError, "Kunci"):
            self.studio.edit_json(0, {"invalid": "field"})

    def test_repack_requires_changes(self):
        with self.assertRaisesRegex(ValueError, "Belum"):
            self.studio.save()

    def test_invalid_index(self):
        with self.assertRaisesRegex(ValueError, "tidak ditemukan"):
            self.studio.inspect(-1)
        with self.assertRaisesRegex(ValueError, "tidak ditemukan"):
            self.studio.inspect(1000)

    def test_upload_size_limits(self):
        with self.assertRaises(ValueError):
            self.studio.store_upload(io.BytesIO(b"a"), 0)
        with self.assertRaises(ValueError):
            self.studio.store_upload(io.BytesIO(b"a"), 1024**3+1)

    def test_json_binary_roundtrip(self):
        original = {"blob": b"\x00\xff", "other": [1, None]}
        self.assertEqual(_unjsonable(_jsonable(original)), original)
        self.assertEqual(_safe_name("../../bad:name"), "_.._bad_name")


if __name__ == "__main__":
    unittest.main()
