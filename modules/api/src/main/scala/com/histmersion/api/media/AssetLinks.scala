package com.histmersion
package api.media
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.api.text.SafeName
import com.histmersion.api.exchange.Bundle
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Helper, "pending G63 (no free-standing helper objects)")
object AssetLinks:
  type Body = Body.T
  object Body extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type TargetId = TargetId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TargetId extends Newtype[String, Pure]
  type TargetKind = TargetKind.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TargetKind extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type NormalizeBodyMd = Body
  val NormalizeBodyMd: Body.type = Body
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type NormalizeBodyTitle = Title
  val NormalizeBodyTitle: Title.type = Title
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ToWebMd = Body
  val ToWebMd: Body.type = Body
  export com.histmersion.api.library.Citekey as ToWebCitekey
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ToAbsoluteMd = Body
  val ToAbsoluteMd: Body.type = Body
  export com.histmersion.api.library.{Citekey as ToAbsoluteCitekey, Citekey as UrlCitekey}
  export Bundle.Asset.FileName as UrlName

  private val ImageLink = """!\[([^\]]*)\]\(\s*<?([^)>\s]+)>?(\s+"[^"]*")?\s*\)""".r

  val Prefix = "assets/"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def mapImages(md: Body)(f: (String, Target, String) => String): Body =
    Body(
      ImageLink.replaceAllIn(
        (md).value,
        m =>
          java.util.regex.Matcher
            .quoteReplacement((f((m.group(1)), Target(m.group(2)), (Option(m.group(3)).getOrElse("")))))
      )
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def retarget(md: Body, from: Url, to: Url): Body =
    Body(
      (mapImages(md)((alt, target, title) =>
        if !(target).value.startsWith((from).value) then (s"![$alt]($target$title)")
        else (s"![$alt]($to${(target).value.drop((from).value.length)}$title)")
      )).value
    )

  private val FiguresTarget = """^(?:[a-z]+:)?(?:[^?#]*[/\\])?figures[/\\]([^/\\?#]+)$""".r

  /** OCR bodies carry the path the converter saw on its own disk, often absolute
    * (`/Users/…/md/<citekey>/figures/x.png`); a browser resolves that against the server root and draws nothing, so
    * every spelling is accepted.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fromFigures(md: Body): Body =
    mapImages(md)((alt, target, title) =>
      target.value match
        case FiguresTarget(name) => s"![$alt]($Prefix${safeName(java.net.URLDecoder.decode(name, "UTF-8"))}$title)"
        case _                   => (s"![$alt]($target$title)")
    )

  /** An OCR body writes `![](…)`; a figure with no alt text is invisible to a screen reader. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def numberFigures(md: Body): Body =
    var n = 0
    Body((mapImages(md)((alt, target, title) =>
      n += 1
      val label = if (alt).trim.nonEmpty then alt else s"Figure $n"
      (s"![$label]($target$title)")
    )).value)

  /** The OCR sidecar brackets text it read inside a picture with these markers. */
  private val PictureText =
    """(?s)\*\*-+\s*Start of picture text\s*-+\*\*(?:<br\s*/?>)?\s*(.*?)\s*\*\*-+\s*End of picture text\s*-+\*\*(?:<br\s*/?>)?""".r

  /** One HTML block per figure, with no blank line inside: a blank line ends a Markdown HTML block, and flexmark would
    * render the rest as prose.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def collapsePictureText(md: Body): Body =
    Body(
      PictureText.replaceAllIn(
        (md).value,
        m =>
          val inner = m.group(1).replaceAll("(?s)\\R\\s*\\R", "<br>").replaceAll("\\R", " ").trim
          java.util.regex.Matcher
            .quoteReplacement(s"""<details class="figtext"><summary>figure text</summary><div>$inner</div></details>""")
      )
    )

  private val BoilerplateHeading =
    """(?i)^(original\s+|research\s+|review\s+|short\s+)?(article|paper|research|communication|report)$""".r

  private def flatten(s: String): String =
    ((s).toLowerCase.filter(c => c.isLetterOrDigit || c == ' ').replaceAll("\\s+", " ").trim)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def dropBoilerplateHeadings(md: Body, title: Title): Body =
    val want = flatten(((title).value))
    val kept = (md).value.linesIterator.filterNot { line =>
      val l = line.trim
      if !l.startsWith("#") then false
      else
        val text = flatten((l.dropWhile(_ == '#')))
        (text).nonEmpty && (BoilerplateHeading.matches((text)) || ((want).nonEmpty && text == want))
    }
    Body(kept.mkString("\n"))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def normalizeBody(md: NormalizeBodyMd, title: NormalizeBodyTitle = NormalizeBodyTitle("")): Body =
    dropBoilerplateHeadings(collapsePictureText(fromFigures(md)), title)

  def toWeb(md: ToWebMd, citekey: AssetLinks.ToWebCitekey): Body = retarget(md, Url(Prefix), webBase(citekey))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def toAbsolute(md: ToAbsoluteMd, citekey: AssetLinks.ToAbsoluteCitekey, publicUrl: Url): Body =
    retarget(md, Url(Prefix), Url((publicUrl).value.trim.stripSuffix("/") + webBase(citekey)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def webBase(citekey: Citekey): Url = Url(s"/source/${enc(((citekey).value))}/assets/")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def url(citekey: AssetLinks.UrlCitekey, name: AssetLinks.UrlName): Url = Url(
    (webBase(citekey)).value + enc(name.value)
  )

  private def enc(s: String): String =
    (java.net.URLEncoder.encode((s), "UTF-8").replace("+", "%20"))

  /** Must match the converter's `_safe_name`: either side may name a file. */
  def safeName(raw: String): SafeName.Sanitized = SafeName.of(raw)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mimeOf(name: Bundle.Asset.Extension): Bundle.Asset.MimeText =
    (name).value.toLowerCase.reverse.takeWhile(_ != '.').reverse match
      case "png"          => Bundle.Asset.MimeText("image/png")
      case "jpg" | "jpeg" => Bundle.Asset.MimeText("image/jpeg")
      case "gif"          => Bundle.Asset.MimeText("image/gif")
      case "webp"         => Bundle.Asset.MimeText("image/webp")
      case "svg"          => Bundle.Asset.MimeText("image/svg+xml")
      case _              => Bundle.Asset.MimeText("application/octet-stream")
