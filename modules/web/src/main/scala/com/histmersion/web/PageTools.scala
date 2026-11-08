package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** The tools of a source's or an article's own page: the width of its text and the theme, at the top of the page — also
  * where the top bar has no room for its theme button. The width is kept in the browser's storage (and read back with
  * care: a private window may have none).
  */
object PageTools:
  val StorageKey = "histmersion.pageWidth"
  val WideClass = "page-wide"

  private val width = Var(PageWidth.Reading)

  private def apply(w: PageWidth): Unit =
    width.set(w)
    Option(dom.document.body).foreach(b =>
      if w == PageWidth.Wide then b.classList.add(WideClass) else b.classList.remove(WideClass)
    )

  def choose(w: PageWidth): Unit =
    if w == PageWidth.Reading then Store.Browser.remove(StorageKey) else Store.Browser.put(StorageKey, w.wire.value)
    apply(w)

  /** Read the stored width, apply it, and put the tools above the page's text. */
  def install(): Unit =
    apply(PageWidth.fromWire(PageWidth.Wire(Store.Browser.get(StorageKey).orNull)))
    Option(dom.document.getElementById("page-content")).foreach { content =>
      val holder = dom.document.createElement("div")
      content.parentNode.insertBefore(holder, content)
      val _ = render(holder, element)
    }

  def element: HtmlElement =
    div(
      cls := "page-tools toolbar",
      role := "toolbar",
      aria.label := "Page",
      div(
        cls := "btn-group",
        role := "group",
        aria.label := "Text width",
        PageWidth.values.toList.map(w =>
          button(
            typ := "button",
            cls := "btn btn--toggle btn--sm page-width-btn",
            dataAttr("width") := w.wire.value,
            aria.pressed <-- width.signal.map(x => (x == w).toString),
            w.label.value,
            onClick --> (_ => choose(w))
          )
        )
      ),
      div(cls := "btn-group", Theme.toggle.amend(cls := "page-theme-btn"))
    )
