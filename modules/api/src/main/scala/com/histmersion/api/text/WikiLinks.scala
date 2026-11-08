package com.histmersion
package api.text

import io.github.iltotore.iron.constraint.any.Pure
object WikiLinks:
  type Alias = Alias.T
  object Alias extends Newtype[String, Pure]
  type Body = Body.T
  object Body extends Newtype[String, Pure]
  type RetargetCount = RetargetCount.T
  object RetargetCount extends Newtype[Int, Pure]

  type NormalizeRaw = NormalizeRaw.T
  object NormalizeRaw extends Newtype[String, Pure]
  type RewriteLinksRenderArg3 = Alias
  val RewriteLinksRenderArg3: Alias.type = Alias

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
    object Start extends Newtype[Int, Pure]
    type End = End.T
    object End extends Newtype[Int, Pure]
    type Raw = Raw.T
    object Raw extends Newtype[String, Pure]
    type Embed = Embed.T
    object Embed extends Newtype[Boolean, Pure]
    type Escaped = Escaped.T
    object Escaped extends Newtype[Boolean, Pure]
    type Target = Target.T
    object Target extends Newtype[String, Pure]

  def occurrences(md: Body): List[Occurrence] =
    Link
      .findAllMatchIn((md).value)
      .map { m =>
        val raw = m.group(2)
        Occurrence(
          Occurrence.Start(m.start),
          Occurrence.End(m.end),
          Occurrence.Raw(raw),
          Occurrence.Embed(m.group(1).nonEmpty),
          Occurrence.Escaped(m.matched.contains('\\')),
          normalize(NormalizeRaw(raw.takeWhile(_ != '|')))
        )
      }
      .toList

  /** A link's target as it is stored: no label, no anchor, lower case; none for an in-page anchor or a file name. */
  def normalize(raw: NormalizeRaw): Option[Occurrence.Target] =
    val noLabel = (raw).value.takeWhile(_ != '|')
    val noAnchor = noLabel.takeWhile(_ != '#')
    val t = noAnchor.trim.toLowerCase
    if t.isEmpty || !SlugShape.matches(t) then None else Some(Occurrence.Target(t))

  /** The distinct targets a text links, in order; embeds are not links. */
  def targets(body: Body): List[Occurrence.Target] =
    (Link
      .findAllMatchIn((body).value)
      .filter(_.group(1).isEmpty)
      .flatMap(m => normalize(NormalizeRaw(m.group(2))))
      .toList
      .distinct)
      .map(refactorValue7 => Occurrence.Target((refactorValue7).value))

  /** Every link replaced by what `render` makes of its target, explicit label and written target; embeds and links to
    * no target are kept as written.
    */
  def rewriteLinks(md: Body)(render: (Occurrence.Target, Option[Alias], Alias) => String): Body =
    Body(
      Link.replaceAllIn(
        (md).value,
        m =>
          val raw = m.group(2)
          val (targetPart, explicit) = raw.indexOf('|') match
            case -1 => (raw, None)
            case i  => (raw.take(i), Some(raw.drop(i + 1).trim))
          java.util.regex.Matcher.quoteReplacement(normalize(NormalizeRaw(targetPart)) match
            case Some(t) if m.group(1).isEmpty => (
              render(
                Occurrence.Target((t).value),
                (explicit).map(refactorValue => Alias(refactorValue)),
                Alias(targetPart.takeWhile(_ != '#').trim)
              )
            )
            case _ => m.matched)
      )
    )

  /** The links whose target `to` moves pointed at the new target, labels and anchors kept; with how many moved. */
  def retarget(md: Body, to: Occurrence.Target => Option[Occurrence.Target]): (Body, RetargetCount) =
    def moved(m: scala.util.matching.Regex.Match): Option[Occurrence.Target] =
      if m.group(1).nonEmpty then None
      else normalize(NormalizeRaw(m.group(2).takeWhile(_ != '|'))).flatMap(to)
    val count = Link.findAllMatchIn((md).value).count(moved(_).isDefined)
    if count == 0 then (Body((md).value), RetargetCount(0))
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
      (Body(out), RetargetCount(count))

  /** Whether a text links `ck` (any case). */
  def links(text: Body, ck: com.histmersion.api.library.Citekey): Boolean =
    targets(text).exists(_.value.equalsIgnoreCase(ck.value))
