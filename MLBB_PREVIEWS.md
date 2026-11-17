# MLBB Studio Android preview coverage (beta)
- Texture2D / Sprite: existing image viewer with zoom.
- Mesh / SkinnedMeshRenderer / MeshFilter: existing rotatable mesh viewer.
- GameObject: referenced mesh when loaded, component list.
- AnimationClip: real decoded curve timeline and events (not animation playback on a character).
- Animation, AnimatorController, Animator, Avatar, Transform: reference and rig hierarchy data.
- AudioClip: play PCM/WAV when native decoder supports the sample and Android can play it.
- Material: available texture and saved color swatches. Shader: existing text dump.
- Other types (incl. ParticleSystem and specialized VFX): serialized TypeTree data if available; no fabricated in-engine visual effect.
Dependent bundles and external .resS resources must be loaded for referenced assets.
