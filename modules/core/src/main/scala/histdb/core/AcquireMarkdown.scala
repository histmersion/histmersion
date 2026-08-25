package histdb.core

/** Markdown renderings of the acquisition & assessment results — the wire format of every surface:
  * the MCP tools return these strings, the CLI prints them, and they read the same either way. Pure
  * functions of a DTO, like [[Markdown]]. */
object AcquireMarkdown:

  private def esc(s: String): String = s.replace("|", "\\|").replace("\n", " ").trim
  private def cell(s: Option[String]): String = esc(s.getOrElse("—"))
  private def num(n: Option[Int]): String     = n.map(_.toString).getOrElse("—")

  /** `10.1038/nature14317` → a resolver link, so a reader can open it from the terminal or the chat. */
  private def doiLink(doi: Option[String]): String =
    doi.map(d => s"[$d](https://doi.org/$d)").getOrElse("—")

  private def table(header: List[String], rows: List[List[String]]): String =
    val sep = header.map(_ => "---")
    (List(header, sep) ++ rows).map(_.mkString("| ", " | ", " |")).mkString("\n")

  // ── discover ────────────────────────────────────────────────────────────────────────────────

  def discover(r: DiscoverResult): String =
    if r.works.isEmpty then s"_No works found for **${esc(r.query)}**._"
    else
      val rows = r.works.map { w =>
        val flag = List(
          if w.isRetracted then Some("⚠ retracted") else None,
          if w.inDb then Some("in db") else None,
          if w.isOa then Some(w.oaStatus.getOrElse("oa")) else None
        ).flatten.mkString(", ")
        List(
          w.citekey.map(esc).getOrElse("—"),
          esc(w.title.getOrElse("(untitled)")),
          num(w.year),
          esc(w.authors.take(2).mkString(", ") + (if w.authors.size > 2 then " et al." else "")),
          num(w.citedByCount),
          doiLink(w.doi),
          if flag.isEmpty then "—" else flag
        )
      }
      s"### ${r.count} work(s) for “${esc(r.query)}”\n\n" +
        table(List("citekey", "title", "year", "authors", "cited", "doi", "notes"), rows) +
        "\n\n_Ingest one with `histdb ingest <doi>`; `in db` means the corpus already has it._"

  def work(w: WorkRef): String =
    val meta = List(
      w.authors.mkString(", "),
      w.year.map(_.toString).getOrElse(""),
      w.venue.getOrElse(""),
      w.workType.getOrElse("")
    ).filter(_.nonEmpty).mkString(" · ")
    val oa = if w.isOa then s"open access (${w.oaStatus.getOrElse("?")})" else "no OA copy known"
    val lines = List(
      s"# ${w.title.getOrElse("(untitled)")}",
      "",
      s"_${meta}_",
      "",
      s"**DOI:** ${doiLink(w.doi)}  ",
      s"**Citekey (proposed):** `${w.citekey.getOrElse("—")}`  ",
      s"**Cited by:** ${num(w.citedByCount)}  ",
      s"**Access:** $oa" + w.oaUrl.map(u => s" — $u").getOrElse(""),
      if w.isRetracted then "\n> ⚠ OpenAlex marks this work as RETRACTED." else "",
      if w.inDb then s"\n_Already in the corpus as `${w.citekey.getOrElse("?")}`._" else "",
      w.abstractText.map(a => s"\n## Abstract\n\n$a").getOrElse("")
    )
    lines.filter(_.nonEmpty).mkString("\n")

  def wikirefs(r: WikirefsResult): String =
    if r.dois.isEmpty && r.arxiv.isEmpty then s"_No DOIs cited by **${esc(r.article)}**._"
    else
      val known = r.known.toSet
      val rows  = r.dois.map(d => List(doiLink(Some(d)), if known.contains(d) then "in db" else "—"))
      s"### ${r.article} — ${r.doiCount} DOI(s), ${r.arxiv.size} arXiv id(s)\n\n" +
        table(List("doi", "status"), rows) +
        (if r.arxiv.isEmpty then "" else s"\n\n**arXiv:** ${r.arxiv.mkString(", ")}") +
        "\n\n_A trailhead, not a reading list: assess before ingesting (`histdb assess <doi>`)._"

  // ── refs ────────────────────────────────────────────────────────────────────────────────────

  def refs(r: RefsResult): String =
    if r.references.isEmpty then
      s"_No reference list deposited for **${esc(r.target)}** (backend: ${r.backend})._"
    else
      val rows = r.references.map { x =>
        List(
          esc(x.title.orElse(x.raw).getOrElse("(unstructured)")).take(90),
          num(x.year),
          doiLink(x.doi),
          x.citekey.map(k => s"`$k`").getOrElse("—")
        )
      }
      s"### ${r.count} reference(s) of ${esc(r.target)} · backend ${r.backend}\n\n" +
        table(List("reference", "year", "doi", "in corpus"), rows) +
        s"\n\n_${r.resolved} of ${r.count} resolve to stored sources; ${edgeNote(r)}._"

  private def edgeNote(r: RefsResult): String =
    if r.edgesAdded > 0 then s"${r.edgesAdded} `references` edge(s) written"
    else "re-run with `--apply` to write the `references` edges"

  // ── assess ──────────────────────────────────────────────────────────────────────────────────

  def assessment(a: Assessment): String =
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
        a.influentialCitations.map(i => s" (${i} influential)").getOrElse("") + "  ",
      s"**Recommendation:** ${a.recommendation}"
    ).filter(_.nonEmpty).mkString("\n")

    val rw =
      if a.retractionWatch.isEmpty then ""
      else
        "\n\n**Retraction Watch**\n" + a.retractionWatch.map { n =>
          s"- ${n.nature.getOrElse("notice")}${n.date.map(d => s" ($d)").getOrElse("")}" +
            n.reason.map(r => s" — $r").getOrElse("")
        }.mkString("\n")

    val cr =
      if a.crossrefUpdates.isEmpty then ""
      else
        "\n\n**Crossref update notices**\n" + a.crossrefUpdates.map { u =>
          s"- ${u.kind.getOrElse("update")}: ${u.label.getOrElse("")} ${doiLink(u.doi)}"
        }.mkString("\n")

    val applied = a.applied.map(s => s"\n\n_Stored `status` set to **$s**._").getOrElse(
      if Acquire.statusFor(a.verdict).isDefined && a.citekey.isDefined then
        "\n\n_Re-run with `--apply` to write this verdict to the source's `status`._"
      else "")
    head + rw + cr + applied

  def retractionsUpdate(r: RetractionsUpdate): String =
    r.error match
      case Some(e) => s"Retraction Watch refresh FAILED: $e (the mirror still holds ${r.rows} row(s))."
      case None if r.running =>
        s"Retraction Watch refresh running in the background; the mirror holds **${r.rows}** row(s) " +
          "so far. Ask again to see whether it has finished."
      case None => s"Retraction Watch mirror: **${r.rows}** row(s) from ${r.source}."

  // ── followups ───────────────────────────────────────────────────────────────────────────────

  def followups(r: FollowupsResult): String =
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
        s"- **${esc(f.title.getOrElse("?"))}** (${f.proposedKind})\n" +
          f.contexts.take(2).map(c => s"  > ${esc(c)}").mkString("\n")
      }
      val applied =
        if r.edgesAdded > 0 then s"\n\n_${r.edgesAdded} edge(s) added to the corpus._"
        else "\n\n_Re-run with `--apply` to record these as typed edges (only papers already in the corpus get one)._"
      s"### ${r.count} paper(s) citing ${esc(r.target)} · via ${r.backend}\n\n" +
        table(List("★", "title", "year", "intents", "proposed edge", "doi", "in corpus"), rows) +
        (if ctx.isEmpty then "" else "\n\n**Citation contexts**\n" + ctx.mkString("\n")) +
        applied

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  def archiveSearch(r: ArchiveSearchResult): String =
    if r.items.isEmpty then s"_Nothing on archive.org for **${esc(r.query)}**._"
    else
      val rows = r.items.map(i =>
        List(s"`${i.identifier}`", esc(i.title.getOrElse("?")).take(80), num(i.year),
          esc(i.creator.mkString(", ")).take(40)))
      s"### ${r.count} archive.org item(s) for “${esc(r.query)}”\n\n" +
        table(List("identifier", "title", "year", "creator"), rows) +
        "\n\n_Check one with `histdb archive info <id>` before importing._"

  def archiveItem(i: ArchiveItem): String =
    val files =
      if i.files.isEmpty then "_no epub/pdf files_"
      else table(List("file", "format", "MB"),
        i.files.map(f => List(esc(f.name), cell(f.format), f.megabytes.toString)))
    val restriction =
      if i.lendingOnly then
        List("", "> ⛔ **Lending-only item.** It must be BORROWED on archive.org; it is never " +
          "downloaded here (no DRM circumvention).")
      else Nil
    // NB: no stripMargin — every table row starts with '|', which stripMargin would eat.
    (List(
      s"### ${i.title.getOrElse(i.identifier)}",
      "",
      s"**Identifier:** `${i.identifier}`  ",
      s"**Creator:** ${esc(i.creator.mkString(", "))}  ",
      s"**Year:** ${num(i.year)}  ",
      s"**Publisher:** ${cell(i.publisher)}"
    ) ++ restriction ++ List("", files) ++ i.description.toList.flatMap(d => List("", d)))
      .mkString("\n")

  // ── ingest ──────────────────────────────────────────────────────────────────────────────────

  def ingest(r: IngestReport): String =
    val steps = r.steps.map(s => s"- ${if s.ok then "✓" else "✗"} **${s.step}** — ${s.detail}")
    val pdf = r.pdf match
      case Some(p) => s"**PDF:** ${p.source}${p.url.map(u => s" — $u").getOrElse("")} (${p.bytes} bytes)"
      case None    => "**PDF:** none found through legal open access"
    // no stripMargin here either: `assessment` embeds tables and quote lines
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

  // ── calibre gaps (rendered host-side by the CLI) ─────────────────────────────────────────────

  def calibreGaps(g: CalibreGaps): String =
    if g.gaps.isEmpty then "_Every stored source is in Calibre._"
    else
      val rows = g.gaps.map(x =>
        List(s"`${x.citekey}`", esc(x.title).take(70), num(x.year),
          esc(x.authors.take(2).mkString(", ")), cell(x.provenance)))
      s"### ${g.count} source(s) not in Calibre\n\n" +
        table(List("citekey", "title", "year", "authors", "provenance"), rows) +
        "\n\n_Push them with `histdb calibre sync --apply` (needs the file on this host)._"
