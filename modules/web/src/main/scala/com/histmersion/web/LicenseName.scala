package com.histmersion.web

/** A picture's licence as a reader reads it: the stored SPDX-style id (`CC-BY-SA-3.0`, `CC0-1.0`, `public-domain`) as
  * the licence's own name (`CC BY-SA 3.0`), and where its text is, when the record gives no link of its own. Pure.
  */
object LicenseName:

  private val Cc0 = "(?i)^CC0(?:[- ]1\\.0)?\\.?$".r
  // CC-BY, CC-BY-SA-4.0, CC BY-NC-SA 2.5, CC-BY-SA-3.0-DE (a ported licence)
  private val Cc = "(?i)^CC[- ](BY(?:-(?:NC|SA|ND))*)(?:[- ](\\d(?:\\.\\d)?))?(?:[- ]([A-Z]{2,3}))?$".r
  private val Pdm = "(?i)^PDM(?:[- ]1\\.0)?$".r
  private val Pd = "(?i)^(?:public[- ]domain|PD)$".r

  /** The licence's name: `CC BY-SA 3.0`, `CC0 1.0`, `Public domain`; an id it does not know, as stored. */
  def label(id: String): String = id.trim match
    case Cc0()               => "CC0 1.0"
    case Cc(kind, ver, port) =>
      List(Some("CC"), Some(kind.toUpperCase), Option(ver), Option(port).map(_.toUpperCase)).flatten.mkString(" ")
    case Pdm() => "Public Domain Mark 1.0"
    case Pd()  => "Public domain"
    case other => other

  /** Where the licence's text is, for the ids it knows (Creative Commons' own pages). */
  def url(id: String): Option[String] = id.trim match
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
  def link(id: String, recorded: Option[String]): (String, Option[String]) =
    (label(id), recorded.map(_.trim).filter(_.nonEmpty).orElse(url(id)))

  /** The licence as HTML: its name, linked to its text when there is one (escaped). */
  def html(id: String, recorded: Option[String]): String =
    link(id, recorded) match
      case (name, Some(u)) =>
        s"""<a class="media-license" href="${Fmt.esc(u)}" target="_blank" rel="noopener license">${Fmt.esc(name)}</a>"""
      case (name, None) => s"""<span class="media-license">${Fmt.esc(name)}</span>"""
