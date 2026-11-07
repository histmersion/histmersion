package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ArticleModal:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


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

  def open(id: com.histmersion.web.ArticleModal.Id, opts: Map[String, String] = Map.empty): Unit =
    WindowManager.open(WindowKey.Source(id))

  def openSlug(slug: com.histmersion.web.ApiClient.ArticleSlug, opts: Map[String, String] = Map.empty): Unit =
    WindowManager.open(WindowKey.Article(slug))

  def close(): Unit = ()

  /** Keep the windows in step with the URL (`UiState.open`): now, and on every back/forward. */
  def restore(): Unit = WindowManager.followState()
