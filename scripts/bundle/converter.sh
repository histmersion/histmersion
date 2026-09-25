#!/usr/bin/env bash
# Build the OCR converter as a self-contained directory — no Docker, no system Python, no install.
#
#   scripts/bundle/converter.sh <out-dir>      # → <out-dir>/converter/{python,app.py,models}
#
# python/  a python-build-standalone CPython (Astral's relocatable builds, the ones `uv python
#          install` uses) with the converter's LOCKED dependencies installed straight into it —
#          no venv, so nothing points back at the build machine; run it as `python/bin/python3 -m`.
# models/  docling's layout/table weights, so OCR works offline (DOCLING_ARTIFACTS_PATH).
set -euo pipefail
out="$(mkdir -p "${1:?usage: $0 <out-dir>}" && cd "$1" && pwd)/converter"
here="$(cd "$(dirname "$0")/../../converter" && pwd)"
py_version="$(sed -n 's/^requires-python = ">=\([0-9.]*\)".*/\1/p' "$here/pyproject.toml")"

rm -rf "$out" && mkdir -p "$out"
echo "→ python-build-standalone CPython $py_version"
uv python install "$py_version" --install-dir "$out/.pbs" --no-bin >/dev/null
# the real directory is the fully versioned one; uv also leaves a minor-version symlink beside it
mv "$(find "$out/.pbs" -maxdepth 1 -type d -name "cpython-$py_version.*" | head -1)" "$out/python"
rm -rf "$out/.pbs"
py="$out/python/bin/python3"

echo "→ locked dependencies (uv.lock, extra: ocr)"
req="$(mktemp)"
(cd "$here" && uv export --frozen --no-dev --extra ocr --no-hashes --no-emit-project --format requirements-txt) > "$req"
uv pip install --python "$py" --system --break-system-packages --compile-bytecode -q -r "$req"
rm -f "$req"
cp "$here/app.py" "$out/"

echo "→ docling models"
"$out/python/bin/docling-tools" models download -o "$out/models" >/dev/null
"$py" - <<'PY'
import sys, torch, docling, pymupdf
print(f"  python {sys.version.split()[0]} · torch {torch.__version__} (mps: {torch.backends.mps.is_available()}) · pymupdf {pymupdf.__version__}")
PY
du -sh "$out/python" "$out/models" | sed 's/^/  /'
