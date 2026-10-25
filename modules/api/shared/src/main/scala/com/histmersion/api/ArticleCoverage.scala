package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec, Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.{SourceStatus, WireEnumValues}

/** COVERAGE (#958) — the part of an article that says which sources it was compiled from, which of
  * them it cites, and why it leaves the others out: the "Not used" list, its counts, and the
  * mechanical half of the `_Method_` note.
  *
  * A model used to write all of this by hand, and got it wrong: sources listed as "nothing found"
  * that discuss the topic, counts that did not match the filter. Now the article RECORDS its filter
  * ([[ArticleFilter]]) and a reason per left-out source ([[CoverageReason]]); code computes the rest
  * from the live corpus ([[ArticleCoverage.compute]]) and renders it ([[ArticleCoverage.sections]])
  * every time the article is read — the page, `get_article`, the export — exactly as the "Sources
  * cited" footer is generated rather than written. A model supplies only the REASONS.
  *
  * Everything here is pure; `server.Handlers.articleCoverage` gathers the inputs from the database. */

/** The filter an article is compiled from — the source set its coverage is computed against. The
  * structured part selects the sources every one of which must end up cited or given a reason;
  * `extraQueries` are further free-text searches the writer ran, reported in the Method note with
  * the hits the article cites, never part of the set that must be accounted for. */
final case class ArticleFilter(
    query: Option[String] = None,
    topics: List[String] = Nil,
    tags: List[String] = Nil,
    status: List[String] = Nil,
    places: List[String] = Nil,
    authors: List[String] = Nil,
    /** Deep-time window, signed years (BCE negative). */
    from: Option[Long] = None,
    to: Option[Long] = None,
    extraQueries: List[String] = Nil
):
  /** Nothing selects a source (`extraQueries` alone do not make a filter). */
  def isEmpty: Boolean =
    query.forall(_.trim.isEmpty) && topics.isEmpty && tags.isEmpty && status.isEmpty && places.isEmpty &&
      authors.isEmpty && from.isEmpty && to.isEmpty

  /** The corpus filter it stands for (no paging: coverage reads the whole set). */
  def toFilter: Filter =
    Filter.of(q = query, tags = tags, authors = authors, places = places, topics = topics, status = status,
      from = from, to = to)

  /** The filter line, in the writing guide's `search {…}` form. */
  def line: String =
    def list(xs: List[String]) = xs.map(ArticleFilter.quoted).mkString("[", ", ", "]")
    val parts = query.map(q => s"query: ${ArticleFilter.quoted(q)}").toList ++
      Option.when(topics.nonEmpty)(s"topics: ${list(topics)}") ++
      Option.when(tags.nonEmpty)(s"tags: ${list(tags)}") ++
      Option.when(status.nonEmpty)(s"status: ${list(status)}") ++
      Option.when(places.nonEmpty)(s"places: ${list(places)}") ++
      Option.when(authors.nonEmpty)(s"authors: ${list(authors)}") ++
      from.map(f => s"from: $f") ++ to.map(t => s"to: $t")
    s"search {${parts.mkString(", ")}}"

