package com.histmersion.web.marks

import com.histmersion.web.NewTabLink
import com.histmersion.web.ui.ContextMenu
import com.raquo.laminar.api.L.*

/** A list row's own actions — Bookmark and Open in a new tab — in a column of the row itself, at its right, as tall as
  * the row, lit with the row's hover and focus (so it is plain which row they belong to); and the row's menu (right
  * click, the menu key) with the same and the copies.
  */
object RowActions:

  type ApplyHref = ApplyHref.T
  object ApplyHref extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyByline = ApplyByline.T
  object ApplyByline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(ref: ItemRef, href: com.histmersion.web.marks.RowActions.ApplyHref, byline: com.histmersion.web.marks.RowActions.ApplyByline = ""): Modifier[HtmlElement] =
    List(
      cls("has-actions") := true,
      ContextMenu.on(s"Actions: ${ref.title}", () => ItemMenu.entries(ref, byline)),
      div(cls := "row-actions", ItemActions.bookmarkButton(ref, compact = true), NewTabLink(href, ref.title))
    )
