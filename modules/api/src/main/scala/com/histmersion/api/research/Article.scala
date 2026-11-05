package com.histmersion.api.research

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.api.WireEnumValues
import com.histmersion.api.OffsetPage
import com.histmersion.api.Plural
import com.histmersion.api.StrictJson
import com.histmersion.api.WireEnum
import com.histmersion.api.catalogue.ArticleEventLead
import com.histmersion.api.checks.FormatIssue
import com.histmersion.api.checks.Lint.*
import com.histmersion.api.library.Filter
import com.histmersion.api.library.Label
import com.histmersion.api.{ListPatch, StrictJson}
import com.histmersion.api.{Plural, StatusCount}
import com.histmersion.api.{Plural, WireEnum}
import com.histmersion.api.library.Source
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import io.circe.{Decoder, Encoder}
import scala.annotation.tailrec
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
final case class Article(
    slug: Article.Slug,
    title: Article.Title,
    kind: Article.KindValue,
    body: Article.Body,
    summary: Option[Article.SummaryText] = None,
    tags: scala.List[Article.Tags] = Nil,
    regions: scala.List[Article.Regions] = Nil,
    period: Option[Period] = None,
    updatedAt: Option[Article.UpdatedAt] = None,
    filter: Option[Article.Filter] = None,
    reasons: Map[com.histmersion.api.library.Citekey, Article.ReasonsValue] = Map.empty
)

