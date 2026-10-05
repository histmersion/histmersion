---
name: sync-calibre
description: Add every fetched source (OA papers, archive.org books, uploaded files) into Calibre — the canonical store for originals — with proper metadata, so nothing goes missing from the library. Use after any acquisition (ingest-paper / import-archive / import-file), when the Calibre-gap hook reminds you, or when the user asks to make sure downloads are in Calibre.
allowed-tools: Bash(histmersion:*), {{tool:*}}
---

# Sync fetched sources into Calibre

Calibre is the **canonical** store for originals (`CLAUDE.md` → "Calibre is canonical"). Anything
Histmersion fetched that is not already `provenance: calibre` must be added there with metadata, or the
user's library silently loses it. `histmersion calibre …` is the only way to touch Calibre — never
`calibredb` directly.

The hook wired in `.claude/settings.json` runs `histmersion calibre gaps --quiet` after Bash calls and at
end of turn; it prints one line and exits 1 when gaps exist. This skill is the procedure it points at.

## 1. See the gap (safe, read-only)

```bash
histmersion calibre gaps                 # the full list: citekey, title, year, authors, provenance
histmersion calibre gaps --limit 20
histmersion calibre sync                 # dry run: exactly what --apply would push
```

A source is a gap when it has a stored original and `provenance != calibre` and no `calibreId` yet.

## 2. Push them (Calibre GUI MUST be closed)

```bash
histmersion calibre sync --apply                     # everything
histmersion calibre sync --apply --limit 10          # a batch at a time
histmersion calibre push <citekey>                   # just one
```

Each push adds the file to Calibre with title/authors/DOI/tags from the database and records the
`calibreId` back on the source. If the GUI is open the command refuses — ask the user to close it,
then re-run. Do not work around it.

When a record that is already in Calibre changes its title or authors (a chapter split out of a
volume, a corrected author list), bring the entry in line instead of pushing a duplicate:

```bash
histmersion calibre update <citekey>                 # one entry
histmersion calibre update --all                     # every linked entry: a dry run listing the changes
histmersion calibre update --all --apply             # …written (Calibre GUI closed)
```

It rewrites the entry named by the source's `calibreId`: title, authors ("First Last", no role or
life dates), the record's year, identifiers (the DOI merged in; Calibre's ISBN, google, asin… kept)
and tags (= the record's topics); publisher and description only where Calibre has none. The file
stays as it is; `--all` leaves an entry linked from several records alone.

## 3. Check for duplicates afterwards

The old `dedup_raw.py` is gone: Histmersion stores one original per source and lint catches the rest.

```bash
histmersion lint --rule duplicate-sha256 --rule duplicate-doi
```

(`{{tool:lint}}` with `rules: ["duplicate-sha256","duplicate-doi"]`.) Report a real duplicate to
the user with both citekeys rather than deleting anything.

## Notes

- **Books and articles both belong in Calibre** — the user keeps papers there too.
- If a source's metadata is thin, enrich it first (`{{tool:patch_source}}` →
  `metadata.title/authors/year/doi`) so Calibre gets clean fields rather than "Unknown".
- Skip an item deliberately only when the user says so (e.g. an unauthorised-mirror file they do not want in
  their library). The default is: everything fetched goes into Calibre.

## Done means

`histmersion calibre gaps --quiet` exits 0.
