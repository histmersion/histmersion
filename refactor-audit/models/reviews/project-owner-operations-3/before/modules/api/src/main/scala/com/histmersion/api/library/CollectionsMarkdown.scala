package com.histmersion.api.library
import com.histmersion.api.Plural

/** The collections' answers as Markdown, for the CLI and the MCP tools alike. */
object CollectionsMarkdown:

  type GapsEscS = GapsEscS.T
  object GapsEscS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateAllCellS = UpdateAllCellS.T
  object UpdateAllCellS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuditCellS = AuditCellS.T
  object AuditCellS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type CollectionsResult = CollectionsResult.T
  object CollectionsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionResult = CollectionResult.T
  object CollectionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WhoResult = WhoResult.T
  object WhoResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WhoAuthors = WhoAuthors.T
  object WhoAuthors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResult = SearchResult.T
  object SearchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnlinkedResult = UnlinkedResult.T
  object UnlinkedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnlinkedTotal = UnlinkedTotal.T
  object UnlinkedTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UncountedLineResult = UncountedLineResult.T
  object UncountedLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UncountedLineN = UncountedLineN.T
  object UncountedLineN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GapsResult = GapsResult.T
  object GapsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SyncResult = SyncResult.T
  object SyncResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateAllResult = UpdateAllResult.T
  object UpdateAllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateAllApplied = UpdateAllApplied.T
  object UpdateAllApplied extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuditResult = AuditResult.T
  object AuditResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportedResult = ImportedResult.T
  object ImportedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedResult = LinkedResult.T
  object LinkedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PushedResult = PushedResult.T
  object PushedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedOneResult = UpdatedOneResult.T
  object UpdatedOneResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetResult = SetResult.T
  object SetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def collections(cs: List[CollectionInfo]): com.histmersion.api.library.CollectionsMarkdown.CollectionsResult =
    if cs.isEmpty then _root_.com.histmersion.api.library.CollectionsMarkdown.CollectionsResult("_No source collection is registered._")
    else _root_.com.histmersion.api.library.CollectionsMarkdown.CollectionsResult(cs.map(collection).mkString("\n\n"))

  def collection(c: CollectionInfo): com.histmersion.api.library.CollectionsMarkdown.CollectionResult =
    s"### ${c.title} (`${c.name}`)\n\n${c.description}\n\n" +
      s"- can: ${c.capabilities.mkString(", ")}\n" +
      s"- reachable: ${if c.reachable then "yes" else s"no — ${c.problem.getOrElse("unknown")}"}"

  private def who(authors: List[com.histmersion.api.library.CollectionsMarkdown.WhoAuthors]): com.histmersion.api.library.CollectionsMarkdown.WhoResult =
    _root_.com.histmersion.api.library.CollectionsMarkdown.WhoResult(authors.take(3).mkString(", ") + (if authors.sizeIs > 3 then " et al." else ""))

  /** A search as a table: one row per entry, with the formats of its files. */
  def search(entries: List[CollectionEntry]): com.histmersion.api.library.CollectionsMarkdown.SearchResult =
    if entries.isEmpty then _root_.com.histmersion.api.library.CollectionsMarkdown.SearchResult("_No matching books in the Calibre library._")
    else
      val rows = entries.map { b =>
        val fmts = b.formats.flatMap(_.split("\\.").lastOption).map(_.toUpperCase).distinct.mkString(", ")
        s"| ${b.id} | ${b.title.replace("|", "\\|")} | ${who(b.authors).replace("|", "\\|")} " +
          s"| ${b.year.map(_.toString).getOrElse("—")} | $fmts |"
      }
      _root_.com.histmersion.api.library.CollectionsMarkdown.SearchResult((List(s"### ${entries.size} book(s)", "", "| id | title | authors | year | formats |", "|---|---|---|---:|---|")
        ++ rows ++ List("", "_Import one with `histmersion calibre import <id> --citekey <k>`._")).mkString("\n"))

  /** The entries no source links, newest first, with the path of each file. */
  def unlinked(entries: List[CollectionEntry], total: com.histmersion.api.library.CollectionsMarkdown.UnlinkedTotal): com.histmersion.api.library.CollectionsMarkdown.UnlinkedResult =
    if entries.isEmpty then _root_.com.histmersion.api.library.CollectionsMarkdown.UnlinkedResult("_Every Calibre entry is linked to a source._")
    else
      val lines = entries.map { b =>
        val year = b.year.fold("")(y => s" ($y)")
        val files = if b.formats.isEmpty then "  - no file" else b.formats.map(f => s"  - `$f`").mkString("\n")
        val by = who(b.authors)
        s"- **${b.id}** · ${b.title}$year — ${if by.isEmpty then "no author" else by}\n$files"
      }
      val shown = if total > entries.size then s"${entries.size} of $total" else entries.size.toString
      (List(
        s"### $shown Calibre ${Plural.noun(total, "entry", "entries")} not linked to any source (newest first)",
        ""
      )
        ++ lines ++ List(
          "",
          "_Import one with `histmersion calibre import <id>`, or link it to an existing record with " +
            "`histmersion calibre link <citekey> <id>`._"
        )).mkString("\n")

  /** The line for the records with no fetched file — never a gap. */
  def uncountedLine(n: com.histmersion.api.library.CollectionsMarkdown.UncountedLineN): com.histmersion.api.library.CollectionsMarkdown.UncountedLineResult =
    s"$n ${Plural.noun(n, _root_.com.histmersion.api.Plural.NounSingular("record"))} without a fetched file — not counted; see the acquire list " +
      "(`histmersion queue acquire`); `--json` lists them."

  /** The counted gaps as a table, then one line for the records with no fetched file. */
  def gaps(rows: List[CollectionGapRow]): com.histmersion.api.library.CollectionsMarkdown.GapsResult =
    def esc(s: com.histmersion.api.library.CollectionsMarkdown.GapsEscS) = s.replace("|", "\\|").replace("\n", " ").trim
    val counted = rows.filter(_.counted)
    val missing = rows.count(_.original == "none")
    val head =
      if counted.isEmpty then "_Every source with a fetched file is in Calibre._"
      else
        val lines = counted.map { g =>
          s"| `${g.citekey}` | ${esc(g.title).take(70)} | ${g.year.fold("—")(_.toString)} " +
            s"| ${esc(g.authors.take(2).mkString(", "))} | ${esc(g.provenance.getOrElse("—"))} | ${g.original} |"
        }
        (List(
          s"### ${counted.size} source(s) not in Calibre",
          "",
          "| citekey | title | year | authors | provenance | original |",
          "|---|---|---|---|---|---|"
        )
          ++ lines ++ List(
            "",
            "_Push them with `histmersion calibre sync --apply`: the file is " +
              "`<cli.calibre.originals-dir>/<citekey>/paper.*` on this host (`local`), else the original the server " +
              "stored (`stored`)._"
          )).mkString("\n")
    if missing == 0 then _root_.com.histmersion.api.library.CollectionsMarkdown.GapsResult(head) else _root_.com.histmersion.api.library.CollectionsMarkdown.GapsResult(s"$head\n\n${uncountedLine(_root_.com.histmersion.api.library.CollectionsMarkdown.UncountedLineN(missing))}")

  /** A sync: the gaps pushed (or that would be), each push, and the summary line. */
  def sync(s: CollectionSync): com.histmersion.api.library.CollectionsMarkdown.SyncResult =
    val ready = s.gaps.gaps.filter(_.counted)
    val missing = s.gaps.gaps.count(_.original == "none")
    val note = if missing == 0 then "" else " · " + uncountedLine(_root_.com.histmersion.api.library.CollectionsMarkdown.UncountedLineN(missing))
    val table = if ready.nonEmpty then gaps(ready) + "\n\n" else ""
    if !s.applied then
      table + s"Dry run: ${ready.size} source(s) would be pushed$note" +
        (if ready.nonEmpty then " Re-run with --apply." else "")
    else
      val lines = s.pushes.map(p =>
        p.id.fold(s"  ! ${p.citekey}: ${p.error.getOrElse("")}")(id => s"  + ${p.citekey} → Calibre id $id")
      )
      val failed = s.pushes.count(_.id.isEmpty)
      table + lines.map(_ + "\n").mkString + s"pushed ${s.pushes.size - failed} source(s), $failed failed$note"

  /** An update of every linked item: what changes (or changed), then what was left alone and why. */
  def updateAll(rows: List[CollectionUpdateRow], applied: com.histmersion.api.library.CollectionsMarkdown.UpdateAllApplied): com.histmersion.api.library.CollectionsMarkdown.UpdateAllResult =
    def cell(s: com.histmersion.api.library.CollectionsMarkdown.UpdateAllCellS) = s.replace("|", "\\|").replace("\n", " ").take(80)
    val changing = rows.collect {
      case r if r.state == "would-change" => (r, "")
      case r if r.state == "updated"      => (r, "written")
      case r if r.state == "failed"       => (r, s"FAILED: ${r.reason.getOrElse("")}")
    }
    val table = changing.flatMap { (r, note) =>
      r.changes.map(c =>
        s"| ${r.id} | ${r.citekeys.mkString(", ")} | ${c.field} | ${cell(c.from)} | ${cell(c.to)} | $note |"
      )
    }
    val left = rows.collect {
      case r if r.state == "shared" =>
        s"- ${r.id}: linked from ${r.citekeys.mkString(", ")} — update one by hand (`histmersion calibre update <citekey>`)"
      case r if r.state == "missing" =>
        s"- ${r.id}: no such Calibre entry (${r.citekeys.mkString(", ")}) — re-push, or clear the source's calibre id"
      case r if r.state == "refused" => s"- ${r.id} (${r.citekeys.mkString(", ")}): ${r.reason.getOrElse("")}"
    }
    val upToDate = rows.count(_.state == "up-to-date")
    val head =
      if changing.isEmpty then s"_No linked Calibre entry needs updating ($upToDate up to date)._"
      else
        val verb = if applied then "updated" else "would change"
        (List(
          s"### ${changing.size} Calibre ${Plural.noun(changing.size, "entry", "entries")} $verb · $upToDate up to date",
          "",
          "| id | citekey | field | Calibre | record | |",
          "|---:|---|---|---|---|---|"
        ) ++ table).mkString("\n")
    val tail = if left.isEmpty then "" else s"\n\n**Left alone (${left.size})**\n\n${left.mkString("\n")}"
    val hint =
      if !applied && changing.nonEmpty then "\n\n_Dry run. Re-run with `--apply` (Calibre GUI closed)._" else ""
    _root_.com.histmersion.api.library.CollectionsMarkdown.UpdateAllResult(head + tail + hint)

  /** An audit: the linked items that disagree with their records. */
  def audit(a: CollectionAudit): com.histmersion.api.library.CollectionsMarkdown.AuditResult =
    def cell(s: com.histmersion.api.library.CollectionsMarkdown.AuditCellS) = s.replace("|", "\\|").replace("\n", " ").take(90)
    def view(v: CollectionRecordView) = s"${v.title} — ${v.authors.mkString("; ")}${v.year.fold("")(y => s" ($y)")}"
    if a.findings.isEmpty then _root_.com.histmersion.api.library.CollectionsMarkdown.AuditResult(s"_All ${a.checked} linked Calibre entries agree with the corpus._")
    else
      val table =
        "| id | citekey(s) | issue | corpus | Calibre | fix |\n|---:|---|---|---|---|---|\n" +
          a.findings
            .map { f =>
              val todo =
                f.fix.fold("re-push, or clear the source's calibre id")(ck => s"`histmersion calibre update $ck`")
              s"| ${f.id} | ${f.citekeys.mkString(", ")} | ${f.labels.mkString("; ")} | ${cell(view(f.record))} | " +
                s"${cell(f.entry.fold("—")(view))} | $todo |"
            }
            .mkString("\n")
      _root_.com.histmersion.api.library.CollectionsMarkdown.AuditResult(s"### ${a.findings.size} of ${a.checked} linked Calibre entries disagree with the corpus\n\n$table\n\n" +
        "_`histmersion calibre update --all` lists every change it would make; `--apply` writes them._")

  def imported(i: CollectionImported): com.histmersion.api.library.CollectionsMarkdown.ImportedResult =
    (List(s"Imported Calibre id ${i.id} as `${i.citekey}` — ${i.title}") ++
      i.relinkedFrom.map(prev => s"Re-linked `${i.citekey}` from Calibre id $prev to ${i.id}") ++
      List(s"File (left in place, never modified): ${i.file}") ++
      i.cover.map(c => if c.warning then s"warning: cover not stored — ${c.line}" else c.line) ++
      i.job.map(j => s"queued job ${j.jobId} for ${j.citekey}") ++
      i.jobError.map(e => s"not converted: $e")).mkString("\n")

  def linked(l: CollectionLinked): com.histmersion.api.library.CollectionsMarkdown.LinkedResult = _root_.com.histmersion.api.library.CollectionsMarkdown.LinkedResult(s"linked `${l.citekey}` to Calibre id ${l.id} — ${l.title}")

  def pushed(p: CollectionPushed): com.histmersion.api.library.CollectionsMarkdown.PushedResult =
    p.replaced.fold(s"added `${p.citekey}` to Calibre as id ${p.id} — ${p.title}")(fmt =>
      s"replaced the ${fmt.toUpperCase} of Calibre id ${p.id} with `${p.citekey}`'s original"
    )

  def updatedOne(u: CollectionUpdatedOne): com.histmersion.api.library.CollectionsMarkdown.UpdatedOneResult =
    if u.changed.isEmpty then _root_.com.histmersion.api.library.CollectionsMarkdown.UpdatedOneResult(s"Calibre id ${u.id} already agrees with `${u.citekey}` — nothing written")
    else _root_.com.histmersion.api.library.CollectionsMarkdown.UpdatedOneResult(s"updated Calibre id ${u.id} from `${u.citekey}` (${u.changed.mkString(", ")}) — ${u.title}")

  def set(s: CollectionSetDone): com.histmersion.api.library.CollectionsMarkdown.SetResult = _root_.com.histmersion.api.library.CollectionsMarkdown.SetResult(s"set ${s.fields.mkString(", ")} on Calibre id ${s.id}")
