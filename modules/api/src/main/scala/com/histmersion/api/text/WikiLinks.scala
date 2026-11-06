package com.histmersion.api.text
object WikiLinks:
  type Alias = Alias.T
  object Alias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetargetCount = RetargetCount.T
  object RetargetCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type NormalizeRaw = NormalizeRaw.T
  object NormalizeRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RewriteLinksRenderArg3 = RewriteLinksRenderArg3.T
  object RewriteLinksRenderArg3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Citekey =
    """^(?=.*(?:_|\d{4}))[a-z][a-z0-9_]*$""".r

  private val SlugShape = """^[a-z0-9][a-z0-9_.-]*$""".r

  private val Link = """(!?)\\?\[\\?\[([^\[\]\n]+?)\\?\]\\?\]""".r

  /** Whether a target reads as a citekey (a four-digit year or an underscore in it) rather than an article's slug. */
  def isCitekeyShaped(s: String): Boolean = (Citekey.matches((s)))

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

  def occurrences(md: com.histmersion.api.text.WikiLinks.Body): List[Occurrence] =
    Link
      .findAllMatchIn((md).value)
      .map { m =>
        val raw = m.group(2)
        Occurrence(
          _root_.com.histmersion.api.text.WikiLinks.Occurrence.Start(m.start),
          _root_.com.histmersion.api.text.WikiLinks.Occurrence.End(m.end),
          _root_.com.histmersion.api.text.WikiLinks.Occurrence.Raw(raw),
          _root_.com.histmersion.api.text.WikiLinks.Occurrence.Embed(m.group(1).nonEmpty),
          _root_.com.histmersion.api.text.WikiLinks.Occurrence.Escaped(m.matched.contains('\\')),
          normalize(_root_.com.histmersion.api.text.WikiLinks.NormalizeRaw(raw.takeWhile(_ != '|')))
        )
      }
      .toList

  /** A link's target as it is stored: no label, no anchor, lower case; none for an in-page anchor or a file name. */
  def normalize(raw: com.histmersion.api.text.WikiLinks.NormalizeRaw): Option[com.histmersion.api.text.WikiLinks.Occurrence.Target] =
    val noLabel = (raw).value.takeWhile(_ != '|')
    val noAnchor = noLabel.takeWhile(_ != '#')
    val t = noAnchor.trim.toLowerCase
    if t.isEmpty || !SlugShape.matches(t) then None else Some(_root_.com.histmersion.api.text.WikiLinks.Occurrence.Target(t))

  /** The distinct targets a text links, in order; embeds are not links. */
  def targets(body: com.histmersion.api.text.WikiLinks.Body): List[com.histmersion.api.text.WikiLinks.Occurrence.Target] =
    (Link
      .findAllMatchIn((body).value)
      .filter(_.group(1).isEmpty)
      .flatMap(m => normalize(_root_.com.histmersion.api.text.WikiLinks.NormalizeRaw(m.group(2))))
      .toList
      .distinct).map(refactorValue7 => _root_.com.histmersion.api.text.WikiLinks.Occurrence.Target((refactorValue7).value))

  /** Every link replaced by what `render` makes of its target, explicit label and written target; embeds and links to
    * no target are kept as written.
    */
  def rewriteLinks(md: com.histmersion.api.text.WikiLinks.Body)(render: (com.histmersion.api.text.WikiLinks.Occurrence.Target, Option[com.histmersion.api.text.WikiLinks.Alias], com.histmersion.api.text.WikiLinks.RewriteLinksRenderArg3) => String): com.histmersion.api.text.WikiLinks.Body =
    _root_.com.histmersion.api.text.WikiLinks.Body(Link.replaceAllIn(
      (md).value,
      m =>
        val raw = m.group(2)
        val (targetPart, explicit) = raw.indexOf('|') match
          case -1 => (raw, None)
          case i  => (raw.take(i), Some(raw.drop(i + 1).trim))
        java.util.regex.Matcher.quoteReplacement(normalize(_root_.com.histmersion.api.text.WikiLinks.NormalizeRaw(targetPart)) match
          case Some(t) if m.group(1).isEmpty => (render(_root_.com.histmersion.api.text.WikiLinks.Occurrence.Target((t).value), (explicit).map(refactorValue => _root_.com.histmersion.api.text.WikiLinks.Alias(refactorValue)), _root_.com.histmersion.api.text.WikiLinks.RewriteLinksRenderArg3(targetPart.takeWhile(_ != '#').trim)))
          case _                             => m.matched)
    ))

  /** The links whose target `to` moves pointed at the new target, labels and anchors kept; with how many moved. */
  def retarget(md: com.histmersion.api.text.WikiLinks.Body, to: com.histmersion.api.text.WikiLinks.Occurrence.Target => Option[com.histmersion.api.text.WikiLinks.Occurrence.Target]): (com.histmersion.api.text.WikiLinks.Body, com.histmersion.api.text.WikiLinks.RetargetCount) =
    def moved(m: scala.util.matching.Regex.Match): Option[com.histmersion.api.text.WikiLinks.Occurrence.Target] =
      if m.group(1).nonEmpty then None
      else normalize(_root_.com.histmersion.api.text.WikiLinks.NormalizeRaw(m.group(2).takeWhile(_ != '|'))).flatMap(to)
    val count = Link.findAllMatchIn((md).value).count(moved(_).isDefined)
    if count == 0 then (_root_.com.histmersion.api.text.WikiLinks.Body((md).value), _root_.com.histmersion.api.text.WikiLinks.RetargetCount(0))
    else
      val out = Link.replaceAllIn(
        (md).value,
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
      (_root_.com.histmersion.api.text.WikiLinks.Body(out), _root_.com.histmersion.api.text.WikiLinks.RetargetCount(count))

  /** Whether a text links `ck` (any case). */
  def links(text: com.histmersion.api.text.WikiLinks.Body, ck: com.histmersion.api.library.Citekey): Boolean = targets(text).exists(_.equalsIgnoreCase(ck))
