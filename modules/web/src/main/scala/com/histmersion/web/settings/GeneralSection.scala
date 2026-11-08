package com.histmersion
package web.settings

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.api.instance.{MapStartConfig, MapStartMode, UiConfig}
import com.histmersion.web.{Auth, Theme, ThemeChoice, UiState}
import com.histmersion.web.map.MapMotion
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Settings › General: the theme and the map's motion (this browser's own), and where the map opens (the settings
  * file's `ui.map-start`, for every browser).
  */
object GeneralSection:
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
            ThemeChoice.values.toList.map(c => c.wire.value -> themeName(c)),
            Theme.choice.map(_.wire.value),
            w => Theme.choose(ThemeChoice.fromWire(w))
          ),
          choiceRow(
            "Map motion",
            "motion",
            MapMotion.values.toList.map(m => m.wire.value -> m.label.value),
            UiState.mapMotion.signal.map(_.wire.value),
            w => MapMotion.fromWire(MapMotion.Wire(w)).foreach(m => UiState.set(UiState.mapMotion := m)())
          )
        )
      ),
      startCard(ui)
    )

  private def themeName(c: ThemeChoice): String = c match
    case ThemeChoice.System => "Follow the system"
    case ThemeChoice.Light  => "Light"
    case ThemeChoice.Dark   => "Dark"

  private def choiceRow(
      title: String,
      name: String,
      options: List[(String, String)],
      current: Signal[String],
      choose: String => Unit
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
      def text(k: String): Option[String] =
        p.field(ConfigEdit.Field.Key(s"ui.map-start.$k"))
          .flatMap(f => f.effective)
          .map(v => Draft.plain(Some(Draft.Text(v.value))).value)
      MapStartConfig(
        text("mode").map(MapStartMode.Wire(_)).getOrElse(ui.mapStart.mode),
        text("lat").flatMap(_.toDoubleOption).map(Latitude(_)).getOrElse(ui.mapStart.lat),
        text("lon").flatMap(_.toDoubleOption).map(Longitude(_)).getOrElse(ui.mapStart.lon),
        text("zoom").flatMap(_.toDoubleOption).map(MapStartConfig.Zoom(_)).getOrElse(ui.mapStart.zoom),
        text("label").map(MapStartConfig.Label(_)).getOrElse(ui.mapStart.label)
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
      div(
        cls := "settings-card-body",
        StartPlace.chooser(choice, StartPlace.ChooserName("general-start"), showLegend = false)
      ),
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
