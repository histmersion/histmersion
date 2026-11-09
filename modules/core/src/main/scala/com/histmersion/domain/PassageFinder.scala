package com.histmersion
package domain

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.ocr.Chunk

import com.histmersion.api.LatinFold
import com.histmersion.api.catalogue.MatchSpan
import com.histmersion.api.library.{PassageHit, PassageResult}
import com.histmersion.api.given

import java.util.regex.{Matcher, Pattern, PatternSyntaxException}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.util.time.Timer

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
  type ContextChars = ContextChars.T
  object ContextChars extends Newtype[Int, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type MaxMatches = MaxMatches.T
  object MaxMatches extends Newtype[Int, Pure]
  type Passage = Passage.T
  object Passage extends Newtype[String, Pure]
  type Query = Query.T
  object Query extends Newtype[String, Pure]
  type Regex = Regex.T
  object Regex extends Newtype[Boolean, Pure]
  type TimeoutMs = TimeoutMs.T
  object TimeoutMs extends Newtype[Long, Pure]

  /** The body `get_source` reads — chunks in order, joined by a blank line (`SourceRepo.bodyText`). */
  def body(segments: Seq[Chunk]): Passage = Passage(segments.map(_.body.value).mkString(Separator))

  private val Separator = "\n\n"

  def find(
      citekey: Citekey,
      segments: Seq[Chunk],
      query: Query,
      regex: Regex,
      limit: Limit,
      context: ContextChars,
      maxMatches: MaxMatches,
      timeoutMs: TimeoutMs
  )(using timer: Timer): Either[Problem, PassageResult] =
    val q = Option(query.value).getOrElse("").trim
    if q.isEmpty then Left(Problem.Unclassified(Legacy.Text("the query is empty — give the words to find")))
    else
      compile(Query(q), regex).flatMap { pattern =>
        val text = body(segments).value
        val folded = Folded(text)
        val until = timer.after(
          scala.concurrent.duration.FiniteDuration(timeoutMs.value.max(1L), scala.concurrent.duration.MILLISECONDS)
        )
        try
          val (spans, capped) = matches(pattern, folded, text, maxMatches.value.max(1), until)
          val ranges = merge(spans, text, context.value.max(0))
          val headings = Headings(segments, text)
          val shown = ranges.take(limit.value.max(1)).map { r =>
            PassageHit(
              PassageHit.Offset(r.start),
              headings.at(r.firstMatch).map(PassageHit.Heading(_)),
              PassageHit.Text(text.substring(r.start, r.end)),
              r.spans.reverse.map((s, e) => MatchSpan(MatchSpan.Start(s - r.start), MatchSpan.End(e - r.start)))
            )
          }
          Right(
            PassageResult(
              com.histmersion.api.library.Citekey(citekey.value),
              PassageResult.Query(q),
              PassageResult.Regex(regex.value),
              PassageResult.Matches(spans.size),
              PassageResult.Capped(capped),
              PassageResult.Total(ranges.size),
              shown.toList,
              PassageResult.BodyChars(text.length)
            )
          )
        catch
          case _: TimedOut =>
            Left(
              Problem.Unclassified(
                Legacy.Text(
                  s"the regex ran longer than $timeoutMs ms over this body — make it more specific " +
                    "(anchor it, avoid nested repetition like (a+)+)"
                )
              )
            )
      }

  /** The query as a pattern over the FOLDED body. */
  private[domain] def compile(q: Query, regex: Regex): Either[Problem, Pattern] =
    val flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE
    if regex.value then
      try Right(Pattern.compile(fold(q.value), flags))
      catch
        case e: PatternSyntaxException =>
          val at = if e.getIndex >= 0 then s" at position ${e.getIndex}" else ""
          Left(
            Problem.Unclassified(
              Legacy.Text(
                s"not a valid regex: ${e.getDescription}$at in `${e.getPattern}` (leave regex off to match the text literally)"
              )
            )
          )
    else
      val words = fold(q.value).split("\\s+").toList.filter(_.nonEmpty)
      Right(Pattern.compile(words.map(Pattern.quote).mkString("\\s+"), flags))

  /** One character as the matcher sees it: [[LatinFold]] plus the OCR typography. */
  private[domain] def foldChar(c: Char): String =
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

  private[domain] def fold(s: String): String = Folded(s).text

  /** `source` folded, with the index of the source character every folded character came from (`back(folded.length) ==
    * source.length`), so a match maps back to the original offsets.
    */
  final private[domain] class Folded(val text: String, back: Array[Int]):
    def start(i: Int): Int = back(i)

    /** The original end of a match ending (exclusive) at folded index `i`. */
    def end(i: Int, source: String): Int =
      if i <= 0 then 0
      else
        // the combining marks of the last letter belong to the match too
        source.indexWhere(c => !isMark(c), back(i - 1) + 1) match
          case -1 => source.length
          case k  => k

  private[domain] object Folded:
    def apply(source: String): Folded =
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

  private def isMark(c: Char): Boolean = c >= '̀' && c <= 'ͯ'

  final private class TimedOut extends RuntimeException(null, null, false, false)

  /** A view of the folded body that gives up once `until` (System.nanoTime) has passed: the regex engine reads every
    * character through `charAt`, so a pathological pattern is stopped mid-scan.
    */
  final private class Bounded(s: CharSequence, until: Timer.Deadline) extends CharSequence:
    // a counter, so the clock is read once per 64 Ki characters rather than on every one
    private var reads = 0
    def length: Int = s.length
    def charAt(i: Int): Char =
      reads += 1
      if (reads & 0xffff) == 0 && until.exceeded then throw new TimedOut
      s.charAt(i)
    def subSequence(a: Int, b: Int): CharSequence = new Bounded(s.subSequence(a, b), until)
    override def toString: String = s.toString

  /** Every non-empty match as `[start, end)` in the ORIGINAL body, in order, up to `max`. */
  private def matches(
      p: Pattern,
      f: Folded,
      source: String,
      max: Int,
      until: Timer.Deadline
  ): (Vector[(Int, Int)], Boolean) =
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
  final private case class Range(start: Int, end: Int, firstMatch: Int, spans: List[(Int, Int)])

  /** Matches → passages. A match that starts inside the previous passage joins it (the passage then ends no earlier
    * than the match does); any other starts a passage of its own, which begins no earlier than where the previous one
    * ended — so no text is shown twice, and dense matches do not snowball into one enormous passage.
    */
  private def merge(spans: Vector[(Int, Int)], text: String, context: Int): Vector[Range] =
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
  private[domain] def startOf(text: String, s: Int, half: Int): Int =
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
  private def skipBlanks(text: String, from: Int, upTo: Int): Int =
    if from < upTo && text.charAt(from).isWhitespace then skipBlanks(text, from + 1, upTo) else from

  /** Where a passage around a match ending at `e` ends: the last line end within `half` characters after it, else the
    * last word end, else exactly `half` after; trailing blanks dropped.
    */
  private[domain] def endOf(text: String, e: Int, half: Int): Int =
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
  private def dropBlanks(text: String, to: Int, downTo: Int): Int =
    if to > downTo && text.charAt(to - 1).isWhitespace then dropBlanks(text, to - 1, downTo) else to

  /** The heading a body offset sits under: its chunk's breadcrumb, or — for a body stored without chunks, whose heading
    * lines are still in the text — the nearest Markdown heading above it.
    */
  final private class Headings(segments: Seq[Chunk], text: String):
    private val starts: Array[Int] =
      segments.iterator.scanLeft(0)((at, c) => at + c.body.value.length + Separator.length).take(segments.size).toArray

    private lazy val inline: Vector[(Int, String)] =
      val Heading = """(?m)^#{1,6}[ \t]+(.*?)[ \t]*#*[ \t]*$""".r
      Heading.findAllMatchIn(text).map(m => (m.start, m.group(1))).filter(_._2.nonEmpty).toVector

    def at(offset: Int): Option[String] =
      val i = java.util.Arrays.binarySearch(starts, offset) match
        case k if k >= 0 => k
        case k           => -k - 2
      val own = if i >= 0 && i < segments.size then segments(i).heading.map(_.value) else None
      own.orElse(inline.takeWhile(_._1 <= offset).lastOption.map(_._2))
