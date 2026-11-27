package com.histmersion
package api.research
import com.histmersion.util.nonempty.NonEmptyVector
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.Newtype
import com.histmersion.api.research.Coverage.{FilterLine, Rationale}
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
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import scala.annotation.tailrec
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.text.WikiLinks
import com.histmersion.api.RowCount
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.automation.Creation
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.GeoPick
import com.histmersion.api.automation.Goal
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
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
    reasons: Map[Citekey, Coverage.Rationale] = Map.empty
)

object Article:
  type Export = Export.T
  object Export extends Newtype[String, Pure]
  type FullText = FullText.T
  object FullText extends Newtype[Boolean, Pure]
  type Heading = Heading.T
  object Heading extends Newtype[String, Pure]
  type Lede = Lede.T
  object Lede extends Newtype[String, Pure]
  type Part = Part.T
  object Part extends Newtype[String, Pure]
  type Period = Period.T
  object Period extends Newtype[String, Pure]
  type Prose = Prose.T
  object Prose extends Newtype[String, Pure]
  export Goal.Plans.ArticleDraft.Section
  type VersionId = VersionId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object VersionId extends Newtype[Long, Pure]

  type Slug = Slug.T
  object Slug extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  export Kind.Wire as KindValue
  export ArticleEventLead.Body
  type SummaryText = SummaryText.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object SummaryText extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type Regions = Regions.T
  object Regions extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]

  object Coverage:
    type DisagreementKind = DisagreementKind.T
    object DisagreementKind extends Newtype[String, Pure]
    type Export = Export.T
    object Export extends Newtype[String, Pure]
    type Markdown = Markdown.T
    object Markdown extends Newtype[String, Pure]
    type Method = Method.T
    object Method extends Newtype[String, Pure]
    type NotUsed = NotUsed.T
    object NotUsed extends Newtype[String, Pure]
    type Sections = Sections.T
    object Sections extends Newtype[String, Pure]

    type DisagreementKinds = DisagreementKindsValue
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type DisagreementKindsValue = DisagreementKind
    val DisagreementKindsValue: DisagreementKind.type = DisagreementKind
    export Article.Slug as ComputeSlug
    export api.research.Coverage.Total as ComputeTotal

    val DisagreementKinds: scala.List[DisagreementKinds] = scala.List(
      DisagreementKind("challenges"),
      DisagreementKind("refutes"),
      DisagreementKind("updates"),
      DisagreementKind("superseded_by")
    )

    private val Stated: Set[Source.Status] = Set(
      Source.Status.Contested,
      Source.Status.Superseded,
      Source.Status.Debunked,
      Source.Status.Retracted,
      Source.Status.Unassessed
    )

    private val Disputed: scala.List[Source.Status] =
      scala.List(Source.Status.Contested, Source.Status.Superseded, Source.Status.Debunked, Source.Status.Retracted)

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
    @tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
    def compute(
        slug: Coverage.ComputeSlug,
        filter: Filter,
        total: Coverage.ComputeTotal,
        hits: scala.List[api.research.Coverage.Source],
        citedInFilter: Set[Citekey],
        cited: scala.List[Citekey],
        reasons: Map[Citekey, Rationale],
        edges: scala.List[api.research.Coverage.Edge],
        searches: scala.List[CoverageSearch]
    ): Coverage =
      val citedSet = cited.toSet
      val read = hits.distinctBy(_.citekey).sortBy(_.citekey.value)
      val readSet = read.map(_.citekey).toSet
      val notUsed = read
        .filterNot(s => citedSet(s.citekey))
        .map(s => api.research.Coverage.Entry(s, reasons.get(s.citekey)))
      val capped = read.size < total.value
      val outside =
        cited.distinct.filterNot(ck => citedInFilter(ck) || readSet(ck))
      val statuses =
        Source.Status.values.toList.flatMap(st =>
          Option(read.count(_.status == st)).filter(_ > 0).map(n => StatusCount(st, StatusCount.Count(n)))
        )
      val keptEdges = edges
        .filter(e => DisagreementKinds.contains(e.kind) && (citedSet(e.from) || citedSet(e.to)))
        .distinct
        .sortBy(e => (e.from.value, e.kind.value, e.to.value))
      val drift = scala
        .List(
          api.research.Coverage
            .Drift(DriftKind.Unplaced, notUsed.filter(_.reason.isEmpty).map(_.source.citekey)),
          api.research.Coverage
            .Drift(DriftKind.ReasonForCited, reasons.keys.toList.filter(citedSet).sorted),
          api.research.Coverage.Drift(
            DriftKind.ReasonOutside,
            if capped then Nil else reasons.keys.toList.filterNot(ck => readSet(ck) || citedSet(ck)).sorted
          )
        )
        .filter(_.citekeys.nonEmpty)
      api.research.Coverage(
        slug,
        filter,
        filter.shownLine,
        total,
        api.research.Coverage.Checked(read.size),
        read.filter(s => citedSet(s.citekey)),
        notUsed,
        outside,
        statuses,
        keptEdges,
        searches,
        api.research.Coverage.Reason.list(reasons),
        drift
      )

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def unplacedAfter(
        c: Coverage,
        cited: Set[Citekey],
        reasons: Map[Citekey, Rationale]
    ): scala.List[Citekey] =
      c.notUsed.map(_.source.citekey).filterNot(ck => cited(ck) || reasons.contains(ck))

    /** HTML comments, so Obsidian and the page do not show them. */
    val GeneratedStart = "<!-- generated from the article's filter: edits here are not kept -->"
    val GeneratedEnd = "<!-- end of generated sections -->"

    val NotUsedHeading = "Not used"

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def sections(c: Coverage): Sections = Sections(s"${notUsed(c)}\n\n${method(c)}")

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def exported(c: Coverage): Export =
      Export(s"$GeneratedStart\n\n${sections(c)}\n\n$GeneratedEnd")

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def notUsed(c: Coverage): NotUsed =
      val src = Plural.noun(Plural.Count((c.total).value.toLong), Plural.Noun.Singular("source"))
      val citedN = c.cited.size
      val n = c.notUsed.size
      val scope = if (c.capped) == (true) then s"; the first ${c.checked} (by citekey) were checked" else ""
      val lead =
        if c.total == api.research.Coverage.Total(0) then "_The filter returns no source._"
        else if n == 0 then
          if (c.capped) == (true) then
            s"_The filter returns ${c.total} $src; all ${c.checked} checked are cited above._"
          else
            s"_${
                if c.total == api.research.Coverage.Total(1) then "The one source"
                else s"All ${c.total} sources"
              } the filter returns ${if c.total == api.research.Coverage.Total(1) then "is" else "are"} cited above._"
        else
          val citedText = if citedN == 0 then "None of them is"
          else
            Plural.count(
              Plural.CountN((citedN).toLong),
              Plural.CountSingular("of them is"),
              Plural.CountPlural("of them are")
            )
          s"The filter returns ${c.total} $src$scope. $citedText cited above; " +
            s"${if n == 1 then "the other one is" else s"the other $n are"} listed here with the reason ${
                if n == 1 then "it is" else "each is"
              } left out:"
      val groups = c.notUsed.groupBy(_.reason).toList.sortBy((r, es) => (r.isEmpty, es.map(_.source.citekey.value).min))
      val lines = groups.map { (reason, es) =>
        val who =
          es.sortBy(_.source.citekey.value)
            .map(e => s"[[${e.source.citekey}]]${statusNote(e.source.status)}")
            .mkString(", ")
        s"- $who — ${reason.fold("not yet assessed")(endSentence)}"
      }
      NotUsed(s"## $NotUsedHeading\n\n$lead" + (if lines.isEmpty then "" else "\n\n" + lines.mkString("\n")))

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def statusNote(s: Source.Status): String = if Stated(s) then (s" (${s.wire})") else ("")

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def endSentence(s: Rationale): String =
      val t = s"$s"
      if t.endsWith(".") || t.endsWith("?") || t.endsWith("!") then t else s"$t."

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def method(c: Coverage): Method =
      val statuses =
        if c.statuses.isEmpty then Nil
        else
          val have = c.statuses.map(s => s"${s.count} ${s.status.wire}").mkString(", ")
          val none = Disputed.filterNot(d => c.statuses.exists(_.status == d)).map(_.wire.value)
          val noneText = if none.isEmpty then "" else s"; none ${orList(none)}"
          scala.List(s"statuses${if (c.capped) == (true) then s" of the ${c.checked} checked" else ""}: $have$noneText")
      val searches = c.searches.map { s =>
        val cites =
          if s.cited.isEmpty then "none of them cited" else s"cited: ${s.cited.map(ck => s"[[$ck]]").mkString(", ")}"
        s"further search ${escape(Filter.quoted(s.query.value))} → ${s.total} ($cites)"
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
      val parts =
        s"${escape(c.filterLine.value)} → ${c.total}" :: statuses ++ searches ++ outside ++ scala.List(edges) ++
          Critiques.methodPart(c.critiques).toList
      Method(s"_Method: ${parts.mkString("; ")}._")

    private def orList(xs: scala.List[String]): String = xs match
      case Nil        => ("")
      case one :: Nil => ((one))
      case _          => (s"${xs.init.mkString(", ")} or ${xs.last}")

    /** An `_` or `*` of a user's query would end the Method line's Markdown emphasis. */
    private def escape(s: String): String = ((s).replace("*", "\\*").replace("_", "\\_"))

    private def isHeading(l: String) = (l).trim.startsWith("#")
    private def level(l: String) = (l).trim.takeWhile(_ == '#').length

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def withoutNotUsed(text: Body): Body =
      val lines = (text).value.split("\n", -1).toList
      lines.indexWhere(l =>
        isHeading((l)) && l.trim.replaceAll("^#+\\s*", "").trim.toLowerCase.startsWith("not used")
      ) match
        case -1 => Body((text).value)
        case i  =>
          val lv = level((lines(i)))
          val rest = lines.drop(i + 1)
          val end = rest.indexWhere(l => isHeading((l)) && level((l)) <= lv)
          Body((lines.take(i) ++ (if end < 0 then Nil else rest.drop(end))).mkString("\n"))

    def hasMethodLine(text: String): Boolean = ((text).linesIterator.exists(_.trim.startsWith("_Method")))

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def stripGenerated(body: Body): Body =
      val s = (body).value.indexOf(GeneratedStart)
      if s < 0 then Body((body).value)
      else
        val e = (body).value.indexOf(GeneratedEnd, s)
        val tail = if e < 0 then "" else (body).value.substring(e + GeneratedEnd.length)
        Body(((body).value.substring(0, s).stripTrailing + (if tail.trim.isEmpty then ""
                                                            else "\n\n" + tail.dropWhile(_.isWhitespace))).trim)

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown(c: Coverage): Markdown =
      val drift =
        if c.drift.isEmpty then "No drift: every source the filter returns is cited or has a reason."
        else
          c.drift
            .map { d =>
              val what = d.kind match
                case DriftKind.Unplaced       => "neither cited nor given a reason"
                case DriftKind.ReasonForCited => "given a reason but cited in the body (the reason is stale)"
                case DriftKind.ReasonOutside  => "given a reason but no longer returned by the filter"
              s"- ${Plural.count(Plural.Count((d.citekeys.size).toLong), Plural.Noun.Singular("source"))} $what: ${d.citekeys.map(ck => s"[[$ck]]").mkString(", ")}"
            }
            .mkString("\n")
      val critiques = Critiques.markdown(c.critiques)
      Markdown(
        s"## Coverage of [[${c.slug}]]\n\n`${c.filterLine}` → ${c.total} (${c.cited.size} cited, ${c.notUsed.size} not used" +
          s"${
              if (c.capped) == (true) then s", ${(c.total).value - (c.checked).value} not checked" else ""
            })\n\n### Drift\n\n$drift" +
          (if critiques.value.isEmpty then ""
           else s"\n\n$critiques") + s"\n\n### Generated sections\n\n${sections(c)}" +
          (if c.eventLeads.isEmpty then "" else "\n\n" + ArticleEventLead.markdown(c.eventLeads))
      )

  final case class Deleted(slug: Slug, deleted: Deleted.Value) derives JsonCodec, KindlingsSchema, api.json.Schema

  object Deleted:
    type Value = Value.T
    object Value extends Newtype[Boolean, Pure]

  object Edits:
    type Anchor = Anchor.T
    object Anchor extends Newtype[String, Pure]
    type HeadLimit = HeadLimit.T
    object HeadLimit extends Newtype[Int, Pure]
    type Markdown = Markdown.T
    object Markdown extends Newtype[String, Pure]
    type NewText = NewText.T
    object NewText extends Newtype[String, Pure]
    type Offset = Offset.T
    object Offset extends Newtype[Int, Pure]

    export Article.{Body as ApplyOneText, Body as LocateText, Body as OccurrencesText}

    enum Op(val wire: Op.Wire):
      case Replace extends Op(Op.Wire("replace"))

      case InsertAfter extends Op(Op.Wire("insertAfter"))

      case InsertBefore extends Op(Op.Wire("insertBefore"))

      case Delete extends Op(Op.Wire("delete"))

    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    object Op:
      export GeoPick.Wire

      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def parse(s: Wire): Option[Op] = values.find(_.wire.value.equalsIgnoreCase(s.value.trim))
      given WireEnumValues[Op]:
        def all: Array[Op] = values
        @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
        def wire(e: Op): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
        @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
        def fromWire(s: WireEnumValues.Wire): Option[Op] = parse(Wire((s).value))
      given JsonValueCodec[Op] = WireEnum.codec
      given Schema[Op] = WireEnum.schema

    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    final case class EditAnswer(op: Op, anchor: EditAnswer.Anchor, text: Option[EditAnswer.Text] = None)
        derives JsonCodec
    object EditAnswer:
      export Edits.{Anchor, NewText as Text}

      @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
      @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
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
      def anchor: Anchor
      case Replace(anchor: Replace.Anchor, text: Replace.Text)
      case InsertAfter(anchor: InsertAfter.Anchor, text: InsertAfter.Text)
      case InsertBefore(anchor: InsertBefore.Anchor, text: InsertBefore.Text)
      case Delete(anchor: Delete.Anchor)

    object Edit:
      object Replace:
        export Edits.{Anchor, NewText as Text}

      object InsertAfter:
        export Edits.{Anchor, NewText as Text}

      object InsertBefore:
        export Edits.{Anchor, NewText as Text}

      object Delete:
        export Edits.Anchor

    /** A replace whose text is empty or null is not read as a delete: a strict-schema provider sends `null` (or `""`)
      * for a field it left out.
      */
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(a: EditAnswer): Either[Problem, Edit] =
      val anchor = Option(a.anchor).getOrElse(Anchor(""))
      val text = Option(a.text).flatten.getOrElse(NewText(""))
      if anchor.value.trim.isEmpty then Left(Problem.Invalid.Missing.here(Problem.Field("anchor")))
      else
        a.op match
          case Op.Delete                             => Right(Edit.Delete(anchor))
          case Op.Replace if text.value.trim.isEmpty =>
            Left(Problem.Content.Proposal(Problem.Content.Requirement.ReplaceText, Problem.Where("text")))
          case Op.Replace                   => Right(Edit.Replace(anchor, text))
          case _ if text.value.trim.isEmpty => Left(Problem.Invalid.Missing.here(Problem.Field("text")))
          case Op.InsertAfter               => Right(Edit.InsertAfter(anchor, text))
          case Op.InsertBefore              => Right(Edit.InsertBefore(anchor, text))

    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    final case class EditResult(
        index: EditResult.Index,
        op: Op,
        anchor: EditResult.Anchor,
        text: EditResult.Text,
        problem: Option[Problem] = None
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema:
      def applied: Boolean = (problem.isEmpty)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def label: String = (s"edit $index (${op.wire} \"$anchor\")")

    object EditResult:

      type Index = Index.T
      object Index extends Newtype[Int, Pure]
      export Edits.{Anchor, NewText as Text}

    final case class Applied(body: Body, results: scala.List[EditResult]):
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def problems: scala.List[Applied.Issue] =
        results.flatMap(r => r.problem.map(p => Applied.Issue(s"${r.label}: ${p.sentence}")))
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def appliedCount: Applied.Count =
        Applied.Count(results.count((refactorCallback => ((refactorCallback.applied) == (true)))))

    object Applied:
      type Count = Count.T
      object Count extends Newtype[Int, Pure]
      type Issue = Issue.T
      object Issue extends Newtype[String, Pure]

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def head(anchor: Anchor, max: HeadLimit = HeadLimit(48)): String =
      val one = (anchor).value.trim.replaceAll("\\s+", " ")
      if one.length <= (max).value then (one) else (one.take((max).value).trim + "…")

    private def squeeze(s: String): (String, Array[Int]) =
      val out = StringBuilder()
      val pos = Array.newBuilder[Int]
      var i = 0
      while i < (s).length do
        if s(i).isWhitespace then
          val from = i
          while i < (s).length && s(i).isWhitespace do i += 1
          out.append(' '); pos += from
        else
          out.append(s(i)); pos += i; i += 1
      ((out.toString), (pos.result()))

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
    private def occurrences(text: Edits.OccurrencesText, needle: String): scala.List[Offset] =
      (Iterator
        .iterate((text).value.indexOf(needle))(i => (text).value.indexOf(needle, i + 1))
        .takeWhile(_ >= 0)
        .toList)
        .map(refactorValue14 => Offset(refactorValue14))

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
    def locate(text: Edits.LocateText, anchor: Anchor): Either[Problem, (Offset, Offset)] =
      val a = (anchor).value.trim
      occurrences(text, (a)) match
        case scala.List(i) => Right((i, Offset((i).value + a.length)))
        case _ :: _ :: _   =>
          Left(
            Problem.Invalid.Ambiguous.here(
              Problem.TypeName.of(Anchor),
              Problem.Raw(s"$a"),
              Problem.Count(occurrences(text, (a)).size.toLong)
            )
          )
        case Nil =>
          val (sq, pos) = squeeze(((text).value))
          val na = a.replaceAll("\\s+", " ")
          occurrences(Body((sq)), (na)) match
            case scala.List(i) =>
              val last = (i).value + na.length - 1
              Right((Offset(pos((i).value)), Offset(pos(last) + 1)))
            case Nil =>
              Left(Problem.Content.Proposal(Problem.Content.Requirement.AnchorInArticle, Problem.Where("anchor")))
            case more =>
              Left(
                Problem.Invalid.Ambiguous
                  .here(Problem.TypeName.of(Anchor), Problem.Raw(s"$a"), Problem.Count(more.size.toLong))
              )

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def cut(text: Body, s: Offset, e: Offset): Body =
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
      Body(head + joint + tail + (if tail.isEmpty then post.filter(_ == '\n').take(1) else ""))

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
    def applyOne(text: Edits.ApplyOneText, e: Edit): Either[Problem, Body] =
      locate(text, e.anchor).map { (s, end) =>
        e match
          case Edit.Replace(_, t) => Body(text.value.substring(0, s.value) + t.value + text.value.substring(end.value))
          case Edit.InsertAfter(_, t) =>
            Body(text.value.substring(0, end.value) + t.value + text.value.substring(end.value))
          case Edit.InsertBefore(_, t) =>
            Body(text.value.substring(0, s.value) + t.value + text.value.substring(s.value))
          case Edit.Delete(_) => cut(text, s, end)
      }

    @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def applyAll(
        body: Body,
        edits: scala.List[EditAnswer],
        refuse: EditAnswer => Option[Problem] = _ => None
    ): Applied =
      @tailrec def loop(rest: scala.List[EditAnswer], number: Int, cur: Body, acc: Vector[EditResult]): Applied =
        rest match
          case Nil       => Applied(cur, acc.toList)
          case a :: tail =>
            val base = EditResult(
              EditResult.Index(number),
              a.op,
              EditResult.Anchor(head(Option(a.anchor).getOrElse(Anchor("")))),
              a.text.getOrElse(NewText(""))
            )
            parse(a).flatMap(e => refuse(a).toLeft(e)).flatMap(applyOne(cur, _)) match
              case Right(t) => loop(tail, ((number) + 1), t, acc :+ base)
              case Left(p)  => loop(tail, ((number) + 1), cur, acc :+ base.copy(problem = Some(p)))
      loop(edits, (1), body, Vector.empty)

    enum Draft:
      case Edits(edits: scala.List[EditAnswer])
      case Body(text: Draft.Body.Text)

    object Draft:
      object Body:
        export Article.Body as Text

    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    final case class ArticleCheckRequest(
        edits: scala.List[EditAnswer] = Nil,
        body: Option[Body] = None,
        kind: Option[Kind.Wire] = None
    ) derives JsonCodec:
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def draft: Either[Problem, Draft] =
        (edits, body.filter(_.value.trim.nonEmpty)) match
          case (Nil, None) =>
            Left(
              Problem.Invalid.Inconsistent
                .here(NonEmptyVector.of(Problem.Field("edits"), Problem.Field("body")), Problem.Invalid.Exclusion.OneOf)
            )
          case (_ :: _, Some(_)) =>
            Left(
              Problem.Invalid.Inconsistent
                .here(NonEmptyVector.of(Problem.Field("edits"), Problem.Field("body")), Problem.Invalid.Exclusion.OneOf)
            )
          case (es, None)     => Right(Draft.Edits(es))
          case (Nil, Some(b)) => Right(Draft.Body(b))
    object ArticleCheckRequest:
      @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
      @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
      given Schema[ArticleCheckRequest] = KindlingsSchema
        .derived[ArticleCheckRequest]
        .schema
        .modify(_.edits)(_.description("Edits to the CURRENT stored body, applied in order"))
        .modify(_.body)(_.description("Instead of edits: a whole draft body to check"))
        .modify(_.kind)(
          _.description("Check the section set of this kind instead of the article's (index = none, for a fragment)")
        )

    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    final case class ArticleCheck(
        slug: Slug,
        kind: Kind.Wire,
        edits: scala.List[EditResult],
        problems: scala.List[Problem],
        fatal: ArticleCheck.Fatal,
        wordsBefore: ArticleCheck.WordsBefore,
        wordsAfter: ArticleCheck.WordsAfter,
        body: Body
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema:
      def clean: Boolean = (problems.isEmpty)

    object ArticleCheck:

      type Fatal = Fatal.T
      object Fatal extends Newtype[Boolean, Pure]
      type WordsBefore = WordsBefore.T
      object WordsBefore extends Newtype[Int, Pure]
      type WordsAfter = WordsAfter.T
      object WordsAfter extends Newtype[Int, Pure]

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown(c: ArticleCheck): Markdown =
      val edits =
        if c.edits.isEmpty then ""
        else
          "\n\n### Edits\n\n" + c.edits
            .map(r => s"- ${r.label}: ${r.problem.map(_.sentence).getOrElse("applies")}")
            .mkString("\n")
      val verdict =
        if (c.clean) == (true) then "No problems: these are the checks code runs on the answer, and it would pass them."
        else if (c.fatal) == ArticleCheck.Fatal(true) then
          s"${Plural.count(Plural.CountN((c.problems.size).toLong), Plural.CountSingular("problem"))}, at least one FATAL (never written):"
        else s"${Plural.count(Plural.CountN((c.problems.size).toLong), Plural.CountSingular("problem"))}:"
      val problems = if (c.clean) == (true) then "" else "\n\n" + c.problems.map(p => s"- ${p.sentence}").mkString("\n")
      Markdown(
        s"## Check of [[${c.slug}]] (${c.kind})\n\n${c.wordsBefore} → ${c.wordsAfter} words.$edits\n\n### Result\n\n$verdict$problems"
      )

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
  ) derives StrictJson:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def isEmpty: Boolean =
      (query.forall((_).value.trim.isEmpty) && topics.isEmpty && tags.isEmpty && status.isEmpty && places.isEmpty &&
        authors.isEmpty && from.isEmpty && to.isEmpty)

    def toFilter: api.library.Filter =
      api.library.Filter.of(
        q = query,
        tags = tags,
        authors = authors,
        places = places,
        topics = topics,
        status = status,
        from = from,
        to = to
      )

    /** The filter's one line, as a coverage report and a refused filter show it. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def shownLine: FilterLine = FilterLine(line)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def line: String =
      def list(xs: scala.List[String]) = xs.map(Filter.quoted).mkString("[", ", ", "]")
      val parts = query.map(q => s"query: ${Filter.quoted(((q).value))}").toList ++
        Option.when(topics.nonEmpty)(s"topics: ${list(topics.map(_.value))}") ++
        Option.when(tags.nonEmpty)(s"tags: ${list(tags.map(_.value))}") ++
        Option.when(status.nonEmpty)(s"status: ${list(status.map(_.value))}") ++
        Option.when(places.nonEmpty)(s"places: ${list(places.map(_.value))}") ++
        Option.when(authors.nonEmpty)(s"authors: ${list(authors.map(_.value))}") ++
        from.map(f => s"from: $f") ++ to.map(t => s"to: $t")
      s"search {${parts.mkString(", ")}}"

  object Filter:

    export api.library.Filter.{Authors, Places, Q as Query, Status, Tags, Topics}
    export com.histmersion.api.catalogue.{Year as From, Year as To}
    type ExtraQueries = ExtraQueries.T
    object ExtraQueries extends Newtype[String, Pure]

    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
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

    private[api] def quoted(s: String): String = ("\"" + (s).replace("\\", "\\\\").replace("\"", "\\\"") + "\"")

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def normalise(f: Filter): Either[Problem, Option[Filter]] =
      def clean(xs: scala.List[String]) = xs.flatMap((_).split(",")).map(_.trim).filter(_.nonEmpty).distinct
      val status = clean(f.status.map(_.value)).map(_.toLowerCase(java.util.Locale.ROOT))
      val unknown = status.filterNot(s => Source.Status.values.exists(_.wire.value == s))
      val tidy = Filter(
        f.query.map(q => Query(q.value.trim)).filter(q => q.value.nonEmpty),
        clean(f.topics.map(_.value)).map(Topics(_)),
        clean(f.tags.map(_.value)).map(Tags(_)),
        status.map(Status(_)),
        clean(f.places.map(_.value)).map(Places(_)),
        clean(f.authors.map(_.value)).map(Authors(_)),
        f.from,
        f.to,
        (f.extraQueries
          .map((_).value.trim)
          .filter(_.nonEmpty)
          .distinct)
          .map(refactorValue19 => ExtraQueries(refactorValue19))
      )
      if unknown.nonEmpty then
        Left(
          Problem.Invalid.Unknown(
            Problem.TypeName.named[Source.Status],
            Problem.Raw(s"${unknown.mkString(", ")}"),
            Source.Status.values.toVector.map(v => Problem.Raw(s"${v.wire}")),
            Vector(Problem.Field("filter"), Problem.Field("status"))
          )
        )
      else if tidy.from.zip(tidy.to).exists((a, b) => a > b) then
        Left(
          Problem.Invalid.Inconsistent(
            NonEmptyVector.of(Problem.Field("from"), Problem.Field("to")),
            Problem.Invalid.Exclusion.Ordered,
            Vector(Problem.Field("filter"))
          )
        )
      else if tidy.isEmpty && tidy.extraQueries.nonEmpty then
        Left(
          Problem.Invalid.Inconsistent(
            NonEmptyVector.of(Problem.Field("query"), Problem.Field("topics"), Problem.Field("tags")),
            Problem.Invalid.Exclusion.AtLeastOne,
            Vector(Problem.Field("filter"))
          )
        )
      else Right(Option.when(!tidy.isEmpty)(tidy))

  final case class Import(
      text: Import.Text,
      slug: Option[Slug] = None,
      kind: Option[Kind.Wire] = None,
      mtime: Option[Import.Mtime] = None,
      force: Import.Force = Import.Force(false),
      strict: Import.Strict = Import.Strict(false),
      createdBy: Option[Creation.By] = None,
      via: Option[Creation.Via] = None
  ) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

  object Import:
    export Article.Body as Text
    type Mtime = Mtime.T
    object Mtime extends Newtype[Long, Pure]
    type Force = Force.T
    object Force extends Newtype[Boolean, Pure]
    type Strict = Strict.T
    object Strict extends Newtype[Boolean, Pure]

  enum Kind:
    case Concept, Myth, Culture, Region, Timeline, Report, Index

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type FromStringS = Wire
    val FromStringS: Wire.type = Wire

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromString(s: Wire): Option[Kind] =
      values.find(_.toString.equalsIgnoreCase((s).value))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((Wire(e.toString.toLowerCase)).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def fromWire(s: WireEnumValues.Wire): Option[Kind] =
        values.find(_.toString.equalsIgnoreCase((s).value))

    given JsonValueCodec[Kind] = WireEnum.codec
    given Schema[Kind] = WireEnum.schema

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class KindCount(kind: Kind.Wire, count: KindCount.Count)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object KindCount:
    type Count = Count.T
    object Count extends Newtype[Int, Pure]

  final case class Link(targetKind: Link.TargetKind, target: Link.Target)

  object Link:
    type TargetKind = TargetKind.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object TargetKind extends Newtype[String, Pure]
    export api.text.WikiLinks.Occurrence.Target

  object Links:
    type Markdown = Markdown.T
    object Markdown extends Newtype[String, Pure]

    export Article.Body as ResolveMarkdownMd

    val TargetArticle = "article"
    val TargetSource = "source"

    export api.text.WikiLinks.{isCitekeyShaped, normalize, occurrences, targets, Occurrence}

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
    def classify(targets: scala.List[Link.Target], isSource: Link.Target => Boolean): scala.List[Link] =
      targets.map { t =>
        if isCitekeyShaped(t.value) && isSource(t) then Link(Link.TargetKind(TargetSource), t)
        else Link(Link.TargetKind(TargetArticle), t)
      }

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def links(body: Body, isSource: Link.Target => Boolean): scala.List[Link] =
      classify(targets(WikiLinks.Body((body).value)), isSource)

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def hrefFor(
        target: Link.Target,
        citekeyOf: Link.Target => Option[Citekey],
        isArticle: Link.Target => Boolean
    ): Option[String] =
      (citekeyOf(Link.Target((target).value))
        .map(ck => s"/source/$ck")
        .orElse(Option.when(((isArticle(Link.Target((target).value))) == (true)))(s"/article/$target")))
        .map(refactorValue28 => (refactorValue28))

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def linkMarkdown(
        md: Body,
        href: String => Option[String],
        displayLabel: String => Option[String] = _ => None
    ): String =
      val out = rewriteLinks(WikiLinks.Body((md).value)) { (t, explicit, written) =>
        val label = explicit.getOrElse(written).value
        val text =
          if label.isEmpty then displayLabel(t.value).getOrElse(Label.display(Label.Text(t.value)).value) else label
        href(t.value) match
          case Some(h) => s"[$text]($h)"
          case None    => s"""<span class="dangling" title="unresolved link">$text</span>"""
      }
      out.value

    export WikiLinks.{retarget, rewriteLinks}

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
    def resolveMarkdown(md: Links.ResolveMarkdownMd, isSource: Link.Target => Boolean): Markdown =
      Markdown(
        linkMarkdown(
          md,
          t => Some(if isCitekeyShaped(t) && isSource(Link.Target(t)) then (s"/source/$t") else (s"/article/$t"))
        )
      )

  /** The articles' lint rules: a thin or stand-in summary, filler phrases, uncited paragraphs, unpaired critiques. */
  object Lint:
    type FillerPhrasesText = FillerPhrasesText.T
    object FillerPhrasesText extends Newtype[String, Pure]

    type FillerPhrases = FillerPhrasesValue
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type FillerPhrasesValue = FillerPhrasesText
    val FillerPhrasesValue: FillerPhrasesText.type = FillerPhrasesText

    val FillerPhrases: scala.List[FillerPhrases] = scala.List(
      FillerPhrasesText("it is worth noting"),
      FillerPhrasesText("it should be noted"),
      FillerPhrasesText("it is important to note"),
      FillerPhrasesText("interestingly"),
      FillerPhrasesText("notably"),
      FillerPhrasesText("significantly"),
      FillerPhrasesText("it is clear that"),
      FillerPhrasesText("needless to say"),
      FillerPhrasesText("of course"),
      FillerPhrasesText("as is well known"),
      FillerPhrasesText("it goes without saying"),
      FillerPhrasesText("in conclusion"),
      FillerPhrasesText("to summarize"),
      FillerPhrasesText("in summary"),
      FillerPhrasesText("broadly speaking"),
      FillerPhrasesText("generally speaking"),
      FillerPhrasesText("it must be emphasized"),
      FillerPhrasesText("it cannot be overstated"),
      FillerPhrasesText("plays a crucial role"),
      FillerPhrasesText("rich tapestry"),
      FillerPhrasesText("sheds light on"),
      FillerPhrasesText("shed new light"),
      FillerPhrasesText("paved the way"),
      FillerPhrasesText("remains to be seen"),
      FillerPhrasesText("further research is needed")
    )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private[api] val FillerPattern: scala.util.matching.Regex =
      FillerPhrases.map(p => java.util.regex.Pattern.quote((p).value)).mkString("(?i)(?:", "|", ")").r

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def articleThinSummary(rows: scala.List[(Slug, SummaryText)]): scala.List[LintItem] =
      rows
        .flatMap { (slug, summary) =>
          val s = (summary).value.trim
          val why =
            if s.isEmpty then Some("no summary")
            else
              Articles
                .summaryOf(Some(SummaryText(s)))
                .left
                .toOption
                .map(_ => s"stand-in summary: \"$s\"")
                .orElse(Option.when(s.length < MinSummaryChars)(s"only ${s.length} chars: \"$s\""))
          why.map(w =>
            LintItem(
              Citekey((slug).value),
              api.checks.Lint.LintItem.Detail(w)
            )
          )
        }
        .sortBy(_.citekey.value)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def articleFiller(articles: scala.List[(Slug, Body)]): scala.List[LintItem] =
      articles
        .flatMap { (slug, body) =>
          val matches = FillerPattern.findAllMatchIn((body).value).map(_.matched.toLowerCase).toList.distinct
          if matches.isEmpty then None
          else
            Some(
              LintItem(
                Citekey((slug).value),
                api.checks.Lint.LintItem.Detail.filler(matches)
              )
            )
        }
        .sortBy(_.citekey.value)

    private[api] val WikilinkCitekey =
      """\[\[((?=[a-z0-9_]*(?:_|\d{4}))[a-z][a-z0-9_]*)""".r

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def articleUncitedParagraphs(articles: scala.List[(Slug, Body)]): scala.List[LintItem] =
      articles
        .flatMap { (slug, body) =>
          val paras = extractParagraphs(api.checks.Lint.ProseBody((body).value))
          val uncited = paras.filter(p => WikilinkCitekey.findFirstIn((p).value).isEmpty)
          if uncited.isEmpty then None
          else
            Some(
              LintItem(
                Citekey((slug).value),
                api.checks.Lint.LintItem
                  .Detail(s"${uncited.size} paragraph(s) with no [[citekey]]: \"${(uncited.head).value.take(60)}…\"")
              )
            )
        }
        .sortBy(_.citekey.value)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def articleCritiquePairing(
        articles: scala.List[(Slug, Body)],
        resolve: CritiquePairing.Mention.Key => Option[Citekey],
        edges: scala.List[api.research.Coverage.Edge],
        work: Citekey => Option[CritiquePairing.Work]
    ): scala.List[LintItem] =
      articles
        .flatMap((slug, body) =>
          CritiquePairing
            .hits(body, resolve, edges, work)
            .map(h =>
              LintItem(
                Citekey((slug).value),
                api.checks.Lint.LintItem.Detail(h.line)
              )
            )
        )
        .sortBy(_.citekey.value)

  final case class List(
      total: RowCount,
      articles: scala.List[Summary],
      offset: api.Offset = api.Offset(0),
      next: Option[api.Offset] = None,
      kinds: scala.List[KindCount] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def page: OffsetPage = OffsetPage(total, offset, RowCount(articles.size))
  object List:
    type Total = Total.T
    object Total extends Newtype[Int, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def of(
        total: Total,
        rows: scala.List[Summary],
        offset: UnreadPage.Offset,
        kinds: scala.List[KindCount] = Nil
    ): List =
      List(
        RowCount((total).value),
        rows,
        api.Offset((offset).value),
        OffsetPage(
          RowCount((total).value),
          api.Offset((offset).value),
          RowCount(rows.size)
        ).next,
        kinds
      )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def byKind(counts: scala.List[KindCount]): scala.List[KindCount] =
      counts.filter(_.count.value > 0).sortBy(k => (-(k.count).value, k.kind.value))

  final case class Patch(
      title: Option[Title] = None,
      summary: Option[SummaryText] = None,
      body: Option[Body] = None,
      append: Option[Patch.Append] = None,
      section: Option[Patch.Section] = None,
      tags: Option[ListPatch] = None,
      regions: Option[scala.List[Patch.Regions]] = None,
      period: Option[api.catalogue.Period.Input] = None,
      kind: Option[Kind.Wire] = None,
      ifUpdatedAt: Option[Patch.IfUpdatedAt] = None,
      filter: Option[Filter] = None,
      reasons: scala.List[api.research.Coverage.Reason] = Nil,
      critiqueReasons: scala.List[CritiqueReason] = Nil,
      createdBy: Option[Creation.By] = None,
      via: Option[Creation.Via] = None
  ) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema, CanEqual:
    def onlyCritiques: Boolean =
      (critiqueReasons.nonEmpty && this
        .modify(_.critiqueReasons)
        .setTo(Nil)
        .modify(_.ifUpdatedAt)
        .setTo(None) == Patch())
  object Patch:

    type Append = Append.T
    object Append extends Newtype[String, Pure]
    export Goal.Plans.ArticleDraft.Section
    export Article.{Regions, UpdatedAt as IfUpdatedAt}

  object Plans:
    type Markdown = Markdown.T
    object Markdown extends Newtype[String, Pure]

    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    final case class FilterProposal(
        slug: Slug,
        filter: Filter,
        rationale: FilterProposal.Rationale = FilterProposal.Rationale(""),
        total: FilterProposal.Total = FilterProposal.Total(0),
        cited: FilterProposal.Cited = FilterProposal.Cited(0),
        citedIn: FilterProposal.CitedIn = FilterProposal.CitedIn(0),
        missed: scala.List[FilterProposal.Missed] = Nil,
        sample: scala.List[FilterProposal.Sample] = Nil
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema:
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def recall: FilterProposal.Recall = if cited == FilterProposal.Cited(0) then FilterProposal.Recall(1.0)
      else FilterProposal.Recall((citedIn).value.toDouble / (cited).value)

    object FilterProposal:
      type Recall = Recall.T
      object Recall extends Newtype[Double, Pure]

      type Rationale = Rationale.T
      object Rationale extends Newtype[String, Pure]
      type Total = Total.T
      object Total extends Newtype[Int, Pure]
      type Cited = Cited.T
      object Cited extends Newtype[Int, Pure]
      type CitedIn = CitedIn.T
      @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
      object CitedIn extends Newtype[Int, Pure]
      type Missed = Missed.T
      object Missed extends Newtype[String, Pure]
      type Sample = Sample.T
      object Sample extends Newtype[String, Pure]

    final case class ProposedArticle(
        slug: Slug,
        title: Title,
        kind: Kind.Wire,
        filter: Filter,
        why: ArticlePlan.Rationale = ArticlePlan.Rationale(""),
        hits: ProposedArticle.Hits = ProposedArticle.Hits(0),
        overlap: ProposedArticle.Overlap = ProposedArticle.Overlap(0.0),
        overlapWith: Option[ProposedArticle.OverlapWith] = None
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    object ProposedArticle:
      type Hits = Hits.T
      object Hits extends Newtype[Int, Pure]
      type Overlap = Overlap.T
      object Overlap extends Newtype[Double, Pure]
      type OverlapWith = OverlapWith.T
      @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
      object OverlapWith extends Newtype[String, Pure]

    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    final case class ArticlePlan(
        goal: ArticlePlan.Goal,
        rationale: ArticlePlan.Rationale = ArticlePlan.Rationale(""),
        articles: scala.List[ProposedArticle] = Nil,
        dropped: scala.List[Problem] = Nil
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema:
      def isEmpty: Boolean = (articles.isEmpty)

    object ArticlePlan:

      export ReviewGoal.Slug as Goal
      type Rationale = Rationale.T
      object Rationale extends Newtype[String, Pure]

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def filterLine(f: Filter): String = f.line

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def filterBlock(p: FilterProposal): Markdown =
      val missed =
        if p.missed.isEmpty then "" else s"\n- cited but not returned: ${p.missed.map(c => s"[[$c]]").mkString(", ")}"
      val sample = if p.sample.isEmpty then ""
      else s"\n- returned, not cited (a few): ${p.sample.map(c => s"[[$c]]").mkString(", ")}"
      Markdown(
        s"${p.filter.line} → ${Plural.count(Plural.Count((p.total).value.toLong), Plural.CountSingular("source"))}; returns ${p.citedIn} of the ${Plural.count(Plural.Count((p.cited).value.toLong), Plural.CountSingular("cited source"))}" +
          missed + sample
      )

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def planBlock(p: ArticlePlan): Markdown =
      val as = p.articles.map { a =>
        val ov = a.overlapWith.fold("")(o => f" · overlaps [[$o]] ${(a.overlap).value * 100}%.0f%%")
        s"- `${a.slug}` — ${a.title} (${a.kind}) · ${a.filter.line} → ${Plural.count(Plural.Count((a.hits).value.toLong), Plural.CountSingular("source"))}$ov" +
          Option(a.why).filter(w => s"$w".nonEmpty).fold("")(w => s": $w")
      }
      val dropped = p.dropped.map(d => s"- dropped: ${d.sentence}")
      Markdown(
        (Option(p.rationale.value).filter(_.nonEmpty).toList ++ scala.List((as ++ dropped).mkString("\n")))
          .filter(_.nonEmpty)
          .mkString("\n\n")
      )

  final case class Put(
      title: Title,
      kind: Kind.Wire,
      body: Body,
      summary: Option[SummaryText] = None,
      tags: scala.List[Put.Tags] = Nil,
      regions: scala.List[Put.Regions] = Nil,
      periodStart: Option[Put.PeriodStart] = None,
      periodEnd: Option[Put.PeriodEnd] = None,
      filter: Option[Filter] = None,
      reasons: scala.List[api.research.Coverage.Reason] = Nil,
      createdBy: Option[Creation.By] = None,
      via: Option[Creation.Via] = None
  ) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema
  object Put:

    export Article.{Regions, Tags}
    export com.histmersion.api.catalogue.{Year as PeriodEnd, Year as PeriodStart}

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
      api.research.Coverage.Reason.list(a.reasons)
    )

  final case class Ref(slug: Slug, title: Title, summary: Option[SummaryText] = None)

  final case class ReviewView(
      slug: Slug,
      badge: ReviewBadge,
      outcome: Option[ReviewView.Outcome] = None,
      updatedAt: Option[ReviewView.UpdatedAt] = None,
      rounds: scala.List[ReviewRound] = Nil,
      goals: scala.List[ReviewGoal] = Nil,
      approvals: scala.List[ReviewApproval] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema
  object ReviewView:
    type Markdown = Markdown.T
    object Markdown extends Newtype[String, Pure]

    type Outcome = Outcome.T
    object Outcome extends Newtype[String, Pure]
    export Article.UpdatedAt

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def reviewerName(reviewer: ReviewFinding.Reviewer): String = named(reviewer.value)

    private def named(wire: String): String = wire match
      case "rules"       => "rules reviewer"
      case "cold-reader" => "cold reader"
      case "skeptic"     => "skeptic"
      case "mechanical"  => "mechanical check"
      case other         => other

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(
      LintRule.G5TextBranch,
      "renders a review finding's outcome, a String vocabulary of the ledger (open, fixed, wont-fix, set-aside)"
    )
    def outcomeLine(f: ReviewFinding): String =
      val base = f.outcome.value match
        case "wont-fix"                                               => "won't fix"
        case "set-aside"                                              => "set aside"
        case "open" if ((f.deferred) == ReviewFinding.Deferred(true)) => "open (left for the next run)"
        case other                                                    => other
      val when = f.closedRunNumber.zip(f.closedRound).fold("")((run, round) => s" in run $run, round $round")
      val by = f.closedBy.filter(_.value.nonEmpty).fold("")(b => s" by ${named(b.value)}")
      val note = Option(f.note.value).map(_.trim).filter(_.nonEmpty).fold("")(n => s": $n")
      val back = if (f.reopened).value > 0 then
        s" (reopened ${Plural.count(Plural.Count((f.reopened).value.toLong), Plural.CountSingular("time"))})"
      else ""
      (s"$base$by$when$back$note")

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown(r: ReviewView): Markdown =
      val head = s"## Review of [[${r.slug}]]\n\n**${r.badge.label}** — ${r.badge.detail}" +
        r.outcome
          .filter((_).value.nonEmpty)
          .fold("")(o => s"\n\nReview manager: $o" + r.updatedAt.fold("")(t => s" ($t)"))
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
                    s"- **${f.id}** ${f.severity.value.toUpperCase} · ${reviewerName(f.reviewer)}" +
                      (if f.kind.value.nonEmpty then s" · ${f.kind}" else "") + (if f.where.value.nonEmpty then
                                                                                   s" · ${f.where}"
                                                                                 else "") +
                      s": ${f.problem} — *${outcomeLine(f)}*"
                  )
                  .mkString("\n")
            }
            .mkString("\n\n")
      Markdown(head + goals + approvals + history)

  final case class Row(
      slug: Slug,
      title: Title,
      kind: Kind.Wire,
      body: Body,
      summary: Option[SummaryText],
      tags: scala.List[Row.Tags],
      regions: scala.List[Row.Regions],
      periodStart: Option[Row.PeriodStart],
      periodEnd: Option[Row.PeriodEnd],
      updatedAt: Option[Row.UpdatedAt] = None,
      uid: Option[java.util.UUID] = None,
      creator: Option[Creation] = None,
      createdAt: Option[Creation.At] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Row:
    export Article.{Regions, Tags}
    export com.histmersion.api.catalogue.{Year as PeriodEnd, Year as PeriodStart}
    export Article.UpdatedAt

  final case class Summary(
      slug: Slug,
      title: Title,
      kind: Kind.Wire,
      summary: Option[SummaryText],
      tags: scala.List[Summary.Tags],
      regions: scala.List[Summary.Regions],
      periodStart: Option[Summary.PeriodStart],
      periodEnd: Option[Summary.PeriodEnd],
      updatedAt: Option[Summary.UpdatedAt] = None,
      review: Option[ReviewBadge] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Summary:
    export Article.{Regions, Tags}
    export com.histmersion.api.catalogue.{Year as PeriodEnd, Year as PeriodStart}
    export Article.UpdatedAt

  final case class Version(
      id: Version.Id,
      slug: Slug,
      summary: Option[SummaryText],
      updatedAt: Version.UpdatedAt,
      createdAt: Version.CreatedAt,
      wordCount: Version.WordCount
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Version:
    type Id = Id.T
    object Id extends Newtype[Long, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type WordCount = WordCount.T
    object WordCount extends Newtype[Int, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class VersionFull(
      id: VersionFull.Id,
      slug: Slug,
      body: Body,
      summary: Option[SummaryText],
      updatedAt: VersionFull.UpdatedAt,
      createdAt: VersionFull.CreatedAt
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object VersionFull:
    export Version.{CreatedAt, Id, UpdatedAt}

  final case class View(
      article: Row,
      backlinks: scala.List[Backlink],
      sourcesCited: scala.List[CitedSource],
      formatting: scala.List[FormatIssue] = Nil,
      coverage: Option[Coverage] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema
