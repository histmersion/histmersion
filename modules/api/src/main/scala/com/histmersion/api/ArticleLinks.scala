package com.histmersion.api

/** Besides `[[target]]`, `[[target|label]]` and `[[target#anchor]]`, wiki text holds backslash-escaped links
  * (`\[\[x\]\]`, written so a Markdown viewer shows the brackets), which are real links, and Obsidian image embeds
  * (`![[map.png]]`) and in-page anchors (`[[#Sources|…]]`), which are not.
  */
object ArticleLinks:

  val TargetArticle = "article"
  val TargetSource = "source"

  private val Citekey =
    """^(?=.*(?:_|\d{4}))[a-z][a-z0-9_]*$""".r

  private val SlugShape = """^[a-z0-9][a-z0-9_.-]*$""".r

  private val Link = """(!?)\\?\[\\?\[([^\[\]\n]+?)\\?\]\\?\]""".r

  def isCitekeyShaped(s: String): Boolean = Citekey.matches(s)

  final case class Occurrence(
      start: Int,
      end: Int,
      raw: String,
      embed: Boolean,
      escaped: Boolean,
      target: Option[String]
  )

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

  def normalize(raw: String): Option[String] =
    val noLabel = raw.takeWhile(_ != '|')
    val noAnchor = noLabel.takeWhile(_ != '#')
    val t = noAnchor.trim.toLowerCase
    if t.isEmpty || !SlugShape.matches(t) then None else Some(t)

  def targets(body: String): List[String] =
    Link
      .findAllMatchIn(body)
      .filter(_.group(1).isEmpty)
      .flatMap(m => normalize(m.group(2)))
      .toList
      .distinct

  def classify(targets: List[String], isSource: String => Boolean): List[ArticleLink] =
    targets.map { t =>
      if isCitekeyShaped(t) && isSource(t) then ArticleLink(TargetSource, t)
      else ArticleLink(TargetArticle, t)
    }

  def links(body: String, isSource: String => Boolean): List[ArticleLink] =
    classify(targets(body), isSource)

  def hrefFor(target: String, citekeyOf: String => Option[String], isArticle: String => Boolean): Option[String] =
    citekeyOf(target)
      .map(ck => s"/source/$ck")
      .orElse(Option.when(isArticle(target))(s"/article/$target"))

  def linkMarkdown(
      md: String,
      href: String => Option[String],
      displayLabel: String => Option[String] = _ => None
  ): String =
    rewriteLinks(md) { (t, explicit, written) =>
      val label = explicit.getOrElse(written)
      val text = if label.isEmpty then displayLabel(t).getOrElse(Label.display(t)) else label
      href(t) match
        case Some(h) => s"[$text]($h)"
        case None    => s"""<span class="dangling" title="unresolved link">$text</span>"""
    }

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

  def resolveMarkdown(md: String, isSource: String => Boolean): String =
    linkMarkdown(md, t => Some(if isCitekeyShaped(t) && isSource(t) then s"/source/$t" else s"/article/$t"))
