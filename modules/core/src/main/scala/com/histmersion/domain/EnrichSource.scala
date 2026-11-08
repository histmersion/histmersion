package com.histmersion
package domain

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import scala.annotation.tailrec
import com.histmersion.api.ListPatch
import com.histmersion.api.library.{FindingsPatch, MetadataPatch, WorkRef}
import com.histmersion.api.catalogue.{Geocode, Period, Year}
import com.histmersion.api.checks.Lint
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.domain.research.Slug

/** The `enrich-source` pipeline's typed answers and its pure logic (docs/research-embedded-agents.md §4.2): what each
  * LLM step must return, how the body is cut into windows, how an answer is verified, and how the verified result
  * becomes ONE `SourcePatch`. The server runs the steps; every function here is pure and unit-tested.
  */
object EnrichSource:
  type Century = Century.T
  object Century extends Newtype[Int, Pure]
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type FindingCount = FindingCount.T
  object FindingCount extends Newtype[Int, Pure]
  type Last = Last.T
  object Last extends Newtype[Boolean, Pure]
  type MaxWindows = MaxWindows.T
  object MaxWindows extends Newtype[Int, Pure]
  type MaxAbsYear = AgentVerify.MaxAbsYear
  val MaxAbsYear: AgentVerify.MaxAbsYear.type = AgentVerify.MaxAbsYear
  export AgentVerify.{MinRatio, Problem, SourceBody}
  type Topic = Topic.T
  object Topic extends Newtype[String, Pure]
  type Region = Region.T
  object Region extends Newtype[String, Pure]
  type PlaceQuery = FindingProposal.Place
  val PlaceQuery: FindingProposal.Place.type = FindingProposal.Place
  type Tag = Tag.T
  object Tag extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type WindowChars = WindowChars.T
  object WindowChars extends Newtype[Int, Pure]

  type CheckClassificationMaxAbsYear = MaxAbsYear
  val CheckClassificationMaxAbsYear: MaxAbsYear.type = MaxAbsYear
  type MentionsCenturyText = Text
  val MentionsCenturyText: Text.type = Text
  type StatesYearText = Text
  val StatesYearText: Text.type = Text
  type CenturyAsYearText = Text
  val CenturyAsYearText: Text.type = Text
  type CheckFindingsMaxAbsYear = MaxAbsYear
  val CheckFindingsMaxAbsYear: MaxAbsYear.type = MaxAbsYear
  type PatchNotes = PatchNotes.T
  object PatchNotes extends Newtype[String, Pure]

  import AgentVerify.*

  // ── LLM answers (their JSON schemas are what the provider is asked to fill) ─────────────────────

  /** A year as the source states it. The model never converts; [[AgentVerify.toSignedYear]] does. */
  final case class StatedYear(value: ToSignedYearValue, unit: YearUnit.Wire) derives KindlingsCodecAsObject
  object StatedYear:
    given Schema[StatedYear] = KindlingsSchema
      .derived[StatedYear]
      .schema
      .modify(_.value)(_.description("The number as written, always positive (3300 for 3300 BCE)"))
      .modify(_.unit)(
        _.description("BCE | CE | calBP (calibrated years before 1950) | BP (UNcalibrated radiocarbon years)")
      )

  final case class StatedPeriod(start: StatedYear, end: StatedYear, name: Option[StatedPeriod.Name] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object StatedPeriod:
    type Name = Name.T
    object Name extends Newtype[String, Pure]

  final case class Classification(
      summary: Classification.Summary,
      topics: List[Topic] = Nil,
      regions: List[Region] = Nil,
      tags: List[Tag] = Nil,
      period: Option[StatedPeriod],
      status: com.histmersion.domain.library.Source.Status.Wire,
      statusEvidence: Classification.StatusEvidence
  ) derives KindlingsCodecAsObject
  object Classification:
    type Summary = Summary.T
    object Summary extends Newtype[String, Pure]
    type StatusEvidence = StatusEvidence.T
    object StatusEvidence extends Newtype[String, Pure]

    given Schema[Classification] = KindlingsSchema
      .derived[Classification]
      .schema
      .modify(_.summary)(_.description("3-6 sentences: what the source argues and on what evidence"))
      .modify(_.tags)(_.description("lowercase_snake_case; prefer tags the facets tool lists"))
      .modify(_.period)(
        _.description("The deep-time span the source is ABOUT (not its publication date); null if none")
      )
      .modify(_.status)(
        _.description("foundational | current | contested | superseded | debunked | retracted | unassessed")
      )
      .modify(_.statusEvidence)(_.description("Why that status, from the text; empty if unassessed"))

  final case class FindingProposal(
      claim: FindingProposal.Claim,
      quote: AgentProposals.Proposal.Quote,
      place: Option[FindingProposal.Place],
      placeKind: Option[Geocode.KindHint.Wire],
      start: Option[StatedYear],
      end: Option[StatedYear],
      periodName: Option[FindingProposal.PeriodName],
      tags: List[Tag] = Nil,
      confidence: com.histmersion.domain.library.Confidence.Wire
  ) derives KindlingsCodecAsObject
  object FindingProposal:
    type Claim = Claim.T
    object Claim extends Newtype[String, Pure]
    type Place = Place.T
    object Place extends Newtype[String, Pure]
    type PeriodName = PeriodName.T
    object PeriodName extends Newtype[String, Pure]

    given Schema[FindingProposal] = KindlingsSchema
      .derived[FindingProposal]
      .schema
      .modify(_.claim)(_.description("One claim the source makes, in your words, one sentence"))
      .modify(_.quote)(_.description("The sentence(s) of the text that state it, copied EXACTLY, 8-60 words"))
      .modify(_.place)(_.description("The place the claim is about, as a gazetteer would name it; null if none"))
      .modify(_.placeKind)(
        _.description(
          "What kind of place `place` is: site (a city, settlement or excavated site) | region (a country, landscape or province) | polity (a state, kingdom or empire) | water (a sea, lake or river) | unknown; null if no place"
        )
      )
      .modify(_.confidence)(_.description("high | medium | low | contested — how firmly the SOURCE states it"))

  final case class Findings(findings: List[FindingProposal] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  final case class Notes(notes: Notes.Value)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Notes:
    type Value = Value.T
    object Value extends Newtype[String, Pure]

  // ── checkpointed intermediate values ───────────────────────────────────────────────────────────

  /** One stretch of the body sent to the findings step; `heading` is the nearest heading above it. */
  final case class Window(index: Window.Index, heading: Option[Window.Heading], text: Window.Text)
      derives KindlingsCodecAsObject

  object Window:
    type Index = Index.T
    object Index extends Newtype[Int, Pure]
    type Heading = Heading.T
    object Heading extends Newtype[String, Pure]
    type Text = Text.T
    object Text extends Newtype[String, Pure]

  /** What `load` records: enough of the source for every later step, and its `updatedAt` for the final patch's
    * precondition.
    */
  final case class Loaded(
      citekey: Citekey,
      title: Loaded.Title,
      authors: List[AuthorName],
      year: Option[com.histmersion.Year],
      venue: Option[Loaded.Venue],
      status: Source.Status.Wire,
      hasSummary: Loaded.HasSummary,
      hasNotes: Loaded.HasNotes,
      hasPeriod: Loaded.HasPeriod,
      period: Option[Period],
      topics: List[Topic],
      regions: List[Region],
      existingClaims: List[Loaded.ExistingClaims],
      updatedAt: Option[Source.UpdatedAt],
      head: Loaded.Head,
      windows: List[Window],
      tags: List[Tag] = Nil,
      doi: Option[Doi] = None
  ) derives KindlingsCodecAsObject

  object Loaded:
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    type Venue = Venue.T
    object Venue extends Newtype[String, Pure]
    type HasSummary = HasSummary.T
    object HasSummary extends Newtype[Boolean, Pure]
    type HasNotes = HasNotes.T
    object HasNotes extends Newtype[Boolean, Pure]
    type HasPeriod = HasPeriod.T
    object HasPeriod extends Newtype[Boolean, Pure]
    type ExistingClaims = ExistingClaims.T
    object ExistingClaims extends Newtype[String, Pure]
    type Head = Head.T
    object Head extends Newtype[String, Pure]

  /** A finding that passed verification, with its quote's match ratio. `placeKind` is the model's judgement of what
    * `place` is (`None` = no hint; absent from checkpoints older than #981).
    */
  final case class VerifiedFinding(
      claim: VerifiedFinding.Claim,
      quote: VerifiedFinding.Quote,
      ratio: AgentVerify.Ratio,
      place: Option[VerifiedFinding.Place],
      start: Option[Year],
      end: Option[Year],
      periodName: Option[VerifiedFinding.PeriodName],
      tags: List[Tag],
      confidence: VerifiedFinding.Confidence,
      placeKind: Option[Geocode.KindHint] = None
  ) derives KindlingsCodecAsObject:
    /** The finding's own period, for a time-aware choice among dated places. */
    def period: Option[Period] = (start, end) match
      case (Some(s), Some(e)) => Some(Period(s, e))
      case (Some(s), None)    => Some(Period(s, s))
      case (None, Some(e))    => Some(Period(e, e))
      case _                  => None

  object VerifiedFinding:
    type Claim = Claim.T
    object Claim extends Newtype[String, Pure]
    type Quote = Quote.T
    object Quote extends Newtype[String, Pure]
    type Place = Place.T
    object Place extends Newtype[String, Pure]
    type PeriodName = PeriodName.T
    object PeriodName extends Newtype[String, Pure]
    type Confidence = Confidence.T
    object Confidence extends Newtype[String, Pure]

  /** One window's verified findings. `proposed` = how many the accepted answer held, `quoteFailed` = how many of those
    * quotes were not found in the window (dropped on a last attempt) — what `agent compare` reads the verifier's pass
    * ratio from (None in checkpoints older than #992).
    */
  final case class VerifiedFindings(
      findings: List[VerifiedFinding],
      proposed: Option[VerifiedFindings.Proposed] = None,
      quoteFailed: Option[VerifiedFindings.QuoteFailed] = None
  ) derives KindlingsCodecAsObject

  object VerifiedFindings:
    type Proposed = Proposed.T
    object Proposed extends Newtype[Int, Pure]
    type QuoteFailed = QuoteFailed.T
    object QuoteFailed extends Newtype[Int, Pure]

  /** What the geocoder chose for one finding's place, for a person to audit in the run trace: `placeId`/`label` empty
    * when the name is unknown, `approximate` saying why the point is only near what was asked (a qualifier dropped, a
    * placeholder point).
    */
  final case class GeocodeChoice(
      claim: GeocodeChoice.Claim,
      asked: GeocodeChoice.Asked,
      placeId: Option[com.histmersion.domain.catalogue.Place.Id],
      label: Option[GeocodeChoice.Label],
      approximate: Option[GeocodeChoice.Approximate]
  ) derives KindlingsCodecAsObject

  object GeocodeChoice:
    type Claim = Claim.T
    object Claim extends Newtype[String, Pure]
    type Asked = Asked.T
    object Asked extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Approximate = Approximate.T
    object Approximate extends Newtype[String, Pure]

  /** The `geocode` step: the findings with their points, and every choice behind them. */
  final case class Geocoded(findings: List[Finding.Input], choices: List[GeocodeChoice]) derives KindlingsCodecAsObject

  /** A classification whose period has been converted to signed years. */
  final case class Classified(
      summary: Classified.Summary,
      topics: List[Topic],
      regions: List[Region],
      tags: List[Tag],
      period: Option[Period],
      periodName: Option[StatedPeriod.Name],
      status: Source.Status,
      statusEvidence: Classified.StatusEvidence
  ) derives KindlingsCodecAsObject

  object Classified:
    type Summary = Summary.T
    object Summary extends Newtype[String, Pure]
    type StatusEvidence = StatusEvidence.T
    object StatusEvidence extends Newtype[String, Pure]

  final case class Patched(
      findingsAdded: Patched.FindingsAdded,
      fieldsSet: List[Patched.FieldsSet],
      statusProposal: Option[Patched.StatusProposal]
  ) derives KindlingsCodecAsObject

  object Patched:
    type FindingsAdded = FindingsAdded.T
    object FindingsAdded extends Newtype[Int, Pure]
    type FieldsSet = FieldsSet.T
    object FieldsSet extends Newtype[String, Pure]
    type StatusProposal = StatusProposal.T
    object StatusProposal extends Newtype[String, Pure]

  /** The `metadata` step: the record against OpenAlex/Crossref. `checked = false` with a `note` when it could not be
    * (no DOI, upstream off or down); the differences are run warnings, never writes.
    */
  final case class MetaChecked(
      checked: MetaChecked.Checked,
      differences: List[MetaChecked.Differences],
      note: MetaChecked.Note = MetaChecked.Note("")
  ) derives KindlingsCodecAsObject

  object MetaChecked:
    type Checked = Checked.T
    object Checked extends Newtype[Boolean, Pure]
    type Differences = Differences.T
    object Differences extends Newtype[String, Pure]
    type Note = Note.T
    object Note extends Newtype[String, Pure]

  /** What `refs` / `assess` did (or why they were skipped). */
  final case class Upstreamed(
      ran: Upstreamed.Ran,
      summary: Upstreamed.Summary,
      retracted: Upstreamed.Retracted = Upstreamed.Retracted(false)
  ) derives KindlingsCodecAsObject

  object Upstreamed:
    type Ran = Ran.T
    object Ran extends Newtype[Boolean, Pure]
    type Summary = Summary.T
    object Summary extends Newtype[String, Pure]
    type Retracted = Retracted.T
    object Retracted extends Newtype[Boolean, Pure]

  /** The `status` step: whether the classify step's status proposal was applied, recorded for a person (`approval` id),
    * or needed nothing.
    */
  final case class StatusRouted(
      applied: Option[StatusRouted.Applied],
      approval: Option[StatusRouted.Approval],
      note: StatusRouted.Note
  ) derives KindlingsCodecAsObject

  object StatusRouted:
    type Applied = Applied.T
    object Applied extends Newtype[String, Pure]
    type Approval = Approval.T
    object Approval extends Newtype[Long, Pure]
    type Note = Note.T
    object Note extends Newtype[String, Pure]

  /** The `close` step: the per-source lint, the acquisition item and the log line. */
  final case class Closed(line: Closed.Line, lint: List[Closed.Lint], acquire: Option[Closed.Acquire])
      derives KindlingsCodecAsObject

  object Closed:
    type Line = Line.T
    object Line extends Newtype[String, Pure]
    type Lint = Lint.T
    object Lint extends Newtype[String, Pure]
    type Acquire = Acquire.T
    object Acquire extends Newtype[String, Pure]

  // ── pure logic ─────────────────────────────────────────────────────────────────────────────────

  private val Heading = "^#{1,6}\\s+(.+)$".r

  /** Cut `body` into paragraph-aligned windows of at most `windowChars`; when there are more than `maxWindows`, keep
    * `maxWindows` of them spread evenly from the first to the last (a book's argument is not all in chapter one). Each
    * window remembers the heading it falls under.
    */
  def windows(body: SourceBody, windowChars: WindowChars, maxWindows: MaxWindows): List[Window] =
    val size = math.max(500, windowChars.value)
    val paras = body.value
      .split("\\n\\s*\\n")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(p => if p.length <= size then List(p) else p.grouped(size).toList)
    // fold paragraphs into windows, tracking the last heading seen
    @tailrec def windows(
        rest: List[String],
        acc: Vector[(Option[String], String)],
        buf: String,
        bufHead: Option[String],
        lastHead: Option[String]
    ): List[(Option[String], String)] =
      rest match
        case Nil       => (if buf.isEmpty then acc else acc :+ (bufHead, buf)).toList
        case p :: tail =>
          val head = p.linesIterator.nextOption().collect { case Heading(h) => h.trim }.orElse(lastHead)
          if buf.isEmpty then windows(tail, acc, p, head, head)
          else if buf.length + 2 + p.length <= size then windows(tail, acc, buf + "\n\n" + p, bufHead, head)
          else windows(tail, acc :+ (bufHead, buf), p, head, head)
    val all = windows(paras, Vector.empty, "", None, None)
    val n = math.max(1, maxWindows.value)
    val picked =
      if all.size <= n then all
      else if n == 1 then List(all.head)
      else (0 until n).map(i => all(math.round(i.toDouble * (all.size - 1) / (n - 1)).toInt)).distinct.toList
    picked.zipWithIndex.map { case ((h, t), i) =>
      Window(Window.Index(i + 1), h.map(Window.Heading(_)), Window.Text(t))
    }

  /** Turn a stated period into signed years, or say why not. A point (no end) is `start..start`. */
  def convert(p: StatedPeriod, maxAbsYear: MaxAbsYear, within: Option[Period]): Either[Problem, Period] =
    for
      su <- YearUnit
        .parse(p.start.unit)
        .toRight(AgentVerify.Problem(s"unknown unit '${p.start.unit}' (BCE, CE, calBP or BP)"))
      eu <- YearUnit
        .parse(p.end.unit)
        .toRight(AgentVerify.Problem(s"unknown unit '${p.end.unit}' (BCE, CE, calBP or BP)"))
      s <- toSignedYear(p.start.value, su)
      e <- toSignedYear(p.end.value, eu)
      _ <- periodProblems(PeriodStart(s.value), PeriodEnd(e.value), maxAbsYear, within) match
        case Nil => Right(())
        case ps  => Left(AgentVerify.Problem(ps.mkString("; ")))
    yield Period(Year(s.value), Year(e.value))

  /** `classify`: status in the vocabulary, a summary, a convertible and sane period, and the status's evidence unless
    * it is `unassessed`. A period that cannot be converted (raw BP) is dropped, and missing evidence let through, with
    * a warning on the last attempt rather than failing the whole classification.
    */
  def checkClassification(
      c: Classification,
      maxAbsYear: CheckClassificationMaxAbsYear,
      last: Last
  ): Either[List[Problem], (Classified, List[Description])] =
    val status = AgentVerify.status(c.status)
    val period = c.period.map(p => convert(p, maxAbsYear, None))
    val summary = Option(c.summary.value).map(_.trim).filter(_.nonEmpty)
    // a status other than unassessed is a proposal a person may weigh: it needs its evidence
    val evidence = Option(c.statusEvidence.value).map(_.trim).getOrElse("")
    val noEvidence = Option.when(status.exists(_ != Source.Status.Unassessed) && evidence.isEmpty)(
      "the status evidence is empty — say, from the text, why that status"
    )
    val problems = status.left.toOption.toList ++
      summary.fold(List(AgentVerify.Problem("the summary is empty")))(_ => Nil) ++
      period.flatMap(_.left.toOption).map(m => AgentVerify.Problem(s"period: $m")).toList ++ noEvidence
        .map(AgentVerify.Problem(_))
        .toList
    val fatal = status.isLeft || summary.isEmpty
    if problems.isEmpty || (last.value && !fatal) then
      val clean = (l: List[String]) => l.map(_.trim).filter(_.nonEmpty).distinct
      Right(
        (
          Classified(
            Classified.Summary(summary.get),
            clean(c.topics.map(_.value)).map(Topic(_)),
            clean(c.regions.map(_.value)).map(Region(_)),
            clean(c.tags.map(_.value)).map(t => normaliseTag(Tag(t))),
            period.flatMap(_.toOption),
            c.period.flatMap(_.name),
            status.toOption.get,
            Classified.StatusEvidence(evidence)
          ),
          (period.flatMap(_.left.toOption).map(m => s"period dropped: $m").toList ++ noEvidence.toList)
            .map(Description(_))
        )
      )
    else Left(problems)

  /** Tags are lowercase_snake_case in this corpus (the `non-canonical-tag` lint rule). */
  def normaliseTag(t: Tag): Tag =
    Tag(t.value.trim.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", ""))

  private def normalised(s: String): NormalisedText = normalise(AgentVerify.Text(s))

  /** The findings step's guards (#1424, config `agents.enrich.guards`) and the gazetteer they ask:
    * `resolves(place, kind, period)` is true when the geocoder would give the place a point — the same question the
    * `geocode` step asks later, put while the answer can still be sent back.
    */
  final case class FindingGuards(
      minPlacedShare: FindingGuards.MinPlacedShare,
      centuryGapYears: FindingGuards.CenturyGapYears,
      pointerPhrases: List[FindingGuards.PointerPhrases],
      resolves: (FindingGuards.PlaceQuery, Geocode.KindHint, Option[Period]) => Boolean
  )

  object FindingGuards:
    type PlaceQuery = EnrichSource.PlaceQuery
    val PlaceQuery: EnrichSource.PlaceQuery.type = EnrichSource.PlaceQuery

    type MinPlacedShare = MinPlacedShare.T
    object MinPlacedShare extends Newtype[Double, Pure]
    type CenturyGapYears = CenturyGapYears.T
    object CenturyGapYears extends Newtype[Long, Pure]
    type PointerPhrases = PointerPhrases.T
    object PointerPhrases extends Newtype[String, Pure]

  private val CenturyMention = "(?i)\\bcentur(?:y|ies)\\b|\\b\\d{1,2}\\s*(?:st|nd|rd|th)\\s*c\\.".r
  private val OrdinalDigits = "(?i)\\b(\\d{1,2})\\s*(?:st|nd|rd|th)\\b".r
  private val OrdinalWords = List(
    "first",
    "second",
    "third",
    "fourth",
    "fifth",
    "sixth",
    "seventh",
    "eighth",
    "ninth",
    "tenth",
    "eleventh",
    "twelfth",
    "thirteenth",
    "fourteenth",
    "fifteenth",
    "sixteenth",
    "seventeenth",
    "eighteenth",
    "nineteenth",
    "twentieth",
    "twenty-first"
  ).zipWithIndex.map((w, i) => w -> (i + 1)).toMap
  private val OrdinalWord = {
    val w = OrdinalWords.keys.toList.sortBy(-_.length).mkString("|")
    s"(?i)\\b($w)(?=(?:\\s*(?:-|–|and|or|to)\\s*(?:$w))?\\s+centur)".r
  }
  private val Era = "(?:AD|A\\.D\\.|CE|C\\.E\\.|BCE|B\\.C\\.E\\.|BC|B\\.C\\.)"

  /** The centuries `text` mentions ("11th century", "15th c.", "eleventh and twelfth centuries"), as their ordinal
    * numbers; empty when it mentions no century.
    */
  def centuriesMentioned(text: Text): List[Century] =
    val words = OrdinalWord
      .findAllMatchIn(text.value)
      .flatMap(m => OrdinalWords.get(m.group(1).toLowerCase(java.util.Locale.ROOT)))
      .toList
    val digits =
      if CenturyMention.findFirstIn(text.value).isDefined then
        OrdinalDigits.findAllMatchIn(text.value).map(_.group(1).toInt).toList
      else Nil
    (digits ++ words).filter(_ > 0).distinct.map(Century(_))

  /** Whether `text` mentions a century at all (an ordinal or just the word). */
  def mentionsCentury(text: MentionsCenturyText): Boolean =
    CenturyMention.findFirstIn(text.value).isDefined || OrdinalWord.findFirstIn(text.value).isDefined

  /** Whether `text` states `year` (its absolute value) as a year with its era: "79 CE", "AD 79", "79 B.C.". */
  def statesYear(text: StatesYearText, year: Year): Boolean =
    val n = math.abs(year.value).toString
    s"(?i)(?:\\b$Era\\s*$n(?!\\d)|(?<!\\d)$n\\s*$Era(?![A-Za-z]))".r.findFirstIn(text.value).isDefined

  private def ordinal(n: Long): String =
    val suffix = if n % 100 >= 11 && n % 100 <= 13 then "th"
    else
      n % 10 match
        case 1 => "st"
        case 2 => "nd"
        case 3 => "rd"
        case _ => "th"
    s"$n$suffix"

  /** A year below 100 that is almost surely a century stored as a year (#1424): the finding mentions a century and the
    * year is not inside one it mentions ("1st century" = 1..100 is right), or the source's own period starts at least
    * `gap` years after it. A year the text states with its era ("79 CE") is valid. The problem names the century as
    * years, e.g. "'11th century' is the years 1001–1100 (BCE negative)".
    */
  def centuryAsYear(
      start: Year,
      end: Year,
      text: CenturyAsYearText,
      within: Option[Period],
      gap: FindingGuards.CenturyGapYears
  ): Option[Description] =
    val centuries = centuriesMentioned(text)
    def inMentioned(y: Year) = centuries.exists { c =>
      val a = math.abs(y.value)
      a >= (c.value - 1L) * 100 + 1 && a <= c.value * 100L
    }
    val suspicious = List(start, end).distinct.filter { y =>
      math.abs(y.value) < 100 && !statesYear(text, y) &&
      ((mentionsCentury(text) && !inMentioned(y)) || within.exists(p => p.start.value - math.abs(y.value) >= gap.value))
    }
    suspicious.headOption.map { y =>
      val c = centuries
        .find(_.value == math.abs(y.value))
        .orElse(centuries.headOption)
        .map(_.value.toLong)
        .getOrElse(math.abs(y.value))
      Description(
        s"the year $y looks like a century stored as a year: '${ordinal(c)} century' is the years " +
          s"${(c - 1) * 100 + 1}–${c * 100} (BCE negative); a year below 100 needs the text to state it (e.g. '79 CE')"
      )
    }

  /** The first of `phrases` (lowercase) the claim contains as whole words, if any. */
  def pointerPhrase(
      claim: FindingProposal.Claim,
      phrases: List[FindingGuards.PointerPhrases]
  ): Option[FindingGuards.PointerPhrases] =
    val lower = claim.value.toLowerCase(java.util.Locale.ROOT)
    phrases.find(p =>
      s"(?<![\\p{L}\\p{N}])${java.util.regex.Pattern.quote(p.value)}(?![\\p{L}\\p{N}])".r.findFirstIn(lower).isDefined
    )

  /** The placement guard: of the proposals that name a place, how many resolve; a problem when fewer than `minShare`
    * do, listing the names that did not.
    */
  def placementProblem(
      named: List[(Count, FindingProposal.Place, Boolean)],
      minShare: FindingGuards.MinPlacedShare
  ): Option[Problem] =
    val total = named.size
    val resolved = named.count(_._3)
    Option.when(total > 0 && minShare.value > 0 && resolved.toDouble < minShare.value * total) {
      val missing = named.filterNot(_._3).map((i, p, _) => s"'$p' (finding $i)").mkString(", ")
      val pct = BigDecimal(minShare.value * 100).setScale(0, BigDecimal.RoundingMode.HALF_UP)
      AgentVerify.Problem(
        s"only $resolved of $total places resolve to a gazetteer place (at least $pct% must); not found: $missing — " +
          "give a place name the gazetteer knows (a site, city or region), or leave place null for a non-spatial claim"
      )
    }

  /** `findings`: every quote must be in THIS window (OCR-tolerantly), every confidence in the vocabulary, every period
    * convertible and sane. A finding that fails is rejected — the whole answer is sent back while attempts remain; on
    * the last attempt the failures are dropped with a warning each and the rest kept. Findings repeating an existing
    * claim are dropped silently.
    *
    * The [[FindingGuards]] (#1424) add three checks that send the answer back too, but on the last attempt KEEP it with
    * a warning: too few named places resolve in the gazetteer, a century stored as a year, and a claim that points at a
    * table ("this date", "the next column") instead of standing alone on the map.
    */
  def checkFindings(
      f: Findings,
      window: Window,
      minRatio: MinRatio,
      maxAbsYear: CheckFindingsMaxAbsYear,
      within: Option[Period],
      existingClaims: Set[Loaded.ExistingClaims],
      guards: FindingGuards,
      last: Last
  ): Either[List[Problem], (VerifiedFindings, List[Description])] =
    val windowBody = AgentVerify.SourceBody(window.text.value)
    val quotes = f.findings.map(p => findQuote(p.quote, windowBody, minRatio))
    val checked = f.findings.zip(quotes).zipWithIndex.map { case ((p, quote), i) =>
      val label = s"finding ${i + 1} (${p.claim.value.take(60)})"
      val conf = AgentVerify.confidence(p.confidence)
      val per: Either[Problem, Option[Period]] = (p.start, p.end) match
        case (None, None)    => Right(None)
        case (Some(s), e)    => convert(StatedPeriod(s, e.getOrElse(s)), maxAbsYear, None).map(Some(_))
        case (None, Some(e)) => convert(StatedPeriod(e, e), maxAbsYear, None).map(Some(_))
      val issues = List(
        Option.when(p.claim.value.trim.isEmpty)("the claim is empty"),
        Option.when(!quote.accepted)(
          s"the quote is not in the text (best match ${BigDecimal(quote.ratio.value).setScale(2, BigDecimal.RoundingMode.HALF_UP)}) — copy it exactly"
        ),
        conf.left.toOption.map(_.value),
        per.left.toOption.map(m => s"period: $m")
      ).flatten
      // the guards' problems: sent back like the others, but never a reason to drop the finding
      val said = List(Some(p.claim.value), Some(p.quote.value), p.periodName.map(_.value)).flatten.mkString("\n")
      val century =
        per.toOption.flatten.flatMap(pd => centuryAsYear(pd.start, pd.end, Text(said), within, guards.centuryGapYears))
      val pointer = pointerPhrase(p.claim, guards.pointerPhrases).map(ph =>
        s"'$ph' only makes sense next to the table: a finding must stand alone on the map: name the date/event instead of pointing at the table"
      )
      val soft = (century.map(_.value).toList ++ pointer.toList).map(m => s"$label: $m")
      val result =
        if issues.nonEmpty then Left(s"$label: ${issues.mkString("; ")}")
        else
          Right(
            VerifiedFinding(
              VerifiedFinding.Claim(p.claim.value.trim),
              VerifiedFinding.Quote(p.quote.value.trim),
              quote.ratio,
              p.place.map(_.value.trim).filter(_.nonEmpty).map(VerifiedFinding.Place(_)),
              per.toOption.flatten.map(_.start),
              per.toOption.flatten.map(_.end),
              p.periodName.map(_.value.trim).filter(_.nonEmpty).map(VerifiedFinding.PeriodName(_)),
              p.tags.map(normaliseTag).filter(_.value.nonEmpty).distinct,
              VerifiedFinding.Confidence(conf.toOption.get.toString.toLowerCase(java.util.Locale.ROOT)),
              p.placeKind.map(Geocode.KindHint.parse).filter(_ != Geocode.KindHint.Unknown)
            )
          )
      (result, soft, per.toOption.flatten)
    }
    val results = checked.map(_._1)
    val rejected = results.collect { case Left(m) => m }
    // the placement guard asks the gazetteer only while the answer can still be sent back, or once on
    // the last attempt for the warning; a finding with no place is a non-spatial claim and does not count
    val named = f.findings.zip(checked).zipWithIndex.flatMap { case ((p, (_, _, per)), i) =>
      p.place.map(pl => FindingProposal.Place(pl.value.trim)).filter(_.value.nonEmpty).map { name =>
        val kind = p.placeKind.map(Geocode.KindHint.parse).getOrElse(Geocode.KindHint.Unknown)
        (Count(i + 1), name, guards.resolves(name, kind, per))
      }
    }
    val soft = checked.flatMap(_._2) ++ placementProblem(named, guards.minPlacedShare).map(_.value).toList
    if (rejected.nonEmpty || soft.nonEmpty) && !last.value then Left((rejected ++ soft).map(AgentVerify.Problem(_)))
    else
      val norm = existingClaims.map(c => normalised(c.value))
      val kept = results
        .collect { case Right(v) => v }
        .filterNot(v => norm.contains(normalised(v.claim.value)))
        .distinctBy(v => normalised(v.quote.value))
      // outside the source's own span is suspicious, not wrong (a source cites older and later events)
      val outside = for
        v <- kept; s <- v.start; e <- v.end; p <- within
        problem <- periodProblems(PeriodStart(s.value), PeriodEnd(e.value), maxAbsYear, Some(p)).lastOption
      yield s"check '${v.claim.value.take(60)}': $problem"
      Right(
        (
          VerifiedFindings(
            kept,
            Some(VerifiedFindings.Proposed(f.findings.size)),
            Some(VerifiedFindings.QuoteFailed(quotes.count(!_.accepted)))
          ),
          (rejected.map(m => s"dropped $m") ++ outside ++ soft.map(m => s"kept $m")).map(Description(_))
        )
      )

  /** `notes`: non-empty, and every `[[link]]` names a citekey or an article. */
  def checkNotes(n: Notes, isCitekey: Citekey => Boolean, isSlug: Slug => Boolean): Either[List[Problem], Description] =
    val text = Option(n.notes.value).map(_.trim).getOrElse("")
    if text.isEmpty then Left(List(Problem("the notes are empty")))
    else
      linkProblems(LinkProblemsText(text), isCitekey, isSlug) match
        case Nil => Right(Description(text))
        case ps  => Left(ps)

  /** The ONE patch the run writes. It only FILLS: a summary, notes or period the source already has is left alone (a
    * person or an earlier run wrote it), topics/regions are merged, tags are added, findings are added. A status is set
    * only from `unassessed` to `current`/`foundational`; any other proposal goes to a person (returned, not applied).
    */
  def patch(
      l: Loaded,
      c: Classified,
      findings: List[Finding.Input],
      notes: Option[PatchNotes]
  ): (Source.Patch, List[Description], Option[Description]) =
    def merge[A](have: List[A], add: List[A]): Option[List[A]] =
      Option.when(add.exists(a => !have.contains(a)))((have ++ add).distinct)
    val setStatus = Option.when(
      l.status == Source.Status.Unassessed.wire &&
        (c.status == Source.Status.Current || c.status == Source.Status.Foundational)
    )(c.status)
    val proposal = Option.when(setStatus.isEmpty && c.status != Source.Status.Unassessed && c.status.wire != l.status)(
      Description(s"${c.status.wire}: ${c.statusEvidence}")
    )
    val p = Source.Patch(
      metadata = setStatus.map(s => MetadataPatch(status = Some(s))),
      summary = Option.when(!l.hasSummary.value)(Source.Summary(c.summary.value)),
      notes = notes.filter(_ => !l.hasNotes.value).map(n => Source.Notes(n.value)),
      topics = merge(l.topics, c.topics).map(_.map(t => Source.Topics(t.value))),
      regions = merge(l.regions, c.regions).map(_.map(r => Source.Regions(r.value))),
      period = c.period.filter(_ => !l.hasPeriod.value).map(p => Period.Input(p.start, p.end)),
      tags = Some(c.tags.filterNot(l.tags.contains))
        .filter(_.nonEmpty)
        .map(t => ListPatch(add = t.map(x => ListPatch.Add(x.value)))),
      findings = Option.when(findings.nonEmpty)(FindingsPatch(add = findings)),
      ifUpdatedAt = l.updatedAt
    )
    val fields = List(
      p.metadata.map(_ => "status"),
      p.summary.map(_ => "summary"),
      p.notes.map(_ => "notes"),
      p.topics.map(_ => "topics"),
      p.regions.map(_ => "regions"),
      p.period.map(_ => "period"),
      p.tags.map(_ => "tags"),
      p.findings.map(f => s"${f.add.size} finding(s)")
    ).flatten
    (p, fields.map(Description(_)), proposal)

  /** Our record against the upstream one (OpenAlex/Crossref by DOI): title, year, first author, and the citekey's own
    * year (the `year-citekey-mismatch` lint rule). Each difference is a sentence a person can act on; an empty list =
    * they agree. Nothing is written from here — a rename or a metadata fix stays a person's call.
    */
  def compareMetadata(
      citekey: Citekey,
      title: Title,
      authors: List[AuthorName],
      year: Option[com.histmersion.Year],
      upstream: WorkRef
  ): List[Description] =
    def surname(a: String): String =
      val t = a.trim
      val s = if t.contains(",") then t.takeWhile(_ != ',') else t.split("\\s+").lastOption.getOrElse(t)
      AgentVerify.normalise(AgentVerify.Text(s)).value
    val ourYear = year.map(y => Year(y.value.toLong))
    val titleDiff = upstream.title
      .filter(t => !AgentVerify.sameTitle(Title(t.value), title))
      .map(t => s"title differs from the upstream record: \"$t\"")
    val yearDiff = for u <- upstream.year; y <- ourYear if u != y yield s"year $y differs from the upstream record's $u"
    val yearMissing =
      Option.when(year.isEmpty)(upstream.year.map(u => s"no year recorded; the upstream record says $u")).flatten
    val authorDiff = upstream.authors.headOption
      .filter(a => authors.nonEmpty && !authors.map(x => surname(x.value)).contains(surname(a.value)))
      .map(a => s"the upstream first author ${a.value.trim} is not among the recorded authors")
    val keyYear = Lint
      .yearCitekeyMismatch(List(com.histmersion.api.library.Citekey(citekey.value) -> upstream.year.orElse(ourYear)))
      .map(i => s"citekey year: ${i.detail} — rename the source by hand if the record is right")
    ((titleDiff ++ yearDiff ++ yearMissing ++ authorDiff).toList ++ keyYear).map(Description(_))

  /** The lint rules `close` runs for this one source (`no-findings`, `no-period`, `unassessed-status`): each hit is a
    * run warning.
    */
  def closeLint(findings: FindingCount, hasPeriod: Loaded.HasPeriod, status: Source.Status): List[Description] =
    List(
      Option.when(findings.value == 0)(s"${Lint.NoFindings}: the source has no findings"),
      Option.when(!hasPeriod.value)(s"${Lint.NoPeriod}: the source has no period"),
      Option.when(status == Source.Status.Unassessed)(s"${Lint.UnassessedStatus}: the status is still unassessed")
    ).flatten.map(Description(_))