object ArticleFilter:
  given Schema[ArticleFilter] = KindlingsSchema.derived[ArticleFilter].schema
    .description("The filter the article is compiled from; an empty one ({}) clears it")
    .modify(_.query)(_.description("Free text the sources must match (the search query)"))
    .modify(_.topics)(_.description("Topics the sources must all have"))
    .modify(_.tags)(_.description("Tags the sources must all have"))
    .modify(_.status)(_.description("Statuses, any of (current, contested, …)"))
    .modify(_.places)(_.description("Places, any of (a gazetteer id or a name)"))
    .modify(_.authors)(_.description("Authors, any of (a name substring)"))
    .modify(_.from)(_.description("Deep-time window start, signed year (BCE negative)"))
    .modify(_.to)(_.description("Deep-time window end, signed year (BCE negative)"))
    .modify(_.extraQueries)(_.description(
      "Further free-text searches the writer ran; reported in the Method note, not part of the set every source of which must be cited or given a reason"))
  /** Strict (#813): an unknown key is refused, not dropped. */
  given Codec.AsObject[ArticleFilter] = StrictJson.codec(KindlingsCodecAsObject.derived[ArticleFilter])

  /** A free-text value as the filter line shows it: in double quotes, inner quotes escaped. */
  private[api] def quoted(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  /** Validate and tidy a filter as given: values trimmed, blanks and duplicates dropped, statuses
    * from the taxonomy, `from` not after `to`. `None` = no criterion left: the filter is CLEARED. */
  def normalise(f: ArticleFilter): Either[String, Option[ArticleFilter]] =
    def clean(xs: List[String]) = xs.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).distinct
    val status  = clean(f.status).map(_.toLowerCase(java.util.Locale.ROOT))
    val unknown = status.filterNot(s => SourceStatus.values.exists(_.wire == s))
    val tidy = ArticleFilter(f.query.map(_.trim).filter(_.nonEmpty), clean(f.topics), clean(f.tags), status,
      clean(f.places), clean(f.authors), f.from, f.to, f.extraQueries.map(_.trim).filter(_.nonEmpty).distinct)
    if unknown.nonEmpty then
      Left(s"unknown status ${unknown.map(s => s"'$s'").mkString(", ")} — one of ${SourceStatus.values.map(_.wire).mkString(", ")}")
    else if tidy.from.zip(tidy.to).exists(_ > _) then Left(s"the filter's from (${tidy.from.get}) is after its to (${tidy.to.get})")
    else if tidy.isEmpty && tidy.extraQueries.nonEmpty then
      Left("extraQueries without a filter: give the query, topics or tags the article is compiled from")
    else Right(Option.when(!tidy.isEmpty)(tidy))

