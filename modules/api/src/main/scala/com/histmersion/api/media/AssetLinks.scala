package com.histmersion.api.media
import com.histmersion.api.given
object AssetLinks:
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Html = Html.T
  object Html extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Target = Target.T
  object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetId = TargetId.T
  object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetKind = TargetKind.T
  object TargetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type NormalizeBodyMd = _root_.com.histmersion.api.media.AssetLinks.Body
  val NormalizeBodyMd: _root_.com.histmersion.api.media.AssetLinks.Body.type = _root_.com.histmersion.api.media.AssetLinks.Body
  type NormalizeBodyTitle = _root_.com.histmersion.api.media.AssetLinks.Title
  val NormalizeBodyTitle: _root_.com.histmersion.api.media.AssetLinks.Title.type = _root_.com.histmersion.api.media.AssetLinks.Title
  type ToWebMd = _root_.com.histmersion.api.media.AssetLinks.Body
  val ToWebMd: _root_.com.histmersion.api.media.AssetLinks.Body.type = _root_.com.histmersion.api.media.AssetLinks.Body
  type ToWebCitekey = _root_.com.histmersion.api.library.Citekey
  val ToWebCitekey: _root_.com.histmersion.api.library.Citekey.type = _root_.com.histmersion.api.library.Citekey
  type ToAbsoluteMd = _root_.com.histmersion.api.media.AssetLinks.Body
  val ToAbsoluteMd: _root_.com.histmersion.api.media.AssetLinks.Body.type = _root_.com.histmersion.api.media.AssetLinks.Body
  type ToAbsoluteCitekey = _root_.com.histmersion.api.library.Citekey
  val ToAbsoluteCitekey: _root_.com.histmersion.api.library.Citekey.type = _root_.com.histmersion.api.library.Citekey
  type UrlCitekey = _root_.com.histmersion.api.library.Citekey
  val UrlCitekey: _root_.com.histmersion.api.library.Citekey.type = _root_.com.histmersion.api.library.Citekey
  type UrlName = _root_.com.histmersion.api.media.AssetInfo.Name
  val UrlName: _root_.com.histmersion.api.media.AssetInfo.Name.type = _root_.com.histmersion.api.media.AssetInfo.Name

  private val ImageLink = """!\[([^\]]*)\]\(\s*<?([^)>\s]+)>?(\s+"[^"]*")?\s*\)""".r

  val Prefix = "assets/"

  private def mapImages(md: com.histmersion.api.media.AssetLinks.Body)(f: (String, com.histmersion.api.media.AssetLinks.Target, String) => String): com.histmersion.api.media.AssetLinks.Body =
    _root_.com.histmersion.api.media.AssetLinks.Body(ImageLink.replaceAllIn(
      (md).value,
      m => java.util.regex.Matcher.quoteReplacement((f((m.group(1)), _root_.com.histmersion.api.media.AssetLinks.Target(m.group(2)), (Option(m.group(3)).getOrElse("")))).value)
    ))

  private def retarget(md: com.histmersion.api.media.AssetLinks.Body, from: com.histmersion.api.media.AssetLinks.Url, to: com.histmersion.api.media.AssetLinks.Url): com.histmersion.api.media.AssetLinks.Body =
    _root_.com.histmersion.api.media.AssetLinks.Body((mapImages(_root_.com.histmersion.api.media.AssetLinks.Body((md).value))((alt, target, title) =>
      if !(target).value.startsWith((from).value) then (s"![$alt]($target$title)")
      else (s"![$alt]($to${(target).value.drop((from).value.length)}$title)")
    )).value)

  private val FiguresTarget = """^(?:[a-z]+:)?(?:[^?#]*[/\\])?figures[/\\]([^/\\?#]+)$""".r

  /** OCR bodies carry the path the converter saw on its own disk, often absolute
    * (`/Users/…/md/<citekey>/figures/x.png`); a browser resolves that against the server root and draws nothing, so
    * every spelling is accepted.
    */
  def fromFigures(md: com.histmersion.api.media.AssetLinks.Body): com.histmersion.api.media.AssetLinks.Body =
    mapImages(_root_.com.histmersion.api.media.AssetLinks.Body((md).value))((alt, target, title) =>
      target match
        case FiguresTarget(name) => s"![$alt]($Prefix${safeName(java.net.URLDecoder.decode(name, "UTF-8"))}$title)"
        case _                   => (s"![$alt]($target$title)")
    )

  /** An OCR body writes `![](…)`; a figure with no alt text is invisible to a screen reader. */
  def numberFigures(md: com.histmersion.api.media.AssetLinks.Body): com.histmersion.api.media.AssetLinks.Body =
    var n = 0
    _root_.com.histmersion.api.media.AssetLinks.Body((mapImages(_root_.com.histmersion.api.media.AssetLinks.Body((md).value))((alt, target, title) =>
      n += 1
      val label = if (alt).value.trim.nonEmpty then alt else s"Figure $n"
      (s"![$label]($target$title)")
    )).value)

  /** The OCR sidecar brackets text it read inside a picture with these markers. */
  private val PictureText =
    """(?s)\*\*-+\s*Start of picture text\s*-+\*\*(?:<br\s*/?>)?\s*(.*?)\s*\*\*-+\s*End of picture text\s*-+\*\*(?:<br\s*/?>)?""".r

  /** One HTML block per figure, with no blank line inside: a blank line ends a Markdown HTML block, and flexmark would
    * render the rest as prose.
    */
  def collapsePictureText(md: com.histmersion.api.media.AssetLinks.Body): com.histmersion.api.media.AssetLinks.Body =
    _root_.com.histmersion.api.media.AssetLinks.Body(PictureText.replaceAllIn(
      (md).value,
      m =>
        val inner = m.group(1).replaceAll("(?s)\\R\\s*\\R", "<br>").replaceAll("\\R", " ").trim
        java.util.regex.Matcher
          .quoteReplacement(s"""<details class="figtext"><summary>figure text</summary><div>$inner</div></details>""")
    ))

  private val BoilerplateHeading =
    """(?i)^(original\s+|research\s+|review\s+|short\s+)?(article|paper|research|communication|report)$""".r

  private def flatten(s: String): String =
    ((s).value.toLowerCase.filter(c => c.isLetterOrDigit || c == ' ').replaceAll("\\s+", " ").trim)

  def dropBoilerplateHeadings(md: com.histmersion.api.media.AssetLinks.Body, title: com.histmersion.api.media.AssetLinks.Title): com.histmersion.api.media.AssetLinks.Body =
    val want = flatten(((title).value))
    val kept = (md).value.linesIterator.filterNot { line =>
      val l = line.trim
      if !l.startsWith("#") then false
      else
        val text = flatten((l.dropWhile(_ == '#')))
        (text).value.nonEmpty && (BoilerplateHeading.matches((text).value) || ((want).value.nonEmpty && text == want))
    }
    _root_.com.histmersion.api.media.AssetLinks.Body(kept.mkString("\n"))

  def normalizeBody(md: com.histmersion.api.media.AssetLinks.NormalizeBodyMd, title: com.histmersion.api.media.AssetLinks.NormalizeBodyTitle = _root_.com.histmersion.api.media.AssetLinks.NormalizeBodyTitle("")): com.histmersion.api.media.AssetLinks.Body =
    _root_.com.histmersion.api.media.AssetLinks.Body((dropBoilerplateHeadings(_root_.com.histmersion.api.media.AssetLinks.Body((collapsePictureText(_root_.com.histmersion.api.media.AssetLinks.Body((fromFigures(md)).value))).value), title)).value)

  def toWeb(md: com.histmersion.api.media.AssetLinks.ToWebMd, citekey: com.histmersion.api.media.AssetLinks.ToWebCitekey): com.histmersion.api.media.AssetLinks.Body = _root_.com.histmersion.api.media.AssetLinks.Body((retarget(md, _root_.com.histmersion.api.media.AssetLinks.Url(Prefix), _root_.com.histmersion.api.media.AssetLinks.Url((webBase(citekey)).value))).value)

  def toAbsolute(md: com.histmersion.api.media.AssetLinks.ToAbsoluteMd, citekey: com.histmersion.api.media.AssetLinks.ToAbsoluteCitekey, publicUrl: com.histmersion.api.media.AssetLinks.Url): com.histmersion.api.media.AssetLinks.Body =
    _root_.com.histmersion.api.media.AssetLinks.Body((retarget(md, _root_.com.histmersion.api.media.AssetLinks.Url(Prefix), _root_.com.histmersion.api.media.AssetLinks.Url((publicUrl).value.trim.stripSuffix("/") + webBase(citekey)))).value)

  def webBase(citekey: com.histmersion.api.library.Citekey): com.histmersion.api.media.AssetLinks.Url = _root_.com.histmersion.api.media.AssetLinks.Url(s"/source/${enc(((citekey).value))}/assets/")

  def url(citekey: com.histmersion.api.media.AssetLinks.UrlCitekey, name: com.histmersion.api.media.AssetLinks.UrlName): com.histmersion.api.media.AssetLinks.Url = (webBase(citekey)).value + enc(name)

  private def enc(s: String): String =
    (java.net.URLEncoder.encode((s).value, "UTF-8").replace("+", "%20"))

  private val BareImg = """<img(?![^>]*\bloading=)\s""".r

  def lazyImages(html: com.histmersion.api.media.AssetLinks.Html): com.histmersion.api.media.AssetLinks.Html =
    _root_.com.histmersion.api.media.AssetLinks.Html(BareImg.replaceAllIn((html).value, _ => java.util.regex.Matcher.quoteReplacement("""<img loading="lazy" """)))

  /** Must match the converter's `_safe_name`: either side may name a file. */
  def safeName(raw: String): com.histmersion.api.exchange.Bundle.Asset.FileName = com.histmersion.api.text.SafeName.of(raw)

  def mimeOf(name: com.histmersion.api.exchange.Bundle.Asset.Extension): com.histmersion.api.exchange.Bundle.Asset.Mime =
    (name).value.toLowerCase.reverse.takeWhile(_ != '.').reverse match
      case "png"          => _root_.com.histmersion.api.exchange.Bundle.Asset.Mime("image/png")
      case "jpg" | "jpeg" => _root_.com.histmersion.api.exchange.Bundle.Asset.Mime("image/jpeg")
      case "gif"          => _root_.com.histmersion.api.exchange.Bundle.Asset.Mime("image/gif")
      case "webp"         => _root_.com.histmersion.api.exchange.Bundle.Asset.Mime("image/webp")
      case "svg"          => _root_.com.histmersion.api.exchange.Bundle.Asset.Mime("image/svg+xml")
      case _              => _root_.com.histmersion.api.exchange.Bundle.Asset.Mime("application/octet-stream")
