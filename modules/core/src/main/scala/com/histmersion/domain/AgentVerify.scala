package com.histmersion
package domain

import com.histmersion.api.Years
import com.histmersion.api.catalogue.Period

import com.histmersion.util.text.ComparedText
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Source
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug

/** The deterministic checks every LLM answer passes through before code writes anything
  * (docs/research-embedded-agents.md §4.4). All pure; the engine supplies the corpus facts (the body, the set of
  * existing citekeys and slugs) as arguments.
  *
  * JVM-only because quote matching needs `java.text.Normalizer` (NFKC), which Scala.js lacks.
  */
object AgentVerify:
  type MaxAbsYear = MaxAbsYear.T
  object MaxAbsYear extends RefinedType[Long, Pure]
  type MinRatio = MinRatio.T
  object MinRatio extends RefinedType[Double, Pure]
  type NormalisedText = NormalisedText.T
  object NormalisedText extends RefinedType[String, Pure]
  type PeriodEnd = PeriodEnd.T
  object PeriodEnd extends RefinedType[Long, Pure]
  type PeriodStart = PeriodStart.T
  object PeriodStart extends RefinedType[Long, Pure]
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  type Ratio = Ratio.T
  object Ratio extends RefinedType[Double, Pure]
  type SignedYear = SignedYear.T
  object SignedYear extends RefinedType[Long, Pure]
  type SourceBody = SourceBody.T
  object SourceBody extends RefinedType[String, Pure]
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  type Wikilink = Wikilink.T
  object Wikilink extends RefinedType[String, Pure]

  /** A tool call an LLM step made, as the engine records it ([[com.histmersion.domain.automation.agents.ToolUse]]). */
  type SimilarWordA = String
  type SimilarWordB = String
  type LinkProblemsText = Text
  val LinkProblemsText: Text.type = Text
  type ToSignedYearValue = ToSignedYearValue.T
  object ToSignedYearValue extends RefinedType[Long, Pure]

  export com.histmersion.domain.automation.agents.ToolUse

  // ── quotes ──────────────────────────────────────────────────────────────────────────────────

  /** How a proposed quote was found in the body. `ratio` is the share of the quote's words found in order inside one
    * stretch of the body (1.0 = verbatim after normalisation).
    */
  enum QuoteMatch:
    case Verbatim
    case Fuzzy(matched: QuoteMatch.Fuzzy.Matched)
    case Missing(best: QuoteMatch.Missing.Best)

    def ratio: Ratio = this match
      case Verbatim   => Ratio(1.0)
      case Fuzzy(r)   => r
      case Missing(r) => r

    def accepted: Boolean = this match
      case Missing(_) => false
      case _          => true

  object QuoteMatch:
    object Fuzzy:
      export AgentVerify.{Ratio as Matched}

    object Missing:
      export AgentVerify.{Ratio as Best}


  /** The text an OCR'd body and a model's quote are compared in ([[ComparedText.normalise]]). */
  def normalise(s: Text): NormalisedText = normalised(s.value)

  private def normalised(s: String): NormalisedText =
    NormalisedText(ComparedText.normalise(ComparedText.NormaliseS(s)).value)

  private def words(normalised: NormalisedText): Array[String] =
    normalised.value.split("[^\\p{L}\\p{N}']+").filter(_.nonEmpty)

  /** Two words match if equal, or — at four letters or more — one OCR slip apart (`tbe`/`the` is too short to trust;
    * `Mycenaean`/`Mycenacan` is not).
    */
  private[domain] def similarWord(a: SimilarWordA, b: SimilarWordB): Boolean =
    a == b || (math.min(a.length, b.length) >= 4 && math.abs(a.length - b.length) <= 1 && editDistanceAtMostOne(a, b))

  private def editDistanceAtMostOne(a: String, b: String): Boolean =
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
  private def lcs(q: Array[String], w: Array[String]): Int =
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
  def findQuote(quote: AgentProposals.Proposal.Quote, body: SourceBody, minRatio: MinRatio): QuoteMatch =
    val q = normalised(quote.value)
    val b = normalised(body.value)
    if q.value.isEmpty then QuoteMatch.Missing(QuoteMatch.Missing.Best(0))
    else if b.value.contains(q.value) then QuoteMatch.Verbatim
    else
      val qw = words(q)
      if qw.length < 4 then QuoteMatch.Missing(QuoteMatch.Missing.Best(0))
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
        if best >= minRatio.value then QuoteMatch.Fuzzy(QuoteMatch.Fuzzy.Matched(best)) else QuoteMatch.Missing(QuoteMatch.Missing.Best(best))

  /** Titles compared the way a person would: normalised, punctuation ignored. */
  def sameTitle(a: Title, b: Title): Boolean =
    words(normalised(a.value)).mkString(" ") == words(normalised(b.value)).mkString(" ")

  // ── vocabularies and links ───────────────────────────────────────────────────────────────────

  /** A model-proposed status, against the enum. Statuses the DB does not know are rejected, never coerced
    * (`SourceStatus.fromString` would quietly turn a typo into `unassessed`).
    */
  def status(s: com.histmersion.domain.library.Source.Status.Wire): Either[Problem, Source.Status] =
    Source.Status.values
      .find(_.wire.value == s.value.trim.toLowerCase(java.util.Locale.ROOT))
      .toRight(Problem(s"'$s' is not a status (one of: ${Source.Status.values.map(_.wire).mkString(", ")})"))

  def confidence(s: com.histmersion.domain.library.Confidence.Wire): Either[Problem, Confidence] =
    Confidence.fromString(Confidence.Wire(s.value.trim)).toRight(Problem(s"'$s' is not a confidence (one of: high, medium, low, contested)"))

  /** `[[x]]` targets in a text. */
  def wikilinks(text: Text): List[Wikilink] =
    "\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?\\]\\]".r.findAllMatchIn(text.value).map(m => Wikilink(m.group(1).trim)).toList.distinct

  /** Every problem with a text's links: a target that is neither a citekey nor an article slug, and escaped links
    * (`\[\[…\]\]`), which render as text and link nothing.
    */
  def linkProblems(text: LinkProblemsText, isCitekey: Citekey => Boolean, isSlug: Slug => Boolean): List[Problem] =
    val escaped = Option.when(text.value.contains("\\[\\["))(Problem("escaped link brackets (\\[\\[…\\]\\]) link nothing"))
    val dangling = wikilinks(text)
      .filterNot(t =>
        Citekey.option(t.value).exists(isCitekey) ||
          Slug.option(t.value).exists(isSlug)
      )
      .map(t => Problem(s"[[$t]] is neither a citekey nor an article"))
    escaped.toList ++ dangling

  // ── years and periods ────────────────────────────────────────────────────────────────────────

  /** How a model states a year. It never converts; code does. `BP` is RAW (uncalibrated) radiocarbon years, which
    * cannot be turned into a calendar year without a calibration curve.
    */
  enum YearUnit(val wire: YearUnit.Wire):
    case BCE extends YearUnit(YearUnit.Wire("BCE"))
    case CE extends YearUnit(YearUnit.Wire("CE"))
    case CalBP extends YearUnit(YearUnit.Wire("calBP"))
    case BP extends YearUnit(YearUnit.Wire("BP"))

  object YearUnit:
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]


    def parse(s: Wire): Option[YearUnit] =
      s.value.trim match
        case "BCE" | "BC" | "bce" | "bc" | "B.C." | "B.C.E."        => Some(BCE)
        case "CE" | "AD" | "ce" | "ad" | "A.D." | "C.E."            => Some(CE)
        case "calBP" | "cal BP" | "calbp" | "cal. BP" | "cal yr BP" => Some(CalBP)
        case "BP" | "bp" | "uncal BP" | "uncalBP" | "14C BP"        => Some(BP)
        case _                                                      => None

  /** A stated year → Histmersion's signed year (historical reckoning, no year 0: 1 BCE = -1, 1 CE = 1; V015). `calBP`
    * is astronomical `1950 − BP` (which has a year 0) mapped to historical. Raw `BP` is refused: it needs calibration,
    * and a guess would sit on the map as if it were a date.
    */
  def toSignedYear(value: ToSignedYearValue, unit: YearUnit): Either[Problem, SignedYear] = unit match
    case YearUnit.BCE if value.value <= 0 => Left(Problem(s"$value BCE: a BCE year is a positive number"))
    case YearUnit.CE if value.value <= 0  => Left(Problem(s"$value CE: there is no year 0 (or a negative CE year); use BCE"))
    case YearUnit.BCE                     => Right(SignedYear(-value.value))
    case YearUnit.CE                      => Right(SignedYear(value.value))
    case YearUnit.CalBP                   =>
      Right(SignedYear(Years.fromAstronomical(Years.Astronomical(1950L - value.value)).value))
    case YearUnit.BP => Left(Problem(s"$value BP is an uncalibrated radiocarbon age; it cannot be stored as a calendar year"))

  /** A proposed period's problems (empty = sane): start after end, a year 0, a year beyond `maxAbsYear`, and — when the
    * source has a period of its own — a finding outside it.
    */
  def periodProblems(start: PeriodStart, end: PeriodEnd, maxAbsYear: MaxAbsYear, within: Option[Period] = None): List[Problem] =
    List(
      Option.when(start.value > end.value)(Problem(s"starts ($start) after it ends ($end)")),
      Option.when(start.value == 0 || end.value == 0)(Problem("year 0 does not exist (1 BCE is -1, 1 CE is 1)")),
      Option.when(math.abs(start.value) > maxAbsYear.value || math.abs(end.value) > maxAbsYear.value)(Problem(s"a year beyond ±$maxAbsYear")),
      within.flatMap(p =>
        Option.when(start.value < p.start.value || end.value > p.end.value)(
          Problem(s"$start..$end lies outside the source's own period ${p.start}..${p.end}")
        )
      )
    ).flatten
