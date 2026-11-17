# UnityPy Studio Android

GUI lokal **offline** berbahasa Indonesia untuk inspeksi dan pengeditan aset Unity langsung dari browser Android. Backend Python berbasis [UnityPy](https://github.com/K0lb3/UnityPy), tanpa Flask, CDN, atau server cloud. Proyek ini **bukan APK**; Termux harus aktif saat GUI digunakan.

## Instalasi Android (tanpa PC)

Gunakan Termux versi F-Droid/GitHub, bukan build Play Store lama:

```bash
pkg update && pkg install -y python git clang libjpeg-turbo libpng
termux-setup-storage
# Salin/ekstrak proyek ke ~/UnityPyStudioAndroid, atau clone branch GitHub.
cd ~/UnityPyStudioAndroid
pip install -r requirements.txt
bash start.sh
```

Buka **http://127.0.0.1:8765** menggunakan Chrome/Firefox di HP yang sama. Untuk file besar, gunakan path seperti `/sdcard/Download/model.unity3d`. Jika pip gagal karena dependensi native UnityPy di Android, perlu paket yang kompatibel atau build dependensinya; instalasi Android **belum terverifikasi** dalam lingkungan ini.

## Fitur yang diimplementasikan

- Buka AssetBundle, assets, dan berkas lain yang didukung UnityPy lewat path atau upload file Android (unggahan maksimal 1 GiB).
- Explorer aset dengan pencarian nama, PathID, filter tipe, pagination, dan pemilihan banyak aset.
- Inspector metadata dan struktur objek, termasuk SkinnedMeshRenderer/bone list.
- Preview Texture2D, Sprite, serta Mesh OBJ dalam bentuk wireframe 3D interaktif (offline).
- Ekspor Texture2D/Sprite PNG, Mesh OBJ, AudioClip WAV, Font TTF/OTF, TextAsset, Texture2DArray ZIP, JSON TypeTree, RAW, dan batch ZIP berikut error report.
- Edit TextAsset (UTF-8), ganti Texture2D dari PNG, dan edit TypeTree JSON advanced.
- Laporan skeleton/rig dan selisih nama tulang dari dua file.
- Repack sebagai file **baru**, lalu validasi bahwa output dapat dibuka ulang oleh UnityPy dan jumlah objek tetap sama. Sumber asli tidak ditimpa.

## Batasan yang penting

- Ini **editor asset**, bukan rig retargeter otomatis, mesh/skinning weight painter, atau animator 3D. Data rig yang ditampilkan adalah untuk audit, belum untuk transplantasi model Shadow → Ling/Argus.
- Retarget pose, ubah bone bindings, impor mesh OBJ dengan proper skinning, perbaikan clipping jubah, editor animasi kurva, dan pemulihan referensi objek **belum diimplementasikan**.
- TypeTree mungkin tidak tersedia (terutama MonoBehaviour tanpa data tipe), dan beberapa codec/format Unity tidak didukung UnityPy.
- Repack hanya tersedia jika sumber memiliki `env.file`. Bundle dengan resource stream eksternal, `bundle` multi-file, atau format/proteksi khusus mungkin membutuhkan alur khusus.
- Ekspor batch disimpan dalam memori sebelum unduh; untuk ribuan aset besar, proses dapat menghabiskan RAM Android.
- Output yang lolos pemeriksaan struktur **belum tentu bekerja dalam game**. Selalu simpan backup dan lakukan uji langsung di game.
- Server hanya bind ke `127.0.0.1`; token per sesi diperlukan untuk endpoint mutasi/data. Jangan expose port ke internet.

## Tests

```bash
python -m unittest discover -s tests -v
python -m compileall -q studio
```

`tests` menggunakan mock object agar API inti dapat diuji tanpa aset milik game atau instalasi UnityPy di runner. Uji end-to-end dengan bundle nyata dan instalasi Termux tetap diperlukan.

## Lisensi / atribusi

Kode frontend & backend ini disediakan di bawah lisensi MIT. UnityPy adalah dependensi pihak ketiga dengan lisensi terpisah, lihat repositori upstream.
