# Working on these docs

The site is built with [Zensical](https://zensical.org), the successor of Material for MkDocs by the
same team. It reads `docs/mkdocs.yml`.

```text
docs/
├── mkdocs.yml          the site: navigation, theme, Markdown extensions, link validation
├── main.py             macros: {% raw %}{{ histmersion_version() }}, {{ repo_file("path") }}{% endraw %}
├── requirements.txt    the pinned toolchain (zensical)
├── Justfile            venv · build · serve · docker-build · docker-serve
├── Dockerfile          the same toolchain in a container
├── user-guide/         the pages (docs_dir), including this Contributing section and llms.txt
│   └── assets/screenshots/   the screenshots — generated, see below
├── demo/               the demo collection the screenshots are taken from
└── research-*.md …     design notes — NOT part of the site; linked from Architecture
```

## Build and preview

```bash
just docs-venv      # once: docs/.venv with the pinned Zensical
just docs-build     # docs/site; --strict: any broken link or anchor fails the build
just docs-serve     # live preview on http://localhost:8001 (HISTMERSION_DOCS_PORT)
```

Without uv, `just -f docs/Justfile docker-serve` runs the same in a container. Nothing is published
from here; `docs/site/` is a static site, ready to upload to any static host when the time comes.

## Screenshots

Every picture of the web page in these docs is generated, from scratch, by one command:

```bash
just docs-screenshots                      # all of them → docs/user-guide/assets/screenshots/*.png
just docs-screenshots --only source,about  # some (the names are the keys in the script)
just docs-screenshots --serve              # load the demo and keep the server up, to look around
```

It ({{ repo_file("scripts/docs/screenshots.sh") }}) starts a throwaway Postgres (`just test-db` on port
55581, `HISTMERSION_DOCS_DB_PORT`), runs the server on the JVM from the build's class path on a free
port with an **empty** temporary config, data directory and `HOME` — never an installed Histmersion,
its config or its database — and a stand-in for the OCR converter that answers health checks only.
It loads the demo collection ({{ repo_file("docs/demo/README.md") }}) through the REST API, then
Playwright ({{ repo_file("modules/web/test/docs-screenshots.mjs") }}) takes each picture at a fixed
viewport (1440 × 900, the phone at 390 × 844), with the browser's clock fixed and every request but
the server's and the map tiles refused, waiting for the network, the tiles and the fonts. Afterwards
it stops what it started, by process id, and removes the temporary files. No model is called: the
agents are on, so their windows exist, but no provider is configured and every manager is paused.

It needs the pinned JDK 25, sbt, pixi, node with `npm ci --prefix modules/web`, and Playwright's
Chromium (`npm --prefix modules/web exec -- playwright install chromium`). The map's background is
OpenStreetMap's, so it needs the internet (`HISTMERSION_SCREENSHOTS_OFFLINE=1` leaves it blank); keep
its attribution in the picture. `pngquant` and `oxipng`, when installed, shrink the files.

To add a screenshot: add a key to `shots` in the Playwright script (what to open, where to point,
what to click), add the data it needs to the demo collection, run `just docs-screenshots --only
<name>`, and use it in a page:

```markdown
<figure markdown="span">
  ![What the picture shows, for someone who cannot see it](../assets/screenshots/<name>.png){ loading=lazy }
  <figcaption>One line: what to notice.</figcaption>
</figure>
```

Every picture has alt text (what is in it) and a caption (what to look at). A page may show a light and
a dark variant with `#only-light` / `#only-dark` after the file name, as the welcome page does with
the standard and the dark base map. Regenerate the screenshots when the web page changes, and commit
the PNGs with the change.

## Generated pages

Three reference pages hold generated parts between markers. Do not edit inside the markers:

| page | part | regenerate |
|---|---|---|
| `reference/cli.md` | `BEGIN CLI REFERENCE` … `END CLI REFERENCE` | `just cli-docs` |
| `reference/rest-api.md` | `BEGIN API INDEX` … `END API INDEX` | `just api-docs` |
| `reference/mcp-tools.md` | the tool tables (by hand, from `tools/list`; a test checks every tool and the count) | — |

## Rules for pages

- **Write for a reader who is not a programmer** in the user pages: short sentences, every term
  defined on first use, numbers over adjectives. Technical depth goes in *Reference* and
  *Contributing*. The plain-language half of {{ repo_file("docs/writing-guide.md") }}
  is the house style.
- **Every fact from the code**: a command, a flag, a setting or a default is copied from `--help`,
  `reference.conf` or the code — never written from memory.
- **Link to repository files that are not pages** (design notes, scripts, `AGENTS.md`) with the
  {% raw %}`{{ repo_file("docs/plan.md") }}`{% endraw %} macro, which makes an absolute link; a relative link outside
  `user-guide/` fails the strict build.
- **Jinja is live in every page** (the macros). To show literal double braces, wrap them in
  Jinja's `raw` … `endraw` tags.
- **Diagrams** are Mermaid code fences (`` ```mermaid ``), for flows: a pipeline, the architecture.
  What the web page looks like is a **screenshot** (above), never a diagram of it.
- **No personal data** — no e-mail addresses, no machine-specific paths, no secrets; use placeholders
  such as `<data>`, `you@example.org`, `<the key>`.
- Never name an unauthorised source of copyrighted works anywhere.
- Link [Responsible use](../responsible-use.md) from every page that tells people to fetch, compile or
  publish something.
