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
  type Export = Export.T
  object Export extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FullText = FullText.T
  object FullText extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Heading = Heading.T
  object Heading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lede = Lede.T
  object Lede extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Part = Part.T
  object Part extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Period = Period.T
  object Period extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Prose = Prose.T
  object Prose extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Section = Section.T
  object Section extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionId = VersionId.T
  object VersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReasonsValue = ReasonsValue.T
  object ReasonsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Coverage:
    type DisagreementKind = DisagreementKind.T
    object DisagreementKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Export = Export.T
    object Export extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Markdown = Markdown.T
    object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Method = Method.T
    object Method extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotUsed = NotUsed.T
    object NotUsed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sections = Sections.T
    object Sections extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type DisagreementKinds = DisagreementKindsValue
    type ComputeSlug = _root_.com.histmersion.api.research.Article.Slug
    val ComputeSlug: _root_.com.histmersion.api.research.Article.Slug.type = _root_.com.histmersion.api.research.Article.Slug
    type ComputeTotal = _root_.com.histmersion.api.research.Coverage.Total
    val ComputeTotal: _root_.com.histmersion.api.research.Coverage.Total.type = _root_.com.histmersion.api.research.Coverage.Total
    type ComputeReasonsPart1 = ComputeReasonsPart1.T
    object ComputeReasonsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
    type ComputeReasonsPart2 = ComputeReasonsPart2.T
    object ComputeReasonsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnplacedAfterReasonsPart2 = UnplacedAfterReasonsPart2.T
    object UnplacedAfterReasonsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val DisagreementKinds: scala.List[com.histmersion.api.research.Article.Coverage.DisagreementKinds] = scala.List(_root_.com.histmersion.api.research.Article.Coverage.DisagreementKind("challenges"), _root_.com.histmersion.api.research.Article.Coverage.DisagreementKind("refutes"), _root_.com.histmersion.api.research.Article.Coverage.DisagreementKind("updates"), _root_.com.histmersion.api.research.Article.Coverage.DisagreementKind("superseded_by"))

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
        citedInFilter: Set[com.histmersion.api.library.Citekey],
        cited: scala.List[com.histmersion.api.library.Citekey],
        reasons: Map[com.histmersion.api.library.Citekey, com.histmersion.api.research.Article.Coverage.ComputeReasonsPart2],
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
      val outside = cited.distinct.filterNot(ck => citedInFilter(_root_.com.histmersion.api.library.Citekey((ck).value)) || readSet(ck))
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
        cited: Set[com.histmersion.api.library.Citekey],
        reasons: Map[com.histmersion.api.library.Citekey, com.histmersion.api.research.Article.Coverage.UnplacedAfterReasonsPart2]
    ): scala.List[String] =
      c.notUsed.map(_.source.citekey).filterNot(ck => cited(ck) || reasons.contains(ck))

    /** HTML comments, so Obsidian and the page do not show them. */
    val GeneratedStart = "<!-- generated from the article's filter: edits here are not kept -->"
    val GeneratedEnd = "<!-- end of generated sections -->"

    val NotUsedHeading = "Not used"

    def sections(c: com.histmersion.api.research.Coverage): com.histmersion.api.research.Article.Coverage.Sections = _root_.com.histmersion.api.research.Article.Coverage.Sections(s"${notUsed(c)}\n\n${method(c)}")

    def exported(c: com.histmersion.api.research.Coverage): com.histmersion.api.research.Article.Coverage.Export =
      _root_.com.histmersion.api.research.Article.Coverage.Export(s"$GeneratedStart\n\n${sections(c)}\n\n$GeneratedEnd")

    def notUsed(c: com.histmersion.api.research.Coverage): com.histmersion.api.research.Article.Coverage.NotUsed =
      val src = Plural.noun(c.total, _root_.com.histmersion.api.Plural.Noun.Singular("source"))
      val citedN = c.cited.size
      val n = c.notUsed.size
      val scope = if ((c.capped) == (true)) then s"; the first ${c.checked} (by citekey) were checked" else ""
      val lead =
        if c.total == _root_.com.histmersion.api.research.Coverage.Total(0) then "_The filter returns no source._"
        else if n == 0 then
          if ((c.capped) == (true)) then s"_The filter returns ${c.total} $src; all ${c.checked} checked are cited above._"
          else
            s"_${if c.total == _root_.com.histmersion.api.research.Coverage.Total(1) then "The one source" else s"All ${c.total} sources"} the filter returns ${
                if c.total == _root_.com.histmersion.api.research.Coverage.Total(1) then "is" else "are"
              } cited above._"
        else
          val citedText = if citedN == 0 then "None of them is" else Plural.count(_root_.com.histmersion.api.Plural.CountN((citedN).toLong), _root_.com.histmersion.api.Plural.CountSingular("of them is"), _root_.com.histmersion.api.Plural.CountPlural("of them are"))
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

    private def statusNote(s: Source.Status): String = if Stated(s) then (s" (${s.wire})") else ("")

    private def endSentence(s: String): String =
      if (s).value.endsWith(".") || (s).value.endsWith("?") || (s).value.endsWith("!") then ((s).value) else (s"$s.")

    def method(c: com.histmersion.api.research.Coverage): com.histmersion.api.research.Article.Coverage.Method =
      val statuses =
        if c.statuses.isEmpty then Nil
        else
          val have = c.statuses.map(s => s"${s.count} ${s.status.wire}").mkString(", ")
          val none = Disputed.filterNot(d => c.statuses.exists(_.status == d)).map(_.wire)
          val noneText = if none.isEmpty then "" else s"; none ${orList(none)}"
          scala.List(s"statuses${if ((c.capped) == (true)) then s" of the ${c.checked} checked" else ""}: $have$noneText")
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

    private def orList(xs: scala.List[String]): String = xs match
      case Nil        => ("")
      case one :: Nil => ((one).value)
      case _          => (s"${xs.init.mkString(", ")} or ${xs.last}")

    /** An `_` or `*` of a user's query would end the Method line's Markdown emphasis. */
    private def escape(s: String): String = ((s).value.replace("*", "\\*").replace("_", "\\_"))

    private def isHeading(l: String) = (l).value.trim.startsWith("#")
    private def level(l: String) = (l).value.trim.takeWhile(_ == '#').length

    def withoutNotUsed(text: com.histmersion.api.research.Article.Body): com.histmersion.api.research.Article.Body =
      val lines = (text).value.split("\n", -1).toList
      lines.indexWhere(l =>
        isHeading((l)) && l.trim.replaceAll("^#+\\s*", "").trim.toLowerCase.startsWith("not used")
      ) match
        case -1 => _root_.com.histmersion.api.research.Article.Body((text).value)
        case i  =>
          val lv = level((lines(i)))
          val rest = lines.drop(i + 1)
          val end = rest.indexWhere(l => isHeading((l)) && level((l)) <= lv)
          _root_.com.histmersion.api.research.Article.Body((lines.take(i) ++ (if end < 0 then Nil else rest.drop(end))).mkString("\n"))

    def hasMethodLine(text: String): Boolean = ((text).value.linesIterator.exists(_.trim.startsWith("_Method")))

    def stripGenerated(body: com.histmersion.api.research.Article.Body): com.histmersion.api.research.Article.Body =
      val s = (body).value.indexOf(GeneratedStart)
      if s < 0 then _root_.com.histmersion.api.research.Article.Body((body).value)
      else
        val e = (body).value.indexOf(GeneratedEnd, s)
        val tail = if e < 0 then "" else (body).value.substring(e + GeneratedEnd.length)
        _root_.com.histmersion.api.research.Article.Body(((body).value.substring(0, s).stripTrailing + (if tail.trim.isEmpty then ""
                                               else "\n\n" + tail.dropWhile(_.isWhitespace))).trim)

    def markdown(c: com.histmersion.api.research.Coverage): com.histmersion.api.research.Article.Coverage.Markdown =
      val drift =
        if c.drift.isEmpty then "No drift: every source the filter returns is cited or has a reason."
        else
          c.drift
            .map { d =>
              val what = d.kind match
                case DriftKind.Unplaced       => "neither cited nor given a reason"
                case DriftKind.ReasonForCited => "given a reason but cited in the body (the reason is stale)"
                case DriftKind.ReasonOutside  => "given a reason but no longer returned by the filter"
              s"- ${Plural.count(_root_.com.histmersion.api.Plural.Count((d.citekeys.size).toLong), _root_.com.histmersion.api.Plural.Noun.Singular("source"))} $what: ${d.citekeys.map(ck => s"[[$ck]]").mkString(", ")}"
            }
            .mkString("\n")
      val critiques = Critiques.markdown(c.critiques)
      s"## Coverage of [[${c.slug}]]\n\n`${c.filterLine}` → ${c.total} (${c.cited.size} cited, ${c.notUsed.size} not used" +
        s"${if ((c.capped) == (true)) then s", ${(c.total).value - c.checked} not checked" else ""})\n\n### Drift\n\n$drift" +
        (if critiques.isEmpty then "" else s"\n\n$critiques") + s"\n\n### Generated sections\n\n${sections(c)}" +
        (if c.eventLeads.isEmpty then "" else "\n\n" + ArticleEventLead.markdown(c.eventLeads))

  final case class Deleted(slug: com.histmersion.api.research.Article.Slug, deleted: Deleted.Value) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Deleted:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  object Edits:
    type Anchor = Anchor.T
    object Anchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeadLimit = HeadLimit.T
    object HeadLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Markdown = Markdown.T
    object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NewText = NewText.T
    object NewText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Occurrences = Occurrences.T
    object Occurrences extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]



    type ParseResultPart1 = ParseResultPart1.T
    object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OccurrencesText = _root_.com.histmersion.api.research.Article.Body
    val OccurrencesText: _root_.com.histmersion.api.research.Article.Body.type = _root_.com.histmersion.api.research.Article.Body
    type LocateResultPart1 = LocateResultPart1.T
    object LocateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocateText = _root_.com.histmersion.api.research.Article.Body
    val LocateText: _root_.com.histmersion.api.research.Article.Body.type = _root_.com.histmersion.api.research.Article.Body
    type ApplyOneResultPart1 = ApplyOneResultPart1.T
    object ApplyOneResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApplyOneText = _root_.com.histmersion.api.research.Article.Body
    val ApplyOneText: _root_.com.histmersion.api.research.Article.Body.type = _root_.com.histmersion.api.research.Article.Body
    type ApplyAllRefuseResult = ApplyAllRefuseResult.T
    object ApplyAllRefuseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    enum Op(val wire: Op.Wire):
      case Replace extends Op(_root_.com.histmersion.api.research.Article.Edits.Op.Wire("replace"))

      case InsertAfter extends Op(_root_.com.histmersion.api.research.Article.Edits.Op.Wire("insertAfter"))

      case InsertBefore extends Op(_root_.com.histmersion.api.research.Article.Edits.Op.Wire("insertBefore"))

      case Delete extends Op(_root_.com.histmersion.api.research.Article.Edits.Op.Wire("delete"))

    object Op:
      type Wire = Wire.T
      object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


      def parse(s: com.histmersion.api.research.Article.Edits.Op.Wire): Option[Op] = values.find((_.wire).value.equalsIgnoreCase((s).value.trim))
      given WireEnumValues[Op]:
        def all: Array[Op] = Op.values
        def wire(e: Op): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((e.wire).value)
        def fromWire(s: _root_.com.histmersion.api.WireEnumValues.Wire): _root_.scala.Option[Op] = parse(_root_.com.histmersion.api.research.Article.Edits.Op.Wire((s).value))
      given Encoder[Op] = WireEnum.encoder
      given Decoder[Op] = WireEnum.decoder
      given Schema[Op] = WireEnum.schema

    final case class EditAnswer(op: Op, anchor: EditAnswer.Anchor, text: Option[EditAnswer.Text] = None) derives KindlingsCodecAsObject
    object EditAnswer:

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
      def anchor: com.histmersion.api.research.Article.Edits.Anchor
      case Replace(anchor: Edit.Replace.Anchor, text: Edit.Replace.Text)
      case InsertAfter(anchor: Edit.InsertAfter.Anchor, text: Edit.InsertAfter.Text)
      case InsertBefore(anchor: Edit.InsertBefore.Anchor, text: Edit.InsertBefore.Text)
      case Delete(anchor: Edit.Delete.Anchor)






    /** A replace whose text is empty or null is not read as a delete: a strict-schema provider sends `null` (or `""`)
      * for a field it left out.
      */
    def parse(a: EditAnswer): Either[com.histmersion.api.research.Article.Edits.ParseResultPart1, Edit] =
      val anchor = Option(a.anchor).getOrElse("")
      val text = Option(a.text).flatten.getOrElse("")
      if anchor.trim.isEmpty then Left(_root_.com.histmersion.api.research.Article.Edits.ParseResultPart1("no anchor — copy the passage the edit is about from the body"))
      else
        a.op match
          case Op.Delete                       => Right(Edit.Delete(anchor))
          case Op.Replace if text.trim.isEmpty =>
            Left(_root_.com.histmersion.api.research.Article.Edits.ParseResultPart1("a replace with no new text — give the text, or use delete to remove the passage"))
          case Op.Replace             => Right(Edit.Replace(anchor, text))
          case _ if text.trim.isEmpty => Left(_root_.com.histmersion.api.research.Article.Edits.ParseResultPart1("no text to insert"))
          case Op.InsertAfter         => Right(Edit.InsertAfter(anchor, text))
          case Op.InsertBefore        => Right(Edit.InsertBefore(anchor, text))

    final case class EditResult(index: EditResult.Index, op: Op, anchor: EditResult.Anchor, text: EditResult.Text, problem: Option[EditResult.Problem] = None)
        derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema:
      def applied: Boolean = (problem.isEmpty)
      def label: String = (s"edit $index (${op.wire} \"$anchor\")")

    object EditResult:

      type Index = Index.T
      object Index extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Problem = Problem.T
      object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class Applied(body: com.histmersion.api.research.Article.Body, results: scala.List[EditResult]):
      def problems: scala.List[com.histmersion.api.research.Article.Edits.Applied.ProblemsResult] = results.flatMap(r => r.problem.map(p => _root_.com.histmersion.api.research.Article.Edits.Applied.ProblemsResult(s"${r.label}: $p")))
      def appliedCount: com.histmersion.api.research.Article.Edits.Applied.Count = _root_.com.histmersion.api.research.Article.Edits.Applied.Count(results.count((refactorCallback => ((refactorCallback.applied) == (true)))))

    object Applied:
      type Count = Count.T
      object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type ProblemsResult = ProblemsResult.T
      object ProblemsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def head(anchor: com.histmersion.api.research.Article.Edits.Anchor, max: com.histmersion.api.research.Article.Edits.HeadLimit = _root_.com.histmersion.api.research.Article.Edits.HeadLimit(48)): String =
      val one = (anchor).value.trim.replaceAll("\\s+", " ")
      if one.length <= (max).value then (one) else (one.take((max).value).trim + "…")

    private def squeeze(s: String): (String, Array[Int]) =
      val out = StringBuilder()
      val pos = Array.newBuilder[Int]
      var i = 0
      while i < (s).value.length do
        if s(i).isWhitespace then
          val from = i
          while i < (s).value.length && s(i).isWhitespace do i += 1
          out.append(' '); pos += from
        else
          out.append(s(i)); pos += i; i += 1
      ((out.toString), (pos.result()))

    private def occurrences(text: com.histmersion.api.research.Article.Edits.OccurrencesText, needle: String): scala.List[com.histmersion.api.research.Article.Edits.Occurrences] =
      (Iterator.iterate((text).value.indexOf(needle))(i => (text).value.indexOf(needle, i + 1)).takeWhile(_ >= 0).toList).map(refactorValue14 => _root_.com.histmersion.api.research.Article.Edits.Occurrences(refactorValue14))

    def locate(text: com.histmersion.api.research.Article.Edits.LocateText, anchor: com.histmersion.api.research.Article.Edits.Anchor): Either[com.histmersion.api.research.Article.Edits.LocateResultPart1, (com.histmersion.api.personal.UnreadPage.Offset, com.histmersion.api.personal.UnreadPage.Offset)] =
      val a = (anchor).value.trim
      occurrences(text, (a)) match
        case scala.List(i) => Right((_root_.com.histmersion.api.personal.UnreadPage.Offset((i).value), _root_.com.histmersion.api.personal.UnreadPage.Offset((i).value + a.length)))
        case _ :: _ :: _   =>
          Left(_root_.com.histmersion.api.research.Article.Edits.LocateResultPart1(s"the anchor occurs ${occurrences(text, (a)).size} times — quote more of the passage so it occurs once"))
        case Nil =>
          val (sq, pos) = squeeze(((text).value))
          val na = a.replaceAll("\\s+", " ")
          occurrences(_root_.com.histmersion.api.research.Article.Body((sq).value), (na)) match
            case scala.List(i) =>
              val last = (i).value + na.length - 1
              Right((pos(i), pos(last) + 1))
            case Nil =>
              Left(_root_.com.histmersion.api.research.Article.Edits.LocateResultPart1("the anchor is not in the article — copy it exactly from the current body (earlier edits included)"))
            case more => Left(_root_.com.histmersion.api.research.Article.Edits.LocateResultPart1(s"the anchor occurs ${more.size} times — quote more of the passage so it occurs once"))

    private def cut(text: com.histmersion.api.research.Article.Body, s: com.histmersion.api.personal.UnreadPage.Offset, e: com.histmersion.api.personal.UnreadPage.Offset): com.histmersion.api.research.Article.Body =
      val before = (text).value.substring(0, (s).value)
      val after = (text).value.substring((e).value)
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
      _root_.com.histmersion.api.research.Article.Body(head + joint + tail + (if tail.isEmpty then post.filter(_ == '\n').take(1) else ""))

    def applyOne(text: com.histmersion.api.research.Article.Edits.ApplyOneText, e: Edit): Either[com.histmersion.api.research.Article.Edits.ApplyOneResultPart1, com.histmersion.api.research.Article.Body] =
      locate(text, e.anchor).map { (s, end) =>
        e match
          case Edit.Replace(_, t)      => text.substring(0, s) + t + text.substring(end)
          case Edit.InsertAfter(_, t)  => text.substring(0, end) + t + text.substring(end)
          case Edit.InsertBefore(_, t) => text.substring(0, s) + t + text.substring(s)
          case Edit.Delete(_)          => cut(text, s, end)
      }

    def applyAll(
        body: com.histmersion.api.research.Article.Body,
        edits: scala.List[EditAnswer],
        refuse: EditAnswer => Option[com.histmersion.api.research.Article.Edits.ApplyAllRefuseResult] = _ => None
    ): Applied =
      @tailrec def loop(rest: scala.List[EditAnswer], number: Int, cur: com.histmersion.api.research.Article.Body, acc: Vector[EditResult]): Applied =
        rest match
          case Nil       => Applied(cur, acc.toList)
          case a :: tail =>
            val base = EditResult(number, a.op, head(Option(a.anchor).getOrElse("")), a.text.getOrElse(""))
            parse(a).flatMap(e => refuse(a).toLeft(e)).flatMap(applyOne(cur, _)) match
              case Right(t) => loop(tail, ((number).value + 1), t, acc :+ base)
              case Left(p)  => loop(tail, ((number).value + 1), cur, acc :+ base.copy(problem = Some(p)))
      loop(edits, (1), body, Vector.empty)

    enum Draft:
      case Edits(edits: scala.List[EditAnswer])
      case Body(text: Draft.Body.Text)


    final case class ArticleCheckRequest(
        edits: scala.List[EditAnswer] = Nil,
        body: Option[com.histmersion.api.research.Article.Body] = None,
        kind: Option[com.histmersion.api.research.Article.Kind.Wire] = None
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
        kind: com.histmersion.api.research.Article.Kind.Wire,
        edits: scala.List[EditResult],
        problems: scala.List[ArticleCheck.Problems],
        fatal: ArticleCheck.Fatal,
        wordsBefore: ArticleCheck.WordsBefore,
        wordsAfter: ArticleCheck.WordsAfter,
        body: com.histmersion.api.research.Article.Body
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema:
      def clean: Boolean = (problems.isEmpty)

    object ArticleCheck:
      type Problem = Problem.T
      object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type Fatal = Fatal.T
      object Fatal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type WordsBefore = WordsBefore.T
      object WordsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type WordsAfter = WordsAfter.T
      object WordsAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def markdown(c: ArticleCheck): com.histmersion.api.research.Article.Edits.Markdown =
      val edits =
        if c.edits.isEmpty then ""
        else "\n\n### Edits\n\n" + c.edits.map(r => s"- ${r.label}: ${r.problem.getOrElse("applies")}").mkString("\n")
      val verdict =
        if ((c.clean) == (true)) then "No problems: these are the checks code runs on the answer, and it would pass them."
        else if ((c.fatal) == _root_.com.histmersion.api.research.Article.Edits.ArticleCheck.Fatal(true)) then s"${Plural.count(_root_.com.histmersion.api.Plural.CountN((c.problems.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("problem"))}, at least one FATAL (never written):"
        else s"${Plural.count(_root_.com.histmersion.api.Plural.CountN((c.problems.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("problem"))}:"
      val problems = if ((c.clean) == (true)) then "" else "\n\n" + c.problems.map(p => s"- $p").mkString("\n")
      _root_.com.histmersion.api.research.Article.Edits.Markdown(s"## Check of [[${c.slug}]] (${c.kind})\n\n${c.wordsBefore} → ${c.wordsAfter} words.$edits\n\n### Result\n\n$verdict$problems")

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
    def isEmpty: Boolean =
      (query.forall((_).value.trim.isEmpty) && topics.isEmpty && tags.isEmpty && status.isEmpty && places.isEmpty &&
        authors.isEmpty && from.isEmpty && to.isEmpty)

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

    def line: String =
      def list(xs: scala.List[String]) = xs.map(Filter.quoted).mkString("[", ", ", "]")
      val parts = query.map(q => s"query: ${Filter.quoted(((q).value))}").toList ++
        Option.when(topics.nonEmpty)(s"topics: ${list(topics)}") ++
        Option.when(tags.nonEmpty)(s"tags: ${list(tags)}") ++
        Option.when(status.nonEmpty)(s"status: ${list(status)}") ++
        Option.when(places.nonEmpty)(s"places: ${list(places)}") ++
        Option.when(authors.nonEmpty)(s"authors: ${list(authors)}") ++
        from.map(f => s"from: $f") ++ to.map(t => s"to: $t")
      s"search {${parts.mkString(", ")}}"

  object Filter:
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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

    private[api] def quoted(s: String): String = ("\"" + (s).value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")

    def normalise(f: Filter): Either[com.histmersion.api.research.Article.Filter.Problem, Option[Filter]] =
      def clean(xs: scala.List[String]) = xs.flatMap((_).value.split(",")).map(_.trim).filter(_.nonEmpty).distinct
      val status = clean(f.status).map(_.toLowerCase(java.util.Locale.ROOT))
      val unknown = status.filterNot(s => Source.Status.values.exists(_.wire == s))
      val tidy = Filter(
        f.query.map((_).value.trim).filter((_).value.nonEmpty),
        clean(f.topics),
        clean(f.tags),
        status,
        clean(f.places),
        clean(f.authors),
        f.from,
        f.to,
        (f.extraQueries.map((_).value.trim).filter(_.nonEmpty).distinct).map(refactorValue19 => _root_.com.histmersion.api.research.Article.Filter.ExtraQueries(refactorValue19))
      )
      if unknown.nonEmpty then
        Left(
          s"unknown status ${unknown.map(s => s"'$s'").mkString(", ")} — one of ${Source.Status.values.map(_.wire).mkString(", ")}"
        )
      else if tidy.from.zip(tidy.to).exists(_ > _) then
        Left(_root_.com.histmersion.api.research.Article.Filter.Problem(s"the filter's from (${tidy.from.get}) is after its to (${tidy.to.get})"))
      else if tidy.isEmpty && tidy.extraQueries.nonEmpty then
        Left(_root_.com.histmersion.api.research.Article.Filter.Problem("extraQueries without a filter: give the query, topics or tags the article is compiled from"))
      else Right(Option.when(!tidy.isEmpty)(tidy))

  final case class Import(
      text: Import.Text,
      slug: Option[com.histmersion.api.research.Article.Slug] = None,
      kind: Option[com.histmersion.api.research.Article.Kind.Wire] = None,
      mtime: Option[Import.Mtime] = None,
      force: Import.Force = _root_.com.histmersion.api.research.Article.Import.Force(false),
      strict: Import.Strict = _root_.com.histmersion.api.research.Article.Import.Strict(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Import:
    type Mtime = Mtime.T
    object Mtime extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Force = Force.T
    object Force extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Strict = Strict.T
    object Strict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  enum Kind:
    case Concept, Myth, Culture, Region, Timeline, Report, Index

  object Kind:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def fromString(s: com.histmersion.api.research.Article.Kind.Wire): Option[Kind] =
      values.find(_.toString.equalsIgnoreCase((s).value))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((_root_.com.histmersion.api.research.Article.Kind.Wire(e.toString.toLowerCase)).value)
      def fromWire(s: _root_.com.histmersion.api.WireEnumValues.Wire): _root_.scala.Option[Kind] =
        Kind.values.find(_.toString.equalsIgnoreCase((s).value))

    given Encoder[Kind] = WireEnum.encoder
    given Decoder[Kind] = WireEnum.decoder
    given Schema[Kind] = WireEnum.schema

  final case class KindCount(kind: com.histmersion.api.research.Article.Kind.Wire, count: KindCount.Count) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

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
    type Markdown = Markdown.T
    object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ResolveMarkdownMd = _root_.com.histmersion.api.research.Article.Body
    val ResolveMarkdownMd: _root_.com.histmersion.api.research.Article.Body.type = _root_.com.histmersion.api.research.Article.Body

    val TargetArticle = "article"
    val TargetSource = "source"

    export com.histmersion.api.text.WikiLinks.{isCitekeyShaped, normalize, occurrences, targets, Occurrence}

    def classify(targets: scala.List[com.histmersion.api.research.Article.Link.Target], isSource: com.histmersion.api.research.Article.Link.Target => Boolean): scala.List[Link] =
      targets.map { t =>
        if isCitekeyShaped(t) && isSource(t) then Link(_root_.com.histmersion.api.research.Article.Link.TargetKind(TargetSource), t)
        else Link(_root_.com.histmersion.api.research.Article.Link.TargetKind(TargetArticle), t)
      }

    def links(body: com.histmersion.api.research.Article.Body, isSource: com.histmersion.api.research.Article.Link.Target => Boolean): scala.List[Link] =
      classify(targets(body), { val refactorFunction2 = isSource; (refactorValue11) => ((refactorFunction2(_root_.com.histmersion.api.research.Article.Link.Target((refactorValue11).value))).value) })

    def hrefFor(target: com.histmersion.api.research.Article.Link.Target, citekeyOf: com.histmersion.api.research.Article.Link.Target => Option[com.histmersion.api.library.Citekey], isArticle: com.histmersion.api.research.Article.Link.Target => Boolean): Option[String] =
      (citekeyOf(_root_.com.histmersion.api.research.Article.Link.Target((target).value))
        .map(ck => s"/source/$ck")
        .orElse(Option.when(((isArticle(_root_.com.histmersion.api.research.Article.Link.Target((target).value))) == (true)))(s"/article/$target"))).map(refactorValue28 => (refactorValue28))

    def linkMarkdown(
        md: com.histmersion.api.research.Article.Body,
        href: String => Option[String],
        displayLabel: String => Option[String] = _ => None
    ): String =
      rewriteLinks(md) { (t, explicit, written) =>
        val label = explicit.getOrElse(written)
        val text = if label.isEmpty then displayLabel(t).getOrElse(Label.display(t)) else label
        href(t) match
          case Some(h) => s"[$text]($h)"
          case None    => s"""<span class="dangling" title="unresolved link">$text</span>"""
      }

    export com.histmersion.api.text.WikiLinks.{retarget, rewriteLinks}

    def resolveMarkdown(md: com.histmersion.api.research.Article.Links.ResolveMarkdownMd, isSource: com.histmersion.api.research.Article.Link.Target => Boolean): com.histmersion.api.research.Article.Links.Markdown =
      linkMarkdown(md, t => Some(if isCitekeyShaped(t) && isSource(t) then (s"/source/$t") else (s"/article/$t")))

  /** The articles' lint rules: a thin or stand-in summary, filler phrases, uncited paragraphs, unpaired critiques. */
  object Lint:
    type FillerPhrasesText = FillerPhrasesText.T
    object FillerPhrasesText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type FillerPhrases = FillerPhrasesValue

    val FillerPhrases: scala.List[com.histmersion.api.research.Article.Lint.FillerPhrases] = scala.List(
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("it is worth noting"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("it should be noted"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("it is important to note"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("interestingly"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("notably"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("significantly"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("it is clear that"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("needless to say"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("of course"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("as is well known"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("it goes without saying"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("in conclusion"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("to summarize"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("in summary"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("broadly speaking"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("generally speaking"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("it must be emphasized"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("it cannot be overstated"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("plays a crucial role"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("rich tapestry"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("sheds light on"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("shed new light"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("paved the way"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("remains to be seen"),
      _root_.com.histmersion.api.research.Article.Lint.FillerPhrasesText("further research is needed")
    )

    private[api] val FillerPattern: scala.util.matching.Regex =
      FillerPhrases.map(p => java.util.regex.Pattern.quote((p).value)).mkString("(?i)(?:", "|", ")").r

    def articleThinSummary(rows: scala.List[(com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.research.Article.SummaryText)]): scala.List[LintItem] =
      rows
        .flatMap { (slug, summary) =>
          val s = (summary).value.trim
          val why =
            if s.isEmpty then Some("no summary")
            else
              Articles
                .summaryOf(Some(_root_.com.histmersion.api.research.Article.SummaryText(s)))
                .left
                .toOption
                .map(_ => s"stand-in summary: \"$s\"")
                .orElse(Option.when(s.length < MinSummaryChars)(s"only ${s.length} chars: \"$s\""))
          why.map(LintItem(slug, _root_.com.histmersion.api.checks.Lint.LintItem.Detail(_)))
        }
        .sortBy(_.citekey)

    def articleFiller(articles: scala.List[(com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.research.Article.Body)]): scala.List[LintItem] =
      articles
        .flatMap { (slug, body) =>
          val matches = FillerPattern.findAllMatchIn((body).value).map(_.matched.toLowerCase).toList.distinct
          if matches.isEmpty then None
          else
            Some(
              LintItem(
                slug,
                _root_.com.histmersion.api.checks.Lint.LintItem.Detail(s"filler: ${matches.take(3).mkString(", ")}${
                    if matches.sizeIs > 3 then s" (+${matches.size - 3} more)" else ""
                  }")
              )
            )
        }
        .sortBy(_.citekey)

    private[api] val WikilinkCitekey =
      """\[\[((?=[a-z0-9_]*(?:_|\d{4}))[a-z][a-z0-9_]*)""".r

    def articleUncitedParagraphs(articles: scala.List[(com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.research.Article.Body)]): scala.List[LintItem] =
      articles
        .flatMap { (slug, body) =>
          val paras = extractParagraphs(body)
          val uncited = paras.filter(p => WikilinkCitekey.findFirstIn(p).isEmpty)
          if uncited.isEmpty then None
          else Some(LintItem(slug, s"${uncited.size} paragraph(s) with no [[citekey]]: \"${uncited.head.take(60)}…\""))
        }
        .sortBy(_.citekey)

    def articleCritiquePairing(
        articles: scala.List[(com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.research.Article.Body)],
        resolve: com.histmersion.api.library.Citekey => Option[com.histmersion.api.library.Citekey],
        edges: scala.List[com.histmersion.api.research.Coverage.Edge],
        work: com.histmersion.api.library.Citekey => Option[CritiquePairing.Work]
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
    type Total = Total.T
    object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def of(total: com.histmersion.api.research.Article.List.Total, rows: scala.List[Summary], offset: com.histmersion.api.personal.UnreadPage.Offset, kinds: scala.List[KindCount] = Nil): List =
      List(com.histmersion.api.RowCount((total).value), rows, com.histmersion.api.Offset((offset).value), OffsetPage(com.histmersion.api.RowCount((total).value), com.histmersion.api.Offset((offset).value), com.histmersion.api.RowCount(rows.size)).next, kinds)

    def byKind(counts: scala.List[KindCount]): scala.List[KindCount] =
      counts.filter((_.count).value > 0).sortBy(k => (-(k.count).value, k.kind))

  final case class Patch(
      title: Option[com.histmersion.api.research.Article.Title] = None,
      summary: Option[com.histmersion.api.research.Article.SummaryText] = None,
      body: Option[com.histmersion.api.research.Article.Body] = None,
      append: Option[Patch.Append] = None,
      section: Option[Patch.Section] = None,
      tags: Option[ListPatch] = None,
      regions: Option[scala.List[Patch.Regions]] = None,
      period: Option[Period.Input] = None,
      kind: Option[com.histmersion.api.research.Article.Kind.Wire] = None,
      ifUpdatedAt: Option[Patch.IfUpdatedAt] = None,
      filter: Option[Filter] = None,
      reasons: scala.List[com.histmersion.api.research.Coverage.Reason] = Nil,
      critiqueReasons: scala.List[CritiqueReason] = Nil
  ) derives KindlingsSchema, com.histmersion.api.json.Schema:
    def onlyCritiques: Boolean =
      (critiqueReasons.nonEmpty && this.modify(_.critiqueReasons).setTo(Nil).modify(_.ifUpdatedAt).setTo(None) == Patch())
  object Patch:



    type Append = Append.T
    object Append extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Section = Section.T
    object Section extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  object Plans:
    type Markdown = Markdown.T
    object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class FilterProposal(
        slug: com.histmersion.api.research.Article.Slug,
        filter: Filter,
        rationale: FilterProposal.Rationale = _root_.com.histmersion.api.research.Article.Plans.FilterProposal.Rationale(""),
        total: FilterProposal.Total = _root_.com.histmersion.api.research.Article.Plans.FilterProposal.Total(0),
        cited: FilterProposal.Cited = _root_.com.histmersion.api.research.Article.Plans.FilterProposal.Cited(0),
        citedIn: FilterProposal.CitedIn = _root_.com.histmersion.api.research.Article.Plans.FilterProposal.CitedIn(0),
        missed: scala.List[FilterProposal.Missed] = Nil,
        sample: scala.List[FilterProposal.Sample] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema:
      def recall: com.histmersion.api.research.Article.Plans.FilterProposal.Recall = if cited == _root_.com.histmersion.api.research.Article.Plans.FilterProposal.Cited(0) then _root_.com.histmersion.api.research.Article.Plans.FilterProposal.Recall(1.0) else _root_.com.histmersion.api.research.Article.Plans.FilterProposal.Recall((citedIn).value.toDouble / (cited).value)

    object FilterProposal:
      type Recall = Recall.T
      object Recall extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
        kind: com.histmersion.api.research.Article.Kind.Wire,
        filter: Filter,
        why: ProposedArticle.Why = _root_.com.histmersion.api.research.Article.Plans.ProposedArticle.Why(""),
        hits: ProposedArticle.Hits = _root_.com.histmersion.api.research.Article.Plans.ProposedArticle.Hits(0),
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
        rationale: ArticlePlan.Rationale = _root_.com.histmersion.api.research.Article.Plans.ArticlePlan.Rationale(""),
        articles: scala.List[ProposedArticle] = Nil,
        dropped: scala.List[ArticlePlan.Dropped] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema:
      def isEmpty: Boolean = (articles.isEmpty)

    object ArticlePlan:

      type Rationale = Rationale.T
      object Rationale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Dropped = Dropped.T
      object Dropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def filterLine(f: Filter): String = f.line

    def filterBlock(p: FilterProposal): com.histmersion.api.research.Article.Plans.Markdown =
      val missed =
        if p.missed.isEmpty then "" else s"\n- cited but not returned: ${p.missed.map(c => s"[[$c]]").mkString(", ")}"
      val sample = if p.sample.isEmpty then ""
      else s"\n- returned, not cited (a few): ${p.sample.map(c => s"[[$c]]").mkString(", ")}"
      s"${p.filter.line} → ${Plural.count(p.total, _root_.com.histmersion.api.Plural.CountSingular("source"))}; returns ${p.citedIn} of the ${Plural.count(p.cited, _root_.com.histmersion.api.Plural.CountSingular("cited source"))}" +
        missed + sample

    def planBlock(p: ArticlePlan): com.histmersion.api.research.Article.Plans.Markdown =
      val as = p.articles.map { a =>
        val ov = a.overlapWith.fold("")(o => f" · overlaps [[$o]] ${(a.overlap).value * 100}%.0f%%")
        s"- `${a.slug}` — ${a.title} (${a.kind}) · ${a.filter.line} → ${Plural.count(a.hits, _root_.com.histmersion.api.Plural.CountSingular("source"))}$ov" +
          Option(a.why).filter(_.nonEmpty).fold("")(w => s": $w")
      }
      val dropped = p.dropped.map(d => s"- dropped: $d")
      (Option(p.rationale).filter(_.nonEmpty).toList ++ scala.List((as ++ dropped).mkString("\n")))
        .filter(_.nonEmpty)
        .mkString("\n\n")

  final case class Put(
      title: com.histmersion.api.research.Article.Title,
      kind: com.histmersion.api.research.Article.Kind.Wire,
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
    type Markdown = Markdown.T
    object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



    type Outcome = Outcome.T
    object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def reviewerName(wire: com.histmersion.api.research.ReviewFinding.Reviewer.Wire): String = wire match
      case refactorPattern if (refactorPattern.value == "rules") && (refactorPattern5.value == "rules")       => ("rules reviewer")
      case refactorPattern2 if (refactorPattern2.value == "cold-reader") && (refactorPattern6.value == "cold-reader") => ("cold reader")
      case refactorPattern7 if (refactorPattern3.value == "skeptic") && (refactorPattern7.value == "skeptic")     => ("skeptic")
      case refactorPattern4 if (refactorPattern4.value == "mechanical") && (refactorPattern8.value == "mechanical")  => ("mechanical check")
      case other         => ((other).value)

    def outcomeLine(f: ReviewFinding): String =
      val base = f.outcome match
        case "wont-fix"           => "won't fix"
        case "set-aside"          => "set aside"
        case "open" if ((f.deferred) == _root_.com.histmersion.api.research.ReviewFinding.Deferred(true)) => "open (left for the next run)"
        case other                => other
      val when = f.closedRunNumber.zip(f.closedRound).fold("")((run, round) => s" in run $run, round $round")
      val by = f.closedBy.filter(_.nonEmpty).fold("")(b => s" by ${reviewerName(b)}")
      val note = Option(f.note).map(_.trim).filter(_.nonEmpty).fold("")(n => s": $n")
      val back = if (f.reopened).value > 0 then s" (reopened ${Plural.count(f.reopened, _root_.com.histmersion.api.Plural.CountSingular("time"))})" else ""
      (s"$base$by$when$back$note")

    def markdown(r: ReviewView): com.histmersion.api.research.Article.ReviewView.Markdown =
      val head = s"## Review of [[${r.slug}]]\n\n**${r.badge.label}** — ${r.badge.detail}" +
        r.outcome.filter((_).value.nonEmpty).fold("")(o => s"\n\nReview manager: $o" + r.updatedAt.fold("")(t => s" ($t)"))
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
      _root_.com.histmersion.api.research.Article.ReviewView.Markdown(head + goals + approvals + history)

  final case class Row(
      slug: com.histmersion.api.research.Article.Slug,
      title: com.histmersion.api.research.Article.Title,
      kind: com.histmersion.api.research.Article.Kind.Wire,
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
    type PeriodStart = PeriodStart.T
    object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodEnd = PeriodEnd.T
    object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Summary(
      slug: com.histmersion.api.research.Article.Slug,
      title: com.histmersion.api.research.Article.Title,
      kind: com.histmersion.api.research.Article.Kind.Wire,
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
    type PeriodStart = PeriodStart.T
    object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodEnd = PeriodEnd.T
    object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]


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



  final case class View(
      article: Row,
      backlinks: scala.List[Backlink],
      sourcesCited: scala.List[CitedSource],
      formatting: scala.List[FormatIssue] = Nil,
      coverage: Option[com.histmersion.api.research.Coverage] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
