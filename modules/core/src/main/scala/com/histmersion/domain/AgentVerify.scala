package com.histmersion.domain

import com.histmersion.api.Years
import com.histmersion.api.catalogue.Period

import com.histmersion.util.text.ComparedText
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Source

/** The deterministic checks every LLM answer passes through before code writes anything
  * (docs/research-embedded-agents.md §4.4). All pure; the engine supplies the corpus facts (the body, the set of
  * existing citekeys and slugs) as arguments.
  *
  * JVM-only because quote matching needs `java.text.Normalizer` (NFKC), which Scala.js lacks.
  */
object AgentVerify:

  /** A tool call an LLM step made, as the engine records it ([[com.histmersion.domain.automation.agents.ToolUse]]). */
  type NormaliseResult = NormaliseResult.T
  object NormaliseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormaliseS = NormaliseS.T
  object NormaliseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WordsResult = WordsResult.T
  object WordsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WordsNormalised = WordsNormalised.T
  object WordsNormalised extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SimilarWordResult = SimilarWordResult.T
  object SimilarWordResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SimilarWordA = SimilarWordA.T
  object SimilarWordA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SimilarWordB = SimilarWordB.T
  object SimilarWordB extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditDistanceAtMostOneResult = EditDistanceAtMostOneResult.T
  object EditDistanceAtMostOneResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditDistanceAtMostOneA = EditDistanceAtMostOneA.T
  object EditDistanceAtMostOneA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditDistanceAtMostOneB = EditDistanceAtMostOneB.T
  object EditDistanceAtMostOneB extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LcsResult = LcsResult.T
  object LcsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LcsQ = LcsQ.T
  object LcsQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LcsW = LcsW.T
  object LcsW extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindQuoteQuote = FindQuoteQuote.T
  object FindQuoteQuote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindQuoteBody = FindQuoteBody.T
  object FindQuoteBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindQuoteMinRatio = FindQuoteMinRatio.T
  object FindQuoteMinRatio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SameTitleResult = SameTitleResult.T
  object SameTitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SameTitleA = SameTitleA.T
  object SameTitleA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SameTitleB = SameTitleB.T
  object SameTitleB extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusResultPart1 = StatusResultPart1.T
  object StatusResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusS = StatusS.T
  object StatusS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfidenceResultPart1 = ConfidenceResultPart1.T
  object ConfidenceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfidenceS = ConfidenceS.T
  object ConfidenceS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikilinksResult = WikilinksResult.T
  object WikilinksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikilinksText = WikilinksText.T
  object WikilinksText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkProblemsResult = LinkProblemsResult.T
  object LinkProblemsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkProblemsText = LinkProblemsText.T
  object LinkProblemsText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkProblemsIsCitekeyArg1 = LinkProblemsIsCitekeyArg1.T
  object LinkProblemsIsCitekeyArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkProblemsIsCitekeyResult = LinkProblemsIsCitekeyResult.T
  object LinkProblemsIsCitekeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkProblemsIsSlugArg1 = LinkProblemsIsSlugArg1.T
  object LinkProblemsIsSlugArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkProblemsIsSlugResult = LinkProblemsIsSlugResult.T
  object LinkProblemsIsSlugResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToSignedYearResultPart1 = ToSignedYearResultPart1.T
  object ToSignedYearResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToSignedYearResultPart2 = ToSignedYearResultPart2.T
  object ToSignedYearResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToSignedYearValue = ToSignedYearValue.T
  object ToSignedYearValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodProblemsResult = PeriodProblemsResult.T
  object PeriodProblemsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodProblemsStart = PeriodProblemsStart.T
  object PeriodProblemsStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodProblemsEnd = PeriodProblemsEnd.T
  object PeriodProblemsEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodProblemsMaxAbsYear = PeriodProblemsMaxAbsYear.T
  object PeriodProblemsMaxAbsYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  export com.histmersion.domain.automation.agents.ToolUse

  // ── quotes ──────────────────────────────────────────────────────────────────────────────────

  /** How a proposed quote was found in the body. `ratio` is the share of the quote's words found in order inside one
    * stretch of the body (1.0 = verbatim after normalisation).
    */
  enum QuoteMatch:
    case Verbatim
    case Fuzzy(matched: QuoteMatch.Fuzzy.Matched)
    case Missing(best: QuoteMatch.Missing.Best)

    def ratio: com.histmersion.domain.AgentVerify.QuoteMatch.RatioResult = this match
      case Verbatim   => 1.0
      case Fuzzy(r)   => r
      case Missing(r) => r

    def accepted: com.histmersion.domain.AgentVerify.QuoteMatch.AcceptedResult = this match
      case Missing(_) => false
      case _          => true

  object QuoteMatch:
    object Fuzzy:
      type Matched = Matched.T
      object Matched extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object Missing:
      type Best = Best.T
      object Best extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type RatioResult = RatioResult.T
    object RatioResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AcceptedResult = AcceptedResult.T
    object AcceptedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The text an OCR'd body and a model's quote are compared in ([[ComparedText.normalise]]). */
  def normalise(s: com.histmersion.domain.AgentVerify.NormaliseS): com.histmersion.domain.AgentVerify.NormaliseResult = ComparedText.normalise(s)

  private def words(normalised: com.histmersion.domain.AgentVerify.WordsNormalised): Array[com.histmersion.domain.AgentVerify.WordsResult] =
    normalised.split("[^\\p{L}\\p{N}']+").filter(_.nonEmpty)

  /** Two words match if equal, or — at four letters or more — one OCR slip apart (`tbe`/`the` is too short to trust;
    * `Mycenaean`/`Mycenacan` is not).
    */
  private[domain] def similarWord(a: com.histmersion.domain.AgentVerify.SimilarWordA, b: com.histmersion.domain.AgentVerify.SimilarWordB): com.histmersion.domain.AgentVerify.SimilarWordResult =
    a == b || (math.min(a.length, b.length) >= 4 && math.abs(a.length - b.length) <= 1 && editDistanceAtMostOne(a, b))

  private def editDistanceAtMostOne(a: com.histmersion.domain.AgentVerify.EditDistanceAtMostOneA, b: com.histmersion.domain.AgentVerify.EditDistanceAtMostOneB): com.histmersion.domain.AgentVerify.EditDistanceAtMostOneResult =
    if a.length == b.length then a.indices.count(i => a(i) != b(i)) <= 1
    else
      val (s, l) = if a.length < b.length then (a, b) else (b, a)
      var i = 0 // index into the shorter word
      var j = 0 // index into the longer word
      var skipped = false
      var ok = true
      while ok && i < s.length && j < l.length do
        if s(i) == l(j) then { i += 1; j += 1 }
        else if skipped then ok = false
        else { skipped = true; j += 1 }
      ok

  /** Longest common subsequence of `q` inside `w` under [[similarWord]] — how many quote words appear, in order, in
    * that stretch of the body.
    */
  private def lcs(q: Array[com.histmersion.domain.AgentVerify.LcsQ], w: Array[com.histmersion.domain.AgentVerify.LcsW]): com.histmersion.domain.AgentVerify.LcsResult =
    val prev = Array.fill(w.length + 1)(0)
    val cur = Array.fill(w.length + 1)(0)
    for i <- q.indices do
      for j <- w.indices do cur(j + 1) = if similarWord(q(i), w(j)) then prev(j) + 1 else math.max(prev(j + 1), cur(j))
      Array.copy(cur, 0, prev, 0, cur.length)
    prev(w.length)

  /** Find `quote` in `body`, OCR-tolerantly. Verbatim after [[normalise]] first; otherwise every place a word of the
    * quote's first three occurs anchors a window of 1.3× the quote's length, and the best in-order word match over
    * those windows is the ratio. `minRatio` decides Fuzzy/Missing. A quote of fewer than 4 words must be verbatim: a
    * short quote matches anywhere by chance.
    */
  def findQuote(quote: com.histmersion.domain.AgentVerify.FindQuoteQuote, body: com.histmersion.domain.AgentVerify.FindQuoteBody, minRatio: com.histmersion.domain.AgentVerify.FindQuoteMinRatio): QuoteMatch =
    val q = normalise(quote)
    val b = normalise(body)
    if q.isEmpty then QuoteMatch.Missing(0)
    else if b.contains(q) then QuoteMatch.Verbatim
    else
      val qw = words(q)
      if qw.length < 4 then QuoteMatch.Missing(0)
      else
        val bw = words(b)
        val heads = qw.take(3).toSet
        val span = math.ceil(qw.length * 1.3).toInt
        val anchors = bw.indices.filter(i => heads.exists(h => similarWord(h, bw(i))))
        val best = anchors.iterator
          .map { i =>
            val from = math.max(0, i - 3)
            lcs(qw, bw.slice(from, math.min(bw.length, from + span + 3))).toDouble / qw.length
          }
          .foldLeft(0.0)(math.max)
        if best >= minRatio then QuoteMatch.Fuzzy(best) else QuoteMatch.Missing(best)

  /** Titles compared the way a person would: normalised, punctuation ignored. */
  def sameTitle(a: com.histmersion.domain.AgentVerify.SameTitleA, b: com.histmersion.domain.AgentVerify.SameTitleB): com.histmersion.domain.AgentVerify.SameTitleResult =
    words(normalise(a)).mkString(" ") == words(normalise(b)).mkString(" ")

  // ── vocabularies and links ───────────────────────────────────────────────────────────────────

  /** A model-proposed status, against the enum. Statuses the DB does not know are rejected, never coerced
    * (`SourceStatus.fromString` would quietly turn a typo into `unassessed`).
    */
  def status(s: com.histmersion.domain.AgentVerify.StatusS): Either[com.histmersion.domain.AgentVerify.StatusResultPart1, Source.Status] =
    Source.Status.values
      .find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
      .toRight(s"'$s' is not a status (one of: ${Source.Status.values.map(_.wire).mkString(", ")})")

  def confidence(s: com.histmersion.domain.AgentVerify.ConfidenceS): Either[com.histmersion.domain.AgentVerify.ConfidenceResultPart1, Confidence] =
    Confidence.fromString(s.trim).toRight(s"'$s' is not a confidence (one of: high, medium, low, contested)")

  /** `[[x]]` targets in a text. */
  def wikilinks(text: com.histmersion.domain.AgentVerify.WikilinksText): List[com.histmersion.domain.AgentVerify.WikilinksResult] =
    "\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?\\]\\]".r.findAllMatchIn(text).map(_.group(1).trim).toList.distinct

  /** Every problem with a text's links: a target that is neither a citekey nor an article slug, and escaped links
    * (`\[\[…\]\]`), which render as text and link nothing.
    */
  def linkProblems(text: com.histmersion.domain.AgentVerify.LinkProblemsText, isCitekey: com.histmersion.domain.AgentVerify.LinkProblemsIsCitekeyArg1 => com.histmersion.domain.AgentVerify.LinkProblemsIsCitekeyResult, isSlug: com.histmersion.domain.AgentVerify.LinkProblemsIsSlugArg1 => com.histmersion.domain.AgentVerify.LinkProblemsIsSlugResult): List[com.histmersion.domain.AgentVerify.LinkProblemsResult] =
    val escaped = Option.when(text.contains("\\[\\["))("escaped link brackets (\\[\\[…\\]\\]) link nothing")
    val dangling = wikilinks(text)
      .filterNot(t => isCitekey(t) || isSlug(t))
      .map(t => s"[[$t]] is neither a citekey nor an article")
    escaped.toList ++ dangling

  // ── years and periods ────────────────────────────────────────────────────────────────────────

  /** How a model states a year. It never converts; code does. `BP` is RAW (uncalibrated) radiocarbon years, which
    * cannot be turned into a calendar year without a calibration curve.
    */
  enum YearUnit(val wire: YearUnit.Wire):
    case BCE extends YearUnit("BCE")
    case CE extends YearUnit("CE")
    case CalBP extends YearUnit("calBP")
    case BP extends YearUnit("BP")

  object YearUnit:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.domain.AgentVerify.YearUnit.ParseS): Option[YearUnit] =
      s.trim match
        case "BCE" | "BC" | "bce" | "bc" | "B.C." | "B.C.E."        => Some(BCE)
        case "CE" | "AD" | "ce" | "ad" | "A.D." | "C.E."            => Some(CE)
        case "calBP" | "cal BP" | "calbp" | "cal. BP" | "cal yr BP" => Some(CalBP)
        case "BP" | "bp" | "uncal BP" | "uncalBP" | "14C BP"        => Some(BP)
        case _                                                      => None

  /** A stated year → Histmersion's signed year (historical reckoning, no year 0: 1 BCE = -1, 1 CE = 1; V015). `calBP`
    * is astronomical `1950 − BP` (which has a year 0) mapped to historical. Raw `BP` is refused: it needs calibration,
    * and a guess would sit on the map as if it were a date.
    */
  def toSignedYear(value: com.histmersion.domain.AgentVerify.ToSignedYearValue, unit: YearUnit): Either[com.histmersion.domain.AgentVerify.ToSignedYearResultPart1, com.histmersion.domain.AgentVerify.ToSignedYearResultPart2] = unit match
    case YearUnit.BCE if value <= 0 => Left(s"$value BCE: a BCE year is a positive number")
    case YearUnit.CE if value <= 0  => Left(s"$value CE: there is no year 0 (or a negative CE year); use BCE")
    case YearUnit.BCE               => Right(-value)
    case YearUnit.CE                => Right(value)
    case YearUnit.CalBP             =>
      Right(Years.fromAstronomical(1950L - value))
    case YearUnit.BP => Left(s"$value BP is an uncalibrated radiocarbon age; it cannot be stored as a calendar year")

  /** A proposed period's problems (empty = sane): start after end, a year 0, a year beyond `maxAbsYear`, and — when the
    * source has a period of its own — a finding outside it.
    */
  def periodProblems(start: com.histmersion.domain.AgentVerify.PeriodProblemsStart, end: com.histmersion.domain.AgentVerify.PeriodProblemsEnd, maxAbsYear: com.histmersion.domain.AgentVerify.PeriodProblemsMaxAbsYear, within: Option[Period] = None): List[com.histmersion.domain.AgentVerify.PeriodProblemsResult] =
    List(
      Option.when(start > end)(s"starts ($start) after it ends ($end)"),
      Option.when(start == 0 || end == 0)("year 0 does not exist (1 BCE is -1, 1 CE is 1)"),
      Option.when(math.abs(start) > maxAbsYear || math.abs(end) > maxAbsYear)(s"a year beyond ±$maxAbsYear"),
      within.flatMap(p =>
        Option.when(start < p.start || end > p.end)(
          s"$start..$end lies outside the source's own period ${p.start}..${p.end}"
        )
      )
    ).flatten
