# MLBB Mod Studio — APK Android Native (beta)

This branch turns the existing AssetStudioMobile Kotlin/C++ Android app into a mobile-first MLBB-oriented workshop. No UnityPy/Python or Termux, browser or localhost required.

## Use on Android
1. Install the debug APK from a successful Build Android APK GitHub Actions run for this branch.
2. Launch MLBB Mod Studio.
3. Tap Pilih file / folder mod, select local supported Unity assets and load them.
4. Return to dashboard and choose Hero, Skill, Portrait, Icon, Animasi, VFX, Mesh, Texture, Audio, Rig, or Material.
5. Select an asset to inspect or preview. Use native texture or OBJ mesh replacement where supported, and save a new output.

## Safety and limitations
- Categories are name/type heuristics, NOT a semantic MLBB skill database. Use Semua aset if some assets are missing.
- This APK does NOT yet support editing arbitrary skill logic, automatic skeleton retargeting, full animation keyframes, MLBB shader simulation or an in-engine preview.
- Existing Mesh/Texture replacement has version/format constraints.
- No automatic installation into game data directories, no modification of originals.
- CI compiling does NOT prove success on a real MLBB bundle or Android device. In-game validation is necessary.

## CI test note (2026-11-17)
The full existing baseline suite ran 132 tests, with six failures in old MCP tests (McpCoreTest, McpHttpServerTest, McpRendererTest and McpStartFailureTest). These failures are unrelated to the new MLBB UI/classifier files. The APK build workflow runs the dedicated MlbbCategoryTest suite, then assembles the APK. Full baseline test failures remain open and must not be reported as passing. APK startup and MLBB real-game compatibility still need physical device tests.
