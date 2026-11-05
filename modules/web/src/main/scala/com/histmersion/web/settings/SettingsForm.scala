package com.histmersion.web.settings

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.web.Auth
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** A group of settings with one Save: the changes are held until Save writes them into the settings file together (or
  * Undo drops them), then the server applies them.
  */
object SettingsForm:

  /** One setting of the form: its key, and the name and help shown instead of reference.conf's words. */
  final case class Item(
      key: Item.Key,
      name: Option[Item.Name] = None,
      help: Option[Item.Help] = None,
      fallback: Option[Item.Fallback] = None
  )

  object Item:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Help = Help.T
    object Help extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Fallback = Fallback.T
    object Fallback extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(title: String, intro: Option[String], items: List[Item], extra: Modifier[HtmlElement]*): HtmlElement =
    val draft = Draft()
    sectionTag(
      cls := "settings-card",
      div(
        cls := "settings-card-head",
        h2(cls := "settings-card-title", title),
        intro.map(t => p(cls := "settings-card-intro", t))
      ),
      div(
        cls := "settings-card-body",
        children <-- SettingsStore.current.map {
          case None           => List(p(cls := "muted", "Loading…"))
          case Some(Left(m))  => List(com.histmersion.web.ErrorLine.text(s"Could not read the settings: $m"))
          case Some(Right(p)) =>
            items.flatMap(i =>
              p.field(i.key)
                .map(f =>
                  SettingControl(f.modify(_.default).setTo(f.default.orElse(i.fallback)), draft, i.name, i.help)
                )
            )
        },
        extra
      ),
      saveBar(draft)
    )

  /** Save / Undo for a form's draft, and what the save did. */
  def saveBar(draft: Draft, consent: () => Boolean = () => false): HtmlElement =
    div(
      cls := "settings-save",
      child.maybe <-- Auth.writable.map(w =>
        Option.when(!w)(div(cls := "pane-note", Auth.keyNeeded("change settings")))
      ),
      div(
        cls := "toolbar",
        button(
          typ := "button",
          cls := "btn btn--primary settings-save-btn",
          child.text <-- draft.count.map(n =>
            if n == 0 then "Save" else s"Save ${if n == 1 then "the change" else s"$n changes"}"
          ),
          disabled <-- draft.isEmpty.combineWith(Auth.writable).map((empty, w) => empty || !w),
          onClick --> (_ => SettingsStore.save(draft.changes, consent()).foreach(_ => draft.clear()))
        ),
        button(
          typ := "button",
          cls := "btn btn--ghost settings-undo-btn",
          "Undo",
          disabled <-- draft.isEmpty,
          onClick --> (_ => draft.clear())
        )
      ),
      SettingsStore.notice
    )
