package com.histmersion
package domain

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Highlighting of search terms inside already-rendered HTML — pure, no parser, no dependencies.
  *
  * Used by `GET /source/{ck}?q=…` so a reader lands on the passage that matched. The rule that makes this safe without
  * a DOM: only TEXT is rewritten. Anything between `<` and `>` (tag names, attributes such as `href="/source/egypt"`),
  * the contents of `<script>`/`<style>`, and HTML entities (`&amp;`) are copied verbatim, so markup can never be
  * corrupted by a term that happens to occur in it. Each match becomes `<mark id="hl<n>">…</mark>`; the ids are what
  * the web bundle's `InPage.nextMatch` steps through.
  */
object Highlight:
  type Html = Html.T
  object Html extends RefinedType[String, Pure]
  type MarkCount = MarkCount.T
  object MarkCount extends RefinedType[Int, Pure]
  type Term = Term.T
  object Term extends RefinedType[String, Pure]

  /** Terms worth highlighting, from a `websearch_to_tsquery` style query: `"quoted phrases"` stay phrases, `-excluded`
    * terms and the `or`/`and` operators are dropped. Longest first, so a phrase wins over its own words.
    */
  type ApplyHtml = Html
  val ApplyHtml: Html.type = Html
  type ApplyQ = Term
  val ApplyQ: Term.type = Term
  type MarksHtml = Html
  val MarksHtml: Html.type = Html
  type MarksTerms = Term
  val MarksTerms: Term.type = Term

  def terms(q: Term): List[Term] =
    val phrase = """(-?)"([^"]*)"""".r
    val src = Option(q).fold("")(_.value)
    val phrases = phrase.findAllMatchIn(src).filter(_.group(1).isEmpty).map(_.group(2)).toList
    val bare = phrase.replaceAllIn(src, " ").split("""\s+""").toList
    val words = bare.filterNot(_.startsWith("-")).map(_.replaceAll("""^\W+|\W+$""", ""))
    (phrases ++ words)
      .map(_.trim)
      .filter(t => t.nonEmpty && !Operators(t.toLowerCase))
      .distinctBy(_.toLowerCase)
      .sortBy(-_.length)
      .map(Term(_))

  private val Operators = Set("or", "and", "not")

  /** Convenience: parse `q` and highlight in one step. Returns `(html, matchCount)`. */
  def apply(html: ApplyHtml, q: ApplyQ): (Html, MarkCount) = marks(html, terms(q))

  /** Wrap each occurrence of `terms` in the text of `html` in `<mark id="hl<n>">`. */
  def marks(html: MarksHtml, terms: List[MarksTerms]): (Html, MarkCount) =
    val src = html.value
    if src.isEmpty || terms.isEmpty then (html, MarkCount(0))
    else
      val re = pattern(terms)
      // java.lang.StringBuilder: scala's has no append(CharSequence, from, to), and would
      // silently auto-tuple the three arguments into append(Any).
      val sb = new java.lang.StringBuilder(src.length + 32)
      var i = 0
      var n = 0
      while i < src.length do
        src.charAt(i) match
          case '<' =>
            val skipTo = verbatimEnd(src, i)
            sb.append(src, i, skipTo)
            i = skipTo
          case _ =>
            val end = src.indexOf('<', i) match
              case -1 => src.length
              case k  => k
            n += markText(src.substring(i, end), re, sb, n)
            i = end
      (Html(sb.toString), MarkCount(n))

  /** Case-insensitive match of any term at a word start, extended over the rest of the word so a stemmed hit ("egypt" →
    * "Egyptian") is highlighted whole. Phrases allow any run of whitespace.
    */
  private def pattern(terms: List[Term]): scala.util.matching.Regex =
    val alts = terms
      .map(t => java.util.regex.Pattern.quote(t.value))
      .map(_.replaceAll("""\s+""", """\\E\\s+\\Q"""))
      .mkString("|")
    s"""(?iu)\\b(?:$alts)\\w*""".r

  /** Append `text` to `sb` with matches wrapped; entities are left alone. `soFar` is the number of marks already
    * emitted (ids continue from there). Returns how many marks this call added.
    */
  private def markText(text: String, re: scala.util.matching.Regex, sb: java.lang.StringBuilder, soFar: Int): Int =
    var n = 0
    Entity.split(text).foreach {
      case Left(entity) => sb.append(entity)
      case Right(plain) =>
        var last = 0
        re.findAllMatchIn(plain).foreach { m =>
          if m.start >= last then
            sb.append(plain, last, m.start)
            n += 1
            sb.append("<mark id=\"hl")
              .append(soFar + n)
              .append("\">")
              .append(plain, m.start, m.end)
              .append("</mark>")
            last = m.end
        }
        sb.append(plain, last, plain.length)
    }
    n

  /** End index (exclusive) of the verbatim run starting at `<` — a whole tag, or a whole `<script>`/`<style>` element
    * including its content.
    */
  private def verbatimEnd(html: String, at: Int): Int =
    val lower = html.regionMatches(true, at, "<script", 0, 7) -> html.regionMatches(true, at, "<style", 0, 6)
    val close = lower match
      case (true, _) => Some("</script>")
      case (_, true) => Some("</style>")
      case _         => None
    close match
      case Some(tag) =>
        html.toLowerCase.indexOf(tag, at) match
          case -1 => html.length
          case k  => k + tag.length
      case None =>
        html.indexOf('>', at) match
          case -1 => html.length
          case k  => k + 1

  private object Entity:

    private val Re = """&(?:#\d+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]{1,31});""".r

    /** Split text into `Left(entity)` (copy verbatim) and `Right(plain)` (highlightable) pieces. */
    def split(text: String): List[Either[String, String]] =
      val out = List.newBuilder[Either[String, String]]
      var last = 0
      Re.findAllMatchIn(text).foreach { m =>
        if m.start > last then out += Right(text.substring(last, m.start))
        out += Left(m.matched)
        last = m.end
      }
      if last < text.length then out += Right(text.substring(last))
      out.result()
