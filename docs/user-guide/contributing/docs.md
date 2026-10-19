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
- **Diagrams** are Mermaid code fences (`` ```mermaid ``).
- **No personal data** — no e-mail addresses, no machine-specific paths, no secrets; use placeholders
  such as `<data>`, `you@example.org`, `<the key>`.
- Never name an unauthorised source of copyrighted works anywhere.
- Link [Responsible use](../responsible-use.md) from every page that tells people to fetch, compile or
  publish something.