object Article:

  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindValue = KindValue.T
  object KindValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryText = SummaryText.T
  object SummaryText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regions = Regions.T
  object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReasonsValue = ReasonsValue.T
  object ReasonsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Coverage:

    type DisagreementKinds = DisagreementKinds.T
    object DisagreementKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ComputeSlug = ComputeSlug.T
    object ComputeSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ComputeTotal = ComputeTotal.T
    object ComputeTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ComputeCitedInFilter = ComputeCitedInFilter.T
    object ComputeCitedInFilter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ComputeCited = ComputeCited.T
    object ComputeCited extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ComputeReasonsPart1 = ComputeReasonsPart1.T
    object ComputeReasonsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ComputeReasonsPart2 = ComputeReasonsPart2.T
    object ComputeReasonsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnplacedAfterResult = UnplacedAfterResult.T
    object UnplacedAfterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnplacedAfterCited = UnplacedAfterCited.T
    object UnplacedAfterCited extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnplacedAfterReasonsPart1 = UnplacedAfterReasonsPart1.T
    object UnplacedAfterReasonsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnplacedAfterReasonsPart2 = UnplacedAfterReasonsPart2.T
    object UnplacedAfterReasonsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SectionsResult = SectionsResult.T
    object SectionsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExportedResult = ExportedResult.T
    object ExportedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotUsedResult = NotUsedResult.T
    object NotUsedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StatusNoteResult = StatusNoteResult.T
    object StatusNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EndSentenceResult = EndSentenceResult.T
    object EndSentenceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EndSentenceS = EndSentenceS.T
    object EndSentenceS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MethodResult = MethodResult.T
    object MethodResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OrListResult = OrListResult.T
    object OrListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OrListXs = OrListXs.T
    object OrListXs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EscapeResult = EscapeResult.T
    object EscapeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EscapeS = EscapeS.T
    object EscapeS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsHeadingL = IsHeadingL.T
    object IsHeadingL extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LevelL = LevelL.T
    object LevelL extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WithoutNotUsedResult = WithoutNotUsedResult.T
    object WithoutNotUsedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WithoutNotUsedText = WithoutNotUsedText.T
    object WithoutNotUsedText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HasMethodLineResult = HasMethodLineResult.T
    object HasMethodLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HasMethodLineText = HasMethodLineText.T
    object HasMethodLineText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StripGeneratedResult = StripGeneratedResult.T
    object StripGeneratedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StripGeneratedBody = StripGeneratedBody.T
    object StripGeneratedBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkdownResult = MarkdownResult.T
    object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val DisagreementKinds: scala.List[com.histmersion.api.research.Article.Coverage.DisagreementKinds] = scala.List("challenges", "refutes", "updates", "superseded_by")

    private val Stated: Set[Source.Status] = Set(
      Source.Status.Contested,
      Source.Status.Superseded,
      Source.Status.Debunked,
      Source.Status.Retracted,
      Source.Status.Unassessed
    )

    private val Disputed: scala.List[Source.Status] =
      scala.List(Source.Status.Contested, Source.Status.Superseded, Source.Status.Debunked, Source.Status.Retracted)

    def compute(
        slug: com.histmersion.api.research.Article.Coverage.ComputeSlug,
        filter: Filter,
        total: com.histmersion.api.research.Article.Coverage.ComputeTotal,
        hits: scala.List[com.histmersion.api.research.Coverage.Source],
        citedInFilter: Set[com.histmersion.api.research.Article.Coverage.ComputeCitedInFilter],
        cited: scala.List[com.histmersion.api.research.Article.Coverage.ComputeCited],
        reasons: Map[com.histmersion.api.research.Article.Coverage.ComputeReasonsPart1, com.histmersion.api.research.Article.Coverage.ComputeReasonsPart2],
        edges: scala.List[com.histmersion.api.research.Coverage.Edge],
        searches: scala.List[CoverageSearch]
    ): com.histmersion.api.research.Coverage =
      val citedSet = cited.toSet
      val read = hits.distinctBy(_.citekey).sortBy(_.citekey)
      val readSet = read.map(_.citekey).toSet
      val notUsed = read
        .filterNot(s => citedSet(s.citekey))
        .map(s => com.histmersion.api.research.Coverage.Entry(s, reasons.get(s.citekey)))
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
      val drift = scala
        .List(
          com.histmersion.api.research.Coverage
            .Drift(DriftKind.Unplaced, notUsed.filter(_.reason.isEmpty).map(_.source.citekey)),
          com.histmersion.api.research.Coverage
            .Drift(DriftKind.ReasonForCited, reasons.keys.toList.filter(citedSet).sorted),
          com.histmersion.api.research.Coverage.Drift(
            DriftKind.ReasonOutside,
            if capped then Nil else reasons.keys.toList.filterNot(ck => readSet(ck) || citedSet(ck)).sorted
          )
        )
        .filter(_.citekeys.nonEmpty)
      com.histmersion.api.research.Coverage(
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
        com.histmersion.api.research.Coverage.Reason.list(reasons),
        drift
      )

    def unplacedAfter(
        c: com.histmersion.api.research.Coverage,
        cited: Set[com.histmersion.api.research.Article.Coverage.UnplacedAfterCited],
        reasons: Map[com.histmersion.api.research.Article.Coverage.UnplacedAfterReasonsPart1, com.histmersion.api.research.Article.Coverage.UnplacedAfterReasonsPart2]
    ): scala.List[com.histmersion.api.research.Article.Coverage.UnplacedAfterResult] =
      c.notUsed.map(_.source.citekey).filterNot(ck => cited(ck) || reasons.contains(ck))

    /** HTML comments, so Obsidian and the page do not show them. */
    val GeneratedStart = "<!-- generated from the article's filter: edits here are not kept -->"
    val GeneratedEnd = "<!-- end of generated sections -->"

    val NotUsedHeading = "Not used"

    def sections(c: com.histmersion.api.research.Coverage): com.histmersion.api.research.Article.Coverage.SectionsResult = s"${notUsed(c)}\n\n${method(c)}"

    def exported(c: com.histmersion.api.research.Coverage): com.histmersion.api.research.Article.Coverage.ExportedResult =
      s"$GeneratedStart\n\n${sections(c)}\n\n$GeneratedEnd"

    def notUsed(c: com.histmersion.api.research.Coverage): com.histmersion.api.research.Article.Coverage.NotUsedResult =
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

    private def statusNote(s: Source.Status): com.histmersion.api.research.Article.Coverage.StatusNoteResult = if Stated(s) then s" (${s.wire})" else ""

    private def endSentence(s: com.histmersion.api.research.Article.Coverage.EndSentenceS): com.histmersion.api.research.Article.Coverage.EndSentenceResult =
      if s.endsWith(".") || s.endsWith("?") || s.endsWith("!") then s else s"$s."

    def method(c: com.histmersion.api.research.Coverage): com.histmersion.api.research.Article.Coverage.MethodResult =
      val statuses =
        if c.statuses.isEmpty then Nil
        else
          val have = c.statuses.map(s => s"${s.count} ${s.status.wire}").mkString(", ")
          val none = Disputed.filterNot(d => c.statuses.exists(_.status == d)).map(_.wire)
          val noneText = if none.isEmpty then "" else s"; none ${orList(none)}"
          scala.List(s"statuses${if c.capped then s" of the ${c.checked} checked" else ""}: $have$noneText")
      val searches = c.searches.map { s =>
        val cites =
          if s.cited.isEmpty then "none of them cited" else s"cited: ${s.cited.map(ck => s"[[$ck]]").mkString(", ")}"
        s"further search ${escape(Filter.quoted(s.query))} → ${s.total} ($cites)"
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
      val parts = s"${escape(c.filterLine)} → ${c.total}" :: statuses ++ searches ++ outside ++ scala.List(edges) ++
        Critiques.methodPart(c.critiques).toList
      s"_Method: ${parts.mkString("; ")}._"

    private def orList(xs: scala.List[com.histmersion.api.research.Article.Coverage.OrListXs]): com.histmersion.api.research.Article.Coverage.OrListResult = xs match
      case Nil        => ""
      case one :: Nil => one
      case _          => s"${xs.init.mkString(", ")} or ${xs.last}"

    /** An `_` or `*` of a user's query would end the Method line's Markdown emphasis. */
    private def escape(s: com.histmersion.api.research.Article.Coverage.EscapeS): com.histmersion.api.research.Article.Coverage.EscapeResult = s.replace("*", "\\*").replace("_", "\\_")

    private def isHeading(l: com.histmersion.api.research.Article.Coverage.IsHeadingL) = l.trim.startsWith("#")
    private def level(l: com.histmersion.api.research.Article.Coverage.LevelL) = l.trim.takeWhile(_ == '#').length

    def withoutNotUsed(text: com.histmersion.api.research.Article.Coverage.WithoutNotUsedText): com.histmersion.api.research.Article.Coverage.WithoutNotUsedResult =
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

    def hasMethodLine(text: com.histmersion.api.research.Article.Coverage.HasMethodLineText): com.histmersion.api.research.Article.Coverage.HasMethodLineResult = text.linesIterator.exists(_.trim.startsWith("_Method"))

    def stripGenerated(body: com.histmersion.api.research.Article.Coverage.StripGeneratedBody): com.histmersion.api.research.Article.Coverage.StripGeneratedResult =
      val s = body.indexOf(GeneratedStart)
      if s < 0 then body
      else
        val e = body.indexOf(GeneratedEnd, s)
        val tail = if e < 0 then "" else body.substring(e + GeneratedEnd.length)
        (body.substring(0, s).stripTrailing + (if tail.trim.isEmpty then ""
                                               else "\n\n" + tail.dropWhile(_.isWhitespace))).trim

    def markdown(c: com.histmersion.api.research.Coverage): com.histmersion.api.research.Article.Coverage.MarkdownResult =
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

  final case class Deleted(slug: com.histmersion.api.research.Article.Slug, deleted: Deleted.Value) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Deleted:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  object Edits:

    type SqueezeResultPart2 = SqueezeResultPart2.T
    object SqueezeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseResultPart1 = ParseResultPart1.T
    object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeadResult = HeadResult.T
    object HeadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeadAnchor = HeadAnchor.T
    object HeadAnchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeadMax = HeadMax.T
    object HeadMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SqueezeResultPart1 = SqueezeResultPart1.T
    object SqueezeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SqueezeS = SqueezeS.T
    object SqueezeS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OccurrencesResult = OccurrencesResult.T
    object OccurrencesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OccurrencesText = OccurrencesText.T
    object OccurrencesText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OccurrencesNeedle = OccurrencesNeedle.T
    object OccurrencesNeedle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocateResultPart1 = LocateResultPart1.T
    object LocateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocateResultPart2Part1 = LocateResultPart2Part1.T
    object LocateResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocateResultPart2Part2 = LocateResultPart2Part2.T
    object LocateResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocateText = LocateText.T
    object LocateText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocateAnchor = LocateAnchor.T
    object LocateAnchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CutResult = CutResult.T
    object CutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CutText = CutText.T
    object CutText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CutS = CutS.T
    object CutS extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CutE = CutE.T
    object CutE extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApplyOneResultPart1 = ApplyOneResultPart1.T
    object ApplyOneResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApplyOneResultPart2 = ApplyOneResultPart2.T
    object ApplyOneResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApplyOneText = ApplyOneText.T
    object ApplyOneText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApplyAllBody = ApplyAllBody.T
    object ApplyAllBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApplyAllRefuseResult = ApplyAllRefuseResult.T
    object ApplyAllRefuseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkdownResult = MarkdownResult.T
    object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    enum Op(val wire: Op.Wire):
      case Replace extends Op("replace")

      case InsertAfter extends Op("insertAfter")

      case InsertBefore extends Op("insertBefore")

      case Delete extends Op("delete")

    object Op:
      type Wire = Wire.T
      object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type ParseS = ParseS.T
      object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type WireResult = WireResult.T
      object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type FromWireS = FromWireS.T
      object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      def parse(s: com.histmersion.api.research.Article.Edits.Op.ParseS): Option[Op] = values.find(_.wire.equalsIgnoreCase(s.trim))
      given WireEnumValues[Op]:
        def all: Array[Op] = Op.values
        def wire(e: Op): com.histmersion.api.research.Article.Edits.Op.WireResult = e.wire
        def fromWire(s: com.histmersion.api.research.Article.Edits.Op.FromWireS): Option[Op] = parse(s)
      given Encoder[Op] = WireEnum.encoder
      given Decoder[Op] = WireEnum.decoder
      given Schema[Op] = WireEnum.schema

    final case class EditAnswer(op: Op, anchor: EditAnswer.Anchor, text: Option[EditAnswer.Text] = None) derives KindlingsCodecAsObject
    object EditAnswer:
      type Anchor = Anchor.T
      object Anchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Text = Text.T
      object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      given Schema[EditAnswer] = KindlingsSchema
        .derived[EditAnswer]
        .schema
        .description("One change to the article body")
        .modify(_.op)(_.description("replace | insertAfter | insertBefore | delete"))
        .modify(_.anchor)(
          _.description(
            "Text copied EXACTLY from the current body (a sentence, or enough words to occur only once); " +
              "edits apply in order, so an anchor is looked up in the text the earlier edits produced"
          )
        )
        .modify(_.text)(
          _.description(
            "replace: the new text; insertAfter/insertBefore: the text to insert, verbatim (start it with a blank " +
              "line to add a paragraph); delete: null"
          )
        )

    enum Edit:
      def anchor: com.histmersion.api.research.Article.Edits.Edit.AnchorResult
      case Replace(anchor: Edit.Replace.Anchor, text: Edit.Replace.Text)
      case InsertAfter(anchor: Edit.InsertAfter.Anchor, text: Edit.InsertAfter.Text)
      case InsertBefore(anchor: Edit.InsertBefore.Anchor, text: Edit.InsertBefore.Text)
      case Delete(anchor: Edit.Delete.Anchor)

    object Edit:
      object Replace:
        type Anchor = Anchor.T
        object Anchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
        type Text = Text.T
        object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      object InsertAfter:
        type Anchor = Anchor.T
        object Anchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
        type Text = Text.T
        object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      object InsertBefore:
        type Anchor = Anchor.T
        object Anchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
        type Text = Text.T
        object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      object Delete:
        type Anchor = Anchor.T
        object Anchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type AnchorResult = AnchorResult.T
      object AnchorResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    /** A replace whose text is empty or null is not read as a delete: a strict-schema provider sends `null` (or `""`)
      * for a field it left out.
      */
    def parse(a: EditAnswer): Either[com.histmersion.api.research.Article.Edits.ParseResultPart1, Edit] =
      val anchor = Option(a.anchor).getOrElse("")
      val text = Option(a.text).flatten.getOrElse("")
      if anchor.trim.isEmpty then Left("no anchor — copy the passage the edit is about from the body")
      else
        a.op match
          case Op.Delete                       => Right(Edit.Delete(anchor))
          case Op.Replace if text.trim.isEmpty =>
            Left("a replace with no new text — give the text, or use delete to remove the passage")
          case Op.Replace             => Right(Edit.Replace(anchor, text))
          case _ if text.trim.isEmpty => Left("no text to insert")
          case Op.InsertAfter         => Right(Edit.InsertAfter(anchor, text))
          case Op.InsertBefore        => Right(Edit.InsertBefore(anchor, text))

    final case class EditResult(index: EditResult.Index, op: Op, anchor: EditResult.Anchor, text: EditResult.Text, problem: Option[EditResult.Problem] = None)
        derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema:
      def applied: com.histmersion.api.research.Article.Edits.EditResult.AppliedResult = problem.isEmpty
      def label: com.histmersion.api.research.Article.Edits.EditResult.LabelResult = s"edit $index (${op.wire} \"$anchor\")"

    object EditResult:
      type AppliedResult = AppliedResult.T
      object AppliedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type LabelResult = LabelResult.T
      object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type Index = Index.T
      object Index extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Anchor = Anchor.T
      object Anchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Text = Text.T
      object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Problem = Problem.T
      object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class Applied(body: com.histmersion.api.research.Article.Body, results: scala.List[EditResult]):
      def problems: scala.List[com.histmersion.api.research.Article.Edits.Applied.ProblemsResult] = results.flatMap(r => r.problem.map(p => s"${r.label}: $p"))
      def appliedCount: com.histmersion.api.research.Article.Edits.Applied.AppliedCountResult = results.count(_.applied)

    object Applied:
      type ProblemsResult = ProblemsResult.T
      object ProblemsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type AppliedCountResult = AppliedCountResult.T
      object AppliedCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def head(anchor: com.histmersion.api.research.Article.Edits.HeadAnchor, max: com.histmersion.api.research.Article.Edits.HeadMax = 48): com.histmersion.api.research.Article.Edits.HeadResult =
      val one = anchor.trim.replaceAll("\\s+", " ")
      if one.length <= max then one else one.take(max).trim + "…"

    private def squeeze(s: com.histmersion.api.research.Article.Edits.SqueezeS): (com.histmersion.api.research.Article.Edits.SqueezeResultPart1, com.histmersion.api.research.Article.Edits.SqueezeResultPart2) =
      val out = StringBuilder()
      val pos = Array.newBuilder[Int]
      var i = 0
      while i < s.length do
        if s(i).isWhitespace then
          val from = i
          while i < s.length && s(i).isWhitespace do i += 1
          out.append(' '); pos += from
        else
          out.append(s(i)); pos += i; i += 1
      (out.toString, pos.result())

    private def occurrences(text: com.histmersion.api.research.Article.Edits.OccurrencesText, needle: com.histmersion.api.research.Article.Edits.OccurrencesNeedle): scala.List[com.histmersion.api.research.Article.Edits.OccurrencesResult] =
      Iterator.iterate(text.indexOf(needle))(i => text.indexOf(needle, i + 1)).takeWhile(_ >= 0).toList

    def locate(text: com.histmersion.api.research.Article.Edits.LocateText, anchor: com.histmersion.api.research.Article.Edits.LocateAnchor): Either[com.histmersion.api.research.Article.Edits.LocateResultPart1, (com.histmersion.api.research.Article.Edits.LocateResultPart2Part1, com.histmersion.api.research.Article.Edits.LocateResultPart2Part2)] =
      val a = anchor.trim
      occurrences(text, a) match
        case scala.List(i) => Right((i, i + a.length))
        case _ :: _ :: _   =>
          Left(s"the anchor occurs ${occurrences(text, a).size} times — quote more of the passage so it occurs once")
        case Nil =>
          val (sq, pos) = squeeze(text)
          val na = a.replaceAll("\\s+", " ")
          occurrences(sq, na) match
            case scala.List(i) =>
              val last = i + na.length - 1
              Right((pos(i), pos(last) + 1))
            case Nil =>
              Left("the anchor is not in the article — copy it exactly from the current body (earlier edits included)")
            case more => Left(s"the anchor occurs ${more.size} times — quote more of the passage so it occurs once")

    private def cut(text: com.histmersion.api.research.Article.Edits.CutText, s: com.histmersion.api.research.Article.Edits.CutS, e: com.histmersion.api.research.Article.Edits.CutE): com.histmersion.api.research.Article.Edits.CutResult =
      val before = text.substring(0, s)
      val after = text.substring(e)
      val pre = before.reverseIterator.takeWhile(_.isWhitespace).mkString.reverse
      val post = after.takeWhile(_.isWhitespace)
      val gap = pre + post
      val head = before.dropRight(pre.length)
      val tail = after.drop(post.length)
      val joint =
        if head.isEmpty || tail.isEmpty then ""
        else if gap.count(_ == '\n') >= 2 then "\n\n"
        else if gap.contains('\n') then "\n"
        else if gap.nonEmpty then " "
        else ""
      head + joint + tail + (if tail.isEmpty then post.filter(_ == '\n').take(1) else "")

    def applyOne(text: com.histmersion.api.research.Article.Edits.ApplyOneText, e: Edit): Either[com.histmersion.api.research.Article.Edits.ApplyOneResultPart1, com.histmersion.api.research.Article.Edits.ApplyOneResultPart2] =
      locate(text, e.anchor).map { (s, end) =>
        e match
          case Edit.Replace(_, t)      => text.substring(0, s) + t + text.substring(end)
          case Edit.InsertAfter(_, t)  => text.substring(0, end) + t + text.substring(end)
          case Edit.InsertBefore(_, t) => text.substring(0, s) + t + text.substring(s)
          case Edit.Delete(_)          => cut(text, s, end)
      }

    def applyAll(
        body: com.histmersion.api.research.Article.Edits.ApplyAllBody,
        edits: scala.List[EditAnswer],
        refuse: EditAnswer => Option[com.histmersion.api.research.Article.Edits.ApplyAllRefuseResult] = _ => None
    ): Applied =
      @tailrec def loop(rest: scala.List[EditAnswer], number: Int, cur: String, acc: Vector[EditResult]): Applied =
        rest match
          case Nil       => Applied(cur, acc.toList)
          case a :: tail =>
            val base = EditResult(number, a.op, head(Option(a.anchor).getOrElse("")), a.text.getOrElse(""))
            parse(a).flatMap(e => refuse(a).toLeft(e)).flatMap(applyOne(cur, _)) match
              case Right(t) => loop(tail, number + 1, t, acc :+ base)
              case Left(p)  => loop(tail, number + 1, cur, acc :+ base.copy(problem = Some(p)))
      loop(edits, 1, body, Vector.empty)

    enum Draft:
      case Edits(edits: scala.List[EditAnswer])
      case Body(text: Draft.Body.Text)

    object Draft:
      object Body:
        type Text = Text.T
        object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    final case class ArticleCheckRequest(
        edits: scala.List[EditAnswer] = Nil,
        body: Option[com.histmersion.api.research.Article.Body] = None,
        kind: Option[com.histmersion.api.research.Article.KindValue] = None
    ) derives KindlingsCodecAsObject:
      def draft: Either[com.histmersion.api.research.Article.Edits.ArticleCheckRequest.DraftResultPart1, Draft] =
        (edits, body.filter(_.trim.nonEmpty)) match
          case (Nil, None)       => Left("give `edits` (against the stored body) or `body` (a whole draft)")
          case (_ :: _, Some(_)) => Left("give `edits` or `body`, not both")
          case (es, None)        => Right(Draft.Edits(es))
          case (Nil, Some(b))    => Right(Draft.Body(b))
    object ArticleCheckRequest:
      type DraftResultPart1 = DraftResultPart1.T
      object DraftResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      given Schema[ArticleCheckRequest] = KindlingsSchema
        .derived[ArticleCheckRequest]
        .schema
        .modify(_.edits)(_.description("Edits to the CURRENT stored body, applied in order"))
        .modify(_.body)(_.description("Instead of edits: a whole draft body to check"))
        .modify(_.kind)(
          _.description("Check the section set of this kind instead of the article's (index = none, for a fragment)")
        )

    final case class ArticleCheck(
        slug: com.histmersion.api.research.Article.Slug,
        kind: com.histmersion.api.research.Article.KindValue,
        edits: scala.List[EditResult],
        problems: scala.List[ArticleCheck.Problems],
        fatal: ArticleCheck.Fatal,
        wordsBefore: ArticleCheck.WordsBefore,
        wordsAfter: ArticleCheck.WordsAfter,
        body: com.histmersion.api.research.Article.Body
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema:
      def clean: com.histmersion.api.research.Article.Edits.ArticleCheck.CleanResult = problems.isEmpty

    object ArticleCheck:
      type CleanResult = CleanResult.T
      object CleanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type Problems = Problems.T
      object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Fatal = Fatal.T
      object Fatal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type WordsBefore = WordsBefore.T
      object WordsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type WordsAfter = WordsAfter.T
      object WordsAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def markdown(c: ArticleCheck): com.histmersion.api.research.Article.Edits.MarkdownResult =
      val edits =
        if c.edits.isEmpty then ""
        else "\n\n### Edits\n\n" + c.edits.map(r => s"- ${r.label}: ${r.problem.getOrElse("applies")}").mkString("\n")
      val verdict =
        if c.clean then "No problems: these are the checks code runs on the answer, and it would pass them."
        else if c.fatal then s"${Plural.count(c.problems.size, "problem")}, at least one FATAL (never written):"
        else s"${Plural.count(c.problems.size, "problem")}:"
      val problems = if c.clean then "" else "\n\n" + c.problems.map(p => s"- $p").mkString("\n")
      s"## Check of [[${c.slug}]] (${c.kind})\n\n${c.wordsBefore} → ${c.wordsAfter} words.$edits\n\n### Result\n\n$verdict$problems"

  final case class Filter(
      query: Option[Filter.Query] = None,
      topics: scala.List[Filter.Topics] = Nil,
      tags: scala.List[Filter.Tags] = Nil,
      status: scala.List[Filter.Status] = Nil,
      places: scala.List[Filter.Places] = Nil,
      authors: scala.List[Filter.Authors] = Nil,
      from: Option[Filter.From] = None,
      to: Option[Filter.To] = None,
      extraQueries: scala.List[Filter.ExtraQueries] = Nil
  ):
    def isEmpty: com.histmersion.api.research.Article.Filter.IsEmptyResult =
      query.forall(_.trim.isEmpty) && topics.isEmpty && tags.isEmpty && status.isEmpty && places.isEmpty &&
        authors.isEmpty && from.isEmpty && to.isEmpty

    def toFilter: com.histmersion.api.library.Filter =
      com.histmersion.api.library.Filter.of(
        q = query,
        tags = tags,
        authors = authors,
        places = places,
        topics = topics,
        status = status,
        from = from,
        to = to
      )

    def line: com.histmersion.api.research.Article.Filter.LineResult =
      def list(xs: scala.List[String]) = xs.map(Filter.quoted).mkString("[", ", ", "]")
      val parts = query.map(q => s"query: ${Filter.quoted(q)}").toList ++
        Option.when(topics.nonEmpty)(s"topics: ${list(topics)}") ++
        Option.when(tags.nonEmpty)(s"tags: ${list(tags)}") ++
        Option.when(status.nonEmpty)(s"status: ${list(status)}") ++
        Option.when(places.nonEmpty)(s"places: ${list(places)}") ++
        Option.when(authors.nonEmpty)(s"authors: ${list(authors)}") ++
        from.map(f => s"from: $f") ++ to.map(t => s"to: $t")
      s"search {${parts.mkString(", ")}}"

  object Filter:
    type IsEmptyResult = IsEmptyResult.T
    object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LineResult = LineResult.T
    object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type QuotedResult = QuotedResult.T
    object QuotedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type QuotedS = QuotedS.T
    object QuotedS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NormaliseResultPart1 = NormaliseResultPart1.T
    object NormaliseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExtraQueries = ExtraQueries.T
    object ExtraQueries extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[Filter] = KindlingsSchema
      .derived[Filter]
      .schema
      .description("The filter the article is compiled from; an empty one ({}) clears it")
      .modify(_.query)(_.description("Free text the sources must match (the search query)"))
      .modify(_.topics)(_.description("Topics the sources must all have"))
      .modify(_.tags)(_.description("Tags the sources must all have"))
      .modify(_.status)(_.description("Statuses, any of (current, contested, …)"))
      .modify(_.places)(_.description("Places, any of (a gazetteer id or a name)"))
      .modify(_.authors)(_.description("Authors, any of (a name substring)"))
      .modify(_.from)(_.description("Deep-time window start, signed year (BCE negative)"))
      .modify(_.to)(_.description("Deep-time window end, signed year (BCE negative)"))
      .modify(_.extraQueries)(
        _.description(
          "Further free-text searches the writer ran; reported in the Method note, not part of the set every source of which must be cited or given a reason"
        )
      )

    given Codec.AsObject[Filter] = StrictJson.codec(KindlingsCodecAsObject.derived[Filter])

    private[api] def quoted(s: com.histmersion.api.research.Article.Filter.QuotedS): com.histmersion.api.research.Article.Filter.QuotedResult = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    def normalise(f: Filter): Either[com.histmersion.api.research.Article.Filter.NormaliseResultPart1, Option[Filter]] =
      def clean(xs: scala.List[String]) = xs.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).distinct
      val status = clean(f.status).map(_.toLowerCase(java.util.Locale.ROOT))
      val unknown = status.filterNot(s => Source.Status.values.exists(_.wire == s))
      val tidy = Filter(
        f.query.map(_.trim).filter(_.nonEmpty),
        clean(f.topics),
        clean(f.tags),
        status,
        clean(f.places),
        clean(f.authors),
        f.from,
        f.to,
        f.extraQueries.map(_.trim).filter(_.nonEmpty).distinct
      )
      if unknown.nonEmpty then
        Left(
          s"unknown status ${unknown.map(s => s"'$s'").mkString(", ")} — one of ${Source.Status.values.map(_.wire).mkString(", ")}"
        )
      else if tidy.from.zip(tidy.to).exists(_ > _) then
        Left(s"the filter's from (${tidy.from.get}) is after its to (${tidy.to.get})")
      else if tidy.isEmpty && tidy.extraQueries.nonEmpty then
        Left("extraQueries without a filter: give the query, topics or tags the article is compiled from")
      else Right(Option.when(!tidy.isEmpty)(tidy))

  final case class Import(
      text: Import.Text,
      slug: Option[com.histmersion.api.research.Article.Slug] = None,
      kind: Option[com.histmersion.api.research.Article.KindValue] = None,
      mtime: Option[Import.Mtime] = None,
      force: Import.Force = false,
      strict: Import.Strict = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Import:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Mtime = Mtime.T
    object Mtime extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Force = Force.T
    object Force extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Strict = Strict.T
    object Strict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  enum Kind:
    case Concept, Myth, Culture, Region, Timeline, Report, Index

  object Kind:
    type FromStringS = FromStringS.T
    object FromStringS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def fromString(s: com.histmersion.api.research.Article.Kind.FromStringS): Option[Kind] =
      values.find(_.toString.equalsIgnoreCase(s))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): com.histmersion.api.research.Article.Kind.WireResult = e.toString.toLowerCase
      def fromWire(s: com.histmersion.api.research.Article.Kind.FromWireS): Option[Kind] =
        Kind.values.find(_.toString.equalsIgnoreCase(s))

    given Encoder[Kind] = WireEnum.encoder
    given Decoder[Kind] = WireEnum.decoder
    given Schema[Kind] = WireEnum.schema

  final case class KindCount(kind: com.histmersion.api.research.Article.KindValue, count: KindCount.Count) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object KindCount:
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Link(targetKind: Link.TargetKind, target: Link.Target)

  object Link:
    type TargetKind = TargetKind.T
    object TargetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Links:

    type ClassifyTargets = ClassifyTargets.T
    object ClassifyTargets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClassifyIsSourceArg1 = ClassifyIsSourceArg1.T
    object ClassifyIsSourceArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClassifyIsSourceResult = ClassifyIsSourceResult.T
    object ClassifyIsSourceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinksBody = LinksBody.T
    object LinksBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinksIsSourceArg1 = LinksIsSourceArg1.T
    object LinksIsSourceArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinksIsSourceResult = LinksIsSourceResult.T
    object LinksIsSourceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HrefForResult = HrefForResult.T
    object HrefForResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HrefForTarget = HrefForTarget.T
    object HrefForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HrefForCitekeyOfArg1 = HrefForCitekeyOfArg1.T
    object HrefForCitekeyOfArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HrefForCitekeyOfResult = HrefForCitekeyOfResult.T
    object HrefForCitekeyOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HrefForIsArticleArg1 = HrefForIsArticleArg1.T
    object HrefForIsArticleArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HrefForIsArticleResult = HrefForIsArticleResult.T
    object HrefForIsArticleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkMarkdownResult = LinkMarkdownResult.T
    object LinkMarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkMarkdownMd = LinkMarkdownMd.T
    object LinkMarkdownMd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkMarkdownHrefArg1 = LinkMarkdownHrefArg1.T
    object LinkMarkdownHrefArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkMarkdownHrefResult = LinkMarkdownHrefResult.T
    object LinkMarkdownHrefResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkMarkdownDisplayLabelArg1 = LinkMarkdownDisplayLabelArg1.T
    object LinkMarkdownDisplayLabelArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkMarkdownDisplayLabelResult = LinkMarkdownDisplayLabelResult.T
    object LinkMarkdownDisplayLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveMarkdownResult = ResolveMarkdownResult.T
    object ResolveMarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveMarkdownMd = ResolveMarkdownMd.T
    object ResolveMarkdownMd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveMarkdownIsSourceArg1 = ResolveMarkdownIsSourceArg1.T
    object ResolveMarkdownIsSourceArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveMarkdownIsSourceResult = ResolveMarkdownIsSourceResult.T
    object ResolveMarkdownIsSourceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val TargetArticle = "article"
    val TargetSource = "source"

    export com.histmersion.api.text.WikiLinks.{isCitekeyShaped, normalize, occurrences, targets, Occurrence}

    def classify(targets: scala.List[com.histmersion.api.research.Article.Links.ClassifyTargets], isSource: com.histmersion.api.research.Article.Links.ClassifyIsSourceArg1 => com.histmersion.api.research.Article.Links.ClassifyIsSourceResult): scala.List[Link] =
      targets.map { t =>
        if isCitekeyShaped(t) && isSource(t) then Link(TargetSource, t)
        else Link(TargetArticle, t)
      }

    def links(body: com.histmersion.api.research.Article.Links.LinksBody, isSource: com.histmersion.api.research.Article.Links.LinksIsSourceArg1 => com.histmersion.api.research.Article.Links.LinksIsSourceResult): scala.List[Link] =
      classify(targets(body), isSource)

    def hrefFor(target: com.histmersion.api.research.Article.Links.HrefForTarget, citekeyOf: com.histmersion.api.research.Article.Links.HrefForCitekeyOfArg1 => Option[com.histmersion.api.research.Article.Links.HrefForCitekeyOfResult], isArticle: com.histmersion.api.research.Article.Links.HrefForIsArticleArg1 => com.histmersion.api.research.Article.Links.HrefForIsArticleResult): Option[com.histmersion.api.research.Article.Links.HrefForResult] =
      citekeyOf(target)
        .map(ck => s"/source/$ck")
        .orElse(Option.when(isArticle(target))(s"/article/$target"))

    def linkMarkdown(
        md: com.histmersion.api.research.Article.Links.LinkMarkdownMd,
        href: com.histmersion.api.research.Article.Links.LinkMarkdownHrefArg1 => Option[com.histmersion.api.research.Article.Links.LinkMarkdownHrefResult],
        displayLabel: com.histmersion.api.research.Article.Links.LinkMarkdownDisplayLabelArg1 => Option[com.histmersion.api.research.Article.Links.LinkMarkdownDisplayLabelResult] = _ => None
    ): com.histmersion.api.research.Article.Links.LinkMarkdownResult =
      rewriteLinks(md) { (t, explicit, written) =>
        val label = explicit.getOrElse(written)
        val text = if label.isEmpty then displayLabel(t).getOrElse(Label.display(t)) else label
        href(t) match
          case Some(h) => s"[$text]($h)"
          case None    => s"""<span class="dangling" title="unresolved link">$text</span>"""
      }

    export com.histmersion.api.text.WikiLinks.{retarget, rewriteLinks}

    def resolveMarkdown(md: com.histmersion.api.research.Article.Links.ResolveMarkdownMd, isSource: com.histmersion.api.research.Article.Links.ResolveMarkdownIsSourceArg1 => com.histmersion.api.research.Article.Links.ResolveMarkdownIsSourceResult): com.histmersion.api.research.Article.Links.ResolveMarkdownResult =
      linkMarkdown(md, t => Some(if isCitekeyShaped(t) && isSource(t) then s"/source/$t" else s"/article/$t"))

  /** The articles' lint rules: a thin or stand-in summary, filler phrases, uncited paragraphs, unpaired critiques. */
  object Lint:

    type FillerPhrases = FillerPhrases.T
    object FillerPhrases extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleThinSummaryRowsPart1 = ArticleThinSummaryRowsPart1.T
    object ArticleThinSummaryRowsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleThinSummaryRowsPart2 = ArticleThinSummaryRowsPart2.T
    object ArticleThinSummaryRowsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleFillerArticlesPart1 = ArticleFillerArticlesPart1.T
    object ArticleFillerArticlesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleFillerArticlesPart2 = ArticleFillerArticlesPart2.T
    object ArticleFillerArticlesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleUncitedParagraphsArticlesPart1 = ArticleUncitedParagraphsArticlesPart1.T
    object ArticleUncitedParagraphsArticlesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleUncitedParagraphsArticlesPart2 = ArticleUncitedParagraphsArticlesPart2.T
    object ArticleUncitedParagraphsArticlesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCritiquePairingArticlesPart1 = ArticleCritiquePairingArticlesPart1.T
    object ArticleCritiquePairingArticlesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCritiquePairingArticlesPart2 = ArticleCritiquePairingArticlesPart2.T
    object ArticleCritiquePairingArticlesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCritiquePairingResolveArg1 = ArticleCritiquePairingResolveArg1.T
    object ArticleCritiquePairingResolveArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCritiquePairingResolveResult = ArticleCritiquePairingResolveResult.T
    object ArticleCritiquePairingResolveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCritiquePairingWorkArg1 = ArticleCritiquePairingWorkArg1.T
    object ArticleCritiquePairingWorkArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val FillerPhrases: scala.List[com.histmersion.api.research.Article.Lint.FillerPhrases] = scala.List(
      "it is worth noting",
      "it should be noted",
      "it is important to note",
      "interestingly",
      "notably",
      "significantly",
      "it is clear that",
      "needless to say",
      "of course",
      "as is well known",
      "it goes without saying",
      "in conclusion",
      "to summarize",
      "in summary",
      "broadly speaking",
      "generally speaking",
      "it must be emphasized",
      "it cannot be overstated",
      "plays a crucial role",
      "rich tapestry",
      "sheds light on",
      "shed new light",
      "paved the way",
      "remains to be seen",
      "further research is needed"
    )

    private[api] val FillerPattern: scala.util.matching.Regex =
      FillerPhrases.map(p => java.util.regex.Pattern.quote(p)).mkString("(?i)(?:", "|", ")").r

    def articleThinSummary(rows: scala.List[(com.histmersion.api.research.Article.Lint.ArticleThinSummaryRowsPart1, com.histmersion.api.research.Article.Lint.ArticleThinSummaryRowsPart2)]): scala.List[LintItem] =
      rows
        .flatMap { (slug, summary) =>
          val s = summary.trim
          val why =
            if s.isEmpty then Some("no summary")
            else
              Articles
                .summaryOf(Some(s))
                .left
                .toOption
                .map(_ => s"stand-in summary: \"$s\"")
                .orElse(Option.when(s.length < MinSummaryChars)(s"only ${s.length} chars: \"$s\""))
          why.map(LintItem(slug, _))
        }
        .sortBy(_.citekey)

    def articleFiller(articles: scala.List[(com.histmersion.api.research.Article.Lint.ArticleFillerArticlesPart1, com.histmersion.api.research.Article.Lint.ArticleFillerArticlesPart2)]): scala.List[LintItem] =
      articles
        .flatMap { (slug, body) =>
          val matches = FillerPattern.findAllMatchIn(body).map(_.matched.toLowerCase).toList.distinct
          if matches.isEmpty then None
          else
            Some(
              LintItem(
                slug,
                s"filler: ${matches.take(3).mkString(", ")}${
                    if matches.sizeIs > 3 then s" (+${matches.size - 3} more)" else ""
                  }"
              )
            )
        }
        .sortBy(_.citekey)

    private[api] val WikilinkCitekey =
      """\[\[((?=[a-z0-9_]*(?:_|\d{4}))[a-z][a-z0-9_]*)""".r

    def articleUncitedParagraphs(articles: scala.List[(com.histmersion.api.research.Article.Lint.ArticleUncitedParagraphsArticlesPart1, com.histmersion.api.research.Article.Lint.ArticleUncitedParagraphsArticlesPart2)]): scala.List[LintItem] =
      articles
        .flatMap { (slug, body) =>
          val paras = extractParagraphs(body)
          val uncited = paras.filter(p => WikilinkCitekey.findFirstIn(p).isEmpty)
          if uncited.isEmpty then None
          else Some(LintItem(slug, s"${uncited.size} paragraph(s) with no [[citekey]]: \"${uncited.head.take(60)}…\""))
        }
        .sortBy(_.citekey)

    def articleCritiquePairing(
        articles: scala.List[(com.histmersion.api.research.Article.Lint.ArticleCritiquePairingArticlesPart1, com.histmersion.api.research.Article.Lint.ArticleCritiquePairingArticlesPart2)],
        resolve: com.histmersion.api.research.Article.Lint.ArticleCritiquePairingResolveArg1 => Option[com.histmersion.api.research.Article.Lint.ArticleCritiquePairingResolveResult],
        edges: scala.List[com.histmersion.api.research.Coverage.Edge],
        work: com.histmersion.api.research.Article.Lint.ArticleCritiquePairingWorkArg1 => Option[CritiquePairing.Work]
    ): scala.List[LintItem] =
      articles
        .flatMap((slug, body) => CritiquePairing.hits(body, resolve, edges, work).map(h => LintItem(slug, h.line)))
        .sortBy(_.citekey)

  final case class List(
      total: com.histmersion.api.RowCount,
      articles: scala.List[Summary],
      offset: com.histmersion.api.Offset = com.histmersion.api.Offset(0),
      next: Option[com.histmersion.api.Offset] = None,
      kinds: scala.List[KindCount] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def page: OffsetPage = OffsetPage(total, offset, com.histmersion.api.RowCount(articles.size))
  object List:

    type OfTotal = OfTotal.T
    object OfTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfOffset = OfOffset.T
    object OfOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(total: com.histmersion.api.research.Article.List.OfTotal, rows: scala.List[Summary], offset: com.histmersion.api.research.Article.List.OfOffset, kinds: scala.List[KindCount] = Nil): List =
      List(com.histmersion.api.RowCount(total), rows, com.histmersion.api.Offset(offset), OffsetPage(com.histmersion.api.RowCount(total), com.histmersion.api.Offset(offset), com.histmersion.api.RowCount(rows.size)).next, kinds)

    def byKind(counts: scala.List[KindCount]): scala.List[KindCount] =
      counts.filter(_.count > 0).sortBy(k => (-k.count, k.kind))

  final case class Patch(
      title: Option[com.histmersion.api.research.Article.Title] = None,
      summary: Option[com.histmersion.api.research.Article.SummaryText] = None,
      body: Option[com.histmersion.api.research.Article.Body] = None,
      append: Option[Patch.Append] = None,
      section: Option[Patch.Section] = None,
      tags: Option[ListPatch] = None,
      regions: Option[scala.List[Patch.Regions]] = None,
      period: Option[Period.Input] = None,
      kind: Option[com.histmersion.api.research.Article.KindValue] = None,
      ifUpdatedAt: Option[Patch.IfUpdatedAt] = None,
      filter: Option[Filter] = None,
      reasons: scala.List[com.histmersion.api.research.Coverage.Reason] = Nil,
      critiqueReasons: scala.List[CritiqueReason] = Nil
  ) derives KindlingsSchema, com.histmersion.api.json.Schema:
    def onlyCritiques: com.histmersion.api.research.Article.Patch.OnlyCritiquesResult =
      critiqueReasons.nonEmpty && this.modify(_.critiqueReasons).setTo(Nil).modify(_.ifUpdatedAt).setTo(None) == Patch()
  object Patch:


    type OnlyCritiquesResult = OnlyCritiquesResult.T
    object OnlyCritiquesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Append = Append.T
    object Append extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Section = Section.T
    object Section extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IfUpdatedAt = IfUpdatedAt.T
    object IfUpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  object Plans:

    type FilterLineResult = FilterLineResult.T
    object FilterLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FilterBlockResult = FilterBlockResult.T
    object FilterBlockResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanBlockResult = PlanBlockResult.T
    object PlanBlockResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    final case class FilterProposal(
        slug: com.histmersion.api.research.Article.Slug,
        filter: Filter,
        rationale: FilterProposal.Rationale = "",
        total: FilterProposal.Total = 0,
        cited: FilterProposal.Cited = 0,
        citedIn: FilterProposal.CitedIn = 0,
        missed: scala.List[FilterProposal.Missed] = Nil,
        sample: scala.List[FilterProposal.Sample] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema:
      def recall: com.histmersion.api.research.Article.Plans.FilterProposal.RecallResult = if cited == 0 then 1.0 else citedIn.toDouble / cited

    object FilterProposal:
      type RecallResult = RecallResult.T
      object RecallResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type Rationale = Rationale.T
      object Rationale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Total = Total.T
      object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Cited = Cited.T
      object Cited extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type CitedIn = CitedIn.T
      object CitedIn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Missed = Missed.T
      object Missed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Sample = Sample.T
      object Sample extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class ProposedArticle(
        slug: com.histmersion.api.research.Article.Slug,
        title: com.histmersion.api.research.Article.Title,
        kind: com.histmersion.api.research.Article.KindValue,
        filter: Filter,
        why: ProposedArticle.Why = "",
        hits: ProposedArticle.Hits = 0,
        overlap: ProposedArticle.Overlap = 0,
        overlapWith: Option[ProposedArticle.OverlapWith] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object ProposedArticle:
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Hits = Hits.T
      object Hits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Overlap = Overlap.T
      object Overlap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type OverlapWith = OverlapWith.T
      object OverlapWith extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class ArticlePlan(
        goal: ArticlePlan.Goal,
        rationale: ArticlePlan.Rationale = "",
        articles: scala.List[ProposedArticle] = Nil,
        dropped: scala.List[ArticlePlan.Dropped] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema:
      def isEmpty: com.histmersion.api.research.Article.Plans.ArticlePlan.IsEmptyResult = articles.isEmpty

    object ArticlePlan:
      type IsEmptyResult = IsEmptyResult.T
      object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type Goal = Goal.T
      object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Rationale = Rationale.T
      object Rationale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Dropped = Dropped.T
      object Dropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def filterLine(f: Filter): com.histmersion.api.research.Article.Plans.FilterLineResult = f.line

    def filterBlock(p: FilterProposal): com.histmersion.api.research.Article.Plans.FilterBlockResult =
      val missed =
        if p.missed.isEmpty then "" else s"\n- cited but not returned: ${p.missed.map(c => s"[[$c]]").mkString(", ")}"
      val sample = if p.sample.isEmpty then ""
      else s"\n- returned, not cited (a few): ${p.sample.map(c => s"[[$c]]").mkString(", ")}"
      s"${p.filter.line} → ${Plural.count(p.total, "source")}; returns ${p.citedIn} of the ${Plural.count(p.cited, "cited source")}" +
        missed + sample

    def planBlock(p: ArticlePlan): com.histmersion.api.research.Article.Plans.PlanBlockResult =
      val as = p.articles.map { a =>
        val ov = a.overlapWith.fold("")(o => f" · overlaps [[$o]] ${a.overlap * 100}%.0f%%")
        s"- `${a.slug}` — ${a.title} (${a.kind}) · ${a.filter.line} → ${Plural.count(a.hits, "source")}$ov" +
          Option(a.why).filter(_.nonEmpty).fold("")(w => s": $w")
      }
      val dropped = p.dropped.map(d => s"- dropped: $d")
      (Option(p.rationale).filter(_.nonEmpty).toList ++ scala.List((as ++ dropped).mkString("\n")))
        .filter(_.nonEmpty)
        .mkString("\n\n")

  final case class Put(
      title: com.histmersion.api.research.Article.Title,
      kind: com.histmersion.api.research.Article.KindValue,
      body: com.histmersion.api.research.Article.Body,
      summary: Option[com.histmersion.api.research.Article.SummaryText] = None,
      tags: scala.List[Put.Tags] = Nil,
      regions: scala.List[Put.Regions] = Nil,
      periodStart: Option[Put.PeriodStart] = None,
      periodEnd: Option[Put.PeriodEnd] = None,
      filter: Option[Filter] = None,
      reasons: scala.List[com.histmersion.api.research.Coverage.Reason] = Nil
  ) derives KindlingsSchema, com.histmersion.api.json.Schema
  object Put:


    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodStart = PeriodStart.T
    object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodEnd = PeriodEnd.T
    object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Codec.AsObject[Put] = StrictJson.codec(KindlingsCodecAsObject.derived[Put])

    def of(a: Article): Put = Put(
      a.title,
      a.kind,
      a.body,
      a.summary,
      a.tags,
      a.regions,
      a.period.map(_.start),
      a.period.map(_.end),
      a.filter,
      com.histmersion.api.research.Coverage.Reason.list(a.reasons)
    )

  final case class Ref(slug: com.histmersion.api.research.Article.Slug, title: com.histmersion.api.research.Article.Title, summary: Option[com.histmersion.api.research.Article.SummaryText] = None)

  final case class ReviewView(
      slug: com.histmersion.api.research.Article.Slug,
      badge: ReviewBadge,
      outcome: Option[ReviewView.Outcome] = None,
      updatedAt: Option[ReviewView.UpdatedAt] = None,
      rounds: scala.List[ReviewRound] = Nil,
      goals: scala.List[ReviewGoal] = Nil,
      approvals: scala.List[ReviewApproval] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
  object ReviewView:


    type ReviewerNameResult = ReviewerNameResult.T
    object ReviewerNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReviewerNameWire = ReviewerNameWire.T
    object ReviewerNameWire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OutcomeLineResult = OutcomeLineResult.T
    object OutcomeLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkdownResult = MarkdownResult.T
    object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Outcome = Outcome.T
    object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def reviewerName(wire: com.histmersion.api.research.Article.ReviewView.ReviewerNameWire): com.histmersion.api.research.Article.ReviewView.ReviewerNameResult = wire match
      case "rules"       => "rules reviewer"
      case "cold-reader" => "cold reader"
      case "skeptic"     => "skeptic"
      case "mechanical"  => "mechanical check"
      case other         => other

    def outcomeLine(f: ReviewFinding): com.histmersion.api.research.Article.ReviewView.OutcomeLineResult =
      val base = f.outcome match
        case "wont-fix"           => "won't fix"
        case "set-aside"          => "set aside"
        case "open" if f.deferred => "open (left for the next run)"
        case other                => other
      val when = f.closedRunNumber.zip(f.closedRound).fold("")((run, round) => s" in run $run, round $round")
      val by = f.closedBy.filter(_.nonEmpty).fold("")(b => s" by ${reviewerName(b)}")
      val note = Option(f.note).map(_.trim).filter(_.nonEmpty).fold("")(n => s": $n")
      val back = if f.reopened > 0 then s" (reopened ${Plural.count(f.reopened, "time")})" else ""
      s"$base$by$when$back$note"

    def markdown(r: ReviewView): com.histmersion.api.research.Article.ReviewView.MarkdownResult =
      val head = s"## Review of [[${r.slug}]]\n\n**${r.badge.label}** — ${r.badge.detail}" +
        r.outcome.filter(_.nonEmpty).fold("")(o => s"\n\nReview manager: $o" + r.updatedAt.fold("")(t => s" ($t)"))
      val goals =
        if r.goals.isEmpty then ""
        else "\n\n### Research goals\n\n" + r.goals.map(g => s"- `${g.slug}` (${g.status}) ${g.title}").mkString("\n")
      val approvals =
        if r.approvals.isEmpty then ""
        else
          "\n\n### Waiting for a person\n\n" + r.approvals.map(a => s"- approval #${a.id} (${a.kind})").mkString("\n")
      val history =
        if r.rounds.isEmpty then "\n\n### Review history\n\nNo finding recorded."
        else
          "\n\n### Review history\n\n" + r.rounds
            .map { rd =>
              s"#### Run ${rd.runNumber}, round ${rd.round}${rd.startedAt.fold("")(t => s" ($t)")}\n\n" +
                rd.findings
                  .map(f =>
                    s"- **${f.id}** ${f.severity.toUpperCase} · ${reviewerName(f.reviewer)}" +
                      (if f.kind.nonEmpty then s" · ${f.kind}" else "") + (if f.where.nonEmpty then s" · ${f.where}"
                                                                           else "") +
                      s": ${f.problem} — *${outcomeLine(f)}*"
                  )
                  .mkString("\n")
            }
            .mkString("\n\n")
      head + goals + approvals + history

  final case class Row(
      slug: com.histmersion.api.research.Article.Slug,
      title: com.histmersion.api.research.Article.Title,
      kind: com.histmersion.api.research.Article.KindValue,
      body: com.histmersion.api.research.Article.Body,
      summary: Option[com.histmersion.api.research.Article.SummaryText],
      tags: scala.List[Row.Tags],
      regions: scala.List[Row.Regions],
      periodStart: Option[Row.PeriodStart],
      periodEnd: Option[Row.PeriodEnd],
      updatedAt: Option[Row.UpdatedAt] = None,
      uid: Option[java.util.UUID] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Row:
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodStart = PeriodStart.T
    object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodEnd = PeriodEnd.T
    object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Summary(
      slug: com.histmersion.api.research.Article.Slug,
      title: com.histmersion.api.research.Article.Title,
      kind: com.histmersion.api.research.Article.KindValue,
      summary: Option[com.histmersion.api.research.Article.SummaryText],
      tags: scala.List[Summary.Tags],
      regions: scala.List[Summary.Regions],
      periodStart: Option[Summary.PeriodStart],
      periodEnd: Option[Summary.PeriodEnd],
      updatedAt: Option[Summary.UpdatedAt] = None,
      review: Option[ReviewBadge] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Summary:
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodStart = PeriodStart.T
    object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodEnd = PeriodEnd.T
    object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Version(
      id: Version.Id,
      slug: com.histmersion.api.research.Article.Slug,
      summary: Option[com.histmersion.api.research.Article.SummaryText],
      updatedAt: Version.UpdatedAt,
      createdAt: Version.CreatedAt,
      wordCount: Version.WordCount
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Version:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WordCount = WordCount.T
    object WordCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class VersionFull(
      id: VersionFull.Id,
      slug: com.histmersion.api.research.Article.Slug,
      body: com.histmersion.api.research.Article.Body,
      summary: Option[com.histmersion.api.research.Article.SummaryText],
      updatedAt: VersionFull.UpdatedAt,
      createdAt: VersionFull.CreatedAt
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object VersionFull:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class View(
      article: Row,
      backlinks: scala.List[Backlink],
      sourcesCited: scala.List[CitedSource],
      formatting: scala.List[FormatIssue] = Nil,
      coverage: Option[com.histmersion.api.research.Coverage] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
