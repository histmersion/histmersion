package com.histmersion
package web.marks
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.personal.{Note, Target}
import com.histmersion.web.{Auth, ErrorLine, WindowKey, WindowManager}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.library.Title
import com.histmersion.web.{:=, given}

/** Writing a note: what it is left on, a plain text box (Markdown) with a preview of what it will look like, and Save
  * or Cancel — nothing is saved until Save. What is typed is kept by this browser as a draft ([[NoteDrafts]]) until
  * then, so a reload or a closed tab loses nothing; Cancel throws the draft away. One editor is open at a time; Escape
  * closes it and keeps the draft.
  */
object NoteEditor:

  final private case class Session(target: Target, title: Title, note: Option[Note], back: Option[dom.HTMLElement])

  private val session = Var(Option.empty[Session])

  def isOpen: Boolean = session.now().isDefined

  /** A new note on `target`. */
  def write(target: Target, title: Title): Unit = open(Session(target, title, None, focused))

  def edit(n: Note): Unit = open(Session(n.target, n.title, Some(n), focused))

  private def focused: Option[dom.HTMLElement] = Option(dom.document.activeElement).collect { case e: dom.HTMLElement =>
    e
  }

  private def open(s: Session): Unit =
    if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
    else session.set(Some(s))

  private def close(): Unit =
    val was = session.now()
    session.set(None)
    was.flatMap(_.back).filter(_.isConnected).foreach(_.focus())

  lazy val element: HtmlElement =
    div(cls := "note-editor-root", child.maybe <-- session.signal.map(_.map(dialog)))

  private def dialog(s: Session): HtmlElement =
    val draftKey = NoteDrafts.key(s.target, s.note.map(_.id))
    val restored = NoteDrafts.get(draftKey).filter(d => !s.note.exists(_.text == d))
    val text = Var(restored.getOrElse(s.note.fold(Note.Text(""))(_.text)))
    val preview = Var(false)
    val saving = Var(false)
    val problem = Var(Option.empty[String])
    def save(): Unit =
      if text.now().value.trim.nonEmpty && !saving.now() then
        saving.set(true)
        problem.set(None)
        val f = s.note.fold(MyItems.addNote(s.target, text.now(), s.title))(n => MyItems.editNote(n, text.now()))
        f.onComplete { r =>
          saving.set(false)
          r.fold(
            e => problem.set(Some(Option(e.getMessage).getOrElse("the note was not saved"))),
            _ => {
              NoteDrafts.drop(draftKey)
              close()
            }
          )
        }
    div(
      cls := "note-editor-scrim",
      onClick --> { ev => if ev.target eq ev.currentTarget then close() },
      div(
        cls := "note-editor",
        role := "dialog",
        htmlAttr("aria-modal", com.raquo.laminar.codecs.StringAsIsCodec) := "true",
        aria.labelledBy := "note-editor-title",
        onKeyDown --> { ev =>
          if ev.key == "Escape" then { ev.preventDefault(); ev.stopPropagation(); close() }
          else if ev.key == "Enter" && (ev.metaKey || ev.ctrlKey) then { ev.preventDefault(); save() }
        },
        h2(
          idAttr := "note-editor-title",
          cls := "note-editor-title",
          if s.note.isDefined then "Change your note" else "Add a note"
        ),
        p(
          cls := "note-editor-on muted",
          s"On ${s.target.kind.label.value.toLowerCase}: ",
          strong(s.title),
          Option.when(Target.where(s.target).value.nonEmpty)(
            span(s" · ${Target.where(s.target).value}")
          )
        ),
        Option.when(restored.isDefined)(
          p(
            cls := "pane-note note-draft-restored",
            role := "status",
            "Your unsaved draft is back. Save it, or Cancel to throw it away."
          )
        ),
        div(
          cls := "btn-group note-editor-tabs",
          role := "group",
          aria.label := "Write or preview",
          button(
            typ := "button",
            cls := "btn btn--toggle btn--sm note-write-tab",
            "Write",
            aria.pressed <-- preview.signal.map(p => (!p).toString),
            onClick --> (_ => preview.set(false))
          ),
          button(
            typ := "button",
            cls := "btn btn--toggle btn--sm note-preview-tab",
            "Preview",
            aria.pressed <-- preview.signal.map(_.toString),
            onClick --> (_ => preview.set(true))
          )
        ),
        label(forId := "note-editor-text", cls := "sr-only", "Your note (Markdown)"),
        textArea(
          idAttr := "note-editor-text",
          cls := "field note-editor-text",
          cls("is-hidden") <-- preview.signal,
          rows := 10,
          placeholder := "Write your note. **bold**, *italic*, - a list, [[citekey]] for a source.",
          controlled(
            value <-- text.signal.map(_.value),
            onInput.mapToValue --> { v =>
              val t = Note.Text(v); text.set(t); NoteDrafts.put(draftKey, t)
            }
          ),
          onMountCallback(ctx => ctx.thisNode.ref.focus())
        ),
        div(
          cls := "note-editor-preview",
          cls("is-hidden") <-- preview.signal.map(!_),
          aria.live := "polite",
          child <-- text.signal.map(t =>
            if t.value.trim.isEmpty then p(cls := "muted", "Nothing written yet.")
            else NoteView(NoteMarkdown.Markdown(t.value))
          )
        ),
        p(
          cls := "muted note-editor-hint",
          "Markdown: **bold**, *italic*, `code`, - lists, > quotes, [a link](https://…), [[citekey]]. ",
          "Ctrl/⌘+Enter saves."
        ),
        child.maybe <-- problem.signal.map(
          _.map(m => ErrorLine.text(Problem.Unclassified(Legacy.Text(s"Not saved: $m"))))
        ),
        div(
          cls := "toolbar note-editor-actions",
          button(
            typ := "button",
            cls := "btn btn--primary note-save",
            "Save",
            disabled <-- text.signal.combineWith(saving.signal).map((t, busy) => t.value.trim.isEmpty || busy),
            onClick --> (_ => save())
          ),
          button(
            typ := "button",
            cls := "btn btn--ghost note-cancel",
            "Cancel",
            onClick --> { _ => NoteDrafts.drop(draftKey); close() }
          )
        )
      )
    )
