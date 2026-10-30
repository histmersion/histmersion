package com.histmersion.api

/** The wikilink graph, as pure functions over an article body.
  *
  * A wikilink is `[[target]]`, `[[target|label]]` or `[[target#anchor]]`. The existing wiki also contains
  * BACKSLASH-ESCAPED links (`\[\[haak2015_massive\]\]`, written so a Markdown viewer shows the brackets literally) and
  * IMAGE EMBEDS (`![[map.png]]`); the first are real links and are matched, the second are not links at all and are
  * skipped, as are pure in-page anchors (`[[#Sources|…]]`).
  */
object ArticleLinks:

  val TargetArticle = "article"
  val TargetSource = "source"

  /** The citekey shape the whole project uses: `lastname2015_word`, or `lastname2015` alone (11 live keys have no
    * underscore — a regex that demanded one turned `[[knapp2016]]` into a dead article link). A `[[x]]` is a SOURCE
    * link only when x has this shape AND a source with that citekey exists — otherwise it is an article slug, even if
    * the article does not exist yet (a wiki links forward).
    */
  private val Citekey =
    """^(?=.*(?:_|\d{4}))[a-z][a-z0-9_]*$""".r // `haak2015_massive`, `knapp2016`, `duhoux_linearb1` — not `nodigits`

  /** A slug-shaped target. Anything else (`[[Not A Key]]`, a URL, a filename) is not a link we own, so it is left alone
    * in the text and never reaches `article_links`.
    */
  private val SlugShape = """^[a-z0-9][a-z0-9_.-]*$""".r

  /** `(!)? \[\[ target \]\]`, tolerating a backslash before each bracket. Non-greedy so two links on one line do not
    * merge into one match.
    */
  private val Link = """(!?)\\?\[\\?\[([^\[\]\n]+?)\\?\]\\?\]""".r

  def isCitekeyShaped(s: String): Boolean = Citekey.matches(s)

  /** One `[[…]]` as the renderer finds it: `[start, end)` in the text, the raw inside, whether it is an image embed
    * (`![[x]]`) or written with escaped brackets (`\[\[x\]\]`), and its normalised target — `None` when the renderer
    * leaves it literal (a title with spaces, an anchor-only link).
    */
  final case class Occurrence(
      start: Int,
      end: Int,
      raw: String,
      embed: Boolean,
      escaped: Boolean,
      target: Option[String]
  )

  /** Every wikilink in `md`, in order — the same matches [[rewriteLinks]] replaces, so the formatting lint sees exactly
    * the links the page will draw.
    */
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

  /** The raw target of one wikilink body: strip the `|label`, strip the `#anchor`, trim, lower-case. `None` when what
    * is left is not a slug we can resolve (an anchor-only link, a title with spaces, an image file).
    */
  def normalize(raw: String): Option[String] =
    val noLabel = raw.takeWhile(_ != '|')
    val noAnchor = noLabel.takeWhile(_ != '#')
    val t = noAnchor.trim.toLowerCase
    if t.isEmpty || !SlugShape.matches(t) then None else Some(t)

  /** Every distinct link target in the body, in first-appearance order. Image embeds are skipped. */
  def targets(body: String): List[String] =
    Link
      .findAllMatchIn(body)
      .filter(_.group(1).isEmpty) // `![[x]]` is an embed, not a link
      .flatMap(m => normalize(m.group(2)))
      .toList
      .distinct

  /** Classify the extracted targets. `isSource` answers "is there a source with this citekey?" — the repo supplies it
    * from one query, so this stays pure and unit-testable.
    */
  def classify(targets: List[String], isSource: String => Boolean): List[ArticleLink] =
    targets.map { t =>
      if isCitekeyShaped(t) && isSource(t) then ArticleLink(TargetSource, t)
      else ArticleLink(TargetArticle, t)
    }

  /** Extract + classify in one step. */
  def links(body: String, isSource: String => Boolean): List[ArticleLink] =
    classify(targets(body), isSource)

  /** Where a `[[target]]` points, given what the corpus actually holds — the whole resolution policy, as one pure
    * function.
    *
    *   - `citekeyOf` resolves an ALIAS to a full citekey. The wiki writes `[[allentoft2015]]` and `[[knapp2016]]` where
    *     the corpus stores `allentoft2015_population` / `knapp2016_seven`, exactly the short form
    *     `POST /api/edges/resolve` unifies for edges. It is asked FIRST: a citekey-shaped target is a source reference,
    *     and defaulting it to an article slug (which is what used to happen) turns every citation in every compiled
    *     article into a dead link.
    *   - Otherwise a compiled article with that slug.
    *   - Otherwise `None` — NOT a link. A wiki links forward, so an article slug that does not exist yet is a
    *     legitimate thing to write, but rendering it as a blue link to a 404 is worse than rendering it as text.
    */
  def hrefFor(target: String, citekeyOf: String => Option[String], isArticle: String => Boolean): Option[String] =
    citekeyOf(target)
      .map(ck => s"/source/$ck")
      .orElse(Option.when(isArticle(target))(s"/article/$target"))

  /** Rewrite every wikilink as a Markdown link before the body is handed to flexmark. `href` decides where one target
    * points (see [[hrefFor]]); `None` renders the label as plain text carrying `title="unresolved link"`, so a reader
    * can tell "nothing answers to this name" from "I have not clicked it yet". The label survives (`[[x|the X
    * paper]]`), the `#anchor` is dropped (a rendered article has its own heading ids), escaped links are un-escaped,
    * and image embeds and non-slug brackets are left untouched.
    *
    * `displayLabel` (optional) provides a human-readable label for a target (e.g. "Title, Author, Year" for a source
    * citekey). Used only when the wikilink has no explicit `|label`.
    */
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

  /** The one wikilink walker: `render(target, explicitLabel, writtenText)` replaces every link we own — `target`
    * normalised, `explicitLabel` the `|label` if one was written (possibly empty: `[[x|]]`), `writtenText` the link as
    * written minus its `#anchor`. Image embeds and non-slug brackets are left literal. [[linkMarkdown]] (Markdown
    * links) and the server's HTML pages (typed anchors) both build on it.
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
    ) // `![[x]]` is an embed: leave it literal

  /** Point every wikilink whose normalised target `to` maps somewhere else at that new target, and change NOTHING else:
    * the `|label`, the `#anchor`, escaped brackets and the text around the link all survive byte for byte, and image
    * embeds are never touched. This is what a citekey rename runs over every article body and source note. Returns the
    * new text and how many links moved.
    */
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

  /** The wave-1 behaviour, kept for callers that only know which targets are sources: `[[citekey]]` →
    * `/source/citekey`, everything else → `/article/slug`, nothing unresolvable.
    */
  def resolveMarkdown(md: String, isSource: String => Boolean): String =
    linkMarkdown(md, t => Some(if isCitekeyShaped(t) && isSource(t) then s"/source/$t" else s"/article/$t"))
