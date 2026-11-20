package com.histmersion
package web

import com.histmersion.api.Html
import com.histmersion.web.map.maplibre.GlPopup
import org.scalajs.dom

/** The page's only ways to put markup into the document. Each takes an [[Html]], which only escaping, the code's own
  * literal markup or the allow-list makes, so no text from outside (a title, a credit, a fetched page) reaches a DOM
  * sink as markup. `ContractsSuite` rule `html-sink` keeps every other `innerHTML`, `insertAdjacentHTML` and `setHTML`
  * out of the page.
  */
object HtmlSink:
  /** `el`'s content replaced by `html`. */
  def replace(el: dom.Element, html: Html): Unit = el.innerHTML = html.markup

  /** `html` added at the end of `el`'s content. */
  def append(el: dom.Element, html: Html): Unit = el.insertAdjacentHTML("beforeend", html.markup)

  /** A map popup's content. */
  def popup(p: GlPopup, html: Html): GlPopup = p.setHTML(html.markup)
