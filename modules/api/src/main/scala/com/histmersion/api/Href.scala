package com.histmersion
package api

/** An address a page may link to or show an image from, its scheme checked: only the web (`http`, `https`), mail
  * (`mailto`, links only) and an address on this server (relative, no scheme). `javascript:`, `vbscript:`, `data:` and
  * every other scheme are refused, so a title, credit or URL that came from an upstream cannot run in the page.
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

  /** The schemes an image may be loaded from: no `data:`, whose SVG can carry script. */
  val ImageSchemes: Set[String] = Set("http", "https")

  def link(raw: String): Option[T] = checked(raw, LinkSchemes)

  def image(raw: String): Option[T] = checked(raw, ImageSchemes)

  extension (h: T) def address: String = h

  private val Scheme = """^([A-Za-z][A-Za-z0-9+.\-]*):""".r

  private def checked(raw: String, schemes: Set[String]): Option[T] =
    val inner = raw.filterNot(c => c == '\t' || c == '\n' || c == '\r')
    val start = inner.indexWhere(_ > ' ') match
      case -1 => inner.length
      case i  => i
    val end = inner.lastIndexWhere(_ > ' ') + 1
    val address = if end <= start then "" else inner.substring(start, end)
    Scheme.findPrefixMatchOf(address) match
      case Some(m) if !schemes(m.group(1).toLowerCase(java.util.Locale.ROOT)) => None
      case _                                                                  => Some(address)
