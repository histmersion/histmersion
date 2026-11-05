package com.histmersion.web.settings

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.instance.{MapStartConfig, UiConfig}
import com.histmersion.web.{Auth, Theme, ThemeChoice, UiState}
import com.histmersion.web.map.MapMotion
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Settings › General: the theme and the map's motion (this browser's own), and where the map opens (the settings
  * file's `ui.map-start`, for every browser).
  */
object GeneralSection:

  type ThemeNameResult = ThemeNameResult.T
  object ThemeNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChoiceRowTitle = ChoiceRowTitle.T
  object ChoiceRowTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChoiceRowName = ChoiceRowName.T
  object ChoiceRowName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChoiceRowOptionsPart1 = ChoiceRowOptionsPart1.T
  object ChoiceRowOptionsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChoiceRowOptionsPart2 = ChoiceRowOptionsPart2.T
  object ChoiceRowOptionsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChoiceRowCurrent = ChoiceRowCurrent.T
  object ChoiceRowCurrent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChoiceRowChooseArg1 = ChoiceRowChooseArg1.T
  object ChoiceRowChooseArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def pane(ui: UiConfig): HtmlElement =
    div(
      cls := "settings-section",
      sectionTag(
        cls := "settings-card",
        div(
          cls := "settings-card-head",
          h2(cls := "settings-card-title", "In this browser"),
          p(cls := "settings-card-intro", "Kept by this browser only; another browser or computer has its own.")
        ),
        div(
          cls := "settings-card-body",
          choiceRow(
            "Theme",
            "theme",
            ThemeChoice.values.toList.map(c => c.wire -> themeName(c)),
            Theme.choice.map(_.wire),
            w => Theme.choose(ThemeChoice.fromWire(w))
          ),
          choiceRow(
            "Map motion",
            "motion",
            MapMotion.values.toList.map(m => m.wire -> m.label),
            UiState.mapMotion.signal.map(_.wire),
            w => MapMotion.fromWire(w).foreach(m => UiState.set(UiState.mapMotion := m)())
          )
        )
      ),
      startCard(ui)
    )

  private def themeName(c: ThemeChoice): com.histmersion.web.settings.GeneralSection.ThemeNameResult = c match
    case ThemeChoice.System => "Follow the system"
    case ThemeChoice.Light  => "Light"
    case ThemeChoice.Dark   => "Dark"

  private def choiceRow(
      title: com.histmersion.web.settings.GeneralSection.ChoiceRowTitle,
      name: com.histmersion.web.settings.GeneralSection.ChoiceRowName,
      options: List[(com.histmersion.web.settings.GeneralSection.ChoiceRowOptionsPart1, com.histmersion.web.settings.GeneralSection.ChoiceRowOptionsPart2)],
      current: Signal[com.histmersion.web.settings.GeneralSection.ChoiceRowCurrent],
      choose: com.histmersion.web.settings.GeneralSection.ChoiceRowChooseArg1 => Unit
  ): HtmlElement =
    fieldSet(
      cls := "setting-row setting-choice",
      dataAttr("choice") := name,
      legend(cls := "setting-name", title),
      div(
        cls := "setting-options",
        options.map((w, text) =>
          label(
            cls := "setting-option",
            input(
              typ := "radio",
              nameAttr := s"general-$name",
              value := w,
              controlled(checked <-- current.map(_ == w), onClick.mapTo(w) --> (x => choose(x)))
            ),
            " ",
            text
          )
        )
      )
    )

  /** Where the map opens, saved into the settings file. */
  private def startCard(ui: UiConfig): HtmlElement =
    val choice = Var(ui.mapStart)
    val saved = Var(ui.mapStart)
    // the file's own values once read (another window may have changed them since this page loaded)
    def fromPage(p: ConfigEdit.Page): MapStartConfig =
      def text(k: String) = p.field(s"ui.map-start.$k").flatMap(f => f.effective).map(v => Draft.plain(Some(v)))
      MapStartConfig(
        text("mode").getOrElse(ui.mapStart.mode),
        text("lat").flatMap(_.toDoubleOption).getOrElse(ui.mapStart.lat),
        text("lon").flatMap(_.toDoubleOption).getOrElse(ui.mapStart.lon),
        text("zoom").flatMap(_.toDoubleOption).getOrElse(ui.mapStart.zoom),
        text("label").getOrElse(ui.mapStart.label)
      )
    sectionTag(
      cls := "settings-card",
      SettingsStore.current.map(_.flatMap(_.toOption).map(fromPage)) --> { c =>
        c.foreach { v => saved.set(v); choice.set(v) }
      },
      div(
        cls := "settings-card-head",
        h2(cls := "settings-card-title", "Where the map opens"),
        p(cls := "settings-card-intro", "For everyone who opens Histmersion from this server.")
      ),
      div(cls := "settings-card-body", StartPlace.chooser(choice, "general-start", showLegend = false)),
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
            "Save",
            disabled <-- choice.signal.combineWith(saved.signal, Auth.writable).map((c, s, w) => c == s || !w),
            onClick --> (_ =>
              SettingsStore
                .save(StartPlace.changes(choice.now()).map((k, v) => ConfigEdit.Change(k, Some(v))))
                .foreach(_ => saved.set(choice.now()))
            )
          ),
          button(
            typ := "button",
            cls := "btn btn--ghost settings-undo-btn",
            "Undo",
            disabled <-- choice.signal.combineWith(saved.signal).map(_ == _),
            onClick --> (_ => choice.set(saved.now()))
          )
        ),
        SettingsStore.notice
      )
    )
