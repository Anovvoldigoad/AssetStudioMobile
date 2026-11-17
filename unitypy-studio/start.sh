#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"
echo 'MLBB Mod Studio (UnityPy)'
echo 'Buka http://127.0.0.1:8765 di browser Android.'
echo 'Mode advanced: http://127.0.0.1:8765/advanced'
python -m studio.server --port 8765
