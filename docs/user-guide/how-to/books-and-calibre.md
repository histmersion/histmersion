# Books and Calibre

Many of the books a subject rests on are not open access. Buy them, borrow them, or use your
library's access — then bring your copy in. If you keep your e-books in
[Calibre](https://calibre-ebook.com), the free e-book manager, Histmersion works with it directly.

**Calibre keeps the originals; Histmersion keeps the text and the enrichment.** Histmersion copies a
book out of Calibre to read it, and never changes the Calibre original when it imports.

## Where your library is

`cli.calibre.library-dir` (environment `HISTMERSION_CALIBRE_LIBRARY_DIR`), by default
`~/Calibre Library`. The `histmersion calibre …` commands run on **your** computer (not in the
server) and read the library with Calibre's own `calibredb`, or read-only with `sqlite3` while the
Calibre program is open.

## Import a book you own

```bash
histmersion calibre search "Anthony horse wheel"      # find it; note its Calibre id
histmersion calibre import 1234 --citekey anthony2007_horse
histmersion calibre import 1234 --citekey anthony2007_horse --acquire-item 42   # and tick off its acquisition item
```

The file is uploaded to the server, converted and read like any other source, and the source is
linked to its Calibre entry.

## Keep Calibre complete

Papers that came in from open-access services, archive.org or your uploads can be added to Calibre
too, with their title, authors, DOI and tags:

```bash
histmersion calibre gaps                 # sources not in Calibre yet
histmersion calibre gaps --quiet         # one line and exit 1 when there are any (for a reminder hook)
histmersion calibre push <citekey>       # add one
histmersion calibre sync                 # what sync would add (a dry run)
histmersion calibre sync --apply         # add all of them
```

A source counts as missing from Calibre only when there is a file to add: the original the server
stored (`original: stored`), or a file under `cli.calibre.originals-dir/<citekey>/` on your computer
(`original: local`). Sources whose provenance is `calibre`, or that already have a Calibre id, never
count.

A record with no fetched file is not counted: a citation-graph stub, or a record with only metadata
and an abstract. `gaps` and `sync` give their number on one line ("N records without a fetched
file — not counted; see the acquire list"), and `--quiet` ignores them, so it exits 0 when only
those are left. To add one of them, get the file first: `histmersion ingest <path> --citekey <k>`,
or `histmersion calibre push <citekey> --file <path>`. `--json` still lists every record, with
`original` (`stored`, `local`, `none`) and `counted` (`true` or `false`); `count` is the number
counted, `uncounted` the rest, and `total` the sum of both.

**Close the Calibre program first** for every command that writes to the library (`push`, `sync
--apply`, `update`, `set`): Calibre does not allow two writers.

## Keep the details in step

```bash
histmersion calibre audit                     # compare every linked entry with its record (read-only)
histmersion calibre update <citekey>          # rewrite the Calibre entry's title, authors, year, ids, tags from the record
histmersion calibre set 1234 --field languages:eng --field publisher:'Princeton University Press'
histmersion calibre link <citekey> 1234       # link a source to an existing entry
histmersion calibre push <citekey> --replace  # swap the file of the linked entry for the stored original
```

Use these commands rather than editing the library with other tools while Histmersion is linked to
it, so that the links stay right.

## Covers

`histmersion media covers` gives sources a picture for their hover card: a book's cover from your
Calibre library (read-only), else Open Library's by ISBN. These covers are shown on your own page
only and never travel in a bundle.
