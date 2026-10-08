package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The `enrich-source` pipeline's typed answers and its pure logic (docs/research-embedded-agents.md
  * §4.2): what each LLM step must return, how the body is cut into windows, how an answer is
  * verified, and how the verified result becomes ONE `SourcePatch`. The server runs the steps; every
  * function here is pure and unit-tested. */
object EnrichSource:
  import AgentVerify.*

  // ── LLM answers (their JSON schemas are what the provider is asked to fill) ─────────────────────

  /** A year as the source states it. The model never converts; [[AgentVerify.toSignedYear]] does. */
  final case class StatedYear(value: Long, unit: String) derives KindlingsCodecAsObject
  object StatedYear:
    given Schema[StatedYear] = KindlingsSchema.derived[StatedYear].schema
      .modify(_.value)(_.description("The number as written, always positive (3300 for 3300 BCE)"))
      .modify(_.unit)(_.description("BCE | CE | calBP (calibrated years before 1950) | BP (UNcalibrated radiocarbon years)"))

  final case class StatedPeriod(start: StatedYear, end: StatedYear, name: Option[String] = None)
      derives KindlingsCodecAsObject
  object StatedPeriod:
    given Schema[StatedPeriod] = KindlingsSchema.derived[StatedPeriod].schema

  final case class Classification(
      summary: String,
      topics: List[String] = Nil,
      regions: List[String] = Nil,
      tags: List[String] = Nil,
      period: Option[StatedPeriod],
      status: String,
      statusEvidence: String
  ) derives KindlingsCodecAsObject
  object Classification:
    given Schema[Classification] = KindlingsSchema.derived[Classification].schema
      .modify(_.summary)(_.description("3-6 sentences: what the source argues and on what evidence"))
      .modify(_.tags)(_.description("lowercase_snake_case; prefer tags the facets tool lists"))
      .modify(_.period)(_.description("The deep-time span the source is ABOUT (not its publication date); null if none"))
      .modify(_.status)(_.description("foundational | current | contested | superseded | debunked | retracted | unassessed"))
      .modify(_.statusEvidence)(_.description("Why that status, from the text; empty if unassessed"))

  final case class FindingProposal(
      claim: String,
      quote: String,
      place: Option[String],
      placeKind: Option[String],
      start: Option[StatedYear],
      end: Option[StatedYear],
      periodName: Option[String],
      tags: List[String] = Nil,
      confidence: String
  ) derives KindlingsCodecAsObject
  object FindingProposal:
    given Schema[FindingProposal] = KindlingsSchema.derived[FindingProposal].schema
      .modify(_.claim)(_.description("One claim the source makes, in your words, one sentence"))
      .modify(_.quote)(_.description("The sentence(s) of the text that state it, copied EXACTLY, 8-60 words"))
      .modify(_.place)(_.description("The place the claim is about, as a gazetteer would name it; null if none"))
      .modify(_.placeKind)(_.description(
        "What kind of place `place` is: site (a city, settlement or excavated site) | region (a country, landscape or province) | polity (a state, kingdom or empire) | water (a sea, lake or river) | unknown; null if no place"))
      .modify(_.confidence)(_.description("high | medium | low | contested — how firmly the SOURCE states it"))

  final case class Findings(findings: List[FindingProposal] = Nil) derives KindlingsCodecAsObject
  object Findings:
    given Schema[Findings] = KindlingsSchema.derived[Findings].schema

  final case class Notes(notes: String) derives KindlingsCodecAsObject
  object Notes:
    given Schema[Notes] = KindlingsSchema.derived[Notes].schema

  // ── checkpointed intermediate values ───────────────────────────────────────────────────────────

  /** One stretch of the body sent to the findings step; `heading` is the nearest heading above it. */
  final case class Window(index: Int, heading: Option[String], text: String) derives KindlingsCodecAsObject

  /** What `load` records: enough of the source for every later step, and its `updatedAt` for the
    * final patch's precondition. */
  final case class Loaded(
      citekey: String, title: String, authors: List[String], year: Option[Int], venue: Option[String],
      status: String, hasSummary: Boolean, hasNotes: Boolean, hasPeriod: Boolean, period: Option[Period],
      topics: List[String], regions: List[String], existingClaims: List[String],
      updatedAt: Option[String], head: String, windows: List[Window], tags: List[String] = Nil,
      doi: Option[String] = None
  ) derives KindlingsCodecAsObject

  /** A finding that passed verification, with its quote's match ratio. `placeKind` is the model's
    * judgement of what `place` is (`None` = no hint; absent from checkpoints older than #981). */
  final case class VerifiedFinding(claim: String, quote: String, ratio: Double, place: Option[String],
      start: Option[Long], end: Option[Long], periodName: Option[String], tags: List[String],
      confidence: String, placeKind: Option[Geocode.KindHint] = None) derives KindlingsCodecAsObject:
    /** The finding's own period, for a time-aware choice among dated places. */
    def period: Option[Period] = (start, end) match
      case (Some(s), Some(e)) => Some(Period(s, e))
      case (Some(s), None)    => Some(Period(s, s))
      case (None, Some(e))    => Some(Period(e, e))
      case _                  => None

  /** One window's verified findings. `proposed` = how many the accepted answer held, `quoteFailed` =
    * how many of those quotes were not found in the window (dropped on a last attempt) — what
    * `agent compare` reads the verifier's pass ratio from (None in checkpoints older than #992). */
  final case class VerifiedFindings(findings: List[VerifiedFinding], proposed: Option[Int] = None,
      quoteFailed: Option[Int] = None) derives KindlingsCodecAsObject

  /** What the geocoder chose for one finding's place, for a person to audit in the run trace:
    * `placeId`/`label` empty when the name is unknown, `approximate` saying why the point is only
    * near what was asked (a qualifier dropped, a placeholder point). */
  final case class GeocodeChoice(claim: String, asked: String, placeId: Option[String], label: Option[String],
      approximate: Option[String]) derives KindlingsCodecAsObject

  /** The `geocode` step: the findings with their points, and every choice behind them. */
  final case class Geocoded(findings: List[FindingInput], choices: List[GeocodeChoice]) derives KindlingsCodecAsObject

  /** A classification whose period has been converted to signed years. */
  final case class Classified(summary: String, topics: List[String], regions: List[String], tags: List[String],
      period: Option[Period], periodName: Option[String], status: SourceStatus, statusEvidence: String)
      derives KindlingsCodecAsObject

  final case class Patched(findingsAdded: Int, fieldsSet: List[String], statusProposal: Option[String])
      derives KindlingsCodecAsObject

  /** The `metadata` step: the record against OpenAlex/Crossref. `checked = false` with a `note` when it
    * could not be (no DOI, upstream off or down); the differences are run warnings, never writes. */
  final case class MetaChecked(checked: Boolean, differences: List[String], note: String = "")
      derives KindlingsCodecAsObject

  /** What `refs` / `assess` did (or why they were skipped). */
  final case class Upstreamed(ran: Boolean, summary: String, retracted: Boolean = false) derives KindlingsCodecAsObject

  /** The `status` step: whether the classify step's status proposal was applied, recorded for a
    * person (`approval` id), or needed nothing. */
  final case class StatusRouted(applied: Option[String], approval: Option[Long], note: String)
      derives KindlingsCodecAsObject

  /** The `close` step: the per-source lint, the acquisition item and the log line. */
  final case class Closed(line: String, lint: List[String], acquire: Option[String]) derives KindlingsCodecAsObject

  // ── pure logic ─────────────────────────────────────────────────────────────────────────────────

  private val Heading = "^#{1,6}\\s+(.+)$".r

  /** Cut `body` into paragraph-aligned windows of at most `windowChars`; when there are more than
    * `maxWindows`, keep `maxWindows` of them spread evenly from the first to the last (a book's
    * argument is not all in chapter one). Each window remembers the heading it falls under. */
  def windows(body: String, windowChars: Int, maxWindows: Int): List[Window] =
    val size  = math.max(500, windowChars)
    val paras = body.split("\\n\\s*\\n").toList.map(_.trim).filter(_.nonEmpty)
      .flatMap(p => if p.length <= size then List(p) else p.grouped(size).toList)
    // fold paragraphs into windows, tracking the last heading seen
    val (done, cur, curHead, _) = paras.foldLeft((Vector.empty[(Option[String], String)], "", Option.empty[String], Option.empty[String])) {
      case ((acc, buf, bufHead, lastHead), p) =>
        val head = p.linesIterator.nextOption().collect { case Heading(h) => h.trim }.orElse(lastHead)
        if buf.isEmpty then (acc, p, head, head)
        else if buf.length + 2 + p.length <= size then (acc, buf + "\n\n" + p, bufHead, head)
        else (acc :+ (bufHead, buf), p, head, head)
    }
    val all = (if cur.isEmpty then done else done :+ (curHead, cur)).toList
    val n   = math.max(1, maxWindows)
    val picked =
      if all.size <= n then all
      else if n == 1 then List(all.head)
      else (0 until n).map(i => all(math.round(i.toDouble * (all.size - 1) / (n - 1)).toInt)).distinct.toList
    picked.zipWithIndex.map { case ((h, t), i) => Window(i + 1, h, t) }

  /** Turn a stated period into signed years, or say why not. A point (no end) is `start..start`. */
  def convert(p: StatedPeriod, maxAbsYear: Long, within: Option[Period]): Either[String, Period] =
    for
      su <- YearUnit.parse(p.start.unit).toRight(s"unknown unit '${p.start.unit}' (BCE, CE, calBP or BP)")
      eu <- YearUnit.parse(p.end.unit).toRight(s"unknown unit '${p.end.unit}' (BCE, CE, calBP or BP)")
      s  <- toSignedYear(p.start.value, su)
      e  <- toSignedYear(p.end.value, eu)
      _  <- periodProblems(s, e, maxAbsYear, within) match
              case Nil => Right(())
              case ps  => Left(ps.mkString("; "))
    yield Period(s, e)

  /** `classify`: status in the vocabulary, a summary, a convertible and sane period. A period that
    * cannot be converted (raw BP) is dropped with a warning on the last attempt rather than failing
    * the whole classification. */
  def checkClassification(c: Classification, maxAbsYear: Long, last: Boolean): Either[List[String], (Classified, List[String])] =
    val status  = AgentVerify.status(c.status)
    val period  = c.period.map(p => convert(p, maxAbsYear, None))
    val summary = Option(c.summary).map(_.trim).filter(_.nonEmpty)
    val problems = status.left.toOption.toList ++ summary.fold(List("the summary is empty"))(_ => Nil) ++
      period.flatMap(_.left.toOption).map(m => s"period: $m").toList
    val fatal = status.isLeft || summary.isEmpty
    if problems.isEmpty || (last && !fatal) then
      val clean = (l: List[String]) => l.map(_.trim).filter(_.nonEmpty).distinct
      Right((Classified(summary.get, clean(c.topics), clean(c.regions), clean(c.tags).map(normaliseTag),
        period.flatMap(_.toOption), c.period.flatMap(_.name), status.toOption.get, c.statusEvidence.trim),
        period.flatMap(_.left.toOption).map(m => s"period dropped: $m").toList))
    else Left(problems)

  /** Tags are lowercase_snake_case in this corpus (the `non-canonical-tag` lint rule). */
  def normaliseTag(t: String): String =
    t.trim.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", "")

  /** `findings`: every quote must be in THIS window (OCR-tolerantly), every confidence in the
    * vocabulary, every period convertible and sane. A finding that fails is rejected — the whole
    * answer is sent back while attempts remain; on the last attempt the failures are dropped with a
    * warning each and the rest kept. Findings repeating an existing claim are dropped silently. */
  def checkFindings(f: Findings, window: Window, minRatio: Double, maxAbsYear: Long, within: Option[Period],
      existingClaims: Set[String], last: Boolean): Either[List[String], (VerifiedFindings, List[String])] =
    val quotes  = f.findings.map(p => findQuote(p.quote, window.text, minRatio))
    val results = f.findings.zip(quotes).zipWithIndex.map { case ((p, quote), i) =>
      val label = s"finding ${i + 1} (${p.claim.take(60)})"
      val conf  = AgentVerify.confidence(p.confidence)
      val per: Either[String, Option[Period]] = (p.start, p.end) match
        case (None, None)    => Right(None)
        case (Some(s), e)    => convert(StatedPeriod(s, e.getOrElse(s)), maxAbsYear, None).map(Some(_))
        case (None, Some(e)) => convert(StatedPeriod(e, e), maxAbsYear, None).map(Some(_))
      val issues = List(
        Option.when(p.claim.trim.isEmpty)("the claim is empty"),
        Option.when(!quote.accepted)(s"the quote is not in the text (best match ${BigDecimal(quote.ratio).setScale(2, BigDecimal.RoundingMode.HALF_UP)}) — copy it exactly"),
        conf.left.toOption,
        per.left.toOption.map(m => s"period: $m")).flatten
      if issues.nonEmpty then Left(s"$label: ${issues.mkString("; ")}")
      else Right(VerifiedFinding(p.claim.trim, p.quote.trim, quote.ratio, p.place.map(_.trim).filter(_.nonEmpty),
        per.toOption.flatten.map(_.start), per.toOption.flatten.map(_.end), p.periodName.map(_.trim).filter(_.nonEmpty),
        p.tags.map(normaliseTag).filter(_.nonEmpty).distinct, conf.toOption.get.toString.toLowerCase(java.util.Locale.ROOT),
        p.placeKind.map(Geocode.KindHint.parse).filter(_ != Geocode.KindHint.Unknown)))
    }
    val rejected = results.collect { case Left(m) => m }
    if rejected.nonEmpty && !last then Left(rejected)
    else
      val norm = existingClaims.map(c => normalise(c))
      val kept = results.collect { case Right(v) => v }.filterNot(v => norm.contains(normalise(v.claim)))
        .distinctBy(v => normalise(v.quote))
      // outside the source's own span is suspicious, not wrong (a source cites older and later events)
      val outside = for
        v <- kept; s <- v.start; e <- v.end; p <- within
        problem <- periodProblems(s, e, maxAbsYear, Some(p)).lastOption
      yield s"check '${v.claim.take(60)}': $problem"
      Right((VerifiedFindings(kept, Some(f.findings.size), Some(quotes.count(!_.accepted))),
        rejected.map(m => s"dropped $m") ++ outside))

  /** `notes`: non-empty, and every `[[link]]` names a citekey or an article. */
  def checkNotes(n: Notes, isCitekey: String => Boolean, isSlug: String => Boolean): Either[List[String], String] =
    val text = Option(n.notes).map(_.trim).getOrElse("")
    if text.isEmpty then Left(List("the notes are empty"))
    else linkProblems(text, isCitekey, isSlug) match
      case Nil => Right(text)
      case ps  => Left(ps)

  /** The ONE patch the run writes. It only FILLS: a summary, notes or period the source already has
    * is left alone (a person or an earlier run wrote it), topics/regions are merged, tags are added,
    * findings are added. A status is set only from `unassessed` to `current`/`foundational`; any
    * other proposal goes to a person (returned, not applied). */
  def patch(l: Loaded, c: Classified, findings: List[FindingInput], notes: Option[String])
      : (SourcePatch, List[String], Option[String]) =
    val merge = (have: List[String], add: List[String]) =>
      Option.when(add.exists(a => !have.contains(a)))((have ++ add).distinct)
    val setStatus = Option.when(l.status == SourceStatus.Unassessed.wire &&
      (c.status == SourceStatus.Current || c.status == SourceStatus.Foundational))(c.status)
    val proposal = Option.when(setStatus.isEmpty && c.status != SourceStatus.Unassessed && c.status.wire != l.status)(
      s"${c.status.wire}: ${c.statusEvidence}")
    val p = SourcePatch(
      metadata = setStatus.map(s => MetadataPatch(status = Some(s))),
      summary = Option.when(!l.hasSummary)(c.summary),
      notes = notes.filter(_ => !l.hasNotes),
      topics = merge(l.topics, c.topics),
      regions = merge(l.regions, c.regions),
      period = c.period.filter(_ => !l.hasPeriod).map(p => PeriodInput(p.start, p.end)),
      tags = Some(c.tags.filterNot(l.tags.contains)).filter(_.nonEmpty).map(t => ListPatch(add = t)),
      findings = Option.when(findings.nonEmpty)(FindingsPatch(add = findings)),
      ifUpdatedAt = l.updatedAt)
    val fields = List(
      p.metadata.map(_ => "status"), p.summary.map(_ => "summary"), p.notes.map(_ => "notes"),
      p.topics.map(_ => "topics"), p.regions.map(_ => "regions"), p.period.map(_ => "period"),
      p.tags.map(_ => "tags"), p.findings.map(f => s"${f.add.size} finding(s)")).flatten
    (p, fields, proposal)

  /** Our record against the upstream one (OpenAlex/Crossref by DOI): title, year, first author, and
    * the citekey's own year (the `year-citekey-mismatch` lint rule). Each difference is a sentence a
    * person can act on; an empty list = they agree. Nothing is written from here — a rename or a
    * metadata fix stays a person's call. */
  def compareMetadata(citekey: String, title: String, authors: List[String], year: Option[Int],
      upstream: WorkRef): List[String] =
    def surname(a: String): String =
      val t = a.trim
      val s = if t.contains(",") then t.takeWhile(_ != ',') else t.split("\\s+").lastOption.getOrElse(t)
      AgentVerify.normalise(s)
    val titleDiff = upstream.title.filter(t => !AgentVerify.sameTitle(t, title))
      .map(t => s"title differs from the upstream record: \"$t\"")
    val yearDiff = for u <- upstream.year; y <- year if u != y yield s"year $y differs from the upstream record's $u"
    val yearMissing = Option.when(year.isEmpty)(upstream.year.map(u => s"no year recorded; the upstream record says $u")).flatten
    val authorDiff = upstream.authors.headOption.filter(a => authors.nonEmpty && !authors.map(surname).contains(surname(a)))
      .map(a => s"the upstream first author ${a.trim} is not among the recorded authors")
    val keyYear = Lint.yearCitekeyMismatch(List(citekey -> upstream.year.orElse(year)))
      .map(i => s"citekey year: ${i.detail} — rename the source by hand if the record is right")
    (titleDiff ++ yearDiff ++ yearMissing ++ authorDiff).toList ++ keyYear

  /** The lint rules `close` runs for this one source (`no-findings`, `no-period`, `unassessed-status`):
    * each hit is a run warning. */
  def closeLint(findings: Int, hasPeriod: Boolean, status: SourceStatus): List[String] =
    List(
      Option.when(findings == 0)(s"${Lint.NoFindings}: the source has no findings"),
      Option.when(!hasPeriod)(s"${Lint.NoPeriod}: the source has no period"),
      Option.when(status == SourceStatus.Unassessed)(s"${Lint.UnassessedStatus}: the status is still unassessed")
    ).flatten
