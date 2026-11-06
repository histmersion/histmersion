package com.histmersion.api.text
object WikiLinks:

  type RetargetMovedResult = RetargetMovedResult.T
  object RetargetMovedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IsCitekeyShapedResult = IsCitekeyShapedResult.T
  object IsCitekeyShapedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsCitekeyShapedS = IsCitekeyShapedS.T
  object IsCitekeyShapedS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OccurrencesMd = OccurrencesMd.T
  object OccurrencesMd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormalizeResult = NormalizeResult.T
  object NormalizeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormalizeRaw = NormalizeRaw.T
  object NormalizeRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsResult = TargetsResult.T
  object TargetsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsBody = TargetsBody.T
  object TargetsBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RewriteLinksResult = RewriteLinksResult.T
  object RewriteLinksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RewriteLinksMd = RewriteLinksMd.T
  object RewriteLinksMd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RewriteLinksRenderArg1 = RewriteLinksRenderArg1.T
  object RewriteLinksRenderArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RewriteLinksRenderArg2 = RewriteLinksRenderArg2.T
  object RewriteLinksRenderArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RewriteLinksRenderArg3 = RewriteLinksRenderArg3.T
  object RewriteLinksRenderArg3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RewriteLinksRenderResult = RewriteLinksRenderResult.T
  object RewriteLinksRenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetargetResultPart1 = RetargetResultPart1.T
  object RetargetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetargetResultPart2 = RetargetResultPart2.T
  object RetargetResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetargetMd = RetargetMd.T
  object RetargetMd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetargetToArg1 = RetargetToArg1.T
  object RetargetToArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetargetToResult = RetargetToResult.T
  object RetargetToResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinksResult = LinksResult.T
  object LinksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinksText = LinksText.T
  object LinksText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinksCk = LinksCk.T
  object LinksCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Citekey =
    """^(?=.*(?:_|\d{4}))[a-z][a-z0-9_]*$""".r

  private val SlugShape = """^[a-z0-9][a-z0-9_.-]*$""".r

  private val Link = """(!?)\\?\[\\?\[([^\[\]\n]+?)\\?\]\\?\]""".r

  /** Whether a target reads as a citekey (a four-digit year or an underscore in it) rather than an article's slug. */
  def isCitekeyShaped(s: com.histmersion.api.text.WikiLinks.IsCitekeyShapedS): com.histmersion.api.text.WikiLinks.IsCitekeyShapedResult = _root_.com.histmersion.api.text.WikiLinks.IsCitekeyShapedResult(Citekey.matches((s).value))

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

  def occurrences(md: com.histmersion.api.text.WikiLinks.OccurrencesMd): List[Occurrence] =
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
  def normalize(raw: com.histmersion.api.text.WikiLinks.NormalizeRaw): Option[com.histmersion.api.text.WikiLinks.NormalizeResult] =
    val noLabel = (raw).value.takeWhile(_ != '|')
    val noAnchor = noLabel.takeWhile(_ != '#')
    val t = noAnchor.trim.toLowerCase
    if t.isEmpty || !SlugShape.matches(t) then None else Some(_root_.com.histmersion.api.text.WikiLinks.NormalizeResult(t))

  /** The distinct targets a text links, in order; embeds are not links. */
  def targets(body: com.histmersion.api.text.WikiLinks.TargetsBody): List[com.histmersion.api.text.WikiLinks.TargetsResult] =
    Link
      .findAllMatchIn(body)
      .filter(_.group(1).isEmpty)
      .flatMap(m => normalize(m.group(2)))
      .toList
      .distinct

  /** Every link replaced by what `render` makes of its target, explicit label and written target; embeds and links to
    * no target are kept as written.
    */
  def rewriteLinks(md: com.histmersion.api.text.WikiLinks.RewriteLinksMd)(render: (com.histmersion.api.text.WikiLinks.RewriteLinksRenderArg1, Option[com.histmersion.api.text.WikiLinks.RewriteLinksRenderArg2], com.histmersion.api.text.WikiLinks.RewriteLinksRenderArg3) => com.histmersion.api.text.WikiLinks.RewriteLinksRenderResult): com.histmersion.api.text.WikiLinks.RewriteLinksResult =
    Link.replaceAllIn(
      (md).value,
      m =>
        val raw = m.group(2)
        val (targetPart, explicit) = raw.indexOf('|') match
          case -1 => (raw, None)
          case i  => (raw.take(i), Some(raw.drop(i + 1).trim))
        java.util.regex.Matcher.quoteReplacement(normalize(_root_.com.histmersion.api.text.WikiLinks.NormalizeRaw(targetPart)) match
          case Some(t) if m.group(1).isEmpty => render(t, (explicit).map(refactorValue => _root_.com.histmersion.api.text.WikiLinks.RewriteLinksRenderArg2(refactorValue)), _root_.com.histmersion.api.text.WikiLinks.RewriteLinksRenderArg3(targetPart.takeWhile(_ != '#').trim))
          case _                             => m.matched)
    )

  /** The links whose target `to` moves pointed at the new target, labels and anchors kept; with how many moved. */
  def retarget(md: com.histmersion.api.text.WikiLinks.RetargetMd, to: com.histmersion.api.text.WikiLinks.RetargetToArg1 => Option[com.histmersion.api.text.WikiLinks.RetargetToResult]): (com.histmersion.api.text.WikiLinks.RetargetResultPart1, com.histmersion.api.text.WikiLinks.RetargetResultPart2) =
    def moved(m: scala.util.matching.Regex.Match): Option[com.histmersion.api.text.WikiLinks.RetargetMovedResult] =
      if m.group(1).nonEmpty then None
      else normalize(m.group(2).takeWhile(_ != '|')).flatMap(to)
    val count = Link.findAllMatchIn(md).count(moved(_).isDefined)
    if count == 0 then (md, _root_.com.histmersion.api.text.WikiLinks.RetargetResultPart2(0))
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
      (_root_.com.histmersion.api.text.WikiLinks.RetargetResultPart1(out), count)

  /** Whether a text links `ck` (any case). */
  def links(text: com.histmersion.api.text.WikiLinks.LinksText, ck: com.histmersion.api.text.WikiLinks.LinksCk): com.histmersion.api.text.WikiLinks.LinksResult = targets(text).exists(_.equalsIgnoreCase(ck))
