#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"
echo 'UnityPy Studio Android'
echo 'Buka http://127.0.0.1:8765 di browser Android.'
python -m studio.server --port 8765
