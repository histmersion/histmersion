#!/usr/bin/env bash
# Build the OCR converter as a self-contained directory — no system Python, no install.
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
# On Linux the lock pins torch/torchvision to the CPU-only builds (`+cpu`), which live only on the
# PyTorch index pyproject.toml routes them to — an exported requirements file does not carry it.
extra=()
if [ "$(uname -s)" = "Linux" ]; then
  # every version is pinned by the lock, so taking each from whichever index has it is safe — and
  # needed: under uv's default (first index wins) the PyTorch index also "owns" certifi, numpy, …
  # at versions it does not carry
  extra=(--extra-index-url "$(sed -n '/name = "pytorch-cpu"/{n;s/^url = "\(.*\)"/\1/p;}' "$here/pyproject.toml")"
         --index-strategy unsafe-best-match)
fi
uv pip install --python "$py" --system --break-system-packages --compile-bytecode -q ${extra[@]+"${extra[@]}"} -r "$req"
rm -f "$req"
cp "$here/app.py" "$out/"

"$py" "$(dirname "$0")/relocate_scripts.py" "$out/python/bin"

# only what the pipeline in app.py loads: layout (heron), the table model, RapidOCR (~570 MB);
# the default set adds CodeFormula (610 MB), an ONNX layout copy and the figure classifier (unused)
echo "→ docling models (layout, tableformer, rapidocr)"
# a model host that fails can answer 200 with a JSON error, which the download stores as the model
# (ModelScope did, 2026-11-02): check every file and retry, so a broken bundle never gets built
models_ok() {
  "$py" - "$out/models" <<'PY'
import os, sys
bad = []
for root, _, files in os.walk(sys.argv[1]):
    for f in files:
        p = os.path.join(root, f)
        size = os.path.getsize(p)
        with open(p, "rb") as fh:
            head = fh.read(256)
        if head.lstrip().startswith(b"{") and b'"Success":false' in head.replace(b" ", b""):
            bad.append(f"{p}: an error answer ({head[:120].decode('utf-8', 'replace')})")
        elif f.endswith((".onnx", ".pth", ".safetensors", ".bin")) and size < 64 * 1024:
            bad.append(f"{p}: {size} bytes")
for b in bad:
    print(f"  bad model file: {b}", file=sys.stderr)
sys.exit(1 if bad else 0)
PY
}
for attempt in 1 2 3; do
  rm -rf "$out/models"
  "$py" -m docling.cli.tools models download -q -o "$out/models" layout tableformer rapidocr >/dev/null 2>&1
  models_ok && break
  [ "$attempt" -eq 3 ] && { echo "::error::the docling models did not download (a host answered with errors); try again later" >&2; exit 1; }
  echo "  the models did not download whole, trying again in 30 s ($attempt/3)"
  sleep 30
done
"$py" - <<'PY'
import sys, torch, docling, pymupdf
print(f"  python {sys.version.split()[0]} · torch {torch.__version__} (mps: {torch.backends.mps.is_available()}) · pymupdf {pymupdf.__version__}")
PY
du -sh "$out/python" "$out/models" | sed 's/^/  /'
