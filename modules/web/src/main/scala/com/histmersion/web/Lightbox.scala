package com.histmersion
package web

import com.histmersion.api.media.MediaProvider
import org.scalajs.dom
import org.scalajs.dom.document

/** A picture opened full-screen: a figure in a page (its `/assets/` image), or a stored picture shown small on the map
  * or a card — any `<img>` that names its full size in `data-full` ([[attrs]]), shown with its title, who made it, its
  * licence's name linked to its text, and the provider's page. A click on it (not on a link in its caption) or Escape
  * closes it.
  */
object Lightbox:

  /** What a full-size view shows: the picture and what its licence asks to be said beside it. */
  final case class Item(
      src: String,
      alt: String,
      title: Option[String] = None,
      credit: Option[String] = None,
      author: Option[String] = None,
      license: Option[String] = None,
      licenseUrl: Option[String] = None,
      sourceUrl: Option[String] = None,
      provider: Option[String] = None
  )

  // the data attributes an `<img>` names its full-size view in
  private val Full = "data-full"
  private val keys = List("title", "credit", "author", "license", "license-url", "source-url", "provider")

  /** Pure: the attributes of a thumbnail that opens `item` full size (escaped, each with a leading space). */
  def attrs(item: Item): String =
    val vals = List(item.title, item.credit, item.author, item.license, item.licenseUrl, item.sourceUrl, item.provider)
    s""" $Full="${Fmt.esc(item.src)}"""" + keys
      .zip(vals)
      .collect {
        case (k, Some(v)) if v.trim.nonEmpty =>
          s""" data-lb-$k="${Fmt.esc(v)}""""
      }
      .mkString

  /** Pure: the item an image's attributes (`attr`, absent → None) and `src`/`alt` describe. */
  def fromAttrs(attr: String => Option[String], src: String, alt: String): Item =
    def a(k: String) = attr(s"data-lb-$k").map(_.trim).filter(_.nonEmpty)
    Item(
      attr(Full).filter(_.nonEmpty).getOrElse(src),
      alt,
      a("title"),
      a("credit"),
      a("author"),
      a("license"),
      a("license-url"),
      a("source-url"),
      a("provider")
    )

  /** Pure: the caption under the picture (escaped): its title, then the credit (and the author when the credit does not
    * name them), the licence's name linked to its text, and the provider's page.
    */
  def captionHtml(i: Item): String =
    val title = i.title.map(t => s"""<div class="lightbox-title">${Fmt.esc(t)}</div>""")
    val who = (i.credit, i.author) match
      case (Some(c), Some(a)) if !c.contains(a) => Some(s"${Fmt.esc(a)} · ${Fmt.esc(c)}")
      case (Some(c), _)                         => Some(Fmt.esc(c))
      case (None, Some(a))                      => Some(Fmt.esc(a))
      case _                                    => None
    val lic = i.license.map(l => LicenseName.html(l, i.licenseUrl))
    val page = i.sourceUrl.map { u =>
      val where = i.provider
        .map(p => MediaProvider.title(MediaProvider.Id(p)).value)
        .filter(_.nonEmpty)
        .fold("the source")(p => p)
      s"""<a class="lightbox-source" href="${Fmt.esc(u)}" target="_blank" rel="noopener">view on ${Fmt.esc(
          where
        )} ↗</a>"""
    }
    val line = List(who, lic, page).flatten
    if title.isEmpty && line.isEmpty then ""
    else
      s"""<figcaption class="lightbox-caption">${title.getOrElse("")}""" +
        (if line.isEmpty then ""
         else s"""<div class="lightbox-credit">${line.mkString(" · ")}</div>""") + "</figcaption>"

  /** Pure: does an image with these `src` and `data-full` open in the lightbox? A page's figure (`/assets/`) or a
    * picture that names its full size.
    */
  def opens(src: String, full: Option[String]): Boolean =
    full.exists(_.nonEmpty) || (src != null && src.contains("/assets/"))

  // `var`: the lightbox shown now
  private var shown: Option[dom.Element] = None

  def isOpen: Boolean = shown.isDefined

  /** A click anywhere: on an image that opens full size, the lightbox (answers whether it took the click). */
  def onClick(ev: dom.MouseEvent): Boolean =
    val img = Option(ev.target).collect { case e: dom.Element => e }.flatMap(el => Option(el.closest("img")))
    img.exists { imgEl =>
      val src = imgEl.getAttribute("src")
      val full = Option(imgEl.getAttribute(Full))
      val take = imgEl.closest(".lightbox") == null && opens(src, full)
      if take then
        ev.preventDefault()
        ev.stopPropagation()
        open(fromAttrs(k => Option(imgEl.getAttribute(k)), src, Option(imgEl.getAttribute("alt")).getOrElse("")))
      take
    }

  def open(item: Item): Unit =
    close()
    val box = document.createElement("div").asInstanceOf[dom.HTMLElement]
    box.className = "lightbox"
    box.setAttribute("role", "dialog")
    box.setAttribute("aria-label", item.title.orElse(Option(item.alt).filter(_.nonEmpty)).getOrElse("figure"))
    val fig = document.createElement("figure").asInstanceOf[dom.HTMLElement]
    fig.className = "lightbox-figure"
    val img = document.createElement("img").asInstanceOf[dom.HTMLImageElement]
    img.src = item.src
    img.alt = if item.alt != null then item.alt else ""
    fig.appendChild(img)
    fig.insertAdjacentHTML("beforeend", captionHtml(item))
    box.appendChild(fig)
    // a link in the caption opens its page; anywhere else closes the view
    box.addEventListener(
      "click",
      (e: dom.MouseEvent) =>
        if Option(e.target).collect { case t: dom.Element => t }.forall(_.closest("a") == null) then close()
    )
    document.body.appendChild(box)
    shown = Some(box)

  def close(): Unit =
    shown.foreach(_.remove())
    shown = None
