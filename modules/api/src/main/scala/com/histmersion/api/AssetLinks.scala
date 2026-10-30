package com.histmersion.api

object AssetLinks:

  private val ImageLink = """!\[([^\]]*)\]\(\s*<?([^)>\s]+)>?(\s+"[^"]*")?\s*\)""".r

  val Prefix = "assets/"

  private def mapImages(md: String)(f: (String, String, String) => String): String =
    ImageLink.replaceAllIn(
      md,
      m => java.util.regex.Matcher.quoteReplacement(f(m.group(1), m.group(2), Option(m.group(3)).getOrElse("")))
    )

  private def retarget(md: String, from: String, to: String): String =
    mapImages(md)((alt, target, title) =>
      if !target.startsWith(from) then s"![$alt]($target$title)"
      else s"![$alt]($to${target.drop(from.length)}$title)"
    )

  private val FiguresTarget = """^(?:[a-z]+:)?(?:[^?#]*[/\\])?figures[/\\]([^/\\?#]+)$""".r

  /** OCR bodies carry the path the converter saw on its own disk, often absolute
    * (`/Users/…/md/<citekey>/figures/x.png`); a browser resolves that against the server root and draws nothing, so
    * every spelling is accepted.
    */
  def fromFigures(md: String): String =
    mapImages(md)((alt, target, title) =>
      target match
        case FiguresTarget(name) => s"![$alt]($Prefix${safeName(java.net.URLDecoder.decode(name, "UTF-8"))}$title)"
        case _                   => s"![$alt]($target$title)"
    )

  /** An OCR body writes `![](…)`; a figure with no alt text is invisible to a screen reader. */
  def numberFigures(md: String): String =
    var n = 0
    mapImages(md)((alt, target, title) =>
      n += 1
      val label = if alt.trim.nonEmpty then alt else s"Figure $n"
      s"![$label]($target$title)"
    )

  /** The OCR sidecar brackets text it read inside a picture with these markers. */
  private val PictureText =
    """(?s)\*\*-+\s*Start of picture text\s*-+\*\*(?:<br\s*/?>)?\s*(.*?)\s*\*\*-+\s*End of picture text\s*-+\*\*(?:<br\s*/?>)?""".r

  /** One HTML block per figure, with no blank line inside: a blank line ends a Markdown HTML block, and flexmark would
    * render the rest as prose.
    */
  def collapsePictureText(md: String): String =
    PictureText.replaceAllIn(
      md,
      m =>
        val inner = m.group(1).replaceAll("(?s)\\R\\s*\\R", "<br>").replaceAll("\\R", " ").trim
        java.util.regex.Matcher
          .quoteReplacement(s"""<details class="figtext"><summary>figure text</summary><div>$inner</div></details>""")
    )

  private val BoilerplateHeading =
    """(?i)^(original\s+|research\s+|review\s+|short\s+)?(article|paper|research|communication|report)$""".r

  private def flatten(s: String): String =
    s.toLowerCase.filter(c => c.isLetterOrDigit || c == ' ').replaceAll("\\s+", " ").trim

  def dropBoilerplateHeadings(md: String, title: String): String =
    val want = flatten(title)
    val kept = md.linesIterator.filterNot { line =>
      val l = line.trim
      if !l.startsWith("#") then false
      else
        val text = flatten(l.dropWhile(_ == '#'))
        text.nonEmpty && (BoilerplateHeading.matches(text) || (want.nonEmpty && text == want))
    }
    kept.mkString("\n")

  def normalizeBody(md: String, title: String = ""): String =
    dropBoilerplateHeadings(collapsePictureText(fromFigures(md)), title)

  def toWeb(md: String, citekey: String): String = retarget(md, Prefix, webBase(citekey))

  def toAbsolute(md: String, citekey: String, publicUrl: String): String =
    retarget(md, Prefix, publicUrl.trim.stripSuffix("/") + webBase(citekey))

  def webBase(citekey: String): String = s"/source/${enc(citekey)}/assets/"

  def url(citekey: String, name: String): String = webBase(citekey) + enc(name)

  private def enc(s: String): String =
    java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

  private val BareImg = """<img(?![^>]*\bloading=)\s""".r

  def lazyImages(html: String): String =
    BareImg.replaceAllIn(html, _ => java.util.regex.Matcher.quoteReplacement("""<img loading="lazy" """))

  /** Must match the converter's `_safe_name`: either side may name a file. */
  def safeName(raw: String): String =
    val base = raw.replace('\\', '/').split('/').lastOption.getOrElse("")
    val cleaned = base
      .map(c => if c.isLetterOrDigit || c == '.' || c == '_' || c == '-' then c else '-')
      .mkString
      .replaceAll("^-+|-+$", "")
    if cleaned.isEmpty then "image.png" else cleaned

  def mimeOf(name: String): String =
    name.toLowerCase.reverse.takeWhile(_ != '.').reverse match
      case "png"          => "image/png"
      case "jpg" | "jpeg" => "image/jpeg"
      case "gif"          => "image/gif"
      case "webp"         => "image/webp"
      case "svg"          => "image/svg+xml"
      case _              => "application/octet-stream"
