---
name: enrich-calibre
description: Audit and fix Calibre library metadata — fill missing ISBNs/DOIs, normalize publisher names, author names, tags, title capitalization, and language fields across the research corpus. Runs as an analysis first (showing proposed fixes for approval), then applies accepted changes. Use when metadata is inconsistent or entries were imported without full details.
allowed-tools: Bash(histmersion:*), Bash(calibredb:*), Read, Write
---

# Enrich Calibre entries

Calibre is the canonical store for originals. Its metadata should be complete, consistent, and
machine-searchable. This skill audits the library, proposes fixes, and applies them after approval.

**Calibre GUI must be closed** before any write operation (`calibredb set_metadata`).

## 1. Export the current state

```bash
# Dump all entries the research corpus owns (those with a calibre_id in Histmersion)
histmersion calibre search "" --limit 2000 --json > /tmp/calibre_audit.json
```

Parse the JSON. For each entry, check:

## 2. Completeness audit

For each entry, flag what's missing:

| field | how to fill | notes |
|---|---|---|
| **ISBN** | `histmersion discover meta <doi>` → ISBN; or search by title on OpenLibrary/Crossref | Books should have ISBN; papers usually don't |
| **DOI** | Already in Histmersion for most papers (`histmersion get <ck>` → DOI field) | Push from Histmersion if present but not in Calibre |
| **Publisher** | From Crossref metadata or the book's imprint page | Normalize (see §3) |
| **Year** | Already fixed in Histmersion; push if Calibre disagrees | Calibre year = first-publication year, not reprint |
| **Authors** | From Crossref/histmersion | Normalize (see §3) |
| **Tags** | From Histmersion topics + tags | Normalize (see §3) |
| **Language** | Detect from title/content; most entries are English | `calibredb set_metadata <id> -f languages:eng` |
| **Other identifiers** | OCLC, LCCN, OpenLibrary ID — from Crossref/OpenLibrary | Nice to have, not critical |

## 3. Consistency normalization

### Publisher names

Build a canonical map from the entries. Common issues:
- "OUP Oxford" vs "Oxford University Press" → **Oxford University Press**
- "Wiley" vs "John Wiley & Sons" → **Wiley**
- "BRILL" vs "Brill" → **Brill**
- "Cambridge University Press eBooks" → **Cambridge University Press**
- "Penguin UK" vs "Penguin Books" → **Penguin Books**
- Journal names used as publishers (e.g. "European Journal of Archaeology") → keep as-is (these are papers)
- `null` → fill from Crossref or Histmersion

### Author names

The canonical form is **"Firstname Lastname"** (not "Lastname, Firstname", not "Lastname, Firstname, YYYY-YYYY").

- "Burkert, Walter, 1931-2015" → **"Walter Burkert"**
- "Sir James George Frazer" → **"James George Frazer"**
- "Mircea Eliade, Charles J. Adams" (two authors) → **["Mircea Eliade", "Charles J. Adams"]**
- Initials: expand if possible from Crossref; otherwise keep initials ("J.N. Bremmer" → "Jan N. Bremmer" if known)
- "et al." entries: expand the full author list from Crossref/histmersion if available
- Sort name (Calibre's `author_sort`): **"Lastname, Firstname"** — Calibre usually generates this correctly from the display name

### Tags

Normalize to **lowercase, underscore-separated** (matching Histmersion's tag vocabulary):

- "Gods, Greek; Dieux grecs; Religion; Greece -- Religion" → split into individual tags: `greek_gods`, `greek_religion`
- Archive.org URLs in tags → remove (they're identifiers, not tags)
- "Body; Mind & Spirit" → remove (Calibre import junk)
- "Non-Fiction", "History", "General", "Religion" → remove overly generic tags
- "20th Century", "Retail", "Amazon.com" → remove (noise)
- Keep topical tags that match Histmersion: `ancient_dna`, `bronze_age_collapse`, `demic_diffusion`, etc.
- Use `histmersion facets tags` and `histmersion facets topics` as the canonical vocabulary

### Title capitalization

**Title Case** with these rules:
- Capitalize the first word and all "major" words
- Lowercase articles (a, an, the), conjunctions (and, but, or, nor, for, yet, so), prepositions ≤4 letters (at, by, for, in, of, on, to, up, with) — UNLESS they are the first or last word
- Always capitalize after a colon
- Preserve all-caps abbreviations: DNA, BCE, PIE, LBA, IE
- Preserve intentional styling: `*asterisk*` forms, chemical formulas
- The sort title moves leading "The/A/An" to the end: "The Golden Bough" → sort as "Golden Bough, The"

### Language

Default: `eng` (English). Exceptions:
- `eliade1994_historia` → `pol` (Polish)
- Dumézil volumes → `fre` (French)
- Any other non-English entries → detect and set

## 4. Report before applying

Write a report to `/tmp/calibre_fixes.md` organized as:

```markdown
## Missing fields (N entries)
| calibre_id | citekey | missing |
...

## Publisher normalization (N fixes)
| calibre_id | current | proposed |
...

## Author normalization (N fixes)
| calibre_id | current | proposed |
...

## Tag cleanup (N entries)
| calibre_id | current_tags | proposed_tags |
...

## Title fixes (N entries)
| calibre_id | current | proposed |
...

## Language (N entries need language set)
| calibre_id | title | language |
...
```

**Show this report to the user and wait for approval before applying.**

## 5. Apply (after approval)

```bash
# Set metadata via calibredb (Calibre GUI must be closed)
calibredb set_metadata <id> -f publisher:"Oxford University Press"
calibredb set_metadata <id> -f authors:"Walter Burkert"
calibredb set_metadata <id> -f tags:"greek_religion,sacrifice,ritual"
calibredb set_metadata <id> -f languages:"eng"
calibredb set_metadata <id> -f identifiers:"isbn:9780199216116,doi:10.1093/..."

# For title:
calibredb set_metadata <id> -f title:"Polytheism and Society at Athens"
```

Apply in batches. After each batch, verify with `histmersion calibre search <query> --json`.

## Done means

Every research-corpus entry in Calibre has: title (properly cased), authors (Firstname Lastname),
year (first publication), publisher (normalized), language, and at least one identifier (ISBN or
DOI). Tags are from the Histmersion vocabulary. No archive.org URLs in tags. No birth/death years in
author names. Publisher names are consistent across the library.
