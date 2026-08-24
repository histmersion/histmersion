package histdb.core

/** Render query results as Markdown — the wire format served to LLMs (MCP) and printed by the CLI. */
object Markdown:

  private def year(y: Option[Int]): String = y.map(_.toString).getOrElse("?")

  private def fmtYear(v: Long): String = if v < 0 then s"${-v} BCE" else s"$v CE"

  private def period(start: Option[Long], end: Option[Long]): String =
    (start, end) match
      case (Some(a), Some(b)) => if a == b then fmtYear(a) else s"${fmtYear(a)}–${fmtYear(b)}"
      case _                  => ""

  def searchResults(hits: Vector[SearchHit]): String =
    if hits.isEmpty then "_No matching sources._"
    else
      val body = hits.map { h =>
        val meta = List(year(h.year), h.status, h.regions).filter(_.nonEmpty).mkString(" · ")
        val per  = period(h.periodStart, h.periodEnd)
        val metaLine = if per.nonEmpty then s"$meta · $per" else meta
        val summary  = h.summary.map(s => s"\n  ${s.trim.take(240)}").getOrElse("")
        s"- **${h.citekey}** — ${h.title}\n  _${metaLine}_$summary"
      }
      s"### ${hits.size} result(s)\n\n${body.mkString("\n")}"
