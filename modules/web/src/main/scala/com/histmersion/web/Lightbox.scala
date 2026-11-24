package com.histmersion
package web

import com.histmersion.api.media.MediaProvider
import org.scalajs.dom
import org.scalajs.dom.document
import com.histmersion.api.{Href, Html}
import com.histmersion.api.Html.html as markup
import com.histmersion.lint.{LintRule, tolerated}

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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def attrs(item: Item): Html =
    val vals = List(item.title, item.credit, item.author, item.license, item.licenseUrl, item.sourceUrl, item.provider)
    markup""" data-full="${item.src}"""" ++ Html.concat(
      keys
        .zip(vals)
        .collect {
          case (k, Some(v)) if v.trim.nonEmpty =>
            markup""" data-lb-$k="$v""""
        }
    )

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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def captionHtml(i: Item): Html =
    val title = i.title.map(t => markup"""<div class="lightbox-title">$t</div>""")
    val who = (i.credit, i.author) match
      case (Some(c), Some(a)) if !c.contains(a) => Some(markup"$a · $c")
      case (Some(c), _)                         => Some(Html.text(c))
      case (None, Some(a))                      => Some(Html.text(a))
      case _                                    => None
    val lic = i.license.map(l => LicenseName.html(l, i.licenseUrl))
    // the provider's page: a link only to an address of an allowed scheme
    val page = i.sourceUrl.flatMap(Href.link).map { u =>
      val where = i.provider
        .map(p => MediaProvider.title(MediaProvider.Id(p)).value)
        .filter(_.nonEmpty)
        .fold("the source")(p => p)
      markup"""<a class="lightbox-source" href="$u" target="_blank" rel="noopener">view on $where ↗</a>"""
    }
    val line = List(who, lic, page).flatten
    if title.isEmpty && line.isEmpty then Html.empty
    else
      markup"""<figcaption class="lightbox-caption">${title.getOrElse(Html.empty)}""" ++
        (if line.isEmpty then Html.empty
         else markup"""<div class="lightbox-credit">${Html.join(line, markup" · ")}</div>""") ++ markup"</figcaption>"

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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def open(item: Item): Unit =
    close()
    val box = document.createElement("div").asInstanceOf[dom.HTMLElement]
    box.className = "lightbox"
    box.setAttribute("role", "dialog")
    box.setAttribute("aria-label", item.title.orElse(Option(item.alt).filter(_.nonEmpty)).getOrElse("figure"))
    val fig = document.createElement("figure").asInstanceOf[dom.HTMLElement]
    fig.className = "lightbox-figure"
    val img = document.createElement("img").asInstanceOf[dom.HTMLImageElement]
    Href.image(item.src).foreach(u => img.src = u.address)
    img.alt = if item.alt != null then item.alt else ""
    fig.appendChild(img)
    HtmlSink.append(fig, captionHtml(item))
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
