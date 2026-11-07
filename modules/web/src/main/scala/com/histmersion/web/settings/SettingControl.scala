package com.histmersion.web.settings

import com.histmersion.api.Wording
import com.histmersion.api.settings.ConfigEdit
import com.raquo.laminar.api.L.*

/** One setting in a form: its name and what it does, the control for its kind, and where its value comes from — the
  * default, the settings file, or an environment variable that wins over the file. A setting the file writes with a
  * substitution or in an included file is shown, not changed.
  */
object SettingControl:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The name a setting is shown by: its last word, as words (`contact-email` → "Contact email"). Pure. */
  type NameOfKey = NameOfKey.T
  object NameOfKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ControlId = ControlId.T
  object ControlId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ControlLocked = ControlLocked.T
  object ControlLocked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultTextKind = DefaultTextKind.T
  object DefaultTextKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultTextDefault = DefaultTextDefault.T
  object DefaultTextDefault extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def nameOf(key: com.histmersion.web.settings.SettingControl.NameOfKey): com.histmersion.web.settings.SettingsForm.Item.Name = Wording.humanize(key.split('.').lastOption.getOrElse(key))

  def apply(f: ConfigEdit.Field, draft: Draft, name: Option[com.histmersion.web.settings.SettingsForm.Item.Name] = None, help: Option[com.histmersion.web.settings.SettingsForm.Item.Help] = None): HtmlElement =
    val id = "set-" + f.key.replaceAll("[^A-Za-z0-9_-]", "-")
    val locked = f.locked.isDefined
    div(
      cls := "setting-row",
      dataAttr("key") := f.relative,
      cls("is-changed") <-- draft.signal.map(_.contains(f.key)),
      div(
        cls := "setting-head",
        if f.kind == "flag" then
          label(forId := id, cls := "setting-name", control(f, draft, id, locked), " ", name.getOrElse(nameOf(f.key)))
        else label(forId := id, cls := "setting-name", name.getOrElse(nameOf(f.key))),
        span(cls := "setting-key muted", f.relative)
      ),
      help.orElse(Option(f.doc).filter(_.nonEmpty)).map(t => p(cls := "setting-doc", t)),
      Option.when(f.kind != "flag")(control(f, draft, id, locked)),
      status(f, draft)
    )

  private def control(f: ConfigEdit.Field, draft: Draft, id: com.histmersion.web.settings.SettingControl.ControlId, locked: com.histmersion.web.settings.SettingControl.ControlLocked): HtmlElement =
    f.kind match
      case "flag" =>
        input(
          typ := "checkbox",
          idAttr := id,
          cls := "setting-check",
          disabled := locked,
          controlled(
            checked <-- draft.shown(f).map(Draft.isOn),
            onClick.mapToChecked --> (on => draft.set(f.key, Some(on.toString)))
          )
        )
      case "number" =>
        input(
          typ := "text",
          idAttr := id,
          cls := "field setting-input setting-number",
          disabled := locked,
          inputMode := "decimal",
          controlled(value <-- draft.shown(f), onInput.mapToValue --> (v => draft.set(f.key, Some(v))))
        )
      case _ =>
        input(
          typ := "text",
          idAttr := id,
          cls := "field setting-input",
          disabled := locked,
          placeholder := (if f.kind == "list" then "[a, b, c]" else ""),
          controlled(value <-- draft.shown(f), onInput.mapToValue --> (v => draft.set(f.key, Some(v))))
        )

  /** Where the value comes from, and the way back to the default. */
  /** The default, in words: "On by default" for a flag, else "Default: …". Pure. */
  def defaultText(kind: com.histmersion.web.settings.SettingControl.DefaultTextKind, default: com.histmersion.web.settings.SettingControl.DefaultTextDefault): com.histmersion.web.settings.SettingControl.Text =
    if kind == "flag" then s"${if Draft.isOn(default) then "On" else "Off"} by default."
    else if default.isEmpty then "Default: (empty)."
    else s"Default: $default."

  private def status(f: ConfigEdit.Field, draft: Draft): HtmlElement =
    val dflt = Draft.plain(f.default)
    div(
      cls := "setting-status muted",
      f.locked.map(why => span(cls := "setting-locked", why)),
      Option.when(f.source == ConfigEdit.Source.Environment.wire)(
        span(
          cls := "setting-env",
          s"Set by the environment variable ${f.envVar.getOrElse("")}, which wins over the settings file while it is set."
        )
      ),
      Option.when(f.locked.isEmpty && f.source != ConfigEdit.Source.Environment.wire)(
        span(
          child.text <-- draft.signal.map(d =>
            d.get(f.key) match
              case Some(None)                                      => "Back to the default when saved."
              case Some(Some(_))                                   => "Changed — not saved yet."
              case None if f.source == ConfigEdit.Source.File.wire =>
                s"Set in the settings file. ${defaultText(f.kind, dflt)}"
              case None if f.kind == "flag" => defaultText(f.kind, dflt)
              case None                     => s"The default${if dflt.isEmpty then "" else s": $dflt"}."
          )
        )
      ),
      f.restart.map(why => span(cls := "setting-restart", s" Takes effect after a restart ($why).")),
      Option.when(f.source == ConfigEdit.Source.File.wire && f.locked.isEmpty)(
        button(
          typ := "button",
          cls := "btn btn--bare btn--sm setting-reset",
          "Use the default",
          onClick --> (_ => draft.set(f.key, None))
        )
      )
    )
