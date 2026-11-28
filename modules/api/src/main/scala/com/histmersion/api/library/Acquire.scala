package com.histmersion
package api.library

import com.histmersion.api.library.acquire.RetractionRow
import com.histmersion.api.automation.queue.QueueItem

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.{Csv, LatinFold}
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Plural
import com.histmersion.api.jobs.JobInfo
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.api.automation.Queue
import com.histmersion.api.jobs.JobDetail
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

object Acquire:
  object Intent:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
  type CitationContext = CitationContext.T
  object CitationContext extends Newtype[String, Pure]
  type CitationThreshold = CitationThreshold.T
  object CitationThreshold extends Newtype[Int, Pure]
  type CitedByCount = CitedByCount.T
  object CitedByCount extends Newtype[Int, Pure]
  type ItemNumber = ItemNumber.T
  object ItemNumber extends Newtype[Int, Pure]
  type Outcome = Outcome.T
  object Outcome extends Newtype[String, Pure]
  type Recommendation = Recommendation.T
  object Recommendation extends Newtype[String, Pure]
  type YearGap = YearGap.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object YearGap extends Newtype[Int, Pure]

  private val Stop = Set("the", "a", "an", "of", "on", "and", "in", "to", "for", "study", "new")

  val MinCitekeyYear = 1000

  /** Follows the convention every existing key uses (`drews1993_collapse`, `haak2015_massive`), so a re-ingest of a
    * stored paper lands on its existing citekey instead of a duplicate.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def citekey(authors: List[AuthorName], year: Option[Year], title: Option[Title]): Citekey =
    legacyCitekey(
      authors.map(a => AuthorName(LatinFold.fold((a).value))),
      year,
      title.map(t => Title(LatinFold.fold((t).value)))
    )

  /** Records created before accented letters were folded keep keys that dropped them (`gterbock1938`), so a lookup by
    * generated key tries this form too.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def legacyCitekey(authors: List[AuthorName], year: Option[Year], title: Option[Title]): Citekey =
    val first = authors.map((_).value.trim).find(_.nonEmpty)
    val surname = first
      .map { a =>
        if a.contains(",") then a.takeWhile(_ != ',')
        else a.split("\\s+").filter(_.nonEmpty).lastOption.getOrElse(a)
      }
      .map(_.toLowerCase.replaceAll("[^a-z]", ""))
      .filter(_.nonEmpty)
      .getOrElse("anon")
    // Calibre's "no date" is 0101-01-01: a year below 1000 is a placeholder, not a publication year
    val yr = year.filter(y => (y).value >= MinCitekeyYear).map(y => (y).value.toString).getOrElse("nd")
    val word = """[A-Za-z]+""".r
      .findAllIn(title.fold("")(t => (t).value))
      .map(_.toLowerCase)
      .find(w => w.length > 3 && !Stop.contains(w))
    Citekey(s"$surname$yr" + word.fold("")("_" + _))

  def citekeyCandidates(authors: List[AuthorName], year: Option[Year], title: Option[Title]): List[Citekey] =
    List(citekey(authors, year, title), legacyCitekey(authors, year, title)).distinct

  object Verdicts:
    export Assessment.Verdict as Wire

    val Retracted: Wire = Wire("retracted")
    val Corrected: Wire = Wire("corrected")
    val Current: Wire = Wire("current")
    val LowImpact: Wire = Wire("low-impact")
    val Unassessed: Wire = Wire("unassessed")
    val all: List[Wire] = List(Retracted, Corrected, Current, LowImpact, Unassessed)

  private val RetractingUpdate = Set("retraction", "withdrawal", "removal")
  private val CorrectingUpdate =
    Set(
      "correction",
      "corrigendum",
      "erratum",
      "addendum",
      "expression_of_concern",
      "expression of concern",
      "partial_retraction",
      "partial retraction"
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def isRetractingNature(n: RetractionNotice.Nature): Boolean =
    val s = (n).value.toLowerCase
    (s.contains("retraction") && !s.contains("reinstatement") || s.contains("withdrawal"))

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def verdict(
      retractions: List[RetractionNotice],
      updates: List[UpdateNotice],
      openalexRetracted: Boolean,
      citedByCount: Option[CitedByCount],
      year: Option[Year],
      wellCitedAt: CitationThreshold,
      lowCitedAt: CitationThreshold,
      staleBefore: Year
  ): Verdicts.Wire =
    val kinds = updates.flatMap(_.kind).map(_.value.trim.toLowerCase)
    if openalexRetracted || retractions.exists(r => r.nature.exists(isRetractingNature)) ||
      kinds.exists(RetractingUpdate.contains)
    then Verdicts.Retracted
    else if retractions.nonEmpty || kinds.exists(CorrectingUpdate.contains) then Verdicts.Corrected
    else
      citedByCount match
        case None                                                                               => Verdicts.Unassessed
        case Some(c) if (c).value >= (wellCitedAt).value                                        => Verdicts.Current
        case Some(c) if (c).value < (lowCitedAt).value && year.exists(y => (y) < (staleBefore)) => Verdicts.LowImpact
        case Some(_)                                                                            => Verdicts.Current

  /** Some publishers answer scripts with a 403, an empty file or a challenge, so an open-access work they host is
    * downloaded in the browser, under its licence.
    */
  val BrowserDownloadTopic = "browser download"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def browserDownloadText(
      work: WorkRef,
      doi: Doi,
      citekey: Citekey,
      refusals: List[Problem.NotFound.Answer] = Nil
  ): Option[Outcome] =
    Option.when(((work.isOa) == Source.Candidate.IsOa.OpenAccess)) {
      val who =
        work.authors.map(_.value).headOption.map(a => if work.authors.map(_.value).size > 1 then s"$a et al." else a)
      val head = List(who, work.year.map(_.value.toInt).map(y => s"($y)")).flatten.mkString(" ")
      val title = work.title.map(_.value).map(_.trim).filter(_.nonEmpty).getOrElse((doi).value)
      val oa =
        work.oaStatus.map(_.value.trim.toLowerCase).filter(_.nonEmpty).fold("open access")(s => s"$s open access")
      val at = work.oaUrl
        .map(_.value.trim)
        .filter(u => u.nonEmpty && !u.toLowerCase.contains((doi).value.toLowerCase))
        .fold("")(u => s" at $u")
      val said = refusals
        .map(r => (r.fetcher, r.why.sentence.trim))
        .collect { case (fetcher, why) if why.nonEmpty => s"$fetcher: $why" }
        .distinct
        .mkString("; ")
      Outcome(
        s"${if head.isEmpty then "" else s"$head. "}$title. https://doi.org/$doi — $oa$at, but no script may download " +
          s"it: download it in the browser${
              if said.isEmpty then "" else s" ($said)"
            }, then histmersion ingest <file> --citekey $citekey"
      )
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def flagged(verdict: Verdicts.Wire): Boolean = (verdict == Verdicts.Retracted || verdict == Verdicts.Corrected)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def recommendation(verdict: Verdicts.Wire, citedByCount: Option[CitedByCount]): Recommendation =
    val cited = citedByCount.fold("no citation data")(c => s"$c citation(s)")
    verdict match
      case Verdicts.Retracted =>
        Recommendation(
          "EXCLUDE — a retraction, withdrawal or removal notice exists; set status=retracted and stop citing it"
        )
      case Verdicts.Corrected =>
        Recommendation(
          "CHECK — an amendment (correction / erratum / expression of concern) exists; read it before relying on the paper"
        )
      case Verdicts.Current   => Recommendation(s"KEEP — $cited, no amendment on record")
      case Verdicts.LowImpact =>
        Recommendation(s"REVIEW — old and rarely cited ($cited); keep only if it is directly on topic")
      case _ => Recommendation(s"UNASSESSED — $cited; judge relevance to the topic by hand")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def statusFor(verdict: Verdicts.Wire): Option[Source.Status] = verdict match
    case Verdicts.Retracted => Some(Source.Status.Retracted)
    case Verdicts.Corrected => Some(Source.Status.Contested)
    case Verdicts.Current   => Some(Source.Status.Current)
    case _                  => None

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def statusDecision(
      verdict: Verdicts.Wire,
      current: Source.Status,
      force: Boolean
  ): Option[Either[Source.Status, Source.Status]] =
    statusFor(verdict).map { target =>
      if target == Source.Status.Retracted || force || current == Source.Status.Unassessed || current == target
      then Right(target)
      else Left(current)
    }

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object EdgeKinds:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    val Confirms = "confirms"
    val Challenges = "challenges"
    val Refutes = "refutes"
    val Supersedes = "supersedes"
    val Cites = "cites"
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val all: List[Wire] = List(Wire(Confirms), Wire(Challenges), Wire(Refutes), Wire(Supersedes), Wire(Cites))

  private val RefutesCues = List(
    "refut",
    "disprov",
    "contradict",
    "falsif",
    "not replicat",
    "not be replicat",
    "fails to replicate",
    "failed to reproduce",
    "no support for",
    "no evidence for",
    "cannot be sustained",
    "is untenable",
    "demonstrably wrong"
  )
  private val SupersedeCues =
    List("supersed", "obsolete", "outdated", "superseded by", "has been replaced", "no longer tenable")
  private val ReviseCues =
    List("revis", "updat", "reanalys", "re-analys", "reanalyz", "larger dataset", "more data", "extend")
  private val ChallengeCues = List(
    "challeng",
    "question",
    "dispute",
    "criticis",
    "criticiz",
    "problematic",
    "contrary to",
    "at odds with",
    "inconsisten",
    "however",
    "but see",
    "contra "
  )
  private val ConfirmCues = List(
    "confirm",
    "corroborat",
    "consistent with",
    "in agreement",
    "support",
    "replicat",
    "as shown by",
    "in line with"
  )

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def classify(
      intents: List[Intent.Wire],
      contexts: List[CitationContext],
      isInfluential: Boolean,
      citingYear: Option[Year],
      targetYear: Option[Year],
      gapYears: YearGap = YearGap(5)
  ): EdgeKinds.Wire =
    val text = contexts.mkString(" ").toLowerCase
    val strong = intents.map((_).value.toLowerCase).exists(i => i == "result" || i == "methodology")
    val newer = (citingYear, targetYear) match
      case (Some(c), Some(t)) => (c).value - (t).value >= (gapYears).value
      case _                  => false
    def has(cues: List[String]) = cues.exists(text.contains)

    if has((RefutesCues).map(refactorValue7 => (refactorValue7))) then EdgeKinds.Wire(EdgeKinds.Refutes)
    else if has((SupersedeCues).map(refactorValue8 => (refactorValue8))) then EdgeKinds.Wire(EdgeKinds.Supersedes)
    else if ((isInfluential) == (true)) && strong && newer && has((ReviseCues).map(refactorValue2 => (refactorValue2)))
    then EdgeKinds.Wire(EdgeKinds.Supersedes)
    else if has((ChallengeCues).map(refactorValue4 => (refactorValue4))) then EdgeKinds.Wire(EdgeKinds.Challenges)
    else if has((ConfirmCues).map(refactorValue5 => (refactorValue5))) then EdgeKinds.Wire(EdgeKinds.Confirms)
    else EdgeKinds.Wire(EdgeKinds.Cites)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rankFollowups(rows: List[FollowupRow]): List[FollowupRow] =
    rows.sortBy { r =>
      val strong = r.intents.map(_.value.toLowerCase).exists(i => i == "result" || i == "methodology")
      (if (r.isInfluential).isInfluential then 0 else 1, if strong then 0 else 1, -r.year.fold(0L)(y => (y).value))
    }

  private val DoiCore = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r

  /** DOIs are case-insensitive, so they are stored lower-case: the DOI a text gives (bare, `doi:`, or as a resolver
    * URL), without the punctuation a sentence leaves after it; `None` when it gives none.
    */
  private def normalizeDoi(raw: String): Option[String] =
    val s = Option(raw)
      .getOrElse("")
      .trim
      .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
      .replaceFirst("(?i)^doi:\\s*", "")
      .trim
    DoiCore
      .findFirstIn(s)
      .map(
        _.stripSuffix(".")
          .stripSuffix(",")
          .stripSuffix(";")
          .stripSuffix(")")
          .stripSuffix("}")
          .stripSuffix("|")
          .toLowerCase
      )

  /** The export's `Reason` column is full of quoted commas and embedded newlines, so it needs a real CSV reader.
    * Columns are located by header name because their order has changed between exports, and the last row for a DOI
    * wins because the export lists amendments chronologically. A DOI is normalised (lower case, no resolver prefix) and
    * a row whose DOI is none is dropped.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseRetractionWatch(csv: String): List[RetractionRow] =
    Csv.parse(csv) match
      case Nil            => Nil
      case header :: rows =>
        val idx = header.map(_.trim.replaceAll("^﻿", "")).zipWithIndex.toMap
        def col(r: List[String], name: String): Option[String] =
          idx.get(name).flatMap(r.lift).map(_.trim).filter(_.nonEmpty)
        rows.flatMap { r =>
          col(r, "OriginalPaperDOI").flatMap(normalizeDoi).map { doi =>
            RetractionRow(
              Doi(doi),
              col(r, "Reason").map(x => RetractionNotice.Account(x)),
              col(r, "RetractionDate").map(x => RetractionRow.Date(x)),
              col(r, "RetractionNature").map(x => RetractionRow.Nature(x))
            )
          }
        }

  /** Retraction Watch writes dates as `MM/DD/YYYY 0:00`; Postgres wants `YYYY-MM-DD`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rwDate(raw: Option[RetractionRow.Date]): Option[RetractionRow.Date] =
    raw.map((_).value.trim.takeWhile(_ != ' ')).flatMap { s =>
      s.split("[/-]").toList match
        case m :: d :: y :: Nil if y.length == 4 && m.forall(_.isDigit) && d.forall(_.isDigit) =>
          Some(RetractionRow.Date(f"${y.toInt}%04d-${m.toInt}%02d-${d.toInt}%02d"))
        case y :: m :: d :: Nil if y.length == 4 && m.forall(_.isDigit) && d.forall(_.isDigit) =>
          Some(RetractionRow.Date(f"${y.toInt}%04d-${m.toInt}%02d-${d.toInt}%02d"))
        case _ => None
    }

  object Checks:
    import AcquireCheck.Said

    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    final case class AcquireCheck(
        item: QueueItem.Id,
        checkedAt: AcquireCheck.CheckedAt,
        outcome: AcquireCheck.Outcome,
        reason: Said,
        checks: AcquireCheck.Tries = AcquireCheck.Tries(1)
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    object AcquireCheck:
      type CheckedAt = CheckedAt.T
      object CheckedAt extends Newtype[String, Pure]
      type Outcome = Outcome.T
      object Outcome extends Newtype[String, Pure]
      type Tries = Tries.T
      object Tries extends Newtype[Int, Pure]

      /** The short reason the item carries after the check: `checked 2026-10-27: no legal copy (…)`. */
      type Said = Said.T
      object Said extends Newtype[String, Pure]

    final case class RecheckRequest(
        dryRun: RecheckRequest.DryRun = RecheckRequest.DryRun.Apply,
        limit: Option[RecheckRequest.Limit] = None,
        id: Option[RecheckRequest.Id] = None,
        by: Option[RecheckRequest.By] = None,
        all: RecheckRequest.All = RecheckRequest.All.Due
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    object RecheckRequest:
      enum DryRun derives CanEqual:
        case DryRun, Apply
      object DryRun extends TwoStates[DryRun]:
        extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
      type Limit = Limit.T
      object Limit extends Newtype[Int, Pure]
      export QueueItem.Id
      type By = By.T
      object By extends Newtype[String, Pure]
      enum All derives CanEqual:
        case All, Due
      object All extends TwoStates[All]:
        extension (all: All) def all: Boolean = all == All

    final case class RecheckRow(
        item: RecheckRow.Item,
        doi: Option[Doi] = None,
        action: RecheckRow.Action = RecheckRow.Action(""),
        detail: RecheckRow.Detail = RecheckRow.Detail(""),
        citekey: Option[Citekey] = None
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    object RecheckRow:
      type Item = Item.T
      object Item extends Newtype[Int, Pure]
      type Action = Action.T
      object Action extends Newtype[String, Pure]
      type Detail = Detail.T
      object Detail extends Newtype[String, Pure]

    final case class RecheckResult(
        dryRun: RecheckResult.DryRun,
        open: RecheckResult.Open = RecheckResult.Open(0),
        withDoi: RecheckResult.WithDoi = RecheckResult.WithDoi(0),
        due: RecheckResult.Due = RecheckResult.Due(0),
        allowance: RecheckResult.Allowance = RecheckResult.Allowance(0),
        closed: RecheckResult.Closed = RecheckResult.Closed(0),
        fetched: RecheckResult.Fetched = RecheckResult.Fetched(0),
        noCopy: RecheckResult.NoCopy = RecheckResult.NoCopy(0),
        failed: RecheckResult.Failed = RecheckResult.Failed(0),
        rows: List[RecheckRow] = Nil,
        note: Option[RecheckResult.Note] = None,
        job: Option[JobInfo] = None
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    object RecheckResult:
      enum DryRun derives CanEqual:
        case DryRun, Apply
      object DryRun extends TwoStates[DryRun]:
        extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
      type Open = Open.T
      object Open extends Newtype[Int, Pure]
      type WithDoi = WithDoi.T
      object WithDoi extends Newtype[Int, Pure]
      type Due = Due.T
      object Due extends Newtype[Int, Pure]
      type Allowance = Allowance.T
      object Allowance extends Newtype[Int, Pure]
      type Closed = Closed.T
      object Closed extends Newtype[Int, Pure]
      type Fetched = Fetched.T
      object Fetched extends Newtype[Int, Pure]
      type NoCopy = NoCopy.T
      object NoCopy extends Newtype[Int, Pure]
      type Failed = Failed.T
      object Failed extends Newtype[Int, Pure]
      type Note = Note.T
      object Note extends Newtype[String, Pure]

    final case class RecheckJob(
        all: RecheckJob.All,
        upTo: RecheckJob.UpTo = RecheckJob.UpTo(0),
        since: RecheckJob.Since = RecheckJob.Since(""),
        by: RecheckJob.By = RecheckJob.By(""),
        resumes: Option[RecheckJob.Resumes] = None
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema
    object RecheckJob:

      /** A re-check job's plan, in its detail. */
      enum All derives CanEqual:
        case All, Due
      object All extends TwoStates[All]:
        extension (all: All) def all: Boolean = all == All
      export Plural.Count as UpTo
      type Since = Since.T
      object Since extends Newtype[String, Pure]
      type By = By.T
      object By extends Newtype[String, Pure]
      type Resumes = Resumes.T
      object Resumes extends Newtype[String, Pure]

      @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
      val Detail: JobDetail.Field[Option[RecheckJob]] =
        new com.histmersion.api.jobs.JobDetail.Field(JobDetail.Field.Name("recheck"), None)

      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      val JobKind = AcquisitionJobKinds.AcquireRecheck.kind.value

      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def describe(j: RecheckJob): String =
        "the acquisition list's legal re-check of " +
          (if (j.all).all then "every open item with a DOI"
           else s"up to ${Plural.count(j.upTo, Plural.CountSingular("due item"))}") +
          j.resumes.fold("")(id => s" (resuming job $id)")

    final case class RecheckStatus(
        enabled: RecheckStatus.Enabled,
        withDoi: RecheckStatus.WithDoi = RecheckStatus.WithDoi(0),
        due: RecheckStatus.Due = RecheckStatus.Due(0),
        triedToday: RecheckStatus.TriedToday = RecheckStatus.TriedToday(0),
        perDay: RecheckStatus.PerDay = RecheckStatus.PerDay(0),
        fetchedToday: RecheckStatus.FetchedToday = RecheckStatus.FetchedToday(0),
        closedWeek: RecheckStatus.ClosedWeek = RecheckStatus.ClosedWeek(0),
        fetchedWeek: RecheckStatus.FetchedWeek = RecheckStatus.FetchedWeek(0),
        everyDays: RecheckStatus.EveryDays = RecheckStatus.EveryDays(0),
        nextPass: Option[RecheckStatus.NextPass] = None,
        note: Option[RecheckStatus.Note] = None,
        job: Option[RecheckStatus.Job] = None
    ) derives JsonCodec,
          KindlingsSchema,
          api.json.Schema

    object RecheckStatus:
      enum Enabled derives CanEqual:
        case Enabled, Disabled
      object Enabled extends TwoStates[Enabled]:
        extension (enabled: Enabled) def enabled: Boolean = enabled == Enabled
      type WithDoi = WithDoi.T
      object WithDoi extends Newtype[Int, Pure]
      type Due = Due.T
      object Due extends Newtype[Int, Pure]
      type TriedToday = TriedToday.T
      object TriedToday extends Newtype[Int, Pure]
      type PerDay = PerDay.T
      object PerDay extends Newtype[Int, Pure]
      type FetchedToday = FetchedToday.T
      object FetchedToday extends Newtype[Int, Pure]
      type ClosedWeek = ClosedWeek.T
      object ClosedWeek extends Newtype[Int, Pure]
      type FetchedWeek = FetchedWeek.T
      object FetchedWeek extends Newtype[Int, Pure]
      type EveryDays = EveryDays.T
      object EveryDays extends Newtype[Int, Pure]
      type NextPass = NextPass.T
      object NextPass extends Newtype[String, Pure]
      type Note = Note.T
      object Note extends Newtype[String, Pure]
      export api.SpatialHierarchyResult.Job.Id as Job

    val Closed = "closed"
    val Fetched = "fetched"
    val NoCopy = "no-copy"
    val Failed = "failed"
    val NoDoi = "no-doi"
    val WouldClose = "would-close"
    val WouldTry = "would-try"
    val Due = "due"
