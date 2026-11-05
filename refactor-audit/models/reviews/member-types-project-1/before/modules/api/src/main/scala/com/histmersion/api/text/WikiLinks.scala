package com.histmersion.api.text
object WikiLinks:

  private val Citekey =
    """^(?=.*(?:_|\d{4}))[a-z][a-z0-9_]*$""".r

  private val SlugShape = """^[a-z0-9][a-z0-9_.-]*$""".r

  private val Link = """(!?)\\?\[\\?\[([^\[\]\n]+?)\\?\]\\?\]""".r

  /** Whether a target reads as a citekey (a four-digit year or an underscore in it) rather than an article's slug. */
  def isCitekeyShaped(s: String): Boolean = Citekey.matches(s)

  /** One link in a text: where it is, what was written, whether it is an embed or escaped, and its target. */
  final case class Occurrence(
      start: Occurrence.Start,
      end: Occurrence.End,
      raw: Occurrence.Raw,
      embed: Occurrence.Embed,
      escaped: Occurrence.Escaped,
      target: Option[Occurrence.Target]
  )

  object Occurrence:
    type Start = Start.T
    object Start extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type End = End.T
    object End extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Raw = Raw.T
    object Raw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Embed = Embed.T
    object Embed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Escaped = Escaped.T
    object Escaped extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def occurrences(md: String): List[Occurrence] =
    Link
      .findAllMatchIn(md)
      .map { m =>
        val raw = m.group(2)
        Occurrence(
          m.start,
          m.end,
          raw,
          m.group(1).nonEmpty,
          m.matched.contains('\\'),
          normalize(raw.takeWhile(_ != '|'))
        )
      }
      .toList

  /** A link's target as it is stored: no label, no anchor, lower case; none for an in-page anchor or a file name. */
  def normalize(raw: String): Option[String] =
    val noLabel = raw.takeWhile(_ != '|')
    val noAnchor = noLabel.takeWhile(_ != '#')
    val t = noAnchor.trim.toLowerCase
    if t.isEmpty || !SlugShape.matches(t) then None else Some(t)

  /** The distinct targets a text links, in order; embeds are not links. */
  def targets(body: String): List[String] =
    Link
      .findAllMatchIn(body)
      .filter(_.group(1).isEmpty)
      .flatMap(m => normalize(m.group(2)))
      .toList
      .distinct

  /** Every link replaced by what `render` makes of its target, explicit label and written target; embeds and links to
    * no target are kept as written.
    */
  def rewriteLinks(md: String)(render: (String, Option[String], String) => String): String =
    Link.replaceAllIn(
      md,
      m =>
        val raw = m.group(2)
        val (targetPart, explicit) = raw.indexOf('|') match
          case -1 => (raw, None)
          case i  => (raw.take(i), Some(raw.drop(i + 1).trim))
        java.util.regex.Matcher.quoteReplacement(normalize(targetPart) match
          case Some(t) if m.group(1).isEmpty => render(t, explicit, targetPart.takeWhile(_ != '#').trim)
          case _                             => m.matched)
    )

  /** The links whose target `to` moves pointed at the new target, labels and anchors kept; with how many moved. */
  def retarget(md: String, to: String => Option[String]): (String, Int) =
    def moved(m: scala.util.matching.Regex.Match): Option[String] =
      if m.group(1).nonEmpty then None
      else normalize(m.group(2).takeWhile(_ != '|')).flatMap(to)
    val count = Link.findAllMatchIn(md).count(moved(_).isDefined)
    if count == 0 then (md, 0)
    else
      val out = Link.replaceAllIn(
        md,
        m =>
          java.util.regex.Matcher.quoteReplacement(moved(m) match
            case None       => m.matched
            case Some(next) =>
              val raw = m.group(2)
              val name = raw.takeWhile(c => c != '|' && c != '#')
              val lead = name.takeWhile(_.isWhitespace)
              val trail = name.reverse.takeWhile(_.isWhitespace).reverse
              val newRaw = lead + next + trail + raw.drop(name.length)
              val (s, e) = (m.start(2) - m.start, m.end(2) - m.start)
              m.matched.take(s) + newRaw + m.matched.drop(e))
      )
      (out, count)

  /** Whether a text links `ck` (any case). */
  def links(text: String, ck: String): Boolean = targets(text).exists(_.equalsIgnoreCase(ck))
