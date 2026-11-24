package com.histmersion
package web.marks

import com.histmersion.api.personal.Note
import com.raquo.laminar.api.L.*
import org.scalajs.dom
import com.histmersion.lint.{LintRule, tolerated}

/** The person's notes on one item, where the item is read: each with when it was written, its text, and Change and
  * Delete.
  */
object NoteList:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def on(ref: ItemRef): HtmlElement =
    ref.identity.fold(div(cls := "note-list is-hidden"))(i =>
      div(cls := "note-list", children <-- MyItems.notesOn(i).map(_.map(card)))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def card(n: Note): HtmlElement =
    div(
      cls := "note-card",
      dataAttr("note") := n.id.value,
      p(cls := "note-when muted", "Your note · ", child.text <-- Val(Ago.of(Ago.IsoDate(n.updatedAt.value)).value)),
      NoteView(NoteMarkdown.Markdown(n.text.value)),
      div(
        cls := "btn-group note-card-acts",
        button(
          typ := "button",
          cls := "btn btn--ghost btn--sm note-edit",
          "Change",
          onClick --> (_ => NoteEditor.edit(n))
        ),
        button(
          typ := "button",
          cls := "btn btn--ghost btn--sm note-delete",
          "Delete",
          onClick --> { _ =>
            if dom.window.confirm("Delete this note? It cannot be brought back here.") then MyItems.removeNote(n): Unit
          }
        )
      )
    )
