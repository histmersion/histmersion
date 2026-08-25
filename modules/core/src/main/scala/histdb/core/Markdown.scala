package histdb.core

/** Render query results as Markdown — the wire format served to LLMs (MCP) and printed by the CLI. */
object Markdown:

  private def year(y: Option[Int]): String = y.map(_.toString).getOrElse("?")
  private def fmtYear(v: Long): String     = if v < 0 then s"${-v} BCE" else s"$v CE"
  private def period(start: Option[Long], end: Option[Long]): String = periodLabel(start, end)

  /** `1300 BCE–1100 BCE` (empty when either bound is missing). Public: the SQL layer uses it to
    * phrase the "why" of a match, so the CLI, MCP and the explanations all read the same. */
  def periodLabel(start: Option[Long], end: Option[Long]): String =
    (start, end) match
      case (Some(a), Some(b)) => if a == b then fmtYear(a) else s"${fmtYear(a)}–${fmtYear(b)}"
      case _                  => ""
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
        val meta     = List(year(h.year), h.status, h.regions).filter(_.nonEmpty).mkString(" · ")
        val per      = period(h.periodStart, h.periodEnd)
        val metaLine = if per.nonEmpty then s"$meta · $per" else meta
        val summary  = h.summary.map(s => s"\n  ${s.trim.take(240)}").getOrElse("")
        s"- **${h.citekey}** — ${h.title}\n  _${metaLine}_${snippet(h)}$summary"
      }
      s"### ${hits.size} result(s)\n\n${body.mkString("\n")}"

  /** The filtered search result (plan §1.0): one entry per SOURCE, each followed by a `matches:`
    * sub-list naming the findings that satisfied the filter — claim · place · period · why — so a
    * human or an LLM can judge relevance without opening the source. */
  def searchResults(result: SearchResult): String =
    if result.hits.isEmpty then "_No matching sources._"
    else
      val body = result.hits.map { hit =>
        val h        = hit.source
        val meta     = List(year(h.year), h.status, h.regions).filter(_.nonEmpty).mkString(" · ")
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
      s"$header\n\n${body.mkString("\n")}"

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

  def source(s: SourceDetail, findings: Vector[FindingRow], fullText: Boolean): String =
    val meta    = List(s.authors, year(s.year), s.venue.getOrElse(""), s.status).filter(_.nonEmpty).mkString(" · ")
    val per     = period(s.periodStart, s.periodEnd)
    val header  = s"# ${s.title}\n\n_${meta}${if per.nonEmpty then s" · $per" else ""}_"
    val topics  = if s.topics.nonEmpty then s"\n\n**Topics:** ${s.topics}" else ""
    val regions = if s.regions.nonEmpty then s"  \n**Regions:** ${s.regions}" else ""
    val doi     = s.doi.map(d => s"  \n**DOI:** $d").getOrElse("")
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
          val tail = List(if loc.nonEmpty then s"@ $loc" else "", p, f.confidence.map(c => s"($c)").getOrElse(""))
            .filter(_.nonEmpty)
            .mkString(" · ")
          s"- ${f.claim}${if tail.nonEmpty then s"  \n  _${tail}_" else ""}"
        }
        s"\n\n## Findings (${findings.size})\n${items.mkString("\n")}"
    val body =
      if !fullText then ""
      else s.ocrMarkdown.map(t => s"\n\n---\n\n$t").getOrElse("\n\n_(no full text stored)_")
    header + topics + regions + doi + summary + notes + findingsMd + body

  def neighbors(citekey: String, rows: Vector[NeighborRow]): String =
    if rows.isEmpty then s"_No references recorded for **$citekey**._"
    else
      def block(title: String, dir: String): String =
        val rs = rows.filter(_.direction == dir)
        if rs.isEmpty then ""
        else
          val items = rs.map { r =>
            // real sources (title present) → [[wikilink]] (clickable on the HTML page); else plain
            val target = r.title match
              case Some(t) => s"[[${r.citekey}]] — $t"
              case None    => s"**${r.citekey}**"
            s"- _${r.kind}_ → $target"
          }
          s"\n\n**$title**\n${items.mkString("\n")}"
      s"### References · $citekey" +
        block("Outgoing (this → other)", "out") +
        block("Incoming (other → this)", "in")

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
    * Counts overlap buckets, so they deliberately do not sum to a total. */
  def histogram(h: TimeHistogram, unit: String): String =
    val span = s"${fmtYear(h.from)} → ${fmtYear(h.to)}"
    if h.buckets.isEmpty then s"_No time buckets for $span._"
    else
      val max = h.buckets.map(_.count).max
      val bar = (n: Int) => if n == 0 then "" else "█" * math.max(1, (n.toDouble / max * 24).round.toInt)
      val rows = h.buckets.map { b =>
        s"| ${fmtYear(b.start)}–${fmtYear(b.end)} | ${b.count} | ${bar(b.count)} |"
      }
      val head = s"### $unit per ${h.bucket} yrs · $span"
      val one  = if unit.endsWith("s") then unit.dropRight(1) else unit
      val note = s"_${h.buckets.count(_.count > 0)} non-empty bucket(s); each $one is counted in " +
        "every bucket its period overlaps, so the bars do not sum to a total._"
      // NB: no stripMargin here — every table row starts with '|', which stripMargin would eat.
      (List(head, "", s"| period | $unit | |", "|---|---:|---|") ++ rows ++ List("", note)).mkString("\n")

  // ── the gazetteer (plan §1.6a) ───────────────────────────────────────────────────────────────

  /** A hand-traced shape whose extent is disputed is marked everywhere its label is drawn — unless
    * the label already says so (some traced polygons carry "(contested)" in the label itself, and
    * "Israel (united monarchy, as narrated) (contested) (contested)" helps nobody). */
  def contestedMark(contested: Boolean, label: String = ""): String =
    if contested && !label.toLowerCase.contains("contested") then " (contested)" else ""

  private def kindOf(kind: String, ps: Option[Long], pe: Option[Long]): String =
    val p = period(ps, pe)
    if p.isEmpty then kind else s"$kind · $p"

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
        val meta = s"${kindOf(h.kind, h.periodStart, h.periodEnd)} · ${h.findings} finding(s)$src"
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
      s"\n\n_`${p.id}` · ${kindOf(p.kind, p.periodStart, p.periodEnd)}" +
      s" · ${d.findings} finding(s)${asOfNote(d.asOf)}_"
    val src     = p.source.map(s => s"  \n**Source:** $s").getOrElse("")
    val names   = if p.names.isEmpty then "" else s"  \n**Also known as:** ${p.names}"
    val geom    = d.geometry.map(g => s"  \n**Geometry:** ${g.take(40).takeWhile(_ != ',')}… (GeoJSON)")
      .getOrElse("  \n**Geometry:** _none_")
    def list(title: String, xs: List[PlaceSummary]) =
      if xs.isEmpty then ""
      else s"\n\n**$title**\n" +
        xs.map(x => s"- ${x.label}${contestedMark(x.contested, x.label)} `${x.id}` _(${x.kind})_")
          .mkString("\n")
    val hier = if tree.isEmpty then "" else s"\n\n## Hierarchy\n\n${placeTree(tree)}"
    header + src + names + geom + list("Part of", d.parents) + list("Contains", d.children) + hier

  def placeImport(r: PlaceImportResult): String =
    val head = s"imported ${r.places} place(s), ${r.names} name(s), ${r.relations} relation(s)"
    if r.failed.isEmpty then head
    else s"$head\n\nskipped:\n${r.failed.map(f => s"- $f").mkString("\n")}"

  def placeRebuild(r: PlaceRebuildResult): String =
    s"${r.links} finding→place link(s) over ${r.places} place(s) " +
      s"(${r.byGeometry} by geometry, ${r.byName} by region name); " +
      s"${r.located} finding(s) located, ${r.unlocated} unlocated"
