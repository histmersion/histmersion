package com.histmersion
package web.settings

import com.histmersion.api.Problem
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.web.Auth
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.web.ErrorLine

/** A group of settings with one Save: the changes are held until Save writes them into the settings file together (or
  * Undo drops them), then the server applies them.
  */
object SettingsForm:
  type Intro = Intro.T
  object Intro extends Newtype[String, Pure]

  /** One setting of the form: its key, and the name and help shown instead of reference.conf's words. */
  type ApplyTitle = ApplyTitle.T
  object ApplyTitle extends Newtype[String, Pure]

  final case class Item(
      key: Item.Key,
      name: Option[Item.Name] = None,
      help: Option[Item.Help] = None,
      fallback: Option[Item.Fallback] = None
  )

  object Item:
    type Key = ConfigEdit.Field.Key
    val Key: ConfigEdit.Field.Key.type = ConfigEdit.Field.Key
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Help = Help.T
    object Help extends Newtype[String, Pure]
    type Fallback = Fallback.T
    object Fallback extends Newtype[String, Pure]

  def apply(title: ApplyTitle, intro: Option[Intro], items: List[Item], extra: Modifier[HtmlElement]*): HtmlElement =
    val draft = Draft()
    sectionTag(
      cls := "settings-card",
      div(
        cls := "settings-card-head",
        h2(cls := "settings-card-title", title.value),
        intro.map(t => p(cls := "settings-card-intro", t.value))
      ),
      div(
        cls := "settings-card-body",
        children <-- SettingsStore.current.map {
          case None           => List(p(cls := "muted", "Loading…"))
          case Some(Left(m))  => List(ErrorLine.text(Problem(s"Could not read the settings: $m")))
          case Some(Right(p)) =>
            items.flatMap(i =>
              p.field(i.key)
                .map(f =>
                  SettingControl(
                    f.modify(_.default).setTo(f.default.orElse(i.fallback.map(v => ConfigEdit.Field.Default(v.value)))),
                    draft,
                    i.name,
                    i.help
                  )
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
            if n.value == 0 then "Save" else s"Save ${if n.value == 1 then "the change" else s"$n changes"}"
          ),
          disabled <-- draft.isEmpty.combineWith(Auth.writable).map((empty, w) => empty || !w),
          onClick --> (_ =>
            SettingsStore.save(draft.changes, SettingsStore.SaveLiteralConsent(consent())).foreach(_ => draft.clear())
          )
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
