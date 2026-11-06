package com.histmersion.api.library

import com.histmersion.api.Plural
import com.histmersion.api.given
object AcquireMarkdown:


  private def esc(s: String): String = ((s).replace("|", "\\|").replace("\n", " ").trim)
  private def cell(s: Option[String]): String = esc(s.getOrElse("—"))
  private def num(n: Option[Int]): String = (n.map(_.toString).getOrElse("—"))

  private def doiLink(doi: Option[com.histmersion.api.library.Doi]): String =
    (doi.map(d => s"[$d](https://doi.org/$d)").getOrElse("—"))

  private def table(header: List[String], rows: List[List[String]]): String =
    val sep = header.map(_ => "---")
    ((List(header, sep) ++ rows).map(_.mkString("| ", " | ", " |")).mkString("\n"))

  def discover(r: DiscoverResult): String =
    if r.works.isEmpty then s"_No works found for **${esc(r.query.value)}**._"
    else
      val rows = r.works.map { w =>
        val flag = List(
          if w.isRetracted.value then Some("⚠ retracted") else None,
          if w.inDb.value then Some("in db") else None,
          if w.isOa.value then Some(w.oaStatus.getOrElse("oa")) else None
        ).flatten.mkString(", ")
        List(
          w.citekey.map(_.value.toString).map(esc).getOrElse("—"),
          esc(w.title.map(_.value).getOrElse("(untitled)")),
          num(w.year.map(_.value.toInt)),
          esc(w.authors.map(_.value).take(2).mkString(", ") + (if w.authors.map(_.value).size > 2 then " et al." else "")),
          num(w.citedByCount.map(_.value)),
          doiLink(w.doi),
          if flag.isEmpty then "—" else flag
        )
      }
      s"### ${(r).count} work(s) for “${esc(r.query.value)}”\n\n" +
        table(List("citekey", "title", "year", "authors", "cited", "doi", "notes"), rows) +
        "\n\n_Ingest one with `histmersion ingest <doi>`; `in db` means the corpus already has it._"

  def work(w: WorkRef): String =
    val meta = List(
      w.authors.map(_.value).mkString(", "),
      w.year.map(_.value.toInt).map(_.toString).getOrElse(""),
      w.venue.map(_.value).getOrElse(""),
      w.workType.map(_.value).getOrElse("")
    ).filter(_.nonEmpty).mkString(" · ")
    // OpenAlex and Unpaywall set `oaStatus` (`closed` when there is no copy); Crossref records no access at all.
    val oa =
      if ((w.isOa) == _root_.com.histmersion.api.library.Source.Candidate.IsOa(true)) then s"open access (${w.oaStatus.getOrElse("?")})"
      else if w.oaStatus.isEmpty then "OA status unknown (the metadata came from Crossref, which records none)"
      else "no OA copy known"
    val lines = List(
      s"# ${w.title.map(_.value).getOrElse("(untitled)")}",
      "",
      s"_${meta}_",
      "",
      s"**DOI:** ${doiLink(w.doi)}  ",
      s"**Citekey (proposed):** `${w.citekey.map(_.value.toString).getOrElse("—")}`  ",
      s"**Cited by:** ${num(w.citedByCount.map(_.value))}  ",
      s"**Access:** $oa" + w.oaUrl.map(u => s" — $u").getOrElse(""),
      if ((w.isRetracted) == _root_.com.histmersion.api.library.WorkRef.IsRetracted(true)) then "\n> ⚠ OpenAlex marks this work as RETRACTED." else "",
      if ((w.inDb) == _root_.com.histmersion.api.library.WorkRef.InDb(true)) then s"\n_Already in the corpus as `${w.citekey.map(_.value.toString).getOrElse("?")}`._" else "",
      w.abstractText.map(a => s"\n## Abstract\n\n$a").getOrElse("")
    )
    lines.filter(_.nonEmpty).mkString("\n")

  def wikirefs(r: WikirefsResult): String =
    if r.dois.isEmpty && r.arxiv.isEmpty then s"_No DOIs cited by **${esc(r.article.value)}**._"
    else
      val known = r.known.map(_.value).toSet
      val rows = r.dois.map(d => List(doiLink(Some(Doi(d.value))), if known.contains(d.value) then "in db" else "—"))
      s"### ${r.article} — ${r.doiCount} DOI(s), ${r.arxiv.size} arXiv id(s)\n\n" +
        table(List("doi", "status"), rows) +
        (if r.arxiv.isEmpty then "" else s"\n\n**arXiv:** ${r.arxiv.mkString(", ")}") +
        "\n\n_A trailhead, not a reading list: assess before ingesting (`histmersion assess <doi>`)._"

  def refs(r: RefsResult): String =
    if r.references.isEmpty then s"_No reference list deposited for **${esc(r.target.value)}** (backend: ${r.backend})._"
    else
      val rows = r.references.map { x =>
        List(
          esc(x.title.map(_.value).orElse(x.raw.map(_.value)).getOrElse("(unstructured)")).take(90),
          num(x.year.map(_.value.toInt)),
          doiLink(x.doi),
          x.citekey.map(_.value.toString).map(k => s"`$k`").getOrElse("—")
        )
      }
      s"### ${(r).count} reference(s) of ${esc(r.target.value)} · backend ${r.backend}\n\n" +
        table(List("reference", "year", "doi", "in corpus"), rows) +
        s"\n\n_${r.resolved} of ${r.count} resolve to stored sources; ${edgeNote(r)}._"

  private def edgeNote(r: RefsResult): String =
    if r.edgesAdded.value > 0 then s"${r.edgesAdded} `references` edge(s) written"
    else ("re-run with `--apply` to write the `references` edges")

  def assessment(a: Assessment): String =
    val icon = a.verdict.value match
      case Acquire.Verdicts.Retracted => "⛔"
      case Acquire.Verdicts.Corrected => "⚠"
      case Acquire.Verdicts.LowImpact => "·"
      case _                          => "✓"
    val head = List(
      s"### $icon ${a.verdict.value.toUpperCase} — ${esc(a.target.value)}",
      "",
      s"**DOI:** ${doiLink(a.doi)}  ",
      a.citekey.map(k => s"**Citekey:** `$k`  ").getOrElse(""),
      s"**Cited by:** ${num(a.citedByCount.map(_.value))}" +
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

  def retractionsUpdate(r: RetractionsUpdate): String =
    r.error match
      case Some(e)           => (s"Retraction Watch refresh FAILED: $e (the mirror still holds ${r.rows} row(s)).")
      case None if ((r.running) == _root_.com.histmersion.api.library.RetractionsUpdate.Running(true)) =>
        (s"Retraction Watch refresh running in the background; the mirror holds **${r.rows}** row(s) " +
          "so far. Ask again to see whether it has finished.")
      case None => (s"Retraction Watch mirror: **${r.rows}** row(s) from ${r.source}.")

  def followups(r: FollowupsResult): String =
    if r.followups.isEmpty then s"_Nothing cites **${esc(r.target.value)}** in that window._"
    else
      val rows = r.followups.map { f =>
        List(
          if f.isInfluential.value then "★" else "",
          esc(f.title.map(_.value).getOrElse("(untitled)")).take(80),
          num(f.year.map(_.value.toInt)),
          f.intents.mkString(", "),
          s"**${f.proposedKind}**",
          doiLink(f.doi),
          f.citekey.map(k => s"`$k`").getOrElse("—")
        )
      }
      val ctx = r.followups.filter(_.contexts.nonEmpty).take(5).map { f =>
        val conf = if f.isInfluential.value then "influential" else "background"
        s"- **${esc(f.title.map(_.value).getOrElse("?"))}** (${f.proposedKind} · $conf)\n" +
          f.contexts.take(2).map(c => s"  > ${esc(c.value)}").mkString("\n")
      }
      val applied =
        if r.edgesAdded.value > 0 then s"\n\n_${r.edgesAdded} edge(s) added to the corpus._"
        else "\n\n_Re-run with `--apply` to record these as typed edges (only papers already in the corpus get one)._"
      val caveat = "\n\n_Proposed edge kinds are **proposed — verify by reading** the citing paper. " +
        "The classification is based on citation contexts and intents from Semantic Scholar, which " +
        "can be wrong (e.g. a paper that cites another to disagree may still be classified as `cites`)._"
      s"### ${(r).count} paper(s) citing ${esc(r.target.value)} · via ${r.backend}\n\n" +
        table(List("★", "title", "year", "intents", "proposed edge", "doi", "in corpus"), rows) +
        (if ctx.isEmpty then "" else "\n\n**Citation contexts**\n" + ctx.mkString("\n")) +
        applied + caveat

  def archiveSearch(r: ArchiveSearchResult): String =
    if r.items.isEmpty then s"_Nothing on archive.org for **${esc(r.query.value)}**._"
    else
      val rows = r.items.map(i =>
        List(
          s"`${i.identifier}`",
          esc(i.title.map(_.value).getOrElse("?")).take(80),
          num(i.year.map(_.value.toInt)),
          esc(i.creator.mkString(", ")).take(40)
        )
      )
      s"### ${(r).count} archive.org item(s) for “${esc(r.query.value)}”\n\n" +
        table(List("identifier", "title", "year", "creator"), rows) +
        "\n\n_Check one with `histmersion archive info <id>` before importing._"

  def archiveItem(i: ArchiveItem): String =
    val files =
      if i.files.isEmpty then "_no epub/pdf files_"
      else
        table(List(("file"), ("format"), ("MB")), i.files.map(f => List(esc(f.name.value), cell(f.format.map(_.value)), (f.megabytes.toString))))
    val restriction =
      if ((i.lendingOnly) == _root_.com.histmersion.api.library.ArchiveItem.LendingOnly(true)) then
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
      s"**Creator:** ${esc((i.creator.mkString(", ")))}  ",
      s"**Year:** ${num(i.year.map(_.value.toInt))}  ",
      s"**Publisher:** ${cell(i.publisher.map(_.value))}"
    ) ++ restriction ++ List("", files) ++ i.description.toList.flatMap(d => List("", d)))
      .mkString("\n")

  def ingest(r: Ingest.Report): String =
    val steps = r.steps.map(s => s"- ${if ((s.ok) == _root_.com.histmersion.api.library.Ingest.Step.Ok(true)) then "✓" else "✗"} **${s.step}** — ${s.detail}")
    val pdf = r.pdf match
      case Some(p) =>
        s"**PDF:** ${p.source}${p.url.map(u => s" — $u").getOrElse("")} (${Plural.count(Plural.CountN(p.bytes.value), _root_.com.histmersion.api.Plural.CountSingular("byte"))})"
      case None if r.steps.exists(s => s.step == _root_.com.histmersion.api.library.Ingest.StepName("fetch") && ((s.ok) == _root_.com.histmersion.api.library.Ingest.Step.Ok(true))) =>
        "**PDF:** not fetched — the source already has its original"
      case None => "**PDF:** none found through legal open access"
    // No stripMargin: `assessment` embeds tables, whose rows start with '|'.
    (List(
      s"### ${if ((r.created) == _root_.com.histmersion.api.library.Ingest.Report.Created(true)) then "Ingested" else "Updated"} `${r.citekey}`",
      "",
      r.title.map(_.value).getOrElse("(untitled)") + "  ",
      s"**DOI:** ${doiLink(r.doi)}  ",
      pdf + "  ",
      s"**Reference edges:** ${r.edgesAdded}",
      ""
    ) ++ steps ++ r.dropIn.toList.flatMap(d => List("", s"> $d"))
      ++ r.assessment.toList.flatMap(a => List("", assessment(a)))).mkString("\n")

  def calibreGaps(g: CalibreGaps): String =
    if g.gaps.isEmpty then ("_Every stored source is in Calibre._")
    else
      // An older server's listing has no `storedOriginal`: the column only when the server sent it.
      val said = g.gaps.exists(_.storedOriginal.isDefined)
      val rows = g.gaps.map(x =>
        List(
          s"`${x.citekey}`",
          esc(x.title.value).take(70),
          num(x.year.map(_.value.toInt)),
          esc((x.authors.take(2).mkString(", "))),
          cell(x.provenance.map(_.value))
        ) ++
          Option.when(said)(x.storedOriginal.fold("?")(v => if v.value then "stored" else "none")).toList
      )
      (s"### ${g.count} source(s) not in Calibre\n\n" +
        table((List("citekey", "title", "year", "authors", "provenance") ++ Option.when(said)("original").toList).map(refactorValue10 => (refactorValue10)), rows) +
        "\n\n_Push them with `histmersion calibre sync --apply`: the file is the original the server stored " +
        "(or `<cli.calibre.originals-dir>/<citekey>/paper.*` on this host); one with none needs " +
        "`histmersion calibre push <citekey> --file <path>`._")
