package com.histmersion.web.marks

import com.histmersion.web.{Auth, Icon, WindowKey, WindowManager}
import com.histmersion.web.ui.ContextMenu
import com.raquo.laminar.api.L.*

/** An item's own actions where it is read — Library's reading pane, Explore's detail: Bookmark (pressed while it is),
  * Add note, Mark as read / unread (a source or an article), and "⋯" with the copies. A change that needs the API key
  * asks for it first.
  */
object ItemActions:

  private def writing(act: => Unit): Unit =
    if Auth.writableNow then act else WindowManager.open(WindowKey.ApiKey)

  def bookmark(ref: ItemRef): Unit = writing(MyItems.toggle(ref))

  def bookmarkButton(ref: ItemRef, compact: Boolean = false): HtmlElement =
    val on = MyItems.bookmarked(ref)
    button(
      typ := "button",
      cls := (if compact then "btn btn--bare btn--icon btn--sm row-bookmark" else "btn btn--sm item-bookmark"),
      cls("is-on") <-- on,
      aria.pressed <-- on.map(_.toString),
      title <-- on.map(b =>
        if b then s"Bookmarked — remove the bookmark of “${ref.title}”" else s"Bookmark “${ref.title}”"
      ),
      aria.label <-- on.map(b => if b then s"Remove the bookmark of ${ref.title}" else s"Bookmark ${ref.title}"),
      Icon(Icon.Bookmark),
      Option.when(!compact)(span(child.text <-- on.map(b => if b then "Bookmarked" else "Bookmark"))),
      onClick --> { ev => ev.stopPropagation(); bookmark(ref) }
    )

  def apply(ref: ItemRef, byline: String = ""): HtmlElement =
    val read = MyItems.isRead(ref)
    div(
      cls := "btn-group item-actions",
      role := "group",
      aria.label := "Your marks",
      bookmarkButton(ref),
      button(
        typ := "button",
        cls := "btn btn--sm item-note",
        Icon(Icon.Note),
        "Add note",
        title := s"Write a note on “${ref.title}”",
        onClick --> (_ => NoteEditor.write(ref.target, ref.title))
      ),
      Option.when(ref.readable)(
        button(
          typ := "button",
          cls := "btn btn--sm item-read",
          Icon(Icon.Read),
          cls("is-on") <-- read,
          child.text <-- read.map(r => if r then "Mark as unread" else "Mark as read"),
          onClick --> (_ => writing(MyItems.setRead(ref, !MyItems.readNow(ref.identity.getOrElse("")))))
        )
      ),
      ContextMenu.button(s"More: ${ref.title}", () => ItemMenu.entries(ref, byline), "item-more")
    )