/** Why the article leaves one source of its filter out. An empty `reason` in a patch REMOVES it. */
final case class CoverageReason(citekey: String, reason: String) derives KindlingsCodecAsObject
object CoverageReason:
  given Schema[CoverageReason] = KindlingsSchema.derived[CoverageReason].schema
    .description("Why the article does not cite one source its filter returns")
    .modify(_.citekey)(_.description("The source's citekey"))
    .modify(_.reason)(_.description(
      "One plain sentence: why it is left out (wrong region, period or sub-problem; no finding on the question). Empty removes it"))

  /** The longest reason kept: one line of a list, not a paragraph. */
  val MaxChars = 500

  /** Merge `stated` into `current`: a reason replaces the source's previous one, an empty one removes
    * it. Each reason is folded onto one line (it is a list item). A key that is not citekey-shaped, or
    * a reason longer than [[MaxChars]], is refused. Pure. */
  def merge(current: Map[String, String], stated: List[CoverageReason]): Either[String, Map[String, String]] =
    stated.foldLeft[Either[String, Map[String, String]]](Right(current)) { (acc, r) =>
      acc.flatMap { m =>
        val ck   = Option(r.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse("")
        val text = Option(r.reason).map(_.trim.replaceAll("\\s+", " ")).getOrElse("")
        if !ArticleLinks.isCitekeyShaped(ck) then Left(s"reason for '${r.citekey}': not a citekey")
        else if text.length > MaxChars then Left(s"reason for $ck: ${text.length} characters (at most $MaxChars — one sentence)")
        else if text.isEmpty then Right(m - ck)
        else Right(m.updated(ck, text))
      }
    }

  /** The stored map as a list, in citekey order (the wire form). */
  def list(m: Map[String, String]): List[CoverageReason] = m.toList.sortBy(_._1).map(CoverageReason(_, _))

/** One source of the filter's result set. */
final case class CoverageSource(citekey: String, title: String, year: Option[Int], status: SourceStatus,
    authors: List[String] = Nil) derives KindlingsCodecAsObject
object CoverageSource:
  given Schema[CoverageSource] = KindlingsSchema.derived[CoverageSource].schema

/** A source the article does not cite, with its stored reason (`None` = not given yet). */
final case class CoverageEntry(source: CoverageSource, reason: Option[String]) derives KindlingsCodecAsObject
object CoverageEntry:
  given Schema[CoverageEntry] = KindlingsSchema.derived[CoverageEntry].schema

/** A typed disagreement edge (`from` challenges/refutes/updates/superseded_by `to`). */
final case class CoverageEdge(from: String, kind: String, to: String) derives KindlingsCodecAsObject
object CoverageEdge:
  given Schema[CoverageEdge] = KindlingsSchema.derived[CoverageEdge].schema

/** One of the writer's further searches: how many sources it returns, and which of them the article cites. */
final case class CoverageSearch(query: String, total: Int, cited: List[String]) derives KindlingsCodecAsObject
object CoverageSearch:
  given Schema[CoverageSearch] = KindlingsSchema.derived[CoverageSearch].schema

final case class StatusCount(status: SourceStatus, count: Int) derives KindlingsCodecAsObject
object StatusCount:
  given Schema[StatusCount] = KindlingsSchema.derived[StatusCount].schema

/** How the stored coverage has drifted from the live corpus and the body. */
enum DriftKind(val wire: String):
  /** A source the filter returns that is neither cited nor given a reason. */
  case Unplaced         extends DriftKind("unplaced")
  /** A reason for a source the body now cites (it is not shown; it is stale). */
  case ReasonForCited   extends DriftKind("reason-for-cited")
  /** A reason for a source the filter no longer returns. */
  case ReasonOutside    extends DriftKind("reason-outside-filter")

object DriftKind:
  given WireEnumValues[DriftKind]:
    def all: Array[DriftKind] = DriftKind.values
    def wire(e: DriftKind): String = e.wire
    def fromWire(s: String): Option[DriftKind] = DriftKind.values.find(_.wire == s.trim.toLowerCase)
  given Encoder[DriftKind] = WireEnum.encoder
  given Decoder[DriftKind] = WireEnum.decoder
  given Schema[DriftKind]  = WireEnum.schema

final case class CoverageDrift(kind: DriftKind, citekeys: List[String]) derives KindlingsCodecAsObject
object CoverageDrift:
  given Schema[CoverageDrift] = KindlingsSchema.derived[CoverageDrift].schema

/** `GET /api/articles/{slug}/coverage`: the article's filter against the live corpus.
  *
  * @param total     how many sources the filter returns
  * @param checked   how many of them were read (`total` unless it passed `limits.coverage-sources`;
  *                  the first ones in citekey order are read)
  * @param cited     the filter's sources the body cites
  * @param notUsed   the filter's (read) sources the body does not cite, each with its reason
  * @param citedOutside sources the body cites that the filter does not return
  * @param statuses  the status breakdown of the read sources, in taxonomy order
  * @param edges     typed disagreement edges touching a cited source
  * @param searches  the writer's further searches
  * @param reasons   every stored reason (also those that drifted) */
final case class Coverage(
    slug: String,
    filter: ArticleFilter,
    filterLine: String,
    total: Int,
    checked: Int,
    cited: List[CoverageSource],
    notUsed: List[CoverageEntry],
    citedOutside: List[String],
    statuses: List[StatusCount],
    edges: List[CoverageEdge],
    searches: List[CoverageSearch],
    reasons: List[CoverageReason],
    drift: List[CoverageDrift],
    /** Catalogue events in the filter's period and region the body never names (#1449) — leads for
      * the writer, never prose. */
    eventLeads: List[ArticleEventLead] = Nil,
    /** The critique checklist (§6.8 of docs/research-review-convergence.md): every disagreement edge
      * touching a cited source, addressed, reasoned or open. */
    critiques: List[CritiquePair] = Nil
) derives KindlingsCodecAsObject:
  def capped: Boolean = checked < total
  def unplaced: List[String] = drift.filter(_.kind == DriftKind.Unplaced).flatMap(_.citekeys)
object Coverage:
  given Schema[Coverage] = KindlingsSchema.derived[Coverage].schema

object ArticleCoverage:

  /** The edge kinds that record a disagreement (writing guide, the four disagreement steps). */
  val DisagreementKinds: List[String] = List("challenges", "refutes", "updates", "superseded_by")

  /** Statuses a reader is told at the source's mention (writing guide rule 2). */
  private val Stated: Set[SourceStatus] = Set(SourceStatus.Contested, SourceStatus.Superseded, SourceStatus.Debunked,
    SourceStatus.Retracted, SourceStatus.Unassessed)

  /** Statuses the Method note says are absent when none of the sources has them. */
  private val Disputed: List[SourceStatus] =
    List(SourceStatus.Contested, SourceStatus.Superseded, SourceStatus.Debunked, SourceStatus.Retracted)

  /** THE coverage computation, over plain data the handler gathered.
    *
    * @param hits      the filter's sources as read (at most `limits.coverage-sources`, citekey order)
    * @param total     how many the filter returns in all
    * @param citedInFilter the sources the body cites that the filter returns (exact, whatever the cap)
    * @param cited     every source the body cites (canonical citekeys; a hand-written Not used
    *                  section is NOT citation, see [[withoutNotUsed]])
    * @param reasons   the stored reasons, by citekey
    * @param edges     disagreement edges touching any cited source (other kinds are dropped here) */
  def compute(slug: String, filter: ArticleFilter, total: Int, hits: List[CoverageSource], citedInFilter: Set[String],
      cited: List[String], reasons: Map[String, String], edges: List[CoverageEdge],
      searches: List[CoverageSearch]): Coverage =
    val citedSet = cited.toSet
    val read     = hits.distinctBy(_.citekey).sortBy(_.citekey)
    val readSet  = read.map(_.citekey).toSet
    val notUsed  = read.filterNot(s => citedSet(s.citekey)).map(s => CoverageEntry(s, reasons.get(s.citekey)))
    val capped   = read.size < total
    val outside  = cited.distinct.filterNot(ck => citedInFilter(ck) || readSet(ck))
    val statuses = SourceStatus.values.toList.flatMap(st => Option(read.count(_.status == st)).filter(_ > 0).map(StatusCount(st, _)))
    val keptEdges = edges.filter(e => DisagreementKinds.contains(e.kind) && (citedSet(e.from) || citedSet(e.to)))
      .distinct.sortBy(e => (e.from, e.kind, e.to))
    val drift = List(
      CoverageDrift(DriftKind.Unplaced, notUsed.filter(_.reason.isEmpty).map(_.source.citekey)),
      CoverageDrift(DriftKind.ReasonForCited, reasons.keys.toList.filter(citedSet).sorted),
      // with a capped read a reason may belong to an unread hit: say nothing rather than something wrong
      CoverageDrift(DriftKind.ReasonOutside,
        if capped then Nil else reasons.keys.toList.filterNot(ck => readSet(ck) || citedSet(ck)).sorted)
    ).filter(_.citekeys.nonEmpty)
    Coverage(slug, filter, filter.line, total, read.size, read.filter(s => citedSet(s.citekey)), notUsed, outside,
      statuses, keptEdges, searches, CoverageReason.list(reasons), drift)

  /** The sources still unplaced if the body cited `cited` and the reasons were `reasons` — how a
    * fixer's answer is checked against the coverage it was given. */
  def unplacedAfter(c: Coverage, cited: Set[String], reasons: Map[String, String]): List[String] =
    c.notUsed.map(_.source.citekey).filterNot(ck => cited(ck) || reasons.contains(ck))

  // ── rendering ─────────────────────────────────────────────────────────────────────────────────

  /** Where the generated sections start and end in an EXPORTED note, so an import can drop them (the
    * body never holds them). HTML comments: Obsidian and the page do not show them. */
  val GeneratedStart = "<!-- generated from the article's filter: edits here are not kept -->"
  val GeneratedEnd   = "<!-- end of generated sections -->"

  /** The heading the generated list carries (and a hand-written one is recognised by). */
  val NotUsedHeading = "Not used"

  /** The generated sections, as Markdown: the Not used list (a lead-in with the counts, ending in
    * ':' so it is a list's lead-in, not an uncited paragraph) and the one-line `_Method:_` note. */
  def sections(c: Coverage): String = s"${notUsed(c)}\n\n${method(c)}"

  /** `sections` between the export markers. */
  def exported(c: Coverage): String = s"$GeneratedStart\n\n${sections(c)}\n\n$GeneratedEnd"

  /** `## Not used`, a lead-in with the counts, and one line per reason (sources with the same reason
    * share a line; those with none share the last: "not yet assessed"). */
  def notUsed(c: Coverage): String =
    val src = Plural.noun(c.total, "source")
    val citedN = c.cited.size
    val n = c.notUsed.size
    val scope = if c.capped then s"; the first ${c.checked} (by citekey) were checked" else ""
    val lead =
      if c.total == 0 then "_The filter returns no source._"
      else if n == 0 then
        if c.capped then s"_The filter returns ${c.total} $src; all ${c.checked} checked are cited above._"
        else s"_${if c.total == 1 then "The one source" else s"All ${c.total} sources"} the filter returns ${if c.total == 1 then "is" else "are"} cited above._"
      else
        val citedText = if citedN == 0 then "None of them is" else Plural.count(citedN, "of them is", "of them are")
        s"The filter returns ${c.total} $src$scope. $citedText cited above; " +
          s"${if n == 1 then "the other one is" else s"the other $n are"} listed here with the reason ${if n == 1 then "it is" else "each is"} left out:"
    val groups = c.notUsed.groupBy(_.reason).toList.sortBy((r, es) => (r.isEmpty, es.map(_.source.citekey).min))
    val lines = groups.map { (reason, es) =>
      val who = es.sortBy(_.source.citekey).map(e => s"[[${e.source.citekey}]]${statusNote(e.source.status)}").mkString(", ")
      s"- $who — ${reason.fold("not yet assessed")(endSentence)}"
    }
    s"## $NotUsedHeading\n\n$lead" + (if lines.isEmpty then "" else "\n\n" + lines.mkString("\n"))

  private def statusNote(s: SourceStatus): String = if Stated(s) then s" (${s.wire})" else ""

  private def endSentence(s: String): String = if s.endsWith(".") || s.endsWith("?") || s.endsWith("!") then s else s"$s."

  /** The one-line `_Method:_` note: the filter and its total, the status breakdown, the further
    * searches, citations outside the filter, and the disagreement edges touching the cited sources. */
  def method(c: Coverage): String =
    val statuses =
      if c.statuses.isEmpty then Nil
      else
        val have = c.statuses.map(s => s"${s.count} ${s.status.wire}").mkString(", ")
        val none = Disputed.filterNot(d => c.statuses.exists(_.status == d)).map(_.wire)
        val noneText = if none.isEmpty then "" else s"; none ${orList(none)}"
        List(s"statuses${if c.capped then s" of the ${c.checked} checked" else ""}: $have$noneText")
    val searches = c.searches.map { s =>
      val cites = if s.cited.isEmpty then "none of them cited" else s"cited: ${s.cited.map(ck => s"[[$ck]]").mkString(", ")}"
      s"further search ${escape(ArticleFilter.quoted(s.query))} → ${s.total} ($cites)"
    }
    val outside = Option.when(c.citedOutside.nonEmpty)(
      s"cited from outside the filter: ${c.citedOutside.map(ck => s"[[$ck]]").mkString(", ")}").toList
    val edges =
      if c.edges.isEmpty then "no challenges, refutes, updates or superseded_by edge touches a cited source (not evidence of agreement)"
      else s"disagreement edges touching the cited sources: ${c.edges.map(e => s"[[${e.from}]] ${e.kind} [[${e.to}]]").mkString("; ")}"
    val parts = s"${escape(c.filterLine)} → ${c.total}" :: statuses ++ searches ++ outside ++ List(edges) ++
      Critiques.methodPart(c.critiques).toList
    s"_Method: ${parts.mkString("; ")}._"

  private def orList(xs: List[String]): String = xs match
    case Nil         => ""
    case one :: Nil  => one
    case _           => s"${xs.init.mkString(", ")} or ${xs.last}"

  /** Text inside the italic Method line: an `_` or `*` of a user's query would end the emphasis. */
  private def escape(s: String): String = s.replace("*", "\\*").replace("_", "\\_")

  // ── bodies that still carry hand-written sections ──────────────────────────────────────────────

  private def isHeading(l: String) = l.trim.startsWith("#")
  private def level(l: String)     = l.trim.takeWhile(_ == '#').length

  /** `text` without its hand-written "Not used" section (heading to the next heading of the same or
    * higher level): the sources named there are listed, not cited. */
  def withoutNotUsed(text: String): String =
    val lines = text.split("\n", -1).toList
    lines.indexWhere(l => isHeading(l) && l.trim.replaceAll("^#+\\s*", "").trim.toLowerCase.startsWith("not used")) match
      case -1 => text
      case i =>
        val lv   = level(lines(i))
        val rest = lines.drop(i + 1)
        val end  = rest.indexWhere(l => isHeading(l) && level(l) <= lv)
        (lines.take(i) ++ (if end < 0 then Nil else rest.drop(end))).mkString("\n")

  /** Does the body carry a hand-written `_Method` note (a line starting `_Method`)? */
  def hasMethodLine(text: String): Boolean = text.linesIterator.exists(_.trim.startsWith("_Method"))

  /** An exported note's body with the generated block (between the markers) removed — what an
    * import stores. Text outside the markers is untouched. */
  def stripGenerated(body: String): String =
    val s = body.indexOf(GeneratedStart)
    if s < 0 then body
    else
      val e = body.indexOf(GeneratedEnd, s)
      val tail = if e < 0 then "" else body.substring(e + GeneratedEnd.length)
      (body.substring(0, s).stripTrailing + (if tail.trim.isEmpty then "" else "\n\n" + tail.dropWhile(_.isWhitespace))).trim

  /** How the coverage reads in a terminal: the filter, counts, drift, then the generated sections. */
  def markdown(c: Coverage): String =
    val drift =
      if c.drift.isEmpty then "No drift: every source the filter returns is cited or has a reason."
      else c.drift.map { d =>
        val what = d.kind match
          case DriftKind.Unplaced       => "neither cited nor given a reason"
          case DriftKind.ReasonForCited => "given a reason but cited in the body (the reason is stale)"
          case DriftKind.ReasonOutside  => "given a reason but no longer returned by the filter"
        s"- ${Plural.count(d.citekeys.size, "source")} $what: ${d.citekeys.map(ck => s"[[$ck]]").mkString(", ")}"
      }.mkString("\n")
    val critiques = Critiques.markdown(c.critiques)
    s"## Coverage of [[${c.slug}]]\n\n`${c.filterLine}` → ${c.total} (${c.cited.size} cited, ${c.notUsed.size} not used" +
      s"${if c.capped then s", ${c.total - c.checked} not checked" else ""})\n\n### Drift\n\n$drift" +
      (if critiques.isEmpty then "" else s"\n\n$critiques") + s"\n\n### Generated sections\n\n${sections(c)}" +
      (if c.eventLeads.isEmpty then "" else "\n\n" + ArticleEventLead.markdown(c.eventLeads))
