package com.histmersion
package web.marks

import com.histmersion.web.{Auth, Icon, WindowKey, WindowManager}
import com.histmersion.web.ui.ContextMenu
import com.raquo.laminar.api.L.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.personal.ReadMark
import com.histmersion.api.personal.Target
import com.histmersion.lint.{tolerated, LintRule}

/** An item's own actions where it is read — Library's reading pane, Explore's detail: Bookmark (pressed while it is),
  * Add note, Mark as read / unread (a source or an article), and "⋯" with the copies. A change that needs the API key
  * asks for it first.
  */
object ItemActions:
  enum BookmarkButtonCompact derives CanEqual:
    case Compact, Full
  object BookmarkButtonCompact:
    extension (bookmarkButtonCompact: BookmarkButtonCompact) def compact: Boolean = bookmarkButtonCompact == Compact

  private def writing(act: => Unit): Unit =
    if Auth.writableNow then act else WindowManager.open(WindowKey.ApiKey)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def bookmark(ref: ItemRef): Unit = writing(MyItems.toggle(ref))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def bookmarkButton(ref: ItemRef, compact: BookmarkButtonCompact = BookmarkButtonCompact.Full): HtmlElement =
    val on = MyItems.bookmarked(ref)
    button(
      typ := "button",
      cls := (if compact.compact then "btn btn--bare btn--icon btn--sm row-bookmark" else "btn btn--sm item-bookmark"),
      cls("is-on") <-- on,
      aria.pressed <-- on.map(_.toString),
      title <-- on.map(b =>
        if b then shown"Bookmarked — remove the bookmark of “${ref.title}”" else shown"Bookmark “${ref.title}”"
      ),
      aria.label <-- on.map(b =>
        if b then shown"Remove the bookmark of ${ref.title}" else shown"Bookmark ${ref.title}"
      ),
      Icon(Icon.Bookmark),
      Option.when(!compact.compact)(span(child.text <-- on.map(b => if b then "Bookmarked" else "Bookmark"))),
      onClick --> { ev => ev.stopPropagation(); bookmark(ref) }
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
        title := shown"Write a note on “${ref.title}”",
        onClick --> (_ => NoteEditor.write(ref.target, ref.title))
      ),
      Option.when(ref.readable)(
        button(
          typ := "button",
          cls := "btn btn--sm item-read",
          Icon(Icon.Read),
          cls("is-on") <-- read,
          child.text <-- read.map(r => if r then "Mark as unread" else "Mark as read"),
          onClick --> (_ =>
            writing(MyItems.setRead(ref, ReadMark.Read(!MyItems.readNow(ref.identity.getOrElse(Target.Identity(""))))))
          )
        )
      ),
      ContextMenu.button(
        ContextMenu.ButtonTitle(shown"More: ${ref.title}"),
        () => ItemMenu.entries(ref, byline),
        ContextMenu.ButtonExtraCls("item-more")
      )
    )
