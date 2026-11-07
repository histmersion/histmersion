package com.histmersion.web

/** A picture's licence as a reader reads it: the stored SPDX-style id (`CC-BY-SA-3.0`, `CC0-1.0`, `public-domain`) as
  * the licence's own name (`CC BY-SA 3.0`), and where its text is, when the record gives no link of its own. Pure.
  */
object LicenseName:
  type Html = Html.T
  object Html extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Recorded = Recorded.T
  object Recorded extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type UrlId = _root_.com.histmersion.web.LicenseName.Id
  val UrlId: _root_.com.histmersion.web.LicenseName.Id.type = _root_.com.histmersion.web.LicenseName.Id
  type LinkId = _root_.com.histmersion.web.LicenseName.Id
  val LinkId: _root_.com.histmersion.web.LicenseName.Id.type = _root_.com.histmersion.web.LicenseName.Id
  type HtmlId = _root_.com.histmersion.web.LicenseName.Id
  val HtmlId: _root_.com.histmersion.web.LicenseName.Id.type = _root_.com.histmersion.web.LicenseName.Id
  type HtmlRecorded = _root_.com.histmersion.web.LicenseName.Recorded
  val HtmlRecorded: _root_.com.histmersion.web.LicenseName.Recorded.type = _root_.com.histmersion.web.LicenseName.Recorded

  private val Cc0 = "(?i)^CC0(?:[- ]1\\.0)?\\.?$".r
  // CC-BY, CC-BY-SA-4.0, CC BY-NC-SA 2.5, CC-BY-SA-3.0-DE (a ported licence)
  private val Cc = "(?i)^CC[- ](BY(?:-(?:NC|SA|ND))*)(?:[- ](\\d(?:\\.\\d)?))?(?:[- ]([A-Z]{2,3}))?$".r
  private val Pdm = "(?i)^PDM(?:[- ]1\\.0)?$".r
  private val Pd = "(?i)^(?:public[- ]domain|PD)$".r

  /** The licence's name: `CC BY-SA 3.0`, `CC0 1.0`, `Public domain`; an id it does not know, as stored. */
  def label(id: com.histmersion.web.LicenseName.Id): com.histmersion.web.LicenseName.Label = id.trim match
    case Cc0()               => "CC0 1.0"
    case Cc(kind, ver, port) =>
      List(Some("CC"), Some(kind.toUpperCase), Option(ver), Option(port).map(_.toUpperCase)).flatten.mkString(" ")
    case Pdm() => "Public Domain Mark 1.0"
    case Pd()  => "Public domain"
    case other => other

  /** Where the licence's text is, for the ids it knows (Creative Commons' own pages). */
  def url(id: com.histmersion.web.LicenseName.UrlId): Option[com.histmersion.web.LicenseName.Url] = id.trim match
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
  def link(id: com.histmersion.web.LicenseName.LinkId, recorded: Option[com.histmersion.web.LicenseName.Recorded]): (com.histmersion.web.LicenseName.Label, Option[com.histmersion.web.LicenseName.Url]) =
    (label(id), recorded.map(_.trim).filter(_.nonEmpty).orElse(url(id)))

  /** The licence as HTML: its name, linked to its text when there is one (escaped). */
  def html(id: com.histmersion.web.LicenseName.HtmlId, recorded: Option[com.histmersion.web.LicenseName.HtmlRecorded]): com.histmersion.web.LicenseName.Html =
    link(id, recorded) match
      case (name, Some(u)) =>
        s"""<a class="media-license" href="${Fmt.esc(u)}" target="_blank" rel="noopener license">${Fmt.esc(name)}</a>"""
      case (name, None) => s"""<span class="media-license">${Fmt.esc(name)}</span>"""
