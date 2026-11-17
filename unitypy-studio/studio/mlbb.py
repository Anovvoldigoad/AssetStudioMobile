"""Conservative *heuristic* labels for MLBB Unity assets.

Unity asset names do not provide authoritative hero/skill semantics. The
classifier uses explicit filename tokens and asset type, and surfaces its
confidence; it deliberately does not invent MLBB hero IDs or dependencies.
"""
from __future__ import annotations

from collections import Counter
import re
import unicodedata

CATEGORIES = (
    ("hero", "Hero & Skin", "🦸"),
    ("skill", "Skill & Attack", "⚔️"),
    ("portrait", "Portrait", "🖼️"),
    ("icon", "Icon", "🔰"),
    ("animation", "Animasi", "🎞️"),
    ("vfx", "Efek & VFX", "✨"),
    ("mesh", "Mesh 3D", "🧊"),
    ("texture", "Texture", "🎨"),
    ("audio", "Audio & Voice", "🔊"),
    ("rig", "Rig & Bone", "🦴"),
    ("material", "Material", "◈"),
    ("other", "Lainnya", "📦"),
)
LABELS = {key: name for key, name, _ in CATEGORIES}

TEXTURE_TYPES = {"Texture2D", "Sprite", "Texture2DArray", "Cubemap"}
MESH_TYPES = {"Mesh", "MeshRenderer", "SkinnedMeshRenderer"}
AUDIO_TYPES = {"AudioClip", "AudioMixer"}
RIG_TYPES = {"Transform", "Animator", "Avatar"}
VFX_TYPES = {"ParticleSystem", "ParticleSystemRenderer", "TrailRenderer", "LineRenderer", "VisualEffect"}

PATTERNS = [
    ("portrait", re.compile(r"(?:^|_)(?:portrait|avatar|headicon|herohead|hero_head|loading|bighead|halfbody|heroiconlarge)(?:_|$)")),
    ("icon", re.compile(r"(?:^|_)(?:icon|ico|badge|emblem|smallhead|thumbnail|skillicon|bufficon)(?:_|$)")),
    ("skill", re.compile(r"(?:^|_)(?:skill\d*|ability\d*|attack\d*|atk\d*|ultimate|ult|spell\d*|basicattack|basic_attack|passive|combo\d*)(?:_|$)")),
    ("vfx", re.compile(r"(?:^|_)(?:fx|vfx|effect|particle|bullet|hitfx|trail|muzzle|beam|slash|hit_effect)(?:_|$)")),
    ("animation", re.compile(r"(?:^|_)(?:animation|anim|idle|run|walk|jump|death|recall|emote|clip)(?:_|$)")),
    ("audio", re.compile(r"(?:^|_)(?:voice|sfx|audio|sound|bgm|music|vo|dialog)(?:_|$)")),
    ("hero", re.compile(r"(?:^|_)(?:hero\d*|skin\d*|character|body|weapon|costume|zeo|shizi|avatarbody)(?:_|$)")),
]


def normalized_name(value):
    value = unicodedata.normalize("NFKC", str(value)).casefold().strip()
    return re.sub(r"[^\w]+", "_", value).strip("_")


def asset_kind(type_name):
    if type_name in TEXTURE_TYPES:
        return "texture"
    if type_name in MESH_TYPES:
        return "mesh"
    if type_name in AUDIO_TYPES:
        return "audio"
    if type_name in RIG_TYPES:
        return "rig"
    if type_name in VFX_TYPES:
        return "vfx"
    if type_name == "AnimationClip" or type_name == "AnimatorController":
        return "animation"
    if type_name == "Material" or type_name == "Shader":
        return "material"
    if type_name == "TextAsset" or type_name == "MonoBehaviour":
        return "data"
    return "other"


def classify(name, type_name):
    """Return category, kind, confidence, and **explainable** reason.

    Unknown naming conventions remain unknown; never guess actual game wiring.
    """
    kind = asset_kind(type_name)
    normalized = normalized_name(name)
    for category, pattern in PATTERNS:
        if pattern.search(normalized):
            # Semantic hints are uncertain for most data and geometry.
            return {"category": category, "kind": kind, "confidence": "perkiraan",
                    "reason": "Pola nama aset; belum diverifikasi terhadap referensi game"}
    fallback = kind if kind in LABELS else "other"
    return {"category": fallback, "kind": kind, "confidence": "berdasarkan_tipe",
            "reason": "Kategori dari tipe Unity, bukan hubungan hero/skill yang terverifikasi"}


def catalog(objects, get_name):
    groups = {key: {"key": key, "label": label, "icon": icon, "count": 0} for key, label, icon in CATEGORIES}
    asset_rows = []
    for i, obj in enumerate(objects):
        name, typ = get_name(obj), obj.type.name
        row = {"id": i, "name": name, "type": typ, **classify(name, typ)}
        groups[row["category"]]["count"] += 1
        asset_rows.append(row)
    return {"groups": list(groups.values()), "assets": asset_rows,
            "types": dict(Counter(row["type"] for row in asset_rows))}


def match_name(name):
    """Exact normalized asset *stem* only; no hazardous fuzzy matching."""
    return normalized_name(name)
