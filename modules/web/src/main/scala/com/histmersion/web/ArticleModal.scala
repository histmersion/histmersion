package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.document

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ArticleModal:

  private var lightboxEl: Option[dom.Element] = None

  val element: HtmlElement =
    div(
      WindowManager.element,
      onMountCallback { _ =>
        document.addEventListener("keydown", { (ev: dom.KeyboardEvent) =>
          if !ev.metaKey && !ev.ctrlKey && !ev.altKey then
            val tag = Option(ev.target).map(_.asInstanceOf[dom.Element].tagName.toLowerCase).getOrElse("")
            val editable = tag == "input" || tag == "textarea" ||
              (ev.target != null && ev.target.asInstanceOf[dom.HTMLElement].isContentEditable)
            if !editable && ev.key == "Escape" then
              lightboxEl match
                case Some(_) => closeLightbox()
                case None    => ()
        })

        document.addEventListener("click", { (ev: dom.MouseEvent) =>
          val img = Option(ev.target).flatMap { t =>
            val el = t.asInstanceOf[dom.Element]
            Option(el.closest("img")).orElse(if el.tagName == "IMG" then Some(el) else None)
          }
          img.foreach { imgEl =>
            val src = imgEl.getAttribute("src")
            if src != null && src.contains("/assets/") && imgEl.closest(".lightbox") == null then
              ev.preventDefault()
              openLightbox(src, imgEl.getAttribute("alt"))
          }
        })
      }
    )

  def open(id: String, opts: Map[String, String] = Map.empty): Unit =
    WindowManager.open(WindowKey.Source(id))

  def openSlug(slug: String, opts: Map[String, String] = Map.empty): Unit =
    WindowManager.open(WindowKey.Article(slug))

  def close(): Unit = ()

  /** Keep the windows in step with the URL (`UiState.open`): now, and on every back/forward. */
  def restore(): Unit = WindowManager.followState()

  private def openLightbox(src: String, alt: String): Unit =
    closeLightbox()
    val box = document.createElement("div")
    box.asInstanceOf[dom.HTMLElement].className = "lightbox"
    box.setAttribute("role", "dialog")
    box.setAttribute("aria-label", if alt != null && alt.nonEmpty then alt else "figure")
    box.asInstanceOf[dom.HTMLElement].style.cssText =
      "position:fixed;inset:0;z-index:9999;display:flex;align-items:center;" +
      "justify-content:center;background:rgba(0,0,0,.82);cursor:zoom-out;padding:2rem"
    val img = document.createElement("img").asInstanceOf[dom.HTMLImageElement]
    img.src = src
    img.alt = if alt != null then alt else ""
    img.style.cssText = "max-width:100%;max-height:100%;box-shadow:0 2px 24px rgba(0,0,0,.6)"
    box.appendChild(img)
    box.addEventListener("click", (_: dom.MouseEvent) => closeLightbox())
    document.body.appendChild(box)
    lightboxEl = Some(box)

  private def closeLightbox(): Unit =
    lightboxEl.foreach(_.remove())
    lightboxEl = None
