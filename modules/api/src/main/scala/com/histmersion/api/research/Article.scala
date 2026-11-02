package com.histmersion.api.research

import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.WireEnumValues
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
import com.histmersion.domain.library.Source
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import io.circe.{Decoder, Encoder}
import scala.annotation.tailrec
import sttp.tapir.Schema
final case class Article(
    slug: String,
    title: String,
    kind: String,
    body: String,
    summary: Option[String] = None,
    tags: scala.List[String] = Nil,
    regions: scala.List[String] = Nil,
    period: Option[Period] = None,
    updatedAt: Option[String] = None,
    filter: Option[Article.Filter] = None,
    reasons: Map[String, String] = Map.empty
)

object Article:

  object Coverage:

    val DisagreementKinds: scala.List[String] = scala.List("challenges", "refutes", "updates", "superseded_by")

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
        slug: String,
        filter: Filter,
        total: Int,
        hits: scala.List[com.histmersion.api.research.Coverage.Source],
        citedInFilter: Set[String],
        cited: scala.List[String],
        reasons: Map[String, String],
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
        cited: Set[String],
        reasons: Map[String, String]
    ): scala.List[String] =
      c.notUsed.map(_.source.citekey).filterNot(ck => cited(ck) || reasons.contains(ck))

    /** HTML comments, so Obsidian and the page do not show them. */
    val GeneratedStart = "<!-- generated from the article's filter: edits here are not kept -->"
    val GeneratedEnd = "<!-- end of generated sections -->"

    val NotUsedHeading = "Not used"

    def sections(c: com.histmersion.api.research.Coverage): String = s"${notUsed(c)}\n\n${method(c)}"

    def exported(c: com.histmersion.api.research.Coverage): String =
      s"$GeneratedStart\n\n${sections(c)}\n\n$GeneratedEnd"

    def notUsed(c: com.histmersion.api.research.Coverage): String =
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

    def method(c: com.histmersion.api.research.Coverage): String =
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

    private def orList(xs: scala.List[String]): String = xs match
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

    def markdown(c: com.histmersion.api.research.Coverage): String =
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

  final case class Deleted(slug: String, deleted: Boolean) derives KindlingsCodecAsObject, KindlingsSchema
  object Deleted:
    given Schema[Deleted] = summon[KindlingsSchema[Deleted]].schema

  object Edits:

    enum Op(val wire: String):
      case Replace extends Op("replace")

      case InsertAfter extends Op("insertAfter")

      case InsertBefore extends Op("insertBefore")

      case Delete extends Op("delete")

    object Op:
      def parse(s: String): Option[Op] = values.find(_.wire.equalsIgnoreCase(s.trim))
      given WireEnumValues[Op]:
        def all: Array[Op] = Op.values
        def wire(e: Op): String = e.wire
        def fromWire(s: String): Option[Op] = parse(s)
      given Encoder[Op] = WireEnum.encoder
      given Decoder[Op] = WireEnum.decoder
      given Schema[Op] = WireEnum.schema

    final case class EditAnswer(op: Op, anchor: String, text: Option[String] = None) derives KindlingsCodecAsObject
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
      def anchor: String
      case Replace(anchor: String, text: String)
      case InsertAfter(anchor: String, text: String)
      case InsertBefore(anchor: String, text: String)
      case Delete(anchor: String)

    /** A replace whose text is empty or null is not read as a delete: a strict-schema provider sends `null` (or `""`)
      * for a field it left out.
      */
    def parse(a: EditAnswer): Either[String, Edit] =
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

    final case class EditResult(index: Int, op: Op, anchor: String, text: String, problem: Option[String] = None)
        derives KindlingsCodecAsObject,
          KindlingsSchema:
      def applied: Boolean = problem.isEmpty
      def label: String = s"edit $index (${op.wire} \"$anchor\")"
    object EditResult:
      given Schema[EditResult] = summon[KindlingsSchema[EditResult]].schema

    final case class Applied(body: String, results: scala.List[EditResult]):
      def problems: scala.List[String] = results.flatMap(r => r.problem.map(p => s"${r.label}: $p"))
      def appliedCount: Int = results.count(_.applied)

    def head(anchor: String, max: Int = 48): String =
      val one = anchor.trim.replaceAll("\\s+", " ")
      if one.length <= max then one else one.take(max).trim + "…"

    private def squeeze(s: String): (String, Array[Int]) =
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

    private def occurrences(text: String, needle: String): scala.List[Int] =
      Iterator.iterate(text.indexOf(needle))(i => text.indexOf(needle, i + 1)).takeWhile(_ >= 0).toList

    def locate(text: String, anchor: String): Either[String, (Int, Int)] =
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

    private def cut(text: String, s: Int, e: Int): String =
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

    def applyOne(text: String, e: Edit): Either[String, String] =
      locate(text, e.anchor).map { (s, end) =>
        e match
          case Edit.Replace(_, t)      => text.substring(0, s) + t + text.substring(end)
          case Edit.InsertAfter(_, t)  => text.substring(0, end) + t + text.substring(end)
          case Edit.InsertBefore(_, t) => text.substring(0, s) + t + text.substring(s)
          case Edit.Delete(_)          => cut(text, s, end)
      }

    def applyAll(
        body: String,
        edits: scala.List[EditAnswer],
        refuse: EditAnswer => Option[String] = _ => None
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
      case Body(text: String)

    final case class ArticleCheckRequest(
        edits: scala.List[EditAnswer] = Nil,
        body: Option[String] = None,
        kind: Option[String] = None
    ) derives KindlingsCodecAsObject:
      def draft: Either[String, Draft] =
        (edits, body.filter(_.trim.nonEmpty)) match
          case (Nil, None)       => Left("give `edits` (against the stored body) or `body` (a whole draft)")
          case (_ :: _, Some(_)) => Left("give `edits` or `body`, not both")
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
        slug: String,
        kind: String,
        edits: scala.List[EditResult],
        problems: scala.List[String],
        fatal: Boolean,
        wordsBefore: Int,
        wordsAfter: Int,
        body: String
    ) derives KindlingsCodecAsObject,
          KindlingsSchema:
      def clean: Boolean = problems.isEmpty
    object ArticleCheck:
      given Schema[ArticleCheck] = summon[KindlingsSchema[ArticleCheck]].schema

    def markdown(c: ArticleCheck): String =
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
      query: Option[String] = None,
      topics: scala.List[String] = Nil,
      tags: scala.List[String] = Nil,
      status: scala.List[String] = Nil,
      places: scala.List[String] = Nil,
      authors: scala.List[String] = Nil,
      from: Option[Long] = None,
      to: Option[Long] = None,
      extraQueries: scala.List[String] = Nil
  ):
    def isEmpty: Boolean =
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

    def line: String =
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

    private[api] def quoted(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    def normalise(f: Filter): Either[String, Option[Filter]] =
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
      text: String,
      slug: Option[String] = None,
      kind: Option[String] = None,
      mtime: Option[Long] = None,
      force: Boolean = false,
      strict: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Import:
    given Schema[Import] = summon[KindlingsSchema[Import]].schema

  enum Kind:
    case Concept, Myth, Culture, Region, Timeline, Report, Index

  object Kind:
    def fromString(s: String): Option[Kind] =
      values.find(_.toString.equalsIgnoreCase(s))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): String = e.toString.toLowerCase
      def fromWire(s: String): Option[Kind] =
        Kind.values.find(_.toString.equalsIgnoreCase(s))

    given Encoder[Kind] = WireEnum.encoder
    given Decoder[Kind] = WireEnum.decoder
    given Schema[Kind] = WireEnum.schema

  final case class KindCount(kind: String, count: Int) derives KindlingsCodecAsObject, KindlingsSchema
  object KindCount:
    given Schema[KindCount] = summon[KindlingsSchema[KindCount]].schema

  final case class Link(targetKind: String, target: String)

  object Links:

    val TargetArticle = "article"
    val TargetSource = "source"

    export com.histmersion.api.text.WikiLinks.{isCitekeyShaped, normalize, occurrences, targets, Occurrence}

    def classify(targets: scala.List[String], isSource: String => Boolean): scala.List[Link] =
      targets.map { t =>
        if isCitekeyShaped(t) && isSource(t) then Link(TargetSource, t)
        else Link(TargetArticle, t)
      }

    def links(body: String, isSource: String => Boolean): scala.List[Link] =
      classify(targets(body), isSource)

    def hrefFor(target: String, citekeyOf: String => Option[String], isArticle: String => Boolean): Option[String] =
      citekeyOf(target)
        .map(ck => s"/source/$ck")
        .orElse(Option.when(isArticle(target))(s"/article/$target"))

    def linkMarkdown(
        md: String,
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

    def resolveMarkdown(md: String, isSource: String => Boolean): String =
      linkMarkdown(md, t => Some(if isCitekeyShaped(t) && isSource(t) then s"/source/$t" else s"/article/$t"))

  /** The articles' lint rules: a thin or stand-in summary, filler phrases, uncited paragraphs, unpaired critiques. */
  object Lint:

    val FillerPhrases: scala.List[String] = scala.List(
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

    def articleThinSummary(rows: scala.List[(String, String)]): scala.List[LintItem] =
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

    def articleFiller(articles: scala.List[(String, String)]): scala.List[LintItem] =
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

    def articleUncitedParagraphs(articles: scala.List[(String, String)]): scala.List[LintItem] =
      articles
        .flatMap { (slug, body) =>
          val paras = extractParagraphs(body)
          val uncited = paras.filter(p => WikilinkCitekey.findFirstIn(p).isEmpty)
          if uncited.isEmpty then None
          else Some(LintItem(slug, s"${uncited.size} paragraph(s) with no [[citekey]]: \"${uncited.head.take(60)}…\""))
        }
        .sortBy(_.citekey)

    def articleCritiquePairing(
        articles: scala.List[(String, String)],
        resolve: String => Option[String],
        edges: scala.List[com.histmersion.api.research.Coverage.Edge],
        work: String => Option[CritiquePairing.Work]
    ): scala.List[LintItem] =
      articles
        .flatMap((slug, body) => CritiquePairing.hits(body, resolve, edges, work).map(h => LintItem(slug, h.line)))
        .sortBy(_.citekey)

  final case class List(
      total: Int,
      articles: scala.List[Summary],
      offset: Int = 0,
      next: Option[Int] = None,
      kinds: scala.List[KindCount] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def page: OffsetPage = OffsetPage(total, offset, articles.size)
  object List:
    given Schema[List] = summon[KindlingsSchema[List]].schema
    def of(total: Int, rows: scala.List[Summary], offset: Int, kinds: scala.List[KindCount] = Nil): List =
      List(total, rows, offset, OffsetPage(total, offset, rows.size).next, kinds)

    def byKind(counts: scala.List[KindCount]): scala.List[KindCount] =
      counts.filter(_.count > 0).sortBy(k => (-k.count, k.kind))

  final case class Patch(
      title: Option[String] = None,
      summary: Option[String] = None,
      body: Option[String] = None,
      append: Option[String] = None,
      section: Option[String] = None,
      tags: Option[ListPatch] = None,
      regions: Option[scala.List[String]] = None,
      period: Option[Period.Input] = None,
      kind: Option[String] = None,
      ifUpdatedAt: Option[String] = None,
      filter: Option[Filter] = None,
      reasons: scala.List[com.histmersion.api.research.Coverage.Reason] = Nil,
      critiqueReasons: scala.List[CritiqueReason] = Nil
  ) derives KindlingsSchema:
    def onlyCritiques: Boolean =
      critiqueReasons.nonEmpty && copy(critiqueReasons = Nil, ifUpdatedAt = None) == Patch()
  object Patch:
    given Schema[Patch] = summon[KindlingsSchema[Patch]].schema

    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  object Plans:

    final case class FilterProposal(
        slug: String,
        filter: Filter,
        rationale: String = "",
        total: Int = 0,
        cited: Int = 0,
        citedIn: Int = 0,
        missed: scala.List[String] = Nil,
        sample: scala.List[String] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema:
      def recall: Double = if cited == 0 then 1.0 else citedIn.toDouble / cited
    object FilterProposal:
      given Schema[FilterProposal] = summon[KindlingsSchema[FilterProposal]].schema

    final case class ProposedArticle(
        slug: String,
        title: String,
        kind: String,
        filter: Filter,
        why: String = "",
        hits: Int = 0,
        overlap: Double = 0,
        overlapWith: Option[String] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema
    object ProposedArticle:
      given Schema[ProposedArticle] = summon[KindlingsSchema[ProposedArticle]].schema

    final case class ArticlePlan(
        goal: String,
        rationale: String = "",
        articles: scala.List[ProposedArticle] = Nil,
        dropped: scala.List[String] = Nil
    ) derives KindlingsCodecAsObject,
          KindlingsSchema:
      def isEmpty: Boolean = articles.isEmpty
    object ArticlePlan:
      given Schema[ArticlePlan] = summon[KindlingsSchema[ArticlePlan]].schema

    def filterLine(f: Filter): String = f.line

    def filterBlock(p: FilterProposal): String =
      val missed =
        if p.missed.isEmpty then "" else s"\n- cited but not returned: ${p.missed.map(c => s"[[$c]]").mkString(", ")}"
      val sample = if p.sample.isEmpty then ""
      else s"\n- returned, not cited (a few): ${p.sample.map(c => s"[[$c]]").mkString(", ")}"
      s"${p.filter.line} → ${Plural.count(p.total, "source")}; returns ${p.citedIn} of the ${Plural.count(p.cited, "cited source")}" +
        missed + sample

    def planBlock(p: ArticlePlan): String =
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
      title: String,
      kind: String,
      body: String,
      summary: Option[String] = None,
      tags: scala.List[String] = Nil,
      regions: scala.List[String] = Nil,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      filter: Option[Filter] = None,
      reasons: scala.List[com.histmersion.api.research.Coverage.Reason] = Nil
  ) derives KindlingsSchema
  object Put:
    given Schema[Put] = summon[KindlingsSchema[Put]].schema

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

  final case class Ref(slug: String, title: String, summary: Option[String] = None)

  final case class ReviewView(
      slug: String,
      badge: ReviewBadge,
      outcome: Option[String] = None,
      updatedAt: Option[String] = None,
      rounds: scala.List[ReviewRound] = Nil,
      goals: scala.List[ReviewGoal] = Nil,
      approvals: scala.List[ReviewApproval] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ReviewView:
    given Schema[ReviewView] = summon[KindlingsSchema[ReviewView]].schema

    def reviewerName(wire: String): String = wire match
      case "rules"       => "rules reviewer"
      case "cold-reader" => "cold reader"
      case "skeptic"     => "skeptic"
      case "mechanical"  => "mechanical check"
      case other         => other

    def outcomeLine(f: ReviewFinding): String =
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

    def markdown(r: ReviewView): String =
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
      slug: String,
      title: String,
      kind: String,
      body: String,
      summary: Option[String],
      tags: scala.List[String],
      regions: scala.List[String],
      periodStart: Option[Long],
      periodEnd: Option[Long],
      updatedAt: Option[String] = None,
      uid: Option[java.util.UUID] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Row:
    given Schema[Row] = summon[KindlingsSchema[Row]].schema

  final case class Summary(
      slug: String,
      title: String,
      kind: String,
      summary: Option[String],
      tags: scala.List[String],
      regions: scala.List[String],
      periodStart: Option[Long],
      periodEnd: Option[Long],
      updatedAt: Option[String] = None,
      review: Option[ReviewBadge] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Summary:
    given Schema[Summary] = summon[KindlingsSchema[Summary]].schema

  final case class Version(
      id: Long,
      slug: String,
      summary: Option[String],
      updatedAt: String,
      createdAt: String,
      wordCount: Int
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Version:
    given Schema[Version] = summon[KindlingsSchema[Version]].schema

  final case class VersionFull(
      id: Long,
      slug: String,
      body: String,
      summary: Option[String],
      updatedAt: String,
      createdAt: String
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object VersionFull:
    given Schema[VersionFull] = summon[KindlingsSchema[VersionFull]].schema

  final case class View(
      article: Row,
      backlinks: scala.List[Backlink],
      sourcesCited: scala.List[CitedSource],
      formatting: scala.List[FormatIssue] = Nil,
      coverage: Option[com.histmersion.api.research.Coverage] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object View:
    given Schema[View] = summon[KindlingsSchema[View]].schema
