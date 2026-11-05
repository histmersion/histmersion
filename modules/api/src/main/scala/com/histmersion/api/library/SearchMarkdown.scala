package com.histmersion.api.library
import com.histmersion.api.OffsetPage
import com.histmersion.api.Markdown.*

object SearchMarkdown:

  /** `ts_headline` marks hits with `**` already. */
  private[api] def snippet(h: SearchHit): String =
    h.snippet.map(_.replaceAll("\\s+", " ").trim).filter(_.nonEmpty) match
      case None    => ""
      case Some(s) =>
        val head = h.matchedHeading.map(_.trim).filter(_.nonEmpty).map(x => s"_${x}_ — ").getOrElse("")
        s"\n  > $head$s"

  def searchResults(hits: Vector[SearchHit]): String =
    if hits.isEmpty then "_No matching sources._"
    else
      val body = hits.map { h =>
        val reg = h.regions.map(Label.display).mkString(", ")
        val meta = List(year(h.year.map(_.value.toInt)), h.status.wire, reg).filter(_.nonEmpty).mkString(" · ")
        val per = period(h.periodStart, h.periodEnd)
        val metaLine = if per.nonEmpty then s"$meta · $per" else meta
        val summary = h.summary.map(s => s"\n  ${s.trim.take(240)}").getOrElse("")
        s"- **${h.citekey}** — ${h.title}\n  _${metaLine}_${snippet(h)}$summary"
      }
      s"### ${hits.size} result(s)\n\n${body.mkString("\n")}"

  def searchResults(result: SearchResult): String =
    if result.hits.isEmpty then "_No matching sources._"
    else
      val body = result.hits.map { hit =>
        val h = hit.source
        val reg = h.regions.map(Label.display).mkString(", ")
        val meta = List(year(h.year.map(_.value.toInt)), h.status.wire, reg).filter(_.nonEmpty).mkString(" · ")
        val per = period(h.periodStart, h.periodEnd)
        val metaLine = if per.nonEmpty then s"$meta · $per" else meta
        val why = if hit.why.isEmpty then "" else s"\n  why: ${hit.why.mkString(" · ")}"
        val summary = h.summary.map(s => s"\n  ${s.trim.take(240)}").getOrElse("")
        val matched =
          if hit.matched.isEmpty then ""
          else
            val items = hit.matched.map { m =>
              val loc = place(m.regionName, m.lat, m.lon)
              val p = period(m.periodStart, m.periodEnd)
              val tail = List(loc, p, m.why.mkString(", ")).filter(_.nonEmpty).mkString(" · ")
              s"\n    - ${m.claim.trim}${if tail.nonEmpty then s"  \n      _${tail}_" else ""}"
            }
            s"\n  matches:${items.mkString}"
        s"- **${h.citekey}** — ${h.title}\n  _${metaLine}_$why${snippet(h)}$summary$matched"
      }
      val header = OffsetPage.header(result.page, "source(s)")
      val footer = OffsetPage.footer(result.page)
      s"$header\n\n${body.mkString("\n")}$footer"

  def briefResults(result: SearchResult): String =
    if result.hits.isEmpty then "_No matching sources._"
    else
      val body = result.hits.map { hit =>
        val h = hit.source
        val per = period(h.periodStart, h.periodEnd)
        val meta = List(year(h.year.map(_.value.toInt)), h.status.wire, per).filter(_.nonEmpty).mkString(" · ")
        val why = hit.why.headOption.map(w => s" — $w").getOrElse("")
        s"- **${h.citekey}** — ${h.title}  \n  _${meta}_$why"
      }
      val header = OffsetPage.header(result.page, "source(s)")
      val footer = OffsetPage.footer(result.page)
      s"$header\n\n${body.mkString("\n")}$footer"

  def facets(field: String, rows: Vector[Facet]): String =
    if rows.isEmpty then s"_No $field in the current filter._"
    else
      val items = rows.map(f =>
        s"- ${f.value}${contestedMark(f.contested, f.value)} (${f.count})" +
          f.id.map(i => s" · `$i`").getOrElse("")
      )
      s"### $field (${rows.size})\n\n${items.mkString("\n")}"

  def geoPage(p: GeoHitPage): String =
    if p.hits.isEmpty then
      OffsetPage.markdown(p.page, "finding(s)", "_No findings match that place/time._", Nil)(_ => "")
    else
      val body = geoResults(p.hits.toVector)
      OffsetPage.header(p.page, "finding(s)") + body.dropWhile(_ != '\n') + OffsetPage.footer(p.page)

  def geoResults(hits: Vector[GeoHit]): String =
    if hits.isEmpty then "_No findings match that place/time._"
    else
      val blocks = hits
        .groupBy(h => (h.citekey, h.title))
        .toList
        .sortBy(_._1._1)
        .map { case ((ck, title), hs) =>
          val items = hs
            .map { h =>
              val loc = place(h.regionName, h.lat, h.lon)
              val p = period(h.periodStart, h.periodEnd)
              s"  - ${h.claim} — _${List(loc, p).filter(_.nonEmpty).mkString(", ")}_"
            }
            .mkString("\n")
          s"- **$ck** — $title\n$items"
        }
      s"### ${hits.size} finding(s)\n\n${blocks.mkString("\n")}"

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
      val one = if unit.endsWith("s") then unit.dropRight(1) else unit
      val note =
        if starts then
          s"_${h.buckets.count(_.count > 0)} non-empty bucket(s); each source is counted " +
            "ONCE, in the bucket its period starts in, so the bars do sum to the total._"
        else
          s"_${h.buckets.count(_.count > 0)} non-empty bucket(s); each $one is counted in " +
            "every bucket its period overlaps, so the bars do not sum to a total._"
      // NB: no stripMargin here — every table row starts with '|', which stripMargin would eat.
      val col = if starts then "sources" else unit
      (List(head, "", s"| period | $col | |", "|---|---:|---|") ++ rows ++ List("", note)).mkString("\n")

  final case class CollapsedBucket(start: Long, end: Long, count: Int, merged: Int)

  def collapseHistogram(buckets: List[TimeBucket]): List[CollapsedBucket] =
    if buckets.sizeIs <= 12 then buckets.map(b => CollapsedBucket(b.start, b.end, b.count, 1))
    else
      val runs = buckets
        .foldLeft(List.empty[CollapsedBucket]) { (acc, b) =>
          acc match
            case head :: tail if head.count == b.count =>
              head.copy(end = b.end, merged = head.merged + 1) :: tail
            case _ =>
              CollapsedBucket(b.start, b.end, b.count, 1) :: acc
        }
        .reverse
      runs

  def tree(h: TimeHistogram, unit: String): String =
    val span = s"${fmtYear(h.from)} → ${fmtYear(h.to)}"
    if h.tree.isEmpty then s"_No $unit to group for $span._"
    else
      val one = if unit.endsWith("s") then unit.dropRight(1) else unit
      val head = s"### $unit by ${h.by} · $span" + asOfNote(h.asOf)
      val note = s"_Counts are non-additive: each $one is counted in every bucket it overlaps, so " +
        "children never sum to their parent — \"N in more than one …\" is how many are double-counted._"
      (List(head, "", treeLines(h.tree, unit, 0).mkString("\n"), "", note)).mkString("\n")

  private[api] def treeLines(buckets: List[Bucket], unit: String, depth: Int): List[String] =
    val pad = "  " * depth
    buckets.flatMap { b =>
      val over = if b.overlap > 0 then s" · ${b.overlap} in more than one ${childNoun(b)}" else ""
      val when = if b.kind == Tree.KindTime then ""
      else Option(periodLabel(b.start, b.end)).filter(_.nonEmpty).map(p => s" · $p").getOrElse("")
      val id = b.placeId.map(i => s" `$i`").getOrElse("")
      val line = s"$pad- **${b.label}**${contestedMark(b.contested, b.label)}$id — " +
        s"${b.count} $unit$over$when"
      val kids = treeLines(b.children, unit, depth + 1)
      val samps = b.samples.map(x => s"$pad  - ${x.citekey} — ${x.title}")
      line :: (kids ++ samps)
    }

  private[api] def childNoun(b: Bucket): String =
    if b.children.exists(_.kind == Tree.KindTime) then "period" else "subregion"
