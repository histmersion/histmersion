package com.histmersion.api.research

import com.histmersion.api.Problem

import com.histmersion.Newtype
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
    reasons: Map[com.histmersion.api.library.Citekey, Problem] = Map.empty
)

object Article:
  type Export = Export.T
  object Export extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FullText = FullText.T
  object FullText extends Newtype[Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Heading = Heading.T
  object Heading extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lede = Lede.T
  object Lede extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Part = Part.T
  object Part extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Period = Period.T
  object Period extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Prose = Prose.T
  object Prose extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Section = Section.T
  object Section extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionId = VersionId.T
  object VersionId extends Newtype[Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Slug = Slug.T
  object Slug extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  export Kind.Wire as KindValue
  type Body = Body.T
  object Body extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryText = SummaryText.T
  object SummaryText extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regions = Regions.T
  object Regions extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Coverage:
    type DisagreementKind = DisagreementKind.T
    object DisagreementKind extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Export = Export.T
    object Export extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Markdown = Markdown.T
    object Markdown extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Method = Method.T
    object Method extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotUsed = NotUsed.T
    object NotUsed extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sections = Sections.T
    object Sections extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type DisagreementKinds = DisagreementKindsValue
    type DisagreementKindsValue = DisagreementKind
    val DisagreementKindsValue: DisagreementKind.type = DisagreementKind
    export Article.Slug as ComputeSlug
    export com.histmersion.api.research.Coverage.Total as ComputeTotal
    type SectionsResult = Sections
    val SectionsResult: Sections.type = Sections
    type ExportedResult = Export
    val ExportedResult: Export.type = Export
    type NotUsedResult = NotUsed
    val NotUsedResult: NotUsed.type = NotUsed
    type MethodResult = Method
    val MethodResult: Method.type = Method
    type MarkdownResult = Markdown
    val MarkdownResult: Markdown.type = Markdown

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

    def compute(
        slug: Coverage.ComputeSlug,
        filter: Filter,
        total: Coverage.ComputeTotal,
        hits: scala.List[com.histmersion.api.research.Coverage.Source],
        citedInFilter: Set[com.histmersion.api.library.Citekey],
        cited: scala.List[com.histmersion.api.library.Citekey],
        reasons: Map[com.histmersion.api.library.Citekey, Problem],
        edges: scala.List[com.histmersion.api.research.Coverage.Edge],
        searches: scala.List[CoverageSearch]
    ): Coverage =
      val citedSet = cited.toSet
      val read = hits.distinctBy(_.citekey).sortBy(_.citekey.value)
      val readSet = read.map(_.citekey).toSet
      val notUsed = read
        .filterNot(s => citedSet(s.citekey))
        .map(s => com.histmersion.api.research.Coverage.Entry(s, reasons.get(s.citekey)))
      val capped = read.size < total.value
      val outside =
        cited.distinct.filterNot(ck => citedInFilter(com.histmersion.api.library.Citekey((ck).value)) || readSet(ck))
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
        com.histmersion.api.research.Coverage.FilterLine(filter.line),
        total,
        com.histmersion.api.research.Coverage.Checked(read.size),
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
        c: Coverage,
        cited: Set[com.histmersion.api.library.Citekey],
        reasons: Map[com.histmersion.api.library.Citekey, Problem]
    ): scala.List[com.histmersion.api.library.Citekey] =
      c.notUsed.map(_.source.citekey).filterNot(ck => cited(ck) || reasons.contains(ck))

    /** HTML comments, so Obsidian and the page do not show them. */
    val GeneratedStart = "<!-- generated from the article's filter: edits here are not kept -->"
    val GeneratedEnd = "<!-- end of generated sections -->"

    val NotUsedHeading = "Not used"

    def sections(c: Coverage): Sections = Sections(s"${notUsed(c)}\n\n${method(c)}")

    def exported(c: Coverage): Export =
      Export(s"$GeneratedStart\n\n${sections(c)}\n\n$GeneratedEnd")

    def notUsed(c: Coverage): NotUsed =
      val src = Plural.noun(Plural.Count((c.total).value.toLong), Plural.Noun.Singular("source"))
      val citedN = c.cited.size
      val n = c.notUsed.size
      val scope = if (c.capped) == (true) then s"; the first ${c.checked} (by citekey) were checked" else ""
      val lead =
        if c.total == com.histmersion.api.research.Coverage.Total(0) then "_The filter returns no source._"
        else if n == 0 then
          if (c.capped) == (true) then
            s"_The filter returns ${c.total} $src; all ${c.checked} checked are cited above._"
          else
            s"_${
                if c.total == com.histmersion.api.research.Coverage.Total(1) then "The one source"
                else s"All ${c.total} sources"
              } the filter returns ${
                if c.total == com.histmersion.api.research.Coverage.Total(1) then "is" else "are"
              } cited above._"
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

    private def statusNote(s: Source.Status): String = if Stated(s) then (s" (${s.wire})") else ("")

    private def endSentence(s: Problem): String =
      val t = s.value
      if t.endsWith(".") || t.endsWith("?") || t.endsWith("!") then t else s"$t."

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

    def stripGenerated(body: Body): Body =
      val s = (body).value.indexOf(GeneratedStart)
      if s < 0 then Body((body).value)
      else
        val e = (body).value.indexOf(GeneratedEnd, s)
        val tail = if e < 0 then "" else (body).value.substring(e + GeneratedEnd.length)
        Body(((body).value.substring(0, s).stripTrailing + (if tail.trim.isEmpty then ""
                                                            else "\n\n" + tail.dropWhile(_.isWhitespace))).trim)

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

  final case class Deleted(slug: Slug, deleted: Deleted.Value)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Deleted:
    type Value = Value.T
    object Value extends Newtype[Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Edits:
    type Anchor = Anchor.T
    object Anchor extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeadLimit = HeadLimit.T
    object HeadLimit extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Markdown = Markdown.T
    object Markdown extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NewText = NewText.T
    object NewText extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    export Article.{Body as ApplyOneText, Body as LocateText, Body as OccurrencesText}
    type MarkdownResult = Markdown
    val MarkdownResult: Markdown.type = Markdown

    enum Op(val wire: Op.Wire) derives CanEqual:
      case Replace extends Op(Op.Wire("replace"))

      case InsertAfter extends Op(Op.Wire("insertAfter"))

      case InsertBefore extends Op(Op.Wire("insertBefore"))

      case Delete extends Op(Op.Wire("delete"))

    object Op:
      type Wire = Wire.T
      object Wire extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      def parse(s: Wire): Option[Op] = values.find(_.wire.value.equalsIgnoreCase(s.value.trim))
      given WireEnumValues[Op]:
        def all: Array[Op] = values
        def wire(e: Op): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
        def fromWire(s: WireEnumValues.Wire): Option[Op] = parse(Wire((s).value))
      given Encoder[Op] = WireEnum.encoder
      given Decoder[Op] = WireEnum.decoder
      given Schema[Op] = WireEnum.schema

    final case class EditAnswer(op: Op, anchor: EditAnswer.Anchor, text: Option[EditAnswer.Text] = None)
        derives KindlingsCodecAsObject
    object EditAnswer:
      export Edits.{Anchor, NewText as Text}

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

    enum Edit derives CanEqual:
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
    def parse(a: EditAnswer): Either[Problem, Edit] =
      val anchor = Option(a.anchor).getOrElse(Anchor(""))
      val text = Option(a.text).flatten.getOrElse(NewText(""))
      if anchor.value.trim.isEmpty then
        Left(Problem("no anchor — copy the passage the edit is about from the body"))
      else
        a.op match
          case Op.Delete                             => Right(Edit.Delete(anchor))
          case Op.Replace if text.value.trim.isEmpty =>
            Left(Problem("a replace with no new text — give the text, or use delete to remove the passage"))
          case Op.Replace                   => Right(Edit.Replace(anchor, text))
          case _ if text.value.trim.isEmpty => Left(Problem("no text to insert"))
          case Op.InsertAfter               => Right(Edit.InsertAfter(anchor, text))
          case Op.InsertBefore              => Right(Edit.InsertBefore(anchor, text))

    final case class EditResult(
        index: EditResult.Index,
        op: Op,
        anchor: EditResult.Anchor,
        text: EditResult.Text,
        problem: Option[Problem] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema,
          com.histmersion.api.json.Schema:
      def applied: Boolean = (problem.isEmpty)
      def label: String = (s"edit $index (${op.wire} \"$anchor\")")

    object EditResult:

      type Index = Index.T
      object Index extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      export Edits.{Anchor, NewText as Text}

    final case class Applied(body: Body, results: scala.List[EditResult]):
      def problems: scala.List[Applied.ProblemsResult] =
        results.flatMap(r => r.problem.map(p => Applied.ProblemsResult(s"${r.label}: $p")))
      def appliedCount: Applied.Count =
        Applied.Count(results.count((refactorCallback => ((refactorCallback.applied) == (true)))))

    object Applied:
      type Count = Count.T
      object Count extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type ProblemsResult = ProblemsResult.T
      object ProblemsResult extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

    private def occurrences(text: Edits.OccurrencesText, needle: String): scala.List[Offset] =
      (Iterator
        .iterate((text).value.indexOf(needle))(i => (text).value.indexOf(needle, i + 1))
        .takeWhile(_ >= 0)
        .toList)
        .map(refactorValue14 => Offset(refactorValue14))

    def locate(text: Edits.LocateText, anchor: Anchor): Either[Problem, (Offset, Offset)] =
      val a = (anchor).value.trim
      occurrences(text, (a)) match
        case scala.List(i) => Right((i, Offset((i).value + a.length)))
        case _ :: _ :: _   =>
          Left(
            Problem(
              s"the anchor occurs ${occurrences(text, (a)).size} times — quote more of the passage so it occurs once"
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
              Left(
                Problem(
                  "the anchor is not in the article — copy it exactly from the current body (earlier edits included)"
                )
              )
            case more =>
              Left(
                Problem(
                  s"the anchor occurs ${more.size} times — quote more of the passage so it occurs once"
                )
              )

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

    enum Draft derives CanEqual:
      case Edits(edits: scala.List[EditAnswer])
      case Body(text: Draft.Body.Text)

    object Draft:
      object Body:
        export Article.Body as Text

    final case class ArticleCheckRequest(
        edits: scala.List[EditAnswer] = Nil,
        body: Option[Body] = None,
        kind: Option[Kind.Wire] = None
    ) derives KindlingsCodecAsObject:
      def draft: Either[Problem, Draft] =
        (edits, body.filter(_.value.trim.nonEmpty)) match
          case (Nil, None) =>
            Left(Problem("give `edits` (against the stored body) or `body` (a whole draft)"))
          case (_ :: _, Some(_)) => Left(Problem("give `edits` or `body`, not both"))
          case (es, None)        => Right(Draft.Edits(es))
          case (Nil, Some(b))    => Right(Draft.Body(b))
    object ArticleCheckRequest:
      given Schema[ArticleCheckRequest] = KindlingsSchema
        .derived[ArticleCheckRequest]
        .schema
        .modify(_.edits)(_.description("Edits to the CURRENT stored body, applied in order"))
        .modify(_.body)(_.description("Instead of edits: a whole draft body to check"))
        .modify(_.kind)(
          _.description("Check the section set of this kind instead of the article's (index = none, for a fragment)")
        )

    final case class ArticleCheck(
        slug: Slug,
        kind: Kind.Wire,
        edits: scala.List[EditResult],
        problems: scala.List[Problem],
        fatal: ArticleCheck.Fatal,
        wordsBefore: ArticleCheck.WordsBefore,
        wordsAfter: ArticleCheck.WordsAfter,
        body: Body
    ) derives KindlingsCodecAsObject,
          KindlingsSchema,
          com.histmersion.api.json.Schema:
      def clean: Boolean = (problems.isEmpty)

    object ArticleCheck:

      type Fatal = Fatal.T
      object Fatal extends Newtype[Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type WordsBefore = WordsBefore.T
      object WordsBefore extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type WordsAfter = WordsAfter.T
      object WordsAfter extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def markdown(c: ArticleCheck): Markdown =
      val edits =
        if c.edits.isEmpty then ""
        else "\n\n### Edits\n\n" + c.edits.map(r => s"- ${r.label}: ${r.problem.getOrElse("applies")}").mkString("\n")
      val verdict =
        if (c.clean) == (true) then "No problems: these are the checks code runs on the answer, and it would pass them."
        else if (c.fatal) == ArticleCheck.Fatal(true) then
          s"${Plural.count(Plural.CountN((c.problems.size).toLong), Plural.CountSingular("problem"))}, at least one FATAL (never written):"
        else s"${Plural.count(Plural.CountN((c.problems.size).toLong), Plural.CountSingular("problem"))}:"
      val problems = if (c.clean) == (true) then "" else "\n\n" + c.problems.map(p => s"- $p").mkString("\n")
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
        Option.when(topics.nonEmpty)(s"topics: ${list(topics.map(_.value))}") ++
        Option.when(tags.nonEmpty)(s"tags: ${list(tags.map(_.value))}") ++
        Option.when(status.nonEmpty)(s"status: ${list(status.map(_.value))}") ++
        Option.when(places.nonEmpty)(s"places: ${list(places.map(_.value))}") ++
        Option.when(authors.nonEmpty)(s"authors: ${list(authors.map(_.value))}") ++
        from.map(f => s"from: $f") ++ to.map(t => s"to: $t")
      s"search {${parts.mkString(", ")}}"

  object Filter:

    export com.histmersion.api.library.Filter.{Authors, Places, Q as Query, Status, Tags, Topics}
    export com.histmersion.api.catalogue.{Year as From, Year as To}
    type ExtraQueries = ExtraQueries.T
    object ExtraQueries extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

    private[api] def quoted(s: String): String = ("\"" + (s).replace("\\", "\\\\").replace("\"", "\\\"") + "\"")

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
          .map(refactorValue19 => _root_.com.histmersion.api.research.Article.Filter.ExtraQueries(refactorValue19))
      )
      if unknown.nonEmpty then
        Left(
          Problem(
            s"unknown status ${unknown.map(s => s"'$s'").mkString(", ")} — one of ${Source.Status.values.map(_.wire).mkString(", ")}"
          )
        )
      else if tidy.from.zip(tidy.to).exists((a, b) => a > b) then
        Left(Problem(s"the filter's from (${tidy.from.get}) is after its to (${tidy.to.get})"))
      else if tidy.isEmpty && tidy.extraQueries.nonEmpty then
        Left(Problem("extraQueries without a filter: give the query, topics or tags the article is compiled from"))
      else Right(Option.when(!tidy.isEmpty)(tidy))

  final case class Import(
      text: Import.Text,
      slug: Option[Slug] = None,
      kind: Option[Kind.Wire] = None,
      mtime: Option[Import.Mtime] = None,
      force: Import.Force = Import.Force(false),
      strict: Import.Strict = Import.Strict(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Import:
    export Article.Body as Text
    type Mtime = Mtime.T
    object Mtime extends Newtype[Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Force = Force.T
    object Force extends Newtype[Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Strict = Strict.T
    object Strict extends Newtype[Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Kind derives CanEqual:
    case Concept, Myth, Culture, Region, Timeline, Report, Index

  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromStringS = Wire
    val FromStringS: Wire.type = Wire

    def fromString(s: Wire): Option[Kind] =
      values.find(_.toString.equalsIgnoreCase((s).value))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((Wire(e.toString.toLowerCase)).value)
      def fromWire(s: WireEnumValues.Wire): Option[Kind] =
        values.find(_.toString.equalsIgnoreCase((s).value))

    given Encoder[Kind] = WireEnum.encoder
    given Decoder[Kind] = WireEnum.decoder
    given Schema[Kind] = WireEnum.schema

  final case class KindCount(kind: Kind.Wire, count: KindCount.Count)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object KindCount:
    type Count = Count.T
    object Count extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Link(targetKind: Link.TargetKind, target: Link.Target)

  object Link:
    type TargetKind = TargetKind.T
    object TargetKind extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    export com.histmersion.api.text.WikiLinks.Occurrence.Target

  object Links:
    type Markdown = Markdown.T
    object Markdown extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ResolveMarkdownResult = Markdown
    val ResolveMarkdownResult: Markdown.type = Markdown
    export Article.Body as ResolveMarkdownMd

    val TargetArticle = "article"
    val TargetSource = "source"

    export com.histmersion.api.text.WikiLinks.{isCitekeyShaped, normalize, occurrences, targets, Occurrence}

    def classify(targets: scala.List[Link.Target], isSource: Link.Target => Boolean): scala.List[Link] =
      targets.map { t =>
        if isCitekeyShaped(t.value) && isSource(t) then Link(Link.TargetKind(TargetSource), t)
        else Link(Link.TargetKind(TargetArticle), t)
      }

    def links(body: Body, isSource: Link.Target => Boolean): scala.List[Link] =
      classify(targets(com.histmersion.api.text.WikiLinks.Body((body).value)), isSource)

    def hrefFor(
        target: Link.Target,
        citekeyOf: Link.Target => Option[com.histmersion.api.library.Citekey],
        isArticle: Link.Target => Boolean
    ): Option[String] =
      (citekeyOf(Link.Target((target).value))
        .map(ck => s"/source/$ck")
        .orElse(Option.when(((isArticle(Link.Target((target).value))) == (true)))(s"/article/$target")))
        .map(refactorValue28 => (refactorValue28))

    def linkMarkdown(
        md: Body,
        href: String => Option[String],
        displayLabel: String => Option[String] = _ => None
    ): String =
      val out = rewriteLinks(com.histmersion.api.text.WikiLinks.Body((md).value)) { (t, explicit, written) =>
        val label = explicit.getOrElse(written).value
        val text =
          if label.isEmpty then displayLabel(t.value).getOrElse(Label.display(Label.Text(t.value)).value) else label
        href(t.value) match
          case Some(h) => s"[$text]($h)"
          case None    => s"""<span class="dangling" title="unresolved link">$text</span>"""
      }
      out.value

    export com.histmersion.api.text.WikiLinks.{retarget, rewriteLinks}

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
    object FillerPhrasesText extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type FillerPhrases = FillerPhrasesValue
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

    private[api] val FillerPattern: scala.util.matching.Regex =
      FillerPhrases.map(p => java.util.regex.Pattern.quote((p).value)).mkString("(?i)(?:", "|", ")").r

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
              com.histmersion.api.library.Citekey((slug).value),
              com.histmersion.api.checks.Lint.LintItem.Detail(w)
            )
          )
        }
        .sortBy(_.citekey.value)

    def articleFiller(articles: scala.List[(Slug, Body)]): scala.List[LintItem] =
      articles
        .flatMap { (slug, body) =>
          val matches = FillerPattern.findAllMatchIn((body).value).map(_.matched.toLowerCase).toList.distinct
          if matches.isEmpty then None
          else
            Some(
              LintItem(
                com.histmersion.api.library.Citekey((slug).value),
                com.histmersion.api.checks.Lint.LintItem.Detail(s"filler: ${matches.take(3).mkString(", ")}${
                    if matches.sizeIs > 3 then s" (+${matches.size - 3} more)" else ""
                  }")
              )
            )
        }
        .sortBy(_.citekey.value)

    private[api] val WikilinkCitekey =
      """\[\[((?=[a-z0-9_]*(?:_|\d{4}))[a-z][a-z0-9_]*)""".r

    def articleUncitedParagraphs(articles: scala.List[(Slug, Body)]): scala.List[LintItem] =
      articles
        .flatMap { (slug, body) =>
          val paras = extractParagraphs(com.histmersion.api.checks.Lint.ProseBody((body).value))
          val uncited = paras.filter(p => WikilinkCitekey.findFirstIn((p).value).isEmpty)
          if uncited.isEmpty then None
          else
            Some(
              LintItem(
                com.histmersion.api.library.Citekey((slug).value),
                com.histmersion.api.checks.Lint.LintItem
                  .Detail(s"${uncited.size} paragraph(s) with no [[citekey]]: \"${(uncited.head).value.take(60)}…\"")
              )
            )
        }
        .sortBy(_.citekey.value)

    def articleCritiquePairing(
        articles: scala.List[(Slug, Body)],
        resolve: CritiquePairing.Mention.Key => Option[com.histmersion.api.library.Citekey],
        edges: scala.List[com.histmersion.api.research.Coverage.Edge],
        work: com.histmersion.api.library.Citekey => Option[CritiquePairing.Work]
    ): scala.List[LintItem] =
      articles
        .flatMap((slug, body) =>
          CritiquePairing
            .hits(body, resolve, edges, work)
            .map(h =>
              LintItem(
                com.histmersion.api.library.Citekey((slug).value),
                com.histmersion.api.checks.Lint.LintItem.Detail(h.line)
              )
            )
        )
        .sortBy(_.citekey.value)

  final case class List(
      total: com.histmersion.api.RowCount,
      articles: scala.List[Summary],
      offset: com.histmersion.api.Offset = com.histmersion.api.Offset(0),
      next: Option[com.histmersion.api.Offset] = None,
      kinds: scala.List[KindCount] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def page: OffsetPage = OffsetPage(total, offset, com.histmersion.api.RowCount(articles.size))
  object List:
    type Total = Total.T
    object Total extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(
        total: Total,
        rows: scala.List[Summary],
        offset: com.histmersion.api.personal.UnreadPage.Offset,
        kinds: scala.List[KindCount] = Nil
    ): List =
      List(
        com.histmersion.api.RowCount((total).value),
        rows,
        com.histmersion.api.Offset((offset).value),
        OffsetPage(
          com.histmersion.api.RowCount((total).value),
          com.histmersion.api.Offset((offset).value),
          com.histmersion.api.RowCount(rows.size)
        ).next,
        kinds
      )

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
      period: Option[com.histmersion.api.catalogue.Period.Input] = None,
      kind: Option[Kind.Wire] = None,
      ifUpdatedAt: Option[Patch.IfUpdatedAt] = None,
      filter: Option[Filter] = None,
      reasons: scala.List[com.histmersion.api.research.Coverage.Reason] = Nil,
      critiqueReasons: scala.List[CritiqueReason] = Nil
  ) derives KindlingsSchema,
        com.histmersion.api.json.Schema,
        CanEqual:
    def onlyCritiques: Boolean =
      (critiqueReasons.nonEmpty && this
        .modify(_.critiqueReasons)
        .setTo(Nil)
        .modify(_.ifUpdatedAt)
        .setTo(None) == Patch())
  object Patch:

    type Append = Append.T
    object Append extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Section = Section.T
    object Section extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    export Article.{Regions, UpdatedAt as IfUpdatedAt}

    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  object Plans:
    type Markdown = Markdown.T
    object Markdown extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type FilterBlockResult = Markdown
    val FilterBlockResult: Markdown.type = Markdown
    type PlanBlockResult = Markdown
    val PlanBlockResult: Markdown.type = Markdown

    final case class FilterProposal(
        slug: Slug,
        filter: Filter,
        rationale: FilterProposal.Rationale = FilterProposal.Rationale(""),
        total: FilterProposal.Total = FilterProposal.Total(0),
        cited: FilterProposal.Cited = FilterProposal.Cited(0),
        citedIn: FilterProposal.CitedIn = FilterProposal.CitedIn(0),
        missed: scala.List[FilterProposal.Missed] = Nil,
        sample: scala.List[FilterProposal.Sample] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema,
          com.histmersion.api.json.Schema:
      def recall: FilterProposal.Recall = if cited == FilterProposal.Cited(0) then FilterProposal.Recall(1.0)
      else FilterProposal.Recall((citedIn).value.toDouble / (cited).value)

    object FilterProposal:
      type Recall = Recall.T
      object Recall extends Newtype[Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type Rationale = Rationale.T
      object Rationale extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Total = Total.T
      object Total extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Cited = Cited.T
      object Cited extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type CitedIn = CitedIn.T
      object CitedIn extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Missed = Missed.T
      object Missed extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Sample = Sample.T
      object Sample extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    final case class ProposedArticle(
        slug: Slug,
        title: Title,
        kind: Kind.Wire,
        filter: Filter,
        why: Problem = Problem(""),
        hits: ProposedArticle.Hits = ProposedArticle.Hits(0),
        overlap: ProposedArticle.Overlap = ProposedArticle.Overlap(0.0),
        overlapWith: Option[ProposedArticle.OverlapWith] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema,
          com.histmersion.api.json.Schema

    object ProposedArticle:
      type Hits = Hits.T
      object Hits extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Overlap = Overlap.T
      object Overlap extends Newtype[Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type OverlapWith = OverlapWith.T
      object OverlapWith extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    final case class ArticlePlan(
        goal: ArticlePlan.Goal,
        rationale: ArticlePlan.Rationale = ArticlePlan.Rationale(""),
        articles: scala.List[ProposedArticle] = Nil,
        dropped: scala.List[ArticlePlan.Dropped] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema,
          com.histmersion.api.json.Schema:
      def isEmpty: Boolean = (articles.isEmpty)

    object ArticlePlan:

      export ReviewGoal.Slug as Goal
      type Rationale = Rationale.T
      object Rationale extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Dropped = Dropped.T
      object Dropped extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def filterLine(f: Filter): String = f.line

    def filterBlock(p: FilterProposal): Markdown =
      val missed =
        if p.missed.isEmpty then "" else s"\n- cited but not returned: ${p.missed.map(c => s"[[$c]]").mkString(", ")}"
      val sample = if p.sample.isEmpty then ""
      else s"\n- returned, not cited (a few): ${p.sample.map(c => s"[[$c]]").mkString(", ")}"
      Markdown(
        s"${p.filter.line} → ${Plural.count(Plural.Count((p.total).value.toLong), Plural.CountSingular("source"))}; returns ${p.citedIn} of the ${Plural.count(Plural.Count((p.cited).value.toLong), Plural.CountSingular("cited source"))}" +
          missed + sample
      )

    def planBlock(p: ArticlePlan): Markdown =
      val as = p.articles.map { a =>
        val ov = a.overlapWith.fold("")(o => f" · overlaps [[$o]] ${(a.overlap).value * 100}%.0f%%")
        s"- `${a.slug}` — ${a.title} (${a.kind}) · ${a.filter.line} → ${Plural.count(Plural.Count((a.hits).value.toLong), Plural.CountSingular("source"))}$ov" +
          Option(a.why).filter(_.value.nonEmpty).fold("")(w => s": $w")
      }
      val dropped = p.dropped.map(d => s"- dropped: $d")
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
      reasons: scala.List[com.histmersion.api.research.Coverage.Reason] = Nil
  ) derives KindlingsSchema,
        com.histmersion.api.json.Schema
  object Put:

    export Article.{Regions, Tags}
    export com.histmersion.api.catalogue.{Year as PeriodEnd, Year as PeriodStart}

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

  final case class Ref(slug: Slug, title: Title, summary: Option[SummaryText] = None)

  final case class ReviewView(
      slug: Slug,
      badge: ReviewBadge,
      outcome: Option[ReviewView.Outcome] = None,
      updatedAt: Option[ReviewView.UpdatedAt] = None,
      rounds: scala.List[ReviewRound] = Nil,
      goals: scala.List[ReviewGoal] = Nil,
      approvals: scala.List[ReviewApproval] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema
  object ReviewView:
    type Markdown = Markdown.T
    object Markdown extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type MarkdownResult = Markdown
    val MarkdownResult: Markdown.type = Markdown

    type Outcome = Outcome.T
    object Outcome extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    export Article.UpdatedAt

    def reviewerName(reviewer: ReviewFinding.Reviewer): String = named(reviewer.value)

    private def named(wire: String): String = wire match
      case "rules"       => "rules reviewer"
      case "cold-reader" => "cold reader"
      case "skeptic"     => "skeptic"
      case "mechanical"  => "mechanical check"
      case other         => other

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
      uid: Option[java.util.UUID] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

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
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

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
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Version:
    type Id = Id.T
    object Id extends Newtype[Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WordCount = WordCount.T
    object WordCount extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class VersionFull(
      id: VersionFull.Id,
      slug: Slug,
      body: Body,
      summary: Option[SummaryText],
      updatedAt: VersionFull.UpdatedAt,
      createdAt: VersionFull.CreatedAt
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object VersionFull:
    export Version.{CreatedAt, Id, UpdatedAt}

  final case class View(
      article: Row,
      backlinks: scala.List[Backlink],
      sourcesCited: scala.List[CitedSource],
      formatting: scala.List[FormatIssue] = Nil,
      coverage: Option[Coverage] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema
