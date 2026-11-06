package com.histmersion.web

/** A picture's licence as a reader reads it: the stored SPDX-style id (`CC-BY-SA-3.0`, `CC0-1.0`, `public-domain`) as
  * the licence's own name (`CC BY-SA 3.0`), and where its text is, when the record gives no link of its own. Pure.
  */
object LicenseName:

  type LabelResult = LabelResult.T
  object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelId = LabelId.T
  object LabelId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlResult = UrlResult.T
  object UrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlId = _root_.com.histmersion.web.LicenseName.LabelId
  val UrlId: _root_.com.histmersion.web.LicenseName.LabelId.type = _root_.com.histmersion.web.LicenseName.LabelId
  type LinkResultPart1 = LinkResultPart1.T
  object LinkResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkResultPart2 = LinkResultPart2.T
  object LinkResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkId = _root_.com.histmersion.web.LicenseName.LabelId
  val LinkId: _root_.com.histmersion.web.LicenseName.LabelId.type = _root_.com.histmersion.web.LicenseName.LabelId
  type LinkRecorded = LinkRecorded.T
  object LinkRecorded extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HtmlResult = HtmlResult.T
  object HtmlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HtmlId = _root_.com.histmersion.web.LicenseName.LabelId
  val HtmlId: _root_.com.histmersion.web.LicenseName.LabelId.type = _root_.com.histmersion.web.LicenseName.LabelId
  type HtmlRecorded = _root_.com.histmersion.web.LicenseName.LinkRecorded
  val HtmlRecorded: _root_.com.histmersion.web.LicenseName.LinkRecorded.type = _root_.com.histmersion.web.LicenseName.LinkRecorded

  private val Cc0 = "(?i)^CC0(?:[- ]1\\.0)?\\.?$".r
  // CC-BY, CC-BY-SA-4.0, CC BY-NC-SA 2.5, CC-BY-SA-3.0-DE (a ported licence)
  private val Cc = "(?i)^CC[- ](BY(?:-(?:NC|SA|ND))*)(?:[- ](\\d(?:\\.\\d)?))?(?:[- ]([A-Z]{2,3}))?$".r
  private val Pdm = "(?i)^PDM(?:[- ]1\\.0)?$".r
  private val Pd = "(?i)^(?:public[- ]domain|PD)$".r

  /** The licence's name: `CC BY-SA 3.0`, `CC0 1.0`, `Public domain`; an id it does not know, as stored. */
  def label(id: com.histmersion.web.LicenseName.LabelId): com.histmersion.web.LicenseName.LabelResult = id.trim match
    case Cc0()               => "CC0 1.0"
    case Cc(kind, ver, port) =>
      List(Some("CC"), Some(kind.toUpperCase), Option(ver), Option(port).map(_.toUpperCase)).flatten.mkString(" ")
    case Pdm() => "Public Domain Mark 1.0"
    case Pd()  => "Public domain"
    case other => other

  /** Where the licence's text is, for the ids it knows (Creative Commons' own pages). */
  def url(id: com.histmersion.web.LicenseName.UrlId): Option[com.histmersion.web.LicenseName.UrlResult] = id.trim match
    case Cc0()                              => Some("https://creativecommons.org/publicdomain/zero/1.0/")
    case Cc(kind, ver, port) if ver != null =>
      Some(
        s"https://creativecommons.org/licenses/${kind.toLowerCase}/$ver/" + Option(port).fold("")(p =>
          s"${p.toLowerCase}/"
        )
      )
    case Pdm() => Some("https://creativecommons.org/publicdomain/mark/1.0/")
    case _     => None

  /** The name and the link a picture's licence is shown with: the record's own link first. */
  def link(id: com.histmersion.web.LicenseName.LinkId, recorded: Option[com.histmersion.web.LicenseName.LinkRecorded]): (com.histmersion.web.LicenseName.LinkResultPart1, Option[com.histmersion.web.LicenseName.LinkResultPart2]) =
    (label(id), recorded.map(_.trim).filter(_.nonEmpty).orElse(url(id)))

  /** The licence as HTML: its name, linked to its text when there is one (escaped). */
  def html(id: com.histmersion.web.LicenseName.HtmlId, recorded: Option[com.histmersion.web.LicenseName.HtmlRecorded]): com.histmersion.web.LicenseName.HtmlResult =
    link(id, recorded) match
      case (name, Some(u)) =>
        s"""<a class="media-license" href="${Fmt.esc(u)}" target="_blank" rel="noopener license">${Fmt.esc(name)}</a>"""
      case (name, None) => s"""<span class="media-license">${Fmt.esc(name)}</span>"""
