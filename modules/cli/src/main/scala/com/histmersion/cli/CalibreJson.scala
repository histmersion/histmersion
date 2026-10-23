package com.histmersion.cli

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** What the host-side `calibre … --json` commands print (#1946). Calibre is read on THIS machine, so
  * these answers have no server DTO; they are still case classes with derived codecs, printed through
  * [[Output.emit]] like every other `--json`, never `Json.obj` assembled by hand. The field names are
  * the ones those commands always printed. */
object CalibreJson:

  /** `calibre search --json`: one entry. */
  final case class Book(id: Int, title: String, authors: List[String], year: Option[Int], publisher: Option[String],
      tags: List[String], identifiers: Map[String, String], formats: List[String]) derives KindlingsCodecAsObject
  object Book:
    def of(b: CalibreBook): Book = Book(b.id, b.title, b.authors, b.year, b.publisher, b.tags, b.identifiers, b.formats)

  /** `calibre link --json`, `calibre push --json`. */
  final case class Linked(citekey: String, calibreId: Int) derives KindlingsCodecAsObject

  /** `calibre push --replace --json`: `replaced` is the format swapped in. */
  final case class Replaced(citekey: String, calibreId: Int, replaced: String) derives KindlingsCodecAsObject

  /** `calibre update <citekey> --json`: the fields written. */
  final case class Updated(citekey: String, calibreId: Int, changed: List[String]) derives KindlingsCodecAsObject

  final case class Change(field: String, from: String, to: String) derives KindlingsCodecAsObject

  /** `calibre update --all --json`: one entry's outcome. */
  final case class UpdateRow(calibreId: Int, citekeys: List[String], state: String, changes: List[Change],
      reason: Option[String]) derives KindlingsCodecAsObject
  object UpdateRow:
    def of(e: UpdateEntry): UpdateRow =
      val (state, changes) = e.outcome match
        case UpdateOutcome.Change(cs)    => ("would-change", cs)
        case UpdateOutcome.Applied(cs)   => ("updated", cs)
        case UpdateOutcome.Failed(cs, _) => ("failed", cs)
        case UpdateOutcome.UpToDate      => ("up-to-date", Nil)
        case UpdateOutcome.Shared        => ("shared", Nil)
        case UpdateOutcome.Missing       => ("missing", Nil)
        case UpdateOutcome.Refused(_)    => ("refused", Nil)
      val reason = e.outcome match
        case UpdateOutcome.Failed(_, r) => Some(r)
        case UpdateOutcome.Refused(r)   => Some(r)
        case _                          => None
      UpdateRow(e.calibreId, e.citekeys, state, changes.map(ch => Change(ch.field.name, ch.from, ch.to)), reason)

  /** `calibre audit --json`: one entry that disagrees with its record; `fix` = the citekey whose record
    * `calibre update` would write over it, absent when the entry is missing. */
  final case class AuditRow(calibreId: Int, citekeys: List[String], issues: List[String], fix: Option[String])
      derives KindlingsCodecAsObject
  object AuditRow:
    def of(f: AuditFinding): AuditRow =
      AuditRow(f.calibreId, f.citekeys, f.issues.map(_.toString), Option.when(f.fixable)(f.record.citekey))

  /** `calibre gaps --json` / `calibre sync --json`: EVERY source the server lists, each with where its
    * original is (`local` | `stored` | `none` | `unknown`) and whether it `counted` as a gap. `count`
    * is the counted ones (what `--quiet` reminds about), `uncounted` the rest, `total` both. The
    * server's own fields keep their names, so a script reading `gaps[].citekey` is unchanged. */
  final case class GapRow(citekey: String, title: String, authors: List[String], doi: Option[String],
      year: Option[Int], venue: Option[String], summary: Option[String], topics: List[String],
      provenance: Option[String], calibreId: Option[String], isbn: Option[String],
      storedOriginal: Option[Boolean], original: String, counted: Boolean) derives KindlingsCodecAsObject
  object GapRow:
    def of(e: Calibre.GapEntry): GapRow =
      val g = e.gap
      GapRow(g.citekey, g.title, g.authors, g.doi, g.year, g.venue, g.summary, g.topics, g.provenance,
        g.calibreId, g.isbn, g.storedOriginal, e.original.label, e.counted)

  final case class Gaps(count: Int, uncounted: Int, total: Int, gaps: List[GapRow]) derives KindlingsCodecAsObject
  object Gaps:
    def of(entries: List[Calibre.GapEntry]): Gaps =
      val n = entries.count(_.counted)
      Gaps(n, entries.size - n, entries.size, entries.map(GapRow.of))
