package com.histmersion.core

/** Render query results as Markdown — the wire format served to LLMs (MCP) and printed by the CLI. */
object Markdown:

  private def year(y: Option[Int]): String = y.map(_.toString).getOrElse("n.d.")
  private def fmtYear(v: Long): String     = YearFmt(v)
  private def period(start: Option[Long], end: Option[Long]): String = periodLabel(start, end)

  /** `1300 BCE–1100 BCE` (empty when either bound is missing). Public: the SQL layer uses it to
    * phrase the "why" of a match, so the CLI, MCP and the explanations all read the same. */
  def periodLabel(start: Option[Long], end: Option[Long]): String = YearFmt.period(start, end)
  private def place(region: Option[String], lat: Option[Double], lon: Option[Double]): String =
    (lat, lon) match
      case (Some(la), Some(lo)) => s"${region.map(_ + " ").getOrElse("")}($la, $lo)"
      case _                    => region.getOrElse("")

  /** The passage that matched, as a quote line: `> _heading_ — …**term**…` (plan §1.4). Gives an LLM
    * the evidence, not just the citekey. `ts_headline` marks hits with `**` already. */
  private def snippet(h: SearchHit): String =
    h.snippet.map(_.replaceAll("\\s+", " ").trim).filter(_.nonEmpty) match
      case None    => ""
      case Some(s) =>
        val head = h.matchedHeading.map(_.trim).filter(_.nonEmpty).map(x => s"_${x}_ — ").getOrElse("")
        s"\n  > $head$s"

  def searchResults(hits: Vector[SearchHit]): String =
    if hits.isEmpty then "_No matching sources._"
    else
      val body = hits.map { h =>
        val reg      = h.regions.map(Label.display).mkString(", ")
        val meta     = List(year(h.year), h.status.wire, reg).filter(_.nonEmpty).mkString(" · ")
        val per      = period(h.periodStart, h.periodEnd)
        val metaLine = if per.nonEmpty then s"$meta · $per" else meta
        val summary  = h.summary.map(s => s"\n  ${s.trim.take(240)}").getOrElse("")
        s"- **${h.citekey}** — ${h.title}\n  _${metaLine}_${snippet(h)}$summary"
      }
      s"### ${hits.size} result(s)\n\n${body.mkString("\n")}"

  /** The filtered search result (plan §1.0): one entry per SOURCE, each followed by a `matches:`
    * sub-list naming the findings that satisfied the filter — claim · place · period · why — so a
    * human or an LLM can judge relevance without opening the source. */
  /** @param offset the 0-based offset the page starts at (for the paging footer). */
  def searchResults(result: SearchResult, offset: Int = 0): String =
    if result.hits.isEmpty then "_No matching sources._"
    else
      val body = result.hits.map { hit =>
        val h        = hit.source
        val reg      = h.regions.map(Label.display).mkString(", ")
        val meta     = List(year(h.year), h.status.wire, reg).filter(_.nonEmpty).mkString(" · ")
        val per      = period(h.periodStart, h.periodEnd)
        val metaLine = if per.nonEmpty then s"$meta · $per" else meta
        val why      = if hit.why.isEmpty then "" else s"\n  why: ${hit.why.mkString(" · ")}"
        val summary  = h.summary.map(s => s"\n  ${s.trim.take(240)}").getOrElse("")
        val matched =
          if hit.matched.isEmpty then ""
          else
            val items = hit.matched.map { m =>
              val loc  = place(m.regionName, m.lat, m.lon)
              val p    = period(m.periodStart, m.periodEnd)
              val tail = List(loc, p, m.why.mkString(", ")).filter(_.nonEmpty).mkString(" · ")
              s"\n    - ${m.claim.trim}${if tail.nonEmpty then s"  \n      _${tail}_" else ""}"
            }
            s"\n  matches:${items.mkString}"
        s"- **${h.citekey}** — ${h.title}\n  _${metaLine}_$why${snippet(h)}$summary$matched"
      }
      val header =
        if result.total > result.hits.size then s"### ${result.hits.size} of ${result.total} source(s)"
        else s"### ${result.total} source(s)"
      val footer = pagingFooter(offset + 1, offset + result.hits.size, result.total, result.hits.size)
      s"$header\n\n${body.mkString("\n")}$footer"

  /** Brief search results (`verbose=false`): citekey · title · year · status · period · one why-line.
    * No snippets, no matches list, no summaries — just enough to pick a source to open. */
  def briefResults(result: SearchResult, offset: Int = 0): String =
    if result.hits.isEmpty then "_No matching sources._"
    else
      val body = result.hits.map { hit =>
        val h   = hit.source
        val per = period(h.periodStart, h.periodEnd)
        val meta = List(year(h.year), h.status.wire, per).filter(_.nonEmpty).mkString(" · ")
        val why = hit.why.headOption.map(w => s" — $w").getOrElse("")
        s"- **${h.citekey}** — ${h.title}  \n  _${meta}_$why"
      }
      val header =
        if result.total > result.hits.size then s"### ${result.hits.size} of ${result.total} source(s)"
        else s"### ${result.total} source(s)"
      val footer = pagingFooter(offset + 1, offset + result.hits.size, result.total, result.hits.size)
      s"$header\n\n${body.mkString("\n")}$footer"

  /** Trailing paging line (A6): `_showing 1-20 of 54 -- next: offset=20 -- full set: limit=54_` */
  def pagingFooter(first: Int, last: Int, total: Int, pageSize: Int): String =
    if total <= pageSize then ""
    else
      val next = if last < total then s" — next: offset=$last" else ""
      val full = if total <= 500 then s" — full set: limit=$total" else ""
      s"\n\n_showing $first–$last of $total${next}${full}_"

  /** `/api/facets` output: the vocabulary of one dimension with DISTINCT-source counts. */
  def facets(field: String, rows: Vector[Facet]): String =
    if rows.isEmpty then s"_No $field in the current filter._"
    else
      // a `places` value backed by the gazetteer also prints its id — THAT is what `place=` wants
      // (it matches through `finding_places` and the hierarchy, where the bare name only matches text)
      val items = rows.map(f =>
        s"- ${f.value}${contestedMark(f.contested, f.value)} (${f.count})" +
          f.id.map(i => s" · `$i`").getOrElse(""))
      s"### $field (${rows.size})\n\n${items.mkString("\n")}"

  /** A comma-joined metadata value rendered as WEB UI LINKS — `[steppe](/#tag=steppe)`.
    *
    * Reading a source is otherwise a dead end: the page tells you what it is about and gives you no
    * way to ask "what else is about that". The href is the web UI's own URL hash (the keys of
    * `web.UiState`), so pasted anywhere it is a shareable link that opens the web UI with that
    * filter applied. (The HTML pages use `server.HtmlRender.chip`, the same href.) */
  def chipLinks(values: String, hashKey: String): String =
    chipLinks(values.split(",").iterator.map(_.trim).filter(_.nonEmpty).toList, hashKey)

  /** Chip links from a proper list: `[readable label](/#key=value)` for each value. */
  def chipLinks(values: List[String], hashKey: String): String =
    values.filter(_.nonEmpty)
      .map(v => s"[${Label.display(v).replace("[", "\\[").replace("]", "\\]")}](/#$hashKey=${enc(v)})")
      .mkString(", ")

  private[core] def enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

  /** Table of contents as Markdown (the `toc` section of `get_source`). */
  def toc(entries: List[TocEntry]): String =
    if entries.isEmpty then "_(no headings in body)_"
    else
      val items = entries.map(e => s"- **${e.heading}** — ord ${e.ord}, ${e.chars} chars")
      s"### Table of Contents (${entries.size} heading(s))\n\n${items.mkString("\n")}"

  /** A body slice with a trailing truncation hint when there is more.
    * `total` = total characters available. */
  def bodySlice(text: String, offset: Int, total: Int): String =
    val remaining = total - offset - text.length
    if remaining > 0 then
      s"$text\n\n_…${remaining} more chars — pass offset=${offset + text.length}_"
    else text

  /** Just the findings section, for `section=findings`. */
  def sourceFindingsOnly(findings: Vector[FindingRow]): String =
    if findings.isEmpty then "_(no findings)_"
    else
      val items = findings.map { f =>
        val loc  = place(f.regionName, f.lat, f.lon)
        val p    = period(f.periodStart, f.periodEnd)
        val sig  = f.significance.map(s => s"[$s]").getOrElse("")
        val tail = List(if loc.nonEmpty then s"@ $loc" else "", p, f.confidence.map(c => s"(${c.toString.toLowerCase})").getOrElse(""), sig)
          .filter(_.nonEmpty).mkString(" · ")
        s"- ${f.claim}${if tail.nonEmpty then s"  \n  _${tail}_" else ""}"
      }
      s"## Findings (${findings.size})\n${items.mkString("\n")}"

  def source(s: SourceDetail, findings: Vector[FindingRow], fullText: Boolean): String =
    val authorLinks = if s.authors.nonEmpty then chipLinks(s.authors, "author") else ""
    val meta    = List(authorLinks, year(s.year), s.venue.getOrElse(""), s.status.wire).filter(_.nonEmpty).mkString(" · ")
    val per     = period(s.periodStart, s.periodEnd)
    val header  = s"# ${s.title}\n\n_${meta}${if per.nonEmpty then s" · $per" else ""}_"
    val topics  = if s.topics.nonEmpty then s"\n\n**Topics:** ${chipLinks(s.topics, "topic")}" else ""
    val regions = if s.regions.nonEmpty then s"  \n**Regions:** ${chipLinks(s.regions, "place")}" else ""
    val tags    = if s.tags.nonEmpty then s"  \n**Tags:** ${chipLinks(s.tags, "tag")}" else ""
    val doi     = s.doi.map(d => s"  \n**DOI:** $d").getOrElse("")
    val prov    = s.provenance.filter(_.nonEmpty).map(p => s"  \n**Provenance:** $p").getOrElse("")
    // the permanent identity (V030): what a bundle carries, and what survives a rename
    val uid     = s.uid.map(u => s"  \n**Uid:** $u").getOrElse("")
    val summary = s.summary.map(x => s"\n\n${x.trim}").getOrElse("")
    // the agent-written article about the source (plan §1.9a `notes`) — after the abstract, before
    // the evidence, so `get_source` and the HTML page both show what we have compiled about it
    val notes   = s.notes.map(_.trim).filter(_.nonEmpty).map(n => s"\n\n## Notes\n\n$n").getOrElse("")
    val findingsMd =
      if findings.isEmpty then ""
      else
        val items = findings.map { f =>
          val loc  = place(f.regionName, f.lat, f.lon)
          val p    = period(f.periodStart, f.periodEnd)
          val sig  = f.significance.map(s => s"[$s]").getOrElse("")
          val tail = List(if loc.nonEmpty then s"@ $loc" else "", p, f.confidence.map(c => s"(${c.toString.toLowerCase})").getOrElse(""), sig)
            .filter(_.nonEmpty)
            .mkString(" · ")
          s"- ${f.claim}${if tail.nonEmpty then s"  \n  _${tail}_" else ""}"
        }
        s"\n\n## Findings (${findings.size})\n${items.mkString("\n")}"
    // The stored OCR body is normalised HERE as well as at import (plan §1.8): the corpus was seeded
    // before the figure-link rewrite existed, so a body still carrying `/Users/…/figures/x.png` —
    // which a browser resolves against the server root and draws 0×0 — has to come out right without
    // waiting for a `seed --force`. Both steps are idempotent, so doing it twice costs nothing.
    val body =
      if !fullText then ""
      else s.ocrMarkdown.map(t => s"\n\n---\n\n${AssetLinks.normalizeBody(t, s.title)}")
        .getOrElse("\n\n_(no full text stored)_")
    header + topics + regions + tags + doi + prov + uid + summary + notes + findingsMd + body

  /** `isArticle` answers "is there a compiled article under this slug?". An edge target that is not
    * a source but IS an article — `references → bronze_age_collapse`, which is most of them — becomes
    * a link INTO the wiki instead of the bold dead end it used to be; a target that is neither stays
    * plain text, because a link to a 404 is worse than no link. */
  def neighbors(citekey: String, rows: Vector[NeighborRow]): String =
    if rows.isEmpty then
      s"_No references recorded for **$citekey**._ An empty result is not evidence of agreement — " +
        "disagreement edges are sparse (the corpus has only a handful); use `contradictions` for a focused view."
    else
      def block(title: String, rs: Vector[NeighborRow])(item: NeighborRow => String): String =
        if rs.isEmpty then "" else s"\n\n**$title**\n${rs.map(r => s"- _${r.kind}_ → ${item(r)}").mkString("\n")}"
      def sources(dir: String) = rows.filter(r => r.target == NeighborTarget.Source && r.direction == dir)
      val sourceItem: NeighborRow => String = r =>
        val st = r.status.map(s => s" _(${s.wire})_").getOrElse("")
        s"[[${r.citekey}]]${r.title.map(t => s" — $t").getOrElse("")}$st"
      s"### Sources · $citekey" +
        block("Outgoing (this → other)", sources("out"))(sourceItem) +
        block("Incoming (other → this)", sources("in"))(sourceItem) +
        block("Articles", rows.filter(_.target == NeighborTarget.Article))(r =>
          s"[${r.title.getOrElse(Label.display(r.citekey))}](/article/${r.citekey})") +
        block("Not in the corpus yet", rows.filter(_.target == NeighborTarget.Unresolved))(r => s"**${r.citekey}**")

  def graph(g: SourceGraph): String =
    if g.nodes.isEmpty then s"_No graph data for **${g.root}**._"
    else
      val header = s"### Source graph · ${g.root} (depth ${g.depth}, ${Plural.count(g.nodes.size, "node")}, ${Plural.count(g.links.size, "link")})"
      val nodeLines = g.nodes.sortBy(n => (n.depth, n.id)).map { n =>
        val year = n.year.map(y => s" ($y)").getOrElse("")
        val st = n.status.map(s => s" _[${s.wire}]_").getOrElse("")
        val depth = if n.depth > 0 then s" d=${n.depth}" else " **root**"
        s"- [[${n.id}]]$year$st$depth${n.title.map(t => s" — $t").getOrElse("")}"
      }.mkString("\n")
      val linkLines = g.links.map { l =>
        s"- `${l.source}` →_${l.kind}_ `${l.target}`"
      }.mkString("\n")
      s"$header\n\n**Nodes**\n$nodeLines\n\n**Links**\n$linkLines"

  def contradictions(result: ContradictionsResult, citekey: Option[String]): String =
    if result.rows.isEmpty then
      citekey match
        case Some(ck) =>
          s"_No disagreement edges or contested status for **$ck**._ An empty result is not evidence " +
            "of agreement — disagreement edges are extremely sparse in the corpus."
        case None =>
          "_No contradictions found in the corpus._"
    else
      val items = result.rows.map { r =>
        val edge = (r.direction, r.kind, r.otherCitekey) match
          case (Some(dir), Some(k), Some(other)) =>
            val arrow = if dir == "out" then "→" else "←"
            val otherInfo = List(
              r.otherTitle.getOrElse(""),
              r.otherYear.map(_.toString).getOrElse(""),
              r.otherStatus.fold("")(_.wire)
            ).filter(_.nonEmpty).mkString(" · ")
            s"$k $arrow **$other** ($otherInfo)"
          case _ => s"status: ${r.status.wire}"
        s"- **${r.citekey}** (${year(r.year)} · ${r.status.wire}) — $edge"
      }
      val scope = citekey.map(ck => s" · $ck").getOrElse("")
      s"### ${result.total} contradiction(s)$scope\n\n${items.mkString("\n")}\n\n" +
        "_Edge kinds: references, challenges, refutes, updates, superseded_by. " +
        "An empty result is not evidence of agreement._"

  /** "Mentioned in articles" — the compiled wiki pages that cite this source (`article_links` with
    * `target_kind = 'source'`). The other half of the two-way hop: `/article/{slug}` already lists
    * the sources it cites, so a source has to list the articles that cite it, or the graph is only
    * ever walkable downwards. */
  def sourceArticles(rows: Vector[Backlink]): String =
    if rows.isEmpty then ""
    else
      val items = rows.map(b => s"- [${b.title.getOrElse(b.id)}](/article/${b.id})")
      s"\n\n## Mentioned in articles (${rows.size})\n${items.mkString("\n")}"

  def geoResults(hits: Vector[GeoHit]): String =
    if hits.isEmpty then "_No findings match that place/time._"
    else
      val blocks = hits
        .groupBy(h => (h.citekey, h.title))
        .toList
        .sortBy(_._1._1)
        .map { case ((ck, title), hs) =>
          val items = hs.map { h =>
            val loc = place(h.regionName, h.lat, h.lon)
            val p   = period(h.periodStart, h.periodEnd)
            s"  - ${h.claim} — _${List(loc, p).filter(_.nonEmpty).mkString(", ")}_"
          }.mkString("\n")
          s"- **$ck** — $title\n$items"
        }
      s"### ${hits.size} finding(s)\n\n${blocks.mkString("\n")}"

  /** The timeline strip as text (plan §1.7): a Markdown table plus an inline bar chart, so an LLM
    * (MCP) or a terminal (CLI) can see *when* the evidence is without opening the UI.
    * Counts overlap buckets, so they deliberately do not sum to a total.
    *
    * When the table would exceed 12 rows, CONSECUTIVE buckets with the SAME count are collapsed
    * into one wide row (A16): `1600–1360 BCE: 2 sources (24 buckets)`. */
  def histogram(h: TimeHistogram, unit: String): String =
    val span = s"${fmtYear(h.from)} → ${fmtYear(h.to)}"
    if h.buckets.isEmpty then s"_No time buckets for $span._"
    else
      val collapsed = collapseHistogram(h.buckets)
      val max = collapsed.map(_.count).max
      val bar = (n: Int) => if n == 0 then "" else "█" * math.max(1, (n.toDouble / max * 24).round.toInt)
      val rows = collapsed.map { b =>
        val periodLabel = s"${fmtYear(b.start)}–${fmtYear(b.end)}"
        val suffix = if b.merged > 1 then s" _(${b.merged} buckets)_" else ""
        s"| $periodLabel | ${b.count} | ${bar(b.count)} |$suffix"
      }
      val starts = unit == Aggregate.UnitStarts
      val head = if starts then s"### sources starting per ${h.bucket} yrs · $span"
                 else s"### $unit per ${h.bucket} yrs · $span"
      val one  = if unit.endsWith("s") then unit.dropRight(1) else unit
      val note =
        if starts then s"_${h.buckets.count(_.count > 0)} non-empty bucket(s); each source is counted " +
          "ONCE, in the bucket its period starts in, so the bars do sum to the total._"
        else s"_${h.buckets.count(_.count > 0)} non-empty bucket(s); each $one is counted in " +
          "every bucket its period overlaps, so the bars do not sum to a total._"
      // NB: no stripMargin here — every table row starts with '|', which stripMargin would eat.
      val col = if starts then "sources" else unit
      (List(head, "", s"| period | $col | |", "|---|---:|---|") ++ rows ++ List("", note)).mkString("\n")

  /** A collapsed histogram row: the span of one or more consecutive buckets sharing a count. */
  final case class CollapsedBucket(start: Long, end: Long, count: Int, merged: Int)

  /** Collapse consecutive runs of identical counts when the raw table would exceed 12 rows. */
  def collapseHistogram(buckets: List[TimeBucket]): List[CollapsedBucket] =
    if buckets.sizeIs <= 12 then buckets.map(b => CollapsedBucket(b.start, b.end, b.count, 1))
    else
      // group consecutive buckets with the same count
      val runs = buckets.foldLeft(List.empty[CollapsedBucket]) { (acc, b) =>
        acc match
          case head :: tail if head.count == b.count =>
            head.copy(end = b.end, merged = head.merged + 1) :: tail
          case _ =>
            CollapsedBucket(b.start, b.end, b.count, 1) :: acc
      }.reverse
      runs

  /** The §1.6 NESTED tree as indented Markdown — what `histmersion search --group time,region` prints and
    * what the MCP `search` tool returns when `groupBy` is set. One line per bucket:
    * `- **label** — N source(s) · M in more than one subregion · period`, its samples as a deeper
    * indent, so an LLM can see WHEN and WHERE the evidence sits before opening anything.
    *
    * Counts are non-additive by construction (a unit is in every bucket it overlaps), which the
    * trailing note says explicitly — otherwise the first thing any reader does is add the children
    * up and conclude the numbers are broken. */
  def tree(h: TimeHistogram, unit: String): String =
    val span = s"${fmtYear(h.from)} → ${fmtYear(h.to)}"
    if h.tree.isEmpty then s"_No $unit to group for $span._"
    else
      val one  = if unit.endsWith("s") then unit.dropRight(1) else unit
      val head = s"### $unit by ${h.by} · $span" + asOfNote(h.asOf)
      val note = s"_Counts are non-additive: each $one is counted in every bucket it overlaps, so " +
        "children never sum to their parent — \"N in more than one …\" is how many are double-counted._"
      (List(head, "", treeLines(h.tree, unit, 0).mkString("\n"), "", note)).mkString("\n")

  /** The bucket lines of [[tree]], `depth` levels of two-space indent deep. */
  private def treeLines(buckets: List[Bucket], unit: String, depth: Int): List[String] =
    val pad = "  " * depth
    buckets.flatMap { b =>
      val over  = if b.overlap > 0 then s" · ${b.overlap} in more than one ${childNoun(b)}" else ""
      val when  = if b.kind == Tree.KindTime then "" else
        Option(periodLabel(b.start, b.end)).filter(_.nonEmpty).map(p => s" · $p").getOrElse("")
      val id    = b.placeId.map(i => s" `$i`").getOrElse("")
      val line  = s"$pad- **${b.label}**${contestedMark(b.contested, b.label)}$id — " +
        s"${b.count} $unit$over$when"
      val kids  = treeLines(b.children, unit, depth + 1)
      val samps = b.samples.map(x => s"$pad  - ${x.citekey} — ${x.title}")
      line :: (kids ++ samps)
    }

  /** What a bucket's children ARE, for the overlap phrase ("3 in more than one subregion"). */
  private def childNoun(b: Bucket): String =
    if b.children.exists(_.kind == Tree.KindTime) then "period" else "subregion"

  // ── backup / restore (plan §1.10) ─────────────────────────────────────────────────────────────

  /** What `POST /api/backup` wrote — the checksum is what you verify a copied-off dump against. */
  def backup(r: BackupResult): String =
    s"""### Backup written
       |
       |- **file:** ${r.file}
       |- **size:** ${Backup.humanBytes(r.bytes)}
       |- **sha256:** `${r.sha256}`
       |- **took:** ${r.durationMs} ms""".stripMargin +
      (if r.pruned.isEmpty then ""
       else s"\n- **pruned** (older than the newest kept): ${r.pruned.mkString(", ")}")

  /** A backup job: the finished archive, or where it stands. */
  def backupStatus(s: BackupStatus): String =
    s.stateOf match
      case Some(BackupState.Done) => s.result.map(backup).getOrElse(s"### Backup written\n\n- **file:** ${s.file}")
      case Some(BackupState.Failed) =>
        s"### Backup failed\n\n- **file:** ${s.file}\n- **error:** ${s.error.getOrElse("unknown")}"
      case _ =>
        s"### Backup running\n\n- **file:** ${s.file}\n- **for:** ${s.durationMs / 1000} s — " +
          "`histmersion backup` waits for it; `GET /api/backup/status` reports when it is written"

  /** The archives on the server, newest first. */
  def backups(rows: Vector[BackupInfo]): String =
    if rows.isEmpty then "_No backups on the server yet._"
    else
      val items = rows.map(b => s"- **${b.file}** — ${Backup.humanBytes(b.bytes)} · ${b.createdAt}")
      s"### ${rows.size} backup(s)\n\n${items.mkString("\n")}"

  def restored(r: RestoreResult): String =
    val warn =
      if r.warnings.isEmpty then ""
      else s"\n\n_pg_restore reported ${r.warnings.size} ignored error(s):_\n" +
        r.warnings.take(10).map(w => s"- $w").mkString("\n")
    s"""### Restored ${r.file}
       |
       |- **schema version:** ${r.schemaVersion} (Flyway validate passed)
       |- **sources:** ${r.sourcesBefore} → ${r.sourcesAfter}
       |- **took:** ${r.durationMs} ms""".stripMargin + warn
  // ── the gazetteer (plan §1.6a) ───────────────────────────────────────────────────────────────

  /** A hand-traced shape whose extent is disputed is marked everywhere its label is drawn — unless
    * the label already says so (some traced polygons carry "(contested)" in the label itself, and
    * "Israel (united monarchy, as narrated) (contested) (contested)" helps nobody). */
  def contestedMark(contested: Boolean, label: String = ""): String =
    if contested && !label.toLowerCase.contains("contested") then " (contested)" else ""

  private def kindOf(kind: String, ps: Option[Long], pe: Option[Long]): String =
    val p = period(ps, pe)
    if p.isEmpty then kind else s"$kind · $p"

  /** Kind and period as the place's source states it ([[PlaceDating]]: "end 1769 CE, start unknown"). */
  private def kindWhen(s: PlaceSummary): String =
    if s.dating == PlaceDating.Stated then kindOf(s.kind, s.periodStart, s.periodEnd) else s"${s.kind} · ${s.when}"

  private def asOfNote(asOf: Option[Long]): String =
    asOf.map(y => s" · as of ${fmtYear(y)}").getOrElse("")

  /** `/api/places?q=` — search results. Each row carries the id, because THAT is what `--place` /
    * `place=` want: an id matches through `finding_places` and the hierarchy, a name only by text. */
  def places(rows: Vector[PlaceHit], asOf: Option[Long] = None): String =
    if rows.isEmpty then "_No matching places._"
    else
      val items = rows.map { h =>
        val alt = h.matchedName.filter(!_.equalsIgnoreCase(h.label)).map(n => s" (matched “$n”)").getOrElse("")
        val src = h.source.map(s => s" · $s").getOrElse("")
        val meta = s"${kindWhen(h.toSummary)} · ${h.findings} finding(s)$src"
        s"- **${h.label}**${contestedMark(h.contested, h.label)}$alt — `${h.id}`  \n  _${meta}_"
      }
      s"### ${rows.size} place(s)${asOfNote(asOf)}\n\n${items.mkString("\n")}"

  /** The hierarchy as an indented list — `depth` is the distance from the walk's root. */
  def placeTree(rows: Vector[PlaceTreeRow]): String =
    if rows.isEmpty then "_No hierarchy._"
    else
      rows.map { r =>
        s"${"  " * r.depth}- **${r.label}**${contestedMark(r.contested, r.label)} `${r.id}`" +
          s" _(${r.kind}${if r.findings > 0 then s", ${r.findings} finding(s)" else ""})_"
      }.mkString("\n")

  /** `/api/places/{id}` — one place: what it is, when it was, what it contains, and where it sits. */
  def place(d: PlaceDetail, tree: Vector[PlaceTreeRow] = Vector.empty): String =
    val p       = d.place
    val header  = s"# ${p.label}${contestedMark(d.contested || p.contested, p.label)}" +
      s"\n\n_`${p.id}` · ${kindWhen(p)}" +
      s" · ${d.findings} finding(s)${asOfNote(d.asOf)}_"
    val what    = d.descriptions.headOption.map(x => s"\n\n${x.text}").getOrElse("")
    val src     = p.source.map(s => s"  \n**Source:** $s").getOrElse("")
    val shape   = if d.pointOnlyArea then s"  \n**Shape:** ${PlaceShape.NoBoundaryNote}" else ""
    val dating  = if d.datedByFindings then s"  \n**Dating:** ${PlaceTimeBasis.FindingsNote}" else ""
    val names   = if p.names.isEmpty then "" else s"  \n**Also known as:** ${p.names}"
    val geom    = d.geometry.map(g => s"  \n**Geometry:** ${g.take(40).takeWhile(_ != ',')}… (GeoJSON)")
      .getOrElse("  \n**Geometry:** _none_")
    def list(title: String, xs: List[PlaceSummary]) =
      if xs.isEmpty then ""
      else s"\n\n**$title**\n" +
        xs.map(x => s"- ${x.label}${contestedMark(x.contested, x.label)} `${x.id}` _(${x.kind})_")
          .mkString("\n")
    val fps = if d.footprints.isEmpty then "" else
      "\n\n**Footprints (" + d.footprints.size + ")**\n" +
        d.footprints.map { f =>
          val per = f.period.map(p => s" · ${periodLabel(Some(p.start), Some(p.end))}").getOrElse("")
          val cert = f.certainty.map(c => s" · _${c}_").getOrElse("")
          val ck = f.citekey.map(c => s" [[${c}]]").getOrElse("")
          s"- ${f.role}${per}${cert}${ck}"
        }.mkString("\n")
    val lks = if d.links.isEmpty then "" else
      "\n\n**External links**\n" +
        d.links.map { l =>
          val id = PlaceLink.url(l.scheme, l.externalId).fold(s"`${l.externalId}`")(u => s"[${l.externalId}]($u)")
          s"- ${l.scheme}: $id (${l.relation})"
        }.mkString("\n")
    val tps = if d.types.isEmpty then "" else
      "\n\n**Types**\n" +
        d.types.map { t =>
          val per = t.period.map(p => s" · ${periodLabel(Some(p.start), Some(p.end))}").getOrElse("")
          s"- ${t.typeLabel}${per}"
        }.mkString("\n")
    val hier = if tree.isEmpty then "" else s"\n\n## Hierarchy\n\n${placeTree(tree)}"
    header + what + src + shape + dating + names + geom + fps + lks + tps +
      list("Part of", d.parents) + list("Contains", d.children) + hier

  def placeImport(r: PlaceImportResult): String =
    val head = s"imported ${r.places} place(s), ${r.names} name(s), ${r.relations} relation(s)"
    if r.failed.isEmpty then head
    else s"$head\n\nskipped:\n${r.failed.map(f => s"- $f").mkString("\n")}"

  /** `histmersion places sources` (#1064): each external source, whether its file is there, what the
    * pin says and what was imported. */
  def gazetteerSources(rows: List[GazetteerSourceInfo]): String =
    if rows.isEmpty then "_No external gazetteer sources: no pinned extracts and no LPF files._"
    else
      val head = "| source | file | pinned | imported | places | names | links | licence |\n|---|---|---|---|---:|---:|---:|---|"
      val body = rows.map { r =>
        val file = r.file.fold("—")(f => s"`$f`" + (if r.present then "" else " (missing)"))
        val imported = r.importedVersion.fold("—")(v => s"$v (${r.importedAt.getOrElse("").take(10)})")
        val n = (x: Option[Int]) => x.fold("—")(_.toString)
        s"| ${r.key} — ${r.title} | $file | ${r.pinnedVersion.getOrElse("—")} | $imported | ${n(r.places)} | " +
          s"${n(r.names)} | ${n(r.links)} | ${r.licence.getOrElse("—")} |"
      }
      (head :: body).mkString("\n")

  /** A name edit (#987): what changed, on which place. */
  def placeNameChanged(r: PlaceNameChanged): String =
    val lang = if r.lang.isEmpty then "" else s" (${r.lang})"
    val what =
      if !r.added then s"removed the name **${r.name}**$lang from"
      else if !r.changed then s"**${r.name}**$lang was already a name added by hand to"
      else if r.wasImported then s"kept **${r.name}**$lang (a name the seed gives) as one added by hand, on"
      else s"added the name **${r.name}**$lang to"
    s"$what **${r.label}** `${r.placeId}` — names added by hand survive every re-seed"

  /** The names people added (#987). */
  def manualPlaceNames(rows: List[ManualPlaceName]): String =
    if rows.isEmpty then "_No names added by hand._"
    else
      s"${rows.size} name(s) added by hand:\n\n" + rows.map { r =>
        s"- **${r.name}**${if r.lang.isEmpty then "" else s" (${r.lang})"} → ${r.label} `${r.placeId}`"
      }.mkString("\n")

  /** A place a person added (#987). */
  def placeAdded(p: PlaceSummary): String =
    s"added **${p.label}** `${p.id}` _(${kindOf(p.kind, p.periodStart, p.periodEnd)})_" +
      p.source.fold("")(src => s" — source: $src") +
      "\n\nIt is this collection's own place (carried in a bundle, never touched by a re-seed). " +
      "Run `histmersion places rebuild` to match findings to it."

  def placeRebuild(r: PlaceRebuildResult): String =
    s"${r.links} finding→place link(s) over ${r.places} place(s) " +
      s"(${r.byGeometry} by geometry, ${r.byName} by region name); " +
      s"${r.located} finding(s) located, ${r.unlocated} unlocated"

  // ── events (plan §1.2) ────────────────────────────────────────────────────────────────────

  private def timeSpan(ts: TimeSpan): String =
    val parts = List(
      ts.notBefore.map(y => s"not before ${fmtYear(y)}"),
      ts.beginBy.map(y => s"by ${fmtYear(y)}"),
      ts.endFrom.map(y => s"until ${fmtYear(y)}"),
      ts.notAfter.map(y => s"not after ${fmtYear(y)}")
    ).flatten
    if parts.isEmpty then "undated" else parts.mkString(", ")

  private def eventLine(e: EventSummary): String =
    val ts = timeSpan(e.timeSpan)
    val ck = e.citekey.map(c => s" [[${c}]]").getOrElse("")
    s"- **${e.label}** `${e.id}` _(${e.kind} · ${e.historicity} · $ts)_$ck"

  def eventList(result: EventList): String =
    if result.events.isEmpty then "_No matching events._"
    else
      val body = result.events.map(eventLine).mkString("\n")
      s"### ${result.total} event(s)\n\n$body"

  def event(d: EventDetail): String =
    val e = d.event
    val header = s"# ${e.label}\n\n_`${e.id}` · ${e.kind} · ${e.historicity} · ${timeSpan(e.timeSpan)}_"
    val desc = e.description.map(d => s"\n\n$d").getOrElse("")
    val ck = e.citekey.map(c => s"  \n**Primary source:** [[${c}]]").getOrElse("")
    val place = e.placeId.map(p => s"  \n**Place:** `$p`").getOrElse("")
    val geom = d.geometry.map(g => s"  \n**Geometry:** ${g.take(40).takeWhile(_ != ',')}… (GeoJSON)")
      .getOrElse("")
    val parent = d.parent.map(p => s"\n\n**Part of:** ${p.label} `${p.id}`").getOrElse("")
    val children = if d.children.isEmpty then "" else
      "\n\n**Sub-events**\n" + d.children.map(eventLine).mkString("\n")
    val datings = if d.datings.isEmpty then "" else
      "\n\n**Datings**\n" + d.datings.map { dt =>
        val method = dt.method.map(m => s" [$m]").getOrElse("")
        val note = dt.note.map(n => s" — $n").getOrElse("")
        s"- [[${dt.citekey}]]$method: ${timeSpan(dt.timeSpan)}$note"
      }.mkString("\n")
    val edges = if d.edges.isEmpty then "" else
      "\n\n**Event edges**\n" + d.edges.map { eg =>
        val note = eg.note.map(n => s" — $n").getOrElse("")
        s"- `${eg.srcId}` →_${eg.kind}_ `${eg.dstId}` [[${eg.citekey}]]$note"
      }.mkString("\n")
    val parts = if d.participants.isEmpty then "" else
      "\n\n**Participants**\n" + d.participants.map { p =>
        val ck = p.citekey.map(c => s" [[${c}]]").getOrElse("")
        s"- ${p.actor} _(${p.role})_$ck"
      }.mkString("\n")
    header + desc + ck + place + geom + parent + children + datings + edges + parts

  // ── attestations (plan §1.3) ──────────────────────────────────────────────────────────────

  private def attestationLine(a: Attestation): String =
    val loc = List(
      a.page.map(p => s"p.$p"),
      a.chunkOrd.map(o => s"chunk $o"),
      a.charStart.zip(a.charEnd).headOption.map((s, e) => s"chars $s–$e"),
      a.ctsUrn.map(u => s"CTS `$u`"),
      a.canvas.map(c => s"canvas ${a.xywh.fold(c)(r => s"$c#xywh=$r")}")
    ).flatten
    val locStr = if loc.isEmpty then "" else s" (${loc.mkString("; ")})"
    val q = a.quote.map(t => s""" "${ if t.length > 80 then t.take(77) + "…" else t }"""").getOrElse("")
    val n = a.note.map(n => s" — $n").getOrElse("")
    s"- `#${a.id}` [[${a.citekey}]] → ${a.targetType}:`${a.targetId}`$locStr$q$n"

  def attestationList(result: AttestationList): String =
    if result.attestations.isEmpty then "_No matching attestations._"
    else
      val body = result.attestations.map(attestationLine).mkString("\n")
      s"### ${result.total} attestation(s)\n\n$body"

  def attestation(a: Attestation): String =
    val header = s"# Attestation #${a.id}\n\n_[[${a.citekey}]] → ${a.targetType}:`${a.targetId}`_"
    val loc = List(
      a.page.map(p => s"**Page:** $p"),
      a.chunkOrd.map(o => s"**Chunk:** $o"),
      a.charStart.zip(a.charEnd).headOption.map((s, e) => s"**Chars:** $s–$e"),
      a.ctsUrn.map(u => s"**CTS URN:** `$u`"),
      a.canvas.map(c => s"**Canvas:** $c"),
      a.xywh.map(r => s"**Region:** $r")
    ).flatten
    val locBlock = if loc.isEmpty then "" else "\n\n" + loc.mkString("  \n")
    val q = a.quote.map { t =>
      val ctx = List(
        a.prefix.map(p => s"…$p"),
        Some(s"**$t**"),
        a.suffix.map(s => s"$s…")
      ).flatten.mkString("")
      s"\n\n> $ctx"
    }.getOrElse("")
    val n = a.note.map(n => s"\n\n$n").getOrElse("")
    header + locBlock + q + n

  // ── goals (plan §1.5) ────────────────────────────────────────────────────────────────────────

  private def goalLine(g: Goal): String =
    val period = (g.periodStart, g.periodEnd) match
      case (Some(s), Some(e)) => s" ($s–$e)"
      case _ => ""
    val parent = g.parentSlug.map(p => s" ⊂ $p").getOrElse("")
    s"- **${g.title}** `${g.slug}` _(${g.status})_$period$parent"

  def goalList(result: GoalList): String =
    if result.goals.isEmpty then "_No matching goals._"
    else
      val body = result.goals.map(goalLine).mkString("\n")
      s"### ${result.total} goal(s)\n\n$body"

  def goal(g: Goal): String =
    val header = s"# ${g.title}\n\n_`${g.slug}` · ${g.status}_"
    val period = (g.periodStart, g.periodEnd) match
      case (Some(s), Some(e)) => s"  \n**Period:** $s–$e"
      case _ => ""
    val parent = g.parentSlug.map(p => s"  \n**Parent:** `$p`").getOrElse("")
    val tags = if g.tags.isEmpty then "" else s"  \n**Tags:** ${g.tags.mkString(", ")}"
    val regions = if g.regions.isEmpty then "" else s"  \n**Regions:** ${g.regions.mkString(", ")}"
    val notes = g.notes.map(n => s"\n\n$n").getOrElse("")
    val upd = g.updatedAt.map(u => s"  \n_Updated: ${u}_").getOrElse("")
    header + period + parent + tags + regions + notes + upd

  // ── agent state ──────────────────────────────────────────────────────────────────────────────

  def agentStateList(entries: List[AgentStateEntry]): String =
    if entries.isEmpty then "_No agent state entries._"
    else
      val body = entries.map { e =>
        val short = if e.value.length > 80 then e.value.take(77) + "…" else e.value
        s"- `${e.key}` = $short"
      }.mkString("\n")
      s"### ${entries.size} key(s)\n\n$body"

  def agentState(e: AgentStateEntry): String =
    val upd = e.updatedAt.map(u => s"  \n_Updated: ${u}_").getOrElse("")
    s"# `${e.key}`\n\n```json\n${e.value}\n```$upd"

  // ── media associations (plan §1.6) ──────────────────────────────────────────────────────────

  def assetDetail(ck: String, a: AssetDetail): String =
    val loc = (a.lat, a.lon) match
      case (Some(lat), Some(lon)) => s"  \n**Location:** $lat, $lon"
      case _ => ""
    val per = (a.periodStart, a.periodEnd) match
      case (Some(s), Some(e)) => s"  \n**Period:** ${periodLabel(Some(s), Some(e))}"
      case _ => ""
    val meta = List(
      a.kind.map(k => s"**Kind:** $k"),
      a.license.map(l => s"**License:** $l"),
      Some(s"**Size:** ${Plural.count(a.bytes, "byte")}"),
      (a.width, a.height) match { case (Some(w), Some(h)) => Some(s"**Dimensions:** ${w}×${h}") case _ => None }
    ).flatten.mkString("  \n")
    val cap = a.caption.map(c => s"\n\n> $c").getOrElse("")
    s"# `$ck` / `${a.name}`\n\n$meta$loc$per$cap"

  def assetLinkLine(l: AssetLink): String =
    val xywh = l.xywh.map(x => s" xywh=$x").getOrElse("")
    s"- **${l.relation}** ${l.targetType}:${l.targetId} ← ${l.citekey}/${l.assetName}$xywh"

  def assetLinkList(ll: AssetLinkList): String =
    if ll.links.isEmpty then "_No asset links._"
    else
      val body = ll.links.map(assetLinkLine).mkString("\n")
      s"### ${ll.total} link(s)\n\n$body"

  def assetLink(l: AssetLink): String =
    val xywh = l.xywh.map(x => s"  \n**Region:** $x").getOrElse("")
    val notes = l.notes.map(n => s"  \n$n").getOrElse("")
    s"# Link #${l.id}\n\n**Relation:** ${l.relation}  \n**Target:** ${l.targetType}:${l.targetId}  \n**Asset:** ${l.citekey}/${l.assetName}$xywh$notes"

  /** A citekey rename: what moved, and the two things outside the database that are keyed by the
    * citekey and are NOT the server's to change (the host's Calibre originals directory and the
    * exported Markdown mirror). */
  def sourceRenamed(r: SourceRenamed): String =
    val c = r.counts
    def list(xs: List[String]) = if xs.isEmpty then "none" else xs.map(x => s"`$x`").mkString(", ")
    val columns =
      if c.columns.isEmpty then ""
      else "\n" + c.columns.map(n => s"  - ${n.table}: ${n.rows}").mkString("\n")
    val calibre = r.calibreId match
      case Some(id) =>
        s"Calibre: book $id is linked by its id, and Calibre never stores the citekey — nothing to change there."
      case None =>
        s"Calibre: not pushed yet. `histmersion calibre push ${r.newCitekey}` looks for " +
          s"`<cli.calibre.originals-dir>/${r.newCitekey}/paper.*` — rename the `${r.oldCitekey}` " +
          "directory there first, or pass `--file`."
    s"""# Renamed `${r.oldCitekey}` → `${r.newCitekey}`
       |
       |**${r.source.title}**${r.source.year.fold("")(y => s" ($y)")}
       |
       |- findings renumbered: ${c.findings}
       |- articles rewritten (a version recorded for each): ${list(c.articles)}
       |- source notes/summaries rewritten: ${list(c.sources)}
       |- `[[links]]` rewritten: ${c.links}
       |- parked edges promoted: ${c.edgesPromoted}
       |- other columns:${if c.columns.isEmpty then " none" else columns}
       |
       |`${r.oldCitekey}` is now an alias: every read, URL and `[[${r.oldCitekey}]]` still resolves to `${r.newCitekey}`.
       |
       |$calibre
       |
       |Mirror: the next full `histmersion export` removes `sources/${r.oldCitekey}.md`.""".stripMargin
