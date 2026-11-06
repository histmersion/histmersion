package com.histmersion.domain

import com.histmersion.util.ocr.Chunk

import com.histmersion.api.LatinFold
import com.histmersion.api.catalogue.MatchSpan
import com.histmersion.api.library.{PassageHit, PassageResult}
import com.histmersion.api.given

import java.util.regex.{Matcher, Pattern, PatternSyntaxException}

/** Finds passages inside ONE source's converted body (#954) — pure, no I/O.
  *
  * The body is the one `get_source` slices: the source's chunks joined by a blank line ([[body]]), so an offset
  * reported here is an offset `get_source {offset}` continues from.
  *
  * Matching:
  *   - case-insensitive, and accent-insensitive in the way [[LatinFold]] is: `Hattusa` finds `Ḫattuša`, `Guterbock`
  *     finds `Güterbock`. The body and the query are folded the same way, plus the typography OCR leaves behind: curly
  *     quotes match straight ones, every dash matches `-`, a no-break space matches a space, `ﬁ`/`ﬂ` ligatures match
  *     `fi`/`fl`, soft hyphens vanish. Other scripts (Greek accents, …) are matched as written.
  *   - by default the query is LITERAL text (no stemming, no operators); its words may be separated by any run of
  *     whitespace in the body, so a phrase matches across a line break.
  *   - `regex` makes it a Java regular expression, MULTILINE (`^`/`$` are line ends, as in grep). A malformed one is a
  *     `Left`, and one that runs longer than `timeoutMs` over the body is refused rather than left to hold a request
  *     thread.
  *
  * A passage is the match plus up to `context` characters around it (half before, half after), widened to whole lines
  * when a line break is in reach, else to whole words. Matches whose passage would overlap the previous one are merged
  * into it.
  */
