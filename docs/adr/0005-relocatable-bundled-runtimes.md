# 0005. The bundled runtimes are self-contained and relocatable

- Status: accepted
- Date: 2026-11-21

## Context

The bundle ships PostgreSQL with PostGIS and the OCR converter (Python, docling). A user may move or unpack the bundle
anywhere, and must not need a system Python, a system PostgreSQL or a network connection for OCR. Several upstream tools
bake absolute build paths into what they install.

## Decision

- **PostgreSQL + PostGIS** come from conda-forge through pixi (`runtime/postgres`); only `bin/`, `lib/` and `share/`
  (extensions, time zones, `proj.db`) are copied. conda-forge's PostgreSQL finds `lib/` and `share/` only at the
  absolute prefix baked into its files, so `postgres.sh` records that prefix exactly as the files spell it (not as `pwd`
  spells it), and the server rewrites it to wherever the bundle ends up on first start (`PgRelocation`). PROJ's data
  path is supplied by the server at start.
- **The converter** is a python-build-standalone CPython (Astral's relocatable builds, the ones `uv python install`
  uses) with the converter's locked dependencies installed straight into it. There is no venv, so nothing points back at
  the build machine. uv leaves a minor-version symlink beside the fully versioned directory, and the real directory is
  the versioned one.
- **Console scripts are made relocatable** (`relocate_scripts.py`): pip/uv write the interpreter's build path into each
  script's shebang, so each Python shebang is rewritten into the sh/python polyglot uv uses for relocatable
  environments.
- **On Linux the lock pins torch/torchvision to the CPU-only builds**, which live only on the PyTorch index that
  `pyproject.toml` routes them to; an exported requirements file does not carry that, so the index is passed
  explicitly. Every version is pinned by the lock, so any index may serve it. That is also needed, because under uv's
  default (first index wins) the PyTorch index would "own" certifi, numpy and others at versions it does not carry.
- **The OCR models are bundled** so OCR works offline: only what the pipeline loads (the layout model, the table model,
  RapidOCR), not the default set (CodeFormula, an ONNX layout copy, the figure classifier). They are cached per docling
  version (`HISTMERSION_MODELS_CACHE`), so a bundle downloads them only when docling changes, and an outage of the model
  host does not stop a build. A model host can answer 200 with a JSON error that a download stores as the model
  (ModelScope did, 2026-11-02), so every file is checked, and a bundle is never built from broken ones.
- **In JVM mode the process is `java`**, so the bundle's runtime is found from `HISTMERSION_HOME` rather than from the
  executable's location.
- **The smoke test hides the pixi environment** the PostgreSQL files were installed into, because conda-forge's
  PostgreSQL would otherwise find it by absolute path: the bundle must work on its own.

## Consequences

- A bundle works wherever it is unpacked, offline, with no system dependencies for these two runtimes.
- The bundle is large (the models alone are about 570 MB).

## Code

`scripts/bundle/postgres.sh`, `converter.sh`, `relocate_scripts.py`, `server-jvm.sh`, `smoke.sh`, the server's
`PgRelocation`.
