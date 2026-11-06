package com.histmersion.api.library
import com.histmersion.api.{Csv, LatinFold}
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Plural
import com.histmersion.api.jobs.JobInfo
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object Acquire:
  object Intent:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitationContext = CitationContext.T
  object CitationContext extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitationThreshold = CitationThreshold.T
  object CitationThreshold extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedByCount = CitedByCount.T
  object CitedByCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemNumber = ItemNumber.T
  object ItemNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Outcome = Outcome.T
  object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Recommendation = Recommendation.T
  object Recommendation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refusal = Refusal.T
  object Refusal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearGap = YearGap.T
  object YearGap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private val Stop = Set("the", "a", "an", "of", "on", "and", "in", "to", "for", "study", "new")

  val MinCitekeyYear = 1000

  /** Follows the convention every existing key uses (`drews1993_collapse`, `haak2015_massive`), so a re-ingest of a
    * stored paper lands on its existing citekey instead of a duplicate.
    */
  def citekey(authors: List[com.histmersion.api.library.AuthorName], year: Option[com.histmersion.api.catalogue.Year], title: Option[com.histmersion.api.library.Title]): com.histmersion.api.library.Citekey =
    legacyCitekey(authors.map(a => _root_.com.histmersion.api.library.AuthorName(LatinFold.fold((a).value))), year, title.map(t => _root_.com.histmersion.api.library.Title(LatinFold.fold((t).value))))

  /** Records created before accented letters were folded keep keys that dropped them (`gterbock1938`), so a lookup by
    * generated key tries this form too.
    */
  def legacyCitekey(authors: List[com.histmersion.api.library.AuthorName], year: Option[com.histmersion.api.catalogue.Year], title: Option[com.histmersion.api.library.Title]): com.histmersion.api.library.Citekey =
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
    _root_.com.histmersion.api.library.Citekey(s"$surname$yr" + word.fold("")("_" + _))

  def citekeyCandidates(authors: List[com.histmersion.api.library.AuthorName], year: Option[com.histmersion.api.catalogue.Year], title: Option[com.histmersion.api.library.Title]): List[com.histmersion.api.library.Citekey] =
    List(citekey(authors, year, title), legacyCitekey(authors, year, title)).distinct

  object Verdicts:
    type Wire = com.histmersion.api.library.Assessment.Verdict
    val Wire: _root_.com.histmersion.api.library.Assessment.Verdict.type = _root_.com.histmersion.api.library.Assessment.Verdict

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

  private def isRetractingNature(n: com.histmersion.api.library.RetractionNotice.Nature): Boolean =
    val s = (n).value.toLowerCase
    (s.contains("retraction") && !s.contains("reinstatement") || s.contains("withdrawal"))

  def verdict(
      retractions: List[RetractionNotice],
      updates: List[UpdateNotice],
      openalexRetracted: Boolean,
      citedByCount: Option[com.histmersion.api.library.Acquire.CitedByCount],
      year: Option[com.histmersion.api.catalogue.Year],
      wellCitedAt: com.histmersion.api.library.Acquire.CitationThreshold,
      lowCitedAt: com.histmersion.api.library.Acquire.CitationThreshold,
      staleBefore: com.histmersion.api.catalogue.Year
  ): com.histmersion.api.library.Acquire.Verdicts.Wire =
    val kinds = updates.flatMap(_.kind).map(_.value.trim.toLowerCase)
    if openalexRetracted || retractions.exists(r => r.nature.exists(isRetractingNature)) ||
      kinds.exists(RetractingUpdate.contains)
    then Verdicts.Retracted
    else if retractions.nonEmpty || kinds.exists(CorrectingUpdate.contains) then Verdicts.Corrected
    else
      citedByCount match
        case None                                                      => Verdicts.Unassessed
        case Some(c) if (c).value >= (wellCitedAt).value               => Verdicts.Current
        case Some(c) if (c).value < (lowCitedAt).value && year.exists(y => (y).value < (staleBefore).value) => Verdicts.LowImpact
        case Some(_)                                                   => Verdicts.Current

  /** Some publishers answer scripts with a 403, an empty file or a challenge, so an open-access work they host is
    * downloaded in the browser, under its licence.
    */
  val BrowserDownloadTopic = "browser download"

  def browserDownloadText(work: WorkRef, doi: com.histmersion.api.library.Doi, citekey: com.histmersion.api.library.Citekey, refusals: List[com.histmersion.api.library.Acquire.Refusal] = Nil): Option[com.histmersion.api.library.Acquire.Outcome] =
    Option.when(((work.isOa) == _root_.com.histmersion.api.library.Source.Candidate.IsOa(true))) {
      val who = work.authors.map(_.value).headOption.map(a => if work.authors.map(_.value).size > 1 then s"$a et al." else a)
      val head = List(who, work.year.map(_.value.toInt).map(y => s"($y)")).flatten.mkString(" ")
      val title = work.title.map(_.value).map(_.trim).filter(_.nonEmpty).getOrElse((doi).value)
      val oa = work.oaStatus.map(_.value.trim.toLowerCase).filter(_.nonEmpty).fold("open access")(s => s"$s open access")
      val at = work.oaUrl
        .map(_.value.trim)
        .filter(u => u.nonEmpty && !u.toLowerCase.contains((doi).value.toLowerCase))
        .fold("")(u => s" at $u")
      val said = refusals.map((_).value.trim).filter(_.nonEmpty).distinct.mkString("; ")
      _root_.com.histmersion.api.library.Acquire.Outcome(s"${if head.isEmpty then "" else s"$head. "}$title. https://doi.org/$doi — $oa$at, but no script may download " +
        s"it: download it in the browser${
            if said.isEmpty then "" else s" ($said)"
          }, then histmersion ingest <file> --citekey $citekey")
    }

  def flagged(verdict: com.histmersion.api.library.Acquire.Verdicts.Wire): Boolean = (verdict == Verdicts.Retracted || verdict == Verdicts.Corrected)

  def recommendation(verdict: com.histmersion.api.library.Acquire.Verdicts.Wire, citedByCount: Option[com.histmersion.api.library.Acquire.CitedByCount]): com.histmersion.api.library.Acquire.Recommendation =
    val cited = citedByCount.fold("no citation data")(c => s"$c citation(s)")
    verdict match
      case Verdicts.Retracted =>
        _root_.com.histmersion.api.library.Acquire.Recommendation("EXCLUDE — a retraction, withdrawal or removal notice exists; set status=retracted and stop citing it")
      case Verdicts.Corrected =>
        _root_.com.histmersion.api.library.Acquire.Recommendation("CHECK — an amendment (correction / erratum / expression of concern) exists; read it before relying on the paper")
      case Verdicts.Current   => _root_.com.histmersion.api.library.Acquire.Recommendation(s"KEEP — $cited, no amendment on record")
      case Verdicts.LowImpact => _root_.com.histmersion.api.library.Acquire.Recommendation(s"REVIEW — old and rarely cited ($cited); keep only if it is directly on topic")
      case _                  => _root_.com.histmersion.api.library.Acquire.Recommendation(s"UNASSESSED — $cited; judge relevance to the topic by hand")

  def statusFor(verdict: com.histmersion.api.library.Acquire.Verdicts.Wire): Option[com.histmersion.api.library.Source.Status] = verdict match
    case Verdicts.Retracted => Some(com.histmersion.api.library.Source.Status.Retracted)
    case Verdicts.Corrected => Some(com.histmersion.api.library.Source.Status.Contested)
    case Verdicts.Current   => Some(com.histmersion.api.library.Source.Status.Current)
    case _                  => None

  def statusDecision(
      verdict: com.histmersion.api.library.Acquire.Verdicts.Wire,
      current: com.histmersion.api.library.Source.Status,
      force: Boolean
  ): Option[Either[com.histmersion.api.library.Source.Status, com.histmersion.api.library.Source.Status]] =
    statusFor(verdict).map { target =>
      if target == com.histmersion.api.library.Source.Status.Retracted || force || current == com.histmersion.api.library.Source.Status.Unassessed || current == target
      then Right(target)
      else Left(current)
    }

  object EdgeKinds:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Confirms = "confirms"
    val Challenges = "challenges"
    val Refutes = "refutes"
    val Supersedes = "supersedes"
    val Cites = "cites"
    val all: List[com.histmersion.api.library.Acquire.EdgeKinds.Wire] = List(_root_.com.histmersion.api.library.Acquire.EdgeKinds.Wire(Confirms), _root_.com.histmersion.api.library.Acquire.EdgeKinds.Wire(Challenges), _root_.com.histmersion.api.library.Acquire.EdgeKinds.Wire(Refutes), _root_.com.histmersion.api.library.Acquire.EdgeKinds.Wire(Supersedes), _root_.com.histmersion.api.library.Acquire.EdgeKinds.Wire(Cites))

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

  def classify(
      intents: List[com.histmersion.api.library.Acquire.Intent.Wire],
      contexts: List[com.histmersion.api.library.Acquire.CitationContext],
      isInfluential: Boolean,
      citingYear: Option[com.histmersion.api.catalogue.Year],
      targetYear: Option[com.histmersion.api.catalogue.Year],
      gapYears: com.histmersion.api.library.Acquire.YearGap = _root_.com.histmersion.api.library.Acquire.YearGap(5)
  ): com.histmersion.api.library.Acquire.EdgeKinds.Wire =
    val text = contexts.mkString(" ").toLowerCase
    val strong = intents.map((_).value.toLowerCase).exists(i => i == "result" || i == "methodology")
    val newer = (citingYear, targetYear) match
      case (Some(c), Some(t)) => (c).value - (t).value >= (gapYears).value
      case _                  => false
    def has(cues: List[String]) = cues.exists(text.contains)

    if has((RefutesCues).map(refactorValue7 => (refactorValue7))) then _root_.com.histmersion.api.library.Acquire.EdgeKinds.Wire(EdgeKinds.Refutes)
    else if has((SupersedeCues).map(refactorValue8 => (refactorValue8))) then _root_.com.histmersion.api.library.Acquire.EdgeKinds.Wire(EdgeKinds.Supersedes)
    else if ((isInfluential) == (true)) && strong && newer && has((ReviseCues).map(refactorValue2 => (refactorValue2))) then _root_.com.histmersion.api.library.Acquire.EdgeKinds.Wire(EdgeKinds.Supersedes)
    else if has((ChallengeCues).map(refactorValue4 => (refactorValue4))) then _root_.com.histmersion.api.library.Acquire.EdgeKinds.Wire(EdgeKinds.Challenges)
    else if has((ConfirmCues).map(refactorValue5 => (refactorValue5))) then _root_.com.histmersion.api.library.Acquire.EdgeKinds.Wire(EdgeKinds.Confirms)
    else _root_.com.histmersion.api.library.Acquire.EdgeKinds.Wire(EdgeKinds.Cites)

  def rankFollowups(rows: List[FollowupRow]): List[FollowupRow] =
    rows.sortBy { r =>
      val strong = r.intents.map(_.value.toLowerCase).exists(i => i == "result" || i == "methodology")
      (if (r.isInfluential).value then 0 else 1, if strong then 0 else 1, -r.year.fold(0L)(y => (y).value))
    }

  final case class RetractionRow(doi: com.histmersion.api.library.Doi, reason: Option[RetractionRow.Reason], date: Option[RetractionRow.Date], nature: Option[RetractionRow.Nature])

  object RetractionRow:
    type Reason = com.histmersion.api.library.RetractionNotice.Reason
    val Reason: _root_.com.histmersion.api.library.RetractionNotice.Reason.type = _root_.com.histmersion.api.library.RetractionNotice.Reason
    type Date = com.histmersion.api.library.RetractionNotice.Date
    val Date: _root_.com.histmersion.api.library.RetractionNotice.Date.type = _root_.com.histmersion.api.library.RetractionNotice.Date
    type Nature = com.histmersion.api.library.RetractionNotice.Nature
    val Nature: _root_.com.histmersion.api.library.RetractionNotice.Nature.type = _root_.com.histmersion.api.library.RetractionNotice.Nature

  private val DoiCore = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r

  /** DOIs are case-insensitive, so they are stored lower-case: the DOI a text gives (bare, `doi:`, or as a resolver URL),
    * without the punctuation a sentence leaves after it; `None` when it gives none.
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
  def parseRetractionWatch(csv: String): List[RetractionRow] =
    Csv.parse(csv) match
      case Nil            => Nil
      case header :: rows =>
        val idx = header.map(_.trim.replaceAll("^﻿", "")).zipWithIndex.toMap
        def col(r: List[String], name: String): Option[String] =
          idx.get(name).flatMap(r.lift).map(_.trim).filter(_.nonEmpty)
        rows.flatMap { r =>
          col(r, "OriginalPaperDOI").flatMap(normalizeDoi).map { doi =>
            RetractionRow(_root_.com.histmersion.api.library.Doi(doi), col(r, "Reason").map(x => RetractionRow.Reason(x)), col(r, "RetractionDate").map(x => RetractionRow.Date(x)), col(r, "RetractionNature").map(x => RetractionRow.Nature(x)))
          }
        }

  /** Retraction Watch writes dates as `MM/DD/YYYY 0:00`; Postgres wants `YYYY-MM-DD`. */
  def rwDate(raw: Option[com.histmersion.api.library.Acquire.RetractionRow.Date]): Option[com.histmersion.api.library.Acquire.RetractionRow.Date] =
    raw.map((_).value.trim.takeWhile(_ != ' ')).flatMap { s =>
      s.split("[/-]").toList match
        case m :: d :: y :: Nil if y.length == 4 && m.forall(_.isDigit) && d.forall(_.isDigit) =>
          Some(_root_.com.histmersion.api.library.Acquire.RetractionRow.Date(f"${y.toInt}%04d-${m.toInt}%02d-${d.toInt}%02d"))
        case y :: m :: d :: Nil if y.length == 4 && m.forall(_.isDigit) && d.forall(_.isDigit) =>
          Some(_root_.com.histmersion.api.library.Acquire.RetractionRow.Date(f"${y.toInt}%04d-${m.toInt}%02d-${d.toInt}%02d"))
        case _ => None
    }

  object Checks:

    final case class AcquireCheck(item: com.histmersion.api.automation.Queue.QueueItem.Id, checkedAt: AcquireCheck.CheckedAt, outcome: AcquireCheck.Outcome, reason: com.histmersion.api.automation.Queue.QueueItem.Reason, checks: AcquireCheck.Checks = _root_.com.histmersion.api.library.Acquire.Checks.AcquireCheck.Checks(1))
        derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object AcquireCheck:
      type CheckedAt = CheckedAt.T
      object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Outcome = Outcome.T
      object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Checks = Checks.T
      object Checks extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class RecheckRequest(
        dryRun: RecheckRequest.DryRun = _root_.com.histmersion.api.library.Acquire.Checks.RecheckRequest.DryRun(false),
        limit: Option[RecheckRequest.Limit] = None,
        id: Option[RecheckRequest.Id] = None,
        by: Option[RecheckRequest.By] = None,
        all: RecheckRequest.All = _root_.com.histmersion.api.library.Acquire.Checks.RecheckRequest.All(false)
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object RecheckRequest:
      type DryRun = DryRun.T
      object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Limit = Limit.T
      object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type By = By.T
      object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type All = All.T
      object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class RecheckRow(
        item: RecheckRow.Item,
        doi: Option[com.histmersion.api.library.Doi] = None,
        action: RecheckRow.Action = _root_.com.histmersion.api.library.Acquire.Checks.RecheckRow.Action(""),
        detail: RecheckRow.Detail = _root_.com.histmersion.api.library.Acquire.Checks.RecheckRow.Detail(""),
        citekey: Option[com.histmersion.api.library.Citekey] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object RecheckRow:
      type Item = Item.T
      object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Action = Action.T
      object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Detail = Detail.T
      object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class RecheckResult(
        dryRun: RecheckResult.DryRun,
        open: RecheckResult.Open = _root_.com.histmersion.api.library.Acquire.Checks.RecheckResult.Open(0),
        withDoi: RecheckResult.WithDoi = _root_.com.histmersion.api.library.Acquire.Checks.RecheckResult.WithDoi(0),
        due: RecheckResult.Due = _root_.com.histmersion.api.library.Acquire.Checks.RecheckResult.Due(0),
        allowance: RecheckResult.Allowance = _root_.com.histmersion.api.library.Acquire.Checks.RecheckResult.Allowance(0),
        closed: RecheckResult.Closed = _root_.com.histmersion.api.library.Acquire.Checks.RecheckResult.Closed(0),
        fetched: RecheckResult.Fetched = _root_.com.histmersion.api.library.Acquire.Checks.RecheckResult.Fetched(0),
        noCopy: RecheckResult.NoCopy = _root_.com.histmersion.api.library.Acquire.Checks.RecheckResult.NoCopy(0),
        failed: RecheckResult.Failed = _root_.com.histmersion.api.library.Acquire.Checks.RecheckResult.Failed(0),
        rows: List[RecheckRow] = Nil,
        note: Option[RecheckResult.Note] = None,
        job: Option[JobInfo] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object RecheckResult:
      type DryRun = DryRun.T
      object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Open = Open.T
      object Open extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type WithDoi = WithDoi.T
      object WithDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Due = Due.T
      object Due extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Allowance = Allowance.T
      object Allowance extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Closed = Closed.T
      object Closed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Fetched = Fetched.T
      object Fetched extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type NoCopy = NoCopy.T
      object NoCopy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Failed = Failed.T
      object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Note = Note.T
      object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    final case class RecheckJob(
        all: RecheckJob.All,
        upTo: RecheckJob.UpTo = _root_.com.histmersion.api.library.Acquire.Checks.RecheckJob.UpTo(0),
        since: RecheckJob.Since = _root_.com.histmersion.api.library.Acquire.Checks.RecheckJob.Since(""),
        by: RecheckJob.By = _root_.com.histmersion.api.library.Acquire.Checks.RecheckJob.By(""),
        resumes: Option[RecheckJob.Resumes] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema
    object RecheckJob:


      /** A re-check job's plan, in its detail. */

      type All = All.T
      object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type UpTo = com.histmersion.api.Plural.Count
      val UpTo: _root_.com.histmersion.api.Plural.Count.type = _root_.com.histmersion.api.Plural.Count
      type Since = Since.T
      object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type By = By.T
      object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Resumes = Resumes.T
      object Resumes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      val Detail: com.histmersion.api.jobs.JobDetail.Field[Option[RecheckJob]] =
        new com.histmersion.api.jobs.JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("recheck"), None)

      val JobKind = com.histmersion.api.library.AcquisitionJobKinds.AcquireRecheck.kind.value

      def describe(j: RecheckJob): String =
        "the acquisition list's legal re-check of " +
          (if (j.all).value then "every open item with a DOI" else s"up to ${Plural.count(j.upTo, _root_.com.histmersion.api.Plural.CountSingular("due item"))}") +
          j.resumes.fold("")(id => s" (resuming job $id)")

    final case class RecheckStatus(
        enabled: RecheckStatus.Enabled,
        withDoi: RecheckStatus.WithDoi = _root_.com.histmersion.api.library.Acquire.Checks.RecheckStatus.WithDoi(0),
        due: RecheckStatus.Due = _root_.com.histmersion.api.library.Acquire.Checks.RecheckStatus.Due(0),
        triedToday: RecheckStatus.TriedToday = _root_.com.histmersion.api.library.Acquire.Checks.RecheckStatus.TriedToday(0),
        perDay: RecheckStatus.PerDay = _root_.com.histmersion.api.library.Acquire.Checks.RecheckStatus.PerDay(0),
        fetchedToday: RecheckStatus.FetchedToday = _root_.com.histmersion.api.library.Acquire.Checks.RecheckStatus.FetchedToday(0),
        closedWeek: RecheckStatus.ClosedWeek = _root_.com.histmersion.api.library.Acquire.Checks.RecheckStatus.ClosedWeek(0),
        fetchedWeek: RecheckStatus.FetchedWeek = _root_.com.histmersion.api.library.Acquire.Checks.RecheckStatus.FetchedWeek(0),
        everyDays: RecheckStatus.EveryDays = _root_.com.histmersion.api.library.Acquire.Checks.RecheckStatus.EveryDays(0),
        nextPass: Option[RecheckStatus.NextPass] = None,
        note: Option[RecheckStatus.Note] = None,
        job: Option[RecheckStatus.Job] = None
    ) derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object RecheckStatus:
      type Enabled = Enabled.T
      object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type WithDoi = WithDoi.T
      object WithDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Due = Due.T
      object Due extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type TriedToday = TriedToday.T
      object TriedToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type PerDay = PerDay.T
      object PerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type FetchedToday = FetchedToday.T
      object FetchedToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type ClosedWeek = ClosedWeek.T
      object ClosedWeek extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type FetchedWeek = FetchedWeek.T
      object FetchedWeek extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type EveryDays = EveryDays.T
      object EveryDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type NextPass = NextPass.T
      object NextPass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Note = Note.T
      object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Job = com.histmersion.api.SpatialHierarchyResult.Job.Id
      val Job: _root_.com.histmersion.api.SpatialHierarchyResult.Job.Id.type = _root_.com.histmersion.api.SpatialHierarchyResult.Job.Id


    val Closed = "closed"
    val Fetched = "fetched"
    val NoCopy = "no-copy"
    val Failed = "failed"
    val NoDoi = "no-doi"
    val WouldClose = "would-close"
    val WouldTry = "would-try"
    val Due = "due"
