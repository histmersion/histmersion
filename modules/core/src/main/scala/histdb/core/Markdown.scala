package histdb.core

/** Render query results as Markdown — the wire format served to LLMs (MCP) and printed by the CLI. */
object Markdown:

  private def year(y: Option[Int]): String = y.map(_.toString).getOrElse("?")
  private def fmtYear(v: Long): String     = if v < 0 then s"${-v} BCE" else s"$v CE"
  private def period(start: Option[Long], end: Option[Long]): String =
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

  def source(s: SourceDetail, findings: Vector[FindingRow], fullText: Boolean): String =
    val meta    = List(s.authors, year(s.year), s.venue.getOrElse(""), s.status).filter(_.nonEmpty).mkString(" · ")
    val per     = period(s.periodStart, s.periodEnd)
    val header  = s"# ${s.title}\n\n_${meta}${if per.nonEmpty then s" · $per" else ""}_"
    val topics  = if s.topics.nonEmpty then s"\n\n**Topics:** ${s.topics}" else ""
    val regions = if s.regions.nonEmpty then s"  \n**Regions:** ${s.regions}" else ""
    val doi     = s.doi.map(d => s"  \n**DOI:** $d").getOrElse("")
    val summary = s.summary.map(x => s"\n\n${x.trim}").getOrElse("")
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
    header + topics + regions + doi + summary + findingsMd + body

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
