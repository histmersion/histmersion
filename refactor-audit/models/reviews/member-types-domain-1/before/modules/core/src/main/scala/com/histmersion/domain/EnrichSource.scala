package com.histmersion.domain

import scala.annotation.tailrec
import com.histmersion.api.ListPatch
import com.histmersion.api.library.{FindingsPatch, MetadataPatch, WorkRef}
import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.api.checks.Lint
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding

/** The `enrich-source` pipeline's typed answers and its pure logic (docs/research-embedded-agents.md §4.2): what each
  * LLM step must return, how the body is cut into windows, how an answer is verified, and how the verified result
  * becomes ONE `SourcePatch`. The server runs the steps; every function here is pure and unit-tested.
  */
object EnrichSource:
  import AgentVerify.*

  // ── LLM answers (their JSON schemas are what the provider is asked to fill) ─────────────────────

  /** A year as the source states it. The model never converts; [[AgentVerify.toSignedYear]] does. */
  final case class StatedYear(value: StatedYear.Value, unit: StatedYear.Unit) derives KindlingsCodecAsObject
  object StatedYear:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unit = Unit.T
    object Unit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[StatedYear] = KindlingsSchema
      .derived[StatedYear]
      .schema
      .modify(_.value)(_.description("The number as written, always positive (3300 for 3300 BCE)"))
      .modify(_.unit)(
        _.description("BCE | CE | calBP (calibrated years before 1950) | BP (UNcalibrated radiocarbon years)")
      )

  final case class StatedPeriod(start: StatedYear, end: StatedYear, name: Option[StatedPeriod.Name] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object StatedPeriod:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Classification(
      summary: Classification.Summary,
      topics: List[Classification.Topics] = Nil,
      regions: List[Classification.Regions] = Nil,
      tags: List[Classification.Tags] = Nil,
      period: Option[StatedPeriod],
      status: Classification.Status,
      statusEvidence: Classification.StatusEvidence
  ) derives KindlingsCodecAsObject
  object Classification:
    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StatusEvidence = StatusEvidence.T
    object StatusEvidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
      quote: FindingProposal.Quote,
      place: Option[FindingProposal.Place],
      placeKind: Option[FindingProposal.PlaceKind],
      start: Option[StatedYear],
      end: Option[StatedYear],
      periodName: Option[FindingProposal.PeriodName],
      tags: List[FindingProposal.Tags] = Nil,
      confidence: FindingProposal.Confidence
  ) derives KindlingsCodecAsObject
  object FindingProposal:
    type Claim = Claim.T
    object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Quote = Quote.T
    object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Place = Place.T
    object Place extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceKind = PlaceKind.T
    object PlaceKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodName = PeriodName.T
    object PeriodName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Confidence = Confidence.T
    object Confidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  final case class Findings(findings: List[FindingProposal] = Nil) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class Notes(notes: Notes.Value) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Notes:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  // ── checkpointed intermediate values ───────────────────────────────────────────────────────────

  /** One stretch of the body sent to the findings step; `heading` is the nearest heading above it. */
  final case class Window(index: Window.Index, heading: Option[Window.Heading], text: Window.Text) derives KindlingsCodecAsObject

  object Window:
    type Index = Index.T
    object Index extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Heading = Heading.T
    object Heading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What `load` records: enough of the source for every later step, and its `updatedAt` for the final patch's
    * precondition.
    */
  final case class Loaded(
      citekey: com.histmersion.domain.library.Citekey,
      title: Loaded.Title,
      authors: List[Loaded.Authors],
      year: Option[com.histmersion.Year],
      venue: Option[Loaded.Venue],
      status: Loaded.Status,
      hasSummary: Loaded.HasSummary,
      hasNotes: Loaded.HasNotes,
      hasPeriod: Loaded.HasPeriod,
      period: Option[Period],
      topics: List[Loaded.Topics],
      regions: List[Loaded.Regions],
      existingClaims: List[Loaded.ExistingClaims],
      updatedAt: Option[Loaded.UpdatedAt],
      head: Loaded.Head,
      windows: List[Window],
      tags: List[Loaded.Tags] = Nil,
      doi: Option[com.histmersion.domain.library.Doi] = None
  ) derives KindlingsCodecAsObject

  object Loaded:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Venue = Venue.T
    object Venue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HasSummary = HasSummary.T
    object HasSummary extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HasNotes = HasNotes.T
    object HasNotes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HasPeriod = HasPeriod.T
    object HasPeriod extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExistingClaims = ExistingClaims.T
    object ExistingClaims extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Head = Head.T
    object Head extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A finding that passed verification, with its quote's match ratio. `placeKind` is the model's judgement of what
    * `place` is (`None` = no hint; absent from checkpoints older than #981).
    */
  final case class VerifiedFinding(
      claim: VerifiedFinding.Claim,
      quote: VerifiedFinding.Quote,
      ratio: VerifiedFinding.Ratio,
      place: Option[VerifiedFinding.Place],
      start: Option[VerifiedFinding.Start],
      end: Option[VerifiedFinding.End],
      periodName: Option[VerifiedFinding.PeriodName],
      tags: List[VerifiedFinding.Tags],
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
    object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Quote = Quote.T
    object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Ratio = Ratio.T
    object Ratio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Place = Place.T
    object Place extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Start = Start.T
    object Start extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type End = End.T
    object End extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodName = PeriodName.T
    object PeriodName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Confidence = Confidence.T
    object Confidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    object Proposed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type QuoteFailed = QuoteFailed.T
    object QuoteFailed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Asked = Asked.T
    object Asked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Approximate = Approximate.T
    object Approximate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The `geocode` step: the findings with their points, and every choice behind them. */
  final case class Geocoded(findings: List[Finding.Input], choices: List[GeocodeChoice]) derives KindlingsCodecAsObject

  /** A classification whose period has been converted to signed years. */
  final case class Classified(
      summary: Classified.Summary,
      topics: List[Classified.Topics],
      regions: List[Classified.Regions],
      tags: List[Classified.Tags],
      period: Option[Period],
      periodName: Option[Classified.PeriodName],
      status: Source.Status,
      statusEvidence: Classified.StatusEvidence
  ) derives KindlingsCodecAsObject

  object Classified:
    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodName = PeriodName.T
    object PeriodName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StatusEvidence = StatusEvidence.T
    object StatusEvidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Patched(findingsAdded: Patched.FindingsAdded, fieldsSet: List[Patched.FieldsSet], statusProposal: Option[Patched.StatusProposal])
      derives KindlingsCodecAsObject

  object Patched:
    type FindingsAdded = FindingsAdded.T
    object FindingsAdded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FieldsSet = FieldsSet.T
    object FieldsSet extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StatusProposal = StatusProposal.T
    object StatusProposal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The `metadata` step: the record against OpenAlex/Crossref. `checked = false` with a `note` when it could not be
    * (no DOI, upstream off or down); the differences are run warnings, never writes.
    */
  final case class MetaChecked(checked: MetaChecked.Checked, differences: List[MetaChecked.Differences], note: MetaChecked.Note = "")
      derives KindlingsCodecAsObject

  object MetaChecked:
    type Checked = Checked.T
    object Checked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Differences = Differences.T
    object Differences extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What `refs` / `assess` did (or why they were skipped). */
  final case class Upstreamed(ran: Upstreamed.Ran, summary: Upstreamed.Summary, retracted: Upstreamed.Retracted = false) derives KindlingsCodecAsObject

  object Upstreamed:
    type Ran = Ran.T
    object Ran extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Retracted = Retracted.T
    object Retracted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The `status` step: whether the classify step's status proposal was applied, recorded for a person (`approval` id),
    * or needed nothing.
    */
  final case class StatusRouted(applied: Option[StatusRouted.Applied], approval: Option[StatusRouted.Approval], note: StatusRouted.Note)
      derives KindlingsCodecAsObject

  object StatusRouted:
    type Applied = Applied.T
    object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Approval = Approval.T
    object Approval extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The `close` step: the per-source lint, the acquisition item and the log line. */
  final case class Closed(line: Closed.Line, lint: List[Closed.Lint], acquire: Option[Closed.Acquire]) derives KindlingsCodecAsObject

  object Closed:
    type Line = Line.T
    object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lint = Lint.T
    object Lint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Acquire = Acquire.T
    object Acquire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── pure logic ─────────────────────────────────────────────────────────────────────────────────

  private val Heading = "^#{1,6}\\s+(.+)$".r

  /** Cut `body` into paragraph-aligned windows of at most `windowChars`; when there are more than `maxWindows`, keep
    * `maxWindows` of them spread evenly from the first to the last (a book's argument is not all in chapter one). Each
    * window remembers the heading it falls under.
    */
  def windows(body: String, windowChars: Int, maxWindows: Int): List[Window] =
    val size = math.max(500, windowChars)
    val paras = body
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
    val n = math.max(1, maxWindows)
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
      s <- toSignedYear(p.start.value, su)
      e <- toSignedYear(p.end.value, eu)
      _ <- periodProblems(s, e, maxAbsYear, within) match
        case Nil => Right(())
        case ps  => Left(ps.mkString("; "))
    yield Period(s, e)

  /** `classify`: status in the vocabulary, a summary, a convertible and sane period, and the status's evidence unless
    * it is `unassessed`. A period that cannot be converted (raw BP) is dropped, and missing evidence let through, with
    * a warning on the last attempt rather than failing the whole classification.
    */
  def checkClassification(
      c: Classification,
      maxAbsYear: Long,
      last: Boolean
  ): Either[List[String], (Classified, List[String])] =
    val status = AgentVerify.status(c.status)
    val period = c.period.map(p => convert(p, maxAbsYear, None))
    val summary = Option(c.summary).map(_.trim).filter(_.nonEmpty)
    // a status other than unassessed is a proposal a person may weigh: it needs its evidence
    val evidence = Option(c.statusEvidence).map(_.trim).getOrElse("")
    val noEvidence = Option.when(status.exists(_ != Source.Status.Unassessed) && evidence.isEmpty)(
      "the status evidence is empty — say, from the text, why that status"
    )
    val problems = status.left.toOption.toList ++ summary.fold(List("the summary is empty"))(_ => Nil) ++
      period.flatMap(_.left.toOption).map(m => s"period: $m").toList ++ noEvidence.toList
    val fatal = status.isLeft || summary.isEmpty
    if problems.isEmpty || (last && !fatal) then
      val clean = (l: List[String]) => l.map(_.trim).filter(_.nonEmpty).distinct
      Right(
        (
          Classified(
            summary.get,
            clean(c.topics),
            clean(c.regions),
            clean(c.tags).map(normaliseTag),
            period.flatMap(_.toOption),
            c.period.flatMap(_.name),
            status.toOption.get,
            evidence
          ),
          period.flatMap(_.left.toOption).map(m => s"period dropped: $m").toList ++ noEvidence.toList
        )
      )
    else Left(problems)

  /** Tags are lowercase_snake_case in this corpus (the `non-canonical-tag` lint rule). */
  def normaliseTag(t: String): String =
    t.trim.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", "")

  /** The findings step's guards (#1424, config `agents.enrich.guards`) and the gazetteer they ask:
    * `resolves(place, kind, period)` is true when the geocoder would give the place a point — the same question the
    * `geocode` step asks later, put while the answer can still be sent back.
    */
  final case class FindingGuards(
      minPlacedShare: FindingGuards.MinPlacedShare,
      centuryGapYears: FindingGuards.CenturyGapYears,
      pointerPhrases: List[FindingGuards.PointerPhrases],
      resolves: (String, Geocode.KindHint, Option[Period]) => Boolean
  )

  object FindingGuards:
    type MinPlacedShare = MinPlacedShare.T
    object MinPlacedShare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CenturyGapYears = CenturyGapYears.T
    object CenturyGapYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointerPhrases = PointerPhrases.T
    object PointerPhrases extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  def centuriesMentioned(text: String): List[Int] =
    val words = OrdinalWord
      .findAllMatchIn(text)
      .flatMap(m => OrdinalWords.get(m.group(1).toLowerCase(java.util.Locale.ROOT)))
      .toList
    val digits =
      if CenturyMention.findFirstIn(text).isDefined then OrdinalDigits.findAllMatchIn(text).map(_.group(1).toInt).toList
      else Nil
    (digits ++ words).filter(_ > 0).distinct

  /** Whether `text` mentions a century at all (an ordinal or just the word). */
  def mentionsCentury(text: String): Boolean =
    CenturyMention.findFirstIn(text).isDefined || OrdinalWord.findFirstIn(text).isDefined

  /** Whether `text` states `year` (its absolute value) as a year with its era: "79 CE", "AD 79", "79 B.C.". */
  def statesYear(text: String, year: Long): Boolean =
    val n = math.abs(year).toString
    s"(?i)(?:\\b$Era\\s*$n(?!\\d)|(?<!\\d)$n\\s*$Era(?![A-Za-z]))".r.findFirstIn(text).isDefined

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
  def centuryAsYear(start: Long, end: Long, text: String, within: Option[Period], gap: Long): Option[String] =
    val centuries = centuriesMentioned(text)
    def inMentioned(y: Long) = centuries.exists { c =>
      val a = math.abs(y)
      a >= (c - 1L) * 100 + 1 && a <= c * 100L
    }
    val suspicious = List(start, end).distinct.filter { y =>
      math.abs(y) < 100 && !statesYear(text, y) &&
      ((mentionsCentury(text) && !inMentioned(y)) || within.exists(p => p.start - math.abs(y) >= gap))
    }
    suspicious.headOption.map { y =>
      val c = centuries.find(_ == math.abs(y)).orElse(centuries.headOption).map(_.toLong).getOrElse(math.abs(y))
      s"the year $y looks like a century stored as a year: '${ordinal(c)} century' is the years " +
        s"${(c - 1) * 100 + 1}–${c * 100} (BCE negative); a year below 100 needs the text to state it (e.g. '79 CE')"
    }

  /** The first of `phrases` (lowercase) the claim contains as whole words, if any. */
  def pointerPhrase(claim: String, phrases: List[String]): Option[String] =
    val lower = claim.toLowerCase(java.util.Locale.ROOT)
    phrases.find(p =>
      s"(?<![\\p{L}\\p{N}])${java.util.regex.Pattern.quote(p)}(?![\\p{L}\\p{N}])".r.findFirstIn(lower).isDefined
    )

  /** The placement guard: of the proposals that name a place, how many resolve; a problem when fewer than `minShare`
    * do, listing the names that did not.
    */
  def placementProblem(named: List[(Int, String, Boolean)], minShare: Double): Option[String] =
    val total = named.size
    val resolved = named.count(_._3)
    Option.when(total > 0 && minShare > 0 && resolved.toDouble < minShare * total) {
      val missing = named.filterNot(_._3).map((i, p, _) => s"'$p' (finding $i)").mkString(", ")
      val pct = BigDecimal(minShare * 100).setScale(0, BigDecimal.RoundingMode.HALF_UP)
      s"only $resolved of $total places resolve to a gazetteer place (at least $pct% must); not found: $missing — " +
        "give a place name the gazetteer knows (a site, city or region), or leave place null for a non-spatial claim"
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
      minRatio: Double,
      maxAbsYear: Long,
      within: Option[Period],
      existingClaims: Set[String],
      guards: FindingGuards,
      last: Boolean
  ): Either[List[String], (VerifiedFindings, List[String])] =
    val quotes = f.findings.map(p => findQuote(p.quote, window.text, minRatio))
    val checked = f.findings.zip(quotes).zipWithIndex.map { case ((p, quote), i) =>
      val label = s"finding ${i + 1} (${p.claim.take(60)})"
      val conf = AgentVerify.confidence(p.confidence)
      val per: Either[String, Option[Period]] = (p.start, p.end) match
        case (None, None)    => Right(None)
        case (Some(s), e)    => convert(StatedPeriod(s, e.getOrElse(s)), maxAbsYear, None).map(Some(_))
        case (None, Some(e)) => convert(StatedPeriod(e, e), maxAbsYear, None).map(Some(_))
      val issues = List(
        Option.when(p.claim.trim.isEmpty)("the claim is empty"),
        Option.when(!quote.accepted)(
          s"the quote is not in the text (best match ${BigDecimal(quote.ratio).setScale(2, BigDecimal.RoundingMode.HALF_UP)}) — copy it exactly"
        ),
        conf.left.toOption,
        per.left.toOption.map(m => s"period: $m")
      ).flatten
      // the guards' problems: sent back like the others, but never a reason to drop the finding
      val said = List(Some(p.claim), Some(p.quote), p.periodName).flatten.mkString("\n")
      val century =
        per.toOption.flatten.flatMap(pd => centuryAsYear(pd.start, pd.end, said, within, guards.centuryGapYears))
      val pointer = pointerPhrase(p.claim, guards.pointerPhrases).map(ph =>
        s"'$ph' only makes sense next to the table: a finding must stand alone on the map: name the date/event instead of pointing at the table"
      )
      val soft = (century.toList ++ pointer.toList).map(m => s"$label: $m")
      val result =
        if issues.nonEmpty then Left(s"$label: ${issues.mkString("; ")}")
        else
          Right(
            VerifiedFinding(
              p.claim.trim,
              p.quote.trim,
              quote.ratio,
              p.place.map(_.trim).filter(_.nonEmpty),
              per.toOption.flatten.map(_.start),
              per.toOption.flatten.map(_.end),
              p.periodName.map(_.trim).filter(_.nonEmpty),
              p.tags.map(normaliseTag).filter(_.nonEmpty).distinct,
              conf.toOption.get.toString.toLowerCase(java.util.Locale.ROOT),
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
      p.place.map(_.trim).filter(_.nonEmpty).map { name =>
        val kind = p.placeKind.map(Geocode.KindHint.parse).getOrElse(Geocode.KindHint.Unknown)
        (i + 1, name, guards.resolves(name, kind, per))
      }
    }
    val soft = checked.flatMap(_._2) ++ placementProblem(named, guards.minPlacedShare).toList
    if (rejected.nonEmpty || soft.nonEmpty) && !last then Left(rejected ++ soft)
    else
      val norm = existingClaims.map(c => normalise(c))
      val kept = results
        .collect { case Right(v) => v }
        .filterNot(v => norm.contains(normalise(v.claim)))
        .distinctBy(v => normalise(v.quote))
      // outside the source's own span is suspicious, not wrong (a source cites older and later events)
      val outside = for
        v <- kept; s <- v.start; e <- v.end; p <- within
        problem <- periodProblems(s, e, maxAbsYear, Some(p)).lastOption
      yield s"check '${v.claim.take(60)}': $problem"
      Right(
        (
          VerifiedFindings(kept, Some(f.findings.size), Some(quotes.count(!_.accepted))),
          rejected.map(m => s"dropped $m") ++ outside ++ soft.map(m => s"kept $m")
        )
      )

  /** `notes`: non-empty, and every `[[link]]` names a citekey or an article. */
  def checkNotes(n: Notes, isCitekey: String => Boolean, isSlug: String => Boolean): Either[List[String], String] =
    val text = Option(n.notes).map(_.trim).getOrElse("")
    if text.isEmpty then Left(List("the notes are empty"))
    else
      linkProblems(text, isCitekey, isSlug) match
        case Nil => Right(text)
        case ps  => Left(ps)

  /** The ONE patch the run writes. It only FILLS: a summary, notes or period the source already has is left alone (a
    * person or an earlier run wrote it), topics/regions are merged, tags are added, findings are added. A status is set
    * only from `unassessed` to `current`/`foundational`; any other proposal goes to a person (returned, not applied).
    */
  def patch(
      l: Loaded,
      c: Classified,
      findings: List[Finding.Input],
      notes: Option[String]
  ): (com.histmersion.api.library.Source.Patch, List[String], Option[String]) =
    val merge = (have: List[String], add: List[String]) =>
      Option.when(add.exists(a => !have.contains(a)))((have ++ add).distinct)
    val setStatus = Option.when(
      l.status == Source.Status.Unassessed.wire &&
        (c.status == Source.Status.Current || c.status == Source.Status.Foundational)
    )(c.status)
    val proposal = Option.when(setStatus.isEmpty && c.status != Source.Status.Unassessed && c.status.wire != l.status)(
      s"${c.status.wire}: ${c.statusEvidence}"
    )
    val p = com.histmersion.api.library.Source.Patch(
      metadata = setStatus.map(s => MetadataPatch(status = Some(s))),
      summary = Option.when(!l.hasSummary)(c.summary),
      notes = notes.filter(_ => !l.hasNotes),
      topics = merge(l.topics, c.topics),
      regions = merge(l.regions, c.regions),
      period = c.period.filter(_ => !l.hasPeriod).map(p => Period.Input(p.start, p.end)),
      tags = Some(c.tags.filterNot(l.tags.contains)).filter(_.nonEmpty).map(t => ListPatch(add = t)),
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
    (p, fields, proposal)

  /** Our record against the upstream one (OpenAlex/Crossref by DOI): title, year, first author, and the citekey's own
    * year (the `year-citekey-mismatch` lint rule). Each difference is a sentence a person can act on; an empty list =
    * they agree. Nothing is written from here — a rename or a metadata fix stays a person's call.
    */
  def compareMetadata(
      citekey: String,
      title: String,
      authors: List[String],
      year: Option[Int],
      upstream: WorkRef
  ): List[String] =
    def surname(a: String): String =
      val t = a.trim
      val s = if t.contains(",") then t.takeWhile(_ != ',') else t.split("\\s+").lastOption.getOrElse(t)
      AgentVerify.normalise(s)
    val titleDiff = upstream.title
      .filter(t => !AgentVerify.sameTitle(t.value, title))
      .map(t => s"title differs from the upstream record: \"$t\"")
    val yearDiff = for u <- upstream.year; y <- year if u != com.histmersion.api.catalogue.Year(y) yield s"year $y differs from the upstream record's $u"
    val yearMissing =
      Option.when(year.isEmpty)(upstream.year.map(u => s"no year recorded; the upstream record says $u")).flatten
    val authorDiff = upstream.authors.headOption
      .filter(a => authors.nonEmpty && !authors.map(surname).contains(surname(a.value)))
      .map(a => s"the upstream first author ${a.value.trim} is not among the recorded authors")
    val keyYear = Lint
      .yearCitekeyMismatch(List(citekey -> upstream.year.map(_.value.toInt).orElse(year)))
      .map(i => s"citekey year: ${i.detail} — rename the source by hand if the record is right")
    (titleDiff ++ yearDiff ++ yearMissing ++ authorDiff).toList ++ keyYear

  /** The lint rules `close` runs for this one source (`no-findings`, `no-period`, `unassessed-status`): each hit is a
    * run warning.
    */
  def closeLint(findings: Int, hasPeriod: Boolean, status: Source.Status): List[String] =
    List(
      Option.when(findings == 0)(s"${Lint.NoFindings}: the source has no findings"),
      Option.when(!hasPeriod)(s"${Lint.NoPeriod}: the source has no period"),
      Option.when(status == Source.Status.Unassessed)(s"${Lint.UnassessedStatus}: the status is still unassessed")
    ).flatten
