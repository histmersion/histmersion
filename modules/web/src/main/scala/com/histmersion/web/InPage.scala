package com.histmersion.web

import org.scalajs.dom
import org.scalajs.dom.document

import scala.scalajs.js

/** In-page navigation of a rendered source/article — in a window or on a standalone page: citation numbers (`#cite-n`)
  * and search-match jumps (`#hl1`, the `n` shortcut) scroll INSIDE the content instead of writing the fragment into the
  * URL, where the web UI keeps its filter state.
  */
object InPage:
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Fragment = Fragment.T
  object Fragment extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Href = Href.T
  object Href extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Index = Index.T
  object Index extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The id a same-page link points at: `#cite-3` → `cite-3`; anything else → `None`. Pure. */

  def fragmentOf(href: com.histmersion.web.InPage.Href): Option[com.histmersion.web.InPage.Fragment] =
    Option(href).filter(h => h.startsWith("#") && h.length > 1).map(_.drop(1))

  /** One delegated click handler per content root. */
  def wire(root: dom.Element): Unit =
    root.addEventListener(
      "click",
      (ev: dom.MouseEvent) =>
        ev.target match
          case el: dom.Element =>
            Option(el.closest("a")).foreach { a =>
              if a.classList.contains("next-match") then
                ev.preventDefault(); nextMatch(root)
              else
                fragmentOf(a.getAttribute("href")).foreach { id =>
                  ev.preventDefault()
                  scrollTo(root, id)
                }
            }
          case _ => ()
    )

  private def scrollTo(root: dom.Element, id: com.histmersion.web.InPage.Id): Unit =
    Option(root.querySelector(s"[id=\"${id.replace("\"", "")}\"]"))
      .foreach(_.asInstanceOf[js.Dynamic].scrollIntoView(js.Dynamic.literal(behavior = "smooth", block = "center")))

  /** The index of the match after `current` among `count`, wrapping — pure. */
  def nextIndex(current: com.histmersion.web.InPage.Index, count: com.histmersion.web.InPage.Count): com.histmersion.web.InPage.Index = if count <= 0 then com.histmersion.web.InPage.Index(-1) else (current + 1) % count

  /** Step to the next `<mark id="hl…">`, remembering the position on the root. */
  def nextMatch(root: dom.Element): Unit =
    val marks = root.querySelectorAll("mark[id^=\"hl\"]")
    val cur = Option(root.getAttribute("data-match")).flatMap(_.toIntOption).getOrElse(-1)
    val next = nextIndex(cur, marks.length)
    if next >= 0 then
      root.setAttribute("data-match", next.toString)
      marks(next).asInstanceOf[js.Dynamic].scrollIntoView(js.Dynamic.literal(behavior = "smooth", block = "center"))

  /** `n` steps through the matches — unless the reader is typing. */
  def wireShortcut(root: dom.Element): Unit =
    document.addEventListener(
      "keydown",
      (ev: dom.KeyboardEvent) =>
        val typing = ev.target match
          case el: dom.HTMLElement => el.isContentEditable || Set("input", "textarea", "select")(el.tagName.toLowerCase)
          case _                   => false
        if !typing && !ev.metaKey && !ev.ctrlKey && !ev.altKey && ev.key == "n" then nextMatch(root)
    )
