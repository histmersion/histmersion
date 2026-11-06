package com.histmersion.api.library

import com.histmersion.api.Plural
import com.histmersion.api.given
object AcquireMarkdown:

  type EscResult = EscResult.T
  object EscResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscS = EscS.T
  object EscS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CellResult = CellResult.T
  object CellResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CellS = CellS.T
  object CellS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NumResult = NumResult.T
  object NumResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NumN = NumN.T
  object NumN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoiLinkResult = DoiLinkResult.T
  object DoiLinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoiLinkDoi = DoiLinkDoi.T
  object DoiLinkDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableResult = TableResult.T
  object TableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableHeader = TableHeader.T
  object TableHeader extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableRows = TableRows.T
  object TableRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverResult = DiscoverResult.T
  object DiscoverResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorkResult = WorkResult.T
  object WorkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikirefsResult = WikirefsResult.T
  object WikirefsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsResult = RefsResult.T
  object RefsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgeNoteResult = EdgeNoteResult.T
  object EdgeNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssessmentResult = AssessmentResult.T
  object AssessmentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetractionsUpdateResult = RetractionsUpdateResult.T
  object RetractionsUpdateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FollowupsResult = FollowupsResult.T
  object FollowupsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveSearchResult = ArchiveSearchResult.T
  object ArchiveSearchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveItemResult = ArchiveItemResult.T
  object ArchiveItemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestResult = IngestResult.T
  object IngestResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreGapsResult = CalibreGapsResult.T
  object CalibreGapsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def esc(s: com.histmersion.api.library.AcquireMarkdown.EscS): com.histmersion.api.library.AcquireMarkdown.EscResult = s.replace("|", "\\|").replace("\n", " ").trim
  private def cell(s: Option[com.histmersion.api.library.AcquireMarkdown.CellS]): com.histmersion.api.library.AcquireMarkdown.CellResult = esc(s.getOrElse("—"))
  private def num(n: Option[com.histmersion.api.library.AcquireMarkdown.NumN]): com.histmersion.api.library.AcquireMarkdown.NumResult = _root_.com.histmersion.api.library.AcquireMarkdown.NumResult(n.map(_.toString).getOrElse("—"))

  private def doiLink(doi: Option[com.histmersion.api.library.AcquireMarkdown.DoiLinkDoi]): com.histmersion.api.library.AcquireMarkdown.DoiLinkResult =
    _root_.com.histmersion.api.library.AcquireMarkdown.DoiLinkResult(doi.map(d => s"[$d](https://doi.org/$d)").getOrElse("—"))

  private def table(header: List[com.histmersion.api.library.AcquireMarkdown.TableHeader], rows: List[List[com.histmersion.api.library.AcquireMarkdown.TableRows]]): com.histmersion.api.library.AcquireMarkdown.TableResult =
    val sep = header.map(_ => "---")
    _root_.com.histmersion.api.library.AcquireMarkdown.TableResult((List(header, sep) ++ rows).map(_.mkString("| ", " | ", " |")).mkString("\n"))

  def discover(r: DiscoverResult): com.histmersion.api.library.AcquireMarkdown.DiscoverResult =
    if r.works.isEmpty then s"_No works found for **${esc(r.query)}**._"
    else
      val rows = r.works.map { w =>
        val flag = List(
          if w.isRetracted then Some("⚠ retracted") else None,
          if w.inDb then Some("in db") else None,
          if w.isOa then Some(w.oaStatus.getOrElse("oa")) else None
        ).flatten.mkString(", ")
        List(
          w.citekey.map(_.value.toString).map(esc).getOrElse("—"),
          esc(w.title.map(_.value).getOrElse("(untitled)")),
          num(w.year.map(_.value.toInt)),
          esc(w.authors.map(_.value).take(2).mkString(", ") + (if w.authors.map(_.value).size > 2 then " et al." else "")),
          num(w.citedByCount),
          doiLink(w.doi.map(_.value)),
          if flag.isEmpty then "—" else flag
        )
      }
      s"### ${r.count} work(s) for “${esc(r.query)}”\n\n" +
        table(List("citekey", "title", "year", "authors", "cited", "doi", "notes"), rows) +
        "\n\n_Ingest one with `histmersion ingest <doi>`; `in db` means the corpus already has it._"

  def work(w: WorkRef): com.histmersion.api.library.AcquireMarkdown.WorkResult =
    val meta = List(
      w.authors.map(_.value).mkString(", "),
      w.year.map(_.value.toInt).map(_.toString).getOrElse(""),
      w.venue.map(_.value).getOrElse(""),
      w.workType.getOrElse("")
    ).filter(_.nonEmpty).mkString(" · ")
    // OpenAlex and Unpaywall set `oaStatus` (`closed` when there is no copy); Crossref records no access at all.
    val oa =
      if w.isOa then s"open access (${w.oaStatus.getOrElse("?")})"
      else if w.oaStatus.isEmpty then "OA status unknown (the metadata came from Crossref, which records none)"
      else "no OA copy known"
    val lines = List(
      s"# ${w.title.map(_.value).getOrElse("(untitled)")}",
      "",
      s"_${meta}_",
      "",
      s"**DOI:** ${doiLink(w.doi.map(_.value))}  ",
      s"**Citekey (proposed):** `${w.citekey.map(_.value.toString).getOrElse("—")}`  ",
      s"**Cited by:** ${num(w.citedByCount)}  ",
      s"**Access:** $oa" + w.oaUrl.map(u => s" — $u").getOrElse(""),
      if w.isRetracted then "\n> ⚠ OpenAlex marks this work as RETRACTED." else "",
      if w.inDb then s"\n_Already in the corpus as `${w.citekey.map(_.value.toString).getOrElse("?")}`._" else "",
      w.abstractText.map(a => s"\n## Abstract\n\n$a").getOrElse("")
    )
    lines.filter(_.nonEmpty).mkString("\n")

  def wikirefs(r: WikirefsResult): com.histmersion.api.library.AcquireMarkdown.WikirefsResult =
    if r.dois.isEmpty && r.arxiv.isEmpty then s"_No DOIs cited by **${esc(r.article)}**._"
    else
      val known = r.known.toSet
      val rows = r.dois.map(d => List(doiLink(Some(d)), if known.contains(d) then "in db" else "—"))
      s"### ${r.article} — ${r.doiCount} DOI(s), ${r.arxiv.size} arXiv id(s)\n\n" +
        table(List("doi", "status"), rows) +
        (if r.arxiv.isEmpty then "" else s"\n\n**arXiv:** ${r.arxiv.mkString(", ")}") +
        "\n\n_A trailhead, not a reading list: assess before ingesting (`histmersion assess <doi>`)._"

  def refs(r: RefsResult): com.histmersion.api.library.AcquireMarkdown.RefsResult =
    if r.references.isEmpty then s"_No reference list deposited for **${esc(r.target)}** (backend: ${r.backend})._"
    else
      val rows = r.references.map { x =>
        List(
          esc(x.title.map(_.value).orElse(x.raw).getOrElse("(unstructured)")).take(90),
          num(x.year.map(_.value.toInt)),
          doiLink(x.doi.map(_.value)),
          x.citekey.map(_.value.toString).map(k => s"`$k`").getOrElse("—")
        )
      }
      s"### ${r.count} reference(s) of ${esc(r.target)} · backend ${r.backend}\n\n" +
        table(List("reference", "year", "doi", "in corpus"), rows) +
        s"\n\n_${r.resolved} of ${r.count} resolve to stored sources; ${edgeNote(r)}._"

  private def edgeNote(r: RefsResult): com.histmersion.api.library.AcquireMarkdown.EdgeNoteResult =
    if r.edgesAdded > 0 then s"${r.edgesAdded} `references` edge(s) written"
    else _root_.com.histmersion.api.library.AcquireMarkdown.EdgeNoteResult("re-run with `--apply` to write the `references` edges")

  def assessment(a: Assessment): com.histmersion.api.library.AcquireMarkdown.AssessmentResult =
    val icon = a.verdict match
      case Acquire.Verdicts.Retracted => "⛔"
      case Acquire.Verdicts.Corrected => "⚠"
      case Acquire.Verdicts.LowImpact => "·"
      case _                          => "✓"
    val head = List(
      s"### $icon ${a.verdict.toUpperCase} — ${esc(a.target)}",
      "",
      s"**DOI:** ${doiLink(a.doi)}  ",
      a.citekey.map(k => s"**Citekey:** `$k`  ").getOrElse(""),
      s"**Cited by:** ${num(a.citedByCount)}" +
        a.influentialCitations.map(i => s" ($i influential)").getOrElse("") + "  ",
      s"**Recommendation:** ${a.recommendation}"
    ).filter(_.nonEmpty).mkString("\n")

    val rw =
      if a.retractionWatch.isEmpty then ""
      else
        "\n\n**Retraction Watch**\n" + a.retractionWatch
          .map { n =>
            s"- ${n.nature.getOrElse("notice")}${n.date.map(d => s" ($d)").getOrElse("")}" +
              n.reason.map(r => s" — $r").getOrElse("")
          }
          .mkString("\n")

    val cr =
      if a.crossrefUpdates.isEmpty then ""
      else
        "\n\n**Crossref update notices**\n" + a.crossrefUpdates
          .map { u =>
            s"- ${u.kind.getOrElse("update")}: ${u.label.getOrElse("")} ${doiLink(u.doi)}"
          }
          .mkString("\n")

    val applied = a.applied
      .map(s => s"\n\n_Stored `status` set to **$s**._")
      .orElse(
        a.kept.map(k =>
          s"\n\n_Kept **${k.wire}** (set by hand) — pass `--force-status` (`forceStatus: true`) to " +
            "overwrite it with this verdict._"
        )
      )
      .getOrElse(if Acquire.statusFor(a.verdict).isDefined && a.citekey.isDefined then
        "\n\n_Re-run with `--apply` to write this verdict to the source's `status`._"
      else "")
    head + rw + cr + applied

  def retractionsUpdate(r: RetractionsUpdate): com.histmersion.api.library.AcquireMarkdown.RetractionsUpdateResult =
    r.error match
      case Some(e)           => _root_.com.histmersion.api.library.AcquireMarkdown.RetractionsUpdateResult(s"Retraction Watch refresh FAILED: $e (the mirror still holds ${r.rows} row(s)).")
      case None if r.running =>
        _root_.com.histmersion.api.library.AcquireMarkdown.RetractionsUpdateResult(s"Retraction Watch refresh running in the background; the mirror holds **${r.rows}** row(s) " +
          "so far. Ask again to see whether it has finished.")
      case None => _root_.com.histmersion.api.library.AcquireMarkdown.RetractionsUpdateResult(s"Retraction Watch mirror: **${r.rows}** row(s) from ${r.source}.")

  def followups(r: FollowupsResult): com.histmersion.api.library.AcquireMarkdown.FollowupsResult =
    if r.followups.isEmpty then s"_Nothing cites **${esc(r.target)}** in that window._"
    else
      val rows = r.followups.map { f =>
        List(
          if f.isInfluential then "★" else "",
          esc(f.title.getOrElse("(untitled)")).take(80),
          num(f.year),
          f.intents.mkString(", "),
          s"**${f.proposedKind}**",
          doiLink(f.doi),
          f.citekey.map(k => s"`$k`").getOrElse("—")
        )
      }
      val ctx = r.followups.filter(_.contexts.nonEmpty).take(5).map { f =>
        val conf = if f.isInfluential then "influential" else "background"
        s"- **${esc(f.title.getOrElse("?"))}** (${f.proposedKind} · $conf)\n" +
          f.contexts.take(2).map(c => s"  > ${esc(c)}").mkString("\n")
      }
      val applied =
        if r.edgesAdded > 0 then s"\n\n_${r.edgesAdded} edge(s) added to the corpus._"
        else "\n\n_Re-run with `--apply` to record these as typed edges (only papers already in the corpus get one)._"
      val caveat = "\n\n_Proposed edge kinds are **proposed — verify by reading** the citing paper. " +
        "The classification is based on citation contexts and intents from Semantic Scholar, which " +
        "can be wrong (e.g. a paper that cites another to disagree may still be classified as `cites`)._"
      s"### ${r.count} paper(s) citing ${esc(r.target)} · via ${r.backend}\n\n" +
        table(List("★", "title", "year", "intents", "proposed edge", "doi", "in corpus"), rows) +
        (if ctx.isEmpty then "" else "\n\n**Citation contexts**\n" + ctx.mkString("\n")) +
        applied + caveat

  def archiveSearch(r: ArchiveSearchResult): com.histmersion.api.library.AcquireMarkdown.ArchiveSearchResult =
    if r.items.isEmpty then s"_Nothing on archive.org for **${esc(r.query)}**._"
    else
      val rows = r.items.map(i =>
        List(
          s"`${i.identifier}`",
          esc(i.title.getOrElse("?")).take(80),
          num(i.year),
          esc(i.creator.mkString(", ")).take(40)
        )
      )
      s"### ${r.count} archive.org item(s) for “${esc(r.query)}”\n\n" +
        table(List("identifier", "title", "year", "creator"), rows) +
        "\n\n_Check one with `histmersion archive info <id>` before importing._"

  def archiveItem(i: ArchiveItem): com.histmersion.api.library.AcquireMarkdown.ArchiveItemResult =
    val files =
      if i.files.isEmpty then "_no epub/pdf files_"
      else
        table(List(_root_.com.histmersion.api.library.AcquireMarkdown.TableHeader("file"), _root_.com.histmersion.api.library.AcquireMarkdown.TableHeader("format"), _root_.com.histmersion.api.library.AcquireMarkdown.TableHeader("MB")), i.files.map(f => List(esc(f.name), cell(f.format), _root_.com.histmersion.api.library.AcquireMarkdown.TableRows(f.megabytes.toString))))
    val restriction =
      if i.lendingOnly then
        List(
          "",
          "> ⛔ **Lending-only item.** It must be BORROWED on archive.org; it is never " +
            "downloaded here (no DRM circumvention)."
        )
      else Nil
    // No stripMargin: every table row starts with '|', which stripMargin would eat.
    (List(
      s"### ${i.title.getOrElse(i.identifier)}",
      "",
      s"**Identifier:** `${i.identifier}`  ",
      s"**Creator:** ${esc(i.creator.mkString(", "))}  ",
      s"**Year:** ${num(i.year)}  ",
      s"**Publisher:** ${cell(i.publisher)}"
    ) ++ restriction ++ List("", files) ++ i.description.toList.flatMap(d => List("", d)))
      .mkString("\n")

  def ingest(r: Ingest.Report): com.histmersion.api.library.AcquireMarkdown.IngestResult =
    val steps = r.steps.map(s => s"- ${if s.ok then "✓" else "✗"} **${s.step}** — ${s.detail}")
    val pdf = r.pdf match
      case Some(p) =>
        s"**PDF:** ${p.source}${p.url.map(u => s" — $u").getOrElse("")} (${Plural.count(p.bytes, _root_.com.histmersion.api.Plural.CountSingular("byte"))})"
      case None if r.steps.exists(s => s.step == "fetch" && s.ok) =>
        "**PDF:** not fetched — the source already has its original"
      case None => "**PDF:** none found through legal open access"
    // No stripMargin: `assessment` embeds tables, whose rows start with '|'.
    (List(
      s"### ${if r.created then "Ingested" else "Updated"} `${r.citekey}`",
      "",
      r.title.getOrElse("(untitled)") + "  ",
      s"**DOI:** ${doiLink(r.doi)}  ",
      pdf + "  ",
      s"**Reference edges:** ${r.edgesAdded}",
      ""
    ) ++ steps ++ r.dropIn.toList.flatMap(d => List("", s"> $d"))
      ++ r.assessment.toList.flatMap(a => List("", assessment(a)))).mkString("\n")

  def calibreGaps(g: CalibreGaps): com.histmersion.api.library.AcquireMarkdown.CalibreGapsResult =
    if g.gaps.isEmpty then _root_.com.histmersion.api.library.AcquireMarkdown.CalibreGapsResult("_Every stored source is in Calibre._")
    else
      // An older server's listing has no `storedOriginal`: the column only when the server sent it.
      val said = g.gaps.exists(_.storedOriginal.isDefined)
      val rows = g.gaps.map(x =>
        List(
          s"`${x.citekey}`",
          esc(x.title).take(70),
          num(x.year),
          esc(_root_.com.histmersion.api.library.AcquireMarkdown.EscS(x.authors.take(2).mkString(", "))),
          cell(x.provenance)
        ) ++
          Option.when(said)(x.storedOriginal.fold("?")(if _ then "stored" else "none")).toList
      )
      s"### ${g.count} source(s) not in Calibre\n\n" +
        table((List("citekey", "title", "year", "authors", "provenance") ++ Option.when(said)("original").toList).map(refactorValue10 => _root_.com.histmersion.api.library.AcquireMarkdown.TableHeader(refactorValue10)), rows) +
        "\n\n_Push them with `histmersion calibre sync --apply`: the file is the original the server stored " +
        "(or `<cli.calibre.originals-dir>/<citekey>/paper.*` on this host); one with none needs " +
        "`histmersion calibre push <citekey> --file <path>`._"
