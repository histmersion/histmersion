package com.histmersion.api.research
import com.histmersion.api.{Plural, StatusCount}
import com.histmersion.api.catalogue.ArticleEventLead
import com.histmersion.api.given
import com.histmersion.domain.library.Source

object ArticleCoverage:

  val DisagreementKinds: List[String] = List("challenges", "refutes", "updates", "superseded_by")

  private val Stated: Set[Source.Status] = Set(
    Source.Status.Contested,
    Source.Status.Superseded,
    Source.Status.Debunked,
    Source.Status.Retracted,
    Source.Status.Unassessed
  )

  private val Disputed: List[Source.Status] =
    List(Source.Status.Contested, Source.Status.Superseded, Source.Status.Debunked, Source.Status.Retracted)

  def compute(
      slug: String,
      filter: ArticleFilter,
      total: Int,
      hits: List[CoverageSource],
      citedInFilter: Set[String],
      cited: List[String],
      reasons: Map[String, String],
      edges: List[CoverageEdge],
      searches: List[CoverageSearch]
  ): Coverage =
    val citedSet = cited.toSet
    val read = hits.distinctBy(_.citekey).sortBy(_.citekey)
    val readSet = read.map(_.citekey).toSet
    val notUsed = read.filterNot(s => citedSet(s.citekey)).map(s => CoverageEntry(s, reasons.get(s.citekey)))
    val capped = read.size < total
    val outside = cited.distinct.filterNot(ck => citedInFilter(ck) || readSet(ck))
    val statuses =
      Source.Status.values.toList.flatMap(st =>
        Option(read.count(_.status == st)).filter(_ > 0).map(StatusCount(st, _))
      )
    val keptEdges = edges
      .filter(e => DisagreementKinds.contains(e.kind) && (citedSet(e.from) || citedSet(e.to)))
      .distinct
      .sortBy(e => (e.from, e.kind, e.to))
    val drift = List(
      CoverageDrift(DriftKind.Unplaced, notUsed.filter(_.reason.isEmpty).map(_.source.citekey)),
      CoverageDrift(DriftKind.ReasonForCited, reasons.keys.toList.filter(citedSet).sorted),
      CoverageDrift(
        DriftKind.ReasonOutside,
        if capped then Nil else reasons.keys.toList.filterNot(ck => readSet(ck) || citedSet(ck)).sorted
      )
    ).filter(_.citekeys.nonEmpty)
    Coverage(
      slug,
      filter,
      filter.line,
      total,
      read.size,
      read.filter(s => citedSet(s.citekey)),
      notUsed,
      outside,
      statuses,
      keptEdges,
      searches,
      CoverageReason.list(reasons),
      drift
    )

  def unplacedAfter(c: Coverage, cited: Set[String], reasons: Map[String, String]): List[String] =
    c.notUsed.map(_.source.citekey).filterNot(ck => cited(ck) || reasons.contains(ck))

  /** HTML comments, so Obsidian and the page do not show them. */
  val GeneratedStart = "<!-- generated from the article's filter: edits here are not kept -->"
  val GeneratedEnd = "<!-- end of generated sections -->"

  val NotUsedHeading = "Not used"

  def sections(c: Coverage): String = s"${notUsed(c)}\n\n${method(c)}"

  def exported(c: Coverage): String = s"$GeneratedStart\n\n${sections(c)}\n\n$GeneratedEnd"

  def notUsed(c: Coverage): String =
    val src = Plural.noun(c.total, "source")
    val citedN = c.cited.size
    val n = c.notUsed.size
    val scope = if c.capped then s"; the first ${c.checked} (by citekey) were checked" else ""
    val lead =
      if c.total == 0 then "_The filter returns no source._"
      else if n == 0 then
        if c.capped then s"_The filter returns ${c.total} $src; all ${c.checked} checked are cited above._"
        else
          s"_${if c.total == 1 then "The one source" else s"All ${c.total} sources"} the filter returns ${
              if c.total == 1 then "is" else "are"
            } cited above._"
      else
        val citedText = if citedN == 0 then "None of them is" else Plural.count(citedN, "of them is", "of them are")
        s"The filter returns ${c.total} $src$scope. $citedText cited above; " +
          s"${if n == 1 then "the other one is" else s"the other $n are"} listed here with the reason ${
              if n == 1 then "it is" else "each is"
            } left out:"
    val groups = c.notUsed.groupBy(_.reason).toList.sortBy((r, es) => (r.isEmpty, es.map(_.source.citekey).min))
    val lines = groups.map { (reason, es) =>
      val who =
        es.sortBy(_.source.citekey).map(e => s"[[${e.source.citekey}]]${statusNote(e.source.status)}").mkString(", ")
      s"- $who — ${reason.fold("not yet assessed")(endSentence)}"
    }
    s"## $NotUsedHeading\n\n$lead" + (if lines.isEmpty then "" else "\n\n" + lines.mkString("\n"))

  private def statusNote(s: Source.Status): String = if Stated(s) then s" (${s.wire})" else ""

  private def endSentence(s: String): String =
    if s.endsWith(".") || s.endsWith("?") || s.endsWith("!") then s else s"$s."

  def method(c: Coverage): String =
    val statuses =
      if c.statuses.isEmpty then Nil
      else
        val have = c.statuses.map(s => s"${s.count} ${s.status.wire}").mkString(", ")
        val none = Disputed.filterNot(d => c.statuses.exists(_.status == d)).map(_.wire)
        val noneText = if none.isEmpty then "" else s"; none ${orList(none)}"
        List(s"statuses${if c.capped then s" of the ${c.checked} checked" else ""}: $have$noneText")
    val searches = c.searches.map { s =>
      val cites =
        if s.cited.isEmpty then "none of them cited" else s"cited: ${s.cited.map(ck => s"[[$ck]]").mkString(", ")}"
      s"further search ${escape(ArticleFilter.quoted(s.query))} → ${s.total} ($cites)"
    }
    val outside = Option
      .when(c.citedOutside.nonEmpty)(
        s"cited from outside the filter: ${c.citedOutside.map(ck => s"[[$ck]]").mkString(", ")}"
      )
      .toList
    val edges =
      if c.edges.isEmpty then
        "no challenges, refutes, updates or superseded_by edge touches a cited source (not evidence of agreement)"
      else
        s"disagreement edges touching the cited sources: ${c.edges.map(e => s"[[${e.from}]] ${e.kind} [[${e.to}]]").mkString("; ")}"
    val parts = s"${escape(c.filterLine)} → ${c.total}" :: statuses ++ searches ++ outside ++ List(edges) ++
      Critiques.methodPart(c.critiques).toList
    s"_Method: ${parts.mkString("; ")}._"

  private def orList(xs: List[String]): String = xs match
    case Nil        => ""
    case one :: Nil => one
    case _          => s"${xs.init.mkString(", ")} or ${xs.last}"

  /** An `_` or `*` of a user's query would end the Method line's Markdown emphasis. */
  private def escape(s: String): String = s.replace("*", "\\*").replace("_", "\\_")

  private def isHeading(l: String) = l.trim.startsWith("#")
  private def level(l: String) = l.trim.takeWhile(_ == '#').length

  def withoutNotUsed(text: String): String =
    val lines = text.split("\n", -1).toList
    lines.indexWhere(l =>
      isHeading(l) && l.trim.replaceAll("^#+\\s*", "").trim.toLowerCase.startsWith("not used")
    ) match
      case -1 => text
      case i  =>
        val lv = level(lines(i))
        val rest = lines.drop(i + 1)
        val end = rest.indexWhere(l => isHeading(l) && level(l) <= lv)
        (lines.take(i) ++ (if end < 0 then Nil else rest.drop(end))).mkString("\n")

  def hasMethodLine(text: String): Boolean = text.linesIterator.exists(_.trim.startsWith("_Method"))

  def stripGenerated(body: String): String =
    val s = body.indexOf(GeneratedStart)
    if s < 0 then body
    else
      val e = body.indexOf(GeneratedEnd, s)
      val tail = if e < 0 then "" else body.substring(e + GeneratedEnd.length)
      (body.substring(0, s).stripTrailing + (if tail.trim.isEmpty then ""
                                             else "\n\n" + tail.dropWhile(_.isWhitespace))).trim

  def markdown(c: Coverage): String =
    val drift =
      if c.drift.isEmpty then "No drift: every source the filter returns is cited or has a reason."
      else
        c.drift
          .map { d =>
            val what = d.kind match
              case DriftKind.Unplaced       => "neither cited nor given a reason"
              case DriftKind.ReasonForCited => "given a reason but cited in the body (the reason is stale)"
              case DriftKind.ReasonOutside  => "given a reason but no longer returned by the filter"
            s"- ${Plural.count(d.citekeys.size, "source")} $what: ${d.citekeys.map(ck => s"[[$ck]]").mkString(", ")}"
          }
          .mkString("\n")
    val critiques = Critiques.markdown(c.critiques)
    s"## Coverage of [[${c.slug}]]\n\n`${c.filterLine}` → ${c.total} (${c.cited.size} cited, ${c.notUsed.size} not used" +
      s"${if c.capped then s", ${c.total - c.checked} not checked" else ""})\n\n### Drift\n\n$drift" +
      (if critiques.isEmpty then "" else s"\n\n$critiques") + s"\n\n### Generated sections\n\n${sections(c)}" +
      (if c.eventLeads.isEmpty then "" else "\n\n" + ArticleEventLead.markdown(c.eventLeads))
