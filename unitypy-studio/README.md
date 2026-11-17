# MLBB Mod Studio — UnityPy Android (beta)

**Satu GUI mobile-first** untuk pekerjaan mod aset Mobile Legends: Bang Bang (MLBB): Hero/Skin, Skill/Attack, Portrait, Icon, Animation, VFX, Mesh, Texture, Audio, Rig, Material, dan objek Unity lainnya. Backend dijalankan secara **offline di Termux**; GUI dibuka dengan browser Android. **Belum berupa APK mandiri.** Tidak perlu PC.

> Status: editor aset dan workflow batch awal, **bukan** compiler gameplay MLBB, auto-retarget rig, atau emulator rendering in-game. Deteksi hero/skill memakai **heuristik nama dan tipe**, bukan dependency graph resmi MLBB. Preview WebGL adalah simulasi static mesh dan tekstur di browser, **tidak sama dengan visual yang dijalankan game**.

## Instalasi Android

Gunakan Termux terbaru dari F-Droid/GitHub:

```bash
pkg update && pkg install -y python git clang libjpeg-turbo libpng
termux-setup-storage
git clone -b feature/unitypy-studio-android https://github.com/Anovvoldigoad/AssetStudioMobile.git
cd AssetStudioMobile/unitypy-studio
python -m pip install -r requirements.txt
bash start.sh
```

Buka `http://127.0.0.1:8765` di Chrome/Firefox Android. Untuk membuka berkas besar, masukkan path `/sdcard/Download/namamod.unity3d`. Jika `pip install` gagal karena dependensi native UnityPy di Android, perlu melakukan build paket yang kompatibel; belum ada uji instalasi pada HP asli.

## Yang bisa digunakan sekarang

| Menu | Fungsi nyata | Batasan |
|---|---|---|
| Hero & Skin | Menemukan aset dari pola nama, membandingkan objek dengan nama terkait | Identifikasi hero tidak tervalidasi terhadap ID hero engine |
| Skill & Attack | Filter asset berkaitan dengan skill, inspector, data/animasi/VFX | Tidak mengedit logika skill di runtime |
| Portrait & Icon | Preview PNG, editor Texture2D + fit/cover/stretch | Sprite atlas/UV belum otomatis diperbarui |
| Animasi | Metadata sample rate/curve/event; toggle flag loop AnimationClip | Tanpa keyframe editor dan playback skeletal |
| VFX | Deteksi FX, preview sprite-sheet frame (manual grid), Material RGBA bila ada | Tidak menjalankan shader/ParticleSystem game |
| Mesh 3D | Export OBJ; WebGL static mesh viewer + tekstur pilihan | Tanpa import OBJ ke rig Unity dan skin weights |
| Texture | Preview PNG; ganti Texture2D dari PNG | Dukungan format/compression tergantung UnityPy |
| Rig & Bone | Daftar tulang SkinnedMeshRenderer; perbandingan rig | Retarget otomatis belum tersedia |
| Transform | Edit posisi/skala lokal dengan validasi nilai | Tidak memvalidasi transisi animasi in-game |
| Audio | Preview/ekspor AudioClip WAV yang didukung | Belum ada import AudioClip |
| Auto replace | Unggah ZIP berisi PNG/TXT; dry-run dengan exact-name matching; centang target; apply | Menolak target ambigu, tak mendukung mesh/animation-clips |
| Bundle ZIP | Impor ZIP mod, ekstraksi aman, pilih bundle | Repack **per satu bundle**; resource eksternal harus dicek |
| Export & Repack | Ekspor aset, JSON, batch ZIP, simpan file mod baru, reopen & hitung objek | Tidak menjamin kompatibilitas gameplay |

Mode `Advanced` tersedia di `/advanced` untuk eksplorasi semua tipe, TypeTree JSON, RAW export, inspeksi rig.

### Auto replace yang aman

1. Buka AssetBundle target `.unity3d`, `.assets`, atau ZIP mod yang berisi bundle.
2. Siapkan ZIP berisi **`NamaAsset.png`** untuk target Texture2D atau **`NamaAsset.txt`** untuk target TextAsset. Nama harus cocok dengan nama internal objek setelah normalisasi separator/huruf kecil (tidak fuzzy).
3. Klik **ZIP PNG/TXT**. Periksa tabel *matches*, *ambiguous*, dan *skipped* sebelum pilih target.
4. Centang kandidat yang ingin diganti, klik **Terapkan**, kemudian klik **Build MOD**, dan unduh hasil untuk diuji pada salinan game.

Nama aset duplikat tidak boleh ditimpa otomatis. File sumber dibiarkan utuh, output dibentuk di workspace sementara dengan prefix `MOD_`. Jika beberapa bundle terlibat, proses tiap bundle secara terpisah.

## Preview

- **Texture, Portrait, Icon**: gambar Texture2D/Sprite aktual yang UnityPy decode.
- **Mesh**: geometri OBJ aktual dari asset, divisualisasikan statis dengan WebGL. Tekstur terpilih ditempel menurut koordinat UV OBJ; pemilihan otomatis material/texture yang tepat belum ada.
- **Skill/VFX**: preview frame dari sprite-sheet PNG dengan kolom/baris yang dipilih manual. Tidak ada shader, particle lifetime, serangan, kamera game, hitbox, atau efek dalam battle.
- **Animasi**: metadata, bukan pose animasi di scene.

Tidak ada fitur yang dapat membuktikan tampilan *in-game* dari browser. Pengujian menggunakan MLBB asli tetap dibutuhkan.

## Validasi dan keamanan

- Server hanya mendengarkan `127.0.0.1` dengan token acak per sesi untuk endpoint sensitif.
- ZIP pembuka diperiksa dari path traversal, symlink, batas jumlah/ukuran ekstraksi.
- ZIP pengganti dibatasi ukuran, nama target harus unik, dan editor menjalankan pilihan eksplisit.
- Data input tidak diunggah ke cloud.
- Uji `python -m unittest discover -s tests -v` mencakup klasifikasi, kecocokan ZIP, edit subset AnimationClip/Transform/Material, penolakan arsip berbahaya, autentikasi endpoint, dan repack yang tidak menyentuh sumber.
- Test menggunakan fake UnityPy environment. **Belum ada verifikasi runtime Android maupun mod MLBB nyata.**

## Tujuan berikutnya (belum tersedia)

- App Android native/standalone tanpa Termux.
- Workspace multi-bundle dengan daftar referensi lintas CAB/PathID dan resource eksternal.
- Penelusuran hero/skill/skin ID yang benar berdasarkan skema versi MLBB tertentu.
- Editor animator controller, state machine, binding keyframe, bone map, retarget + skin weights, mesh import dengan format vertex yang sesuai.
- Engine preview dengan skeleton, animasi, shader material, particle/VFX, dan kamera spesifik MLBB, kemudian uji dalam gameplay.
- Installer mod/rollback yang memverifikasi integritas, kompatibilitas versi dan backup.

## Lisensi

Kode baru berlisensi MIT seperti proyek awal; UnityPy dan dependensinya mengikuti lisensi masing-masing.
