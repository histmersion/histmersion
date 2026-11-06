package com.histmersion.api.library
import com.histmersion.api.{Csv, LatinFold}
import com.histmersion.api.given
import com.histmersion.api.Plural
import com.histmersion.api.jobs.JobInfo
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object Acquire:

  type ClassifyHasCues = ClassifyHasCues.T
  object ClassifyHasCues extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRetractionWatchColResult = ParseRetractionWatchColResult.T
  object ParseRetractionWatchColResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRetractionWatchColR = ParseRetractionWatchColR.T
  object ParseRetractionWatchColR extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRetractionWatchColName = ParseRetractionWatchColName.T
  object ParseRetractionWatchColName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type CitekeyResult = CitekeyResult.T
  object CitekeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitekeyAuthors = CitekeyAuthors.T
  object CitekeyAuthors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitekeyYear = _root_.com.histmersion.api.library.Acquire.LegacyCitekeyYear
  val CitekeyYear: _root_.com.histmersion.api.library.Acquire.LegacyCitekeyYear.type = _root_.com.histmersion.api.library.Acquire.LegacyCitekeyYear
  type CitekeyTitle = CitekeyTitle.T
  object CitekeyTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LegacyCitekeyResult = LegacyCitekeyResult.T
  object LegacyCitekeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LegacyCitekeyAuthors = _root_.com.histmersion.api.library.Acquire.CitekeyAuthors
  val LegacyCitekeyAuthors: _root_.com.histmersion.api.library.Acquire.CitekeyAuthors.type = _root_.com.histmersion.api.library.Acquire.CitekeyAuthors
  type LegacyCitekeyYear = LegacyCitekeyYear.T
  object LegacyCitekeyYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LegacyCitekeyTitle = _root_.com.histmersion.api.library.Acquire.CitekeyTitle
  val LegacyCitekeyTitle: _root_.com.histmersion.api.library.Acquire.CitekeyTitle.type = _root_.com.histmersion.api.library.Acquire.CitekeyTitle
  type CitekeyCandidatesResult = CitekeyCandidatesResult.T
  object CitekeyCandidatesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitekeyCandidatesAuthors = _root_.com.histmersion.api.library.Acquire.CitekeyAuthors
  val CitekeyCandidatesAuthors: _root_.com.histmersion.api.library.Acquire.CitekeyAuthors.type = _root_.com.histmersion.api.library.Acquire.CitekeyAuthors
  type CitekeyCandidatesYear = _root_.com.histmersion.api.library.Acquire.LegacyCitekeyYear
  val CitekeyCandidatesYear: _root_.com.histmersion.api.library.Acquire.LegacyCitekeyYear.type = _root_.com.histmersion.api.library.Acquire.LegacyCitekeyYear
  type CitekeyCandidatesTitle = _root_.com.histmersion.api.library.Acquire.CitekeyTitle
  val CitekeyCandidatesTitle: _root_.com.histmersion.api.library.Acquire.CitekeyTitle.type = _root_.com.histmersion.api.library.Acquire.CitekeyTitle
  type IsRetractingNatureResult = IsRetractingNatureResult.T
  object IsRetractingNatureResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsRetractingNatureN = IsRetractingNatureN.T
  object IsRetractingNatureN extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictResult = VerdictResult.T
  object VerdictResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictOpenalexRetracted = VerdictOpenalexRetracted.T
  object VerdictOpenalexRetracted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictCitedByCount = VerdictCitedByCount.T
  object VerdictCitedByCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictYear = VerdictYear.T
  object VerdictYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictWellCitedAt = VerdictWellCitedAt.T
  object VerdictWellCitedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictLowCitedAt = VerdictLowCitedAt.T
  object VerdictLowCitedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictStaleBefore = VerdictStaleBefore.T
  object VerdictStaleBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BrowserDownloadTextResult = BrowserDownloadTextResult.T
  object BrowserDownloadTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BrowserDownloadTextDoi = BrowserDownloadTextDoi.T
  object BrowserDownloadTextDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BrowserDownloadTextCitekey = BrowserDownloadTextCitekey.T
  object BrowserDownloadTextCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BrowserDownloadTextRefusals = BrowserDownloadTextRefusals.T
  object BrowserDownloadTextRefusals extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FlaggedResult = FlaggedResult.T
  object FlaggedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FlaggedVerdict = FlaggedVerdict.T
  object FlaggedVerdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecommendationResult = RecommendationResult.T
  object RecommendationResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecommendationVerdict = RecommendationVerdict.T
  object RecommendationVerdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecommendationCitedByCount = RecommendationCitedByCount.T
  object RecommendationCitedByCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusForVerdict = StatusForVerdict.T
  object StatusForVerdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusDecisionVerdict = _root_.com.histmersion.api.library.Acquire.StatusForVerdict
  val StatusDecisionVerdict: _root_.com.histmersion.api.library.Acquire.StatusForVerdict.type = _root_.com.histmersion.api.library.Acquire.StatusForVerdict
  type StatusDecisionForce = StatusDecisionForce.T
  object StatusDecisionForce extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyResult = ClassifyResult.T
  object ClassifyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyIntents = ClassifyIntents.T
  object ClassifyIntents extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyContexts = ClassifyContexts.T
  object ClassifyContexts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyIsInfluential = ClassifyIsInfluential.T
  object ClassifyIsInfluential extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyCitingYear = ClassifyCitingYear.T
  object ClassifyCitingYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyTargetYear = ClassifyTargetYear.T
  object ClassifyTargetYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyGapYears = ClassifyGapYears.T
  object ClassifyGapYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRetractionWatchCsv = ParseRetractionWatchCsv.T
  object ParseRetractionWatchCsv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RwDateResult = RwDateResult.T
  object RwDateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RwDateRaw = RwDateRaw.T
  object RwDateRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Stop = Set("the", "a", "an", "of", "on", "and", "in", "to", "for", "study", "new")

  val MinCitekeyYear = 1000

  /** Follows the convention every existing key uses (`drews1993_collapse`, `haak2015_massive`), so a re-ingest of a
    * stored paper lands on its existing citekey instead of a duplicate.
    */
  def citekey(authors: List[com.histmersion.api.library.Acquire.CitekeyAuthors], year: Option[com.histmersion.api.library.Acquire.CitekeyYear], title: Option[com.histmersion.api.library.Acquire.CitekeyTitle]): com.histmersion.api.library.Acquire.CitekeyResult =
    legacyCitekey(authors.map(LatinFold.fold), year, title.map(LatinFold.fold))

  /** Records created before accented letters were folded keep keys that dropped them (`gterbock1938`), so a lookup by
    * generated key tries this form too.
    */
  def legacyCitekey(authors: List[com.histmersion.api.library.Acquire.LegacyCitekeyAuthors], year: Option[com.histmersion.api.library.Acquire.LegacyCitekeyYear], title: Option[com.histmersion.api.library.Acquire.LegacyCitekeyTitle]): com.histmersion.api.library.Acquire.LegacyCitekeyResult =
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
    val yr = year.filter(_ >= MinCitekeyYear).map(_.toString).getOrElse("nd")
    val word = """[A-Za-z]+""".r
      .findAllIn(title.getOrElse(""))
      .map(_.toLowerCase)
      .find(w => w.length > 3 && !Stop.contains(w))
    s"$surname$yr" + word.fold("")("_" + _)

  def citekeyCandidates(authors: List[com.histmersion.api.library.Acquire.CitekeyCandidatesAuthors], year: Option[com.histmersion.api.library.Acquire.CitekeyCandidatesYear], title: Option[com.histmersion.api.library.Acquire.CitekeyCandidatesTitle]): List[com.histmersion.api.library.Acquire.CitekeyCandidatesResult] =
    List(citekey(authors, year, title), legacyCitekey(authors, year, title)).distinct

  object Verdicts:
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Retracted = "retracted"
    val Corrected = "corrected"
    val Current = "current"
    val LowImpact = "low-impact"
    val Unassessed = "unassessed"
    val all: List[com.histmersion.api.library.Acquire.Verdicts.All] = List(_root_.com.histmersion.api.library.Acquire.Verdicts.All(Retracted), _root_.com.histmersion.api.library.Acquire.Verdicts.All(Corrected), _root_.com.histmersion.api.library.Acquire.Verdicts.All(Current), _root_.com.histmersion.api.library.Acquire.Verdicts.All(LowImpact), _root_.com.histmersion.api.library.Acquire.Verdicts.All(Unassessed))

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

  private def isRetractingNature(n: com.histmersion.api.library.Acquire.IsRetractingNatureN): com.histmersion.api.library.Acquire.IsRetractingNatureResult =
    val s = (n).value.toLowerCase
    _root_.com.histmersion.api.library.Acquire.IsRetractingNatureResult(s.contains("retraction") && !s.contains("reinstatement") || s.contains("withdrawal"))

  def verdict(
      retractions: List[RetractionNotice],
      updates: List[UpdateNotice],
      openalexRetracted: com.histmersion.api.library.Acquire.VerdictOpenalexRetracted,
      citedByCount: Option[com.histmersion.api.library.Acquire.VerdictCitedByCount],
      year: Option[com.histmersion.api.library.Acquire.VerdictYear],
      wellCitedAt: com.histmersion.api.library.Acquire.VerdictWellCitedAt,
      lowCitedAt: com.histmersion.api.library.Acquire.VerdictLowCitedAt,
      staleBefore: com.histmersion.api.library.Acquire.VerdictStaleBefore
  ): com.histmersion.api.library.Acquire.VerdictResult =
    val kinds = updates.flatMap(_.kind).map(_.trim.toLowerCase)
    if ((openalexRetracted) == _root_.com.histmersion.api.library.Acquire.VerdictOpenalexRetracted(true)) || retractions.exists(r => r.nature.exists(isRetractingNature)) ||
      kinds.exists(RetractingUpdate.contains)
    then _root_.com.histmersion.api.library.Acquire.VerdictResult(Verdicts.Retracted)
    else if retractions.nonEmpty || kinds.exists(CorrectingUpdate.contains) then _root_.com.histmersion.api.library.Acquire.VerdictResult(Verdicts.Corrected)
    else
      citedByCount match
        case None                                                      => _root_.com.histmersion.api.library.Acquire.VerdictResult(Verdicts.Unassessed)
        case Some(c) if c >= wellCitedAt                               => _root_.com.histmersion.api.library.Acquire.VerdictResult(Verdicts.Current)
        case Some(c) if (c).value < (lowCitedAt).value && year.exists((_).value < staleBefore) => _root_.com.histmersion.api.library.Acquire.VerdictResult(Verdicts.LowImpact)
        case Some(_)                                                   => _root_.com.histmersion.api.library.Acquire.VerdictResult(Verdicts.Current)

  /** Some publishers answer scripts with a 403, an empty file or a challenge, so an open-access work they host is
    * downloaded in the browser, under its licence.
    */
  val BrowserDownloadTopic = "browser download"

  def browserDownloadText(work: WorkRef, doi: com.histmersion.api.library.Acquire.BrowserDownloadTextDoi, citekey: com.histmersion.api.library.Acquire.BrowserDownloadTextCitekey, refusals: List[com.histmersion.api.library.Acquire.BrowserDownloadTextRefusals] = Nil): Option[com.histmersion.api.library.Acquire.BrowserDownloadTextResult] =
    Option.when(((work.isOa) == _root_.com.histmersion.api.library.WorkRef.IsOa(true))) {
      val who = work.authors.map(_.value).headOption.map(a => if work.authors.map(_.value).size > 1 then s"$a et al." else a)
      val head = List(who, work.year.map(_.value.toInt).map(y => s"($y)")).flatten.mkString(" ")
      val title = work.title.map(_.value).map(_.trim).filter(_.nonEmpty).getOrElse(doi)
      val oa = work.oaStatus.map(_.trim.toLowerCase).filter(_.nonEmpty).fold("open access")(s => s"$s open access")
      val at = work.oaUrl
        .map(_.trim)
        .filter(u => u.nonEmpty && !u.toLowerCase.contains(doi.toLowerCase))
        .fold("")(u => s" at $u")
      val said = refusals.map((_).value.trim).filter(_.nonEmpty).distinct.mkString("; ")
      _root_.com.histmersion.api.library.Acquire.BrowserDownloadTextResult(s"${if head.isEmpty then "" else s"$head. "}$title. https://doi.org/$doi — $oa$at, but no script may download " +
        s"it: download it in the browser${
            if said.isEmpty then "" else s" ($said)"
          }, then histmersion ingest <file> --citekey $citekey")
    }

  def flagged(verdict: com.histmersion.api.library.Acquire.FlaggedVerdict): com.histmersion.api.library.Acquire.FlaggedResult = _root_.com.histmersion.api.library.Acquire.FlaggedResult(verdict == _root_.com.histmersion.api.library.Acquire.FlaggedVerdict(Verdicts.Retracted) || verdict == _root_.com.histmersion.api.library.Acquire.FlaggedVerdict(Verdicts.Corrected))

  def recommendation(verdict: com.histmersion.api.library.Acquire.RecommendationVerdict, citedByCount: Option[com.histmersion.api.library.Acquire.RecommendationCitedByCount]): com.histmersion.api.library.Acquire.RecommendationResult =
    val cited = citedByCount.fold("no citation data")(c => s"$c citation(s)")
    verdict match
      case Verdicts.Retracted =>
        _root_.com.histmersion.api.library.Acquire.RecommendationResult("EXCLUDE — a retraction, withdrawal or removal notice exists; set status=retracted and stop citing it")
      case Verdicts.Corrected =>
        _root_.com.histmersion.api.library.Acquire.RecommendationResult("CHECK — an amendment (correction / erratum / expression of concern) exists; read it before relying on the paper")
      case Verdicts.Current   => _root_.com.histmersion.api.library.Acquire.RecommendationResult(s"KEEP — $cited, no amendment on record")
      case Verdicts.LowImpact => _root_.com.histmersion.api.library.Acquire.RecommendationResult(s"REVIEW — old and rarely cited ($cited); keep only if it is directly on topic")
      case _                  => _root_.com.histmersion.api.library.Acquire.RecommendationResult(s"UNASSESSED — $cited; judge relevance to the topic by hand")

  def statusFor(verdict: com.histmersion.api.library.Acquire.StatusForVerdict): Option[com.histmersion.api.library.Source.Status] = verdict match
    case Verdicts.Retracted => Some(com.histmersion.api.library.Source.Status.Retracted)
    case Verdicts.Corrected => Some(com.histmersion.api.library.Source.Status.Contested)
    case Verdicts.Current   => Some(com.histmersion.api.library.Source.Status.Current)
    case _                  => None

  def statusDecision(
      verdict: com.histmersion.api.library.Acquire.StatusDecisionVerdict,
      current: com.histmersion.api.library.Source.Status,
      force: com.histmersion.api.library.Acquire.StatusDecisionForce
  ): Option[Either[com.histmersion.api.library.Source.Status, com.histmersion.api.library.Source.Status]] =
    statusFor(verdict).map { target =>
      if target == com.histmersion.api.library.Source.Status.Retracted || force || current == com.histmersion.api.library.Source.Status.Unassessed || current == target
      then Right(target)
      else Left(current)
    }

  object EdgeKinds:
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Confirms = "confirms"
    val Challenges = "challenges"
    val Refutes = "refutes"
    val Supersedes = "supersedes"
    val Cites = "cites"
    val all: List[com.histmersion.api.library.Acquire.EdgeKinds.All] = List(_root_.com.histmersion.api.library.Acquire.EdgeKinds.All(Confirms), _root_.com.histmersion.api.library.Acquire.EdgeKinds.All(Challenges), _root_.com.histmersion.api.library.Acquire.EdgeKinds.All(Refutes), _root_.com.histmersion.api.library.Acquire.EdgeKinds.All(Supersedes), _root_.com.histmersion.api.library.Acquire.EdgeKinds.All(Cites))

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
      intents: List[com.histmersion.api.library.Acquire.ClassifyIntents],
      contexts: List[com.histmersion.api.library.Acquire.ClassifyContexts],
      isInfluential: com.histmersion.api.library.Acquire.ClassifyIsInfluential,
      citingYear: Option[com.histmersion.api.library.Acquire.ClassifyCitingYear],
      targetYear: Option[com.histmersion.api.library.Acquire.ClassifyTargetYear],
      gapYears: com.histmersion.api.library.Acquire.ClassifyGapYears = _root_.com.histmersion.api.library.Acquire.ClassifyGapYears(5)
  ): com.histmersion.api.library.Acquire.ClassifyResult =
    val text = contexts.mkString(" ").toLowerCase
    val strong = intents.map((_).value.toLowerCase).exists(i => i == "result" || i == "methodology")
    val newer = (citingYear, targetYear) match
      case (Some(c), Some(t)) => (c).value - (t).value >= (gapYears).value
      case _                  => false
    def has(cues: List[com.histmersion.api.library.Acquire.ClassifyHasCues]) = cues.exists(text.contains)

    if has((RefutesCues).map(refactorValue7 => _root_.com.histmersion.api.library.Acquire.ClassifyHasCues(refactorValue7))) then _root_.com.histmersion.api.library.Acquire.ClassifyResult(EdgeKinds.Refutes)
    else if has((SupersedeCues).map(refactorValue8 => _root_.com.histmersion.api.library.Acquire.ClassifyHasCues(refactorValue8))) then _root_.com.histmersion.api.library.Acquire.ClassifyResult(EdgeKinds.Supersedes)
    else if ((isInfluential) == _root_.com.histmersion.api.library.Acquire.ClassifyIsInfluential(true)) && strong && newer && has((ReviseCues).map(refactorValue2 => _root_.com.histmersion.api.library.Acquire.ClassifyHasCues(refactorValue2))) then _root_.com.histmersion.api.library.Acquire.ClassifyResult(EdgeKinds.Supersedes)
    else if has((ChallengeCues).map(refactorValue4 => _root_.com.histmersion.api.library.Acquire.ClassifyHasCues(refactorValue4))) then _root_.com.histmersion.api.library.Acquire.ClassifyResult(EdgeKinds.Challenges)
    else if has((ConfirmCues).map(refactorValue5 => _root_.com.histmersion.api.library.Acquire.ClassifyHasCues(refactorValue5))) then _root_.com.histmersion.api.library.Acquire.ClassifyResult(EdgeKinds.Confirms)
    else _root_.com.histmersion.api.library.Acquire.ClassifyResult(EdgeKinds.Cites)

  def rankFollowups(rows: List[FollowupRow]): List[FollowupRow] =
    rows.sortBy { r =>
      val strong = r.intents.map(_.toLowerCase).exists(i => i == "result" || i == "methodology")
      (if ((r.isInfluential) == _root_.com.histmersion.api.library.FollowupRow.IsInfluential(true)) then 0 else 1, if strong then 0 else 1, -r.year.getOrElse(0))
    }

  final case class RetractionRow(doi: com.histmersion.api.library.Doi, reason: Option[RetractionRow.Reason], date: Option[RetractionRow.Date], nature: Option[RetractionRow.Nature])

  object RetractionRow:
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Date = Date.T
    object Date extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Nature = Nature.T
    object Nature extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The export's `Reason` column is full of quoted commas and embedded newlines, so it needs a real CSV reader.
    * Columns are located by header name because their order has changed between exports, and the last row for a DOI
    * wins because the export lists amendments chronologically. The DOI is as the export writes it: the server
    * normalizes it (and drops a row whose DOI is none).
    */
  def parseRetractionWatch(csv: com.histmersion.api.library.Acquire.ParseRetractionWatchCsv): List[RetractionRow] =
    Csv.parse(csv) match
      case Nil            => Nil
      case header :: rows =>
        val idx = header.map(_.trim.replaceAll("^﻿", "")).zipWithIndex.toMap
        def col(r: List[com.histmersion.api.library.Acquire.ParseRetractionWatchColR], name: com.histmersion.api.library.Acquire.ParseRetractionWatchColName): Option[com.histmersion.api.library.Acquire.ParseRetractionWatchColResult] =
          idx.get(name).flatMap(r.lift).map(_.trim).filter(_.nonEmpty)
        rows.flatMap { r =>
          col(r, "OriginalPaperDOI").map { doi =>
            RetractionRow(doi, col(r, "Reason"), col(r, "RetractionDate"), col(r, "RetractionNature"))
          }
        }

  /** Retraction Watch writes dates as `MM/DD/YYYY 0:00`; Postgres wants `YYYY-MM-DD`. */
  def rwDate(raw: Option[com.histmersion.api.library.Acquire.RwDateRaw]): Option[com.histmersion.api.library.Acquire.RwDateResult] =
    raw.map((_).value.trim.takeWhile(_ != ' ')).flatMap { s =>
      s.split("[/-]").toList match
        case m :: d :: y :: Nil if y.length == 4 && m.forall(_.isDigit) && d.forall(_.isDigit) =>
          Some(_root_.com.histmersion.api.library.Acquire.RwDateResult(f"${y.toInt}%04d-${m.toInt}%02d-${d.toInt}%02d"))
        case y :: m :: d :: Nil if y.length == 4 && m.forall(_.isDigit) && d.forall(_.isDigit) =>
          Some(_root_.com.histmersion.api.library.Acquire.RwDateResult(f"${y.toInt}%04d-${m.toInt}%02d-${d.toInt}%02d"))
        case _ => None
    }

  object Checks:

    final case class AcquireCheck(item: AcquireCheck.Item, checkedAt: AcquireCheck.CheckedAt, outcome: AcquireCheck.Outcome, reason: AcquireCheck.Reason, checks: AcquireCheck.Checks = _root_.com.histmersion.api.library.Acquire.Checks.AcquireCheck.Checks(1))
        derives KindlingsCodecAsObject,
          KindlingsSchema, com.histmersion.api.json.Schema

    object AcquireCheck:
      type Item = Item.T
      object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type CheckedAt = CheckedAt.T
      object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Outcome = Outcome.T
      object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
      type DescribeResult = DescribeResult.T
      object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type All = All.T
      object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type UpTo = UpTo.T
      object UpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Since = Since.T
      object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type By = By.T
      object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Resumes = Resumes.T
      object Resumes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      val Detail: com.histmersion.api.jobs.JobDetail.Field[Option[RecheckJob]] =
        com.histmersion.api.jobs.JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("recheck"), None)

      val JobKind = com.histmersion.api.library.AcquisitionJobKinds.AcquireRecheck.kind.value

      def describe(j: RecheckJob): com.histmersion.api.library.Acquire.Checks.RecheckJob.DescribeResult =
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
      type Job = Job.T
      object Job extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    val Closed = "closed"
    val Fetched = "fetched"
    val NoCopy = "no-copy"
    val Failed = "failed"
    val NoDoi = "no-doi"
    val WouldClose = "would-close"
    val WouldTry = "would-try"
    val Due = "due"
