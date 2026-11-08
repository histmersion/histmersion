package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.library.Citekey
import com.histmersion.api.research.Article

object ArticleModal:
  val element: HtmlElement =
    div(
      WindowManager.element,
      // the document's events while the windows are on the page, unbound with them
      documentEvents(_.onKeyDown) --> { (ev: dom.KeyboardEvent) =>
        if !ev.metaKey && !ev.ctrlKey && !ev.altKey then
          val tag = Option(ev.target).map(_.asInstanceOf[dom.Element].tagName.toLowerCase).getOrElse("")
          // an SVG element (a focused map place) is no HTMLElement and has no `isContentEditable`
          val editable = tag == "input" || tag == "textarea" || (ev.target match
            case el: dom.HTMLElement => el.isContentEditable
            case _                   => false)
          if !editable && ev.key == "Escape" && Lightbox.isOpen then
            ev.preventDefault() // the lightbox takes this Escape, not the window under it
            Lightbox.close()
      },
      // a page's figure, or a picture on the map or a card that names its full size: the lightbox
      documentEvents(_.onClick) --> { (ev: dom.MouseEvent) =>
        val _ = Lightbox.onClick(ev)
      }
    )

  def open(id: Citekey, opts: Map[String, String] = Map.empty): Unit =
    WindowManager.open(WindowKey.Source(WindowKey.Source.Citekey(id.value)))

  def openSlug(slug: Article.Slug, opts: Map[String, String] = Map.empty): Unit =
    WindowManager.open(WindowKey.Article(WindowKey.Article.Slug(slug.value)))

  def close(): Unit = ()

  /** Keep the windows in step with the URL (`UiState.open`): now, and on every back/forward. */
  def restore(): Unit = WindowManager.followState()