object PassageFinder:

  /** The body `get_source` reads — chunks in order, joined by a blank line (`SourceRepo.bodyText`). */
  type BodyResult = BodyResult.T
  object BodyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindResultPart1 = FindResultPart1.T
  object FindResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindQuery = FindQuery.T
  object FindQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindRegex = FindRegex.T
  object FindRegex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindLimit = FindLimit.T
  object FindLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindContext = FindContext.T
  object FindContext extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindMaxMatches = FindMaxMatches.T
  object FindMaxMatches extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindTimeoutMs = FindTimeoutMs.T
  object FindTimeoutMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompileResultPart1 = CompileResultPart1.T
  object CompileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompileQ = CompileQ.T
  object CompileQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompileRegex = CompileRegex.T
  object CompileRegex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldCharResult = FoldCharResult.T
  object FoldCharResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldCharC = FoldCharC.T
  object FoldCharC extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldResult = FoldResult.T
  object FoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldS = FoldS.T
  object FoldS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsMarkResult = IsMarkResult.T
  object IsMarkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsMarkC = IsMarkC.T
  object IsMarkC extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesResultPart1Part1 = MatchesResultPart1Part1.T
  object MatchesResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesResultPart1Part2 = MatchesResultPart1Part2.T
  object MatchesResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesResultPart2 = MatchesResultPart2.T
  object MatchesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesSource = MatchesSource.T
  object MatchesSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesMax = MatchesMax.T
  object MatchesMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeSpansPart1 = MergeSpansPart1.T
  object MergeSpansPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeSpansPart2 = MergeSpansPart2.T
  object MergeSpansPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeText = MergeText.T
  object MergeText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeContext = MergeContext.T
  object MergeContext extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartOfResult = StartOfResult.T
  object StartOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartOfText = StartOfText.T
  object StartOfText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartOfS = StartOfS.T
  object StartOfS extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartOfHalf = StartOfHalf.T
  object StartOfHalf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkipBlanksResult = SkipBlanksResult.T
  object SkipBlanksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkipBlanksText = SkipBlanksText.T
  object SkipBlanksText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkipBlanksFrom = SkipBlanksFrom.T
  object SkipBlanksFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkipBlanksUpTo = SkipBlanksUpTo.T
  object SkipBlanksUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndOfResult = EndOfResult.T
  object EndOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndOfText = EndOfText.T
  object EndOfText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndOfE = EndOfE.T
  object EndOfE extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndOfHalf = EndOfHalf.T
  object EndOfHalf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DropBlanksResult = DropBlanksResult.T
  object DropBlanksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DropBlanksText = DropBlanksText.T
  object DropBlanksText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DropBlanksTo = DropBlanksTo.T
  object DropBlanksTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DropBlanksDownTo = DropBlanksDownTo.T
  object DropBlanksDownTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def body(segments: Seq[Chunk]): com.histmersion.domain.PassageFinder.BodyResult = segments.map(_.body).mkString(Separator)

  private val Separator = "\n\n"

  def find(
      citekey: com.histmersion.domain.library.Citekey,
      segments: Seq[Chunk],
      query: com.histmersion.domain.PassageFinder.FindQuery,
      regex: com.histmersion.domain.PassageFinder.FindRegex,
      limit: com.histmersion.domain.PassageFinder.FindLimit,
      context: com.histmersion.domain.PassageFinder.FindContext,
      maxMatches: com.histmersion.domain.PassageFinder.FindMaxMatches,
      timeoutMs: com.histmersion.domain.PassageFinder.FindTimeoutMs
  )(using timer: com.histmersion.util.time.Timer): Either[com.histmersion.domain.PassageFinder.FindResultPart1, PassageResult] =
    val q = Option(query).getOrElse("").trim
    if q.isEmpty then Left("the query is empty — give the words to find")
    else
      compile(q, regex).flatMap { pattern =>
        val text = body(segments)
        val folded = Folded(text)
        val until = timer.after(scala.concurrent.duration.FiniteDuration(timeoutMs.max(1L), scala.concurrent.duration.MILLISECONDS))
        try
          val (spans, capped) = matches(pattern, folded, text, maxMatches.max(1), until)
          val ranges = merge(spans, text, context.max(0))
          val headings = Headings(segments, text)
          val shown = ranges.take(limit.max(1)).map { r =>
            PassageHit(
              r.start,
              headings.at(r.firstMatch),
              text.substring(r.start, r.end),
              r.spans.reverse.map((s, e) => MatchSpan(s - r.start, e - r.start))
            )
          }
          Right(PassageResult(citekey, q, regex, spans.size, capped, ranges.size, shown.toList, text.length))
        catch
          case _: TimedOut =>
            Left(
              s"the regex ran longer than $timeoutMs ms over this body — make it more specific " +
                "(anchor it, avoid nested repetition like (a+)+)"
            )
      }

  /** The query as a pattern over the FOLDED body. */
  private[domain] def compile(q: com.histmersion.domain.PassageFinder.CompileQ, regex: com.histmersion.domain.PassageFinder.CompileRegex): Either[com.histmersion.domain.PassageFinder.CompileResultPart1, Pattern] =
    val flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE
    if regex then
      try Right(Pattern.compile(fold(q), flags))
      catch
        case e: PatternSyntaxException =>
          val at = if e.getIndex >= 0 then s" at position ${e.getIndex}" else ""
          Left(
            s"not a valid regex: ${e.getDescription}$at in `${e.getPattern}` (leave regex off to match the text literally)"
          )
    else
      val words = fold(q).split("\\s+").toList.filter(_.nonEmpty)
      Right(Pattern.compile(words.map(Pattern.quote).mkString("\\s+"), flags))

  /** One character as the matcher sees it: [[LatinFold]] plus the OCR typography. */
  private[domain] def foldChar(c: com.histmersion.domain.PassageFinder.FoldCharC): com.histmersion.domain.PassageFinder.FoldCharResult =
    if c < '\u0080' then c.toString
    else
      c match
        case '‘' | '’' | '‚' | '‛' | '′'             => "'"
        case '“' | '”' | '„' | '‟' | '″'             => "\""
        case '‐' | '‑' | '‒' | '–' | '—' | '―' | '−' => "-"
        case ' ' | ' ' | ' ' | ' '                   => " "
        case '­'                                     => ""
        case 'ﬀ'                                     => "ff"
        case 'ﬁ'                                     => "fi"
        case 'ﬂ'                                     => "fl"
        case 'ﬃ'                                     => "ffi"
        case 'ﬄ'                                     => "ffl"
        case _                                       => LatinFold.foldChar(c)

  private[domain] def fold(s: com.histmersion.domain.PassageFinder.FoldS): com.histmersion.domain.PassageFinder.FoldResult = Folded(s).text

  /** `source` folded, with the index of the source character every folded character came from (`back(folded.length) ==
    * source.length`), so a match maps back to the original offsets.
    */
  final private[domain] class Folded(val text: Folded.Text, back: Array[Int]):
    def start(i: com.histmersion.domain.PassageFinder.Folded.StartI): com.histmersion.domain.PassageFinder.Folded.StartResult = back(i)

    /** The original end of a match ending (exclusive) at folded index `i`. */
    def end(i: com.histmersion.domain.PassageFinder.Folded.EndI, source: com.histmersion.domain.PassageFinder.Folded.EndSource): com.histmersion.domain.PassageFinder.Folded.EndResult =
      if i <= 0 then 0
      else
        // the combining marks of the last letter belong to the match too
        source.indexWhere(c => !isMark(c), back(i - 1) + 1) match
          case -1 => source.length
          case k  => k

  private[domain] object Folded:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type StartResult = StartResult.T
    object StartResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartI = StartI.T
    object StartI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EndResult = EndResult.T
    object EndResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EndI = EndI.T
    object EndI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EndSource = EndSource.T
    object EndSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApplySource = ApplySource.T
    object ApplySource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def apply(source: com.histmersion.domain.PassageFinder.Folded.ApplySource): Folded =
      val sb = new java.lang.StringBuilder(source.length) // local accumulators
      val back = new scala.collection.mutable.ArrayBuilder.ofInt
      back.sizeHint(source.length + 1)
      // index loops over a body of up to several MB: no per-character closure or tuple
      var i = 0
      while i < source.length do
        val f = foldChar(source.charAt(i))
        var k = 0
        while k < f.length do
          sb.append(f.charAt(k)); back += i; k += 1
        i += 1
      back += source.length
      new Folded(sb.toString, back.result())

  private def isMark(c: com.histmersion.domain.PassageFinder.IsMarkC): com.histmersion.domain.PassageFinder.IsMarkResult = c >= '̀' && c <= 'ͯ'

  final private class TimedOut extends RuntimeException(null, null, false, false)

  /** A view of the folded body that gives up once `until` (System.nanoTime) has passed: the regex engine reads every
    * character through `charAt`, so a pathological pattern is stopped mid-scan.
    */
  final private class Bounded(s: CharSequence, until: com.histmersion.util.time.Timer.Deadline) extends CharSequence:
    // a counter, so the clock is read once per 64 Ki characters rather than on every one
    private var reads = 0
    def length: com.histmersion.domain.PassageFinder.Bounded.LengthResult = s.length
    def charAt(i: com.histmersion.domain.PassageFinder.Bounded.CharAtI): com.histmersion.domain.PassageFinder.Bounded.CharAtResult =
      reads += 1
      if (reads & 0xffff) == 0 && until.exceeded then throw new TimedOut
      s.charAt(i)
    def subSequence(a: com.histmersion.domain.PassageFinder.Bounded.SubSequenceA, b: com.histmersion.domain.PassageFinder.Bounded.SubSequenceB): CharSequence = new Bounded(s.subSequence(a, b), until)
    override def toString: String = s.toString

  object Bounded:
    type LengthResult = LengthResult.T
    object LengthResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CharAtResult = CharAtResult.T
    object CharAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CharAtI = CharAtI.T
    object CharAtI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SubSequenceA = SubSequenceA.T
    object SubSequenceA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SubSequenceB = SubSequenceB.T
    object SubSequenceB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Every non-empty match as `[start, end)` in the ORIGINAL body, in order, up to `max`. */
  private def matches(p: Pattern, f: Folded, source: com.histmersion.domain.PassageFinder.MatchesSource, max: com.histmersion.domain.PassageFinder.MatchesMax, until: com.histmersion.util.time.Timer.Deadline): (Vector[(com.histmersion.domain.PassageFinder.MatchesResultPart1Part1, com.histmersion.domain.PassageFinder.MatchesResultPart1Part2)], com.histmersion.domain.PassageFinder.MatchesResultPart2) =
    val m: Matcher = p.matcher(new Bounded(f.text, until))
    val out = Vector.newBuilder[(Int, Int)]
    // the Matcher is itself a cursor, so the loop around it counts with two local vars
    var n = 0
    var capped = false
    while !capped && m.find() do
      if until.exceeded then throw new TimedOut
      if m.end() > m.start() then
        if n == max then capped = true
        else
          out += ((f.start(m.start()), f.end(m.end(), source)))
          n += 1
    (out.result(), capped)

  /** A passage being built: `spans` in reverse order of discovery. */
  final private case class Range(start: Range.Start, end: Range.End, firstMatch: Range.FirstMatch, spans: List[(Range.SpansPart1, Range.SpansPart2)])

  object Range:
    type Start = Start.T
    object Start extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type End = End.T
    object End extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstMatch = FirstMatch.T
    object FirstMatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SpansPart1 = SpansPart1.T
    object SpansPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SpansPart2 = SpansPart2.T
    object SpansPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Matches → passages. A match that starts inside the previous passage joins it (the passage then ends no earlier
    * than the match does); any other starts a passage of its own, which begins no earlier than where the previous one
    * ended — so no text is shown twice, and dense matches do not snowball into one enormous passage.
    */
  private def merge(spans: Vector[(com.histmersion.domain.PassageFinder.MergeSpansPart1, com.histmersion.domain.PassageFinder.MergeSpansPart2)], text: com.histmersion.domain.PassageFinder.MergeText, context: com.histmersion.domain.PassageFinder.MergeContext): Vector[Range] =
    val half = context / 2
    spans.foldLeft(Vector.empty[Range]) { case (acc, (s, e)) =>
      acc.lastOption match
        case Some(r) if s < r.end =>
          acc.init :+ r.copy(end = r.end.max(e), spans = (s, e) :: r.spans)
        case last =>
          val floor = last.fold(0)(_.end)
          acc :+ Range(skipBlanks(text, startOf(text, s, half).max(floor), s), endOf(text, e, half), s, List((s, e)))
    }

  /** Where a passage around a match starting at `s` begins: the first line start within `half` characters before it,
    * else the first word start, else exactly `half` before; leading blanks dropped.
    */
  private[domain] def startOf(text: com.histmersion.domain.PassageFinder.StartOfText, s: com.histmersion.domain.PassageFinder.StartOfS, half: com.histmersion.domain.PassageFinder.StartOfHalf): com.histmersion.domain.PassageFinder.StartOfResult =
    val lo = (s - half).max(0)
    val begin =
      if lo == 0 then 0
      else
        val nl = text.indexOf('\n', lo - 1)
        if nl >= 0 && nl + 1 <= s then nl + 1
        else
          val ws = text.indexWhere(_.isWhitespace, lo - 1)
          if ws >= 0 && ws + 1 <= s then ws + 1 else lo
    skipBlanks(text, begin, s)

  @annotation.tailrec
  private def skipBlanks(text: com.histmersion.domain.PassageFinder.SkipBlanksText, from: com.histmersion.domain.PassageFinder.SkipBlanksFrom, upTo: com.histmersion.domain.PassageFinder.SkipBlanksUpTo): com.histmersion.domain.PassageFinder.SkipBlanksResult =
    if from < upTo && text.charAt(from).isWhitespace then skipBlanks(text, from + 1, upTo) else from

  /** Where a passage around a match ending at `e` ends: the last line end within `half` characters after it, else the
    * last word end, else exactly `half` after; trailing blanks dropped.
    */
  private[domain] def endOf(text: com.histmersion.domain.PassageFinder.EndOfText, e: com.histmersion.domain.PassageFinder.EndOfE, half: com.histmersion.domain.PassageFinder.EndOfHalf): com.histmersion.domain.PassageFinder.EndOfResult =
    val hi = (e + half).min(text.length)
    val finish =
      if hi == text.length then hi
      else
        val nl = text.lastIndexOf('\n', hi)
        if nl >= e then nl
        else
          val ws = text.lastIndexWhere(_.isWhitespace, hi)
          if ws >= e then ws else hi
    dropBlanks(text, finish, e)

  @annotation.tailrec
  private def dropBlanks(text: com.histmersion.domain.PassageFinder.DropBlanksText, to: com.histmersion.domain.PassageFinder.DropBlanksTo, downTo: com.histmersion.domain.PassageFinder.DropBlanksDownTo): com.histmersion.domain.PassageFinder.DropBlanksResult =
    if to > downTo && text.charAt(to - 1).isWhitespace then dropBlanks(text, to - 1, downTo) else to

  /** The heading a body offset sits under: its chunk's breadcrumb, or — for a body stored without chunks, whose heading
    * lines are still in the text — the nearest Markdown heading above it.
    */
  final private class Headings(segments: Seq[Chunk], text: Headings.Text):
    private val starts: Array[com.histmersion.domain.PassageFinder.Headings.Starts] =
      segments.iterator.scanLeft(0)((at, c) => at + c.body.length + Separator.length).take(segments.size).toArray

    private lazy val inline: Vector[(com.histmersion.domain.PassageFinder.Headings.InlinePart1, com.histmersion.domain.PassageFinder.Headings.InlinePart2)] =
      val Heading = """(?m)^#{1,6}[ \t]+(.*?)[ \t]*#*[ \t]*$""".r
      Heading.findAllMatchIn(text).map(m => (m.start, m.group(1))).filter(_._2.nonEmpty).toVector

    def at(offset: com.histmersion.domain.PassageFinder.Headings.AtOffset): Option[com.histmersion.domain.PassageFinder.Headings.AtResult] =
      val i = java.util.Arrays.binarySearch(starts, offset) match
        case k if k >= 0 => k
        case k           => -k - 2
      val own = if i >= 0 && i < segments.size then segments(i).heading else None
      own.orElse(inline.takeWhile(_._1 <= offset).lastOption.map(_._2))

  object Headings:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Starts = Starts.T
    object Starts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InlinePart1 = InlinePart1.T
    object InlinePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InlinePart2 = InlinePart2.T
    object InlinePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AtResult = AtResult.T
    object AtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AtOffset = AtOffset.T
    object AtOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
