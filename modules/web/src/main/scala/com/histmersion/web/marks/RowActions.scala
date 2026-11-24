package com.histmersion
package web.marks

import com.histmersion.web.NewTabLink
import com.histmersion.web.ui.ContextMenu
import com.raquo.laminar.api.L.*
import com.histmersion.lint.{tolerated, LintRule}

/** A list row's own actions — Bookmark and Open in a new tab — in a column of the row itself, at its right, as tall as
  * the row, lit with the row's hover and focus (so it is plain which row they belong to); and the row's menu (right
  * click, the menu key) with the same and the copies.
  */
object RowActions:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(ref: ItemRef, href: ItemRef.Href, byline: String = ""): Modifier[HtmlElement] =
    List(
      cls("has-actions") := true,
      ContextMenu.on(ContextMenu.OnTitle(shown"Actions: ${ref.title}"), () => ItemMenu.entries(ref, byline)),
      div(
        cls := "row-actions",
        ItemActions.bookmarkButton(ref, compact = ItemActions.BookmarkButtonCompact(true)),
        NewTabLink(NewTabLink.Href(href.value), NewTabLink.Label(ref.title.value))
      )
    )
