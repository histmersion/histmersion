package com.histmersion
package api

/** An address a page may link to or show an image from, its scheme checked: only the web (`http`, `https`), mail
  * (`mailto`, links only), an address on this server (relative, no scheme) and, for an image only, an inline raster
  * picture (`data:image/png|jpeg|gif|webp;base64,…`). `javascript:`, `vbscript:`, every other `data:` (an SVG image can
  * carry script) and every other scheme are refused, so a title, credit or URL that came from an upstream cannot run in
  * the page.
  *
  * It is read as a browser reads it: tabs and line breaks anywhere and control characters or spaces around it do not
  * count (`java\tscript:` is `javascript:`); the address kept is the one checked, without them.
  */
type Href = Href.T

object Href:
  opaque type T = String

  given CanEqual[Href, Href] = CanEqual.derived

  /** The schemes a link may have. */
  val LinkSchemes: Set[String] = Set("http", "https", "mailto")

  /** The schemes an image may be loaded from. A `data:` image is allowed only in the form of [[RasterData]]. */
  val ImageSchemes: Set[String] = Set("http", "https")

  /** The picture types an inline `data:` image may have: rasters, which run nothing. SVG (`image/svg+xml`) can carry
    * script and stays refused.
    */
  val RasterTypes: Set[String] = Set("png", "jpeg", "gif", "webp")

  def link(raw: String): Option[T] = checked(raw, LinkSchemes, inlineRaster = false)

  def image(raw: String): Option[T] = checked(raw, ImageSchemes, inlineRaster = true)

  extension (h: T) def address: String = h

  private val Scheme = """^([A-Za-z][A-Za-z0-9+.\-]*):""".r

  /** The one `data:` form an image may have: a raster type of [[RasterTypes]], base64, nothing else (no parameter, no
    * percent-encoding, no space). The type is matched case-insensitively, as a browser reads it.
    */
  private val RasterData = """(?i)data:image/([a-z]+);base64,[A-Za-z0-9+/]+={0,2}""".r

  private def raster(address: String): Boolean =
    address match
      case RasterData(kind) => RasterTypes(kind.toLowerCase(java.util.Locale.ROOT))
      case _                => false

  private def checked(raw: String, schemes: Set[String], inlineRaster: Boolean): Option[T] =
    val inner = raw.filterNot(c => c == '\t' || c == '\n' || c == '\r')
    val start = inner.indexWhere(_ > ' ') match
      case -1 => inner.length
      case i  => i
    val end = inner.lastIndexWhere(_ > ' ') + 1
    val address = if end <= start then "" else inner.substring(start, end)
    Scheme.findPrefixMatchOf(address) match
      case Some(m) if inlineRaster && m.group(1).equalsIgnoreCase("data") && raster(address) => Some(address)
      case Some(m) if !schemes(m.group(1).toLowerCase(java.util.Locale.ROOT))                => None
      case _                                                                                 => Some(address)
